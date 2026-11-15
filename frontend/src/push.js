export function getPushSupport(win = globalThis.window, nav = globalThis.navigator) {
  const checks = {
    secureContext: win?.isSecureContext === true,
    serviceWorker: typeof nav?.serviceWorker?.register === 'function' && typeof nav?.serviceWorker?.getRegistration === 'function',
    notifications: typeof win?.Notification?.requestPermission === 'function',
    pushManager: typeof win?.PushManager === 'function',
  };
  const ua = nav?.userAgent || '';
  const ios = /iPhone|iPad|iPod/i.test(ua) || (nav?.platform === 'MacIntel' && nav?.maxTouchPoints > 1);
  const standalone = nav?.standalone === true || win?.matchMedia?.('(display-mode: standalone)')?.matches === true;
  const inApp = /KAKAOTALK|Instagram|FBAN|FBAV|NAVER|Line\//i.test(ua);
  const blocked = (reason, message) => ({supported:false, reason, message, checks});
  if (!checks.secureContext) return blocked('insecure-context', '보안 연결이 필요합니다. HTTPS 주소로 직접 접속해주세요.');
  if (inApp && (!standalone || !Object.values(checks).every(Boolean))) return blocked('in-app-browser', '카카오톡 등 앱 안에서는 알림이 제한될 수 있습니다. 외부 Safari 또는 Chrome으로 열어주세요. iPhone/iPad는 홈 화면에 추가한 뒤 실행하세요.');
  if (ios && !standalone) return blocked('ios-install-required', 'iPhone/iPad는 iOS/iPadOS 16.4 이상에서 Safari 공유 → 홈 화면에 추가 → 추가된 단짝 아이콘으로 실행한 뒤 로그인하고 알림을 켜주세요.');
  if (!checks.serviceWorker) return blocked('service-worker-unavailable', '이 브라우저에서 Service Worker를 사용할 수 없습니다. 최신 Safari·Chrome·Edge의 일반 모드로 접속해주세요.');
  if (!checks.notifications) return blocked('notifications-unavailable', '이 실행 환경에서 알림 API를 사용할 수 없습니다. 브라우저·OS를 업데이트하고 iPhone/iPad는 홈 화면 앱에서 실행해주세요.');
  if (!checks.pushManager) return blocked('push-api-unavailable', '이 브라우저에서 Push API를 사용할 수 없습니다. 지원 브라우저로 열어주세요. iPhone/iPad는 홈 화면 앱이 필요합니다.');
  return {supported:true, reason:'supported', message:'', checks};
}
export const pushSupported = () => getPushSupport().supported;
async function request(path, options = {}) {
  const response = await fetch(path, { credentials: 'include', ...options });
  if (!response.ok) throw new Error('알림 설정을 저장하지 못했습니다. 서버와 로그인 상태를 확인하세요.');
  return response;
}
export async function currentSubscription() {
  if (!pushSupported()) return null;
  const registration = await navigator.serviceWorker.getRegistration('/');
  return registration?.pushManager.getSubscription();
}
export async function enablePush() {
  const support = getPushSupport();
  if (!support.supported) throw new Error(support.message);
  // Called directly from the button, before other asynchronous operations.
  if (window.Notification.permission === 'denied') throw new Error('알림 권한이 차단되어 있습니다. 브라우저 또는 기기의 단짝 알림 설정에서 허용해주세요.');
  if (await window.Notification.requestPermission() !== 'granted') throw new Error('알림이 허용되지 않았습니다. 브라우저 사이트 설정을 확인하세요.');
  const config = await (await request('/push/public-key')).json();
  if (!config.enabled || !config.publicKey) throw new Error('서버의 푸시 알림 설정이 아직 준비되지 않았습니다.');
  await navigator.serviceWorker.register('/sw.js');
  const registration = await navigator.serviceWorker.ready;
  const value = config.publicKey.replace(/-/g, '+').replace(/_/g, '/');
  const key = Uint8Array.from(atob(value.padEnd(Math.ceil(value.length / 4) * 4, '=')), c => c.charCodeAt(0));
  const subscription = await registration.pushManager.getSubscription() || await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key });
  try {
    await request('/push/subscriptions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(subscription.toJSON()) });
  } catch (error) { await subscription.unsubscribe(); throw error; }
  return subscription;
}
export async function disablePush() {
  const subscription = await currentSubscription();
  if (!subscription) return;
  // Server deletion must succeed before losing the endpoint needed to delete it.
  await request('/push/subscriptions', { method: 'DELETE', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ endpoint: subscription.endpoint }) });
  await subscription.unsubscribe();
}

export async function syncExistingPush() {
  const subscription = await currentSubscription();
  if (!subscription) return;
  const response = await fetch('/push/subscriptions', { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(subscription.toJSON()) });
  if (response.status === 409) await subscription.unsubscribe();
  else if (!response.ok && response.status !== 503) throw new Error('기존 알림 구독을 확인하지 못했습니다.');
}

export async function logoutWithPush() {
  const response = await fetch('/users/logout', { method: 'POST', credentials: 'include' });
  if (!response.ok) throw new Error('로그아웃하지 못했습니다. 연결 상태를 확인해주세요.');
  const subscription = await currentSubscription();
  if (subscription) await subscription.unsubscribe().catch(() => {});
}
