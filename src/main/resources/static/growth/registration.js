'use strict';
// 注册取得本人会话后复用私有头像接口，不开放匿名文件上传。
(() => {
  let previewUrl=null,revision=0,saving=false;
  function reset(){
    revision++;
    if(previewUrl)URL.revokeObjectURL(previewUrl);
    previewUrl=null;
  }
  function preview(form){
    const target=form.querySelector('[data-registration-preview]');if(!target)return;
    target.innerHTML=avatar(form.elements.nickname.value.trim()||'家','large peach');
    if(previewUrl){
      const image=document.createElement('img');image.src=previewUrl;image.alt='待上传头像预览';image.className='user-avatar-image';
      target.firstElementChild.append(image);
    }
    form.querySelector('[data-registration-action="remove-avatar"]').hidden=!previewUrl;
  }
  function mount(form){
    window.GrowthEmailUI?.mountRegistration(form);
    if(!form||form.querySelector('[data-registration-preview]'))return;
    reset();
    const section=document.createElement('section');section.className='registration-avatar';
    section.innerHTML=`<div class="registration-avatar-top"><div data-registration-preview></div><div><h3>你的头像 <small>选填</small></h3><p>不上传时，使用昵称首字作为默认头像。</p><button type="button" class="text-btn" data-registration-action="remove-avatar" hidden>使用默认头像</button></div></div><label class="field"><span>选择头像</span><input name="avatarFile" type="file" accept=".jpg,.jpeg,.png,image/jpeg,image/png"><small>JPG / PNG，最多 2 MB、800 万像素；自动居中裁为方形。</small></label><p class="registration-status" role="status" aria-live="polite"></p>`;
    form.querySelector('button[type="submit"]').before(section);preview(form);
  }
  async function validate(file){
    if(!file)return;
    if(!file.size||file.size>2*1024*1024)throw new Error('头像请选择不超过 2 MB 的 JPG 或 PNG 图片');
    const header=new Uint8Array(await file.slice(0,8).arrayBuffer());
    const png=[137,80,78,71,13,10,26,10].every((value,index)=>header[index]===value);
    const jpeg=header[0]===255&&header[1]===216&&header[2]===255;
    if(!png&&!jpeg)throw new Error('头像仅支持有效的 JPG 或 PNG 图片');
    const url=URL.createObjectURL(file),image=new Image();
    try{
      await new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=()=>reject(new Error('头像图片无法读取，请换一张图片'));image.src=url;});
      if(!image.naturalWidth||!image.naturalHeight||image.naturalWidth*image.naturalHeight>8_000_000)throw new Error('头像请缩小到 800 万像素以内');
    }finally{image.src='';URL.revokeObjectURL(url);}
  }
  document.addEventListener('change',async event=>{
    const input=event.target;if(!input.matches('form[data-form="register"] input[name="avatarFile"]')||saving)return;
    const form=input.form,file=input.files[0];reset();const current=revision;preview(form);
    const status=form.querySelector('.registration-status');status.textContent=file?'正在读取头像…':'';
    try{
      await validate(file);
      if(current!==revision||!form.isConnected)return;
      if(file)previewUrl=URL.createObjectURL(file);
      preview(form);status.textContent=file?'头像将在注册成功后保存。':'';
    }catch(error){
      if(current!==revision||!form.isConnected)return;
      input.value='';preview(form);status.textContent=error.message+'，也可以直接使用默认头像。';
    }
  });
  document.addEventListener('input',event=>{
    if(event.target.matches('form[data-form="register"] input[name="nickname"]'))preview(event.target.form);
  });
  document.addEventListener('click',event=>{
    const button=event.target.closest('[data-registration-action="remove-avatar"]');if(!button||saving)return;
    const form=button.closest('form');form.elements.avatarFile.value='';reset();preview(form);form.querySelector('.registration-status').textContent='将使用默认头像。';
  });
  async function submit(form,input,button){
    if(saving)return;mount(form);
    const file=form.elements.avatarFile.files[0],label=button.innerHTML;
    const controls=[...form.closest('.login-form').querySelectorAll('button,input')].filter(control=>!control.disabled);
    saving=true;
    controls.forEach(control=>control.disabled=true);
    let registered=false,avatarError=null;
    try{
      button.textContent='正在创建账号…';
      await validate(file); // 创建账号前拦截无效文件；后端仍独立校验实际内容。
      const {phone,password,nickname,familyName,inviteCode,email,emailCode}=input;
      await api('/auth/register',{method:'POST',body:{phone,password,nickname,familyName,inviteCode,email,emailCode}});
      registered=true;form.elements.password.value='';
      if(file){
        button.textContent='正在保存头像…';
        const data=new FormData();data.append('file',file);
        try{await api('/account/avatar',{method:'POST',body:data});}catch(error){avatarError=error;}
      }
      reset();state.page='timeline';state.childId=null;await load();
      if(avatarError&&!state.data.user.hasAvatar){
        openModal('账号已注册成功',`<p>头像上传未完成，当前使用默认头像。你可以进入「我的个人资料」重新上传，无需再次注册。</p><p class="error-message">${esc(avatarError.message)}</p><div class="form-actions">${btn('知道了','close','check')}</div>`);
      }else toast('注册成功，欢迎来到我们的家庭空间');
    }catch(error){
      if(!registered)throw error;
      // 注册已成功，后续加载失败时改为登录入口，避免重复创建账号。
      reset();state.data=null;state.loginMode='login';renderLogin();
      const login=document.querySelector('form[data-form="login"]');login.elements.phone.value=input.phone;
      showError(new Error('账号已注册成功，请直接登录。'+(avatarError?'头像保存未确认，可登录后在个人资料中检查。':'')+'家庭空间暂未加载完成：'+error.message),login);
    }finally{
      saving=false;controls.forEach(control=>{if(control.isConnected)control.disabled=false;});
      if(button.isConnected)button.innerHTML=label;
    }
  }
  window.addEventListener('pagehide',reset);
  window.GrowthRegistration={mount,reset,submit};
  mount(document.querySelector('form[data-form="register"]'));
})();
