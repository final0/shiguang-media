"""Run on the deployment host with Python3/PyYAML, kubectl and the uploaded JAR.

First deployment only: import JAR defaults into Nacos, with credentials in K8s Secrets.
Existing Nacos configuration is never overwritten. No passwords are printed or saved.
"""
import copy
import getpass
import json
import os
import pathlib
import re
import subprocess
import time
import urllib.parse
import urllib.request
import zipfile

import yaml

ROOT = pathlib.Path('/opt/apps/shiguang-media')
BASE = 'http://127.0.0.1:8848/nacos'
COORDINATES = {'namespaceId': 'public', 'groupName': 'SHIGUANG', 'dataId': 'springai-demo.yaml'}


def merge(target, source):
    for key, value in source.items():
        if isinstance(value, dict) and isinstance(target.get(key), dict):
            merge(target[key], value)
        else:
            target[key] = copy.deepcopy(value)


def property_value(document, path):
    result = document
    for key in path.split('.'):
        result = result[key]
    return result


def resolve(value, document):
    pattern = re.compile(r'\$\{([^{}]+)\}')
    def replacement(match):
        key, _, default = match.group(1).partition(':')
        if key in os.environ:
            return os.environ[key]
        try:
            return str(property_value(document, key))
        except (KeyError, TypeError):
            return default
    for _ in range(20):
        updated = pattern.sub(replacement, str(value))
        if updated == value:
            if '${' in updated:
                raise RuntimeError('Unresolved configuration placeholder')
            return updated
        value = updated
    raise RuntimeError('Configuration reference cycle')


def apply(resource):
    subprocess.run(['kubectl', 'apply', '-f', '-'], input=json.dumps(resource),
                   text=True, check=True, stdout=subprocess.DEVNULL)


def api(path, token=None, data=None):
    request = urllib.request.Request(BASE + path,
        data=urllib.parse.urlencode(data).encode() if data is not None else None,
        headers={'accessToken': token} if token else {})
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def main():
    password = getpass.getpass('Nacos password: ')
    auth = api('/v3/auth/user/login', data={'username': 'nacos', 'password': password})
    token = auth.get('accessToken') or (auth.get('data') or {}).get('accessToken')
    if not token:
        raise RuntimeError('Nacos login failed')
    existing = api('/v3/client/cs/config?' + urllib.parse.urlencode(COORDINATES), token)
    if existing.get('code') != 20004:
        raise RuntimeError('Config already exists or cannot be checked; no changes made')
    with zipfile.ZipFile(ROOT / 'springai-demo-0.0.1-SNAPSHOT.jar') as jar:
        config = yaml.safe_load(jar.read('BOOT-INF/classes/application.yml'))
        merge(config, yaml.safe_load(jar.read('BOOT-INF/classes/application-mysql.yml')))
    original = copy.deepcopy(config)
    secret_paths = {
        'spring.datasource.username': 'GROWTH_MYSQL_USERNAME',
        'spring.datasource.password': 'GROWTH_MYSQL_PASSWORD',
        'spring.data.redis.password': 'GROWTH_REDIS_PASSWORD',
        'app.minio.access-key': 'GROWTH_MINIO_ACCESS_KEY',
        'app.minio.secret-key': 'GROWTH_MINIO_SECRET_KEY',
        'app.ai.providers.qwen.api-key': 'QWEN_API_KEY',
        'app.ai.providers.deepseek.api-key': 'DEEPSEEK_API_KEY',
        'app.ai.providers.kimi.api-key': 'OPENAI_API_KEY',
    }
    secrets = {}
    for path, name in secret_paths.items():
        secrets[name] = resolve(property_value(original, path), original)
        parent, _, key = path.rpartition('.')
        property_value(config, parent)[key] = '${' + name + '}'
    # Materialize all remaining non-secret defaults before configuring internal endpoints.
    def materialize(node):
        for key, value in node.items():
            if isinstance(value, dict):
                materialize(value)
            elif isinstance(value, str) and value not in {'${' + k + '}' for k in secrets}:
                node[key] = resolve(value, original)
    materialize(config)
    config['spring']['profiles'] = {'active': 'prod'}
    config['server']['address'] = '0.0.0.0'
    config['server']['port'] = 8080
    config['server']['shutdown'] = 'graceful'
    config['server']['tomcat'] = {'threads': {'max': 32, 'min-spare': 4}}
    config['spring']['lifecycle'] = {'timeout-per-shutdown-phase': '30s'}
    jdbc = config['spring']['datasource']['url']
    config['spring']['datasource']['url'] = re.sub(r'(?<=//)[^/]+', '192.168.32.1:3306', jdbc, count=1)
    config['spring']['datasource']['hikari'] = {'maximum-pool-size': 5, 'minimum-idle': 1}
    config['spring']['data']['redis']['host'] = '192.168.32.1'
    config['spring']['data']['redis']['port'] = 6379
    config['app']['minio']['endpoint'] = 'http://192.168.32.1:9000'
    config['app']['minio']['console-url'] = 'http://192.168.32.1:9001'
    config['growth']['media-directory'] = '/tmp/legacy-media'
    config['spring']['sql']['init']['mode'] = 'never'
    content = yaml.safe_dump(config, allow_unicode=True, sort_keys=False)
    for secret in secrets.values():
        if len(secret) > 6 and secret in content:
            raise RuntimeError('Refusing to publish plaintext credentials')
    apply({'apiVersion': 'v1', 'kind': 'Namespace', 'metadata': {'name': 'shiguang'}})
    for name, data in [('shiguang-runtime', secrets),
                       ('shiguang-nacos-auth', {'NACOS_USERNAME': 'nacos', 'NACOS_PASSWORD': password})]:
        # create avoids kubectl storing secret plaintext in last-applied annotations.
        resource = {'apiVersion': 'v1', 'kind': 'Secret', 'type': 'Opaque',
                    'metadata': {'name': name, 'namespace': 'shiguang'}, 'stringData': data}
        subprocess.run(['kubectl', 'create', '-f', '-'], input=json.dumps(resource),
                       text=True, check=True, stdout=subprocess.DEVNULL)
    result = api('/v3/admin/cs/config', token,
                 dict(COORDINATES, content=content, type='yaml', appName='springai-demo'))
    if result.get('code') != 0:
        raise RuntimeError('Nacos publish failed')
    actual = ''
    for _ in range(5):
        reread = api('/v3/client/cs/config?' + urllib.parse.urlencode(COORDINATES), token)
        actual = (reread.get('data') or {}).get('content') or ''
        if actual.strip() == content.strip():
            break
        time.sleep(2)
    if actual.strip() != content.strip():
        raise RuntimeError('Nacos read-back mismatch')
    (ROOT / 'deploy' / 'application.nacos.yaml').write_text(content, encoding='utf-8')
    print('Nacos published and verified: public / SHIGUANG / springai-demo.yaml')
    print('Runtime credentials created in Kubernetes Secrets (values not printed).')


if __name__ == '__main__':
    main()
