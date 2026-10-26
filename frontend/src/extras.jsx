import { useEffect, useState } from 'react';
import { Bookmark, Forward, Monitor, Search, Smartphone } from 'lucide-react';
import api, { errMsg } from './api';
import { useStore } from './store';
import { Avatar, Confirm, Modal, btnDanger, btnGhost, btnPrimary, inputCls } from './components.jsx';

const when = (iso) => new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });

function ResultList({ items, convTitle, onGo, action }) {
  return (
      <ul className="divide-y divide-line">
        {items.map((m) => (
            <li key={m.id} className="flex items-start gap-2 py-2">
              <button onClick={() => onGo(m)} className="min-w-0 flex-1 rounded-lg p-1 text-left hover:bg-panel2 focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-400">
                <div className="flex items-center gap-2 text-xs text-mute"><b className="text-ink">{m.senderName}</b>{convTitle && <span>in {convTitle(m.conversationId)}</span>}<span>{when(m.createdAt)}</span></div>
                <div className="line-clamp-2 break-words text-sm">{m.content}</div>
              </button>
              {action && action(m)}
            </li>
        ))}
      </ul>
  );
}

export function SavedDialog({ onClose }) {
  const { jumpTo, toggleSave, toast, conversations } = useStore();
  const [q, setQ] = useState('');
  const [items, setItems] = useState(null);
  const [page, setPage] = useState(0);
  const [more, setMore] = useState(false);
  const title = (id) => conversations.find((c) => c.id === id)?.title || 'chat';
  const load = async (p, term, append) => {
    try {
      const { data } = await api.get('/api/saved', { params: { q: term, page: p, size: 20 } });
      setItems((cur) => (append ? [...(cur || []), ...data] : data)); setPage(p); setMore(data.length === 20);
    } catch (e) { toast(errMsg(e), 'error'); setItems((c) => c || []); }
  };
  useEffect(() => { const h = setTimeout(() => load(0, q.trim(), false), 250); return () => clearTimeout(h); }, [q]); // eslint-disable-line
  return (
      <Modal title="Saved messages" onClose={onClose} wide>
        <div className="relative mb-3"><Search size={16} className="absolute left-3 top-2.5 text-mute" /><input autoFocus aria-label="Search saved messages" className={`${inputCls} pl-9`} placeholder="Search saved…" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        {items === null ? <div className="h-20 animate-pulse rounded-xl bg-panel2" /> : items.length === 0 ? <p className="p-6 text-center text-sm text-mute">{q ? 'No saved messages match.' : 'Nothing saved yet. Use the bookmark on any message.'}</p> : (
            <ResultList items={items} convTitle={title} onGo={(m) => { jumpTo(m.conversationId, m.id); onClose(); }}
                        action={(m) => <button aria-label="Remove from saved" className="rounded-lg p-2 text-violet-400 hover:bg-panel2" onClick={async () => { await toggleSave(m.id); setItems((l) => l.filter((x) => x.id !== m.id)); }}><Bookmark size={16} fill="currentColor" /></button>} />
        )}
        {more && <div className="mt-3 text-center"><button className={btnGhost} onClick={() => load(page + 1, q.trim(), true)}>Load more</button></div>}
      </Modal>
  );
}

export function SearchDialog({ onClose }) {
  const { conversations, jumpTo, toast } = useStore();
  const [f, setF] = useState({ q: '', conversationId: '', from: '', to: '' });
  const [items, setItems] = useState(null);
  const [page, setPage] = useState(0);
  const [more, setMore] = useState(false);
  const [busy, setBusy] = useState(false);
  const title = (id) => conversations.find((c) => c.id === id)?.title || 'chat';
  const run = async (p, append) => {
    const params = { q: f.q.trim(), page: p, size: 20 };
    if (f.conversationId) params.conversationId = f.conversationId;
    if (f.from) params.from = new Date(`${f.from}T00:00:00`).toISOString();
    if (f.to) { const d = new Date(`${f.to}T00:00:00`); d.setDate(d.getDate() + 1); params.to = d.toISOString(); }
    setBusy(true);
    try {
      const { data } = await api.get('/api/search/messages', { params });
      setItems((cur) => (append ? [...(cur || []), ...data] : data)); setPage(p); setMore(data.length === 20);
    } catch (e) { toast(errMsg(e), 'error'); }
    setBusy(false);
  };
  return (
      <Modal title="Search messages" onClose={onClose} wide>
        <form onSubmit={(e) => { e.preventDefault(); run(0, false); }} className="mb-3 space-y-2">
          <input autoFocus aria-label="Search text" className={inputCls} placeholder="Search text…" value={f.q} onChange={(e) => setF({ ...f, q: e.target.value })} />
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
            <select aria-label="Conversation" className={inputCls} value={f.conversationId} onChange={(e) => setF({ ...f, conversationId: e.target.value })}>
              <option value="">All conversations</option>{conversations.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}
            </select>
            <input aria-label="From date" type="date" className={inputCls} value={f.from} onChange={(e) => setF({ ...f, from: e.target.value })} />
            <input aria-label="To date" type="date" className={inputCls} value={f.to} onChange={(e) => setF({ ...f, to: e.target.value })} />
          </div>
          <button className={btnPrimary} disabled={busy}>{busy ? 'Searching…' : 'Search'}</button>
        </form>
        {items && (items.length === 0 ? <p className="p-4 text-center text-sm text-mute">No matches.</p> : <ResultList items={items} convTitle={title} onGo={(m) => { jumpTo(m.conversationId, m.id); onClose(); }} />)}
        {more && <div className="mt-3 text-center"><button className={btnGhost} onClick={() => run(page + 1, true)}>Load more</button></div>}
      </Modal>
  );
}

