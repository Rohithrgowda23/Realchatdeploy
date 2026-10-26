import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Archive, ArchiveRestore, ArrowLeft, Ban, Bell, BellOff, Bookmark, BookmarkCheck, Check, CheckCheck, EyeOff, AtSign, Camera, Clock, Forward, Images, Info, Languages, Megaphone, Sparkles, MessageSquarePlus, Pencil, Phone, Pin, PinOff, Reply, Search, Send, Settings, Smile, Trash2, Users, Video, X } from 'lucide-react';
import api, { errMsg } from './api';
import { connectWs, disconnectWs } from './ws';
import { useStore } from './store';
import { Avatar, Confirm, Skeleton, btnGhost, btnPrimary, iconBtn, inputCls } from './components.jsx';
import { OAUTH_ERRORS } from './oauthMessages.js';
import { StoriesDialog } from './stories.jsx';
import { CallOverlay, CallsDialog } from './calls.jsx';
import { useCallStore } from './callStore';
import { SummaryDialog, TranslateDialog } from './ai.jsx';
import { ScheduleDialog, ScheduledDialog } from './scheduled.jsx';
import { NotificationsDialog } from './notifications.jsx';
import { MentionMenu, MentionsDialog, MessageText, useMentions } from './mentions.jsx';
import { AttachmentView, ComposerTools, GalleryDialog } from './media.jsx';
import { ForwardDialog, SavedDialog, SearchDialog } from './extras.jsx';
import { GroupInfoDialog, NewChatDialog, NewGroupDialog, SettingsDialog } from './dialogs.jsx';

const EMOJIS = ['👍', '❤️', '😂', '🎉', '😮', '🙏'];
const timeOf = (iso) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
const dayOf = (iso) => new Date(iso).toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });
const shortWhen = (iso) => {
  const d = new Date(iso), now = new Date();
  return d.toDateString() === now.toDateString() ? timeOf(iso) : d.toLocaleDateString([], { month: 'short', day: 'numeric' });
};

export default function Chat() {
  const theme = useStore((s) => s.theme);
  const activeId = useStore((s) => s.activeId);
  const [dialog, setDialog] = useState(null);
  const forwardMsg = useStore((s) => s.forwardMsg);
  const wsOnline = useStore((s) => s.wsOnline);
  const setForwardMsg = useStore((s) => s.setForwardMsg);

  useEffect(() => { document.documentElement.dataset.theme = theme; }, [theme]);
  useEffect(() => { // result of "Connect Google" in Settings
    const r = new URLSearchParams(window.location.search).get('oauth_link');
    if (!r) return;
    const ok = r === 'ok';
    useStore.getState().toast(ok ? 'Google account connected' : (OAUTH_ERRORS[r] || OAUTH_ERRORS.failed), ok ? 'info' : 'error');
    window.history.replaceState({}, '', '/');
  }, []);
  useEffect(() => {
    const st = () => useStore.getState();
    connectWs({
      token: st().token,
      onEvent: (ev) => (ev.type.startsWith('CALL_') ? useCallStore.getState().handle(ev) : st().handleEvent(ev)),
      onDisconnect: () => st().setWsOnline(false),
      onPresence: (p) => st().setPresence(p.userId, p.online),
      onError: (m, fatal) => { st().toast(m, 'error'); if (fatal) st().logout(); },
      onConnect: () => { // also runs after every reconnect -> recovers anything missed while offline
        st().loadConversations();
        st().loadSavedIds();
        st().loadNotifMeta();
        st().loadAiStatus();
        st().loadStoryCount();
        st().loadMissedCount();
        st().setWsOnline(true);
        api.get('/api/presence').then((r) => st().setOnlineList(r.data)).catch(() => {});
        if (st().activeId) st().open(st().activeId);
      },
    });
    return disconnectWs;
  }, []);

  return (
      <div className="flex h-dvh overflow-hidden">
        {!wsOnline && <div role="status" className="fixed inset-x-0 top-0 z-50 bg-amber-500 py-1 text-center text-xs font-medium text-black">Connection lost. Reconnecting…</div>}
        <CallOverlay />
        <Sidebar className={activeId ? 'hidden md:flex' : 'flex'} onDialog={setDialog} />
        <ChatPane className={activeId ? 'flex' : 'hidden md:flex'} onDialog={setDialog} />
        {dialog === 'new' && <NewChatDialog onClose={() => setDialog(null)} />}
        {dialog === 'group' && <NewGroupDialog onClose={() => setDialog(null)} />}
        {dialog === 'calls' && <CallsDialog onClose={() => setDialog(null)} />}
        {dialog === 'stories' && <StoriesDialog onClose={() => setDialog(null)} />}
        {dialog === 'scheduled' && <ScheduledDialog onClose={() => setDialog(null)} />}
        {dialog === 'notifications' && <NotificationsDialog onClose={() => setDialog(null)} />}
        {dialog === 'saved' && <SavedDialog onClose={() => setDialog(null)} />}
        {dialog === 'search' && <SearchDialog onClose={() => setDialog(null)} />}
        {forwardMsg && <ForwardDialog message={forwardMsg} onClose={() => setForwardMsg(null)} />}
        {dialog === 'settings' && <SettingsDialog onClose={() => setDialog(null)} />}
        {dialog === 'info' && activeId && <GroupInfoDialog id={activeId} onClose={() => setDialog(null)} />}
      </div>
  );
}

