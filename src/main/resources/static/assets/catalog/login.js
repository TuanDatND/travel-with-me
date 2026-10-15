import {$,api,run,shell,saveToken,returnPath} from './common.js';
run(async()=>{const account=await shell();if(account.role!=='PUBLIC')location.replace(returnPath(new URLSearchParams(location.search).get('returnTo')));})();
$('login').onsubmit=run(async()=>{
  const data=await api('/api/auth/login',{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData($('login'))))});
  saveToken(data.token);$('login').reset();
  const target=new URLSearchParams(location.search).get('returnTo');
  location.assign(target?returnPath(target):data.user.role==='ADMIN'?'/equipment-admin.html':data.user.role==='STAFF'?'/inventory.html':'/equipment.html');
});