export function ForwardDialog({ message, onClose }) {
  const { conversations, toast } = useStore();
  const [q, setQ] = useState('');
  const [dest, setDest] = useState(null);
  const [caption, setCaption] = useState('');
  const [busy, setBusy] = useState(false);
  const list = conversations.filter((c) => c.title.toLowerCase().includes(q.toLowerCase()) && !(c.announcementOnly && c.myRole === 'MEMBER'));
  const go = async () => {
    setBusy(true);
    try { await api.post(`/api/messages/${message.id}/forward`, { conversationId: dest, caption }); toast('Message forwarded'); onClose(); } catch (e) { toast(errMsg(e), 'error'); }
    setBusy(false);
  };
  return (
      <Modal title="Forward message" onClose={onClose}>
        <div className="mb-3 rounded-xl border-l-2 border-violet-400 bg-panel2 p-2 text-sm"><b>{message.senderName}</b><div className="line-clamp-2 text-mute">{message.content}</div></div>
        <input autoFocus aria-label="Find a chat" className={`${inputCls} mb-2`} placeholder="Find a chat…" value={q} onChange={(e) => setQ(e.target.value)} />
        <ul className="mb-3 max-h-52 overflow-y-auto" role="listbox" aria-label="Destination">
          {list.length === 0 && <li className="p-3 text-sm text-mute">No chats available.</li>}
          {list.map((c) => (
              <li key={c.id}><button role="option" aria-selected={dest === c.id} onClick={() => setDest(c.id)} className={`flex w-full items-center gap-3 rounded-xl p-2 text-left ${dest === c.id ? 'bg-violet-600/20' : 'hover:bg-panel2'}`}><Avatar name={c.title} url={c.avatarUrl} size={32} /><span className="truncate text-sm">{c.title}</span></button></li>
          ))}
        </ul>
        <input aria-label="Caption" className={`${inputCls} mb-3`} placeholder="Add a caption (optional)" maxLength={1000} value={caption} onChange={(e) => setCaption(e.target.value)} />
        <div className="flex justify-end gap-2"><button className={btnGhost} onClick={onClose}>Cancel</button><button className={btnPrimary} disabled={!dest || busy} onClick={go}><Forward size={16} /> Forward</button></div>
      </Modal>
  );
}

const deviceName = (ua = '') => {
  const b = /Edg\//.test(ua) ? 'Edge' : /Firefox\//.test(ua) ? 'Firefox' : /Chrome\//.test(ua) ? 'Chrome' : /Safari\//.test(ua) ? 'Safari' : 'Browser';
  const o = /Windows/.test(ua) ? 'Windows' : /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iOS' : /Mac OS/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : 'Unknown OS';
  return { label: `${b} on ${o}`, mobile: /Android|iPhone|iPad/.test(ua) };
};

