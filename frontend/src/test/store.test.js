import { vi } from 'vitest';
import api from '../api';
import { sendWs } from '../ws';
import { useStore } from '../store';
import { resetStore, toastTexts } from './helpers';

vi.mock('../api', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }, errMsg: (e) => e?.response?.data?.message || e?.message || 'error' }));
vi.mock('../ws', () => ({ sendWs: vi.fn() }));

const conv = (over = {}) => ({ id: 10, title: 'Bob', muted: false, unread: 0, unreadMentions: 0, pinned: false, createdAt: '2026-01-01T00:00:00Z', lastMessage: null, ...over });
const msg = (over = {}) => ({ id: 5, conversationId: 10, senderId: 2, senderName: 'Bob', content: 'hello', createdAt: '2026-10-01T10:00:00Z', reactions: {}, mentions: [], ...over });

describe('incoming events', () => {
    beforeEach(() => { api.post.mockResolvedValue({ data: {} }); api.get.mockResolvedValue({ data: [] }); resetStore({ user: { id: 1 }, conversations: [conv()] }); });

    it('a message in another chat raises unread, updates the preview and shows a toast', () => {
        useStore.getState().handleEvent({ type: 'MESSAGE', data: msg() });
        const c = useStore.getState().conversations[0];
        expect(c.unread).toBe(1);
        expect(c.lastMessage.preview).toBe('hello');
        expect(toastTexts()).toContain('Bob: hello');
    });
    it('a muted chat stays quiet but still counts unread', () => {
        resetStore({ user: { id: 1 }, conversations: [conv({ muted: true })] });
        useStore.getState().handleEvent({ type: 'MESSAGE', data: msg() });
        expect(toastTexts()).toHaveLength(0);
        expect(useStore.getState().conversations[0].unread).toBe(1);
    });
    it('a mention of me is counted and announced', () => {
        useStore.getState().handleEvent({ type: 'MESSAGE', data: msg({ mentions: [{ userId: 1, name: 'Me' }] }) });
        expect(useStore.getState().conversations[0].unreadMentions).toBe(1);
        expect(toastTexts()[0]).toContain('mentioned you');
    });
    it('a message in the open chat is appended, not counted unread, and marked read', () => {
        resetStore({ user: { id: 1 }, conversations: [conv()], activeId: 10, messages: { 10: [] } });
        useStore.getState().handleEvent({ type: 'MESSAGE', data: msg() });
        expect(useStore.getState().messages[10]).toHaveLength(1);
        expect(useStore.getState().conversations[0].unread).toBe(0);
        expect(api.post).toHaveBeenCalledWith('/conversations/10/read', { messageId: 5 });
    });
    it('the same message delivered twice is only shown once', () => {
        resetStore({ user: { id: 1 }, conversations: [conv()], activeId: 10, messages: { 10: [] } });
        useStore.getState().handleEvent({ type: 'MESSAGE', data: msg() });
        useStore.getState().handleEvent({ type: 'MESSAGE', data: msg() });
        expect(useStore.getState().messages[10]).toHaveLength(1);
    });
    it('an edited or deleted message replaces the old copy', () => {
        resetStore({ user: { id: 1 }, conversations: [conv()], messages: { 10: [msg()] } });
        useStore.getState().handleEvent({ type: 'MESSAGE_UPDATED', data: msg({ content: null, deleted: true }) });
        expect(useStore.getState().messages[10][0].deleted).toBe(true);
        expect(useStore.getState().messages[10][0].content).toBeNull();
    });
    it('delivered receipts only ever move forward', () => {
        resetStore({ user: { id: 1 }, details: { 10: { members: [{ userId: 2, lastDeliveredMessageId: 0, lastReadMessageId: 0 }] } } });
        const ev = (n) => ({ type: 'DELIVERED', data: { conversationId: 10, userIds: [2], lastDeliveredMessageId: n } });
        useStore.getState().handleEvent(ev(9));
        useStore.getState().handleEvent(ev(5));
        expect(useStore.getState().details[10].members[0].lastDeliveredMessageId).toBe(9);
    });
    it('read receipts update the member', () => {
        resetStore({ user: { id: 1 }, details: { 10: { members: [{ userId: 2, lastReadMessageId: 0 }] } } });
        useStore.getState().handleEvent({ type: 'READ', data: { conversationId: 10, userId: 2, lastReadMessageId: 7 } });
        expect(useStore.getState().details[10].members[0].lastReadMessageId).toBe(7);
    });
    it('notifications raise the unread badge, and failures of scheduled messages are surfaced', () => {
        useStore.setState({ notifUnread: 0 });
        useStore.getState().handleEvent({ type: 'NOTIFICATION', data: { type: 'MENTION', title: 'x' } });
        useStore.getState().handleEvent({ type: 'NOTIFICATION', data: { type: 'SCHEDULED_FAILED', title: 'Not sent', body: 'Conversation not found' } });
        expect(useStore.getState().notifUnread).toBe(2);
        expect(toastTexts().some((t) => t.includes('Not sent'))).toBe(true);
    });
    it('typing indicators from other people appear and my own are ignored', () => {
        useStore.getState().handleEvent({ type: 'TYPING', data: { conversationId: 10, userId: 2, name: 'Bob', typing: true } });
        useStore.getState().handleEvent({ type: 'TYPING', data: { conversationId: 10, userId: 1, name: 'Me', typing: true } });
        expect(useStore.getState().typing[10]).toEqual({ 2: 'Bob' });
    });
});

