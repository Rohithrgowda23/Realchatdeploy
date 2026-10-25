import { useEffect, useState } from 'react';
import api, { errMsg } from './api';
import { useStore } from './store';
import { Confirm, btnGhost } from './components.jsx';

export default function ConnectedAccounts() {
    const toast = useStore((s) => s.toast);
    const [data, setData] = useState(null);
    const [enabled, setEnabled] = useState(false);
    const [confirm, setConfirm] = useState(false);
    const [busy, setBusy] = useState(false);

    const load = () => api.get('/oauth/accounts').then((r) => setData(r.data)).catch((e) => toast(errMsg(e), 'error'));
    useEffect(() => {
        load();
        api.get('/auth/oauth/providers').then((r) => setEnabled(!!r.data.google)).catch(() => {});
    }, []); // eslint-disable-line

    const google = data?.providers.find((p) => p.provider === 'google');
    const connect = async () => {
        setBusy(true);
        try { const { data: r } = await api.post('/oauth/link-intent'); window.location.assign(r.beginUrl); }
        catch (e) { toast(errMsg(e), 'error'); setBusy(false); }
    };
    const disconnect = async () => {
        try { await api.delete('/oauth/google'); toast('Google disconnected'); load(); } catch (e) { toast(errMsg(e), 'error'); }
    };

    if (!enabled && !google) return null;
    return (
        <section className="mb-6">
            <h3 className="mb-2 text-sm font-medium text-mute">Connected accounts</h3>
            {data === null ? <div className="h-10 animate-pulse rounded-xl bg-panel2" /> : (
                <div className="flex items-center gap-3 rounded-2xl border border-line p-3 text-sm">
                    <div className="min-w-0 flex-1"><div className="font-medium">Google</div><div className="truncate text-xs text-mute">{google ? (google.email || 'Connected') : 'Not connected'}</div></div>
                    {google
                        ? <button className={btnGhost} onClick={() => setConfirm(true)}>Disconnect</button>
                        : <button className={btnGhost} disabled={busy} onClick={connect}>{busy ? 'Redirecting…' : 'Connect'}</button>}
                </div>
            )}
            {confirm && <Confirm danger title="Disconnect Google" confirmLabel="Disconnect"
                                 text={data?.hasPassword ? 'You will no longer be able to sign in with Google. Your password still works.' : 'You will no longer be able to sign in with Google.'}
                                 onClose={() => setConfirm(false)} onConfirm={disconnect} />}
        </section>
    );
}