import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import api from '../api';
import { Login, Register } from '../Auth.jsx';
import { useStore } from '../store';
import { consumeReturnTo } from '../returnTo.js';
import { resetStore } from './helpers';

vi.mock('../api', () => ({
    default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() },
    errMsg: (e) => e?.response?.data?.message || e?.message || 'Something went wrong',
}));

const renderLogin = () => render(<MemoryRouter><Login /></MemoryRouter>);

describe('Login', () => {
    beforeEach(() => { resetStore(); api.get.mockResolvedValue({ data: { google: false } }); });

    it('validates the form before calling the API', async () => {
        renderLogin();
        await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
        expect(await screen.findByText('Enter a valid email')).toBeInTheDocument();
        expect(screen.getByText('Password is required')).toBeInTheDocument();
        expect(api.post).not.toHaveBeenCalled();
    });

    it('signs in, stores the session and does not keep the password', async () => {
        api.post.mockResolvedValue({ data: { token: 'jwt-1', user: { id: 1, name: 'Ann' } } });
        renderLogin();
        await userEvent.type(screen.getByLabelText('Email'), 'ann@example.com');
        await userEvent.type(screen.getByLabelText('Password'), 'secret-pass');
        await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
        await waitFor(() => expect(useStore.getState().token).toBe('jwt-1'));
        expect(api.post).toHaveBeenCalledWith('/auth/login', { email: 'ann@example.com', password: 'secret-pass' });
        expect(sessionStorage.getItem('token')).toBe('jwt-1');
        expect(JSON.stringify(sessionStorage)).not.toContain('secret-pass');
    });

    it('shows the server error and stays signed out', async () => {
        api.post.mockRejectedValue({ response: { status: 401, data: { message: 'Invalid email or password' } } });
        renderLogin();
        await userEvent.type(screen.getByLabelText('Email'), 'ann@example.com');
        await userEvent.type(screen.getByLabelText('Password'), 'wrong');
        await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
        expect(await screen.findByText('Invalid email or password')).toBeInTheDocument();
        expect(useStore.getState().token).toBeNull();
    });

    it('links to password recovery and registration', () => {
        renderLogin();
        expect(screen.getByRole('link', { name: 'Forgot password?' })).toHaveAttribute('href', '/forgot-password');
        expect(screen.getByRole('link', { name: 'Create an account' })).toHaveAttribute('href', '/register');
    });

    it('shows the Google button only when the server has Google configured', async () => {
        renderLogin();
        await waitFor(() => expect(api.get).toHaveBeenCalledWith('/api/auth/oauth/providers'));
        expect(screen.queryByRole('button', { name: /Continue with Google/ })).not.toBeInTheDocument();
    });

    it('offers Google sign-in when enabled', async () => {
        api.get.mockResolvedValue({ data: { google: true } });
        renderLogin();
        expect(await screen.findByRole('button', { name: /Continue with Google/ })).toBeInTheDocument();
    });
});

describe('Register', () => {
    beforeEach(() => { resetStore(); api.get.mockResolvedValue({ data: { google: false } }); });

    it('rejects short and mismatched passwords locally', async () => {
        render(<MemoryRouter><Register /></MemoryRouter>);
        await userEvent.type(screen.getByLabelText('Full name'), 'Ann');
        await userEvent.type(screen.getByLabelText('Email'), 'ann@example.com');
        await userEvent.type(screen.getByLabelText('Password'), 'short');
        await userEvent.type(screen.getByLabelText('Confirm password'), 'different');
        await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
        expect(await screen.findByText('Password must be at least 8 characters')).toBeInTheDocument();
        expect(screen.getByText('Passwords do not match')).toBeInTheDocument();
        expect(api.post).not.toHaveBeenCalled();
    });

    it('shows field errors returned by the server', async () => {
        api.post.mockRejectedValue({ response: { status: 400, data: { message: 'Validation failed', fields: { email: 'Enter a valid email' } } } });
        render(<MemoryRouter><Register /></MemoryRouter>);
        await userEvent.type(screen.getByLabelText('Full name'), 'Ann');
        await userEvent.type(screen.getByLabelText('Email'), 'ann@example.com');
        await userEvent.type(screen.getByLabelText('Password'), 'long-enough-1');
        await userEvent.type(screen.getByLabelText('Confirm password'), 'long-enough-1');
        await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
        expect(await screen.findByText('Enter a valid email')).toBeInTheDocument();
    });
});

describe('return-to after sign-in', () => {
    it('defaults to the app root and clears the stored value', () => {
        expect(consumeReturnTo()).toBe('/');
        sessionStorage.setItem('returnTo', '/join#abc');
        expect(consumeReturnTo()).toBe('/join#abc');
        expect(sessionStorage.getItem('returnTo')).toBeNull();
    });
    it('never redirects to another site', () => {
        for (const bad of ['//evil.example', 'https://evil.example', 'javascript:alert(1)']) {
            sessionStorage.setItem('returnTo', bad);
            expect(consumeReturnTo()).toBe('/');
        }
    });
});