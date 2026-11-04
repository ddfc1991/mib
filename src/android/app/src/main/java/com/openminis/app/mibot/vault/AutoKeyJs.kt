package com.openminis.app.mibot.vault

import org.json.JSONObject

/**
 * [fork/mibot-autokey-v2] The page-side half of the recipe engine.
 *
 * [capture] is injected at DOCUMENT START in every frame (androidx.webkit
 * addDocumentStartJavaScript) before any site code runs: it wraps fetch /
 * XMLHttpRequest, the clipboard APIs (writeText, execCommand('copy'),
 * DataTransfer.setData, copy events) and watches the DOM with a
 * MutationObserver, including shadow roots (attachShadow is wrapped so
 * closed roots are observed too). Each new match of the recipe's key regex
 * is pushed to native through the `mibotKey` WebMessageListener. Where the
 * WebView lacks those features, [script] installs the same layer late and
 * the engine reads `window.__mbc.seen` on each poll.
 *
 * [script] runs one action (detect / fill / email / code / nav /
 * click_create / name / confirm / reveal) and returns a JSON observation.
 * `click_create` is the one-shot click the v2 engine (CreateFlow) drives.
 * `create` is kept for the v1 loop in AutoKey.fetchKey: a small page-side
 * stepper over the same helpers (create, name, confirm, copy, then
 * "exhausted"; at most 3 create clicks, never one after confirm) returning
 * the acted values that loop already understands. Only DOM reading,
 * filling the user's own values and clicking ordinary buttons: no captcha
 * solving, and never a password on a key page.
 */
object AutoKeyJs {
    /** The document-start capture layer for one recipe regex. */
    fun capture(keyRegex: String): String = CAPTURE.replace("__RX__", JSONObject.quote(keyRegex))

    fun script(recipe: JSONObject, action: String, a1: String? = null, a2: String? = null): String =
        "(function(){try{" + capture(recipe.optString("keyRegex")) + "}catch(e){}})();" +
            "(" + FN + ")(" + recipe.toString() + "," + JSONObject.quote(action) + "," + JSONObject.quote(a1 ?: "") + "," + JSONObject.quote(a2 ?: "") + ")"

