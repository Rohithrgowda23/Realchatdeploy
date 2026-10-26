import { useEffect, useState } from 'react';
import { Clock, Pencil, Trash2 } from 'lucide-react';
import api, { errMsg } from './api';
import { useStore } from './store';
import { Confirm, Modal, Skeleton, btnGhost, btnPrimary, iconBtn, inputCls } from './components.jsx';

const pad = (n) => String(n).padStart(2, '0');
const toLocalInput = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
const zone = () => Intl.DateTimeFormat().resolvedOptions().timeZone;
const fmt = (iso) => new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });

// The picker works in YOUR time zone; the browser converts to an exact UTC instant, so the server never guesses a zone.
export function ScheduleDialog({ convId, content, onClose, onDone }) {
    const { toast, replyTo, setReplyTo } = useStore();
    const [text, setText] = useState(content);
    const [when, setWhen] = useState(() => { const d = new Date(Date.now() + 3600e3); d.setSeconds(0, 0); return toLocalInput(d); });
    const [busy, setBusy] = useState(false);
    const quick = [
        ['In 1 hour', () => new Date(Date.now() + 3600e3)],
        ['Tomorrow 9:00', () => { const d = new Date(); d.setDate(d.getDate() + 1); d.setHours(9, 0, 0, 0); return d; }],
    ];
    const submit = async (e) => {
        e.preventDefault();
        const d = new Date(when);
        if (Number.isNaN(d.getTime()) || d.getTime() < Date.now() + 55e3) return toast('Choose a time at least a minute from now', 'error');
        setBusy(true);
        try {
            await api.post(`/api/api/conversations/${convId}/scheduled`, { content: text, replyToId: replyTo?.id || null, scheduledAt: d.toISOString() });
            toast(`Scheduled for ${fmt(d)}`); setReplyTo(null); onDone?.(); onClose();
        } catch (x) { toast(errMsg(x), 'error'); }
        setBusy(false);
    };
    return (
        <Modal title="Schedule message" onClose={onClose}>
            <form onSubmit={submit} className="space-y-3">
                <textarea aria-label="Message to schedule" rows={3} maxLength={4000} className={inputCls} value={text} onChange={(e) => setText(e.target.value)} />
                <label className="block text-sm font-medium">Send at
                    <input type="datetime-local" className={`${inputCls} mt-1`} min={toLocalInput(new Date(Date.now() + 60e3))} value={when} onChange={(e) => setWhen(e.target.value)} />
                </label>
                <div className="flex flex-wrap gap-2">{quick.map(([l, f]) => <button key={l} type="button" className={btnGhost} onClick={() => setWhen(toLocalInput(f()))}>{l}</button>)}</div>
                <p className="text-xs text-mute">Time zone: {zone()}. The server sends it even if you close the browser. It is checked again at send time (you must still be allowed to post).</p>
                <div className="flex justify-end gap-2"><button type="button" className={btnGhost} onClick={onClose}>Cancel</button><button className={btnPrimary} disabled={busy || !text.trim()}><Clock size={16} /> {busy ? 'Scheduling…' : 'Schedule'}</button></div>
            </form>
        </Modal>
    );
}

const STATUS = { PENDING: ['Scheduled', 'bg-violet-500/20 text-violet-300'], SENDING: ['Sending', 'bg-amber-500/20 text-amber-400'], SENT: ['Sent', 'bg-emerald-500/20 text-emerald-400'], CANCELLED: ['Cancelled', 'bg-panel2 text-mute'], FAILED: ['Failed', 'bg-red-500/20 text-red-400'] };

