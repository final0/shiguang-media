'use strict';
// 所有用户文字都经过 escapeHtml；身份、角色和家庭归属以服务端校验为准。
const $ = (s, root = document) => root.querySelector(s);
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const paths = {
  home:'M3 10 12 3l9 7M5 9v11h5v-6h4v6h5V9',
  timeline:'M5 3v18M3 6h4M3 12h4M3 18h4M11 5h9v4h-9zM11 15h9v4h-9z',
  image:'M4 4h16v16H4zM4 16l5-5 4 4 3-3 4 4M15 8h.01',
  video:'M3 5h12v14H3zM15 10l6-4v12l-6-4M7 9l4 3-4 3Z',
  chart:'M4 3v17h17M7 14l4-5 4 3 5-7',
  star:'m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9Z',
  people:'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M18 15a4 4 0 0 1 4 4v2M16 3a4 4 0 0 1 0 8M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
  trash:'M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7',
  settings:'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1Z',
  plus:'M12 5v14M5 12h14',
  arrow:'M4 12h16m-6-6 6 6-6 6',
  chevron:'m9 5 7 7-7 7',
  down:'m6 9 6 6 6-6',
  search:'M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  bell:'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4',
  heart:'M20 5c-3-3-6-1-8 1-2-2-5-4-8-1-5 5 3 11 8 15 5-4 13-10 8-15Z',
  lock:'M6 10h12v11H6zM8 10V6a4 4 0 0 1 8 0v4',
  leaf:'M5 19C1 8 10 3 21 3c0 12-4 18-12 15M4 21l11-11',
  comment:'M21 11a9 9 0 0 1-9 9c-2 0-3-.4-4-1l-5 2 1-5a9 9 0 1 1 17-5Z',
  book:'M12 5C8 3 5 3 2 4v15c3-1 6-1 10 1 4-2 7-2 10-1V4c-3-1-6-1-10 1v15',
  calendar:'M4 5h16v16H4zM8 3v4M16 3v4M4 10h16',
  clock:'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0M12 7v5l3 2',
  upload:'M12 16V3m-5 5 5-5 5 5M4 15v6h16v-6',
  edit:'m15 4 5 5M4 16 16 4a3 3 0 0 1 4 4L8 20H4Z',
  close:'m6 6 12 12M6 18 18 6',
  share:'M16 6 8 10M8 14l8 4M8 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0M22 5a3 3 0 1 1-6 0 3 3 0 0 1 6 0M22 19a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  restore:'M3 10a9 9 0 1 1 1 8M3 4v6h6M12 7v5l3 2',
  download:'M12 3v13m-5-5 5 5 5-5M4 17v4h16v-4',
  logout:'M9 3H4v18h5M10 12h11m-5-5 5 5-5 5',
  menu:'M4 6h16M4 12h16M4 18h16',
  check:'m5 12 4 4 10-10',
  log:'M6 3h12v18H6zM9 7h6M9 11h6M9 15h4',
  sun:'M12 3V1M12 23v-2M3 12H1M23 12h-2M4 4l2 2M18 18l2 2M4 20l2-2M18 6l2-2M17 12a5 5 0 1 1-10 0 5 5 0 0 1 10 0'
};
const icon = name => `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="${paths[name] || paths.leaf}"/></svg>`;
const state = {data:null,page:'timeline',childId:null,filter:'all',month:'all',order:'new',query:'',metric:'height',loginMode:'login',demo:false};
const pages = {timeline:['成长时间线','把平凡的小日子，收藏成独一无二的故事'],gallery:['时光相册','有些瞬间，一张照片就能让人回到那一天'],growth:['成长数据','每一点小小的变化，都值得认真记录'],milestones:['成长里程碑','那些值得记住的，人生第一次'],family:['我们的家','爱，让每一个成长瞬间都有见证'],trash:['回收站','不小心遗落的回忆，还可以找回来'],settings:['空间设置','照顾好这个装满回忆的小空间'],notices:['消息与回忆','来自家人的回应，还有时光寄来的信'],logs:['操作日志','家庭空间的每一次改变，都有迹可循']};
const today = () => {const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;};
const dateText = (s, year=true) => s ? `${year ? s.slice(0,4)+'年' : ''}${Number(s.slice(5,7))}月${Number(s.slice(8,10))}日` : '—';
const timeText = s => s ? `${s.slice(0,10)} ${s.slice(11,16)}` : '';
const bytes = n => n < 1048576 ? `${(n/1024).toFixed(1)} KB` : n < 1073741824 ? `${(n/1048576).toFixed(1)} MB` : `${(n/1073741824).toFixed(1)} GB`;
const admin = () => state.data?.user.role === 'admin';
const selectedChild = () => state.data?.children.find(c=>c.id===state.childId);
const avatar = (name,cls='',userId=null) => {
  const user=userId===state.data?.user.id?state.data.user:state.data?.members.find(m=>m.userId===userId);
  return `<span class="avatar ${cls}">${esc((name||'家').slice(0,1))}${user?.hasAvatar?`<img src="/api/growth/users/${Number(userId)}/avatar" alt="${esc(name)}的头像" class="user-avatar-image" loading="lazy">`:''}</span>`;
};
const canEdit = r => admin() || r.userId === state.data.user.id;
const brand = () => '<div class="brand"><img src="/growth/sprout.svg" alt="拾光"><div><span class="brand-name">拾光</span><small>LITTLE MOMENTS</small></div></div>';
const btn = (label,action,ico='plus',cls='',extra='') => `<button class="btn ${cls}" data-action="${action}" ${extra}>${icon(ico)}${label}</button>`;
const childOptions = id => state.data.children.map(c=>`<option value="${c.id}" ${c.id===id?'selected':''}>${esc(c.name)}</option>`).join('');
const empty = (heading,text,action='',label='记下第一个瞬间',ico='leaf') => `<div class="empty-state">${icon(ico)}<h3>${heading}</h3><p>${text}</p>${action?btn(label,action):''}</div>`;
let toastTimer, confirmAction, objectUrls=[], mediaUploading=false;
function toast(message){const box=$('#toast');box.textContent=message;box.classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>box.classList.remove('visible'),4200);}
async function api(path,{method='GET',body}={}) {
  const multipart=body instanceof FormData;
  const response=await fetch('/api/growth'+path,{method,credentials:'same-origin',headers:{'X-Growth-Request':'1',...(body&&!multipart?{'Content-Type':'application/json'}:{})},body:body?(multipart?body:JSON.stringify(body)):undefined});
  const text=await response.text();let data;try{data=text?JSON.parse(text):null;}catch{data=null;}
  if(!response.ok){const error=new Error(data?.message || (response.status===413?'文件过大：图片最多 8 MB，视频最多 100 MB，每次合计最多 200 MB':'操作未完成，请稍后重试'));error.status=response.status;error.details=data;throw error;}
  return data;
}
function showError(error,form){
  if(window.GrowthAccountDeletion?.handle(error,form))return;
  if(error.status===401&&state.data){state.data=null;closeModal();renderLogin();toast(error.message);return;}
  if(form?.isConnected){let area=$('.error-message',form);if(!area){area=document.createElement('div');area.className='error-message';area.setAttribute('role','alert');form.prepend(area);}area.textContent=error.message||'网络连接失败，请确认服务已经启动';area.scrollIntoView({block:'nearest'});}
  else toast(error.message||'网络连接失败，请确认服务已经启动');
}
async function busy(button,work,form){if(button?.disabled)return;if(button)button.disabled=true;try{await work();}catch(e){showError(e,form);}finally{if(button?.isConnected)button.disabled=false;}}
async function load(){
  state.data=await api('/snapshot');
  if(!state.data.children.some(c=>c.id===state.childId))state.childId=state.data.children[0]?.id??null;
  state.demo=Boolean(state.data.demo);
  render();
}
function age(birthday){if(!birthday)return '';const now=new Date(today()+'T12:00:00'),birth=new Date(birthday+'T12:00:00');let months=(now.getFullYear()-birth.getFullYear())*12+now.getMonth()-birth.getMonth();if(now.getDate()<birth.getDate())months--;months=Math.max(0,months);return months<12?`${months}个月`:`${Math.floor(months/12)}岁${months%12?` ${months%12}个月`:''}`;}
function records(){let rows=state.data.records.filter(r=>r.childId===state.childId);if(state.page==='milestones'||state.filter==='milestone')rows=rows.filter(r=>r.recordType==='milestone');else if(state.filter==='diary')rows=rows.filter(r=>r.recordType==='diary');if(state.month!=='all')rows=rows.filter(r=>r.recordTime.startsWith(state.month));if(state.query.trim()){const q=state.query.trim().toLowerCase();rows=rows.filter(r=>[r.title,r.content,...r.tags].join(' ').toLowerCase().includes(q));}return state.order==='old'?[...rows].reverse():rows;}
const isVideo = media => media.mimeType?.startsWith('video/');
const mediaSummary = media => {const videos=media.filter(isVideo).length;const photos=media.length-videos;return [photos?`${photos} 张照片`:'',videos?`${videos} 个视频`:''].filter(Boolean).join(' · ');};
function cover(r){const photo=r.media?.find(m=>!isVideo(m));if(photo)return `/api/growth/media/${photo.id}`;if(r.media?.length)return null;return r.illustration&&['park','bike','baking','night','birthday','sea'].includes(r.illustration)?`/growth/${r.illustration}.svg`:null;}
function videoCover(){return `<div class="video-cover">${icon('video')}<span>让回忆，动起来</span><small>MP4 · 打开记录播放</small></div>`;}
function videoPlayer(url,label='成长记录视频'){return `<div class="video-player"><video controls playsinline preload="metadata" aria-label="${esc(label)}" src="${esc(url)}"></video><p class="video-error" hidden role="status">视频暂时无法播放，请检查登录状态与网络，或将文件转换为 H.264 / AAC 编码的 MP4 后重新上传。</p></div>`;}
function validateMediaFiles(files){if(files.length>6)throw new Error('每次最多选择 6 个照片或视频文件');let total=0;for(const f of files){const video=/\.mp4$/i.test(f.name)||f.type==='video/mp4';const image=['image/jpeg','image/png'].includes(f.type)||/\.(jpe?g|png)$/i.test(f.name);if(!video&&!image)throw new Error('仅支持 JPG、PNG 图片和 MP4 视频');if(!f.size||f.size>(video?100:8)*1024*1024)throw new Error(video?'每个视频须大于 0 且不超过 100 MB':'每张图片须大于 0 且不超过 8 MB');total+=f.size;}if(total>200*1024*1024)throw new Error('本次照片和视频总大小不能超过 200 MB');}
function recordCard(r){
  const source=cover(r),videos=r.media.filter(isVideo);
  return `<article class="record-card"><button class="record-image" data-action="detail" data-id="${r.id}" aria-label="打开${esc(r.title)}">${source?`<img src="${source}" alt="${esc(r.title)}" loading="lazy">`:videos.length?videoCover():`<div class="text-cover">${icon(r.recordType==='milestone'?'star':'book')}</div>`}<span class="date-badge">${dateText(r.recordTime,false)}</span>${r.media.length?`<span class="image-count">${mediaSummary(r.media)}</span>`:r.illustration?'<span class="sample-label">示例插画</span>':''}</button><div class="record-body"><h3><button class="record-title" data-action="detail" data-id="${r.id}">${esc(r.title)}</button></h3><p class="record-excerpt">${esc(r.content)||'这一刻，也想好好记住。'}</p><div class="record-tags">${r.recordType==='milestone'?'<span class="milestone-tag">✧ 成长里程碑</span>':''}${r.tags.map(t=>`<span># ${esc(t)}</span>`).join('')}</div><div class="record-footer"><span class="record-author">${avatar(r.author,'',r.userId)}${esc(r.author)} · ${esc(selectedChild()?.name)}</span><button data-action="detail" data-id="${r.id}" aria-label="查看评论">${icon('comment')} ${r.comments.length||'说点什么'}</button></div></div></article>`;
}
function sidebar(){const d=state.data,usage=Math.min(100,d.family.storageUsed/d.family.storageQuota*100);const nav=(key,label,ico)=>`<button data-action="navigate" data-page="${key}" class="${state.page===key?'active':''}" ${state.page===key?'aria-current="page"':''}>${icon(ico)}${label}${key==='trash'&&(d.trash.length+(d.archivedChildren?.length||0))?`<span class="count">${(d.trash.length+(d.archivedChildren?.length||0))}</span>`:''}</button>`;return `<aside class="sidebar" aria-label="主导航">${brand()}<button class="family-switch" data-action="navigate" data-page="family">${icon('home')}<span><b>${esc(d.family.name)}</b><small>属于我们的小世界</small></span></button><p class="nav-label">成长的每一页</p><nav class="nav">${nav('timeline','成长时间线','timeline')}${nav('gallery','时光相册','image')}${nav('growth','成长数据','chart')}${nav('milestones','成长里程碑','star')}</nav><div class="nav-divider"></div><p class="nav-label">一家人在一起</p><nav class="nav">${nav('family','家庭成员','people')}${nav('reminders','待办提醒','clock')}${admin()?nav('trash','回收站','trash')+nav('settings','空间设置','settings'):''}</nav><div class="sidebar-bottom"><div class="storage"><div class="storage-head"><span>${icon('lock')} 私密家庭空间</span><span>${Math.round(usage)}%</span></div><div class="progress"><span style="width:${usage}%"></span></div><p>${bytes(d.family.storageUsed)} / ${bytes(d.family.storageQuota)}</p></div><p class="sidebar-note">小时光，慢慢藏。</p></div></aside>`;}
function render(){window.GrowthAssistant?.sync();if(!state.data)return renderLogin();if(!pages[state.page]||(!admin()&&['trash','settings','logs'].includes(state.page)))state.page='timeline';const d=state.data,unread=d.notices.filter(n=>!n.isRead).length;$('#app').innerHTML=`${sidebar()}<div class="shell"><header class="topbar"><div class="breadcrumb"><button class="icon-btn mobile-menu" data-action="menu" aria-label="打开导航">${icon('menu')}</button><span>我们的小世界</span>${icon('chevron')}<strong>${pages[state.page][0]}</strong></div><div class="top-actions"><label class="searchbox">${icon('search')}<input type="search" id="global-search" placeholder="寻找一段小回忆…" value="${esc(state.query)}" aria-label="搜索成长记录"></label><button class="icon-btn notification" data-action="navigate" data-page="notices" aria-label="消息通知${unread?'，'+unread+'条未读':''}">${icon('bell')}${unread?'<i class="dot"></i>':''}</button><button class="profile-button" data-action="account">${avatar(d.user.nickname,'peach',d.user.id)}<span>${esc(d.user.nickname)}</span>${icon('down')}</button></div></header>${state.demo?'<div class="demo-ribbon">你正在独立示例空间中体验 · 插画与数据均为演示内容 · 注册后开启自己的家庭记录</div>':''}<main class="content" id="main-content">${pageContent()}</main></div>`;}
function heading(extra=''){const childControl=['timeline','gallery','growth','milestones'].includes(state.page)&&state.data.children.length?`<select id="child-select" class="child-select" aria-label="切换孩子">${childOptions(state.childId)}</select>`:'';return `<div class="page-heading"><div><h1>${pages[state.page][0]}</h1><p>${pages[state.page][1]}</p></div><div class="heading-actions">${childControl}${extra}</div></div>`;}
function pageContent(){switch(state.page){case'timeline':return timeline();case'gallery':return gallery();case'milestones':return milestonePage();case'growth':return growth();case'family':return family();case'reminders':return window.GrowthReminders.page();case'trash':return trash();case'settings':return settings();case'notices':return notices();case'logs':return logs();default:return timeline();}}
function childEmpty(){return empty('成长的故事，从这里开始','先为孩子建一份档案，再慢慢装满珍贵的回忆。',admin()?'add-child':'',admin()?'添加孩子档案':'');}
function rail(){const d=state.data,c=selectedChild(),measure=d.measurements.filter(m=>m.childId===state.childId).at(-1);const memory=d.records.find(r=>r.childId===state.childId&&r.recordTime.slice(5)===today().slice(5)&&r.recordTime.slice(0,4)<today().slice(0,4));return `<aside class="rail"><section class="panel child-panel">${avatar(c?.name,'large peach')}<h3>${esc(c?.name||'小小的你')}</h3><div class="age">${age(c?.birthday)} · 正在好好长大</div><p class="birthday">${icon('calendar')} ${dateText(c?.birthday)} 出生</p><div class="child-metrics"><div><strong>${measure?.height??'—'}</strong><span>cm</span><small>最近身高</small></div><div><strong>${measure?.weight??'—'}</strong><span>kg</span><small>最近体重</small></div></div></section><section class="panel memory-panel"><h3>${icon('clock')} 往年今日</h3><p>时光走远了，回忆还在这里。</p>${memory?`${cover(memory)?`<button class="memory-photo" data-action="detail" data-id="${memory.id}" aria-label="查看往年今日"><img src="${cover(memory)}" alt="${esc(memory.title)}"></button>`:''}<h4>${esc(memory.title)}</h4><p class="memory-desc">${dateText(memory.recordTime)} · 那时候的你</p><button class="text-btn" data-action="detail" data-id="${memory.id}">重温这一天 ${icon('arrow')}</button>`:'<p class="memory-desc">今天还没有往年的记录。认真收藏今天，未来会收到时光的回信。</p>'}</section><section class="panel family-panel"><h3>${icon('people')} 一家人，一起记录</h3><div class="stack">${d.members.slice(0,5).map((m,i)=>avatar(m.nickname,i%2?'peach':'',m.userId)).join('')}</div><p>${d.members.length} 位家人，守护同一份珍贵。</p>${admin()?btn('邀请家人加入','invite','plus','secondary'):btn('看看我们的家','navigate','people','secondary','data-page="family"')}</section><div class="quote">${icon('leaf')}<br>“你慢慢长大，<br>我们慢慢记录。”<small>EVERY LITTLE MOMENT MATTERS</small></div></aside>`;}
function timeline(){const d=state.data,c=selectedChild();if(!c)return heading()+childEmpty();const all=d.records.filter(r=>r.childId===c.id);return `${heading(btn('记录此刻','new-record','plus'))}<section class="hero"><div class="hero-copy"><span class="eyebrow">THE LITTLE THINGS ARE THE BIG THINGS</span><h2>把成长，藏进时光里。</h2><p>一声稚嫩的笑，一次勇敢的尝试。<br>那些看似平凡的日常，都是独一无二的宝藏。</p>${btn('记下今天的小美好','new-record','edit')}</div><img class="hero-art" src="/growth/park.svg" alt="孩子在秋日公园里放风筝的温暖插画"><span class="hero-foot">陪你长大，是最温柔的事。</span></section><div class="dashboard-grid"><section><div class="stats-strip"><div class="stat"><span class="stat-icon">${icon('book')}</span><div><strong>${all.length}</strong><small>珍藏的成长记录</small></div></div><div class="stat"><span class="stat-icon">${icon('image')}</span><div><strong>${all.reduce((n,r)=>n+r.media.length,0)}</strong><small>留下的照片与视频</small></div></div><div class="stat"><span class="stat-icon">${icon('star')}</span><div><strong>${all.filter(r=>r.recordType==='milestone').length}</strong><small>闪光的里程碑</small></div></div></div><div class="section-top"><h2>成长的足迹 <small>LITTLE FOOTPRINTS</small></h2><span class="pill neutral">${icon('lock')} 仅家人可见</span></div>${filters()}${state.query?`<p class="inline-search-summary">正在寻找「${esc(state.query)}」 · ${records().length} 条记录 <button class="text-btn" data-action="clear-search">清除</button></p>`:''}<div class="record-grid">${records().map(recordCard).join('')||empty('这一页，还等着你的故事','换一个筛选条件，或记下今天的小美好。','new-record')}</div><div class="end-note">${records().length?'已经翻到这里啦 · 好好生活，慢慢记录':'每一段成长，都有值得珍藏的开头'}</div></section>${rail()}</div>`;}
function filters(){const months=[...new Set(state.data.records.filter(r=>r.childId===state.childId).map(r=>r.recordTime.slice(0,7)))].sort().reverse();return `<div class="toolbar"><div class="tabs" aria-label="记录类型">${[['all','全部记录'],['diary','日常点滴'],['milestone','里程碑']].map(([id,label])=>`<button data-action="filter" data-filter="${id}" class="${state.filter===id?'active':''}">${label}</button>`).join('')}</div><div><select id="month-select" aria-label="筛选月份"><option value="all">全部月份</option>${months.map(m=>`<option ${state.month===m?'selected':''}>${m}</option>`).join('')}</select><select id="order-select" aria-label="排序"><option value="new" ${state.order==='new'?'selected':''}>最近优先</option><option value="old" ${state.order==='old'?'selected':''}>最早优先</option></select></div></div>`;}
function gallery(){
  if(!selectedChild())return heading()+childEmpty();
  const rows=records().flatMap(r=>r.media.length?r.media.map(m=>({record:r,url:`/api/growth/media/${m.id}`,video:isVideo(m),sample:false})):cover(r)?[{record:r,url:cover(r),video:false,sample:true}]:[]);
  return heading(btn('添加照片 / 视频','new-record','plus'))+filters()+`<p class="page-note">${rows.length} 个画面 · 点击打开记录，观看照片或视频${state.query?` · 搜索：${esc(state.query)}`:''}</p><div class="gallery-grid">${rows.map(({record:r,url,sample,video})=>`<button class="gallery-item" data-action="detail" data-id="${r.id}">${video?videoCover():`<img src="${url}" alt="${esc(r.title)}" loading="lazy">`}<strong>${esc(r.title)}</strong><small>${dateText(r.recordTime)}${sample?' · 示例插画':video?' · MP4 视频':''}</small></button>`).join('')}</div>${rows.length?'':empty('相册的第一页，留给你','上传照片或视频，收藏一个小小的瞬间。','new-record','添加照片 / 视频','image')}`;
}
function milestonePage(){if(!selectedChild())return heading()+childEmpty();return heading(btn('记录里程碑','new-milestone','star'))+`<div class="hero"><div class="hero-copy"><span class="eyebrow">A LITTLE STEP, A BIG MOMENT</span><h2>每一个第一次，都闪着光。</h2><p>第一次叫妈妈，第一次独立骑车。<br>成长的脚步很轻，我们替时光记得。</p></div><img class="hero-art" src="/growth/bike.svg" alt="孩子第一次骑自行车的插画"></div><div class="record-grid">${records().map(recordCard).join('')||empty('下一个第一次，值得被记住','选择一个里程碑，记录孩子的小小勇敢。','new-milestone','记录里程碑','star')}</div>`;}
function growth(){
  const c=selectedChild();if(!c)return heading()+childEmpty();
  const list=state.data.measurements.filter(m=>m.childId===c.id),last=list.at(-1);
  const vaccines=state.data.vaccines.filter(v=>v.childId===c.id);
  const actions=(kind,id)=>admin()?`<td><div class="row-actions">${btn('编辑','edit-'+kind,'edit','secondary small',`data-id="${id}"`)}${btn('删除','delete-'+kind,'trash','secondary small',`data-id="${id}"`)}</div></td>`:'';
  return heading(btn('整理测量变化','ai-measurements','leaf','secondary')+btn('记录成长','measurement','plus'))+`
    <div class="data-grid">${[['height','身高','cm'],['weight','体重','kg'],['head','头围','cm']].map(([key,label,unit])=>`<div class="data-stat"><span class="label">${label}</span><strong>${last?.[key]??'—'}<small>${unit}</small></strong><p>${last?`最近记录 · ${dateText(last.measureTime)}`:'还没有测量记录'}</p></div>`).join('')}</div>
    <section class="panel chart-panel"><div class="section-top"><h2>一点一点，长大的轨迹</h2><div class="tabs">${[['height','身高'],['weight','体重'],['head','头围']].map(([key,label])=>`<button data-action="metric" data-metric="${key}" class="${state.metric===key?'active':''}">${label}</button>`).join('')}</div></div>${chart(list)}<p class="chart-note">横轴为测量时的整月龄，保留每一次测量点；同月记录可能重叠。这里仅呈现家庭记录，不提供生长标准或医学判断。</p></section>
    <section class="panel"><div class="section-top"><h2>成长测量记录</h2><span class="muted">${list.length} 次记录</span></div><div class="table-wrap"><table class="data-table"><thead><tr><th>测量日期</th><th>月龄</th><th>身高 / cm</th><th>体重 / kg</th><th>头围 / cm</th><th>小备注</th>${admin()?'<th>操作</th>':''}</tr></thead><tbody>${[...list].reverse().map(m=>`<tr><td>${m.measureTime}</td><td>${m.ageMonth} 个月</td><td>${m.height}</td><td>${m.weight}</td><td>${m.head??'—'}</td><td class="wrap-cell">${esc(m.remark)||'—'}</td>${actions('measurement',m.id)}</tr>`).join('')||`<tr><td colspan="${admin()?7:6}">还没有测量记录，点击右上角「记录成长」添加。</td></tr>`}</tbody></table></div></section>
    <section class="panel" style="margin-top:23px"><div class="section-top"><h2>接种小档案</h2>${btn('添加接种','vaccine','plus','secondary small')}</div><p class="page-note">记录已完成的接种，具体安排请以接种机构意见为准。</p><div class="table-wrap"><table class="data-table"><thead><tr><th>疫苗名称</th><th>剂次</th><th>接种日期</th><th>接种机构</th><th>备注</th>${admin()?'<th>操作</th>':''}</tr></thead><tbody>${vaccines.map(v=>`<tr><td>${esc(v.vaccineName)}</td><td>第 ${v.doseNo} 剂</td><td>${v.vaccineDate}</td><td>${esc(v.hospital)||'—'}</td><td class="wrap-cell">${esc(v.remark)||'—'}</td>${actions('vaccine',v.id)}</tr>`).join('')||`<tr><td colspan="${admin()?6:5}">还没有接种记录。</td></tr>`}</tbody></table></div></section>`;
}
function chart(list){const points=list.filter(m=>m[state.metric]!=null);if(!points.length)return '<div class="empty-state compact">还没有这项测量数据，添加后将在这里连成成长曲线。</div>';const values=points.map(m=>Number(m[state.metric])),ages=points.map(m=>m.ageMonth),minValue=Math.min(...values),maxValue=Math.max(...values),pad=Math.max((maxValue-minValue)*.25,1),min=minValue-pad,max=maxValue+pad,minX=Math.min(...ages),maxX=Math.max(...ages);const x=m=>maxX===minX?370:55+(m.ageMonth-minX)/(maxX-minX)*655,y=m=>225-(Number(m[state.metric])-min)/(max-min)*190;const coords=points.map(m=>`${x(m)},${y(m)}`).join(' ');const ticks=[...new Set(Array.from({length:Math.min(6,maxX-minX+1)},(_,i)=>Math.round(minX+i*(maxX-minX)/Math.max(1,Math.min(5,maxX-minX)))))];return `<svg class="chart" viewBox="0 0 760 270" role="img" aria-label="${esc(selectedChild().name)}的成长曲线，${points.length}次测量，明细见下表">${Array.from({length:5},(_,i)=>{const gy=35+i*47.5;return `<line class="gridline" x1="55" x2="710" y1="${gy}" y2="${gy}"/><text x="40" y="${gy+4}" text-anchor="end">${(max-i*(max-min)/4).toFixed(1)}</text>`;}).join('')}<polygon class="area" points="${x(points[0])},225 ${coords} ${x(points.at(-1))},225"/><polyline class="line" points="${coords}"/>${points.map(m=>`<circle class="point" cx="${x(m)}" cy="${y(m)}" r="4"><title>${m.measureTime} · ${m.ageMonth}个月 · ${m[state.metric]} ${state.metric==='weight'?'kg':'cm'}</title></circle>`).join('')}${ticks.map(t=>`<text x="${maxX===minX?370:55+(t-minX)/(maxX-minX)*655}" y="250" text-anchor="middle">${t}个月</text>`).join('')}</svg>`;}
function family(){
  const canManage=m=>admin()&&m.userId!==state.data.user.id&&m.userId!==state.data.family.creatorId;
  return heading(admin()?btn('邀请家人','invite','plus'):'')+`<div class="member-grid">${state.data.members.map((m,i)=>`<section class="panel member-card">${avatar(m.nickname,'large '+(i%2?'peach':''),m.userId)}<h3>${esc(m.nickname)}</h3><span class="pill ${m.role==='admin'?'':'neutral'}">${m.userId===state.data.family.creatorId?'家庭创建者':m.role==='admin'?'家庭管理员':'家庭成员'}${m.userId===state.data.user.id?' · 我':''}</span><p>${dateText(m.createTime)} 加入</p>${canManage(m)?`<div class="family-member-actions">${btn(m.role==='admin'?'取消管理员权限':'设为管理员','member-role','settings','secondary small',`data-id="${m.id}"`)}<button type="button" class="text-btn" data-action="remove-member" data-id="${m.id}">移出家庭</button></div>`:'<span class="muted">一起收藏成长的点滴</span>'}</section>`).join('')}${admin()?`<button class="panel invite-card" data-action="invite">${icon('plus')}<h3>把家人请进来</h3><span class="muted">分享邀请码，一起记录与陪伴</span></button>`:''}</div>
    <div class="section-top family-children-heading"><h2>孩子的小档案</h2>${admin()?btn('添加孩子','add-child','plus','secondary small'):''}</div><div class="member-grid">${state.data.children.map(c=>`<section class="panel member-card">${avatar(c.name,'large peach')}<h3>${esc(c.name)}</h3><span class="pill">${age(c.birthday)}</span><p>${dateText(c.birthday)} 出生</p><div class="family-member-actions"><button class="text-btn" data-action="view-child" data-id="${c.id}">翻开成长记录 ${icon('arrow')}</button>${admin()?`<button class="text-btn family-danger" data-action="archive-child" data-id="${c.id}">删除档案</button>`:''}</div></section>`).join('')||childEmpty()}</div>
    ${admin()?`<p class="page-note">删除的档案及关联资料可在回收站恢复。${btn('查看档案回收站','navigate','trash','secondary small','data-page="trash"')}</p>`:''}<p class="page-note">普通成员可查看、上传、评论，并编辑或删除自己的记录。管理员可管理成长数据、孩子档案、家庭成员、权限、回收站与空间设置；家庭创建者不可被移出或取消权限，不能修改自己的权限。</p>`;
}
function manageMember(id,remove=false){
  if(!admin())return toast('仅家庭管理员可管理成员');
  const member=state.data.members.find(m=>m.id===id);if(!member)return toast('成员已不存在，请刷新页面');
  if(member.userId===state.data.family.creatorId||member.userId===state.data.user.id)return toast('不能操作家庭创建者或自己');
  const role=member.role==='admin'?'member':'admin',label=remove?'移出家庭':role==='admin'?'授予管理员权限':'取消管理员权限';
  const detail=remove?'对方将失去家庭访问权限，已发布的记录保留，其分享会撤销。当前不支持被移出账号重新加入家庭。':role==='admin'?'对方将拥有成长数据、档案、成员及权限管理、回收站和空间设置等管理员能力，也可以授权其他成员。':'对方将恢复为普通家庭成员，保留查看、发布及管理自己记录的权限。';
  confirmDialog(label,`确定为「${esc(member.nickname)}」执行${label}？${detail}`,async()=>{
    await api(`/family/members/${id}`+(remove?'':'/role'),remove?{method:'DELETE'}:{method:'PUT',body:{role}});
    closeModal();await load();toast(remove?'成员已移出家庭':'成员权限已更新');
  },remove?'确认移出':label);
}
function manageChild(id,restore=false){
  if(!admin())return toast('仅家庭管理员可管理孩子档案');
  const child=(restore?state.data.archivedChildren||[]:state.data.children).find(c=>c.id===id);if(!child)return toast('档案状态已变化，请刷新页面');
  const label=restore?'恢复孩子档案':'删除孩子档案';
  const detail=restore?'档案及关联资料将重新显示；原来已删除的记录仍在记录回收站，旧分享不会恢复。':'档案、成长记录、照片视频、测量和接种资料会隐藏并保留，可在档案回收站恢复。旧分享将撤销，保留的媒体仍占用空间。';
  confirmDialog(label,`确定${restore?'恢复':'删除'}「${esc(child.name)}」的档案？${detail}`,async()=>{
    await api(`/children/${id}`+(restore?'/restore':''),{method:restore?'POST':'DELETE'});
    closeModal();await load();toast(restore?'孩子档案及关联资料已恢复':'孩子档案已移入档案回收站');
  },restore?'恢复档案':'删除并保留资料');
}
function purgeChild(id){
  if(!admin())return toast('仅家庭管理员可管理孩子档案');
  const child=(state.data.archivedChildren||[]).find(c=>c.id===id);
  if(!child)return toast('档案状态已变化，请刷新页面');
  confirmDialog('彻底删除孩子档案',`确定彻底删除「${child.name}」的档案？该孩子的全部成长记录（包括记录回收站）、照片视频、测量、接种、评论、分享及关联资料将永久删除，无法恢复。请确认已保存需要的资料。`,async()=>{
    await api(`/children/${id}/purge`,{method:'DELETE'});
    closeModal();await load();toast('孩子档案及关联资料已彻底删除，照片和视频将在后台清理');
  },'彻底删除，无法恢复');
}
function trash(){
  if(!admin())return heading()+empty('仅管理员可管理回收站','请联系家庭管理员。');
  const archived=state.data.archivedChildren||[];
  return heading()+`<section class="panel child-archive-panel"><h2>孩子档案回收站</h2><p class="page-note">档案资料仍占用家庭空间。恢复后，原来单独删除的记录仍在记录回收站，旧分享不恢复；彻底删除会清理该孩子的全部关联资料（包括记录回收站），无法恢复。</p>${archived.map(c=>`<div class="trash-row">${avatar(c.name,'peach')}<div class="trash-copy"><h3>${esc(c.name)}</h3><p>${dateText(c.birthday)} 出生 · 档案及资料已隐藏</p></div><div class="row-actions">${btn('恢复档案','restore-child','restore','secondary small',`data-id="${c.id}"`)}${btn('彻底删除','purge-child','trash','secondary small',`data-id="${c.id}"`)}</div></div>`).join('')||'<p class="muted">暂时没有删除的孩子档案。</p>'}</section>
    <section class="panel"><h2>成长记录回收站</h2><p class="page-note">移入回收站的记录仅管理员可见。恢复不会恢复旧分享；彻底删除后无法撤回。</p>${state.data.trash.map(r=>`<div class="trash-row">${r.illustration?`<img class="trash-thumb" src="/growth/${['park','bike','baking','night','birthday','sea'].includes(r.illustration)?r.illustration:'park'}.svg" alt="示例插画">`:icon('book')}<div class="trash-copy"><h3>${esc(r.title)}</h3><p>${esc(r.author)} · ${timeText(r.deleteTime)} 移入</p></div><div class="row-actions">${btn('恢复','restore','restore','secondary small',`data-id="${r.id}"`)}${btn('彻底删除','purge','trash','secondary small',`data-id="${r.id}"`)}</div></div>`).join('')||empty('记录回收站里空空的','珍贵的回忆，都被好好收藏着。','','','leaf')}</section>`;
}
function settings(){const d=state.data;return heading()+`<section class="panel" style="margin-bottom:22px"><div class="section-top"><h2>拾光小助手 · 千问</h2>${btn('设置与使用','ai-settings','leaf','secondary small')}</div><p class="page-note">管理员可开启或关闭家庭助手。家人主动确认后，才会将所选文字或测量资料发送给千问。</p></section><div class="settings-layout"><section class="panel"><h2>给这个家，起个名字</h2><form data-form="settings"><label class="field"><span>家庭空间名称</span><input name="name" required maxlength="60" value="${esc(d.family.name)}"></label><button class="btn" type="submit">保存修改</button></form></section><section class="panel"><h2>家庭存储空间</h2><div class="settings-info"><p>有效照片 / 视频 <b>${bytes(d.family.storageUsed)}</b> / ${bytes(d.family.storageQuota)}</p><p>回收站照片 / 视频 <b>${bytes(d.family.trashBytes)}</b>，仍占用存储空间。</p><span>每个家庭默认 5 GB 配额，照片和视频合计占用。以上占用包含档案回收站中保留的照片和视频。照片和视频存入家庭私有存储空间。图片会移除 EXIF 信息，视频保留原文件。</span></div></section><section class="panel"><h2>把成长带在身边</h2><p class="page-note">按孩子导出文字、标签、测量和接种数据（JSON）。当前导出不包含照片和视频文件。</p><label class="field"><span>选择孩子</span><select id="export-child">${childOptions(state.childId)}</select></label>${btn('导出成长记录','export','download','secondary',d.children.length?'':'disabled')}</section><section class="panel"><h2>空间里的每一次改变</h2><p class="page-note">查看发布、修改、删除、恢复、分享和成员变更的记录。</p>${btn('查看操作日志','navigate','log','secondary','data-page="logs"')}<p class="page-note"><a href="/ai-chat.html" target="_blank" rel="noopener">打开原有 Spring AI 调试台 ↗</a></p></section></div><section class="panel" style="margin-top:22px"><h2>限时分享管理</h2><div class="table-wrap"><table class="data-table"><thead><tr><th>成长记录</th><th>到期时间</th><th>访问次数</th><th>状态</th><th>操作</th></tr></thead><tbody>${d.shares.map(s=>{const valid=s.status===1&&new Date(s.expireTime)>new Date()&&s.accessCount<s.maxAccess;return `<tr><td class="wrap-cell">${esc(s.title)}</td><td>${timeText(s.expireTime)}</td><td>${s.accessCount} / ${s.maxAccess}</td><td>${s.status===3?'已撤销':valid?'生效中':'已失效'}</td><td>${valid?`<button class="text-btn" data-action="copy-share" data-token="${esc(s.shareToken)}">复制链接</button> · <button class="text-btn" data-action="revoke" data-id="${s.id}">撤销</button>`:'—'}</td></tr>`;}).join('')||'<tr><td colspan="5">还没有分享。在记录详情中可以创建限时分享。</td></tr>'}</tbody></table></div></section>`;}
function notices(){const d=state.data;const memories=d.records.filter(r=>r.recordTime.slice(5)===today().slice(5)&&r.recordTime.slice(0,4)<today().slice(0,4));return heading(btn('全部标为已读','read-notices','check','secondary'))+`<section class="panel">${memories.map(r=>`<div class="notice-row"><span class="stat-icon">${icon('clock')}</span><div class="notice-copy"><h3>往年今日 · ${esc(r.title)}</h3><p>${dateText(r.recordTime)}，有一个值得再看一遍的瞬间。</p></div><button class="text-btn" data-action="detail" data-id="${r.id}">重温 ${icon('arrow')}</button></div>`).join('')}${d.notices.map(n=>`<div class="notice-row ${n.isRead?'':'unread'}"><span class="stat-icon">${icon('bell')}</span><div class="notice-copy"><h3>${esc(n.title)}</h3><p>${esc(n.content)}</p><small>${timeText(n.createTime)}</small></div>${n.linkId?`<button class="text-btn" data-action="detail" data-id="${n.linkId}">查看</button>`:''}</div>`).join('')||(!memories.length?empty('现在安安静静的','家人的评论与往年今日，会在这里与你相遇。','','','bell'):'')}</section>`;}
function logs(){return heading()+`<section class="panel"><p class="page-note">最近 100 条家庭操作记录</p>${state.data.logs.map(l=>`<div class="log-row"><span class="stat-icon">${icon('log')}</span><div class="log-copy"><p>${esc(l.detail)}</p><small>${esc(l.nickname)} · ${timeText(l.createTime)}</small></div><span class="pill neutral">${esc(({upload:'发布',delete:'删除',restore:'恢复',update:'修改',share:'分享',member_change:'成员',export:'导出',setting:'设置'})[l.actionType]||l.actionType)}</span></div>`).join('')}</section>`;}