function Sidebar({ className, onDialog }) {
  const { conversations, convLoading, activeId, open, online, user, notifUnread, storyUnviewed, missedCalls } = useStore();
  const [tab, setTab] = useState('chats');
  const [q, setQ] = useState('');
  const archivedCount = conversations.filter((c) => c.archived).length;
  const list = conversations.filter((c) => (tab === 'archived') === c.archived && c.title.toLowerCase().includes(q.toLowerCase()));
  return (
      <aside className={`${className} w-full flex-col border-r border-line bg-panel md:w-[360px] md:shrink-0`}>
        <div className="flex items-center gap-3 p-4">
          <button onClick={() => onDialog('settings')} aria-label="Profile and settings" className="rounded-full focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-400"><Avatar name={user.name} url={user.avatarUrl} size={40} /></button>
          <div className="min-w-0 flex-1"><div className="font-display text-xl leading-tight">RealChat</div><div className="truncate text-xs text-mute">{user.name}</div></div>
          <button className={`${iconBtn} relative`} aria-label={`Notifications${notifUnread ? `, ${notifUnread} unread` : ''}`} title="Notifications" onClick={() => onDialog('notifications')}><Bell size={20} />{notifUnread > 0 && <span className="absolute -right-0.5 -top-0.5 min-w-4 rounded-full bg-emerald-500 px-1 text-center text-[10px] font-semibold leading-4 text-white">{notifUnread > 99 ? '99+' : notifUnread}</span>}</button>
          <button className={iconBtn} aria-label="New conversation" title="New conversation" onClick={() => onDialog('new')}><MessageSquarePlus size={20} /></button>
          <button className={iconBtn} aria-label="New group" title="New group" onClick={() => onDialog('group')}><Users size={20} /></button>
        </div>
        <div className="flex gap-1 overflow-x-auto px-4 pb-2 text-xs">
          {[['search', 'Search', Search], ['saved', 'Saved', Bookmark], ['stories', 'Stories', Camera], ['calls', 'Calls', Phone], ['scheduled', 'Scheduled', Clock], ['settings', 'Settings', Settings]].map(([k, label, Icon]) => (
              <button key={k} onClick={() => onDialog(k)} className="flex shrink-0 items-center gap-1.5 rounded-full bg-panel2 px-3 py-1.5 text-mute hover:text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-400"><Icon size={14} />{label}{k === 'calls' && missedCalls > 0 && <span aria-label="Missed calls" className="h-2 w-2 rounded-full bg-red-500" />}{k === 'stories' && storyUnviewed > 0 && <span aria-label="New stories" className="h-2 w-2 rounded-full bg-emerald-400" />}</button>
          ))}
        </div>
        <div className="px-4">
          <div className="relative"><Search size={16} className="absolute left-3 top-2.5 text-mute" /><input aria-label="Filter conversations" className={`${inputCls} pl-9`} placeholder="Search chats…" value={q} onChange={(e) => setQ(e.target.value)} /></div>
          <div className="mt-3 flex gap-1 rounded-xl bg-panel2 p-1 text-sm" role="tablist">
            {[['chats', 'Chats'], ['archived', `Archived${archivedCount ? ` (${archivedCount})` : ''}`]].map(([k, label]) => (
                <button key={k} role="tab" aria-selected={tab === k} onClick={() => setTab(k)} className={`flex-1 rounded-lg px-3 py-1.5 transition ${tab === k ? 'bg-panel text-ink shadow' : 'text-mute hover:text-ink'}`}>{label}</button>
            ))}
          </div>
        </div>
        <ul className="mt-2 flex-1 overflow-y-auto px-2 pb-2">
          {convLoading && [0, 1, 2, 3, 4].map((i) => <li key={i} className="flex items-center gap-3 p-3"><Skeleton className="h-12 w-12 rounded-full" /><div className="flex-1 space-y-2"><Skeleton className="h-3 w-1/2" /><Skeleton className="h-3 w-3/4" /></div></li>)}
          {!convLoading && list.length === 0 && (
              <li className="px-6 py-12 text-center text-sm text-mute">
                {tab === 'archived' ? 'No archived chats.' : q ? 'No chats match your search.' : <>
                  <p className="mb-4">No conversations yet.</p>
                  <div className="flex justify-center gap-2"><button className={btnPrimary} onClick={() => onDialog('new')}>Start a chat</button><button className={btnGhost} onClick={() => onDialog('group')}>New group</button></div></>}
              </li>
          )}
          {list.map((c) => {
            const last = c.lastMessage;
            return (
                <li key={c.id}>
                  <button onClick={() => open(c.id)} aria-current={c.id === activeId} className={`flex w-full items-center gap-3 rounded-2xl p-3 text-left transition focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-400 ${c.id === activeId ? 'bg-violet-600/15' : 'hover:bg-panel2'}`}>
                    <Avatar name={c.title} url={c.avatarUrl} size={48} online={c.type === 'DIRECT' ? online.includes(c.otherUserId) : undefined} />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-1">
                        <span className="truncate font-medium">{c.title}</span>
                        {c.type === 'GROUP' && <Users size={12} className="shrink-0 text-mute" />}
                        {c.pinned && <Pin size={12} className="shrink-0 text-violet-400" />}
                        {c.muted && <BellOff size={12} className="shrink-0 text-mute" />}
                        <span className="ml-auto shrink-0 text-xs text-mute">{last ? shortWhen(last.createdAt) : ''}</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="flex-1 truncate text-sm text-mute">{last ? `${last.senderId === user.id ? 'You: ' : ''}${last.preview}` : 'No messages yet'}</span>
                        {c.unreadMentions > 0 && <span aria-label="You were mentioned" className="rounded-full bg-violet-600 px-1.5 text-xs font-semibold text-white">@</span>}
                        {c.unread > 0 && <span className={`min-w-5 rounded-full px-1.5 text-center text-xs font-semibold text-white ${c.muted ? 'bg-slate-500' : 'bg-emerald-500'}`}>{c.unread > 99 ? '99+' : c.unread}</span>}
                      </div>
                    </div>
                  </button>
                </li>
            );
          })}
        </ul>
      </aside>
  );
}

