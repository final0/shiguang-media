"""Nacos 3.x initContainer: fetch once on each Pod start; never log credentials."""
import hashlib
import json
import os
import pathlib
import time
import urllib.parse
import urllib.request


def fetch():
    base = os.environ["NACOS_URL"].rstrip("/")
    login = urllib.parse.urlencode({
        "username": os.environ["NACOS_USERNAME"],
        "password": os.environ["NACOS_PASSWORD"],
    }).encode()
    with urllib.request.urlopen(base + "/v3/auth/user/login", data=login, timeout=15) as response:
        auth = json.load(response)
    token = auth.get("accessToken") or (auth.get("data") or {}).get("accessToken")
    if not token:
        raise RuntimeError("Nacos authentication failed")
    query = urllib.parse.urlencode({
        "namespaceId": os.environ.get("NACOS_NAMESPACE", "public"),
        "groupName": os.environ.get("NACOS_GROUP", "SHIGUANG"),
        "dataId": os.environ.get("NACOS_DATA_ID", "springai-demo.yaml"),
    })
    request = urllib.request.Request(base + "/v3/client/cs/config?" + query,
                                     headers={"accessToken": token})
    with urllib.request.urlopen(request, timeout=20) as response:
        result = json.load(response)
    data = result.get("data") or {}
    content = data.get("content")
    if result.get("code") != 0 or not content or not content.strip():
        raise RuntimeError("Nacos configuration missing or unreadable")
    raw = content.encode("utf-8")
    if data.get("md5") and hashlib.md5(raw).hexdigest() != data["md5"]:
        raise RuntimeError("Nacos configuration checksum mismatch")
    target = pathlib.Path("/runtime/application.yaml")
    temporary = target.with_suffix(".tmp")
    temporary.write_bytes(raw)
    temporary.chmod(0o640)
    temporary.replace(target)
    print("Nacos configuration loaded; sha256=" + hashlib.sha256(raw).hexdigest(), flush=True)


for attempt in range(1, 4):
    try:
        fetch()
        break
    except Exception as error:
        # HTTP errors can include request details; emit only the exception type.
        print("Nacos fetch attempt %d failed (%s)" % (attempt, type(error).__name__), flush=True)
        if attempt == 3:
            raise SystemExit(1)
        time.sleep(3 * attempt)
