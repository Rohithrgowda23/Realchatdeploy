import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Users } from 'lucide-react';
import api, { errMsg } from './api';
import { useStore } from './store';
import { Avatar, btnGhost, btnPrimary } from './components.jsx';

// Invite links look like  https://host/join#<token>. The token stays in the fragment, so it is never sent to a server or logged.
export default function JoinPage() {
    const nav = useNavigate();
    const { open, loadConversations } = useStore();
    const [token] = useState(() => window.location.hash.slice(1));
    const [info, setInfo] = useState(null);
    const [state, setState] = useState('loading'); // loading | ready | invalid | pending | busy
    const [error, setError] = useState('');

    useEffect(() => {
        if (!token) { setState('invalid'); return; }
        api.post('/api/invites/preview', { token })
            .then((r) => { setInfo(r.data); setState(r.data.requestPending ? 'pending' : 'ready'); })
            .catch(() => setState('invalid'));
    }, [token]);

    const join = async () => {
        setState('busy'); setError('');
        try {
            const { data } = await api.post('api/invites/join', { token });
            if (data.status === 'PENDING') { setState('pending'); return; }
            await loadConversations(); await open(data.conversationId);
            window.history.replaceState({}, '', '/');
            nav('/', { replace: true });
        } catch (e) { setError(errMsg(e)); setState(e.response?.status === 404 ? 'invalid' : 'ready'); }
    };

    return (
        <main className="flex min-h-full items-center justify-center p-4">
            <div className="w-full max-w-md rounded-3xl border border-line bg-panel p-8 text-center shadow-2xl">
                {state === 'loading' && <p role="status" className="text-mute">Checking invite…</p>}
                {state === 'invalid' && (<><h1 className="mb-2 text-xl font-semibold">Invite not available</h1><p role="alert" className="mb-4 text-sm text-mute">{error || 'This invite link is invalid, expired or has been revoked.'}</p><button className={btnGhost} onClick={() => nav('/', { replace: true })}>Go to RealChat</button></>)}
                {info && ['ready', 'busy', 'pending'].includes(state) && (
                    <>
                        <div className="mb-3 flex justify-center">{info.avatarUrl ? <Avatar name={info.groupName} url={info.avatarUrl} size={72} /> : <span className="flex h-[72px] w-[72px] items-center justify-center rounded-full bg-violet-600/20 text-violet-300"><Users size={32} /></span>}</div>
                        <h1 className="font-display text-2xl">{info.groupName}</h1>
                        {info.description && <p className="mt-1 text-sm text-mute">{info.description}</p>}
                        <p className="mt-1 text-xs text-mute">{info.memberCount} members</p>
                        {info.alreadyMember ? <><p className="my-4 text-sm">You are already in this group.</p><button className={btnPrimary} onClick={() => nav('/', { replace: true })}>Open RealChat</button></>
                            : state === 'pending' ? <p role="status" className="my-4 rounded-xl bg-amber-500/10 p-3 text-sm text-amber-400">Request sent. A group admin has to approve it; you will be notified.</p>
                                : <>{error && <p role="alert" className="mt-3 text-sm text-red-400">{error}</p>}<button className={`${btnPrimary} mt-5 w-full`} disabled={state === 'busy'} onClick={join}>{info.requiresApproval ? 'Request to join' : 'Join group'}</button></>}
                    </>
                )}
            </div>
        </main>
    );
}