function ChatPane({ className, onDialog }) {
  const { activeId, conversations, details, messages, hasMore, msgLoading, loadOlder, typing, online, user, replyTo, setReplyTo, send, sendTyping, open, patchConversation, loadConversations, toast } = useStore();
  const conv = conversations.find((c) => c.id === activeId);
  const list = messages[activeId] || [];
  const detail = details[activeId];
  const mention = useMentions((detail?.members || []).filter((m) => m.userId !== user.id));
  const scroller = useRef(null);
  const stick = useRef(true);
  const prevHeight = useRef(0);
  const [text, setText] = useState('');
  const [searching, setSearching] = useState(false);
  const [gallery, setGallery] = useState(false);
  const [mentionsOpen, setMentionsOpen] = useState(false);
  const [scheduling, setScheduling] = useState(false);
  const [summaryOpen, setSummaryOpen] = useState(false);
  const ai = useStore((s) => s.ai);
  const startCall = useCallStore((s) => s.startCall);
  const [confirmBlock, setConfirmBlock] = useState(false);
  const lastTyping = useRef(0);
  const readOnly = !!conv && conv.announcementOnly && conv.myRole === 'MEMBER';

  useEffect(() => { setText(''); setSearching(false); stick.current = true; }, [activeId]);
  useLayoutEffect(() => {
    const el = scroller.current;
    if (!el) return;
    if (prevHeight.current) { el.scrollTop += el.scrollHeight - prevHeight.current; prevHeight.current = 0; }
    else if (stick.current) el.scrollTop = el.scrollHeight;
  }, [list.length, activeId, msgLoading]);

  const typers = Object.values(typing[activeId] || {});
  const others = (detail?.members || []).filter((m) => m.userId !== user.id);
  const readState = (m) => { // 0 sent, 1 delivered to all, 2 read by some, 3 read by all
    if (!others.length) return 0;
    const read = others.filter((o) => o.lastReadMessageId >= m.id).length;
    if (read === others.length) return 3;
    if (read > 0) return 2;
    const delivered = others.filter((o) => (o.lastDeliveredMessageId || 0) >= m.id).length;
    return delivered === others.length ? 1 : 0;
  };

  if (!conv) {
    return (
        <section className={`${className} flex-1 flex-col items-center justify-center bg-bg p-8 text-center`}>
          <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-3xl bg-gradient-to-br from-violet-600/30 to-emerald-500/20 text-violet-300"><Send size={28} /></div>
          <h2 className="font-display text-2xl">Select a conversation</h2>
          <p className="mt-1 max-w-xs text-sm text-mute">Pick a chat from the list, or start a new one with someone.</p>
        </section>
    );
  }

  const setting = async (patch, msg) => {
    try { const { data } = await api.patch(`/api/conversations/${conv.id}/settings`, patch); patchConversation(data); toast(msg); } catch (e) { toast(errMsg(e), 'error'); }
  };
  const submit = async (e) => {
    e?.preventDefault();
    const t = text.trim();
    if (!t) return;
    setText(''); sendTyping(false); lastTyping.current = 0; stick.current = true;
    const rid = replyTo?.id; setReplyTo(null);
    const ids = mention.collect(t); mention.reset();
    if (!(await send(t, rid, ids))) setText(t);
  };
  const onType = (v) => {
    setText(v);
    const now = Date.now();
    if (v && now - lastTyping.current > 2000) { lastTyping.current = now; sendTyping(true); }
  };
  const header = conv.type === 'GROUP'
      ? `${conv.memberCount} members${typers.length ? '' : ''}`
      : online.includes(conv.otherUserId) ? 'Online' : 'Offline';

  let prevDay = '';
  return (
      <section className={`${className} min-w-0 flex-1 flex-col bg-bg`}>
        <header className="flex items-center gap-3 border-b border-line bg-panel px-3 py-3 md:px-5">
          <button className={`${iconBtn} md:hidden`} aria-label="Back to chats" onClick={() => open(null)}><ArrowLeft size={20} /></button>
          <Avatar name={conv.title} url={conv.avatarUrl} size={42} online={conv.type === 'DIRECT' ? online.includes(conv.otherUserId) : undefined} />
          <div className="min-w-0 flex-1">
            <div className="truncate font-semibold">{conv.title}</div>
            <div className={`truncate text-xs ${typers.length ? 'text-emerald-400' : 'text-mute'}`}>{typers.length ? `${typers.join(', ')} typing…` : header}</div>
          </div>
          <div className="flex shrink-0 items-center overflow-x-auto">
            {conv.type === 'DIRECT' && <>
              <button className={iconBtn} aria-label="Start audio call" title="Audio call" onClick={() => startCall(conv.id, { id: conv.otherUserId, name: conv.title, avatarUrl: conv.avatarUrl }, 'AUDIO')}><Phone size={18} /></button>
              <button className={iconBtn} aria-label="Start video call" title="Video call" onClick={() => startCall(conv.id, { id: conv.otherUserId, name: conv.title, avatarUrl: conv.avatarUrl }, 'VIDEO')}><Video size={18} /></button>
            </>}
            {ai.enabled && <button className={iconBtn} aria-label="Summarize conversation" title="Summarize with AI" onClick={() => setSummaryOpen(true)}><Sparkles size={18} /></button>}
            <button className={iconBtn} aria-label="Mentions of you" title="Mentions of you" onClick={() => setMentionsOpen(true)}><AtSign size={18} /></button>
            <button className={iconBtn} aria-label="Shared media" title="Shared media" onClick={() => setGallery(true)}><Images size={18} /></button>
            <button className={iconBtn} aria-label="Search messages" title="Search messages" onClick={() => setSearching(!searching)}><Search size={18} /></button>
            <button className={iconBtn} aria-label={conv.pinned ? 'Unpin' : 'Pin'} title={conv.pinned ? 'Unpin' : 'Pin'} onClick={() => setting({ pinned: !conv.pinned }, conv.pinned ? 'Unpinned' : 'Pinned')}>{conv.pinned ? <PinOff size={18} /> : <Pin size={18} />}</button>
            <button className={iconBtn} aria-label={conv.muted ? 'Unmute' : 'Mute'} title={conv.muted ? 'Unmute' : 'Mute'} onClick={() => setting({ muted: !conv.muted }, conv.muted ? 'Unmuted' : 'Muted')}>{conv.muted ? <Bell size={18} /> : <BellOff size={18} />}</button>
            <button className={iconBtn} aria-label={conv.archived ? 'Unarchive' : 'Archive'} title={conv.archived ? 'Unarchive' : 'Archive'} onClick={() => setting({ archived: !conv.archived }, conv.archived ? 'Moved to chats' : 'Archived')}>{conv.archived ? <ArchiveRestore size={18} /> : <Archive size={18} />}</button>
            {conv.type === 'GROUP'
                ? <button className={iconBtn} aria-label="Group details" title="Group details" onClick={() => onDialog('info')}><Info size={18} /></button>
                : <button className={iconBtn} aria-label="Block user" title="Block user" onClick={() => setConfirmBlock(true)}><Ban size={18} /></button>}
          </div>
        </header>
        {searching && <MessageSearch convId={conv.id} onClose={() => setSearching(false)} />}
        <div ref={scroller} className="flex-1 overflow-y-auto px-3 py-4 md:px-6" onScroll={(e) => { const el = e.currentTarget; stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 120; }}>
          {hasMore[conv.id] && <div className="mb-3 text-center"><button className={btnGhost} onClick={() => { prevHeight.current = scroller.current.scrollHeight; loadOlder(conv.id); }}>Load earlier messages</button></div>}
          {msgLoading && list.length === 0 && <div className="space-y-3">{[0, 1, 2].map((i) => <Skeleton key={i} className={`h-12 w-2/3 ${i % 2 ? 'ml-auto' : ''}`} />)}</div>}
          {!msgLoading && list.length === 0 && <p className="mt-16 text-center text-sm text-mute">No messages yet. Say hello 👋</p>}
          {list.map((m) => {
            const d = dayOf(m.createdAt);
            const sep = d !== prevDay; prevDay = d;
            return (
                <div key={m.id}>
                  {sep && <div className="my-3 text-center text-xs text-mute">{d}</div>}
                  <Bubble m={m} mine={m.senderId === user.id} group={conv.type === 'GROUP'} read={readState(m)} canModerate={conv.type === 'GROUP' && (conv.myRole === 'OWNER' || conv.myRole === 'ADMIN')} />
                </div>
            );
          })}
        </div>
        {replyTo && (
            <div className="mx-3 flex items-center gap-2 rounded-t-2xl border border-b-0 border-line bg-panel px-4 py-2 text-sm md:mx-6">
              <Reply size={14} className="text-violet-400" /><span className="min-w-0 flex-1 truncate"><b>{replyTo.senderName}</b> <span className="text-mute">{replyTo.content}</span></span>
              <button aria-label="Cancel reply" className={iconBtn} onClick={() => setReplyTo(null)}><X size={14} /></button>
            </div>
        )}
        {readOnly ? <div className="border-t border-line bg-panel p-4 text-center text-sm text-mute"><Megaphone size={14} className="mr-1 inline" /> Only admins can send messages in this group.</div> : <form onSubmit={submit} className="relative flex items-end gap-2 border-t border-line bg-panel p-3 md:px-6">
          {mention.open && <MentionMenu m={mention} onPick={(c) => setText((v) => mention.choose(c, v))} />}
          <ComposerTools convId={conv.id} caption={text} onSent={() => { setText(''); stick.current = true; }} />
          <textarea aria-label="Message" rows={1} maxLength={4000} value={text} onChange={(e) => { onType(e.target.value); mention.onChange(e.target.value, e.target.selectionStart); }}
                    onKeyDown={(e) => { if (mention.onKeyDown(e, text, setText)) return; if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit(); } }}
                    placeholder="Write a message…" className={`${inputCls} max-h-32 min-h-[42px] resize-none`} />
          <button type="button" className={iconBtn} aria-label="Schedule message" title="Schedule message" disabled={!text.trim()} onClick={() => setScheduling(true)}><Clock size={20} /></button>
          <button className={`${btnPrimary} h-[42px]`} aria-label="Send message" disabled={!text.trim()}><Send size={18} /></button>
        </form>}
        {summaryOpen && <SummaryDialog convId={conv.id} onClose={() => setSummaryOpen(false)} />}
        {scheduling && <ScheduleDialog convId={conv.id} content={text} onClose={() => setScheduling(false)} onDone={() => { setText(''); mention.reset(); }} />}
        {mentionsOpen && <MentionsDialog convId={conv.id} onClose={() => setMentionsOpen(false)} />}
        {gallery && <GalleryDialog convId={conv.id} onClose={() => setGallery(false)} />}
        {confirmBlock && <Confirm danger title="Block user" confirmLabel="Block" text={`${conv.title} will no longer be able to message you, and you cannot message them. You can unblock in Settings.`}
                                  onClose={() => setConfirmBlock(false)} onConfirm={async () => { try { await api.post(`/api/users/${conv.otherUserId}/block`); toast(`${conv.title} blocked`); loadConversations(); } catch (e) { toast(errMsg(e), 'error'); } }} />}
      </section>
  );
}

