package com.openminis.app.mibot.vault

import org.json.JSONObject

/**
 * [fork/mibot-v5b2] The page-side half of the recipe engine. One function,
 * called with the merged recipe, an action and up to two arguments; returns
 * a JSON state: loading / verify / email_code / sms_code / login / in /
 * elsewhere, the captured keys and what it did. Only DOM reading, filling
 * the user's own values and clicking ordinary buttons — no captcha solving.
 */
object AutoKeyJs {
    fun script(recipe: JSONObject, action: String, a1: String? = null, a2: String? = null): String =
        "(" + FN + ")(" + recipe.toString() + "," + JSONObject.quote(action) + "," + JSONObject.quote(a1 ?: "") + "," + JSONObject.quote(a2 ?: "") + ")"

    private const val FN = """function(R,A,U,P){
function vis(e){try{if(!e||!e.getBoundingClientRect)return false;var r=e.getBoundingClientRect();if(r.width<2||r.height<2)return false;var s=getComputedStyle(e);return s.visibility!=='hidden'&&s.display!=='none'&&s.opacity!=='0';}catch(x){return false;}}
function all(sel,root){var out=[];(sel||[]).forEach(function(s){try{Array.prototype.forEach.call((root||document).querySelectorAll(s),function(e){if(out.indexOf(e)<0&&vis(e))out.push(e);});}catch(x){}});return out;}
function norm(t){return (t||'').replace(/\s+/g,' ').trim().toLowerCase();}
function inside(e,roots){return (roots||[]).some(function(d){return d!==e&&d.contains(e);});}
function byText(words,root,exclude){var passes=['button,a,[role=button],input[type=submit]','span,div,label,li,[role=menuitem]'];
 for(var p=0;p<passes.length;p++){var els=(root||document).querySelectorAll(passes[p]);
  for(var wi=0;wi<(words||[]).length;wi++){var w=norm(words[wi]);
   for(var i=0;i<els.length;i++){var e=els[i];if(!vis(e)||inside(e,exclude))continue;var t=norm(e.innerText||e.value||e.textContent);if(!t||t.length>40)continue;
    if(t===w||t==='+ '+w||t==='+'+w||t==='＋'+w){return e.closest('button,a,[role=button]')||e;}}}}
 return null;}
function setVal(el,v){try{var proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(proto,'value').set.call(el,v);}catch(x){el.value=v;}
 el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));}
function click(el){try{el.scrollIntoView({block:'center'});}catch(x){}try{['pointerdown','mousedown','pointerup','mouseup'].forEach(function(n){el.dispatchEvent(new MouseEvent(n,{bubbles:true}));});}catch(x){}try{el.click();}catch(x){}}
function dialogs(){return all(['[role=dialog]','[aria-modal=true]','.ant-modal','.el-dialog','.semi-modal','.arco-modal','[class*=modal]','[class*=Modal]','[class*=dialog]','[class*=Dialog]','[class*=drawer]','[class*=Drawer]']).filter(function(d){var r=d.getBoundingClientRect();return r.width>120&&r.height>60;});}
var rx=null;try{rx=new RegExp(R.keyRegex,'g');}catch(x){}
var W=window;
if(!W.__mb){W.__mb={seen:[],step:0,ts:0};
 var scan=function(s,via){if(!rx||typeof s!=='string'||s.length>3000000)return;var m=s.match(rx);if(m)m.forEach(function(k){if(!W.__mb.seen.some(function(o){return o.k===k;}))W.__mb.seen.push({k:k,via:via});});};
 W.__mb.scan=scan;
 try{if(navigator.clipboard&&navigator.clipboard.writeText){var cw=navigator.clipboard.writeText.bind(navigator.clipboard);navigator.clipboard.writeText=function(t){scan(String(t),'clipboard');return cw(t);};}}catch(x){}
 try{var ex=document.execCommand.bind(document);document.execCommand=function(c){if(String(c).toLowerCase()==='copy'){try{scan(String(window.getSelection()),'clipboard');var a=document.activeElement;if(a&&a.value)scan(a.value,'clipboard');}catch(x){}}return ex.apply(document,arguments);};}catch(x){}
 document.addEventListener('copy',function(){try{scan(String(window.getSelection()),'clipboard');}catch(x){}},true);
 if(R.keyScope!=='dialog'){
  try{var of=W.fetch;if(of){W.fetch=function(){return of.apply(this,arguments).then(function(r){try{r.clone().text().then(function(t){scan(t,'network');});}catch(x){}return r;});};}}catch(x){}
  try{var os=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.send=function(){var xh=this;xh.addEventListener('load',function(){try{if(!xh.responseType||xh.responseType==='text')scan(xh.responseText,'network');}catch(e){}});return os.apply(this,arguments);};}catch(x){}
 }}
var M=W.__mb,url=location.href,host=location.hostname.replace(/^www\./,''),body='';
try{body=(document.body&&document.body.innerText)||'';}catch(x){}
var res={url:url,host:host,title:document.title,acted:'',keys:[],state:'unknown'};
var dl=dialogs();
function scanEls(root,via){Array.prototype.forEach.call(root.querySelectorAll('input,textarea,code,pre,[class*=key],[class*=Key],[class*=secret],[class*=token]'),function(e){M.scan(String(e.value||''),via);M.scan(String(e.textContent||'').slice(0,4000),via);});}
if(R.keyScope==='dialog'){dl.forEach(function(d){M.scan(d.innerText||'','dialog');scanEls(d,'dialog');});}else{M.scan(body,'page');scanEls(document,'page');}
res.keys=M.seen.filter(function(o){return o.k.indexOf('*')<0&&o.k.indexOf('\u2026')<0&&o.k.indexOf('...')<0&&o.k.indexOf('\u2022')<0;});
var own=(R.hosts||[]).some(function(h){var hh=h.split('/')[0];return host===hh||host.slice(-(hh.length+1))==='.'+hh;});
var path=(location.pathname+location.search+location.hash).toLowerCase();
var low=body.toLowerCase();
var codeIn=all(['input[autocomplete=one-time-code]','input[name*=code i]','input[placeholder*=验证码]','input[placeholder*=code i]','input[name*=otp i]','input[maxlength="6"]']).filter(function(e){return e.type!=='hidden'&&e.type!=='password';});
var phoneWords=/短信|手机|sms|phone|mobile|text message/i.test(body), mailWords=/邮箱|邮件|e-?mail|inbox/i.test(body);
var captcha=all(R.verifySelectors).filter(function(e){return !(e.tagName==='INPUT');}).length>0||/人机验证|安全验证|拖动滑块|请完成验证|请完成安全验证|扫码登录|verify you are human|i'm not a robot/i.test(body);
var pass=all(R.loginSelectors);
var loginUrl=(R.loginUrl||[]).some(function(p){return path.indexOf(p)>=0;});
var emailIn=all(['input[type=email]','input[name*=email i]','input[placeholder*=邮箱]','input[placeholder*=mail i]']).filter(function(e){return e.type!=='hidden';});
res.emailEntry=emailIn.length>0&&pass.length===0;
res.disposableRejected=/不支持.{0,8}(邮箱|该邮箱|此邮箱)|临时邮箱|一次性邮箱|disposable|temporary e-?mail|email (domain )?(is )?not (allowed|supported)|use a different email|invalid email domain/i.test(body);
res.onKeys=own&&R.keysMatch?new RegExp(R.keysMatch,'i').test(url):false;
if(document.readyState==='loading')res.state='loading';
else if(captcha)res.state='verify';
else if(codeIn.length&&mailWords&&!phoneWords)res.state='email_code';
else if(codeIn.length&&phoneWords)res.state='sms_code';
else if(pass.length||emailIn.length||(loginUrl&&all(['input']).length>0))res.state='login';
else if(own)res.state='in';
else res.state='elsewhere';
function submitNear(el,words){var sb=null;var f=el&&el.form;if(f)sb=f.querySelector('button[type=submit],input[type=submit]');return sb||byText(words);}
if(A==='fill'&&res.state==='login'&&U){var p=pass[0];var users=all(R.userSelectors).filter(function(e){return e!==p&&e.type!=='password'&&e.type!=='hidden'&&e.type!=='checkbox';});
 if(users.length)setVal(users[0],U);if(p&&P)setVal(p,P);var sb=submitNear(p||users[0],R.submitText);if(sb){setTimeout(function(){click(sb);},350);res.acted='filled';}else res.acted='filled-nosubmit';}
if(A==='email'&&U&&emailIn.length){setVal(emailIn[0],U);var b2=byText(['发送验证码','获取验证码','发送','Send code','Send verification code','Get code','Send link','Continue with email','Continue','继续','下一步','Next','登录','注册','Sign up','Sign in']);if(b2){setTimeout(function(){click(b2);},350);res.acted='email';}else res.acted='email-nosubmit';}
if(A==='code'&&U&&codeIn.length){if(codeIn.length>=U.length&&codeIn[0].maxLength===1){for(var ci=0;ci<U.length;ci++)setVal(codeIn[ci],U[ci]);}else setVal(codeIn[0],U);
 var b3=submitNear(codeIn[0],['确定','确认','验证','登录','提交','Verify','Continue','Submit','Sign in','Next','继续','下一步']);if(b3){setTimeout(function(){click(b3);},400);}res.acted='code';}
if(A==='nav'&&res.state==='in'){var n=byText(R.navText||['API Keys','API keys','API Key','API key','API 密钥','API密钥','API Key 管理','密钥管理','Keys']);if(n){click(n);res.acted='nav';}else res.acted='no-nav';}
if(A==='create'&&res.state==='in'&&res.onKeys){var now=Date.now();
 if(M.step===0){var b=byText(R.createText,document,dl);if(b){click(b);M.step=1;M.ts=now;res.acted='create';}else res.acted='no-create';}
 else if(M.step===1&&now-M.ts>900){var inp=all(R.nameSelectors).filter(function(e){return !e.value&&e.type!=='hidden'&&!e.readOnly;})[0];if(inp){setVal(inp,R.keyName||'Mibot');res.acted='name';}M.step=2;M.ts=now;}
 else if(M.step===2&&now-M.ts>500){var d2=dialogs();var c=null;for(var i=0;i<d2.length&&!c;i++)c=byText(R.confirmText,d2[i]);if(c){click(c);res.acted='confirm';}else res.acted='no-confirm';M.step=3;M.ts=now;}
 else if(M.step===3&&now-M.ts>6000){var d3=dialogs();var cp=null;for(var j=0;j<d3.length&&!cp;j++)cp=byText(R.copyText,d3[j]);if(cp){click(cp);res.acted='copy';}M.step=4;M.ts=now;}
 else if(M.step===4&&now-M.ts>8000){res.acted='exhausted';}
 else res.acted='wait';res.step=M.step;}
return JSON.stringify(res);}"""
}