    internal const val CAPTURE = """(function(){var W=window;if(W.__mbc)return;var rx;try{rx=new RegExp(__RX__,'g');}catch(e){return;}
var C=W.__mbc={seen:[],lastMut:Date.now(),roots:[],t0:Date.now()},known={};
function masked(k){return /[*\u2022\u2026\u25cf]|\.\.\./.test(k);}
function post(k,via,src){if(known[k]||masked(k)||k.length<16)return;known[k]=1;var o={k:k,via:via,src:String(src||'').slice(0,200),t:Date.now(),main:W===W.top};C.seen.push(o);
 try{if(W.mibotKey&&W.mibotKey.postMessage)W.mibotKey.postMessage(JSON.stringify(o));}catch(e){}}
var KC=/[A-Za-z0-9_\-*\u2022\u2026\u25cf\u00b7]/;
function edge(s,i,j){if(i>0&&KC.test(s.charAt(i-1)))return true;if(j<s.length&&KC.test(s.charAt(j)))return true;if(s.slice(Math.max(0,i-3),i)==='...'||s.slice(j,j+3)==='...')return true;return false;}
function scan(s,via,src){if(typeof s!=='string'||!s||s.length>3000000)return;rx.lastIndex=0;var m,n=0;while((m=rx.exec(s))&&n<500){n++;if(!m[0]){rx.lastIndex++;continue;}if(!edge(s,m.index,m.index+m[0].length))post(m[0],via,src);}}
C.scan=scan;
try{var of=W.fetch;if(of){W.fetch=function(input,init){var u='';try{u=typeof input==='string'?input:(input&&input.url)||'';}catch(e){}
 return of.apply(this,arguments).then(function(r){try{var ct=(r.headers&&r.headers.get('content-type'))||'';if(!ct||/json|text|javascript/i.test(ct)){r.clone().text().then(function(t){scan(t,'network',u);},function(){});}}catch(e){}return r;});};}}catch(e){}
try{var XP=XMLHttpRequest.prototype,oo=XP.open,os=XP.send;XP.open=function(m,u){try{this.__mbu=String(u);}catch(e){}return oo.apply(this,arguments);};
 XP.send=function(){var xh=this;try{xh.addEventListener('load',function(){try{var t=xh.responseType;if(!t||t==='text')scan(xh.responseText,'network',xh.__mbu);else if(t==='json')scan(JSON.stringify(xh.response),'network',xh.__mbu);}catch(e){}});}catch(e){}return os.apply(this,arguments);};}catch(e){}
try{var nc=navigator.clipboard;if(nc&&nc.writeText){var cw=nc.writeText.bind(nc);nc.writeText=function(t){scan(String(t),'clipboard','writeText');return cw(t);};}}catch(e){}
try{var ex=Document.prototype.execCommand;Document.prototype.execCommand=function(c){try{if(String(c).toLowerCase()==='copy'){scan(String(W.getSelection()),'clipboard','execCommand');var a=document.activeElement;if(a&&typeof a.value==='string')scan(a.value,'clipboard','execCommand');}}catch(e){}return ex.apply(this,arguments);};}catch(e){}
try{var sd=DataTransfer.prototype.setData;DataTransfer.prototype.setData=function(f,v){try{scan(String(v),'clipboard','setData');}catch(e){}return sd.apply(this,arguments);};}catch(e){}
try{document.addEventListener('copy',function(){try{scan(String(W.getSelection()),'clipboard','copy');}catch(e){}},true);}catch(e){}
function scanNode(n){try{if(!n)return;if(n.nodeType===3){scan(n.data,'page','text');return;}if(n.nodeType!==1)return;
 var t=n.tagName;if(t==='SCRIPT'||t==='STYLE'||t==='NOSCRIPT')return;if(typeof n.value==='string'&&n.value)scan(n.value,'page','value');
 var tc=n.textContent;if(tc&&tc.length<200000)scan(tc,'page','node');
 if(n.querySelectorAll){var ins=n.querySelectorAll('input,textarea');for(var i=0;i<ins.length&&i<200;i++)if(ins[i].value)scan(ins[i].value,'page','value');}}catch(e){}}
var pend=[],sched=false;
function flush(){sched=false;var b=pend;pend=[];for(var i=0;i<b.length&&i<400;i++)scanNode(b[i]);}
var mo=null;try{mo=new MutationObserver(function(ms){C.lastMut=Date.now();for(var i=0;i<ms.length;i++){var m=ms[i];
 if(m.type==='characterData')pend.push(m.target);else if(m.type==='attributes')pend.push(m.target);else for(var j=0;j<m.addedNodes.length;j++)pend.push(m.addedNodes[j]);}
 if(!sched&&pend.length){sched=true;setTimeout(flush,120);}});}catch(e){}
function watch(root){try{if(mo&&root)mo.observe(root,{childList:true,subtree:true,characterData:true,attributes:true,attributeFilter:['value','data-key','title']});}catch(e){}}
C.watch=watch;watch(document);
try{var as=Element.prototype.attachShadow;Element.prototype.attachShadow=function(){var sr=as.apply(this,arguments);try{C.roots.push(sr);watch(sr);}catch(e){}return sr;};}catch(e){}
})();"""

