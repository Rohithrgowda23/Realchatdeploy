import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import api from '../api';
import { ScheduleDialog, ScheduledDialog } from '../scheduled.jsx';
import { NotificationsDialog } from '../notifications.jsx';
import { InviteSection, JoinRequestsSection } from '../invites.jsx';
import { Confirm, Modal, UserPicker } from '../components.jsx';
import { useStore } from '../store';
import { resetStore, toastTexts } from './helpers';

vi.mock('../api', () => ({
    default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() },
    errMsg: (e) => e?.response?.data?.message || e?.message || 'error',
}));

const pad = (n) => String(n).padStart(2, '0');
const local = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;

describe('ScheduleDialog', () => {
    beforeEach(() => resetStore({ user: { id: 1 } }));

    it('refuses a time in the past and sends nothing (the browser blocks the form)', async () => {
        render(<ScheduleDialog convId={3} content="hello" onClose={vi.fn()} onDone={vi.fn()} />);
        const input = screen.getByLabelText('Send at');
        fireEvent.change(input, { target: { value: '2020-01-01T10:00' } });
        expect(input.validity.rangeUnderflow).toBe(true); // the picker's min attribute is "one minute from now"
        await userEvent.click(screen.getByRole('button', { name: 'Schedule' }));
        expect(api.post).not.toHaveBeenCalled();
    });
    it('also checks the time in code when the browser does not enforce min', async () => {
        const { container } = render(<ScheduleDialog convId={3} content="hello" onClose={vi.fn()} onDone={vi.fn()} />);
        fireEvent.change(screen.getByLabelText('Send at'), { target: { value: '2020-01-01T10:00' } });
        fireEvent.submit(container.querySelector('form')); // bypasses native validation, as an old browser would
        expect(toastTexts().some((t) => t.includes('at least a minute'))).toBe(true);
        expect(api.post).not.toHaveBeenCalled();
    });
    it('posts an exact UTC instant converted from the chosen local time', async () => {
        api.post.mockResolvedValue({ data: {} });
        const onClose = vi.fn(); const onDone = vi.fn();
        render(<ScheduleDialog convId={3} content="hello" onClose={onClose} onDone={onDone} />);
        const when = local(new Date(Date.now() + 3 * 3600e3));
        fireEvent.change(screen.getByLabelText('Send at'), { target: { value: when } });
        await userEvent.click(screen.getByRole('button', { name: 'Schedule' }));
        await waitFor(() => expect(api.post).toHaveBeenCalled());
        const [url, body] = api.post.mock.calls[0];
        expect(url).toBe('/conversations/3/scheduled');
        expect(body.content).toBe('hello');
        expect(body.scheduledAt).toBe(new Date(when).toISOString());
        expect(body.scheduledAt.endsWith('Z')).toBe(true);
        expect(onDone).toHaveBeenCalled(); expect(onClose).toHaveBeenCalled();
    });
    it('shows the time zone being used', () => {
        render(<ScheduleDialog convId={3} content="x" onClose={vi.fn()} />);
        expect(screen.getByText(/Time zone:/)).toBeInTheDocument();
    });
    it('keeps the dialog open and explains a server refusal', async () => {
        api.post.mockRejectedValue({ response: { data: { message: 'Only admins can post in this group' } } });
        const onClose = vi.fn();
        render(<ScheduleDialog convId={3} content="hello" onClose={onClose} />);
        fireEvent.change(screen.getByLabelText('Send at'), { target: { value: local(new Date(Date.now() + 3 * 3600e3)) } });
        await userEvent.click(screen.getByRole('button', { name: 'Schedule' }));
        await waitFor(() => expect(toastTexts()).toContain('Only admins can post in this group'));
        expect(onClose).not.toHaveBeenCalled();
    });
});

