import {$,api,el,run,shell,paging,badge,cell,actionButton,emptyTable,notice} from './common.js';
let page=0,request=0;
const productOptions=new Map();
function option(id,name){if(productOptions.has(String(id)))return;const element=el('option',name+' (#'+id+')');element.value=id;$('stockProduct').append(element);productOptions.set(String(id),element);}
function mode(){const adjust=$('stockOperation').value==='adjust';$('quantityLabel').hidden=adjust;$('stockForm').elements.quantity.disabled=adjust;$('totalLabel').hidden=!adjust;$('availableLabel').hidden=!adjust;for(const key of ['quantityTotal','quantityAvailable']){$('stockForm').elements[key].disabled=!adjust;$('stockForm').elements[key].required=adjust;}}
async function load(){
  const version=++request,branch=$('stockBranch').value;$('rows').replaceChildren();$('prev').disabled=true;$('next').disabled=true;$('count').textContent='';$('paging').textContent='';
  if(!branch){emptyTable(6,'Chưa có chi nhánh đang hoạt động.');return;}
  try{
    const data=await api('/api/inventory/branches/'+branch+'?page='+page+'&size=20');if(version!==request)return;
    for(const item of data.content){option(item.productId,item.productName);const row=el('tr'),name=el('div',item.productName);name.append(el('small','Mã #'+item.productId));cell(row,name);cell(row,item.quantityTotal);cell(row,item.quantityAvailable);cell(row,item.quantityRented);cell(row,badge(item.status));
      const actions=el('div',null,'actions');actions.append(actionButton('Kiểm kê',()=>{$('stockProduct').value=item.productId;$('stockOperation').value='adjust';mode();$('stockForm').elements.quantityTotal.value=item.quantityTotal;$('stockForm').elements.quantityAvailable.value=item.quantityAvailable;$('stockForm').scrollIntoView({behavior:'smooth',block:'center'});}),actionButton(item.status==='ACTIVE'?'Tạm dừng':'Mở lại',async()=>{await api('/api/inventory/branches/'+branch+'/products/'+item.productId+'/status',{method:'PATCH',body:JSON.stringify({status:item.status==='ACTIVE'?'INACTIVE':'ACTIVE'})});await load();}));cell(row,actions);$('rows').append(row);}
    if(!data.content.length)emptyTable(6,'Kho chưa có thiết bị. Chọn thiết bị bên dưới để nhập kho.');paging(data);
  }catch(error){if(version===request){emptyTable(6,'Không thể tải kho của chi nhánh này.');throw error;}}
}
$('stockOperation').onchange=mode;
$('stockBranch').onchange=run(async()=>{page=0;await load();});$('stockFilter').onsubmit=run(async()=>{page=0;await load();});
$('prev').onclick=run(async()=>{page--;await load();});$('next').onclick=run(async()=>{page++;await load();});
$('stockForm').onsubmit=run(async()=>{
  const branch=$('stockBranch').value;if(!branch)throw new Error('Vui lòng chọn chi nhánh.');
  const values=Object.fromEntries(new FormData($('stockForm'))),isImport=values.operation==='import';
  const body=isImport?{quantity:Number(values.quantity)}:{quantityTotal:Number(values.quantityTotal),quantityAvailable:Number(values.quantityAvailable)};
  await api('/api/inventory/branches/'+branch+'/products/'+values.productId+(isImport?'/import':''),{method:isImport?'POST':'PUT',body:JSON.stringify(body)});await load();notice('Đã cập nhật tồn kho.');
});
run(async()=>{
  const account=await shell(['ADMIN','STAFF']);if(!account)return;$('workspace').hidden=false;mode();
  const branches=(await api('/api/branches')).filter(b=>b.status==='ACTIVE');
  for(const branch of branches){const opt=el('option',branch.name);opt.value=branch.id;$('stockBranch').append(opt);}
  // API phân trang; tải từng trang để không mất thiết bị ngoài 100 dòng đầu.
  let index=0,data;
  do{data=await api((account.role==='ADMIN'?'/api/admin/products':'/api/products')+'?page='+index+'&size=100');for(const product of data.content)option(product.id,product.name);index++;}while(index<data.totalPages);
  await load();
})();
