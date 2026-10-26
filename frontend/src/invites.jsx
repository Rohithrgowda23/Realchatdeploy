import { useEffect, useState } from 'react';
import { Check, Copy, Link2, UserCheck, X } from 'lucide-react';
import api, { errMsg } from './api';
import { useStore } from './store';
import { Avatar, Confirm, btnGhost, btnPrimary, inputCls } from './components.jsx';

const when = (iso) => (iso ? new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) : 'never');
const EXPIRY = [['1', '1 hour'], ['24', '24 hours'], ['168', '7 days'], ['720', '30 days'], ['', 'Never']];

export function InviteSection({ id }) {
    const toast = useStore((s) => s.toast);
    const [open, setOpen] = useState(false);
    const [list, setList] = useState(null);
    const [f, setF] = useState({ expires: '24', maxUses: '', approval: false });
    const [link, setLink] = useState(null);
    const [copied, setCopied] = useState(false);
    const [confirm, setConfirm] = useState(null);
    const [busy, setBusy] = useState(false);
    const load = () => api.get(`/api/conversations/${id}/invites`).then((r) => setList(r.data)).catch((e) => toast(errMsg(e), 'error'));
    useEffect(() => { if (open) load(); }, [open, id]); // eslint-disable-line

    const create = async (e) => {
        e.preventDefault(); setBusy(true);
        try {
            const { data } = await api.post(`/api/conversations/${id}/invites`, { expiresInHours: f.expires ? Number(f.expires) : null, maxUses: f.maxUses ? Number(f.maxUses) : null, requiresApproval: f.approval });
            // the token goes in the URL fragment: browsers never send it to servers, so it stays out of access logs
            setLink(`${window.location.origin}/join#${data.token}`); setCopied(false); load();
        } catch (x) { toast(errMsg(x), 'error'); }
        setBusy(false);
    };
    const copy = async () => { try { await navigator.clipboard.writeText(link); setCopied(true); } catch { toast('Could not copy: select the link and copy it manually', 'error'); } };

    return (
        <div className="mb-4 rounded-2xl border border-line p-3">
            <button className="flex w-full items-center justify-between text-sm font-medium" aria-expanded={open} onClick={() => setOpen(!open)}><span className="flex items-center gap-2"><Link2 size={16} /> Invite links</span><span className="text-mute">{open ? 'Hide' : 'Show'}</span></button>
            {open && (
                <div className="mt-3">
                    <form onSubmit={create} className="space-y-2">
                        <div className="grid grid-cols-2 gap-2">
                            <label className="text-xs text-mute">Expires<select className={inputCls} value={f.expires} onChange={(e) => setF({ ...f, expires: e.target.value })}>{EXPIRY.map(([v, l]) => <option key={l} value={v}>{l}</option>)}</select></label>
                            <label className="text-xs text-mute">Max uses<input type="number" min="1" max="1000" placeholder="Unlimited" className={inputCls} value={f.maxUses} onChange={(e) => setF({ ...f, maxUses: e.target.value })} /></label>
                        </div>
                        <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="h-4 w-4 accent-violet-600" checked={f.approval} onChange={(e) => setF({ ...f, approval: e.target.checked })} />Admins must approve each person</label>
                        <button className={btnPrimary} disabled={busy}>{busy ? 'Creating…' : 'Create invite link'}</button>
                    </form>
                    {link && (
                        <div className="mt-3 rounded-xl bg-emerald-500/10 p-3 text-sm">
                            <div className="mb-1 text-xs text-emerald-400">Copy it now. For security the link cannot be shown again.</div>
                            <div className="flex items-center gap-2"><input readOnly aria-label="Invite link" className={inputCls} value={link} onFocus={(e) => e.target.select()} /><button type="button" className={btnGhost} onClick={copy}>{copied ? <Check size={16} /> : <Copy size={16} />}</button></div>
                        </div>
                    )}
                    <ul className="mt-3 space-y-2 text-sm">
                        {list === null && <li className="h-8 animate-pulse rounded-lg bg-panel2" />}
                        {list && list.length === 0 && <li className="text-mute">No invite links yet.</li>}
                        {list && list.map((i) => (
                            <li key={i.id} className="flex items-center gap-2 rounded-xl border border-line p-2">
                                <div className="min-w-0 flex-1">
                                    <div><span className={`rounded-full px-2 py-0.5 text-xs ${i.status === 'ACTIVE' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-panel2 text-mute'}`}>{i.status.replace('_', ' ').toLowerCase()}</span>{i.requiresApproval && <span className="ml-1 text-xs text-mute">approval needed</span>}</div>
                                    <div className="truncate text-xs text-mute">by {i.createdBy} · uses {i.useCount}{i.maxUses ? `/${i.maxUses}` : ''} · expires {when(i.expiresAt)}</div>
                                </div>
                                {i.status === 'ACTIVE' && <button className="text-xs text-red-400 hover:underline" onClick={() => setConfirm(i)}>Revoke</button>}
                            </li>
                        ))}
                    </ul>
                </div>
            )}
            {confirm && <Confirm danger title="Revoke invite link" confirmLabel="Revoke" text="The link stops working immediately and its pending join requests are declined."
                                 onClose={() => setConfirm(null)} onConfirm={async () => { try { await api.delete(`api/conversations/${id}/invites/${confirm.id}`); toast('Invite revoked'); load(); } catch (e) { toast(errMsg(e), 'error'); } }} />}
        </div>
    );
}

export function JoinRequestsSection({ id }) {
    const { toast, joinTick, loadDetail } = useStore();
    const [list, setList] = useState(null);
    const load = () => api.get(`/api/conversations/${id}/join-requests`).then((r) => setList(r.data)).catch((e) => toast(errMsg(e), 'error'));
    useEffect(() => { load(); }, [id, joinTick]); // eslint-disable-line
    const decide = async (r, approve) => {
        try { await api.post(`/api/conversations/${id}/join-requests/${r.id}/${approve ? 'approve' : 'reject'}`); toast(approve ? `${r.userName} added` : 'Request declined'); load(); if (approve) loadDetail(id); }
        catch (e) { toast(errMsg(e), 'error'); load(); }
    };
    if (!list || list.length === 0) return null;
    return (
        <div className="mb-4 rounded-2xl border border-violet-500/40 p-3">
            <div className="mb-2 flex items-center gap-2 text-sm font-medium"><UserCheck size={16} /> Join requests <span className="rounded-full bg-violet-600 px-2 text-xs text-white">{list.length}</span></div>
            <ul className="space-y-2">
                {list.map((r) => (
                    <li key={r.id} className="flex items-center gap-2 text-sm">
                        <Avatar name={r.userName} url={r.userAvatar} size={32} />
                        <div className="min-w-0 flex-1"><div className="truncate font-medium">{r.userName}</div><div className="text-xs text-mute">{when(r.createdAt)}</div></div>
                        <button aria-label={`Approve ${r.userName}`} className="rounded-lg bg-emerald-600/20 p-2 text-emerald-400 hover:bg-emerald-600/30" onClick={() => decide(r, true)}><Check size={16} /></button>
                        <button aria-label={`Decline ${r.userName}`} className="rounded-lg bg-red-600/20 p-2 text-red-400 hover:bg-red-600/30" onClick={() => decide(r, false)}><X size={16} /></button>
                    </li>
                ))}
            </ul>
        </div>
    );
}