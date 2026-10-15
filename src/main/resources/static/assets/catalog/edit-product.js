import {$,api,run,shell,validId,notice} from './common.js';
let id;
run(async()=>{
  if(!await shell(['ADMIN']))return;
  const value=new URLSearchParams(location.search).get('id');id=value==null?null:validId(value);
  if(id){const product=await api('/api/admin/products/'+id);$('heading').textContent='Chỉnh sửa thiết bị';document.title='Chỉnh sửa thiết bị · TreekTour';for(const key of ['name','category','salePrice','rentalPricePerDay'])$('productForm').elements[key].value=product[key]??'';}
  $('workspace').hidden=false;
})();
$('productForm').onsubmit=run(async()=>{
  const values=Object.fromEntries(new FormData($('productForm')));
  if(values.salePrice===''&&values.rentalPricePerDay===''){notice('Vui lòng nhập giá bán hoặc giá thuê.',true);return;}
  for(const key of ['salePrice','rentalPricePerDay'])values[key]=values[key]===''?null:values[key];
  await api('/api/admin/products'+(id?'/'+id:''),{method:id?'PUT':'POST',body:JSON.stringify(values)});location.assign('/equipment-admin.html');
});
