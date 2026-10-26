import { useEffect, useState } from 'react';
import { Crown, LogOut, Moon, Sun, Shield, UserMinus, X } from 'lucide-react';
import api, { errMsg } from './api';
import { useStore } from './store';
import { AuditSection, SessionsSection } from './extras.jsx';
import { InviteSection, JoinRequestsSection } from './invites.jsx';
import ConnectedAccounts from './ConnectedAccounts.jsx';
import PasswordSection from './PasswordSection.jsx';
import { AiStatusSection } from './ai.jsx';
import DeleteAccount from './DeleteAccount.jsx';
import { Avatar, Confirm, Modal, UserPicker, btnDanger, btnGhost, btnPrimary, inputCls } from './components.jsx';

export function NewChatDialog({ onClose }) {
  const { open, loadConversations, toast } = useStore();
  const start = async (u) => {
    try {
      const { data } = await api.post('/api/conversations/direct', { userId: u.id });
      await loadConversations();
      await open(data.id);
      onClose();
    } catch (e) { toast(errMsg(e), 'error'); }
  };
  return <Modal title="New conversation" onClose={onClose}><UserPicker onPick={start} /></Modal>;
}

export function NewGroupDialog({ onClose }) {
  const { open, loadConversations, toast } = useStore();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [picked, setPicked] = useState([]);
  const [err, setErr] = useState({});
  const [busy, setBusy] = useState(false);
  const toggle = (u) => setPicked((p) => (p.some((x) => x.id === u.id) ? p.filter((x) => x.id !== u.id) : [...p, u]));
  const create = async (e) => {
    e.preventDefault();
    if (!name.trim()) return setErr({ name: 'Group name is required' });
    setBusy(true); setErr({});
    try {
      const { data } = await api.post('/api/conversations/groups', { name, description, avatarUrl, memberIds: picked.map((u) => u.id) });
      await loadConversations(); await open(data.id); onClose();
    } catch (er) { setErr(er.response?.data?.fields || { form: errMsg(er) }); toast(errMsg(er), 'error'); }
    setBusy(false);
  };
  return (
      <Modal title="Create group" onClose={onClose} wide>
        <form onSubmit={create} noValidate>
          <label htmlFor="gname" className="mb-1 block text-sm font-medium">Group name</label>
          <input id="gname" className={inputCls} value={name} onChange={(e) => setName(e.target.value)} aria-invalid={!!err.name} />
          {err.name && <p className="mt-1 text-xs text-red-400">{err.name}</p>}
          <label htmlFor="gdesc" className="mb-1 mt-3 block text-sm font-medium">Description <span className="text-mute">(optional)</span></label>
          <input id="gdesc" className={inputCls} value={description} onChange={(e) => setDescription(e.target.value)} />
          <label htmlFor="gav" className="mb-1 mt-3 block text-sm font-medium">Avatar image URL <span className="text-mute">(optional)</span></label>
          <input id="gav" className={inputCls} placeholder="https://…" value={avatarUrl} onChange={(e) => setAvatarUrl(e.target.value)} />
          {err.avatarUrl && <p className="mt-1 text-xs text-red-400">{err.avatarUrl}</p>}
          <div className="mb-2 mt-4 text-sm font-medium">Members</div>
          {picked.length > 0 && (
              <div className="mb-2 flex flex-wrap gap-2">
                {picked.map((u) => (
                    <span key={u.id} className="flex items-center gap-1 rounded-full bg-violet-600/20 py-1 pl-3 pr-1 text-xs text-violet-300">
                {u.name}<button type="button" aria-label={`Remove ${u.name}`} onClick={() => toggle(u)} className="rounded-full p-0.5 hover:bg-violet-600/30"><X size={12} /></button>
              </span>
                ))}
              </div>
          )}
          <UserPicker onPick={toggle} selectedIds={picked.map((u) => u.id)} />
          <div className="mt-4 flex justify-end gap-2">
            <button type="button" className={btnGhost} onClick={onClose}>Cancel</button>
            <button className={btnPrimary} disabled={busy}>{busy ? 'Creating…' : 'Create group'}</button>
          </div>
        </form>
      </Modal>
  );
}