function renderLogin(){
  window.GrowthRegistration?.reset();
  // 等待本次登录页 DOM 替换后挂载可选头像选择器。
  queueMicrotask(()=>window.GrowthRegistration?.mount(document.querySelector('form[data-form="register"]')));
  window.GrowthAssistant?.reset();window.GrowthAssistant?.sync();
  const register=state.loginMode==='register';
  $('#app').innerHTML=`<main class="login-page"><section class="login-story">${brand()}<h1>你慢慢长大，<br>我慢慢记录。</h1><p>把每一次微笑、每一个第一次，<br>藏进一家人共有的时光里。</p><img src="/growth/park.svg" alt="秋日里，孩子在公园放飞风筝"><small>COLLECT MOMENTS, GROW TOGETHER.</small></section><section class="login-form-wrap"><div class="login-form"><span class="eyebrow">WELCOME TO LITTLE MOMENTS</span><h2>${register?'开启我们家的成长故事':'欢迎回到，小小的幸福里'}</h2><p>${register?'从今天起，把那些小美好好好收藏。':'登录家庭空间，翻开成长的下一页。'}</p><div class="login-tabs"><button data-action="login-tab" data-mode="login" class="${register?'':'active'}">登录</button><button data-action="login-tab" data-mode="register" class="${register?'active':''}">创建 / 加入家庭</button></div><form data-form="${register?'register':'login'}"><label class="field"><span>手机号</span><input name="phone" type="tel" inputmode="numeric" autocomplete="username" required pattern="1[0-9]{10}" maxlength="11" placeholder="请输入 11 位手机号"></label><label class="field"><span>密码</span><input name="password" type="password" required ${register?'minlength="8" maxlength="60"':'maxlength="72"'} autocomplete="${register?'new-password':'current-password'}" placeholder="${register?'至少 8 个字符':'请输入密码'}"></label>${register?`<div class="form-row"><label class="field"><span>你的昵称</span><input name="nickname" required maxlength="40" placeholder="家人怎么称呼你"></label><label class="field"><span>家庭名称</span><input name="familyName" maxlength="60" placeholder="例如：我们的幸福小家"></label></div><label class="field"><span>家庭邀请码（选填）</span><input name="inviteCode" maxlength="64" placeholder="已有家人邀请？在这里填写"><small>填写邀请码会加入现有家庭；留空则创建新家庭。</small></label>`:''}<button class="btn" type="submit">${register?'开启成长故事':'回到我们的小世界'} ${icon('arrow')}</button></form><div class="divider">先看看拾光是什么样子</div>${btn('体验示例空间','demo','leaf','secondary')}<p class="login-note">${icon('lock')} 家庭内容默认私密，只有家人可见。<br>手机号作为账号使用，未接入短信验证与找回密码。</p><footer>拾光 · 让每个小小的瞬间，都有安放的地方</footer></div></section></main>`;
}
function openModal(title,body){window.GrowthAssistant?.reset();const dialog=$('#modal');dialog.querySelectorAll('video').forEach(video=>video.pause());objectUrls.forEach(URL.revokeObjectURL);objectUrls=[];dialog.innerHTML=`<header class="modal-head"><h2 id="modal-title">${esc(title)}</h2><button class="icon-btn" data-action="close" aria-label="关闭窗口">${icon('close')}</button></header><div class="modal-body">${body}</div>`;if(!dialog.open)dialog.showModal();dialog.scrollTop=0;}
function closeModal(){if(window.GrowthProfile?.busy()){toast('个人资料正在保存，请稍候');return;}if(mediaUploading){toast('照片和视频仍在上传，请等待完成');return;}const modal=$('#modal');if(modal.open)modal.close();objectUrls.forEach(URL.revokeObjectURL);objectUrls=[];confirmAction=null;}
function confirmDialog(title,text,action,label='确认'){confirmAction=action;openModal(title,`<p class="notice-confirm">${esc(text)}</p><div class="form-actions">${btn('再想想','close','close','secondary')}${btn(label,'confirm','check',label.includes('删除')?'danger':'')}</div>`);}
function formActions(label='保存'){return `<div class="form-actions"><button type="button" class="btn secondary" data-action="close">取消</button><button type="submit" class="btn">${label} ${icon('check')}</button></div>`;}
function recordForm(id=null,milestone=false){
  if(!state.data.children.length){if(admin())return childForm();return toast('请先让家庭管理员添加孩子档案');}
  const r=id?state.data.records.find(r=>r.id===id):null;if(id&&!r)return toast('记录已不存在');
  openModal(r?'编辑这段回忆':milestone?'记住一个第一次':'记录今天的小美好',`<p class="modal-intro">不需要特别盛大，平凡的一天也值得被收藏。</p><form data-form="record" ${r?`data-id="${r.id}"`:''}><div class="form-row"><label class="field"><span>为谁记录</span><select name="childId">${childOptions(r?.childId??state.childId)}</select></label><label class="field"><span>发生在这一天</span><input type="date" name="recordTime" required max="${today()}" value="${r?.recordTime??today()}"></label></div><label class="field"><span>给这段回忆起个名字</span><input name="title" required maxlength="80" value="${esc(r?.title)}" placeholder="例如：第一次，自己骑出好远"></label><label class="field"><span>想对未来的你说…</span><textarea name="content" maxlength="4000" rows="4" placeholder="今天发生了什么有趣的小事？">${esc(r?.content)}</textarea></label><div class="record-ai-entry"><button type="button" class="btn secondary small" data-action="ai-diary">${icon('leaf')} 帮我写一写</button><span class="form-hint">把真实的小事，整理成温暖的文字</span></div><div class="form-row"><label class="field"><span>记录类型</span><select name="recordType"><option value="diary" ${r?.recordType==='diary'?'selected':''}>日常点滴</option><option value="milestone" ${milestone||r?.recordType==='milestone'?'selected':''}>成长里程碑</option></select></label><label class="field"><span>里程碑主题（选填）</span><input name="milestone" list="milestone-options" maxlength="60" value="${esc(r?.milestone)}" placeholder="选择或自己写一个"><datalist id="milestone-options">${['第一次微笑','第一次叫妈妈','迈出第一步','第一天上幼儿园','学会骑车','生日快乐'].map(s=>`<option value="${s}"></option>`).join('')}</datalist></label></div><label class="field"><span>小标签（用逗号分隔，最多 6 个）</span><input name="tags" value="${esc(r?.tags.join('，'))}" maxlength="197" placeholder="亲子日常，户外时光，第一次"></label><label class="upload-zone">${icon('image')}<p>${r?'添加更多照片或视频':'选几张照片或一段视频，让回忆更鲜活'}</p><input type="file" name="files" accept=".jpg,.jpeg,.png,.mp4,image/jpeg,image/png,video/mp4" multiple><p>JPG / PNG 每张 8 MB，MP4 每个 100 MB；每次最多 6 个、合计 200 MB，每条记录最多 12 个文件。</p></label><div class="file-preview" id="file-preview"></div><p class="form-hint">视频暂不自动转码。推荐 H.264 / AAC 编码的 MP4；视频仅家人可见，不包含在访客分享中。</p><p class="upload-status" role="status" aria-live="polite" hidden></p>${r?.media.length?`<p class="form-hint">这条记录已有 ${mediaSummary(r.media)}，本次选择会追加照片或视频。</p>`:''}<p class="form-hint">${icon('lock')} 只有家人可见。发布后可单独创建限时分享。</p>${formActions(r?'保存这段回忆':'收藏这一刻')}</form>`);
}
function childForm(){openModal('为孩子建一本成长档案',`<p class="modal-intro">每个小小的你，都有属于自己的成长故事。</p><form data-form="child"><label class="field"><span>孩子的昵称</span><input name="name" required maxlength="40" placeholder="例如：小禾"></label><div class="form-row"><label class="field"><span>出生日期</span><input name="birthday" type="date" required max="${today()}"></label><label class="field"><span>性别</span><select name="gender"><option value="secret">暂不填写</option><option value="girl">女孩</option><option value="boy">男孩</option></select></label></div>${formActions('创建小档案')}</form>`);}
function measurementForm(id=null){
  if(id!==null&&!admin())return toast('仅家庭管理员可以编辑成长数据');
  const m=id===null?null:state.data.measurements.find(row=>row.id===id);
  if(id!==null&&!m)return toast('这条测量记录已不存在，请刷新页面');
  if(!selectedChild())return admin()?childForm():toast('请先让家庭管理员添加孩子档案');
  openModal(m?'编辑成长测量':'又长大了一点点',`<p class="modal-intro">记录每一次测量，慢慢连成成长的轨迹。</p><form data-form="measurement" ${m?`data-id="${m.id}"`:''}>
    <div class="form-row"><label class="field"><span>孩子</span><select name="childId">${childOptions(m?.childId??state.childId)}</select></label><label class="field"><span>测量日期</span><input name="measureTime" type="date" required max="${today()}" value="${esc(m?.measureTime??today())}"></label></div>
    <div class="form-row"><label class="field"><span>身高（cm）</span><input name="height" type="number" required min="20" max="250" step="0.1" value="${esc(m?.height)}" placeholder="例如 95.5"></label><label class="field"><span>体重（kg）</span><input name="weight" type="number" required min="0.5" max="200" step="0.1" value="${esc(m?.weight)}" placeholder="例如 14.0"></label></div>
    <label class="field"><span>头围（cm，选填）</span><input name="head" type="number" min="10" max="100" step="0.1" value="${esc(m?.head)}"></label><label class="field"><span>小备注（选填）</span><input name="remark" maxlength="255" value="${esc(m?.remark)}" placeholder="记录这次测量的情况"></label>${formActions(m?'保存修改':'记下来')}</form>`);
}
function vaccineForm(id=null){
  if(id!==null&&!admin())return toast('仅家庭管理员可以编辑接种记录');
  const v=id===null?null:state.data.vaccines.find(row=>row.id===id);
  if(id!==null&&!v)return toast('这条接种记录已不存在，请刷新页面');
  if(!selectedChild())return admin()?childForm():toast('请先让家庭管理员添加孩子档案');
  openModal(v?'编辑接种记录':'记下一次接种',`<form data-form="vaccine" ${v?`data-id="${v.id}"`:''}><label class="field"><span>孩子</span><select name="childId">${childOptions(v?.childId??state.childId)}</select></label>
    <label class="field"><span>疫苗名称</span><input name="vaccineName" required maxlength="64" value="${esc(v?.vaccineName)}" placeholder="按接种凭证填写"></label><div class="form-row"><label class="field"><span>第几剂</span><input name="doseNo" type="number" required min="1" max="20" value="${esc(v?.doseNo??1)}"></label><label class="field"><span>接种日期</span><input name="vaccineDate" type="date" required max="${today()}" value="${esc(v?.vaccineDate??today())}"></label></div>
    <label class="field"><span>接种机构（选填）</span><input name="hospital" maxlength="128" value="${esc(v?.hospital)}"></label><label class="field"><span>备注（选填）</span><input name="remark" maxlength="255" value="${esc(v?.remark)}"></label>${formActions(v?'保存修改':'保存接种记录')}</form>`);
}
function deleteGrowthData(kind,id){
  if(!admin())return toast('仅家庭管理员可以删除成长数据');
  const measurement=kind==='measurement';
  const row=(measurement?state.data.measurements:state.data.vaccines).find(item=>item.id===id);
  if(!row)return toast('这条记录已不存在，请刷新页面');
  const child=state.data.children.find(item=>item.id===row.childId);
  const label=measurement?'成长测量':'接种记录';
  const description=measurement?`${dateText(row.measureTime)}的测量`:`${dateText(row.vaccineDate)}的${row.vaccineName}（第 ${row.doseNo} 剂）`;
  confirmDialog(`删除这条${label}？`,`将删除${child?.name??'孩子'}${description}。删除后无法恢复${measurement?'，成长曲线和最新测量会随之更新':''}。`,async()=>{
    await api(`/${measurement?'measurements':'vaccines'}/${id}`,{method:'DELETE'});
    closeModal();await load();toast(`${label}已删除`);
  },'确认删除');
}
const mediaDownloadButton = m => btn(isVideo(m)?'下载视频':'下载照片','download-media','download','secondary small',`data-id="${m.id}"`);
const mediaFileActions = (m,label) => `<div class="media-file-actions"><span>${esc(label)} · ${bytes(m.sizeBytes)}</span>${mediaDownloadButton(m)}</div>`;
function detailPhoto(m,index,coverPhoto=false){
  return `<figure class="detail-photo${coverPhoto?' detail-photo-cover':''}"><button type="button" class="photo-preview" data-action="lightbox" data-id="${m.id}" aria-label="放大第 ${index+1} 张照片"><img ${coverPhoto?'class="detail-cover"':''} src="/api/growth/media/${m.id}" alt="成长记录照片 ${index+1}" ${coverPhoto?'':'loading="lazy"'}></button><figcaption>${mediaFileActions(m,`照片 ${index+1}`)}</figcaption></figure>`;
}
function lightbox(id){
  const m=state.data?.records.flatMap(r=>r.media).find(m=>m.id===id);
  if(!m||isVideo(m))return toast('照片已删除或不在当前家庭中，请刷新后重试');
  openModal('成长的一个小瞬间',`<img class="lightbox-image" src="/api/growth/media/${m.id}" alt="成长记录照片">${mediaFileActions(m,'照片')}<p class="form-hint">下载的是上传处理后保存的照片，已移除 EXIF 信息。</p>`);
}
async function downloadMedia(id){
  const userId=state.data?.user.id,familyId=state.data?.family.id;
  const m=state.data?.records.flatMap(r=>r.media).find(m=>m.id===id);
  if(!m)return toast('文件已删除或不在当前家庭中，请刷新后重试');
  const url=`/api/growth/media/${id}/download`;
  // HEAD 先检查登录、权限及文件是否存在；不使用 Blob 缓存整个视频。
  const response=await fetch(url,{method:'HEAD',credentials:'same-origin',cache:'no-store'});
  if(state.data?.user.id!==userId||state.data?.family.id!==familyId)return;
  if(!response.ok){
    const messages={401:'登录已失效，请重新登录后下载',403:'没有权限下载此文件',404:'文件不存在、已移入回收站或孩子档案已归档，请刷新后重试',409:'当前账号或家庭状态暂不支持下载'};
    const error=new Error(messages[response.status]||'文件暂时无法下载，请稍后重试');error.status=response.status;throw error;
  }
  // 实际 GET 仍由后端重新校验权限；交给浏览器管理下载进度，文件名由响应头提供。
  const link=document.createElement('a');link.href=url;link.download='';link.hidden=true;
  document.body.append(link);try{link.click();}finally{link.remove();}
  toast('已发起下载，请查看浏览器的下载列表');
}
function detail(id){
  const r=state.data.records.find(r=>r.id===id);if(!r)return toast('这条记录已删除或不在当前家庭中');
  const photos=r.media.filter(m=>!isVideo(m)),videos=r.media.filter(isVideo);
  const photoContent=photos.length?detailPhoto(photos[0],0,true):cover(r)?`<img class="detail-cover" src="${cover(r)}" alt="${esc(r.title)}">`:'';
  const otherPhotos=photos.length>1?`<div class="detail-images">${photos.slice(1).map((m,i)=>detailPhoto(m,i+1)).join('')}</div>`:'';
  const videoContent=videos.length?`<section class="detail-videos"><h3>会动的回忆 · ${videos.length} 个视频</h3>${videos.map((m,i)=>`<div class="video-item">${videoPlayer('/api/growth/media/'+m.id,r.title+' · 视频 '+(i+1))}${mediaFileActions(m,`视频 ${i+1}`)}</div>`).join('')}</section>`:'';
  openModal(r.title,`<div class="detail-meta">${avatar(r.author,'',r.userId)}${esc(r.author)} · ${dateText(r.recordTime)} <span class="pill">${r.recordType==='milestone'?'成长里程碑':'日常点滴'}</span></div>${photoContent}<p class="detail-content">${esc(r.content)}</p>${otherPhotos}${videoContent}${r.media.length?'<p class="form-hint">仅家人可下载。照片为上传处理后保存的版本，已移除 EXIF 信息；视频保留上传文件。</p>':''}${r.illustration&&!r.media.length?'<p class="form-hint">此图为示例插画，上传你自己的照片或视频后会显示真实内容。</p>':''}<div class="record-tags">${r.tags.map(t=>`<span># ${esc(t)}</span>`).join('')}</div>${canEdit(r)?`<div class="detail-actions">${btn('编辑','edit','edit','secondary small',`data-id="${r.id}"`)}${btn('限时分享','share','share','secondary small',`data-id="${r.id}"`)}${btn('移入回收站','delete','trash','secondary small',`data-id="${r.id}"`)}</div>`:''}<section class="comments"><h3>家人的小回应 · ${r.comments.length}</h3>${r.comments.map(c=>`<div class="comment">${avatar(c.nickname,'peach',c.userId)}<div><small>${esc(c.nickname)} · ${timeText(c.createTime)}</small><p>${esc(c.content)}</p></div></div>`).join('')}<form class="comment-form" data-form="comment" data-id="${r.id}"><input name="content" required maxlength="500" placeholder="留下一句温暖的话…" aria-label="评论内容"><button class="btn small" type="submit">发送</button></form></section>`);
}
function shareForm(id){openModal('把这一刻，分享给在意的人',`<p class="modal-intro">对方只能查看这一条记录。到期、达到访问上限或记录被删除后，链接自动失效。</p><form data-form="share" data-id="${id}"><div class="form-row"><label class="field"><span>分享有效期</span><select name="hours"><option value="24">1 天</option><option value="72">3 天</option><option value="168">7 天</option></select></label><label class="field"><span>最多访问次数</span><input type="number" name="maxAccess" min="1" max="1000" required value="20"></label></div><p class="form-hint">分享仅包含文字和带水印的图片预览，不包含视频，也不提供原图下载。请仅将链接发给信任的人；网页无法阻止截图或另行拍摄。</p>${formActions('生成分享链接')}</form>`);}
async function copy(text){try{await navigator.clipboard.writeText(text);toast('已经复制好了');}catch{openModal('复制这段内容',`<p class="modal-intro">浏览器暂不支持自动复制，请选中下方内容手动复制。</p><label class="field"><input readonly value="${esc(text)}" aria-label="待复制内容"></label>`);$('input',$('#modal')).select();}}
function navigate(page){state.page=page;state.filter='all';state.month='all';state.query='';state.order='new';history.replaceState(null,'','#'+page);render();window.scrollTo({top:0,behavior:'smooth'});}

document.addEventListener('click',event=>{
  const button=event.target.closest('[data-action]');if(!button||button.disabled)return;const action=button.dataset.action,id=Number(button.dataset.id);
  busy(button,async()=>{
    switch(action){
      case'login-tab':state.loginMode=button.dataset.mode;renderLogin();break;
      case'demo':await api('/auth/demo',{method:'POST'});state.page='timeline';await load();break;
      case'menu':$('.sidebar').classList.toggle('open');break;
      case'navigate':navigate(button.dataset.page);break;
      case'filter':state.filter=button.dataset.filter;render();break;
      case'metric':state.metric=button.dataset.metric;render();break;
      case'clear-search':state.query='';render();break;
      case'new-record':recordForm();break;
      case'new-milestone':recordForm(null,true);break;
      case'add-child':childForm();break;
      case'ai-diary':await window.GrowthAssistant.open({task:'diary',form:button.closest('form[data-form="record"]'),trigger:button});break;
      case'ai-measurements':await window.GrowthAssistant.open({task:'measurements',trigger:button});break;
      case'ai-settings':await window.GrowthAssistant.open({trigger:button});break;
      case'measurement':measurementForm();break;
      case'vaccine':vaccineForm();break;
      case'edit-measurement':measurementForm(id);break;
      case'delete-measurement':deleteGrowthData('measurement',id);break;
      case'edit-vaccine':vaccineForm(id);break;
      case'delete-vaccine':deleteGrowthData('vaccine',id);break;
      case'detail':detail(id);break;
      case'edit':recordForm(id);break;
      case'close':closeModal();break;
      case'view-child':state.childId=id;navigate('timeline');break;
      case'lightbox':lightbox(id);break;
      case'download-media':await downloadMedia(id);break;
      case'delete':confirmDialog('把这段回忆暂时收起来？','记录、照片和视频会一起进入回收站，管理员可以恢复。已经发出的分享链接会立即失效。',async()=>{await api('/records/'+id,{method:'DELETE'});closeModal();await load();toast('已经移入回收站');},'移入回收站');break;
      case'restore':await api(`/records/${id}/restore`,{method:'POST'});await load();toast('回忆已经回到时间线，旧分享仍然失效');break;
      case'purge':confirmDialog('确定永久删除这段回忆？','文字、评论和图片将永久清理，无法恢复。请确认已经保存好需要的内容。',async()=>{await api(`/records/${id}/purge`,{method:'DELETE'});closeModal();await load();toast('记录已彻底删除，照片和视频将在后台清理');},'永久删除');break;
      case'confirm':{const fn=confirmAction;if(fn)await fn();break;}
      case'share':shareForm(id);break;
      case'copy':await copy(button.dataset.value);break;
      case'copy-share':await copy(location.origin+'/share.html?token='+button.dataset.token);break;
      case'revoke':await api('/shares/'+id,{method:'DELETE'});await load();toast('分享已撤销');break;
      case'invite':{
        confirmDialog('生成家庭邀请码','新邀请码有效期为 7 天，生成后旧码立即失效。收到邀请码的家人可以在注册页面加入你的家庭。',async()=>{
          const result=await api('/family/invite',{method:'POST'});
          openModal('邀请家人，一起见证成长',`<p class="modal-intro">让家人打开网站，选择「创建 / 加入家庭」，注册时填写下方邀请码。</p><div class="success-message">家庭邀请码<input readonly aria-label="家庭邀请码" value="${esc(result.code)}"></div><p class="form-hint">有效期至 ${timeText(result.expiresAt)}。邀请码允许多人加入，请只交给你信任的家人。</p><div class="form-actions">${btn('复制邀请码','copy','share','',`data-value="${esc(result.code)}"`)}</div>`);
        },'生成邀请码');break;
      }
      case'remove-member':manageMember(id,true);break;
      case'member-role':manageMember(id);break;
      case'archive-child':manageChild(id);break;
      case'restore-child':manageChild(id,true);break;
      case'purge-child':purgeChild(id);break;
      case'read-notices':await api('/notices/read',{method:'POST'});await load();toast('消息都读过啦');break;
      case'export':{
        const childId=Number($('#export-child').value);const data=await api(`/children/${childId}/export`);const url=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:'application/json;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download=`拾光-成长记录-${today()}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);toast('成长数据已导出（不含照片和视频文件）');break;
      }
      case'account':await window.GrowthProfile.open();break;
      case'logout':await api('/auth/logout',{method:'POST'});state.data=null;state.childId=null;state.loginMode='login';closeModal();renderLogin();break;
      case'retry':await initialize();break;
    }
  });
});
document.addEventListener('change',event=>{
  const target=event.target;
  if(target.id==='child-select'){state.childId=Number(target.value);state.month='all';state.query='';render();}
  if(target.id==='month-select'){state.month=target.value;render();}
  if(target.id==='order-select'){state.order=target.value;render();}
  if(target.name==='files'){
    $('#file-preview').querySelectorAll('video').forEach(video=>video.pause());
    objectUrls.forEach(URL.revokeObjectURL);objectUrls=[];
    const files=[...target.files];
    try{validateMediaFiles(files);}catch(error){target.value='';$('#file-preview').innerHTML='';toast(error.message);return;}
    $('#file-preview').innerHTML=files.map(f=>{const url=URL.createObjectURL(f);objectUrls.push(url);return /\.mp4$/i.test(f.name)||f.type==='video/mp4'?`<div class="file-preview-video">${videoPlayer(url,'待上传视频预览')}<small>${esc(f.name)} · ${bytes(f.size)}</small></div>`:`<img src="${url}" alt="待上传图片预览">`;}).join('');
  }
});
document.addEventListener('input',event=>{
  if(event.target.id==='global-search'){
    state.query=event.target.value;
    if(!['timeline','gallery','milestones'].includes(state.page)){state.page='timeline';state.filter='all';state.month='all';render();const input=$('#global-search');input.focus();input.setSelectionRange?.(input.value.length,input.value.length);}
    else $('#main-content').innerHTML=pageContent();
  }
});
document.addEventListener('submit',event=>{
  const form=event.target.closest('form[data-form]');if(!form)return;event.preventDefault();const input=Object.fromEntries(new FormData(form));const button=$('button[type="submit"]',form);const type=form.dataset.form;
  busy(button,async()=>{
    $('.error-message',form)?.remove();
    if(type==='register'){
      if(!window.GrowthRegistration)throw new Error('注册页面尚未加载完成，请刷新后重试');
      await window.GrowthRegistration.submit(form,input,button);return;
    }
    if(type==='login'){
      await api('/auth/'+type,{method:'POST',body:input});state.page='timeline';state.childId=null;await load();return;
    }
    if(type==='record'){
      const files=[...$('input[name="files"]',form).files];
      validateMediaFiles(files);
      const tags=input.tags.split(/[,，]/).map(t=>t.trim()).filter(Boolean);
      if(tags.length>6||tags.some(t=>t.length>32))throw new Error('最多填写 6 个标签，每个标签不超过 32 个字符');
      const payload={childId:Number(input.childId),title:input.title,content:input.content,recordTime:input.recordTime,recordType:input.recordType,milestone:input.milestone,tags};
      const existing=form.dataset.id;const result=await api('/records'+(existing?'/'+existing:''),{method:existing?'PUT':'POST',body:payload});
      form.dataset.id=result.id; // 媒体失败时重试编辑已保存记录，避免重复创建文字记录。
      if(files.length){
        const data=new FormData();files.forEach(f=>data.append('files',f));
        const status=$('.upload-status',form);status.hidden=false;status.textContent='正在上传照片和视频，请保持页面打开…';button.textContent='上传中…';
        mediaUploading=true;
        const locked=[...$('#modal').querySelectorAll('button,input,select,textarea')].filter(control=>!control.disabled);
        locked.forEach(control=>control.disabled=true);
        try{await api(`/records/${result.id}/media`,{method:'POST',body:data});}
        catch(e){status.textContent='文字已保存，媒体上传未完成。';e.message='文字已保存，照片或视频未成功上传：'+e.message+'。可重新选择文件后再次保存。';throw e;}
        finally{mediaUploading=false;locked.forEach(control=>control.disabled=false);button.textContent='保存这段回忆';}
      }
      state.childId=payload.childId;closeModal();state.page='timeline';state.filter='all';state.month='all';state.query='';await load();toast('这一刻，已经好好收藏');return;
    }
    if(type==='child'){const result=await api('/children',{method:'POST',body:input});state.childId=result.id;closeModal();await load();toast('成长档案已创建');return;}
    if(type==='measurement'){const editing=form.dataset.id;await api('/measurements'+(editing?'/'+editing:''),{method:editing?'PUT':'POST',body:{...input,childId:Number(input.childId),height:Number(input.height),weight:Number(input.weight),head:input.head?Number(input.head):null}});state.childId=Number(input.childId);closeModal();await load();toast(editing?'成长测量已更新':'又一个成长刻度，记下来了');return;}
    if(type==='vaccine'){const editing=form.dataset.id;await api('/vaccines'+(editing?'/'+editing:''),{method:editing?'PUT':'POST',body:{...input,childId:Number(input.childId),doseNo:Number(input.doseNo)}});state.childId=Number(input.childId);closeModal();await load();toast(editing?'接种记录已更新':'接种记录已保存');return;}
    if(type==='comment'){const id=Number(form.dataset.id);await api(`/records/${id}/comments`,{method:'POST',body:input});await load();detail(id);toast('温暖的话，已经送达');return;}
    if(type==='share'){const result=await api(`/records/${form.dataset.id}/shares`,{method:'POST',body:{hours:Number(input.hours),maxAccess:Number(input.maxAccess)}});const url=location.origin+result.url;openModal('这一刻，准备好分享了',`<div class="success-message">分享链接已生成<input readonly aria-label="分享链接" value="${esc(url)}"></div><p class="form-hint">${timeText(result.expiresAt)} 到期。请把链接发给信任的人；管理员可以在空间设置中撤销。</p><div class="form-actions">${btn('复制分享链接','copy','share','',`data-value="${esc(url)}"`)}</div>`);await load();return;}
    if(type==='settings'){await api('/family',{method:'PUT',body:input});await load();toast('家庭名称已更新');}
  },form);
});
$('#modal').addEventListener('click',event=>{if(event.target===$('#modal')){const rect=event.target.getBoundingClientRect();if(event.clientX<rect.left||event.clientX>rect.right||event.clientY<rect.top||event.clientY>rect.bottom)closeModal();}});
$('#modal').addEventListener('close',()=>{$('#modal').querySelectorAll('video').forEach(video=>video.pause());objectUrls.forEach(URL.revokeObjectURL);objectUrls=[];});
document.addEventListener('error',event=>{if(event.target.tagName==='VIDEO'){const message=event.target.closest('.video-player')?.querySelector('.video-error');if(message)message.hidden=false;}},true);
$('#modal').addEventListener('cancel',event=>{if(mediaUploading){event.preventDefault();toast('照片和视频仍在上传，请等待完成');}});
window.addEventListener('hashchange',()=>{const page=location.hash.slice(1);if(state.data&&pages[page])navigate(page);});
async function initialize(){try{state.page=pages[location.hash.slice(1)]?location.hash.slice(1):'timeline';await load();}catch(e){if(window.GrowthAccountDeletion?.handle(e))return;if(e.status===401){state.data=null;renderLogin();}else{$('#app').innerHTML=`<div class="initial-loading">${brand()}<p>暂时没有连上你的家庭空间</p><p class="muted">请确认网站服务及数据库连接正常。</p>${btn('重新连接','retry','restore')}</div>`;toast(e.message);}}}
// 等待扩展脚本注册错误提示后再恢复会话，刷新时也能看到注销倒计时。
document.addEventListener('DOMContentLoaded',initialize,{once:true});
