'use strict';
(() => {
  const modal=document.querySelector('#modal');
  let saving=false,previewUrl=null,account=null;
  const releasePreview=()=>{if(previewUrl){URL.revokeObjectURL(previewUrl);previewUrl=null;}};
  const submit=label=>`<button class="btn small" type="submit">${label}</button>`;
  const currentPassword=()=>'<label class="field"><span>当前登录密码</span><input name="currentPassword" type="password" autocomplete="current-password" required maxlength="72"></label>';
  function preview(){
    const target=modal.querySelector('[data-profile-preview]');
    if(!target||!account)return;
    target.innerHTML=avatar(account.nickname,'large peach',account.id);
    const remove=modal.querySelector('[data-profile-action="remove-avatar"]');if(remove)remove.hidden=!account.hasAvatar;
  }
  async function open(){
    if(saving)return;
    const userId=state.data?.user.id;if(!userId)return;
    const loaded=await api('/account');
    if(state.data?.user.id!==userId)return;
    releasePreview();account=loaded;
    openModal('我的个人资料',`<div class="profile-intro"><div data-profile-preview>${avatar(account.nickname,'large peach',account.id)}</div><div><h3 data-profile-name>${esc(account.nickname)}</h3><p>${esc(state.data.family.name)} · ${admin()?'家庭管理员':'家庭成员'}</p></div></div>
      <p class="modal-intro">让家人一眼认出你。以下信息仅修改你自己的账号。</p>
      <section class="profile-section"><h3>头像</h3><form data-profile-form="avatar"><label class="field"><span>选择新头像</span><input name="file" type="file" accept=".jpg,.jpeg,.png,image/jpeg,image/png" required><small>JPG / PNG，最多 2 MB、800 万像素；自动居中裁为方形。存入家庭私有 MinIO。</small></label><div class="profile-actions">${submit('保存头像')}<button class="btn secondary small" type="button" data-profile-action="remove-avatar" ${account.hasAvatar?'':'hidden'}>恢复默认头像</button></div></form></section>
      <section class="profile-section"><h3>昵称</h3><form data-profile-form="nickname"><label class="field"><span>家人怎么称呼你</span><input name="nickname" required maxlength="40" autocomplete="nickname" value="${esc(account.nickname)}"></label>${submit('保存昵称')}</form></section>
      ${account.demo?'<div class="profile-note">当前是临时示例账号，可以体验昵称和头像修改。手机号和密码需注册正式账号后设置。</div>':`
      <details class="profile-section"><summary>更换手机号 <small>当前：${esc(account.phone)}</small></summary><form data-profile-form="phone"><label class="field"><span>新手机号</span><input name="phone" type="tel" autocomplete="tel" inputmode="numeric" pattern="1[0-9]{10}" maxlength="11" required placeholder="请输入新的11位手机号"></label>${currentPassword()}<p class="form-hint">通过当前密码验证，尚未接入短信验证。修改后需用新手机号重新登录，其他旧登录会话也会失效。</p>${submit('确认更换手机号')}</form></details>
      <details class="profile-section"><summary>修改登录密码</summary><form data-profile-form="password">${currentPassword()}<div class="form-row"><label class="field"><span>新密码</span><input name="newPassword" type="password" autocomplete="new-password" required minlength="8" maxlength="60"></label><label class="field"><span>确认新密码</span><input name="confirmPassword" type="password" autocomplete="new-password" required minlength="8" maxlength="60"></label></div><p class="form-hint">8–60 个字符，UTF-8 长度不超过 72 字节。修改成功后，所有旧登录会话失效，请重新登录。</p>${submit('确认修改密码')}</form></details>`}
      ${account.demo?'':`<section class="profile-section"><h3>注销账户</h3><p class="form-hint">申请后立即停用，12 小时内可以取消。${admin()?'你是管理员：到期将删除整个家庭及全部内容。':'到期清除个人资料，家庭共享记录将保留并显示“已注销用户”。'}</p><button type="button" class="btn secondary small" data-deletion-action="open">申请注销账户</button></section>`}
      ${account.demo?'':`<section class="profile-section"><h3>QQ 邮箱与提醒</h3><p class="form-hint">验证邮箱后，可以接收自己设置的待办提醒。</p><button type="button" class="btn secondary small" data-email-action="open">绑定／更换邮箱与接收设置</button></section>`}
      <p class="profile-status" role="status" aria-live="polite"></p><div class="form-actions">${btn('退出登录','logout','logout','secondary')}<button type="button" class="btn secondary" data-action="close">关闭</button></div>`);
  }
  async function perform(form,operation,after){
    if(saving)return;
    form.querySelector('.error-message')?.remove();
    const controls=[...modal.querySelectorAll('button,input,select,textarea')].filter(el=>!el.disabled);
    saving=true;controls.forEach(el=>el.disabled=true);
    const status=modal.querySelector('.profile-status');if(status)status.textContent='正在保存，请稍候…';
    const unlock=()=>{saving=false;controls.forEach(el=>{if(el.isConnected)el.disabled=false;});};
    let committed=false;
    try{
      await operation();committed=true;
      if(after)await after();
      unlock();if(status?.isConnected)status.textContent='修改已保存。';
    }catch(error){unlock();if(committed&&error.status!==401)error.message='修改已保存，但资料刷新失败，请刷新页面：'+error.message;if(status?.isConnected)status.textContent=committed?'修改已保存，资料刷新未完成。':'未完成保存，请查看提示。';showError(error,form);}
  }
  function signInAgain(phone,message){
    saving=false;
    closeModal();state.data=null;state.childId=null;state.demo=false;state.loginMode='login';renderLogin();
    const input=document.querySelector('form[data-form="login"] input[name="phone"]');if(input)input.value=phone;
    toast(message);
  }
  async function refreshProfile(){
    await load();account=await api('/account');
    if(!modal.querySelector('[data-profile-preview]'))return;
    // 保存昵称时保留尚未保存的头像选择及其预览。
    if(!modal.querySelector('form[data-profile-form="avatar"] input[name="file"]').files.length){releasePreview();preview();}
    modal.querySelector('[data-profile-name]').textContent=account.nickname;
  }
  document.addEventListener('submit',event=>{
    const form=event.target.closest('form[data-profile-form]');if(!form)return;
    event.preventDefault();if(saving)return;
    const values=Object.fromEntries(new FormData(form)),kind=form.dataset.profileForm;
    if(kind==='nickname')perform(form,async()=>{
      if(!values.nickname.trim())throw new Error('请输入昵称');
      await api('/account',{method:'PUT',body:{nickname:values.nickname.trim()}});
    },async()=>{await refreshProfile();form.elements.nickname.value=account.nickname;toast('昵称已更新');});
    if(kind==='avatar')perform(form,async()=>{
      const file=values.file;if(!file?.size||file.size>2*1024*1024)throw new Error('请选择不超过2 MB的头像图片');
      const data=new FormData();data.append('file',file);await api('/account/avatar',{method:'POST',body:data});
    },async()=>{form.reset();await refreshProfile();toast('头像已更新');});
    if(kind==='phone')perform(form,async()=>{
      if(values.phone===account.phone)throw new Error('新手机号与当前手机号相同');
      await api('/account/phone',{method:'PUT',body:values});
    },()=>signInAgain(values.phone,'手机号已更新，请使用新手机号重新登录'));
    if(kind==='password'){
      const phone=account.phone;
      perform(form,async()=>{
        if(values.newPassword!==values.confirmPassword)throw new Error('两次新密码不一致');
        if(new TextEncoder().encode(values.newPassword).length>72)throw new Error('密码UTF-8长度不能超过72字节，请缩短后重试');
        await api('/account/password',{method:'PUT',body:values});
      },()=>signInAgain(phone,'密码已修改，请使用新密码重新登录'));
    }
  });
  document.addEventListener('click',event=>{
    const button=event.target.closest('[data-profile-action="remove-avatar"]');if(!button||button.disabled||saving)return;
    const form=button.closest('form');perform(form,()=>api('/account/avatar',{method:'DELETE'}),async()=>{form.reset();await refreshProfile();toast('已恢复默认头像');});
  });
  document.addEventListener('change',event=>{
    const input=event.target;if(!input.matches('form[data-profile-form="avatar"] input[name="file"]'))return;
    releasePreview();const file=input.files[0];if(!file){preview();return;}
    if(file.size>2*1024*1024||!(/\.(jpe?g|png)$/i.test(file.name)||['image/jpeg','image/png'].includes(file.type))){input.value='';preview();toast('请选择不超过2 MB的JPG或PNG图片');return;}
    previewUrl=URL.createObjectURL(file);
    modal.querySelector('[data-profile-preview]').innerHTML=`<span class="avatar large peach"><img class="user-avatar-image" src="${previewUrl}" alt="待保存头像预览"></span>`;
  });
  document.addEventListener('error',event=>{if(event.target.matches?.('img.user-avatar-image'))event.target.remove();},true);
  modal.addEventListener('cancel',event=>{if(saving){event.preventDefault();toast('个人资料正在保存，请稍候');}});
  modal.addEventListener('close',()=>{releasePreview();account=null;modal.querySelectorAll('[data-profile-form] input[type="password"]').forEach(input=>input.value='');});
  window.GrowthProfile={open,busy:()=>saving};
})();
