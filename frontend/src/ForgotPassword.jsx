import { useState } from 'react';
import { Link } from 'react-router-dom';
import api, { errMsg } from './api';
import { Field, Shell } from './Auth.jsx';
import { btnPrimary } from './components.jsx';

export default function ForgotPassword() {
    const [email, setEmail] = useState('');
    const [busy, setBusy] = useState(false);
    const [done, setDone] = useState(false);
    const [error, setError] = useState('');
    const submit = async (e) => {
        e.preventDefault();
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return setError('Enter a valid email');
        setBusy(true); setError('');
        try { await api.post('/auth/forgot-password', { email }); setDone(true); } catch (er) { setError(errMsg(er)); }
        setBusy(false);
    };
    return (
        <Shell title="Forgot your password?" subtitle="Enter your email and we will send a reset link if an account exists."
               footer={<Link className="text-violet-400 hover:underline" to="/login">Back to sign in</Link>}>
            {done ? (
                <p role="status" className="rounded-xl bg-emerald-500/10 p-3 text-sm text-emerald-400">If an account exists for that email, a reset link is on its way. The link works once and expires soon.</p>
            ) : (
                <form onSubmit={submit} noValidate>
                    <Field id="email" label="Email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} error={error} />
                    <button className={`${btnPrimary} w-full`} disabled={busy}>{busy ? 'Sending…' : 'Send reset link'}</button>
                </form>
            )}
        </Shell>
    );
}