describe('sending', () => {
    beforeEach(() => { resetStore({ user: { id: 1 }, activeId: 10, messages: { 10: [] } }); });

    it('sends over the WebSocket, including structured mentions', async () => {
        sendWs.mockReturnValue(true);
        expect(await useStore.getState().send('hi @Bob', null, [2])).toBe(true);
        expect(sendWs).toHaveBeenCalledWith('/app/chat.send', { conversationId: 10, content: 'hi @Bob', replyToId: null, mentions: [2] });
        expect(api.post).not.toHaveBeenCalled();
    });
    it('falls back to REST when the socket is down so the message is not lost', async () => {
        sendWs.mockReturnValue(false);
        api.post.mockResolvedValue({ data: msg({ id: 77, content: 'offline hello' }) });
        expect(await useStore.getState().send('offline hello', null, [])).toBe(true);
        expect(api.post).toHaveBeenCalledWith('/conversations/10/messages', { content: 'offline hello', replyToId: null, mentionIds: null });
        expect(useStore.getState().messages[10].map((m) => m.id)).toEqual([77]);
    });
    it('reports a rejected message and returns false so the text can be restored', async () => {
        sendWs.mockReturnValue(false);
        api.post.mockRejectedValue({ response: { data: { message: 'Only admins can post in this group' } } });
        expect(await useStore.getState().send('hello', null, [])).toBe(false);
        expect(toastTexts()).toContain('Only admins can post in this group');
    });
});

describe('saved messages and hiding', () => {
    beforeEach(() => { resetStore({ user: { id: 1 }, messages: { 10: [msg(), msg({ id: 6 })] } }); });
    it('toggles saved state on success only', async () => {
        api.post.mockResolvedValue({}); api.delete.mockResolvedValue({});
        await useStore.getState().toggleSave(5);
        expect(useStore.getState().saved).toContain(5);
        await useStore.getState().toggleSave(5);
        expect(useStore.getState().saved).not.toContain(5);
        api.post.mockRejectedValue({ response: { data: { message: 'nope' } } });
        await useStore.getState().toggleSave(6);
        expect(useStore.getState().saved).not.toContain(6);
    });
    it('delete-for-me removes the message from the local list', async () => {
        api.post.mockResolvedValue({});
        await useStore.getState().hideMessage(5, 10);
        expect(useStore.getState().messages[10].map((m) => m.id)).toEqual([6]);
    });
});

describe('session handling', () => {
    it('logging out revokes the session on the server and clears local state', () => {
        api.delete.mockResolvedValue({});
        sessionStorage.setItem('token', 'jwt-x');
        useStore.setState({ token: 'jwt-x', user: { id: 1 } });
        useStore.getState().logout();
        expect(api.delete).toHaveBeenCalledWith('/sessions/current', { headers: { Authorization: 'Bearer jwt-x' } });
        expect(sessionStorage.getItem('token')).toBeNull();
        expect(useStore.getState().token).toBeNull();
    });
});