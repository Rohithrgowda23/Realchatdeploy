import { useEffect, useState } from 'react';
import { AtSign, Bell, Clock, PhoneMissed, UserCheck, X } from 'lucide-react';
import api, { errMsg } from './api';
import { useStore } from './store';
import { Confirm, Modal, Skeleton, btnGhost, iconBtn } from './components.jsx';

const ICON = { MENTION: AtSign, JOIN_REQUEST: UserCheck, JOIN_DECISION: UserCheck, SCHEDULED_FAILED: Clock, MISSED_CALL: PhoneMissed };
const when = (iso) => new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });

export function NotificationsDialog({ onClose }) {
    const { toast, jumpTo, open, loadNotifMeta, notifPrefs, setNotifPrefs } = useStore();
    const [items, setItems] = useState(null);
    const [more, setMore] = useState(false);
    const [confirmClear, setConfirmClear] = useState(false);

    const load = async (append) => {
        const before = append && items?.length ? items[items.length - 1].id : undefined;
        try {
            const { data } = await api.get('/notifications', { params: { before, size: 20 } });
            setItems((c) => (append ? [...(c || []), ...data] : data)); setMore(data.length === 20);
        } catch (e) { toast(errMsg(e), 'error'); setItems((c) => c || []); }
    };
    useEffect(() => { load(false); }, []); // eslint-disable-line

    const act = async (fn) => { try { await fn(); } catch (e) { toast(errMsg(e), 'error'); } loadNotifMeta(); };
    const markAll = () => act(async () => { await api.post('/notifications/read'); setItems((l) => l.map((n) => ({ ...n, read: true }))); });
    const dismiss = (n) => act(async () => { await api.delete(`/notifications/${n.id}`); setItems((l) => l.filter((x) => x.id !== n.id)); });
    const clearAll = () => act(async () => { await api.delete('/notifications'); setItems([]); setMore(false); });
    const go = async (n) => {
        if (!n.read) { await api.post('/notifications/read', { ids: [n.id] }).catch(() => {}); setItems((l) => l.map((x) => (x.id === n.id ? { ...x, read: true } : x))); loadNotifMeta(); }
        if (n.type === 'MENTION' && n.conversationId && n.messageId) { jumpTo(n.conversationId, n.messageId); onClose(); }
        else if (n.conversationId) { open(n.conversationId); onClose(); }
    };
    const setPref = async (key, value) => {
        const next = { ...notifPrefs, [key]: value };
        setNotifPrefs(next);
        try { await api.put('/notifications/preferences', next); } catch (e) { setNotifPrefs(notifPrefs); toast(errMsg(e), 'error'); }
    };

    return (
        <Modal title="Notifications" onClose={onClose} wide>
            <div className="mb-3 flex gap-2">
                <button className={btnGhost} onClick={markAll}>Mark all read</button>
                <button className={btnGhost} onClick={() => setConfirmClear(true)} disabled={!items?.length}>Clear all</button>
            </div>
            {items === null && <Skeleton className="h-16 w-full" />}
            {items && items.length === 0 && <p className="p-6 text-center text-sm text-mute">You are all caught up.</p>}
            <ul className="divide-y divide-line">
                {items?.map((n) => { const Icon = ICON[n.type] || Bell; return (
                    <li key={n.id} className="flex items-start gap-2 py-2">
                        <button onClick={() => go(n)} className="flex min-w-0 flex-1 items-start gap-3 rounded-lg p-1 text-left hover:bg-panel2">
                            <span className="relative mt-0.5 shrink-0 text-violet-300"><Icon size={18} />{!n.read && <span aria-label="Unread" className="absolute -right-1 -top-1 h-2 w-2 rounded-full bg-emerald-400" />}</span>
                            <span className="min-w-0 flex-1"><span className={`block text-sm ${n.read ? '' : 'font-semibold'}`}>{n.title}</span>{n.body && <span className="line-clamp-2 block text-sm text-mute">{n.body}</span>}<span className="text-xs text-mute">{when(n.createdAt)}</span></span>
                        </button>
                        <button aria-label="Dismiss notification" className={iconBtn} onClick={() => dismiss(n)}><X size={16} /></button>
                    </li>); })}
            </ul>
            {more && <div className="mt-3 text-center"><button className={btnGhost} onClick={() => load(true)}>Load more</button></div>}
            <div className="mt-5 border-t border-line pt-4">
                <h3 className="mb-2 text-sm font-medium text-mute">Notify me about</h3>
                {[['mentions', 'Mentions of me'], ['joinRequests', 'Group join requests and decisions'], ['scheduled', 'Scheduled messages that could not be sent'], ['calls', 'Missed calls']].map(([k, l]) => (
                    <label key={k} className="flex cursor-pointer items-center justify-between py-1.5 text-sm">{l}<input type="checkbox" className="h-5 w-5 accent-violet-600" checked={!!notifPrefs[k]} onChange={(e) => setPref(k, e.target.checked)} /></label>
                ))}
                <p className="mt-1 text-xs text-mute">New-message alerts are controlled per chat with the mute button.</p>
            </div>
            {confirmClear && <Confirm danger title="Clear notifications" confirmLabel="Clear all" text="All notifications will be deleted." onClose={() => setConfirmClear(false)} onConfirm={clearAll} />}
        </Modal>
    );
}