export function GroupInfoDialog({ id, onClose }) {
  const { details, loadDetail, loadConversations, toast, user, online } = useStore();
  const d = details[id];
  const [edit, setEdit] = useState(null);
  const [adding, setAdding] = useState(false);
  const [confirm, setConfirm] = useState(null);
  useEffect(() => { loadDetail(id); }, [id, loadDetail]);
  if (!d) return <Modal title="Group" onClose={onClose}><div className="h-24 animate-pulse rounded-xl bg-panel2" /></Modal>;
  const c = d.conversation;
  const myRole = c.myRole;
  const canManage = myRole === 'OWNER' || myRole === 'ADMIN';
  const run = async (fn, ok) => {
    try { await fn(); await loadDetail(id); await loadConversations(); if (ok) toast(ok); } catch (e) { toast(errMsg(e), 'error'); }
  };
  const canRemove = (m) => m.userId !== user.id && (myRole === 'OWNER' || (myRole === 'ADMIN' && m.role === 'MEMBER'));
  return (
      <Modal title="Group details" onClose={onClose} wide>
        <div className="mb-4 flex items-center gap-3">
          <Avatar name={c.title} url={c.avatarUrl} size={56} />
          <div className="min-w-0 flex-1">
            <div className="truncate text-lg font-semibold">{c.title}</div>
            <div className="truncate text-sm text-mute">{c.description || 'No description'}</div>
            <div className="text-xs text-mute">{c.memberCount} members</div>
          </div>
          {canManage && <button className={btnGhost} onClick={() => setEdit({ name: c.title, description: c.description || '', avatarUrl: c.avatarUrl || '' })}>Edit</button>}
        </div>
        {edit && (
            <form className="mb-4 space-y-2 rounded-2xl border border-line p-3" onSubmit={(e) => { e.preventDefault(); run(() => api.patch(`/api/conversations/${id}`, edit), 'Group updated').then(() => setEdit(null)); }}>
              <input aria-label="Group name" className={inputCls} value={edit.name} onChange={(e) => setEdit({ ...edit, name: e.target.value })} />
              <input aria-label="Description" className={inputCls} placeholder="Description" value={edit.description} onChange={(e) => setEdit({ ...edit, description: e.target.value })} />
              <input aria-label="Avatar URL" className={inputCls} placeholder="Avatar URL (https://…)" value={edit.avatarUrl} onChange={(e) => setEdit({ ...edit, avatarUrl: e.target.value })} />
              <div className="flex justify-end gap-2"><button type="button" className={btnGhost} onClick={() => setEdit(null)}>Cancel</button><button className={btnPrimary}>Save</button></div>
            </form>
        )}
        {canManage && (
            <label className="mb-4 flex cursor-pointer items-center justify-between rounded-2xl border border-line p-3 text-sm">
              <span><b>Announcement-only mode</b><span className="block text-xs text-mute">Only owners and admins can send messages.</span></span>
              <input type="checkbox" className="h-5 w-5 accent-violet-600" checked={!!c.announcementOnly} onChange={() => run(() => api.patch(`/api/conversations/${id}/announcement`, { enabled: !c.announcementOnly }), c.announcementOnly ? 'Everyone can post' : 'Announcement-only enabled')} />
            </label>
        )}
        <div className="mb-2 flex items-center justify-between">
          <h3 className="text-sm font-medium">Members</h3>
          {canManage && <button className="text-sm text-violet-400 hover:underline" onClick={() => setAdding(!adding)}>{adding ? 'Done' : 'Add members'}</button>}
        </div>
        {adding && (
            <div className="mb-3 rounded-2xl border border-line p-3">
              <UserPicker excludeIds={d.members.map((m) => m.userId)} onPick={(u) => run(() => api.post(`/api/conversations/${id}/members`, { userIds: [u.id] }), `${u.name} added`)} />
            </div>
        )}
        <ul className="mb-4 divide-y divide-line">
          {d.members.map((m) => (
              <li key={m.userId} className="flex items-center gap-3 py-2">
                <Avatar name={m.name} url={m.avatarUrl} size={36} online={online.includes(m.userId)} />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium">{m.name}{m.userId === user.id && <span className="text-mute"> (you)</span>}</div>
                  <div className="flex items-center gap-1 text-xs text-mute">
                    {m.role === 'OWNER' && <Crown size={12} className="text-amber-400" />}{m.role === 'ADMIN' && <Shield size={12} className="text-emerald-400" />}{m.role.toLowerCase()}
                  </div>
                </div>
                {myRole === 'OWNER' && m.role !== 'OWNER' && (
                    <>
                      <button className="text-xs text-violet-400 hover:underline" onClick={() => run(() => api.put(`/api/conversations/${id}/members/${m.userId}/role`, { role: m.role === 'ADMIN' ? 'MEMBER' : 'ADMIN' }))}>{m.role === 'ADMIN' ? 'Demote' : 'Make admin'}</button>
                      <button className="text-xs text-amber-400 hover:underline" onClick={() => setConfirm({ title: 'Transfer ownership', text: `Make ${m.name} the owner? You will become an admin.`, label: 'Transfer', fn: () => run(() => api.post(`/api/conversations/${id}/transfer`, { userId: m.userId }), 'Ownership transferred') })}>Make owner</button>
                    </>
                )}
                {canRemove(m) && <button aria-label={`Remove ${m.name}`} className="rounded-lg p-1.5 text-red-400 hover:bg-red-500/10" onClick={() => setConfirm({ title: 'Remove member', text: `Remove ${m.name} from this group?`, label: 'Remove', danger: true, fn: () => run(() => api.delete(`/api/conversations/${id}/members/${m.userId}`), 'Member removed') })}><UserMinus size={16} /></button>}
              </li>
          ))}
        </ul>
        {canManage && <JoinRequestsSection id={id} />}
        {canManage && <InviteSection id={id} />}
        {canManage && <AuditSection id={id} />}
        <button className={`${btnDanger} w-full`} onClick={() => setConfirm({ title: 'Leave group', text: myRole === 'OWNER' ? 'You are the owner. Ownership passes to another member when you leave.' : 'You will stop receiving messages from this group.', label: 'Leave', danger: true, fn: async () => { try { await api.post(`/api/conversations/${id}/leave`); await loadConversations(); onClose(); } catch (e) { toast(errMsg(e), 'error'); } } })}><LogOut size={16} /> Leave group</button>
        {confirm && <Confirm title={confirm.title} text={confirm.text} confirmLabel={confirm.label} danger={confirm.danger} onConfirm={confirm.fn} onClose={() => setConfirm(null)} />}
      </Modal>
  );
}