export function SessionsSection() {
  const { toast } = useStore();
  const [list, setList] = useState(null);
  const [confirm, setConfirm] = useState(false);
  const load = () => api.get('api/sessions').then((r) => setList(r.data)).catch((e) => toast(errMsg(e), 'error'));
  useEffect(() => { load(); }, []); // eslint-disable-line
  const revoke = async (s) => { try { await api.delete(`/api/sessions/${s.id}`); toast('Session signed out'); load(); } catch (e) { toast(errMsg(e), 'error'); } };
  return (
      <section className="mb-6">
        <h3 className="mb-2 text-sm font-medium text-mute">Active sessions</h3>
        {list === null ? <div className="h-12 animate-pulse rounded-xl bg-panel2" /> : (
            <ul className="divide-y divide-line">
              {list.map((s) => { const d = deviceName(s.device); return (
                  <li key={s.id} className="flex items-center gap-3 py-2">
                    {d.mobile ? <Smartphone size={18} className="text-mute" /> : <Monitor size={18} className="text-mute" />}
                    <div className="min-w-0 flex-1 text-sm"><div className="truncate">{d.label}{s.current && <span className="ml-2 rounded-full bg-emerald-500/20 px-2 text-xs text-emerald-400">This device</span>}</div><div className="truncate text-xs text-mute">{s.ip} · active {when(s.lastSeenAt)}</div></div>
                    {!s.current && <button className={btnGhost} onClick={() => revoke(s)}>Sign out</button>}
                  </li>); })}
            </ul>
        )}
        {list && list.length > 1 && <button className={`${btnDanger} mt-2`} onClick={() => setConfirm(true)}>Sign out all other sessions</button>}
        {confirm && <Confirm danger title="Sign out other sessions" confirmLabel="Sign out all" text="Every other device will be signed out immediately." onClose={() => setConfirm(false)}
                             onConfirm={async () => { try { await api.post('/api/sessions/revoke-others'); toast('Other sessions signed out'); load(); } catch (e) { toast(errMsg(e), 'error'); } }} />}
      </section>
  );
}

const ACTIONS = { GROUP_CREATED: 'created the group', MEMBER_ADDED: 'added', MEMBER_REMOVED: 'removed', MEMBER_LEFT: 'left the group', ROLE_CHANGED: 'changed role of', OWNERSHIP_TRANSFERRED: 'transferred ownership to', GROUP_UPDATED: 'updated group details', ANNOUNCEMENT_ON: 'enabled announcement-only mode', ANNOUNCEMENT_OFF: 'disabled announcement-only mode', INVITE_CREATED: 'created an invite link', INVITE_REVOKED: 'revoked an invite link', MEMBER_JOINED: 'joined with an invite link', JOIN_APPROVED: 'approved the join request of', JOIN_REJECTED: 'declined the join request of' };

export function AuditSection({ id }) {
  const { toast } = useStore();
  const [open, setOpen] = useState(false);
  const [action, setAction] = useState('');
  const [items, setItems] = useState(null);
  const [page, setPage] = useState(0);
  const [more, setMore] = useState(false);
  const load = async (p, append) => {
    try {
      const { data } = await api.get(`/api/conversations/${id}/audit`, { params: { action, page: p, size: 15 } });
      setItems((c) => (append ? [...(c || []), ...data] : data)); setPage(p); setMore(data.length === 15);
    } catch (e) { toast(errMsg(e), 'error'); }
  };
  useEffect(() => { if (open) load(0, false); }, [open, action, id]); // eslint-disable-line
  return (
      <div className="mb-4 rounded-2xl border border-line p-3">
        <button className="flex w-full items-center justify-between text-sm font-medium" aria-expanded={open} onClick={() => setOpen(!open)}>Activity history<span className="text-mute">{open ? 'Hide' : 'Show'}</span></button>
        {open && (
            <div className="mt-2">
              <select aria-label="Filter activity" className={`${inputCls} mb-2`} value={action} onChange={(e) => setAction(e.target.value)}>
                <option value="">All activity</option>{Object.keys(ACTIONS).map((k) => <option key={k} value={k}>{k.replaceAll('_', ' ').toLowerCase()}</option>)}
              </select>
              {items === null ? <div className="h-10 animate-pulse rounded-xl bg-panel2" /> : items.length === 0 ? <p className="text-sm text-mute">No activity.</p> : (
                  <ul className="space-y-1.5 text-sm">{items.map((e) => <li key={e.id}><b>{e.actorName}</b> {ACTIONS[e.action] || e.action} {e.targetName && <b>{e.targetName}</b>}{e.detail && e.action === 'ROLE_CHANGED' ? ` to ${e.detail.toLowerCase()}` : ''}<div className="text-xs text-mute">{when(e.createdAt)}</div></li>)}</ul>
              )}
              {more && <div className="mt-2 text-center"><button className={btnGhost} onClick={() => load(page + 1, true)}>Load more</button></div>}
            </div>
        )}
      </div>
  );
}