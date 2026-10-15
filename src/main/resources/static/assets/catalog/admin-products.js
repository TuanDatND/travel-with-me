import {$,api,el,link,money,run,shell,formQuery,paging,badge,cell,actionButton,emptyTable} from './common.js';
let page=0, request=0;
async function load(){
  const version=++request,query=formQuery($('filters'));query.set('page',page);query.set('size',20);
  const data=await api('/api/admin/products?'+query);if(version!==request)return;$('rows').replaceChildren();
  for(const product of data.content){const row=el('tr'),name=el('div',product.name);name.append(el('small','Mã #'+product.id));cell(row,name);cell(row,product.category);cell(row,money(product.salePrice));cell(row,money(product.rentalPricePerDay));cell(row,badge(product.status));
    const actions=el('div',null,'actions');actions.append(link('Chỉnh sửa','/equipment-edit.html?id='+product.id,'button secondary small'),actionButton(product.status==='ACTIVE'?'Ngừng hoạt động':'Kích hoạt',async()=>{await api('/api/admin/products/'+product.id+'/status',{method:'PATCH',body:JSON.stringify({status:product.status==='ACTIVE'?'INACTIVE':'ACTIVE'})});await load();}));cell(row,actions);$('rows').append(row);}
  if(!data.content.length)emptyTable(6,'Không có thiết bị phù hợp.');paging(data);
}
$('filters').onsubmit=run(async()=>{page=0;await load();});$('prev').onclick=run(async()=>{page--;await load();});$('next').onclick=run(async()=>{page++;await load();});
run(async()=>{if(!await shell(['ADMIN']))return;$('workspace').hidden=false;await load();})();