function Bubble({ m, mine, group, read, canModerate }) {
  const { setReplyTo, toast, saved, toggleSave, hideMessage, setForwardMsg, jumpTo, ai } = useStore();
  const isSaved = saved.includes(m.id);
  const [sel, setSel] = useState(false);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(m.content || '');
  const [confirmDel, setConfirmDel] = useState(false);
  const [translating, setTranslating] = useState(false);
  const call = async (fn) => { try { await fn(); } catch (e) { toast(errMsg(e), 'error'); } };
  const saveEdit = () => call(async () => { await api.put(`/messages/${m.id}`, { content: draft }); setEditing(false); });
  const reactions = Object.entries(m.reactions || {});
  const me = useStore.getState().user.id;

  return (
      <div id={`msg-${m.id}`} className={`group mb-1.5 flex rounded-2xl transition ${mine ? 'justify-end' : 'justify-start'}`}>
        <div className={`relative max-w-[85%] md:max-w-[65%] ${mine ? 'items-end' : 'items-start'} flex flex-col`}>
          <div onClick={() => setSel(!sel)} className={`rounded-2xl px-3.5 py-2 text-sm shadow-sm ${mine ? 'rounded-br-md bg-gradient-to-br from-violet-600 to-indigo-600 text-white' : 'rounded-bl-md border border-line bg-panel text-ink'}`}>
            {group && !mine && <div className="mb-0.5 text-xs font-semibold text-emerald-400">{m.senderName}</div>}
            {m.forwardedFrom && <div className="mb-1 flex items-center gap-1 text-xs italic opacity-80"><Forward size={12} /> Forwarded from {m.forwardedFrom}</div>}
            {m.replyTo && <div onClick={(e) => { e.stopPropagation(); jumpTo(m.conversationId, m.replyTo.id); }} className={`cursor-pointer mb-1 rounded-lg border-l-2 px-2 py-1 text-xs ${mine ? 'border-white/60 bg-white/10' : 'border-violet-400 bg-panel2'}`}><b>{m.replyTo.senderName}</b><div className="truncate opacity-80">{m.replyTo.content ?? 'Message deleted'}</div></div>}
            {m.deleted ? <em className="opacity-70">This message was deleted</em> : editing ? (
                <div onClick={(e) => e.stopPropagation()} className="flex items-center gap-1">
                  <input autoFocus aria-label="Edit message" className="w-56 rounded-lg bg-black/20 px-2 py-1 text-white outline-none" value={draft} onChange={(e) => setDraft(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') saveEdit(); if (e.key === 'Escape') setEditing(false); }} />
                  <button aria-label="Save edit" onClick={saveEdit}><Check size={16} /></button><button aria-label="Cancel edit" onClick={() => setEditing(false)}><X size={16} /></button>
                </div>
            ) : <>{m.attachment && <div className="mb-1"><AttachmentView m={m} /></div>}{m.content && <MessageText m={m} me={me} />}</>}
            <div className={`mt-0.5 flex items-center justify-end gap-1 text-[10px] ${mine ? 'text-white/70' : 'text-mute'}`}>
              {m.edited && !m.deleted && <span>edited</span>}<span>{timeOf(m.createdAt)}</span>
              {mine && !m.deleted && (read === 0 ? <Check size={12} aria-label="Sent" /> : <CheckCheck size={12} aria-label={read === 1 ? 'Delivered' : read === 2 ? 'Read by some' : 'Read'} className={read === 3 ? 'text-emerald-300' : read === 2 ? 'text-emerald-300/60' : ''} />)}
            </div>
          </div>
          {reactions.length > 0 && (
              <div className="-mt-1 flex flex-wrap gap-1">
                {reactions.map(([emoji, ids]) => (
                    <button key={emoji} onClick={() => call(() => api.post(`/api/messages/${m.id}/reactions`, { emoji }))} className={`rounded-full border px-1.5 text-xs ${ids.includes(me) ? 'border-violet-400 bg-violet-500/20' : 'border-line bg-panel'}`}>{emoji} {ids.length}</button>
                ))}
              </div>
          )}
          {!m.deleted && !editing && (
              <div className={`mt-0.5 flex items-center gap-0.5 rounded-xl border border-line bg-panel p-0.5 shadow ${sel ? 'flex' : 'hidden group-hover:flex group-focus-within:flex'}`}>
                {EMOJIS.map((e) => <button key={e} aria-label={`React ${e}`} className="rounded-lg px-1 text-sm hover:bg-panel2" onClick={() => { call(() => api.post(`/api/messages/${m.id}/reactions`, { emoji: e })); setSel(false); }}>{e}</button>)}
                <button aria-label="Reply" className={iconBtn} onClick={() => { setReplyTo({ id: m.id, senderName: m.senderName, content: (m.content || '').slice(0, 80) }); setSel(false); }}><Reply size={14} /></button>
                <button aria-label={isSaved ? 'Remove from saved' : 'Save message'} className={iconBtn} onClick={() => { toggleSave(m.id); setSel(false); }}>{isSaved ? <BookmarkCheck size={14} className="text-violet-400" /> : <Bookmark size={14} />}</button>
                <button aria-label="Forward" className={iconBtn} onClick={() => { setForwardMsg(m); setSel(false); }}><Forward size={14} /></button>
                <button aria-label="Delete for me" title="Delete for me" className={iconBtn} onClick={() => hideMessage(m.id, m.conversationId)}><EyeOff size={14} /></button>
                {mine && !(m.attachment && !m.content) && <button aria-label="Edit" className={iconBtn} onClick={() => { setDraft(m.content); setEditing(true); setSel(false); }}><Pencil size={14} /></button>}
                {ai.enabled && m.content && <button aria-label="Translate message" title="Translate with AI" className={iconBtn} onClick={() => { setTranslating(true); setSel(false); }}><Languages size={14} /></button>}
                {(mine || canModerate) && <button aria-label="Delete for everyone" title="Delete for everyone" className={`${iconBtn} hover:text-red-400`} onClick={() => setConfirmDel(true)}><Trash2 size={14} /></button>}
              </div>
          )}
        </div>
        {translating && <TranslateDialog m={m} onClose={() => setTranslating(false)} />}
        {confirmDel && <Confirm danger title="Delete message" confirmLabel="Delete" text="This message will be deleted for everyone in the conversation." onClose={() => setConfirmDel(false)} onConfirm={() => call(() => api.delete(`/messages/${m.id}`))} />}
      </div>
  );
}

function MessageSearch({ convId, onClose }) {
  const [q, setQ] = useState('');
  const [res, setRes] = useState(null);
  const toast = useStore((s) => s.toast);
  useEffect(() => {
    if (q.trim().length < 2) { setRes(null); return undefined; }
    const h = setTimeout(async () => {
      try { const { data } = await api.get(`/api/conversations/${convId}/messages/search`, { params: { q: q.trim() } }); setRes(data); } catch (e) { toast(errMsg(e), 'error'); }
    }, 300);
    return () => clearTimeout(h);
  }, [q, convId, toast]);
  return (
      <div className="border-b border-line bg-panel p-3 md:px-5">
        <div className="flex gap-2"><input autoFocus aria-label="Search in conversation" className={inputCls} placeholder="Search in this conversation…" value={q} onChange={(e) => setQ(e.target.value)} /><button className={iconBtn} aria-label="Close search" onClick={onClose}><X size={18} /></button></div>
        {res && <ul className="mt-2 max-h-48 overflow-y-auto text-sm">{res.length === 0 ? <li className="p-2 text-mute">No matches.</li> : res.map((m) => <li key={m.id} className="rounded-lg p-2 hover:bg-panel2"><span className="font-medium">{m.senderName}</span> <span className="text-xs text-mute">{dayOf(m.createdAt)} {timeOf(m.createdAt)}</span><div className="truncate text-mute">{m.content}</div></li>)}</ul>}
      </div>
  );
}