export function ScheduledDialog({ onClose }) {
    const toast = useStore((s) => s.toast);
    const [list, setList] = useState(null);
    const [tab, setTab] = useState('pending');
    const [edit, setEdit] = useState(null); // {id, content, when}
    const [confirm, setConfirm] = useState(null);
    const load = () => api.get('/api/scheduled').then((r) => setList(r.data)).catch((e) => { toast(errMsg(e), 'error'); setList([]); });
    useEffect(() => { load(); }, []); // eslint-disable-line
    const shown = (list || []).filter((s) => (tab === 'pending' ? ['PENDING', 'SENDING'].includes(s.status) : !['PENDING', 'SENDING'].includes(s.status)))
        .sort((a, b) => (tab === 'pending' ? new Date(a.scheduledAt) - new Date(b.scheduledAt) : new Date(b.scheduledAt) - new Date(a.scheduledAt)));
    const save = async (e) => {
        e.preventDefault();
        const d = new Date(edit.when);
        if (Number.isNaN(d.getTime())) return toast('Choose a valid time', 'error');
        try { await api.put(`/api/scheduled/${edit.id}`, { content: edit.content, scheduledAt: d.toISOString() }); toast('Updated'); setEdit(null); load(); }
        catch (x) { toast(errMsg(x), 'error'); load(); }
    };
    return (
        <Modal title="Scheduled messages" onClose={onClose} wide>
            <div className="mb-3 flex gap-1 rounded-xl bg-panel2 p-1 text-sm" role="tablist">
                {[['pending', 'Upcoming'], ['history', 'History']].map(([k, l]) => <button key={k} role="tab" aria-selected={tab === k} onClick={() => setTab(k)} className={`flex-1 rounded-lg px-3 py-1.5 ${tab === k ? 'bg-panel shadow' : 'text-mute hover:text-ink'}`}>{l}</button>)}
            </div>
            {list === null && <Skeleton className="h-16 w-full" />}
            {list && shown.length === 0 && <p className="p-6 text-center text-sm text-mute">{tab === 'pending' ? 'Nothing scheduled. Use the clock next to the send button.' : 'No sent, cancelled or failed messages yet.'}</p>}
            <ul className="space-y-2">
                {shown.map((s) => (
                    <li key={s.id} className="rounded-2xl border border-line p-3 text-sm">
                        {edit?.id === s.id ? (
                            <form onSubmit={save} className="space-y-2">
                                <textarea aria-label="Edit message" rows={2} className={inputCls} value={edit.content} onChange={(e) => setEdit({ ...edit, content: e.target.value })} />
                                <input aria-label="Send at" type="datetime-local" className={inputCls} value={edit.when} onChange={(e) => setEdit({ ...edit, when: e.target.value })} />
                                <div className="flex justify-end gap-2"><button type="button" className={btnGhost} onClick={() => setEdit(null)}>Cancel</button><button className={btnPrimary}>Save</button></div>
                            </form>
                        ) : (
                            <>
                                <div className="mb-1 flex items-center gap-2"><span className="font-medium">{s.conversationTitle || 'Chat'}</span><span className={`rounded-full px-2 py-0.5 text-xs ${STATUS[s.status][1]}`}>{STATUS[s.status][0]}</span><span className="ml-auto text-xs text-mute">{fmt(s.scheduledAt)}</span></div>
                                <div className="line-clamp-3 whitespace-pre-wrap break-words text-mute">{s.content}</div>
                                {s.lastError && <div className="mt-1 text-xs text-red-400">{s.status === 'FAILED' ? 'Not sent: ' : 'Retrying after: '}{s.lastError}</div>}
                                {s.status === 'PENDING' && (
                                    <div className="mt-2 flex justify-end gap-1">
                                        <button aria-label="Edit scheduled message" className={iconBtn} onClick={() => setEdit({ id: s.id, content: s.content, when: toLocalInput(new Date(s.scheduledAt)) })}><Pencil size={16} /></button>
                                        <button aria-label="Cancel scheduled message" className={`${iconBtn} hover:text-red-400`} onClick={() => setConfirm(s)}><Trash2 size={16} /></button>
                                    </div>
                                )}
                            </>
                        )}
                    </li>
                ))}
            </ul>
            {confirm && <Confirm danger title="Cancel scheduled message" confirmLabel="Cancel message" text="It will not be sent." onClose={() => setConfirm(null)}
                                 onConfirm={async () => { try { await api.delete(`/api/scheduled/${confirm.id}`); toast('Cancelled'); load(); } catch (e) { toast(errMsg(e), 'error'); load(); } }} />}
        </Modal>
    );
}