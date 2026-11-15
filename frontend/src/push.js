export const pushSupported = () => window.isSecureContext && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
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
  if (!pushSupported()) throw new Error('이 브라우저 또는 접속 주소에서는 알림을 지원하지 않습니다.');
  // Called directly from the button, before other asynchronous operations.
  if (await Notification.requestPermission() !== 'granted') throw new Error('알림이 허용되지 않았습니다. 브라우저 사이트 설정을 확인하세요.');
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
