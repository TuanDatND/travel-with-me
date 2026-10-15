import {$,api,el,link,money,run,shell,formQuery,paging,equipmentArt} from './common.js';
let page=0, request=0;
async function load(){
  const version=++request, query=formQuery($('filters'));query.set('page',page);query.set('size',12);
  const data=await api('/api/products?'+query);if(version!==request)return;
  $('products').replaceChildren();
  for(const product of data.content){
    const card=el('article',null,'product-card'), body=el('div',null,'product-body'), prices=el('div',null,'prices');
    for(const [label,value] of [['Giá bán',product.salePrice],['Thuê / ngày',product.rentalPricePerDay]]){const block=el('div');block.append(el('small',label),el('strong',money(value)));prices.append(block);}
    body.append(el('span',product.category||'Thiết bị','tag'),el('h3',product.name),prices,link('Xem chi tiết →','/equipment-detail.html?id='+product.id,'button secondary card-link'));
    card.append(equipmentArt(product.category),body);$('products').append(card);
  }
  if(!data.content.length)$('products').append(el('div','Không tìm thấy thiết bị phù hợp. Thử thay đổi bộ lọc.','empty'));
  paging(data);
}
$('filters').onsubmit=run(async()=>{page=0;await load();});
$('prev').onclick=run(async()=>{page--;await load();});$('next').onclick=run(async()=>{page++;await load();});
run(async()=>{await shell();await load();})();
