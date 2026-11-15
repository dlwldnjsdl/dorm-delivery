import test from 'node:test';
import assert from 'node:assert/strict';
import { getPushSupport } from '../src/push.js';
import { readFileSync } from 'node:fs';

function browser({secure=true,ios=false,installed=false,inApp=false,missing=[]}={}) {
  const win={isSecureContext:secure,PushManager:function(){},Notification:{requestPermission:async()=> 'granted'},matchMedia:()=>({matches:installed})};
  const nav={userAgent:(ios?'iPhone':'Windows')+(inApp?' KAKAOTALK':''),serviceWorker:{register:async()=>{},getRegistration:async()=>null},standalone:installed};
  for(const name of missing) if(name==='serviceWorker')delete nav[name];else delete win[name];
  return [win,nav];
}
test('insecure context stays blocked even if all APIs exist',()=>{
  const result=getPushSupport(...browser({secure:false}));assert.equal(result.supported,false);assert.equal(result.reason,'insecure-context');
});
test('secure iPhone Safari tab receives home-screen instructions, not HTTPS advice',()=>{
  const result=getPushSupport(...browser({ios:true,missing:['PushManager','Notification']}));
  assert.equal(result.reason,'ios-install-required');assert.match(result.message,/홈 화면/);assert.doesNotMatch(result.message,/HTTPS/);
});
test('Kakao browser explains how to open an external browser',()=>{
  const result=getPushSupport(...browser({ios:true,inApp:true,missing:['PushManager']}));
  assert.equal(result.reason,'in-app-browser');assert.match(result.message,/Safari/);
});
test('installed iPhone app and supported desktop remain supported',()=>{
  assert.equal(getPushSupport(...browser({ios:true,installed:true})).supported,true);
  assert.equal(getPushSupport(...browser()).supported,true);
});
test('missing APIs are independently diagnosed and never bypassed',()=>{
  for(const [name,reason] of [['serviceWorker','service-worker-unavailable'],['Notification','notifications-unavailable'],['PushManager','push-api-unavailable']]){
    const result=getPushSupport(...browser({missing:[name]}));assert.equal(result.supported,false);assert.equal(result.reason,reason);
  }
});
test('home screen manifest stays on the same origin and standalone',()=>{
  const manifest=JSON.parse(readFileSync(new URL('../public/manifest.webmanifest',import.meta.url),'utf8'));
  assert.equal(manifest.display,'standalone');assert.equal(manifest.start_url,'/');assert.equal(manifest.scope,'/');
  const html=readFileSync(new URL('../index.html',import.meta.url),'utf8');assert.match(html,/rel="manifest"/);
});
