import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { MessagesSquare } from 'lucide-react';
import api, { errMsg } from './api';
import { useStore } from './store';
import { btnGhost, btnPrimary, inputCls } from './components.jsx';
import { OAUTH_ERRORS } from './oauthMessages.js';
import { consumeReturnTo } from './returnTo.js';

export function Shell({ title, subtitle, children, footer }) {
    return (
        <main className="relative flex min-h-full items-center justify-center overflow-hidden p-4">
            <div aria-hidden className="pointer-events-none absolute -left-32 -top-32 h-96 w-96 rounded-full bg-violet-600/25 blur-3xl" />
            <div aria-hidden className="pointer-events-none absolute -bottom-32 -right-24 h-96 w-96 rounded-full bg-emerald-500/15 blur-3xl" />
            <div className="relative w-full max-w-md rounded-3xl border border-line bg-panel/90 p-8 shadow-2xl backdrop-blur">
                <div className="mb-6 flex items-center gap-3">
                    <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-violet-500 to-indigo-600 text-white shadow-lg shadow-violet-900/40"><MessagesSquare size={22} /></span>
                    <span className="font-display text-2xl">RealChat</span>
                </div>
                <h1 className="text-xl font-semibold">{title}</h1>
                <p className="mb-5 mt-1 text-sm text-mute">{subtitle}</p>
                {children}
                <p className="mt-5 text-center text-sm text-mute">{footer}</p>
            </div>
        </main>
    );
}

export function Field({ label, error, id, ...rest }) {
    return (
        <div className="mb-3">
            <label htmlFor={id} className="mb-1 block text-sm font-medium">{label}</label>
            <input id={id} className={inputCls} aria-invalid={!!error} aria-describedby={error ? `${id}-err` : undefined} {...rest} />
            {error && <p id={`${id}-err`} className="mt-1 text-xs text-red-400">{error}</p>}
        </div>
    );
}

const BACKEND =
    import.meta.env.VITE_BACKEND_URL || 'https://realchatdeploy.onrender.com';

function GoogleButton({ label }) {
    const [busy, setBusy] = useState(false);

    return (
        <>
            <div className="my-4 flex items-center gap-3 text-xs text-mute">
                <span className="h-px flex-1 bg-line" />
                or
                <span className="h-px flex-1 bg-line" />
            </div>

            <button
                type="button"
                disabled={busy}
                className={`${btnGhost} w-full border border-line`}
                onClick={() => {
                    setBusy(true);
                    window.location.assign(
                        `${BACKEND.replace(/\/$/, '')}/oauth2/authorization/google`
                    );
                }}
            >
                <svg
                    width="18"
                    height="18"
                    viewBox="0 0 48 48"
                    aria-hidden="true"
                >
                    <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
                    <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
                    <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
                    <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
                </svg>
                {busy ? 'Redirecting to Google…' : label}
            </button>
        </>
    );
}


function useAuthSubmit(path) {
    const nav = useNavigate();
    const setAuth = useStore((s) => s.setAuth);
    const [busy, setBusy] = useState(false);
    const [errors, setErrors] = useState({});
    const submit = async (body) => {
        setBusy(true); setErrors({});
        try {
            const { data } = await api.post(path, body);
            setAuth(data.token, data.user);
            nav(consumeReturnTo(), { replace: true });
        } catch (e) { setErrors({ ...(e.response?.data?.fields || {}), form: errMsg(e) }); }
        setBusy(false);
    };
    return { submit, busy, errors, setErrors };
}

const emailOk = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);

export function Login() {
    const [params] = useSearchParams();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const { submit, busy, errors, setErrors } = useAuthSubmit('/auth/login');
    const onSubmit = (e) => {
        e.preventDefault();
        const er = {};
        if (!emailOk(email)) er.email = 'Enter a valid email';
        if (!password) er.password = 'Password is required';
        if (Object.keys(er).length) return setErrors(er);
        submit({ email, password });
    };
    return (
        <Shell title="Welcome back" subtitle="Sign in to continue your conversations." footer={<>New here? <Link className="text-violet-400 hover:underline" to="/register">Create an account</Link></>}>
            {params.get('oauth_error') && <p role="alert" className="mb-3 rounded-xl bg-red-500/10 p-3 text-sm text-red-400">{OAUTH_ERRORS[params.get('reason')] || OAUTH_ERRORS.failed}</p>}
            {params.get('expired') && <p role="alert" className="mb-3 rounded-xl bg-amber-500/10 p-3 text-sm text-amber-400">Your session expired. Please sign in again.</p>}
            <form onSubmit={onSubmit} noValidate>
                <Field id="email" label="Email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} error={errors.email} />
                <Field id="password" label="Password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} error={errors.password} />
                <div className="-mt-1 mb-3 text-right text-sm"><Link className="text-violet-400 hover:underline" to="/forgot-password">Forgot password?</Link></div>
                {errors.form && !errors.email && !errors.password && <p role="alert" className="mb-3 text-sm text-red-400">{errors.form}</p>}
                <button className={`${btnPrimary} w-full`} disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
            </form>
            <GoogleButton label="Continue with Google" />
        </Shell>
    );
}

export function Register() {
    const [f, setF] = useState({ name: '', email: '', password: '', confirm: '' });
    const { submit, busy, errors, setErrors } = useAuthSubmit('/auth/register');
    const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
    const onSubmit = (e) => {
        e.preventDefault();
        const er = {};
        if (!f.name.trim()) er.name = 'Name is required';
        if (!emailOk(f.email)) er.email = 'Enter a valid email';
        if (f.password.length < 8) er.password = 'Password must be at least 8 characters';
        if (f.confirm !== f.password) er.confirm = 'Passwords do not match';
        if (Object.keys(er).length) return setErrors(er);
        submit({ name: f.name, email: f.email, password: f.password });
    };
    return (
        <Shell title="Create your account" subtitle="Private, real-time conversations." footer={<>Already registered? <Link className="text-violet-400 hover:underline" to="/login">Sign in</Link></>}>
            <form onSubmit={onSubmit} noValidate>
                <Field id="name" label="Full name" autoComplete="name" value={f.name} onChange={set('name')} error={errors.name} />
                <Field id="email" label="Email" type="email" autoComplete="email" value={f.email} onChange={set('email')} error={errors.email} />
                <Field id="password" label="Password" type="password" autoComplete="new-password" value={f.password} onChange={set('password')} error={errors.password} />
                <Field id="confirm" label="Confirm password" type="password" autoComplete="new-password" value={f.confirm} onChange={set('confirm')} error={errors.confirm} />
                {errors.form && !errors.name && !errors.email && !errors.password && !errors.confirm && <p role="alert" className="mb-3 text-sm text-red-400">{errors.form}</p>}
                <button className={`${btnPrimary} w-full`} disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
            </form>
            <GoogleButton label="Sign up with Google" />
        </Shell>
    );
}