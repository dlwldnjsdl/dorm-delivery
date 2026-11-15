import { useEffect, useState } from 'react';
import { currentSubscription, disablePush, enablePush, getPushSupport } from './push';

export default function PushNotificationButton() {
  const [enabled, setEnabled] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const support = getPushSupport();
  useEffect(() => { currentSubscription().then(sub => setEnabled(Boolean(sub))).catch(() => setMessage('알림 상태를 확인할 수 없습니다.')); }, []);
  async function toggle() {
    setBusy(true); setMessage('');
    try {
      if (enabled) await disablePush(); else await enablePush();
      setEnabled(!enabled);
    } catch (error) { setMessage(error.message); }
    finally { setBusy(false); }
  }
  return <div className="push-controls">
    <button type="button" className="logout-button" disabled={busy || !support.supported} onClick={toggle}>{busy ? '설정 중…' : enabled ? '알림 끄기' : '알림 켜기'}</button>
    <small role="status">{!support.supported ? support.message : message}</small>
  </div>;
}
