'use strict';
(() => {
  pages.reminders=['待办提醒','把要做的小事记下来，让拾光按时提醒你'];
  let items=[],owner=null,refreshVersion=0,pollTimer=null;
  const yes=value=>value===true||Number(value)===1;
  const labels={ACTIVE:'进行中',PAUSED:'已暂停，请编辑时间重新开启',SUSPENDED:'自动暂停，等待账号／邮箱／孩子恢复可用',COMPLETE:'已完成',FAILED:'发送失败，请编辑后重新安排',QUEUED:'等待发送',SENDING:'发送中',SENT:'已提交邮箱服务器',CANCELLED:'已取消'};
  const cadence={ONCE:'仅一次',DAILY:'每天',WEEKLY:'每周'};
  const time=value=>value?new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(new Date(Number(value))):'—';
  const inputTime=value=>{
    const parts=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date(Number(value)));
    const p=Object.fromEntries(parts.map(x=>[x.type,x.value]));return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`;
  };
  const button=(label,action,id='',extra='')=>`<button type="button" class="btn secondary small" data-reminder-action="${action}" data-id="${id}" ${extra}>${label}</button>`;
  const codeFields=(email='',registration=false)=>`<label class="field"><span>QQ 邮箱</span><input name="email" type="email" required maxlength="100" autocomplete="email" value="${esc(email)}" placeholder="你的邮箱@qq.com"><small>目前只支持 @qq.com；验证码在当前浏览器会话使用，5 分钟内有效。</small></label><div class="email-code-row"><label class="field"><span>6 位验证码</span><input name="${registration?'emailCode':'code'}" required pattern="[0-9]{6}" maxlength="6" inputmode="numeric" autocomplete="one-time-code"></label><button type="button" class="btn secondary small" data-email-action="send" data-purpose="${registration?'register':'bind'}">发送验证码</button></div>`;
  function mountRegistration(form){
    if(!form||form.querySelector('[data-registration-email]'))return;
    const section=document.createElement('section');section.dataset.registrationEmail='1';section.innerHTML=codeFields('',true);
    form.querySelector('button[type="submit"]').before(section);
  }
  async function emailForm(){
    const userId=state.data?.user.id;if(!userId||window.GrowthProfile?.busy())return;
    const data=await api('/account/email');if(state.data?.user.id!==userId)return;
    openModal('QQ 邮箱与提醒设置',`<p class="modal-intro">当前邮箱：${data.email?esc(data.email)+'（已验证）':'尚未绑定'}。更换后旧邮箱不再接收新的提醒。</p><form data-email-form="bind">${codeFields()}<label class="field"><span>当前登录密码</span><input name="currentPassword" type="password" required maxlength="72" autocomplete="current-password"></label><button type="submit" class="btn">验证并保存邮箱</button></form><section class="profile-section"><h3>邮件接收设置</h3><form data-email-form="preference"><label class="deletion-consent"><input type="checkbox" name="enabled" ${yes(data.emailEnabled)?'checked':''}><span>接收我设置的待办提醒</span></label><p class="form-hint">绑定／更换邮箱会取消已排队邮件。受影响的一次性提醒需重新安排；每天／每周提醒继续使用已验证的新邮箱。</p><button type="submit" class="btn secondary">保存接收设置</button></form></section>`);
  }
  async function sendCode(buttonEl){
    const form=buttonEl.closest('form'),email=form.elements.email;
    if(!email.reportValidity())return;
    const registering=buttonEl.dataset.purpose==='register',phone=registering?form.elements.phone:null;
    if(phone&&!phone.reportValidity())return;
    const body={email:email.value.trim(),...(phone?{phone:phone.value}:{})};
    await api(registering?'/auth/email-code':'/account/email-code',{method:'POST',body});
    toast('验证码已提交发送，请检查收件箱和垃圾邮件');
    buttonEl.dataset.until=String(Date.now()+60000);
    const tick=()=>{
      if(!buttonEl.isConnected){clearInterval(timer);return;}
      const left=Math.max(0,Math.ceil((Number(buttonEl.dataset.until)-Date.now())/1000));buttonEl.disabled=left>0;buttonEl.textContent=left?`${left} 秒后重发`:'发送验证码';if(!left)clearInterval(timer);
    };
    const timer=setInterval(tick,500);tick();
  }
  function page(){
    clearTimeout(pollTimer);
    queueMicrotask(refresh);
    return heading()+`<div data-reminder-host><p class="muted">正在读取你的提醒…</p></div>`;
  }
  function scheduleRefresh(){
    clearTimeout(pollTimer);
    const host=document.querySelector('[data-reminder-host]');if(!host||!state.data?.user.id)return;
    pollTimer=setTimeout(()=>{
      if(!host.isConnected)return;
      // 编辑弹窗和键盘操作期间不替换列表；隐藏页面不发轮询请求。
      if(document.hidden||document.querySelector('#modal[open]')||host.querySelector(':focus-visible')){scheduleRefresh();return;}
      refresh(true);
    },10000);
  }
  async function refresh(silent=false){
    clearTimeout(pollTimer);
    const host=document.querySelector('[data-reminder-host]'),userId=state.data?.user.id;if(!host||!userId)return;const version=++refreshVersion;
    try{
      const data=await api('/reminders');if(!host.isConnected||state.data?.user.id!==userId||version!==refreshVersion)return;
      items=data.items;owner=userId;
      host.innerHTML=`<div class="reminder-banner">${data.account.email?`收件邮箱：${esc(data.account.email)} · ${yes(data.account.emailEnabled)?'已开启':'已关闭'}邮件提醒`:'先绑定并验证 QQ 邮箱，再设置提醒。'}<br><small>时间均为北京时间。邮件可能延迟，以发送记录为准；只向本人已验证邮箱发送。</small>${!data.mailConfigured?'<p>邮件服务尚未配置，请联系管理员。</p>':''}</div><div class="reminder-toolbar">${button('新增提醒','new')}${button('刷新状态','refresh')}<button class="btn secondary small" type="button" data-email-action="open">邮箱与接收设置</button></div><div class="reminder-list">${items.map(r=>`<article class="reminder-item"><h3>${esc(r.title)}</h3><div class="reminder-meta">${esc(cadence[r.cadence])} · ${esc(labels[r.state]||r.state)}${r.childName?' · '+esc(r.childName):''}<br>首次计划：${time(r.startMs)}　下次计划：${time(r.nextMs)}<br>最近投递：${esc(labels[r.deliveryStatus]||'尚未发送')}</div>${r.content?`<p>${esc(r.content)}</p>`:''}<div class="reminder-actions">${button('编辑／重新安排','edit',r.id)}${r.state==='ACTIVE'||r.state==='SUSPENDED'?button('暂停','pause',r.id):''}${r.state!=='COMPLETE'?button('标记完成','complete',r.id):''}${button('发送记录','history',r.id)}${button('删除','delete',r.id)}</div></article>`).join('')||empty('还没有待办提醒','记录一件需要按时完成的小事，例如测量身高或接种疫苗。','','','clock')}</div>`;
    }catch(error){if(!silent&&host.isConnected&&state.data?.user.id===userId&&version===refreshVersion){host.innerHTML='<p class="muted">提醒加载失败，请刷新重试。</p>';showError(error);}}
    finally{scheduleRefresh();}
  }
  function edit(id){
    const r=id?items.find(x=>x.id===id):null;if(id&&(!r||owner!==state.data?.user.id))return toast('列表已变化，请刷新后重试');
    const active=state.data.children.some(c=>c.id===r?.childId);
    openModal(r?'编辑待办提醒':'新增待办提醒',`<p class="modal-intro">只发送至本人已验证的 QQ 邮箱。每天／每周均从所选时间开始重复。</p><form data-reminder-form ${r?`data-id="${r.id}"`:''}><label class="field"><span>需要做什么</span><input name="title" maxlength="80" required value="${esc(r?.title)}" placeholder="例如：给孩子测量身高"></label><label class="field"><span>备注（选填）</span><textarea name="content" maxlength="1000" rows="3">${esc(r?.content)}</textarea></label><div class="form-row"><label class="field"><span>提醒时间（北京时间）</span><input name="time" type="datetime-local" required value="${inputTime(r&&r.nextMs>Date.now()?r.nextMs:Date.now()+3600000)}"></label><label class="field"><span>重复</span><select name="cadence">${Object.entries(cadence).map(([key,label])=>`<option value="${key}" ${r?.cadence===key?'selected':''}>${label}</option>`).join('')}</select></label></div><label class="field"><span>关联孩子（选填）</span><select name="childId" ${r?.childId&&!active?'required':''}><option value="personal">个人待办，不关联孩子</option>${r?.childId&&!active?`<option value="" selected disabled>原档案已归档，请恢复档案或改为个人待办</option>`:''}${state.data.children.map(c=>`<option value="${c.id}" ${c.id===r?.childId?'selected':''}>${esc(c.name)}</option>`).join('')}</select></label><p class="form-hint">编辑保存后会取消旧的待发送邮件，按新时间重新安排；已发出的邮件无法撤回。</p>${formActions('保存提醒')}</form>`);
  }
  async function history(id){
    const userId=state.data?.user.id;const rows=await api(`/reminders/${id}/history`);if(state.data?.user.id!==userId)return;
    openModal('最近 30 次发送记录',`<div class="table-wrap"><table class="data-table"><thead><tr><th>计划时间</th><th>状态</th><th>尝试次数</th></tr></thead><tbody>${rows.map(r=>`<tr><td>${time(r.scheduledMs)}</td><td>${esc(labels[r.status]||r.status)}${r.status==='FAILED'?'<br><small>请检查邮箱配置，或重新安排提醒</small>':''}</td><td>${r.attempts}</td></tr>`).join('')||'<tr><td colspan="3">尚无发送记录</td></tr>'}</tbody></table></div><p class="form-hint">“已提交邮箱服务器”不代表已送达或已阅读。服务器确认结果不明确时，重试可能造成重复邮件。</p>`);
  }
  document.addEventListener('click',event=>{
    const emailButton=event.target.closest('[data-email-action]');
    if(emailButton&&!emailButton.disabled){
      if(emailButton.dataset.emailAction==='open')busy(emailButton,emailForm);
      else{
        emailButton.disabled=true;sendCode(emailButton).catch(e=>showError(e,emailButton.closest('form'))).finally(()=>{if(emailButton.isConnected&&!(Number(emailButton.dataset.until)>Date.now()))emailButton.disabled=false;});
      }
      return;
    }
    const target=event.target.closest('[data-reminder-action]');if(!target)return;
    const id=Number(target.dataset.id),action=target.dataset.reminderAction;
    busy(target,async()=>{
      if(action==='refresh')return refresh();if(action==='new')return edit();if(action==='edit')return edit(id);if(action==='history')return history(id);
      confirmDialog(action==='delete'?'删除这条提醒？':action==='pause'?'暂停这条提醒？':'将提醒标记为完成？','之后尚未发出的邮件会取消，已开始发送的邮件可能仍会到达。',async()=>{
        await api(`/reminders/${id}`+(action==='delete'?'':'/state'),{method:action==='delete'?'DELETE':'PUT',...(action==='delete'?{}:{body:{state:action==='pause'?'PAUSED':'COMPLETE'}})});
        closeModal();await refresh();toast('提醒已更新');
      },action==='delete'?'删除提醒':'确认');
    });
  });
  document.addEventListener('submit',event=>{
    const form=event.target.closest('form[data-email-form],form[data-reminder-form]');if(!form)return;event.preventDefault();
    const input=Object.fromEntries(new FormData(form));
    busy(form.querySelector('button[type="submit"]'),async()=>{
      if(form.hasAttribute('data-reminder-form')){
        const id=form.dataset.id;
        await api('/reminders'+(id?'/'+id:''),{method:id?'PUT':'POST',body:{...input,childId:input.childId&&input.childId!=='personal'?Number(input.childId):null}});
        closeModal();await refresh();toast('提醒已保存');
      }else if(form.dataset.emailForm==='bind'){
        await api('/account/email',{method:'PUT',body:input});form.reset();toast('邮箱已验证并保存');await emailForm();await refresh();
      }else{
        await api('/account/email-preference',{method:'PUT',body:{enabled:input.enabled==='on'}});toast('接收设置已保存');await refresh();
      }
    },form);
  });
  window.GrowthEmailUI={mountRegistration};window.GrowthReminders={page};
})();