describe('ScheduledDialog', () => {
    beforeEach(() => resetStore({ user: { id: 1 } }));
    const rows = [
        { id: 5, conversationTitle: 'Bob', content: 'pending one', scheduledAt: new Date(Date.now() + 3600e3).toISOString(), status: 'PENDING' },
        { id: 6, conversationTitle: 'Team', content: 'failed one', scheduledAt: new Date(Date.now() - 3600e3).toISOString(), status: 'FAILED', lastError: 'Conversation not found' },
    ];

    it('lists upcoming messages and keeps failures in History with the reason', async () => {
        api.get.mockResolvedValue({ data: rows });
        render(<ScheduledDialog onClose={vi.fn()} />);
        expect(await screen.findByText('pending one')).toBeInTheDocument();
        expect(screen.queryByText('failed one')).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('tab', { name: 'History' }));
        expect(screen.getByText('failed one')).toBeInTheDocument();
        expect(screen.getByText(/Conversation not found/)).toBeInTheDocument();
    });
    it('cancels after confirmation', async () => {
        api.get.mockResolvedValue({ data: rows });
        api.delete.mockResolvedValue({});
        render(<ScheduledDialog onClose={vi.fn()} />);
        await screen.findByText('pending one');
        await userEvent.click(screen.getByRole('button', { name: 'Cancel scheduled message' }));
        await userEvent.click(screen.getByRole('button', { name: 'Cancel message' }));
        await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/scheduled/5'));
    });
});

