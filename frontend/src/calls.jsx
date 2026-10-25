import { useEffect, useRef, useState } from 'react';
import { Mic, MicOff, Phone, PhoneIncoming, PhoneMissed, PhoneOff, PhoneOutgoing, Video, VideoOff } from 'lucide-react';
import api, { errMsg } from './api';
import { useStore } from './store';
import { useCallStore } from './callStore';
import { Avatar, Modal, Skeleton, btnGhost, iconBtn } from './components.jsx';

const mmss = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;

function MediaEl({ stream, muted, className, audioOnly }) {
    const ref = useRef(null);
    useEffect(() => { if (ref.current) ref.current.srcObject = stream || null; }, [stream]);
    return audioOnly ? <audio ref={ref} autoPlay muted={muted} /> : <video ref={ref} autoPlay playsInline muted={muted} className={className} />;
}

/** Full-screen incoming / outgoing / active call UI. Renders nothing when idle. */
export function CallOverlay() {
    const { status, call, muted, camOff, seconds, connState, local, remote, acceptCall, rejectCall, endCall, toggleMute, toggleCamera } = useCallStore();
    const first = useRef(null);
    useEffect(() => { first.current?.focus(); }, [status]);
    if (status === 'idle' || !call) return null;
    const video = call.type === 'VIDEO';
    const label = status === 'incoming' ? `Incoming ${video ? 'video' : 'audio'} call` : status === 'outgoing' ? 'Calling…' : status === 'connecting' ? 'Connecting…'
        : connState === 'disconnected' ? 'Reconnecting…' : mmss(seconds);

    return (
        <div role="dialog" aria-modal="true" aria-label={label} className="fixed inset-0 z-[60] flex flex-col bg-neutral-950 text-white">
            {video && status !== 'incoming' && remote && <MediaEl stream={remote} className="absolute inset-0 h-full w-full object-cover" />}
            {!video && remote && <MediaEl stream={remote} audioOnly />}
            <div className="relative z-10 flex flex-1 flex-col items-center justify-center gap-3 p-6 text-center" style={{ background: video && remote ? 'transparent' : undefined }}>
                {!(video && remote && status === 'active') && <Avatar name={call.peerName || '?'} url={call.peerAvatar || undefined} size={112} />}
                <h2 className="text-2xl font-semibold drop-shadow">{call.peerName}</h2>
                <p role="status" className="text-sm opacity-80 drop-shadow">{label}</p>
                {connState === 'disconnected' && <p className="rounded-full bg-amber-500/90 px-3 py-1 text-xs text-black">Connection unstable… trying to recover</p>}
            </div>
            {video && local && status !== 'incoming' && <MediaEl stream={local} muted className="absolute bottom-28 right-4 z-20 h-36 w-28 rounded-xl border border-white/30 object-cover" />}
            <div className="relative z-10 flex items-center justify-center gap-5 bg-gradient-to-t from-black/80 to-transparent p-8">
                {status === 'incoming' ? (
                    <>
                        <button ref={first} aria-label="Decline call" onClick={rejectCall} className="flex h-16 w-16 items-center justify-center rounded-full bg-red-600 hover:bg-red-500 focus:outline-none focus-visible:ring-4 focus-visible:ring-white/60"><PhoneOff size={26} /></button>
                        <button aria-label="Accept call" onClick={acceptCall} className="flex h-16 w-16 items-center justify-center rounded-full bg-emerald-600 hover:bg-emerald-500 focus:outline-none focus-visible:ring-4 focus-visible:ring-white/60">{video ? <Video size={26} /> : <Phone size={26} />}</button>
                    </>
                ) : (
                    <>
                        <button ref={first} aria-label={muted ? 'Unmute microphone' : 'Mute microphone'} aria-pressed={muted} onClick={toggleMute} className={`flex h-14 w-14 items-center justify-center rounded-full ${muted ? 'bg-white text-black' : 'bg-white/15 hover:bg-white/25'}`}>{muted ? <MicOff size={22} /> : <Mic size={22} />}</button>
                        {video && <button aria-label={camOff ? 'Turn camera on' : 'Turn camera off'} aria-pressed={camOff} onClick={toggleCamera} className={`flex h-14 w-14 items-center justify-center rounded-full ${camOff ? 'bg-white text-black' : 'bg-white/15 hover:bg-white/25'}`}>{camOff ? <VideoOff size={22} /> : <Video size={22} />}</button>}
                        <button aria-label="End call" onClick={() => endCall(false)} className="flex h-16 w-16 items-center justify-center rounded-full bg-red-600 hover:bg-red-500"><PhoneOff size={26} /></button>
                    </>
                )}
            </div>
        </div>
    );
}

const when = (iso) => new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });

export function CallsDialog({ onClose }) {
    const { toast, loadMissedCount } = useStore();
    const startCall = useCallStore((s) => s.startCall);
    const [items, setItems] = useState(null);
    const [more, setMore] = useState(false);
    const load = async (append) => {
        const before = append && items?.length ? items[items.length - 1].id : undefined;
        try { const { data } = await api.get('/calls', { params: { before, size: 20 } }); setItems((c) => (append ? [...(c || []), ...data] : data)); setMore(data.length === 20); }
        catch (e) { toast(errMsg(e), 'error'); setItems((c) => c || []); }
    };
    useEffect(() => { load(false); api.post('/calls/seen').then(loadMissedCount).catch(() => {}); }, []); // eslint-disable-line
    const callBack = (c, type) => { onClose(); startCall(c.conversationId, { id: c.peerId, name: c.peerName, avatarUrl: c.peerAvatar }, type); };
    const icon = (c) => (c.missed ? <PhoneMissed size={18} className="text-red-400" /> : c.direction === 'INCOMING' ? <PhoneIncoming size={18} className="text-emerald-400" /> : <PhoneOutgoing size={18} className="text-violet-300" />);
    const outcome = (c) => (c.missed ? 'Missed' : c.status === 'REJECTED' ? 'Declined' : c.status === 'FAILED' ? 'Failed' : c.status === 'MISSED' ? 'No answer' : c.durationSeconds != null ? mmss(c.durationSeconds) : c.status.toLowerCase());
    return (
        <Modal title="Calls" onClose={onClose} wide>
            {items === null && <Skeleton className="h-16 w-full" />}
            {items && items.length === 0 && <p className="p-6 text-center text-sm text-mute">No calls yet. Use the phone or video button in a chat.</p>}
            <ul className="divide-y divide-line">
                {items?.map((c) => (
                    <li key={c.id} className="flex items-center gap-3 py-2">
                        <Avatar name={c.peerName} url={c.peerAvatar || undefined} size={40} />
                        <div className="min-w-0 flex-1"><div className={`truncate text-sm font-medium ${c.missed ? 'text-red-400' : ''}`}>{c.peerName}</div><div className="flex items-center gap-1.5 text-xs text-mute">{icon(c)}{c.type === 'VIDEO' ? 'Video' : 'Audio'} · {outcome(c)} · {when(c.startedAt)}</div></div>
                        <button aria-label={`Audio call ${c.peerName}`} className={iconBtn} onClick={() => callBack(c, 'AUDIO')}><Phone size={18} /></button>
                        <button aria-label={`Video call ${c.peerName}`} className={iconBtn} onClick={() => callBack(c, 'VIDEO')}><Video size={18} /></button>
                    </li>
                ))}
            </ul>
            {more && <div className="mt-3 text-center"><button className={btnGhost} onClick={() => load(true)}>Load more</button></div>}
        </Modal>
    );
}