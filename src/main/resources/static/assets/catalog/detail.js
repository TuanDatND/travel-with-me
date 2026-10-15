import {$,api,el,money,run,shell,validId,equipmentArt} from './common.js';
let id;
run(async()=>{
  await shell();id=validId(new URLSearchParams(location.search).get('id'));
  const product=await api('/api/products/'+id);document.title=product.name+' · TreekTour';
  $('name').textContent=product.name;$('category').textContent=product.category||'THIẾT BỊ';$('sale').textContent=money(product.salePrice);$('rent').textContent=money(product.rentalPricePerDay);
  const art=equipmentArt(product.category);art.classList.add('large');art.id='art';$('art').replaceWith(art);$('detail').hidden=false;
  if(product.rentalPricePerDay==null){$('availability').hidden=true;$('noRental').hidden=false;return;}
  const branches=(await api('/api/branches')).filter(b=>b.status==='ACTIVE');
  for(const branch of branches){const option=el('option',branch.name);option.value=branch.id;$('availability').elements.branchId.append(option);}
  const now=new Date(),today=`${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`;
  for(const key of ['startDate','endDate']){$('availability').elements[key].min=today;$('availability').elements[key].value=today;}
  if(!branches.length){$('availability').hidden=true;$('noRental').hidden=false;$('noRental').textContent='Chưa có chi nhánh đang hoạt động để tra cứu.';}
})();
$('availability').onsubmit=run(async()=>{
  $('availabilityResult').hidden=true;
  const query=new URLSearchParams(new FormData($('availability')));query.set('productId',id);
  const data=await api('/api/inventory/availability?'+query);
  const result=$('availabilityResult');result.replaceChildren(el('span','SỐ LƯỢNG CÓ THỂ THUÊ','eyebrow'),el('strong',data.quantityRentable+' thiết bị'),
    el('p',`Còn sẵn: ${data.quantityAvailable} · Đặt trùng lịch cao nhất: ${data.peakReserved}`,'muted'),el('p','Kết quả tham khảo tại thời điểm tra cứu, chưa giữ chỗ.','muted'));result.hidden=false;
});