    private const val FN = """function(R,A,U,P){
var W=window,C=W.__mbc||{seen:[],lastMut:0,roots:[],t0:Date.now(),scan:function(){}};
function vis(e){try{if(!e||!e.getBoundingClientRect)return false;var r=e.getBoundingClientRect();if(r.width<2||r.height<2)return false;var s=getComputedStyle(e);return s.visibility!=='hidden'&&s.display!=='none'&&parseFloat(s.opacity||'1')>0.05;}catch(x){return false;}}
function roots(){var out=[document];(C.roots||[]).forEach(function(r){if(out.indexOf(r)<0)out.push(r);});
 try{var all=document.querySelectorAll('*');for(var i=0;i<all.length&&i<5000;i++){var sr=all[i].shadowRoot;if(sr&&out.indexOf(sr)<0)out.push(sr);}}catch(x){}return out;}
var RT=roots();
function qa(sel,root){var out=[];var rs=root?[root]:RT;rs.forEach(function(rt){try{Array.prototype.forEach.call(rt.querySelectorAll(sel),function(e){if(out.indexOf(e)<0)out.push(e);});}catch(x){}});return out;}
function all(sels,root){var out=[];(sels||[]).forEach(function(s){qa(s,root).forEach(function(e){if(out.indexOf(e)<0&&vis(e))out.push(e);});});return out;}
function norm(t){return (t||'').replace(/\s+/g,' ').trim().toLowerCase();}
function inside(e,list){return (list||[]).some(function(d){return d!==e&&d.contains(e);});}
function label(e){return norm(e.innerText||e.value||e.textContent||'');}
function attrs(e){return norm((e.getAttribute&&((e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')))||'');}
function byText(words,root,exclude){var passes=['button,a,[role=button],[role=menuitem],[role=tab],input[type=submit],input[type=button]','span,div,label,li'];
 for(var p=0;p<passes.length;p++){var els=qa(passes[p],root);
  for(var wi=0;wi<(words||[]).length;wi++){var w=norm(words[wi]);if(!w)continue;
   for(var i=0;i<els.length;i++){var e=els[i];if(!vis(e)||inside(e,exclude))continue;var t=label(e);
    if(t&&t.length<=40&&(t===w||t==='+ '+w||t==='+'+w||t==='\uff0b'+w||t==='\uff0b '+w))return e.closest('button,a,[role=button]')||e;
    if(p===0){var at=attrs(e);if(at&&(at===w||at.indexOf(w)===0))return e;}}}}
 return null;}
function setVal(el,v){try{var proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(proto,'value').set.call(el,v);}catch(x){el.value=v;}
 el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));}
function click(el){if(!el)return false;var now=Date.now(),last=+(el.getAttribute&&el.getAttribute('data-mb-t')||0);if(now-last<1500)return false;try{el.setAttribute('data-mb-t',String(now));}catch(x){}
 try{el.scrollIntoView({block:'center'});}catch(x){}try{['pointerdown','mousedown','pointerup','mouseup'].forEach(function(n){el.dispatchEvent(new MouseEvent(n,{bubbles:true,cancelable:true}));});}catch(x){}try{el.click();}catch(x){}return true;}
function dialogs(){return all(['[role=dialog]','[role=alertdialog]','[aria-modal=true]','dialog[open]','.ant-modal','.ant-drawer-content','.el-dialog','.el-drawer','.semi-modal','.semi-sidesheet','.arco-modal','.arco-drawer','[class*=modal]','[class*=Modal]','[class*=dialog]','[class*=Dialog]','[class*=drawer]','[class*=Drawer]','[data-state=open]']).filter(function(d){var r=d.getBoundingClientRect();return r.width>120&&r.height>60&&d.querySelector('input,button,textarea,code,pre');})
 .filter(function(d,i,arr){return !arr.some(function(o){return o!==d&&d.contains(o);});});}
var url=location.href,host=location.hostname.replace(/^www\./,''),body='';
try{body=(document.body&&document.body.innerText)||'';}catch(x){}
var shadowText='';RT.forEach(function(r){if(r!==document){try{shadowText+=' '+(r.textContent||'').slice(0,20000);}catch(x){}}});
var text=body+shadowText,low=text.toLowerCase();
var path=(location.pathname+location.search+location.hash).toLowerCase();
var res={url:url,host:host,title:document.title,path:location.pathname,acted:'',keys:[],state:'unknown'};
var dl=dialogs();
function scanEls(root,via){qa('input,textarea,code,pre,[class*=key],[class*=Key],[class*=secret],[class*=token]',root).forEach(function(e){C.scan(String(e.value||''),via,'el');C.scan(String(e.textContent||'').slice(0,4000),via,'el');});}
dl.forEach(function(d){C.scan(d.innerText||'','dialog','dialog');scanEls(d,'dialog');});
if(R.keyScope!=='dialog'){C.scan(text,'page','body');scanEls(null,'page');}
res.keys=(C.seen||[]).slice(-20);
var own=(R.hosts||[]).some(function(h){var hh=String(h).split('/')[0].replace(/^www\./,'');return host===hh||host.slice(-(hh.length+1))==='.'+hh;});
var loginUrl=(R.loginUrl||[]).some(function(p){return path.indexOf(p)>=0;});
var onKeys=false;try{onKeys=own&&!!R.keysMatch&&new RegExp(R.keysMatch,'i').test(url);}catch(x){}
res.onKeys=onKeys;res.own=own;
var t0=0;try{t0=performance.now();}catch(x){}
var lastMut=C.lastMut||0,quiet=Date.now()-lastMut>800;
var spin=all(['[class*=spin]','[class*=Spin]','[class*=loading]','[class*=Loading]','[aria-busy=true]','[class*=skeleton]']).some(function(e){var r=e.getBoundingClientRect();return r.width*r.height>W.innerWidth*W.innerHeight*0.5;});
var inputsVis=all(['input:not([type=hidden])','textarea','button']).length;
if(!W.__mbFirst&&text.replace(/\s/g,'').length>30)W.__mbFirst=Date.now();
var rendered=document.readyState!=='loading'&&!spin&&(text.replace(/\s/g,'').length>30||inputsVis>1)&&(quiet||(W.__mbFirst&&Date.now()-W.__mbFirst>6000));
res.rendered=!!rendered;
var challenge=/just a moment|checking your browser|verifying you are human|checking if the site connection is secure|请稍候|正在验证您的浏览器|安全检查中/i.test(document.title+' '+text.slice(0,600))||
 qa('#challenge-form,#cf-challenge-running,[id^=cf-chl],#challenge-stage,script[src*="challenge-platform"]').length>0||
 (document.readyState==='complete'&&t0>5000&&text.replace(/\s/g,'').length===0&&inputsVis===0&&!dl.length);
var widgets=all(R.verifySelectors).filter(function(e){if(e.tagName==='INPUT')return false;if(e.tagName!=='IFRAME'&&/qr.?code/i.test(String(e.id||'')+' '+String(e.className&&e.className.baseVal!==undefined?e.className.baseVal:e.className||'')))return false;var r=e.getBoundingClientRect();return r.width>=40&&r.height>=20;});
var pass=all(R.loginSelectors).filter(function(p){return !inside(p,dl)||!onKeys;});
var users=all(R.userSelectors).filter(function(e){return e.type!=='password'&&e.type!=='hidden'&&e.type!=='checkbox'&&e.type!=='radio'&&!e.readOnly;});
function formOf(e){return e.closest('form')||(e.parentElement&&e.parentElement.parentElement&&e.parentElement.parentElement.parentElement)||document.body;}
var loginPass=pass.filter(function(p){if(loginUrl)return true;if(onKeys)return false;var f=formOf(p);return users.some(function(u){return f.contains(u);});});
var emailIn=all(['input[type=email]','input[name*=email i]','input[placeholder*=邮箱]','input[placeholder*=mail i]','input[autocomplete=email]','input[autocomplete=username]']).filter(function(e){return e.type!=='hidden'&&!inside(e,dl);});
var phoneIn=all(['input[type=tel]','input[name*=phone i]','input[name*=mobile i]','input[placeholder*=手机]','input[placeholder*=phone i]']).filter(function(e){return !inside(e,dl);});
var qr=all(R.qrSelectors||['[class*=qrcode] canvas','[class*=qrcode] img','[class*=qr-code] canvas','[class*=qr-code] img','[class*=QrCode] canvas','[class*=QrCode] img','canvas[class*=qr]','img[class*=qr]','img[alt*=二维码]','img[src*=qrcode]']).filter(function(e){var r=e.getBoundingClientRect();return r.width>=80&&r.height>=80;});
var codeIn=all(['input[autocomplete=one-time-code]','input[name*=code i]','input[placeholder*=验证码]','input[placeholder*=code i]','input[name*=otp i]','input[maxlength="6"]','input[maxlength="1"]']).filter(function(e){return e.type!=='hidden'&&e.type!=='password'&&!inside(e,dl);});
var phoneWords=/短信|手机|sms|phone|mobile|text message/i.test(text),mailWords=/邮箱|邮件|e-?mail|inbox/i.test(text);
res.emailEntry=!onKeys&&emailIn.length>0&&loginPass.length===0;
res.disposableRejected=/不支持.{0,8}(邮箱|该邮箱|此邮箱)|临时邮箱|一次性邮箱|disposable|temporary e-?mail|email (domain )?(is )?not (allowed|supported)|use a different email|invalid email domain/i.test(text);
res.google=/(^|\.)accounts\.google\.com$/.test(location.hostname);
res.precheck=-1;(R.precheck||[]).some(function(pc,i){try{if(new RegExp(pc.rx,'i').test(text)){res.precheck=i;return true;}}catch(x){}return false;});
if(res.google)res.state='google';
else if(document.readyState==='loading')res.state='loading';
else if(challenge&&!widgets.length)res.state='challenge';
else if(widgets.length)res.state='verify';
else if(!onKeys&&qr.length&&!loginPass.length&&!phoneIn.length&&!emailIn.length)res.state='qr';
else if(!onKeys&&codeIn.length&&mailWords&&!phoneWords)res.state='email_code';
else if(!onKeys&&codeIn.length&&phoneWords)res.state='sms_code';
else if(loginPass.length||(!onKeys&&(emailIn.length||phoneIn.length)&&(loginUrl||!own))||(loginUrl&&!onKeys&&users.length>0))res.state='login';
else if(own)res.state=rendered?'in':'loading';
else res.state='elsewhere';
var top=dl.length?dl[dl.length-1]:null;
function nameField(){var cands=[];if(top)cands=all(R.nameSelectors,top).concat(all(['input[type=text]','input:not([type])'],top));
 else cands=all(['form input[name*=name i]','form input[placeholder*=名称]','form input[placeholder*=name i]','form input[type=text]','main input[name*=name i]','main input[placeholder*=name i]']);
 return cands.filter(function(e){return e.type!=='hidden'&&e.type!=='password'&&!e.readOnly&&!e.disabled;})[0]||null;}
function pageForm(){if(top)return null;var fs=all(['form']).filter(function(f){return f.querySelector('input[type=text],input:not([type])')&&f.querySelector('button[type=submit],input[type=submit],button');});return fs[0]||null;}
var nf=nameField(),pf=pageForm();
res.dialogOpen=!!top;res.formOnPage=onKeys&&!!pf&&/(\/new|\/create|create|new)/.test(path);res.nameEmpty=!!nf&&!nf.value;
function createBtn(){var b=byText(R.createText,null,dl);if(b)return b;var ic=all(['button[aria-label]','[role=button][aria-label]','button[title]','a[title]']).filter(function(e){var a=attrs(e);return /(create|new|add|新建|创建|添加)/.test(a)&&/(key|token|密钥)/.test(a)&&!inside(e,dl);});return ic[0]||null;}
function confirmBtn(){if(top){var c=byText(R.confirmText,top);if(c)return c;var sb=top.querySelector('button[type=submit]');if(sb&&vis(sb))return sb;return null;}
 if(pf){var s2=pf.querySelector('button[type=submit],input[type=submit]');if(s2&&vis(s2))return s2;return byText(R.confirmText,pf);}return null;}
var cb=onKeys?createBtn():null,fb=onKeys?confirmBtn():null;
res.createFound=!!cb;res.confirmFound=!!fb;
function submitNear(el,words){var sb=null;var f=el&&el.form;if(f)sb=f.querySelector('button[type=submit],input[type=submit]');return sb||byText(words);}
if(A==='fill'){if(res.state==='login'&&!onKeys&&U&&loginPass.length){var p=loginPass[0];var f0=formOf(p);var us=users.filter(function(e){return f0.contains(e);});
  if(us.length)setVal(us[0],U);if(P)setVal(p,P);var sbt=submitNear(p,R.submitText);if(sbt){setTimeout(function(){click(sbt);},350);res.acted='filled';}else res.acted='filled-nosubmit';}
 else res.acted='refused';}
if(A==='email'&&U&&emailIn.length&&!onKeys){setVal(emailIn[0],U);var b2=byText(['发送验证码','获取验证码','发送','Send code','Send verification code','Get code','Send link','Continue with email','Continue','继续','下一步','Next','登录','注册','Sign up','Sign in']);if(b2){setTimeout(function(){click(b2);},350);res.acted='email';}else res.acted='email-nosubmit';}
if(A==='code'&&U&&codeIn.length){if(codeIn.length>=U.length&&codeIn[0].maxLength===1){for(var ci=0;ci<U.length;ci++)setVal(codeIn[ci],U[ci]);}else setVal(codeIn[0],U);
 var b3=submitNear(codeIn[0],['确定','确认','验证','登录','提交','Verify','Continue','Submit','Sign in','Next','继续','下一步']);if(b3){setTimeout(function(){click(b3);},400);}res.acted='code';}
if(A==='nav'&&res.state==='in'){var n=byText(R.navText||['API Keys','API keys','API Key','API key','API 密钥','API密钥','API Key 管理','密钥管理','Keys']);if(n&&click(n))res.acted='nav';else res.acted='no-nav';}
if(A==='click_create'&&onKeys){res.acted=cb&&click(cb)?'create':'no-create';}
if(A==='create'&&onKeys&&res.state==='in'){var L=W.__mbL;var lp=location.pathname;var tn=Date.now();
 if(!L||L.path!==lp&&L.step>0&&L.step<3&&!top&&!pf){L=W.__mbL={step:0,ts:0,tries:(L&&L.tries)||0,path:lp};}
 L.path=lp;res.acted='wait';
 if(L.step===0){if(cb&&L.tries<3&&tn-L.ts>1600){if(click(cb)){L.tries++;L.step=1;L.ts=tn;res.acted='create';}}else if(!cb&&pf&&res.nameEmpty){L.step=1;L.ts=tn;res.acted='create';}else if(!cb)res.acted=L.tries>=3?'exhausted':'no-create';else if(L.tries>=3)res.acted='exhausted';}
 else if(L.step===1&&tn-L.ts>900){if(top||pf){if(nf&&!nf.value)setVal(nf,R.keyName||'Mibot');res.acted='name';L.step=2;L.ts=tn;}else if(tn-L.ts>8000){L.step=0;}}
 else if(L.step===2&&tn-L.ts>700){if(fb&&click(fb)){res.acted='confirm';L.step=3;L.ts=tn;}else if(tn-L.ts>8000){res.acted='no-confirm';L.step=3;L.ts=tn;}}
 else if(L.step===3&&tn-L.ts>3000){res.acted=reveal(top||null)?'copy':'wait';L.step=4;L.ts=tn;}
 else if(L.step===4&&tn-L.ts>12000){res.acted='exhausted';}
 res.step=L.step;}
if(A==='name'&&onKeys){if(nf&&!nf.value){setVal(nf,R.keyName||'Mibot');res.acted='name';}
 (R.clickText||[]).forEach(function(t){var e=byText([t],top||null);if(e)click(e);});
 (R.switchOff||[]).forEach(function(t){var l=byText([t],top||null);var el=l;for(var k=0;k<4&&el;k++){var sw=el.querySelector&&el.querySelector('[role=switch][aria-checked=true],input[type=checkbox]:checked');if(sw){click(sw);break;}el=el.parentElement;}});}
if(A==='confirm'&&onKeys){res.acted=fb&&click(fb)?'confirm':'no-confirm';}
function reveal(scope){var rv=all(R.revealSelectors||[],scope).concat(all(R.copySelectors||[],scope));var done=false;
 for(var ri=0;ri<rv.length&&!done;ri++)done=click(rv[ri]);
 if(!done){var cp=byText(R.copyText,scope);if(cp)done=click(cp);}
 if(!done){var ic2=all(['button[aria-label]','[role=button][aria-label]','button[title]','[class*=copy]','[class*=Copy]','[class*=eye]','[class*=Eye]'],scope).filter(function(e){return /(copy|复制|show|reveal|查看|显示|eye)/i.test(attrs(e)+' '+(e.className&&e.className.baseVal!==undefined?e.className.baseVal:e.className||''));});if(ic2.length)done=click(ic2[0]);}
 return done;}
if(A==='reveal')res.acted=reveal(top||null)?'reveal':'no-reveal';
return JSON.stringify(res);}"""
}
