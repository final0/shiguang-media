const form = document.querySelector('#chat-form');
const provider = document.querySelector('#provider');
const model = document.querySelector('#model');
const stream = document.querySelector('#stream');
const question = document.querySelector('#question');
const messages = document.querySelector('#messages');
const status = document.querySelector('#status');
const send = document.querySelector('#send');
const stop = document.querySelector('#stop');
const clear = document.querySelector('#clear');
let history = [];
let controller;

function setBusy(busy) {
    provider.disabled = model.disabled = stream.disabled = question.disabled = clear.disabled = busy;
    send.disabled = busy || !provider.value;
    stop.disabled = !busy;
}

function resetChat() {
    history = [];
    messages.replaceChildren();
    status.textContent = '对话已清空';
}

function addMessage(role, content) {
    messages.querySelector('.empty')?.remove();
    const article = document.createElement('article');
    article.className = `message ${role}`;
    const label = document.createElement('strong');
    label.textContent = role === 'user' ? '你' : 'AI';
    const text = document.createElement('pre');
    // 模型输出按纯文本展示，不能作为 HTML 注入页面。
    text.textContent = content;
    article.append(label, text);
    messages.append(article);
    messages.scrollTop = messages.scrollHeight;
    return text;
}

async function loadProviders() {
    try {
        const response = await fetch('/api/ai/providers');
        if (!response.ok) throw new Error(`读取配置失败（HTTP ${response.status}）`);
        const items = await response.json();
        provider.replaceChildren();
        for (const item of items) {
            const option = document.createElement('option');
            option.value = item.id;
            option.textContent = `${item.id} / ${item.model}${!item.enabled ? '（未启用）' : !item.configured ? '（未配置密钥）' : ''}`;
            option.disabled = !item.enabled || !item.configured;
            provider.append(option);
        }
        const selected = items.find(item => item.defaultProvider && item.enabled && item.configured)
            || items.find(item => item.enabled && item.configured);
        provider.value = selected?.id || '';
        status.textContent = selected ? '可以开始对话' : '请在服务端配置至少一个厂商的 API Key，重启应用后刷新页面。';
        setBusy(false);
    } catch (error) {
        status.textContent = error.message;
    }
}

// POST SSE 使用 fetch 读取，缓冲不完整事件；TextDecoder 处理跨数据块的中文字符。
async function readStream(response, onDelta) {
    if (!response.body) throw new Error('浏览器不支持流式响应');
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let completed = false;
    function consume(frame) {
        const data = frame.split(/\r?\n/).filter(line => line.startsWith('data:'))
            .map(line => line.slice(5).trimStart()).join('\n');
        if (!data) return;
        const event = JSON.parse(data);
        if (event.type === 'error') throw new Error(event.content || '模型调用失败');
        if (event.type === 'delta') onDelta(event.content);
        if (event.type === 'done') completed = true;
    }
    try {
        while (!completed) {
            const { value, done } = await reader.read();
            buffer += done ? decoder.decode() : decoder.decode(value, { stream: true });
            let separator;
            while ((separator = /\r?\n\r?\n/.exec(buffer)) !== null) {
                consume(buffer.slice(0, separator.index));
                buffer = buffer.slice(separator.index + separator[0].length);
            }
            if (done) {
                if (buffer.trim()) consume(buffer);
                if (!completed) throw new Error('连接提前结束，未收到完成事件');
                break;
            }
        }
    } finally {
        try { await reader.cancel(); } catch (_) { /* 请求可能已取消。 */ }
        reader.releaseLock();
    }
}

form.addEventListener('submit', async event => {
    event.preventDefault();
    const content = question.value.trim();
    if (!content || controller || !provider.value) return;
    const current = { role: 'user', content };
    // 保留成对的成功历史，避免超出服务端条数和总长度限制。
    const context = history.slice(-38);
    while (context.length && context.reduce((sum, item) => sum + item.content.length, content.length) > 64000) {
        context.splice(0, 2);
    }
    addMessage('user', content);
    const output = addMessage('assistant', '');
    controller = new AbortController();
    setBusy(true);
    status.textContent = '正在等待模型…';
    let answer = '';
    try {
        const response = await fetch(stream.checked ? '/api/ai/chat/stream' : '/api/ai/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ provider: provider.value, model: model.value.trim() || null, messages: [...context, current] }),
            signal: controller.signal
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || `请求失败（HTTP ${response.status}）`);
        }
        if (stream.checked) {
            await readStream(response, delta => {
                answer += delta;
                output.textContent = answer;
                messages.scrollTop = messages.scrollHeight;
            });
        } else {
            const result = await response.json();
            answer = result.content || '';
            output.textContent = answer;
        }
        if (!answer.trim()) throw new Error('模型未返回文本内容');
        // 过长的回复不加入下一轮，以满足服务端单消息 16000 字符限制。
        history = answer.length <= 16000 ? [...context, current, { role: 'assistant', content: answer }].slice(-38) : [];
        question.value = '';
        status.textContent = answer.length <= 16000 ? '回答完成' : '回答完成；回复过长，下一次将开始新对话。';
    } catch (error) {
        const message = error.name === 'AbortError' ? '已停止生成' : error.message;
        status.textContent = `${message}；本轮未加入后续上下文，可重试。`;
        if (!answer) output.textContent = message;
    } finally {
        controller = undefined;
        setBusy(false);
    }
});

stop.addEventListener('click', () => controller?.abort());
clear.addEventListener('click', resetChat);
provider.addEventListener('change', resetChat);
model.addEventListener('change', resetChat);
loadProviders();
