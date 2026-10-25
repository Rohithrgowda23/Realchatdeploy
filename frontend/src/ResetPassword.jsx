import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { errMsg } from './api';
import { Field, Shell } from './Auth.jsx';
import { btnPrimary } from './components.jsx';

export default function ResetPassword() {
    const [token] = useState(() => new URLSearchParams(window.location.search).get('token') || '');
    const [state, setState] = useState('checking'); // checking | valid | invalid | done
    const [pw, setPw] = useState('');
    const [confirm, setConfirm] = useState('');
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        window.history.replaceState({}, '', '/reset-password'); // keep the token out of the address bar and history
        if (!token) { setState('invalid'); return; }
        api.post('/auth/reset-password/validate', { token }).then((r) => setState(r.data.valid ? 'valid' : 'invalid')).catch(() => setState('invalid'));
    }, [token]);

    const submit = async (e) => {
        e.preventDefault();
        const er = {};
        if (pw.length < 8) er.password = 'Password must be at least 8 characters';
        if (confirm !== pw) er.confirm = 'Passwords do not match';
        if (Object.keys(er).length) return setErrors(er);
        setBusy(true); setErrors({});
        try { await api.post('/auth/reset-password', { token, newPassword: pw }); setState('done'); }
        catch (x) { setErrors({ form: errMsg(x), ...(x.response?.data?.fields?.newPassword ? { password: x.response.data.fields.newPassword } : {}) }); }
        setBusy(false);
    };

    return (
        <Shell title="Choose a new password" subtitle="This link can be used once." footer={<Link className="text-violet-400 hover:underline" to="/login">Back to sign in</Link>}>
            {state === 'checking' && <p role="status" className="text-sm text-mute">Checking your link…</p>}
            {state === 'invalid' && <p role="alert" className="text-sm text-red-400">This reset link is invalid or has expired. <Link className="text-violet-400 hover:underline" to="/forgot-password">Request a new one</Link>.</p>}
            {state === 'done' && <p role="status" className="rounded-xl bg-emerald-500/10 p-3 text-sm text-emerald-400">Password updated. You were signed out everywhere. <Link className="underline" to="/login">Sign in</Link></p>}
            {state === 'valid' && (
                <form onSubmit={submit} noValidate>
                    <Field id="pw" label="New password" type="password" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} error={errors.password} />
                    <Field id="pw2" label="Confirm password" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} error={errors.confirm} />
                    {errors.form && !errors.password && <p role="alert" className="mb-3 text-sm text-red-400">{errors.form}</p>}
                    <button className={`${btnPrimary} w-full`} disabled={busy}>{busy ? 'Saving…' : 'Update password'}</button>
                </form>
            )}
        </Shell>
    );
}