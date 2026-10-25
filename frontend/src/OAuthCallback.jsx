import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api, { errMsg } from './api';
import { useStore } from './store';
import { consumeReturnTo } from './returnTo.js';

// Landing page after Google sign-in. The URL carries only a single-use code (never a token); we swap it
// for a real session here and only then navigate into the app.
export default function OAuthCallback() {
    const nav = useNavigate();
    const setAuth = useStore((s) => s.setAuth);
    const ran = useRef(false); // the code is single-use, so React StrictMode's double effect must not call twice
    const [error, setError] = useState('');

    useEffect(() => {
        if (ran.current) return;
        ran.current = true;
        const code = new URLSearchParams(window.location.search).get('code');
        window.history.replaceState({}, '', '/oauth/callback'); // remove the code from the address bar/history
        if (!code) { nav('/login?oauth_error=1&reason=failed', { replace: true }); return; }
        api.post('/auth/oauth/exchange', { code })
            .then(({ data }) => { setAuth(data.token, data.user); nav(consumeReturnTo(), { replace: true }); })
            .catch((e) => setError(errMsg(e)));
    }, [nav, setAuth]);

    return (
        <main className="flex min-h-full items-center justify-center p-4 text-center">
            {error ? (
                <div><p role="alert" className="mb-3 text-red-400">{error}</p><Link className="text-violet-400 hover:underline" to="/login">Back to sign in</Link></div>
            ) : <p className="text-mute" role="status">Signing you in…</p>}
        </main>
    );
}