'use strict';
const escapeText=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
async function openSharedMemory(){
  const root=document.querySelector('#shared-record');
  const token=new URLSearchParams(location.search).get('token');
  try{
    if(!token||!/^[a-f0-9]{64}$/.test(token))throw new Error('这条分享链接不完整，请向家人获取新的链接。');
    const response=await fetch('/api/growth/public/'+token,{credentials:'same-origin'});
    const record=await response.json();
    if(!response.ok)throw new Error(record.message||'这段分享已经失效。');
    const images=record.media.map(m=>`<div class="watermarked"><img src="/api/growth/public/${token}/media/${m.id}" alt="带水印的成长照片预览"><span class="watermark">拾光 · 家庭珍藏</span></div>`).join('');
    const illustration=['park','bike','baking','night','birthday','sea'].includes(record.illustration)?`<div class="watermarked"><img src="/growth/${record.illustration}.svg" alt="示例插画"><span class="watermark">拾光 · 示例插画</span></div>`:'';
    root.innerHTML=`<article class="panel"><span class="eyebrow">A LITTLE MOMENT TO SHARE</span><h1>${escapeText(record.title)}</h1><p class="detail-meta">${escapeText(record.recordTime)} · ${record.recordType==='milestone'?'一个闪光的里程碑':'日常里的一点美好'}</p>${images||(record.hiddenVideoCount?'':illustration)}${record.hiddenVideoCount?'<p class="form-hint">这条记录中的视频仅对家庭成员开放，未包含在本次分享中。</p>':''}<p class="detail-content">${escapeText(record.content)}</p><p class="form-hint">这段分享将于 ${escapeText(record.expiresAt.replace('T',' ').slice(0,16))} 到期。</p></article>`;
  }catch(error){root.innerHTML=`<div class="error-message"><h2>把美好，留在回忆里</h2><p>${escapeText(error.message||'暂时无法打开，请稍后再试。')}</p><a href="/">前往拾光首页</a></div>`;}
}
openSharedMemory();
