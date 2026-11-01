const JSON_HEADERS = { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" };
const json = (data, status=200) => new Response(JSON.stringify(data), { status, headers: JSON_HEADERS });
const b64url = (bytes) => btoa(String.fromCharCode(...bytes)).replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"");
async function randomToken() { const b=new Uint8Array(32); crypto.getRandomValues(b); return b64url(b); }
async function googleToken(env, refreshToken) {
  const body=new URLSearchParams({client_id:env.GOOGLE_CLIENT_ID,client_secret:env.GOOGLE_CLIENT_SECRET,refresh_token:refreshToken,grant_type:"refresh_token"});
  const r=await fetch("https://oauth2.googleapis.com/token",{method:"POST",headers:{"content-type":"application/x-www-form-urlencoded"},body});
  const d=await r.json(); if(!r.ok) throw new Error(d.error_description||d.error||"Google token refresh failed"); return d.access_token;
}
async function api(env, session, path, init={}) {
  const saved=await env.OAUTH_KV.get("session:"+session); if(!saved) return json({error:"unauthorized"},401);
  const record=JSON.parse(saved); const access=await googleToken(env,record.refresh_token);
  const r=await fetch("https://www.googleapis.com"+path,{...init,headers:{...(init.headers||{}),authorization:"Bearer "+access}});
  const body=await r.text(); return new Response(body,{status:r.status,headers:{"content-type":r.headers.get("content-type")||"application/json","cache-control":"no-store"}});
}
export default {
 async fetch(request,env) {
  const u=new URL(request.url), p=u.pathname;
  try {
   if(request.method==="GET" && p==="/health") return json({ok:true,service:"photos-cleaner-google-api",oauthConfigured:!!(env.GOOGLE_CLIENT_ID&&env.GOOGLE_CLIENT_SECRET)});
   if(request.method==="GET" && p==="/auth/google") {
    const state=await randomToken(); await env.OAUTH_KV.put("state:"+state,"1",{expirationTtl:600});
    const redirect=u.origin+"/oauth/callback";
    const scopes=["openid","email","https://www.googleapis.com/auth/drive.metadata.readonly","https://www.googleapis.com/auth/drive","https://www.googleapis.com/auth/gmail.modify"];
    const q=new URLSearchParams({client_id:env.GOOGLE_CLIENT_ID,redirect_uri:redirect,response_type:"code",scope:scopes.join(" "),access_type:"offline",prompt:"consent",state});
    return Response.redirect("https://accounts.google.com/o/oauth2/v2/auth?"+q.toString(),302);
   }
   if(request.method==="GET" && p==="/oauth/callback") {
    const state=u.searchParams.get("state"), code=u.searchParams.get("code");
    if(!state||!code||!await env.OAUTH_KV.get("state:"+state)) return json({error:"invalid_state"},400);
    await env.OAUTH_KV.delete("state:"+state);
    const redirect=u.origin+"/oauth/callback";
    const body=new URLSearchParams({code,client_id:env.GOOGLE_CLIENT_ID,client_secret:env.GOOGLE_CLIENT_SECRET,redirect_uri:redirect,grant_type:"authorization_code"});
    const r=await fetch("https://oauth2.googleapis.com/token",{method:"POST",headers:{"content-type":"application/x-www-form-urlencoded"},body});
    const d=await r.json(); if(!r.ok||!d.refresh_token) return json({error:"token_exchange_failed",details:d.error_description||d.error||"No refresh token"},400);
    const session=await randomToken(); await env.OAUTH_KV.put("session:"+session,JSON.stringify({refresh_token:d.refresh_token}),{expirationTtl:2592000});
    return new Response("Połączono konto Google. Wróć do aplikacji Photos Cleaner i wklej kod sesji: "+session,{headers:{"content-type":"text/plain; charset=utf-8","cache-control":"no-store"}});
   }
   const session=request.headers.get("authorization")?.replace(/^Bearer\s+/i,"");
   if(!session) return json({error:"missing_session"},401);
   if(request.method==="GET" && p==="/api/drive/files") return api(env,session,"/drive/v3/files?"+new URLSearchParams({pageSize:"100",orderBy:"name",fields:"files(id,name,mimeType,size,md5Checksum,trashed,webViewLink),nextPageToken",q:"trashed=false"}));
   if(request.method==="GET" && p==="/api/gmail/messages") return api(env,session,"/gmail/v1/users/me/messages?"+new URLSearchParams({maxResults:"10",q:"in:anywhere -in:trash"}));
   if(request.method==="GET" && p==="/api/gmail/message") {
    const id=u.searchParams.get("id"); if(!id||!/^[A-Za-z0-9_-]+$/.test(id)) return json({error:"invalid_id"},400);
    return api(env,session,"/gmail/v1/users/me/messages/"+id+"?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date");
   }
   if(request.method==="POST" && p==="/api/drive/trash") {
    const d=await request.json(); if(!Array.isArray(d.ids)||!d.ids.length||d.ids.length>10) return json({error:"invalid_ids"},400);
    const results=[]; for(const id of d.ids){if(typeof id!=="string"||!/^[A-Za-z0-9_-]+$/.test(id)){results.push({id,ok:false});continue;} const r=await api(env,session,"/drive/v3/files/"+id,{method:"PATCH",headers:{"content-type":"application/json"},body:JSON.stringify({trashed:true})});results.push({id,ok:r.ok});}
    return json({results});
   }
   if(request.method==="POST" && p==="/api/gmail/trash") {
    const d=await request.json(); if(!Array.isArray(d.ids)||!d.ids.length||d.ids.length>10) return json({error:"invalid_ids"},400);
    const results=[]; for(const id of d.ids){if(typeof id!=="string"||!/^[A-Za-z0-9_-]+$/.test(id)){results.push({id,ok:false});continue;} const r=await api(env,session,"/gmail/v1/users/me/messages/"+id+"/trash",{method:"POST"});results.push({id,ok:r.ok});}
    return json({results});
   }
   return json({error:"not_found"},404);
  } catch(e) { return json({error:"server_error",message:String(e.message||e)},500); }
 }
};
