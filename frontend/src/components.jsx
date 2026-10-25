import { useEffect, useState } from 'react';
import { X, Search, Check, AlertCircle, MessageCircle } from 'lucide-react';
import api, { errMsg } from './api';
import { useStore } from './store';

export const btn = 'inline-flex items-center justify-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-400 disabled:cursor-not-allowed disabled:opacity-50';
export const btnPrimary = `${btn} bg-gradient-to-r from-violet-600 to-indigo-600 text-white shadow-lg shadow-violet-900/30 hover:brightness-110`;
export const btnGhost = `${btn} bg-panel2 text-ink hover:bg-line`;
export const btnDanger = `${btn} bg-red-600/90 text-white hover:bg-red-600`;
export const inputCls = 'w-full rounded-xl border border-line bg-panel2 px-3 py-2 text-sm text-ink placeholder:text-mute focus:border-violet-500 focus:outline-none focus:ring-2 focus:ring-violet-500/30';
export const iconBtn = 'rounded-lg p-2 text-mute transition hover:bg-panel2 hover:text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-400';

const palette = ['from-violet-500 to-indigo-600', 'from-emerald-500 to-teal-600', 'from-fuchsia-500 to-violet-600', 'from-sky-500 to-indigo-600', 'from-amber-500 to-rose-500'];

export function Avatar({ name = '?', url, size = 40, online }) {
  const bg = palette[(name.charCodeAt(0) || 0) % palette.length];
  const [broken, setBroken] = useState(false);
  return (
    <div className="relative shrink-0" style={{ width: size, height: size }}>
      {url && !broken
        ? <img src={url} alt="" onError={() => setBroken(true)} className="h-full w-full rounded-full object-cover" />
        : <div className={`flex h-full w-full items-center justify-center rounded-full bg-gradient-to-br ${bg} font-semibold text-white`} style={{ fontSize: size * 0.4 }}>{(name.trim()[0] || '?').toUpperCase()}</div>}
      {online !== undefined && <span aria-label={online ? 'Online' : 'Offline'} className={`absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-panel ${online ? 'bg-emerald-400' : 'bg-slate-500'}`} />}
    </div>
  );
}

export function Modal({ title, onClose, children, wide }) {
  useEffect(() => {
    const h = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [onClose]);
  return (
    <div className="fixed inset-0 z-40 flex items-end justify-center bg-black/60 backdrop-blur-sm sm:items-center sm:p-4" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div role="dialog" aria-modal="true" aria-label={title} className={`max-h-[90vh] w-full overflow-y-auto rounded-t-3xl border border-line bg-panel p-5 shadow-2xl sm:rounded-3xl ${wide ? 'sm:max-w-xl' : 'sm:max-w-md'}`}>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="font-display text-xl">{title}</h2>
          <button aria-label="Close" onClick={onClose} className={iconBtn}><X size={18} /></button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Confirm({ title, text, confirmLabel = 'Confirm', danger, onConfirm, onClose }) {
  const [busy, setBusy] = useState(false);
  return (
    <Modal title={title} onClose={onClose}>
      <p className="mb-5 text-sm text-mute">{text}</p>
      <div className="flex justify-end gap-2">
        <button className={btnGhost} onClick={onClose}>Cancel</button>
        <button className={danger ? btnDanger : btnPrimary} disabled={busy} onClick={async () => { setBusy(true); await onConfirm(); setBusy(false); onClose(); }}>{confirmLabel}</button>
      </div>
    </Modal>
  );
}

export function Toasts() {
  const toasts = useStore((s) => s.toasts);
  return (
    <div className="pointer-events-none fixed inset-x-0 top-3 z-50 flex flex-col items-center gap-2 px-3" role="status" aria-live="polite">
      {toasts.map((t) => (
        <div key={t.id} className="pointer-events-auto flex max-w-sm items-center gap-2 rounded-2xl border border-line bg-panel px-4 py-3 text-sm shadow-xl">
          {t.kind === 'error' ? <AlertCircle size={16} className="text-red-400" /> : t.kind === 'message' ? <MessageCircle size={16} className="text-violet-400" /> : <Check size={16} className="text-emerald-400" />}
          <span className="truncate">{t.text}</span>
        </div>
      ))}
    </div>
  );
}

export const Skeleton = ({ className = '' }) => <div className={`animate-pulse rounded-xl bg-panel2 ${className}`} />;

/** Debounced user search. `onPick(user)` fires on click; `selectedIds` shows a check mark. */
export function UserPicker({ onPick, selectedIds = [], excludeIds = [] }) {
  const [q, setQ] = useState('');
  const [res, setRes] = useState([]);
  const [busy, setBusy] = useState(false);
  const toast = useStore((s) => s.toast);
  useEffect(() => {
    if (q.trim().length < 2) { setRes([]); return undefined; }
    setBusy(true);
    const h = setTimeout(async () => {
      try { const { data } = await api.get('/users/search', { params: { q: q.trim() } }); setRes(data); } catch (e) { toast(errMsg(e), 'error'); }
      setBusy(false);
    }, 250);
    return () => clearTimeout(h);
  }, [q, toast]);
  const list = res.filter((u) => !excludeIds.includes(u.id));
  return (
    <div>
      <div className="relative mb-2">
        <Search size={16} className="absolute left-3 top-2.5 text-mute" />
        <input autoFocus aria-label="Search people" className={`${inputCls} pl-9`} placeholder="Search by name or email…" value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      <ul className="max-h-60 overflow-y-auto">
        {busy && <li className="p-3 text-sm text-mute">Searching…</li>}
        {!busy && q.trim().length >= 2 && list.length === 0 && <li className="p-3 text-sm text-mute">No one found.</li>}
        {!busy && q.trim().length < 2 && <li className="p-3 text-sm text-mute">Type at least 2 characters.</li>}
        {list.map((u) => (
          <li key={u.id}>
            <button onClick={() => onPick(u)} className="flex w-full items-center gap-3 rounded-xl p-2 text-left hover:bg-panel2 focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-400">
              <Avatar name={u.name} url={u.avatarUrl} size={36} />
              <div className="min-w-0 flex-1"><div className="truncate text-sm font-medium">{u.name}</div>{u.about && <div className="truncate text-xs text-mute">{u.about}</div>}</div>
              {selectedIds.includes(u.id) && <Check size={16} className="text-emerald-400" />}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
