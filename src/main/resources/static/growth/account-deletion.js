'use strict';
(() => {
  const modal=document.querySelector('#modal');
  let timer=null,working=false;
  const passwordField=()=>'<label class="field"><span>原账号登录密码</span><input name="password" type="password" required maxlength="72" autocomplete="current-password"></label>';
  const phoneField=phone=>`<label class="field"><span>原账号手机号</span><input name="phone" type="tel" required maxlength="64" autocomplete="username" value="${esc(phone)}"></label>`;
  function stopTimer(){clearInterval(timer);timer=null;}
  function loginScreen(phone=''){
    stopTimer();window.GrowthAssistant?.reset();
    state.data=null;state.childId=null;state.demo=false;state.loginMode='login';renderLogin();
    const input=document.querySelector('form[data-form="login"] input[name="phone"]');if(input)input.value=phone;
  }
  async function open(){
    if(working||window.GrowthProfile?.busy())return;
    const account=await api('/account'),family=admin();
    openModal('申请注销账户',`<div class="deletion-warning"><p><strong>${family?'管理员注销会删除整个家庭':'注销你的个人账号'}</strong></p><p>${family?'家庭将立即停用，所有家人暂时无法访问。12 小时到期后，孩子档案、成长记录、照片视频、测量、接种、分享、回收站及操作记录将永久清理。其他成员保留个人账号，可重新创建或加入家庭。':'你的账号将立即停用。12 小时到期后清除手机号、密码、头像和昵称；你贡献的家庭共享记录保留，作者显示为“已注销用户”。'}</p><p>只有发起注销的本人可以使用原账号密码取消。到期后不能恢复，请提前保存需要的资料。</p></div><form data-deletion-form="apply" data-phone="${esc(account.phone)}">${passwordField()}<label class="deletion-consent"><input name="confirmed" type="checkbox" required><span>我已了解，注销后有 12 小时恢复期，到期将永久清理。</span></label>${family?'<label class="deletion-consent"><input name="familyConfirmed" type="checkbox" required><span>我确认注销整个家庭及其中的全部内容，并已告知家人。</span></label>':''}<div class="form-actions"><button type="button" class="btn secondary" data-action="close">保留账号</button><button type="submit" class="btn danger">确认申请注销</button></div></form>`);
  }
  function pending(details,phone=''){
    loginScreen(phone);
    const family=details.scope==='FAMILY';
    openModal('已申请注销 · 12 小时内可取消',`<p class="modal-intro">${family?'该家庭已由管理员申请注销，家庭空间暂时停用。':'该账号已申请注销，暂时无法登录或重复注册。'}</p><div class="deletion-clock"><span>距离不可恢复还剩</span><strong data-deletion-clock role="timer">--:--:--</strong><small>恢复截止：${esc(new Date(details.deadlineMs).toLocaleString('zh-CN'))}</small></div><p data-deletion-expired class="deletion-warning" hidden>恢复期限已结束，无法再取消注销。资料将在后台清理；完成后可使用原手机号重新注册。</p>${details.canCancel?`<form data-deletion-form="cancel"><p>是否取消注销？请输入原账号的手机号和密码验证身份。注册时填写的新密码不能用于恢复。</p>${phoneField(phone)}${passwordField()}<button type="submit" class="btn">取消注销，保留资料</button></form>`:`<p class="form-hint">${details.serverNowMs<details.deadlineMs?'只能由发起注销的账号本人取消，请联系该家庭管理员。':'到期后不能恢复，清理任务会自动重试。'}</p>`}<div class="form-actions"><button type="button" class="btn secondary" data-action="close">返回登录页</button></div>`);
    // 根据服务端剩余时间累计，避免本机时区或系统时间偏差改变恢复窗口。
    const remaining=details.deadlineMs-details.serverNowMs,start=performance.now();
    const tick=()=>{
      const target=modal.querySelector('[data-deletion-clock]');if(!modal.open||!target){stopTimer();return;}
      const seconds=Math.max(0,Math.ceil((remaining-(performance.now()-start))/1000));
      target.textContent=[Math.floor(seconds/3600),Math.floor(seconds/60)%60,seconds%60].map(n=>String(n).padStart(2,'0')).join(':');
      if(seconds===0){
        const expired=modal.querySelector('[data-deletion-expired]');expired.hidden=false;
        expired.textContent=details.ownRequest?'恢复期限已结束，无法再取消注销。资料将在后台清理；完成后可使用原手机号重新注册。':'家庭恢复期限已结束，资料将在后台清理。你的个人账号保留，清理完成后请使用原账号登录并选择新家庭。';
        const form=modal.querySelector('[data-deletion-form="cancel"]');if(form)form.hidden=true;stopTimer();
      }
    };
    tick();if(remaining>0)timer=setInterval(tick,500);
  }
  function rehome(phone){
    loginScreen(phone);
    openModal('为你的账号选择一个家',`<p class="modal-intro">你的个人账号仍然保留，目前没有所属家庭。验证原密码后，可以重新创建家庭，也可以用邀请码加入家人。</p><form data-deletion-form="rehome">${phoneField(phone)}${passwordField()}<label class="field"><span>新家庭名称（选填）</span><input name="familyName" maxlength="60" placeholder="我们的幸福小家"></label><label class="field"><span>家庭邀请码（选填）</span><input name="inviteCode" maxlength="64" placeholder="填写则加入已有家庭，留空则创建家庭"></label><button type="submit" class="btn">创建／加入家庭</button></form>`);
  }
  function handle(error,form){
    const data=error.details;if(!data)return false;
    const phone=data.phone||form?.elements?.phone?.value||'';
    if(data.code==='ACCOUNT_DELETION_PENDING'){pending(data,phone);return true;}
    if(data.code==='FAMILY_REQUIRED'){rehome(phone);return true;}
    return false;
  }
  document.addEventListener('click',event=>{
    const button=event.target.closest('[data-deletion-action="open"]');if(button)busy(button,open);
  });
  document.addEventListener('submit',event=>{
    const form=event.target.closest('form[data-deletion-form]');if(!form)return;
    event.preventDefault();if(working)return;
    const input=Object.fromEntries(new FormData(form)),kind=form.dataset.deletionForm;
    working=true;const controls=[...form.querySelectorAll('input,button')];controls.forEach(el=>el.disabled=true);
    (async()=>{
      try{
        if(kind==='apply'){
          const data=await api('/account/deletion',{method:'POST',body:{password:input.password,confirmed:input.confirmed==='on',familyConfirmed:input.familyConfirmed==='on'}});
          form.reset();pending(data,form.dataset.phone);
        }else if(kind==='cancel'){
          await api('/auth/cancel-deletion',{method:'POST',body:{phone:input.phone,password:input.password}});
          form.reset();closeModal();loginScreen(input.phone);toast('已取消你的注销申请，请重新登录');
        }else if(kind==='rehome'){
          await api('/auth/rehome',{method:'POST',body:input});
          form.reset();closeModal();state.page='timeline';await load();toast('欢迎回到家庭空间');
        }
      }catch(error){showError(error,form);}
      finally{working=false;controls.forEach(el=>{if(el.isConnected)el.disabled=false;});}
    })();
  });
  modal.addEventListener('close',stopTimer);
  window.addEventListener('pagehide',stopTimer);
  window.GrowthAccountDeletion={handle};
})();
