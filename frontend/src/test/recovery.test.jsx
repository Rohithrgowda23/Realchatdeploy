import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import api from '../api';
import ForgotPassword from '../ForgotPassword.jsx';
import ResetPassword from '../ResetPassword.jsx';
import JoinPage from '../JoinPage.jsx';
import { resetStore } from './helpers';

vi.mock('../api', () => ({
    default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
    errMsg: (e) => e?.response?.data?.message || e?.message || 'Something went wrong',
}));

describe('ForgotPassword', () => {
    it('shows the same generic confirmation without revealing whether the account exists', async () => {
        api.post.mockResolvedValue({ data: { message: 'x' } });
        render(<MemoryRouter><ForgotPassword /></MemoryRouter>);
        await userEvent.type(screen.getByLabelText('Email'), 'someone@example.com');
        await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }));
        expect(await screen.findByText(/If an account exists for that email/)).toBeInTheDocument();
        expect(api.post).toHaveBeenCalledWith('/auth/forgot-password', { email: 'someone@example.com' });
    });
    it('rejects an obviously invalid email locally', async () => {
        render(<MemoryRouter><ForgotPassword /></MemoryRouter>);
        await userEvent.type(screen.getByLabelText('Email'), 'nope');
        await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }));
        expect(await screen.findByText('Enter a valid email')).toBeInTheDocument();
        expect(api.post).not.toHaveBeenCalled();
    });
    it('shows rate-limit errors', async () => {
        api.post.mockRejectedValue({ response: { status: 429, data: { message: 'Too many requests. Please try again in 30 seconds.' } } });
        render(<MemoryRouter><ForgotPassword /></MemoryRouter>);
        await userEvent.type(screen.getByLabelText('Email'), 'a@example.com');
        await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }));
        expect(await screen.findByText(/Too many requests/)).toBeInTheDocument();
    });
});

describe('ResetPassword', () => {
    it('explains an invalid or expired link and offers a new one', async () => {
        window.history.replaceState({}, '', '/reset-password?token=abc');
        api.post.mockResolvedValue({ data: { valid: false } });
        render(<MemoryRouter><ResetPassword /></MemoryRouter>);
        expect(await screen.findByText(/invalid or has expired/)).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Request a new one' })).toBeInTheDocument();
    });
    it('removes the token from the address bar', async () => {
        window.history.replaceState({}, '', '/reset-password?token=abc');
        api.post.mockResolvedValue({ data: { valid: true } });
        render(<MemoryRouter><ResetPassword /></MemoryRouter>);
        await screen.findByLabelText('New password');
        expect(window.location.search).toBe('');
    });
    it('checks the passwords match, then sets the new password once', async () => {
        window.history.replaceState({}, '', '/reset-password?token=tok-123');
        api.post.mockImplementation((url) => Promise.resolve({ data: url.endsWith('validate') ? { valid: true } : { message: 'ok' } }));
        render(<MemoryRouter><ResetPassword /></MemoryRouter>);
        await userEvent.type(await screen.findByLabelText('New password'), 'newpassword1');
        await userEvent.type(screen.getByLabelText('Confirm password'), 'different-one');
        await userEvent.click(screen.getByRole('button', { name: 'Update password' }));
        expect(await screen.findByText('Passwords do not match')).toBeInTheDocument();
        await userEvent.clear(screen.getByLabelText('Confirm password'));
        await userEvent.type(screen.getByLabelText('Confirm password'), 'newpassword1');
        await userEvent.click(screen.getByRole('button', { name: 'Update password' }));
        expect(await screen.findByText(/Password updated/)).toBeInTheDocument();
        expect(api.post).toHaveBeenCalledWith('/auth/reset-password', { token: 'tok-123', newPassword: 'newpassword1' });
    });
});

describe('JoinPage (invite link)', () => {
    beforeEach(() => { resetStore(); });
    const preview = (over = {}) => ({ data: { groupName: 'Team', description: 'Our team', memberCount: 3, requiresApproval: false, alreadyMember: false, requestPending: false, ...over } });

    it('reports an invalid link without exposing details', async () => {
        window.location.hash = '#bad-token';
        api.post.mockRejectedValue({ response: { status: 404 } });
        render(<MemoryRouter><JoinPage /></MemoryRouter>);
        expect(await screen.findByText('Invite not available')).toBeInTheDocument();
    });
    it('sends the token in the request body, never in a URL', async () => {
        window.location.hash = '#tok-body';
        api.post.mockImplementation((url) => Promise.resolve(url === '/invites/preview' ? preview({ requiresApproval: true }) : { data: { status: 'PENDING', conversationId: 9 } }));
        render(<MemoryRouter><JoinPage /></MemoryRouter>);
        await userEvent.click(await screen.findByRole('button', { name: 'Request to join' }));
        expect(await screen.findByText(/Request sent/)).toBeInTheDocument();
        expect(api.post).toHaveBeenCalledWith('/invites/join', { token: 'tok-body' });
        expect(api.post.mock.calls.every(([url]) => !url.includes('tok-body'))).toBe(true);
    });
    it('joins directly when no approval is needed', async () => {
        window.location.hash = '#tok-direct';
        api.get.mockResolvedValue({ data: [] });
        api.post.mockImplementation((url) => Promise.resolve(url === '/invites/preview' ? preview() : { data: { status: 'JOINED', conversationId: 5 } }));
        render(<MemoryRouter><JoinPage /></MemoryRouter>);
        await userEvent.click(await screen.findByRole('button', { name: 'Join group' }));
        await vi.waitFor(() => expect(api.post).toHaveBeenCalledWith('/invites/join', { token: 'tok-direct' }));
    });
    it('tells existing members they are already in the group', async () => {
        window.location.hash = '#tok-member';
        api.post.mockResolvedValue(preview({ alreadyMember: true }));
        render(<MemoryRouter><JoinPage /></MemoryRouter>);
        expect(await screen.findByText(/already in this group/)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Join group' })).not.toBeInTheDocument();
    });
});