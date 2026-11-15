import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { disablePush, logoutWithPush, enablePush } from '../src/push.js';

function browser(subscription) {
  globalThis.window = { isSecureContext: true, PushManager: function(){}, Notification: {requestPermission: async()=> 'granted'} };
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { serviceWorker: { register: async()=>{}, getRegistration: async () => ({ pushManager: { getSubscription: async () => subscription } }) } } });
}
test('unsubscribe waits for successful server deletion', async () => {
  const steps=[];
  browser({ endpoint:'https://example.test/sub',unsubscribe:async()=>steps.push('browser') });
  globalThis.fetch=async()=>{steps.push('server');return {ok:true};};
  await disablePush();assert.deepEqual(steps,['server','browser']);
});
test('transient server deletion failure retains subscription for retry', async () => {
  let removed=false;
  browser({endpoint:'https://example.test/sub',unsubscribe:async()=>{removed=true;}});
  globalThis.fetch=async()=>({ok:false});
  await assert.rejects(disablePush());assert.equal(removed,false);
});
test('logout clears server device binding before browser unsubscribe', async () => {
  const steps=[];
  browser({endpoint:'https://example.test/sub',unsubscribe:async()=>steps.push('browser')});
  globalThis.fetch=async(url)=>{assert.equal(url,'/users/logout');steps.push('server');return {ok:true};};
  await logoutWithPush();assert.deepEqual(steps,['server','browser']);
});
test('service worker displays payload and safe fallback and rejects external click URL', async () => {
  const listeners={};const shown=[];
  const self={addEventListener:(name,handler)=>{listeners[name]=handler;},registration:{showNotification:async(title,options)=>shown.push({title,options})},location:{origin:'http://localhost:5173'}};
  vm.runInNewContext(readFileSync(new URL('../public/sw.js',import.meta.url),'utf8'),{self,URL});
  let completion;
  listeners.push({data:{json:()=>({title:'단짝',body:'새 알림',url:'https://evil.test'})},waitUntil:p=>{completion=p;}});
  await completion;assert.equal(shown[0].options.body,'새 알림');assert.equal(shown[0].options.data.url,'/');
  listeners.push({data:{json:()=>{throw new Error();}},waitUntil:p=>{completion=p;}});
  await completion;assert.equal(shown[1].title,'단짝');
});

test('enable requests permission from the click before network and stores a real subscription shape', async () => {
  const steps=[];
  browser(null);
  window.Notification.permission='default';
  window.Notification.requestPermission=()=>{steps.push('permission');return Promise.resolve('granted');};
  const subscription={toJSON:()=>({endpoint:'https://example.test/sub',keys:{}}),unsubscribe:async()=>{}};
  const registration={pushManager:{getSubscription:async()=>null,subscribe:async options=>{
    assert.equal(options.userVisibleOnly,true);assert.equal(options.applicationServerKey.length,65);
    steps.push('subscribe');return subscription;
  }}};
  navigator.serviceWorker.register=async path=>{assert.equal(path,'/sw.js');steps.push('register');return registration;};
  navigator.serviceWorker.ready=Promise.resolve(registration);
  globalThis.fetch=async(path,options)=>{
    if(path==='/push/public-key'){steps.push('config');return {ok:true,json:async()=>({enabled:true,publicKey:Buffer.alloc(65).toString('base64url')})};}
    assert.equal(path,'/push/subscriptions');assert.equal(options.method,'POST');
    assert.equal(JSON.parse(options.body).endpoint,'https://example.test/sub');steps.push('save');return {ok:true};
  };
  assert.equal(await enablePush(),subscription);
  assert.deepEqual(steps,['permission','config','register','subscribe','save']);
});

test('denied permission does not repeat the prompt or contact the server', async () => {
  browser(null);window.Notification.permission='denied';
  window.Notification.requestPermission=()=>{throw new Error('unexpected prompt');};
  globalThis.fetch=()=>{throw new Error('unexpected network');};
  await assert.rejects(enablePush(),/차단/);
});
