import { create } from 'zustand';
import api, { errMsg } from './api';
import { sendWs } from './ws';

function tokenValid(t) {
  try {
    const p = JSON.parse(atob(t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return p.exp * 1000 > Date.now();
  } catch { return false; }
}
const startToken = (() => {
  const t = sessionStorage.getItem('token');
  if (t && !tokenValid(t)) { sessionStorage.clear(); return null; }
  return t;
})();

const previewOf = (m) => (m.content ? m.content.slice(0, 80) : m.attachment ? (m.attachment.kind === 'VOICE' ? 'Voice message' : m.attachment.kind === 'IMAGE' ? 'Photo' : `Attachment: ${m.attachment.fileName}`) : '');
const activity = (c) => new Date(c.lastMessage?.createdAt || c.createdAt).getTime();
const sortConvs = (l) => [...l].sort((a, b) => (a.pinned !== b.pinned ? (a.pinned ? -1 : 1) : activity(b) - activity(a)));
let toastSeq = 0;

export const useStore = create((set, get) => ({
  token: startToken,
  user: startToken ? JSON.parse(sessionStorage.getItem('user') || 'null') : null,
  theme: localStorage.getItem('theme') || 'dark',
  conversations: [], convLoading: true, activeId: null,
  saved: [], forwardMsg: null, joinTick: 0, ai: { enabled: false, provider: 'none', model: '', languages: [] }, notifUnread: 0, storyUnviewed: 0, missedCalls: 0, wsOnline: true, notifPrefs: { mentions: true, joinRequests: true, scheduled: true, calls: true }, messages: {}, hasMore: {}, msgLoading: false, details: {}, typing: {}, online: [], toasts: [], replyTo: null,

  toast: (text, kind = 'info') => {
    const id = ++toastSeq;
    set((s) => ({ toasts: [...s.toasts, { id, text, kind }] }));
    setTimeout(() => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })), 4000);
  },
  setAuth: (token, user) => { sessionStorage.setItem('token', token); sessionStorage.setItem('user', JSON.stringify(user)); set({ token, user }); },
  setUser: (user) => { sessionStorage.setItem('user', JSON.stringify(user)); set({ user }); },
  logout: () => {
    const t = sessionStorage.getItem('token');
    if (t) api.delete('/api/sessions/current', { headers: { Authorization: `Bearer ${t}` } }).catch(() => {}); // revoke server-side
    sessionStorage.clear(); set({ token: null, user: null, conversations: [], messages: {}, details: {}, activeId: null, convLoading: true }); },
  setTheme: (theme) => { localStorage.setItem('theme', theme); document.documentElement.dataset.theme = theme; set({ theme }); },
  setReplyTo: (replyTo) => set({ replyTo }),
  setPresence: (userId, on) => set((s) => ({ online: on ? [...new Set([...s.online, userId])] : s.online.filter((x) => x !== userId) })),
  setOnlineList: (online) => set({ online }),

  loadConversations: async () => {
    try {
      const { data } = await api.get('/api/conversations');
      set((s) => ({ conversations: data, convLoading: false, activeId: data.some((c) => c.id === s.activeId) ? s.activeId : null }));
    } catch (e) { set({ convLoading: false }); get().toast(errMsg(e), 'error'); }
  },
  loadDetail: async (id) => {
    try { const { data } = await api.get(`/api/conversations/${id}`); set((s) => ({ details: { ...s.details, [id]: data } })); } catch { /* removed from group */ }
  },
  open: async (id) => {
    set({ activeId: id, replyTo: null });
    if (id == null) return;
    set({ msgLoading: true });
    try {
      const { data } = await api.get(`/api/conversations/${id}/messages`, { params: { size: 30 } });
      set((s) => ({ messages: { ...s.messages, [id]: data }, hasMore: { ...s.hasMore, [id]: data.length === 30 } }));
    } catch (e) { get().toast(errMsg(e), 'error'); }
    set({ msgLoading: false });
    get().loadDetail(id);
    get().markRead(id);
  },
  loadOlder: async (id) => {
    const cur = get().messages[id] || [];
    if (!cur.length) return;
    try {
      const { data } = await api.get(`/api/conversations/${id}/messages`, { params: { before: cur[0].id, size: 30 } });
      set((s) => ({ messages: { ...s.messages, [id]: [...data, ...(s.messages[id] || [])] }, hasMore: { ...s.hasMore, [id]: data.length === 30 } }));
    } catch (e) { get().toast(errMsg(e), 'error'); }
  },
  markRead: (id) => {
    const last = (get().messages[id] || []).slice(-1)[0];
    set((s) => ({ conversations: s.conversations.map((c) => (c.id === id ? { ...c, unread: 0, unreadMentions: 0 } : c)) }));
    if (last) api.post(`/api/conversations/${id}/read`, { messageId: last.id }).catch(() => {});
  },
  addMessage: (m) => set((s) => {
    const list = s.messages[m.conversationId];
    if (!list || list.some((x) => x.id === m.id)) return {};
    return { messages: { ...s.messages, [m.conversationId]: [...list, m] } };
  }),
  send: async (content, replyToId, mentions) => {
    const id = get().activeId;
    if (sendWs('/app/chat.send', { conversationId: id, content, replyToId: replyToId || null, mentions: mentions?.length ? mentions : null })) return true;
    try { // WebSocket is down: persist through REST so the message is not lost
      const { data } = await api.post(`/api/conversations/${id}/messages`, { content, replyToId: replyToId || null, mentionIds: mentions?.length ? mentions : null });
      get().addMessage(data);
      return true;
    } catch (e) { get().toast(errMsg(e), 'error'); return false; }
  },
  sendTyping: (typing) => { const id = get().activeId; if (id) sendWs('/app/chat.typing', { conversationId: id, typing }); },
  patchConversation: (c) => set((s) => ({ conversations: sortConvs(s.conversations.map((x) => (x.id === c.id ? c : x))) })),

  setForwardMsg: (forwardMsg) => set({ forwardMsg }),
  setNotifPrefs: (notifPrefs) => set({ notifPrefs }),
  setWsOnline: (wsOnline) => set({ wsOnline }),
  loadMissedCount: async () => { try { const { data } = await api.get('/api/calls/missed-count'); set({ missedCalls: data.count }); } catch { /* non-fatal */ } },
  loadStoryCount: async () => { try { const { data } = await api.get('/api/stories/feed'); set({ storyUnviewed: data.filter((g) => g.hasUnviewed).length }); } catch { /* non-fatal */ } },
  loadAiStatus: async () => { try { const { data } = await api.get('/api/ai/status'); set({ ai: data }); } catch { /* AI is optional */ } },
  loadNotifMeta: async () => {
    try {
      const [c, p] = await Promise.all([api.get('/api/notifications/unread-count'), api.get('/api/notifications/preferences')]);
      set({ notifUnread: c.data.count, notifPrefs: p.data });
    } catch { /* non-fatal */ }
  },
  loadSavedIds: async () => { try { const { data } = await api.get('/api/saved/ids'); set({ saved: data }); } catch { /* non-fatal */ } },
  toggleSave: async (id) => {
    const isSaved = get().saved.includes(id);
    try {
      if (isSaved) await api.delete(`/api/saved/${id}`); else await api.post(`/api/saved/${id}`);
      set((s) => ({ saved: isSaved ? s.saved.filter((x) => x !== id) : [...s.saved, id] }));
      get().toast(isSaved ? 'Removed from saved' : 'Saved');
    } catch (e) { get().toast(errMsg(e), 'error'); }
  },
  hideMessage: async (id, convId) => {
    try {
      await api.post(`/messages/${id}/hide`);
      set((s) => ({ messages: { ...s.messages, [convId]: (s.messages[convId] || []).filter((x) => x.id !== id) } }));
      get().toast('Deleted for you');
    } catch (e) { get().toast(errMsg(e), 'error'); }
  },
  jumpTo: async (convId, msgId) => {
    if (get().activeId !== convId) await get().open(convId);
    for (let i = 0; i < 15 && !(get().messages[convId] || []).some((m) => m.id === msgId) && get().hasMore[convId]; i++) await get().loadOlder(convId);
    setTimeout(() => {
      const el = document.getElementById(`msg-${msgId}`);
      if (!el) { get().toast('That message is no longer available', 'error'); return; }
      el.scrollIntoView({ block: 'center' });
      el.classList.add('ring-2', 'ring-violet-400');
      setTimeout(() => el.classList.remove('ring-2', 'ring-violet-400'), 1800);
    }, 150);
  },

  handleEvent: (ev) => {
    const s = get();
    const me = s.user?.id;
    switch (ev.type) {
      case 'MESSAGE': {
        const m = ev.data;
        s.addMessage(m);
        const known = s.conversations.find((c) => c.id === m.conversationId);
        if (!known) { s.loadConversations(); break; }
        const active = s.activeId === m.conversationId;
        const mine = m.senderId === me;
        set((st) => ({
          conversations: sortConvs(st.conversations.map((c) => c.id === m.conversationId
              ? { ...c, lastMessage: { id: m.id, senderId: m.senderId, preview: previewOf(m), createdAt: m.createdAt }, unread: active || mine ? 0 : c.unread + 1, unreadMentions: active || mine ? 0 : (c.unreadMentions || 0) + (m.mentions?.some((x) => x.userId === me) ? 1 : 0) }
              : c)),
        }));
        if (active && !mine) s.markRead(m.conversationId);
        if (!active && !mine && !known.muted) s.toast(s.notifPrefs.mentions && m.mentions?.some((x) => x.userId === me) ? `${m.senderName} mentioned you: ${previewOf(m).slice(0, 50)}` : `${m.senderName}: ${previewOf(m).slice(0, 60)}`, 'message');
        break;
      }
      case 'MESSAGE_UPDATED': {
        const m = ev.data;
        set((st) => ({ messages: { ...st.messages, [m.conversationId]: (st.messages[m.conversationId] || []).map((x) => (x.id === m.id ? m : x)) } }));
        break;
      }
      case 'READ': {
        const { conversationId: cid, userId, lastReadMessageId } = ev.data;
        set((st) => {
          const d = st.details[cid];
          if (!d) return {};
          return { details: { ...st.details, [cid]: { ...d, members: d.members.map((x) => (x.userId === userId ? { ...x, lastReadMessageId } : x)) } } };
        });
        break;
      }
      case 'TYPING': {
        const { conversationId: cid, userId, name, typing } = ev.data;
        if (userId === me) break;
        const upd = (on) => set((st) => {
          const cur = { ...(st.typing[cid] || {}) };
          if (on) cur[userId] = name; else delete cur[userId];
          return { typing: { ...st.typing, [cid]: cur } };
        });
        upd(typing);
        if (typing) setTimeout(() => upd(false), 4000);
        break;
      }
      case 'CONVERSATION_UPDATED': {
        const cid = ev.data.conversationId;
        s.loadConversations();
        if (s.details[cid] || s.activeId === cid) s.loadDetail(cid);
        break;
      }
      case 'NOTIFICATION':
        set((st) => ({ notifUnread: st.notifUnread + 1 }));
        if (ev.data.type === 'SCHEDULED_FAILED' || ev.data.type === 'MISSED_CALL') s.toast(`${ev.data.title}${ev.data.body ? `: ${ev.data.body}` : ''}`, 'error');
        break;
      case 'DELIVERED': {
        const { conversationId: cid, userIds, lastDeliveredMessageId } = ev.data;
        set((st) => {
          const d = st.details[cid];
          if (!d) return {};
          return { details: { ...st.details, [cid]: { ...d, members: d.members.map((x) => (userIds.includes(x.userId) ? { ...x, lastDeliveredMessageId: Math.max(x.lastDeliveredMessageId || 0, lastDeliveredMessageId) } : x)) } } };
        });
        break;
      }
      case 'JOIN_REQUEST':
        s.toast(`${ev.data.userName} wants to join ${ev.data.groupName}`, 'message');
        set((st) => ({ joinTick: st.joinTick + 1 }));
        break;
      case 'JOIN_DECISION':
        s.toast(ev.data.approved ? `You were added to ${ev.data.groupName}` : `Your request to join ${ev.data.groupName} was declined`, ev.data.approved ? 'info' : 'error');
        if (ev.data.approved) s.loadConversations();
        break;
      default: break;
    }
  },
}));