describe('NotificationsDialog', () => {
    const items = [{ id: 1, type: 'MENTION', title: 'Ann mentioned you', body: 'hi', conversationId: 3, messageId: 9, read: false, createdAt: new Date().toISOString() }];
    beforeEach(() => {
        resetStore({ user: { id: 1 }, notifPrefs: { mentions: true, joinRequests: true, scheduled: true, calls: true } });
        api.get.mockImplementation((url) => Promise.resolve({ data: url === '/notifications' ? items : url.endsWith('unread-count') ? { count: 1 } : { mentions: true, joinRequests: true, scheduled: true, calls: true } }));
        api.post.mockResolvedValue({}); api.delete.mockResolvedValue({}); api.put.mockResolvedValue({});
    });

    it('shows notifications and marks everything read', async () => {
        render(<NotificationsDialog onClose={vi.fn()} />);
        expect(await screen.findByText('Ann mentioned you')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Mark all read' }));
        await waitFor(() => expect(api.post).toHaveBeenCalledWith('/notifications/read'));
    });
    it('dismisses one notification', async () => {
        render(<NotificationsDialog onClose={vi.fn()} />);
        await screen.findByText('Ann mentioned you');
        await userEvent.click(screen.getByRole('button', { name: 'Dismiss notification' }));
        await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/notifications/1'));
        expect(screen.queryByText('Ann mentioned you')).not.toBeInTheDocument();
    });
    it('clears all only after confirmation', async () => {
        render(<NotificationsDialog onClose={vi.fn()} />);
        await screen.findByText('Ann mentioned you');
        await userEvent.click(screen.getByRole('button', { name: 'Clear all' }));
        expect(api.delete).not.toHaveBeenCalled();
        await userEvent.click(screen.getAllByRole('button', { name: 'Clear all' }).pop());
        await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/notifications'));
    });
    it('saves preference changes including missed calls', async () => {
        render(<NotificationsDialog onClose={vi.fn()} />);
        await userEvent.click(await screen.findByRole('checkbox', { name: 'Missed calls' }));
        await waitFor(() => expect(api.put).toHaveBeenCalledWith('/notifications/preferences', expect.objectContaining({ calls: false, mentions: true })));
    });
    it('clicking a mention opens it and marks it read', async () => {
        const jumpTo = vi.fn(); const onClose = vi.fn();
        useStore.setState({ jumpTo });
        render(<NotificationsDialog onClose={onClose} />);
        await userEvent.click(await screen.findByText('Ann mentioned you'));
        await waitFor(() => expect(jumpTo).toHaveBeenCalledWith(3, 9));
        expect(api.post).toHaveBeenCalledWith('/notifications/read', { ids: [1] });
        expect(onClose).toHaveBeenCalled();
    });
});

describe('invite links and join requests (admin side)', () => {
    beforeEach(() => resetStore({ user: { id: 1 }, joinTick: 0 }));

    it('creates a link whose token sits in the URL fragment and is shown only once', async () => {
        api.get.mockResolvedValue({ data: [] });
        api.post.mockResolvedValue({ data: { id: 4, token: 'abc123tokenXYZ' } });
        render(<InviteSection id={9} />);
        await userEvent.click(screen.getByRole('button', { name: /Invite links/ }));
        await userEvent.click(await screen.findByRole('button', { name: 'Create invite link' }));
        const field = await screen.findByLabelText('Invite link');
        expect(field.value).toBe(`${window.location.origin}/join#abc123tokenXYZ`);
        expect(screen.getByText(/cannot be shown again/)).toBeInTheDocument();
        expect(api.post).toHaveBeenCalledWith('/conversations/9/invites', { expiresInHours: 24, maxUses: null, requiresApproval: false });
    });
    it('revokes only after confirmation', async () => {
        api.get.mockResolvedValue({ data: [{ id: 4, createdBy: 'Ann', status: 'ACTIVE', useCount: 1, maxUses: 5, expiresAt: null, requiresApproval: false }] });
        api.delete.mockResolvedValue({});
        render(<InviteSection id={9} />);
        await userEvent.click(screen.getByRole('button', { name: /Invite links/ }));
        await userEvent.click(await screen.findByRole('button', { name: 'Revoke' }));
        expect(api.delete).not.toHaveBeenCalled();
        await userEvent.click(screen.getAllByRole('button', { name: 'Revoke' }).pop());
        await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/conversations/9/invites/4'));
    });
    it('lets an admin approve or decline a join request', async () => {
        api.get.mockResolvedValue({ data: [{ id: 11, userId: 7, userName: 'Zed', createdAt: new Date().toISOString() }] });
        api.post.mockResolvedValue({});
        render(<JoinRequestsSection id={9} />);
        await userEvent.click(await screen.findByRole('button', { name: 'Approve Zed' }));
        await waitFor(() => expect(api.post).toHaveBeenCalledWith('/conversations/9/join-requests/11/approve'));
        await userEvent.click(await screen.findByRole('button', { name: 'Decline Zed' }));
        await waitFor(() => expect(api.post).toHaveBeenCalledWith('/conversations/9/join-requests/11/reject'));
    });
    it('shows nothing when there are no pending requests', async () => {
        api.get.mockResolvedValue({ data: [] });
        const { container } = render(<JoinRequestsSection id={9} />);
        await waitFor(() => expect(api.get).toHaveBeenCalled());
        expect(container).toBeEmptyDOMElement();
    });
});

describe('shared dialog pieces', () => {
    it('Confirm runs the action and then closes', async () => {
        const order = [];
        render(<Confirm title="Delete" text="Sure?" confirmLabel="Yes, delete" onConfirm={() => order.push('confirm')} onClose={() => order.push('close')} />);
        await userEvent.click(screen.getByRole('button', { name: 'Yes, delete' }));
        await waitFor(() => expect(order).toEqual(['confirm', 'close']));
    });
    it('Cancel closes without running the action', async () => {
        const onConfirm = vi.fn(); const onClose = vi.fn();
        render(<Confirm title="Delete" text="Sure?" onConfirm={onConfirm} onClose={onClose} />);
        await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
        expect(onConfirm).not.toHaveBeenCalled(); expect(onClose).toHaveBeenCalled();
    });
    it('Modal is an accessible dialog that closes on Escape', async () => {
        const onClose = vi.fn();
        render(<Modal title="Settings" onClose={onClose}><p>body</p></Modal>);
        expect(screen.getByRole('dialog', { name: 'Settings' })).toHaveAttribute('aria-modal', 'true');
        await userEvent.keyboard('{Escape}');
        expect(onClose).toHaveBeenCalled();
    });
    it('UserPicker waits for 2 characters, then searches and picks', async () => {
        api.get.mockResolvedValue({ data: [{ id: 2, name: 'Bob', about: 'hi' }] });
        const onPick = vi.fn();
        render(<UserPicker onPick={onPick} />);
        expect(screen.getByText('Type at least 2 characters.')).toBeInTheDocument();
        await userEvent.type(screen.getByLabelText('Search people'), 'b');
        expect(api.get).not.toHaveBeenCalled();
        await userEvent.type(screen.getByLabelText('Search people'), 'o');
        await userEvent.click(await screen.findByRole('button', { name: /Bob/ }, { timeout: 2000 }));
        expect(api.get).toHaveBeenCalledWith('/users/search', { params: { q: 'bo' } });
        expect(onPick).toHaveBeenCalledWith(expect.objectContaining({ id: 2 }));
    });
});