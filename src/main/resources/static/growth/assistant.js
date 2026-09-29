'use strict';
// 连续聊天历史由服务端维护；客户端只提交会话编号/轮次，模型内容均按纯文本渲染。
(() => {
  function createUuid(){
    const cryptoApi=globalThis.crypto;
    if(typeof cryptoApi?.randomUUID==='function')return cryptoApi.randomUUID();
    // 公网 HTTP 下 randomUUID 不可用；getRandomValues 仍可生成安全随机数。
    if(typeof cryptoApi?.getRandomValues!=='function')throw new Error('浏览器不支持生成会话编号，请升级浏览器或使用 HTTPS 访问');
    const bytes=cryptoApi.getRandomValues(new Uint8Array(16));
    bytes[6]=(bytes[6]&0x0f)|0x40;
    bytes[8]=(bytes[8]&0x3f)|0x80;
    const hex=Array.from(bytes,value=>value.toString(16).padStart(2,'0')).join('');
    return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
  }
  const launcher=document.createElement('button');
  launcher.type='button';launcher.className='assistant-launcher';launcher.hidden=true;
  launcher.innerHTML=`${icon('leaf')}<span>问问拾光</span>`;launcher.setAttribute('aria-label','打开拾光小助手');
  const dialog=document.createElement('dialog');dialog.className='assistant-dialog';dialog.setAttribute('aria-label','拾光小助手');
  document.body.append(launcher,dialog);
  let context=null,active=null,draft=null,lastData=null,opener=null;
  const field=name=>context?.host.querySelector(`[data-ai-field="${name}"]`);
  const scope=task=>task==='chat'?'使用范围：本次输入、孩子昵称和月龄、本会话近期完整问答（最多12轮、24000字符）。不自动读取成长记录或相册。':task==='diary'?'使用范围：本次笔记、孩子昵称和月龄；不读取相册。':'使用范围：本次问题、孩子昵称和月龄、日期范围内测量统计及最近12条明细；不作医学判断。';
  const questionLabel=task=>task==='chat'?'想和拾光聊些什么？':task==='diary'?'今天发生了什么？':'想重点看看什么？';
  const placeholder=task=>task==='chat'?'可以聊聊今天的小事，也可以接着问“刚才那段再短一点”。':task==='diary'?'写几句真实发生的小事，小助手帮你整理。':'例如：整理最近几次身高、体重和头围的变化。';
  const sendLabel=task=>task==='chat'?'发送':task==='diary'?'生成日记草稿':'整理测量变化';
  const setStatus=text=>{const target=context?.host.querySelector('.assistant-status');if(target)target.textContent=text;};
  const setBusy=value=>{
    if(!context)return;
    context.host.querySelectorAll('[data-ai-lock]').forEach(control=>control.disabled=value);
    context.host.querySelector('[data-ai-action="stop"]').hidden=!value;
    context.host.querySelector('[data-ai-action="send"]').hidden=value;
    context.host.querySelector('.assistant-result').setAttribute('aria-busy',String(value));
  };
  function stop(message='已停止生成，本次内容未保存。'){
    const running=active;active=null;draft=null;
    if(running){running.controller.abort();api(`/assistant/requests/${running.id}/cancel`,{method:'POST'}).catch(()=>{});if(running.reply){running.reply.textContent='已停止接收，本轮回答未确认完成。';running.reply.classList.add('assistant-message-error');}}
    if(context){setBusy(false);setStatus(message);const output=context.host.querySelector('.assistant-draft');if(output)output.replaceChildren();}
  }
  function forgetChat(c){
    if(!c)return;
    if(c.conversationId)api(`/assistant/conversations/${c.conversationId}`,{method:'DELETE'}).catch(()=>{});
    c.conversationId=null;c.turn=0;
    const chat=c.host.querySelector('.assistant-chat');
    if(chat)chat.innerHTML='<p class="assistant-chat-welcome">你好，我是拾光。聊聊今天的小事吧，也可以让我帮你想想怎么记录。<small>可以连续追问；新对话、切换孩子或关闭面板会清空当前聊天。</small></p>';
  }
  function chatMessage(c,role,text){
    const chat=c.host.querySelector('.assistant-chat'),message=document.createElement('article');
    message.className=`assistant-message assistant-message-${role}`;
    const label=document.createElement('small');label.textContent=role==='user'?'你':'拾光';
    const body=document.createElement('div');body.textContent=text;message.append(label,body);chat.append(message);chat.scrollTop=chat.scrollHeight;
    return body;
  }
  function reset(){
    if(active)stop();
    forgetChat(context);
    if(context?.inline)context.host.remove();
    context=null;draft=null;
    if(dialog.open)dialog.close();dialog.replaceChildren();
  }
  function sync(){
    // 新快照、登出或切换孩子后清除上一次回答，不展示已经修改/删除资料的旧解释。
    if(lastData!==state.data || (context&&!context.inline&&context.pageChild!==state.childId))reset();
    lastData=state.data;launcher.hidden=!state.data;
  }
  const action=(label,name,extra='')=>`<button type="button" class="btn secondary small" data-ai-action="${name}" ${extra}>${label}</button>`;
  function renderPanel(){
    const c=context,s=c.settings;
    c.host.innerHTML=`<header class="assistant-head"><div><span class="eyebrow">LITTLE MOMENTS · AI</span><h2>${icon('leaf')} 拾光小助手</h2><p>和你一起，好好收藏成长</p></div>${action('关闭','close','aria-label="关闭拾光小助手"')}</header>
      <div class="assistant-body"><p class="assistant-notice">${esc(s.notice)}</p>
      ${!s.available?'<p class="error-message">千问暂不可用，请联系管理员检查模型配置。</p>':''}
      ${!s.enabled?`<section class="assistant-welcome"><h3>让小助手陪你整理成长</h3><p>${s.canManage?'开启后，家人可以主动选择资料交给千问整理。':'请家庭管理员先开启拾光小助手。'}</p>${s.canManage?`<label class="assistant-consent"><input type="checkbox" data-ai-field="enableConsent">我已了解资料使用范围，同意为家庭开启</label>${action('开启千问小助手','enable')}`:''}</section>`:''}
      <div class="assistant-workspace" ${s.enabled&&s.available?'':'hidden'}>
        <div class="form-row"><label class="field"><span>为谁记录</span><select data-ai-field="child" data-ai-lock ${c.inline?'disabled':''}>${childOptions(c.childId)}</select></label><label class="field"><span>想做什么</span><select data-ai-field="task" data-ai-lock ${c.inline?'disabled':''}><option value="chat" ${c.task==='chat'?'selected':''}>和拾光聊聊</option><option value="diary" ${c.task==='diary'?'selected':''}>帮我写日记</option><option value="measurements" ${c.task==='measurements'?'selected':''}>整理测量变化</option></select></label></div>
        <div class="assistant-measure-dates form-row" ${c.task==='measurements'?'':'hidden'}><label class="field"><span>开始日期</span><input data-ai-field="from" data-ai-lock type="date" max="${today()}" value="${c.from}"></label><label class="field"><span>结束日期</span><input data-ai-field="to" data-ai-lock type="date" max="${today()}" value="${c.to}"></label></div>
        <div class="assistant-diary-options form-row" ${c.task==='diary'?'':'hidden'}><label class="field"><span>记录日期</span><input data-ai-field="date" data-ai-lock type="date" max="${today()}" value="${c.date}" ${c.inline?'disabled':''}></label><label class="field"><span>表达风格</span><select data-ai-field="style" data-ai-lock><option value="gentle">温柔自然</option><option value="simple">简洁记录</option><option value="playful">轻松童趣</option></select></label></div>
        <section class="assistant-chat" aria-label="当前对话" role="log" aria-live="polite" ${c.task==='chat'?'':'hidden'}><p class="assistant-chat-welcome">你好，我是拾光。聊聊今天的小事吧，也可以让我帮你想想怎么记录。<small>可以连续追问；新对话、切换孩子或关闭面板会清空当前聊天。</small></p></section>
        <label class="field"><span class="assistant-question-label">${questionLabel(c.task)}</span><textarea data-ai-field="question" data-ai-lock maxlength="4000" rows="3" placeholder="${placeholder(c.task)}">${esc(c.question)}</textarea></label>
        <label class="assistant-consent"><input type="checkbox" data-ai-field="consent" data-ai-lock>我同意将本次输入及所选资料发送给千问</label>
        <p class="assistant-scope">${scope(c.task)}</p>
        <div class="assistant-controls"><button type="button" class="btn" data-ai-action="send">${icon('star')} ${sendLabel(c.task)}</button>${action('停止生成','stop','hidden')}${action(c.task==='chat'?'新对话':'清空回答','clear','data-ai-lock')}</div>
        <p class="assistant-status" role="status" aria-live="polite">每天最多 ${s.dailyLimit} 次。草稿需要你核对后保存。</p>
        <section class="assistant-result" aria-busy="false"><div class="assistant-facts"></div><div class="assistant-answer"></div><div class="assistant-sources"></div><div class="assistant-draft"></div></section>
      </div>${s.enabled&&s.canManage?`<footer class="assistant-admin">${action('关闭家庭助手','disable')}<small>关闭后停止接受新请求，尝试停止正在生成的回答。</small></footer>`:''}</div>`;
    // inline 表单与孩子/日期绑定，生成结束也不能被 setBusy 解除绑定。
    if(c.inline)c.host.querySelectorAll('[data-ai-field="child"],[data-ai-field="task"],[data-ai-field="date"]').forEach(el=>el.removeAttribute('data-ai-lock'));
  }
  async function open({task='chat',form=null,trigger=null}={}){
    reset();if(!state.data)return toast('请先登录家庭空间');
    if(!state.data.children.length)return toast('请先添加孩子档案');
    opener=trigger||document.activeElement;
    const now=new Date();now.setDate(now.getDate()-90);
    const from=`${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`;
    let host=dialog;
    if(form){host=document.createElement('section');host.className='assistant-inline';host.setAttribute('aria-label','日记草稿助手');form.querySelector('textarea[name="content"]').closest('label').after(host);}
    const c={host,inline:!!form,form,task,childId:form?Number(form.elements.childId.value):state.childId,pageChild:state.childId,from,to:today(),date:form?form.elements.recordTime.value:today(),question:form?[form.elements.title.value,form.elements.content.value].filter(Boolean).join('\n'):'',settings:null,conversationId:null,turn:0};
    context=c;host.innerHTML='<p class="assistant-loading" role="status">正在打开拾光小助手…</p>';
    if(!form)dialog.showModal();else host.scrollIntoView({block:'nearest',behavior:'smooth'});
    try{const settings=await api('/assistant/settings');if(context!==c)return;c.settings=settings;renderPanel();field('question')?.focus();}
    catch(error){if(context!==c)return;host.innerHTML=`<div class="assistant-body"><p class="error-message">${esc(error.message)}</p>${action('关闭','close')}</div>`;}
  }
  function facts(data){
    const target=context.host.querySelector('.assistant-facts');
    if(!data?.metrics){target.replaceChildren();return;}
    target.innerHTML=`<div class="assistant-fact-card"><h3>已核对的测量数据</h3><p>${esc(data.from)} 至 ${esc(data.to)} · ${data.count} 次测量</p>${data.metrics.map(m=>`<p><b>${esc(m.label)}</b>：${esc(m.first)} → ${esc(m.last)} ${esc(m.unit)}<br><small>${esc(m.firstDate)} 至 ${esc(m.lastDate)} · ${m.count} 次${m.change==null?' · 暂无跨次变化':` · 变化 ${Number(m.change)>0?'+':''}${esc(m.change)} ${esc(m.unit)}`}</small></p>`).join('')}</div>`;
  }
  function sources(rows){
    context.host.querySelector('.assistant-sources').innerHTML=rows?.length?`<details><summary>查看参考明细（最近 ${rows.length} 条）</summary><ul>${rows.map(r=>`<li>${esc(r.measureTime)}：身高 ${esc(r.height)} cm，体重 ${esc(r.weight)} kg${r.head==null?'':`，头围 ${esc(r.head)} cm`}</li>`).join('')}</ul>${action('打开成长数据','sources')}</details>`:'';
  }
  function showDraft(value){
    draft=value;
    context.host.querySelector('.assistant-draft').innerHTML=`<section class="assistant-draft-card"><h3>给这一刻，留一段文字</h3><p class="form-hint">这是 AI 草稿，请核对事实，也可以直接修改。</p><label class="field"><span>标题</span><input data-ai-field="draftTitle" maxlength="80" value="${esc(value.title)}"></label><label class="field"><span>正文</span><textarea data-ai-field="draftContent" maxlength="4000" rows="7">${esc(value.content)}</textarea></label><label class="field"><span>标签（逗号分隔，最多6个）</span><input data-ai-field="draftTags" maxlength="197" value="${esc(value.tags.join('，'))}"></label><div class="assistant-controls">${action('填入记录表单','apply')}${action('复制草稿','copy')}</div><div class="assistant-replace"></div><p class="form-hint">填入后仍需点击记录表单的保存按钮。</p></section>`;
  }
  async function readStream(response,onEvent){
    if(!response.body)throw new Error('浏览器无法读取回答，请换用支持流式响应的浏览器');
    const reader=response.body.getReader(),decoder=new TextDecoder();let buffer='',doneEvent=false;
    const consume=block=>{const data=block.split(/\r?\n/).filter(line=>line.startsWith('data:')).map(line=>line.slice(5).trimStart()).join('\n');if(!data)return;const event=JSON.parse(data);if(event.type==='error')throw new Error(event.data.message||'生成失败');onEvent(event);if(event.type==='done')doneEvent=true;};
    try{
      while(!doneEvent){const {value,done}=await reader.read();buffer+=done?decoder.decode():decoder.decode(value,{stream:true});let separator;while((separator=/\r?\n\r?\n/.exec(buffer))!==null){consume(buffer.slice(0,separator.index));buffer=buffer.slice(separator.index+separator[0].length);}if(buffer.length>100000)throw new Error('回答格式异常，请重试');if(done){if(buffer.trim())consume(buffer);if(!doneEvent)throw new Error('连接提前结束，回答尚未完成');break;}}
    }finally{try{await reader.cancel();}catch(_){}reader.releaseLock();}
  }
  async function send(){
    if(active||!context)return;const c=context;
    if(!field('consent').checked)throw new Error('请先确认本次资料将发送给千问');
    const task=field('task').value,question=field('question').value.trim(),childId=Number(field('child').value);
    if(!question)throw new Error(task==='chat'?'请先输入想聊的内容':task==='diary'?'请先写几句真实发生的小事':'请输入想整理的问题');
    if(question.length>4000)throw new Error('本次笔记或问题请控制在4000字以内');
    const from=task==='chat'?today():task==='diary'?field('date').value:field('from').value,to=task==='measurements'?field('to').value:from;
    if(!from||!to||from>to||to>today())throw new Error('请填写有效日期，不能选择未来日期');
    // 所有编号生成成功后再进入发送状态，避免异常导致面板一直处于忙碌状态。
    const requestId=createUuid(),conversationId=task==='chat'?(c.conversationId||createUuid()):null;
    const controller=new AbortController();const running={id:requestId,controller};active=running;draft=null;
    c.sentChild=childId;c.sentDate=to;c.sentTask=task;
    c.host.querySelectorAll('.assistant-answer,.assistant-facts,.assistant-sources,.assistant-draft').forEach(el=>el.replaceChildren());
    if(task==='chat'){
      c.conversationId=conversationId;
      c.host.querySelector('.assistant-chat-welcome')?.remove();
      chatMessage(c,'user',question);running.reply=chatMessage(c,'assistant','正在想一想…');
    }
    setBusy(true);setStatus('正在核对资料并连接千问…');let answer='';
    try{
      const response=await fetch('/api/growth/assistant/stream',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-Growth-Request':'1'},body:JSON.stringify({requestId,childId,task,question,from,to,style:field('style').value,consent:true,conversationId:task==='chat'?c.conversationId:null,turn:task==='chat'?c.turn:0}),signal:controller.signal});
      if(!response.ok){const error=await response.json().catch(()=>({}));throw new Error(error.message||`请求失败（${response.status}）`);}
      await readStream(response,event=>{
        if(context!==c||active!==running)return;
        switch(event.type){
          case'meta':facts(event.data.facts);sources(event.data.sources);break;
          case'stage':setStatus(event.data.text);break;
          case'delta':{
            answer+=event.data.text;
            if(running.reply){const chat=c.host.querySelector('.assistant-chat'),nearEnd=chat.scrollHeight-chat.scrollTop-chat.clientHeight<80;running.reply.textContent=answer;if(nearEnd)chat.scrollTop=chat.scrollHeight;}
            else c.host.querySelector('.assistant-answer').textContent=answer;
            setStatus(task==='chat'?'拾光正在回复…':'千问正在整理…');break;
          }
          case'draft':showDraft(event.data);break;
          case'done':
            if(task==='chat'){c.turn++;field('question').value='';setStatus('可以继续追问，Ctrl / ⌘ + Enter 发送；新对话可清空上下文。');}
            else setStatus(task==='diary'?'草稿已生成，请核对后填入表单。':'整理完成，仅描述所记录的测量，不代表医学结论。');
            break;
        }
      });
    }catch(error){
      if(context===c&&active===running){draft=null;c.host.querySelectorAll('.assistant-answer,.assistant-facts,.assistant-sources,.assistant-draft').forEach(el=>el.replaceChildren());if(running.reply){running.reply.textContent=`本轮未完成：${error.message}。输入已保留，可重试。`;running.reply.classList.add('assistant-message-error');}setStatus(error.name==='AbortError'?'已停止生成，本次内容未保存。':error.message);}
    }finally{if(active===running){active=null;if(context===c){setBusy(false);if(task==='chat')field('question').focus();}}}
  }
  function getDraft(){
    if(!draft)throw new Error('请先完成草稿生成');
    const value={title:field('draftTitle').value.trim(),content:field('draftContent').value.trim(),tags:field('draftTags').value.split(/[,，]/).map(t=>t.trim()).filter(Boolean)};
    if(!value.title||value.title.length>80||!value.content||value.content.length>4000||value.tags.length>6||value.tags.some(t=>t.length>32))throw new Error('请核对标题、正文和标签长度');
    return value;
  }
  function apply(replace=false){
    if(active)return;const c=context,value=getDraft();
    if(c.inline){
      const form=c.form;
      if(!form.isConnected||Number(form.elements.childId.value)!==c.sentChild||form.elements.recordTime.value!==c.sentDate)throw new Error('记录的孩子或日期已变化，请重新生成');
      if(!replace&&[form.elements.title,form.elements.content,form.elements.tags].some(el=>el.value.trim())){
        c.host.querySelector('.assistant-replace').innerHTML=`<div class="assistant-replace-note"><p>原表单已有文字。确认后将替换标题、正文和标签，照片、视频及其他字段保留。</p><details><summary>查看将被替换的原文</summary><p>${esc(form.elements.title.value)}</p><pre>${esc(form.elements.content.value)}</pre><p>${esc(form.elements.tags.value)}</p></details>${action('确认替换文字','replace')}${action('保留原文','keep')}</div>`;return;
      }
      form.elements.title.value=value.title;form.elements.content.value=value.content;form.elements.tags.value=value.tags.join('，');
      reset();toast('已填入草稿，检查后点击保存即可');
    }else{
      const childId=c.sentChild,date=c.sentDate;reset();state.childId=childId;recordForm();
      const form=document.querySelector('#modal form[data-form="record"]');if(!form)return;
      form.elements.title.value=value.title;form.elements.content.value=value.content;form.elements.tags.value=value.tags.join('，');form.elements.recordTime.value=date;
      toast('已填入新记录，请核对后保存');
    }
  }
  async function handle(event){
    const button=event.target.closest('[data-ai-action]');if(!button||button.disabled||!context||!context.host.contains(button))return;
    const c=context;button.disabled=true;
    try{
      switch(button.dataset.aiAction){
        case'close':reset();opener?.focus();break;
        case'enable':if(!field('enableConsent').checked)throw new Error('请先阅读并确认资料使用范围');c.settings=await api('/assistant/settings',{method:'PUT',body:{enabled:true}});if(context===c)renderPanel();break;
        case'disable':c.settings=await api('/assistant/settings',{method:'PUT',body:{enabled:false}});if(context===c){stop();forgetChat(c);renderPanel();}break;
        case'send':await send();break;
        case'stop':stop();break;
        case'clear':draft=null;forgetChat(c);c.host.querySelectorAll('.assistant-answer,.assistant-facts,.assistant-sources,.assistant-draft').forEach(el=>el.replaceChildren());if(field('task').value==='chat'){field('question').value='';field('consent').checked=false;setStatus('已开始新对话，之前的内容不会带入。');field('question').focus();}else setStatus('回答已清空，可以开始新的整理。');break;
        case'apply':apply();break;
        case'replace':apply(true);break;
        case'keep':c.host.querySelector('.assistant-replace').replaceChildren();break;
        case'copy':{const value=getDraft();await copy(`${value.title}\n\n${value.content}\n\n${value.tags.map(t=>'#'+t).join(' ')}`);break;}
        case'sources':{const childId=c.sentChild;reset();state.childId=childId;navigate('growth');document.querySelector('.chart-panel')?.nextElementSibling?.scrollIntoView({block:'start',behavior:'smooth'});break;}
      }
    }catch(error){if(context===c)setStatus(error.message);toast(error.message);}
    finally{if(button.isConnected)button.disabled=false;}
  }
  document.addEventListener('click',handle);
  document.addEventListener('change',event=>{
    if(!context)return;
    if(context.inline&&event.target===context.form.elements.childId){reset();return;}
    if(context.inline&&event.target===context.form.elements.recordTime){reset();return;}
    if(event.target.matches('[data-ai-field="task"],[data-ai-field="child"],[data-ai-field="from"],[data-ai-field="to"],[data-ai-field="date"]')&&context.host.contains(event.target)){
      if(active)stop();draft=null;forgetChat(context);
      context.host.querySelectorAll('.assistant-answer,.assistant-facts,.assistant-sources,.assistant-draft').forEach(el=>el.replaceChildren());
      const task=field('task').value;context.task=task;
      context.host.querySelector('.assistant-measure-dates').hidden=task!=='measurements';context.host.querySelector('.assistant-diary-options').hidden=task!=='diary';
      context.host.querySelector('.assistant-chat').hidden=task!=='chat';
      context.host.querySelector('[data-ai-action="send"]').textContent=sendLabel(task);
      context.host.querySelector('[data-ai-action="clear"]').textContent=task==='chat'?'新对话':'清空回答';
      context.host.querySelector('.assistant-question-label').textContent=questionLabel(task);
      field('question').placeholder=placeholder(task);
      context.host.querySelector('.assistant-scope').textContent=scope(task);
      if(event.target===field('child')||event.target===field('task'))field('question').value='';
      field('consent').checked=false;setStatus('范围已变化，请确认后重新生成。');
    }
  });
  document.addEventListener('keydown',event=>{
    if(!event.isComposing&&(event.ctrlKey||event.metaKey)&&event.key==='Enter'&&context?.host.contains(event.target)&&event.target===field('question')){
      event.preventDefault();const button=context.host.querySelector('[data-ai-action="send"]');if(!active&&!button.disabled)button.click();
    }
  });
  dialog.addEventListener('cancel',event=>{event.preventDefault();reset();});
  document.querySelector('#modal').addEventListener('close',()=>{if(context?.inline)reset();});
  launcher.addEventListener('click',()=>open({task:'chat',trigger:launcher}));
  window.GrowthAssistant={open,reset,sync};sync();
})();