export function SettingsDialog({ onClose }) {
  const { user, setUser, theme, setTheme, toast, logout } = useStore();
  const [f, setF] = useState({ name: user.name, about: user.about || '', avatarUrl: user.avatarUrl || '' });
  const [errs, setErrs] = useState({});
  const [blocked, setBlocked] = useState(null);
  useEffect(() => { api.get('/api/users/blocked').then((r) => setBlocked(r.data)).catch((e) => toast(errMsg(e), 'error')); }, [toast]);
  const save = async (e) => {
    e.preventDefault(); setErrs({});
    try { const { data } = await api.put('/api/users/me', f); setUser(data); toast('Profile saved'); } catch (er) { setErrs(er.response?.data?.fields || {}); toast(errMsg(er), 'error'); }
  };
  const unblock = async (u) => {
    try { await api.delete(`/api/users/${u.id}/block`); setBlocked((b) => b.filter((x) => x.id !== u.id)); toast(`${u.name} unblocked`); } catch (e) { toast(errMsg(e), 'error'); }
  };
  return (
      <Modal title="Settings" onClose={onClose} wide>
        <section className="mb-6">
          <h3 className="mb-2 text-sm font-medium text-mute">Profile</h3>
          <form onSubmit={save} className="space-y-2">
            <div className="flex items-center gap-3"><Avatar name={f.name || '?'} url={f.avatarUrl} size={56} /><div className="text-sm text-mute">{user.email}</div></div>
            <input aria-label="Name" className={inputCls} value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} />
            {errs.name && <p className="text-xs text-red-400">{errs.name}</p>}
            <input aria-label="About" className={inputCls} placeholder="About you" value={f.about} onChange={(e) => setF({ ...f, about: e.target.value })} />
            <input aria-label="Avatar URL" className={inputCls} placeholder="Avatar image URL (https://…)" value={f.avatarUrl} onChange={(e) => setF({ ...f, avatarUrl: e.target.value })} />
            {errs.avatarUrl && <p className="text-xs text-red-400">{errs.avatarUrl}</p>}
            <button className={btnPrimary}>Save profile</button>
          </form>
        </section>
        <section className="mb-6">
          <h3 className="mb-2 text-sm font-medium text-mute">Appearance</h3>
          <div className="flex gap-2">
            <button className={theme === 'dark' ? btnPrimary : btnGhost} onClick={() => setTheme('dark')}><Moon size={16} /> Dark</button>
            <button className={theme === 'light' ? btnPrimary : btnGhost} onClick={() => setTheme('light')}><Sun size={16} /> Light</button>
          </div>
        </section>
        <section className="mb-6">
          <h3 className="mb-2 text-sm font-medium text-mute">Blocked users</h3>
          {blocked === null ? <div className="h-10 animate-pulse rounded-xl bg-panel2" /> : blocked.length === 0 ? <p className="text-sm text-mute">You have not blocked anyone.</p> : (
              <ul className="divide-y divide-line">{blocked.map((u) => (
                  <li key={u.id} className="flex items-center gap-3 py-2"><Avatar name={u.name} url={u.avatarUrl} size={32} /><span className="flex-1 truncate text-sm">{u.name}</span><button className={btnGhost} onClick={() => unblock(u)}>Unblock</button></li>
              ))}</ul>
          )}
        </section>
        <AiStatusSection />
        <PasswordSection />

        <ConnectedAccounts />
        <SessionsSection />

        {/* Permanently delete the current account */}
        <DeleteAccount />

        <button
            className={`${btnGhost} w-full`}
            onClick={logout}
        >
          <LogOut size={16} /> Sign out
        </button>

      </Modal>
  );
}