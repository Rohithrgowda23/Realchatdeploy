import { useEffect, useState } from 'react';
import api, { errMsg } from './api';
import { useStore } from './store';
import { btnPrimary, inputCls } from './components.jsx';

// Change password, or set one for accounts created with Google only.
export default function PasswordSection() {
    const toast = useStore((s) => s.toast);
    const [hasPassword, setHasPassword] = useState(null);
    const [f, setF] = useState({ current: '', next: '', confirm: '' });
    const [err, setErr] = useState('');
    const [busy, setBusy] = useState(false);
    useEffect(() => { api.get('/api/oauth/accounts').then((r) => setHasPassword(r.data.hasPassword)).catch(() => setHasPassword(true)); }, []);
    const submit = async (e) => {
        e.preventDefault(); setErr('');
        if (f.next.length < 8) return setErr('New password must be at least 8 characters');
        if (f.next !== f.confirm) return setErr('Passwords do not match');
        setBusy(true);
        try {
            await api.put('/api/users/me/password', { currentPassword: hasPassword ? f.current : null, newPassword: f.next });
            toast('Password updated. Other devices were signed out.'); setHasPassword(true); setF({ current: '', next: '', confirm: '' });
        } catch (x) { setErr(errMsg(x)); }
        setBusy(false);
    };
    if (hasPassword === null) return null;
    return (
        <section className="mb-6">
            <h3 className="mb-2 text-sm font-medium text-mute">{hasPassword ? 'Change password' : 'Set a password'}</h3>
            <form onSubmit={submit} className="space-y-2">
                {hasPassword && <input aria-label="Current password" type="password" autoComplete="current-password" className={inputCls} placeholder="Current password" value={f.current} onChange={(e) => setF({ ...f, current: e.target.value })} />}
                <input aria-label="New password" type="password" autoComplete="new-password" className={inputCls} placeholder="New password" value={f.next} onChange={(e) => setF({ ...f, next: e.target.value })} />
                <input aria-label="Confirm new password" type="password" autoComplete="new-password" className={inputCls} placeholder="Confirm new password" value={f.confirm} onChange={(e) => setF({ ...f, confirm: e.target.value })} />
                {err && <p role="alert" className="text-xs text-red-400">{err}</p>}
                <button className={btnPrimary} disabled={busy}>{busy ? 'Saving…' : hasPassword ? 'Change password' : 'Set password'}</button>
            </form>
        </section>
    );
}