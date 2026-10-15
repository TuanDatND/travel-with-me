export const $ = id => document.getElementById(id);
const sessionKey = 'treektour.catalog.token';
let token = sessionStorage.getItem(sessionKey);
export const money = value => value == null ? 'Không áp dụng' : new Intl.NumberFormat('vi-VN', {style:'currency', currency:'VND', maximumFractionDigits:0}).format(value);
export function el(tag, text, className) {
  const element = document.createElement(tag);
  if (text != null) element.textContent = text;
  if (className) element.className = className;
  return element;
}
export function link(text, href, className='') { const a=el('a',text,className); a.href=href; return a; }
export function notice(message, error=false) { $('notice').hidden=false; $('notice').textContent=message; $('notice').classList.toggle('error',error); }
export function saveToken(value) { token=value; if(value) sessionStorage.setItem(sessionKey,value); else sessionStorage.removeItem(sessionKey); }
export async function api(path, options={}) {
  const response=await fetch(path, {...options, headers:{'Content-Type':'application/json', ...(token?{Authorization:'Bearer '+token}:{}), ...options.headers}});
  const payload=await response.json();
  if(!response.ok) { const error=new Error(payload.message || 'Không thể thực hiện yêu cầu'); error.status=response.status; throw error; }
  return payload.data;
}
export const run = action => async event => {
  event?.preventDefault();
  const submit=event?.submitter;
  if(submit) submit.disabled=true;
  try { await action(event); } catch(error) { notice(error.message,true); }
  finally { if(submit) submit.disabled=false; }
};
export function actionButton(text, action, className='secondary small') { const b=el('button',text,className); b.type='button'; b.onclick=run(async()=>{b.disabled=true;try {await action();}finally{b.disabled=false;}}); return b; }
export function badge(status) { return el('span',status==='ACTIVE'?'Đang hoạt động':'Ngừng hoạt động','badge'+(status==='ACTIVE'?'':' off')); }
export function cell(row, content) { const td=el('td'); if(content instanceof Node) td.append(content); else td.textContent=content??'—'; row.append(td); return td; }
export function emptyTable(columns, text='Chưa có dữ liệu.') { const row=el('tr'),td=el('td',text,'muted');td.colSpan=columns;row.append(td);$('rows').replaceChildren(row); }
export function paging(data) { $('paging').textContent=`Trang ${data.page+1} / ${Math.max(1,data.totalPages)}`; $('prev').disabled=data.page===0; $('next').disabled=data.page+1>=data.totalPages; if($('count'))$('count').textContent=`${data.totalElements} kết quả`; }
export function formQuery(form) { return new URLSearchParams([...new FormData(form)].filter(([,value])=>value!=='')); }
export function validId(value) { if(!/^[1-9]\d*$/.test(value||'')) throw new Error('Mã thiết bị không hợp lệ.'); return value; }
export function equipmentArt(category) {
  const box=el('div',null,'product-art');
  // Hình minh họa cố định; dữ liệu sản phẩm chỉ được chèn qua textContent.
  box.innerHTML=category?.toLowerCase().includes('hiking')
    ? '<svg viewBox="0 0 140 120" aria-hidden="true"><path d="M45 27c0-14 50-14 50 0v70H45zM55 23V13h30v10M45 38H33v48h12M95 38h12v48H95M56 55h28v27H56zM56 68h28M52 96v8M88 96v8"/></svg>'
    : '<svg viewBox="0 0 140 120" aria-hidden="true"><path d="M15 95 67 25l58 70H15zM67 25l-8 70M67 25l28 70M59 95l20-38 16 38M10 101h120M67 25v-9"/></svg>';
  box.append(el('span','TREEKTOUR','art-label'));return box;
}
const destinations = new Set(['/equipment.html','/equipment-admin.html','/equipment-edit.html','/inventory.html','/equipment-detail.html']);
export function returnPath(value) { try { const url=new URL(value,location.origin); return url.origin===location.origin && destinations.has(url.pathname)?url.pathname+url.search:'/equipment.html'; } catch {return '/equipment.html';} }
export async function shell(roles=null) {
  const shell=$('shell');
  shell.innerHTML='<aside class="sidebar"><a class="brand" href="/equipment.html">TREEKTOUR<small>OUTDOOR & ADVENTURE</small></a><div class="nav-label">KHÁM PHÁ</div><nav aria-label="Điều hướng chính"><a data-nav="products" href="/equipment.html"><span class="nav-symbol">◇</span>Danh sách thiết bị</a></nav><div class="nav-label" id="managementLabel" hidden>QUẢN TRỊ</div><nav aria-label="Quản trị"><a data-nav="admin" href="/equipment-admin.html" hidden><span class="nav-symbol">▤</span>Quản lý thiết bị</a><a data-nav="inventory" href="/inventory.html" hidden><span class="nav-symbol">▦</span>Tồn kho chi nhánh</a></nav><div class="sidebar-footer">Chuẩn bị tốt hơn.<br>Khám phá xa hơn.</div></aside><div class="topbar"><span class="topbar-title">Thiết bị & tồn kho</span><div class="account" id="account"></div></div>';
  const active=shell.querySelector(`[data-nav="${document.body.dataset.page}"]`);if(active){active.classList.add('active');active.setAttribute('aria-current','page');}
  let account=null;
  if(token) { try { account=await api('/api/users/me'); if(account.status!=='ACTIVE') {saveToken(null);account=null;} } catch(error) { if(error.status===401||error.status===403) saveToken(null); else throw error; } }
  if(account) {
    const identity=el('div',account.fullName,'account-name');identity.append(el('small',account.role==='ADMIN'?'Quản trị viên':account.role==='STAFF'?'Nhân viên chi nhánh':'Khách hàng'));
    $('account').append(identity,actionButton('Đăng xuất',()=>{saveToken(null);location.assign('/equipment.html');}));
    if(account.role==='ADMIN')shell.querySelector('[data-nav="admin"]').hidden=false;
    if(['ADMIN','STAFF'].includes(account.role)){shell.querySelector('[data-nav="inventory"]').hidden=false;$('managementLabel').hidden=false;}
  } else $('account').append(link('Đăng nhập','/login.html?returnTo='+encodeURIComponent(location.pathname+location.search),'button secondary small'));
  if(roles && (!account || !roles.includes(account.role))) {
    if(!account){location.replace('/login.html?returnTo='+encodeURIComponent(location.pathname+location.search));return null;}
    notice('Tài khoản của anh không có quyền mở trang này.',true);return null;
  }
  return account || {role:'PUBLIC'};
}
