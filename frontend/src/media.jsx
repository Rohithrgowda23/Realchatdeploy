import { useEffect, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, Download, FileText, Film, Mic, Paperclip, Pause, Play, Send, Square, Trash2, X } from 'lucide-react';
import api, { errMsg } from './api';
import { useStore } from './store';
import { Modal, Skeleton, btnGhost, btnPrimary, iconBtn } from './components.jsx';

const MAX_BYTES = 25 * 1024 * 1024;
const ACCEPT = 'image/png,image/jpeg,image/gif,image/webp,video/mp4,video/webm,audio/mpeg,audio/ogg,audio/wav,audio/mp4,application/pdf,.txt,.csv,.docx,.xlsx,.pptx,.zip';

export const fmtSize = (n) => (n < 1024 ? `${n} B` : n < 1048576 ? `${(n / 1024).toFixed(1)} KB` : `${(n / 1048576).toFixed(1)} MB`);
const fmtTime = (s) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;

/** Files are only served to signed-in members, so they are fetched with the auth header and shown from a blob URL. */
export function useBlob(id, enabled = true) {
    const [st, setSt] = useState({ url: null, error: false, loading: enabled });
    useEffect(() => {
        if (!enabled || !id) return undefined;
        let url; let dead = false;
        setSt({ url: null, error: false, loading: true });
        api.get(`/api/attachments/${id}`, { responseType: 'blob' })
            .then((r) => { if (dead) return; url = URL.createObjectURL(r.data); setSt({ url, error: false, loading: false }); })
            .catch(() => { if (!dead) setSt({ url: null, error: true, loading: false }); });
        return () => { dead = true; if (url) URL.revokeObjectURL(url); };
    }, [id, enabled]);
    return st;
}

export async function downloadAttachment(a, toast) {
    try {
        const r = await api.get(`/api/attachments/${a.id}`, { params: { download: true }, responseType: 'blob' });
        const url = URL.createObjectURL(r.data);
        const el = document.createElement('a');
        el.href = url; el.download = a.fileName; document.body.appendChild(el); el.click(); el.remove();
        setTimeout(() => URL.revokeObjectURL(url), 2000);
    } catch (e) { toast(errMsg(e), 'error'); }
}

function Unavailable({ text = 'File unavailable' }) {
    return <div className="flex h-24 w-40 items-center justify-center rounded-xl bg-black/20 text-xs opacity-80">{text}</div>;
}

export function Lightbox({ items, index, onClose }) {
    const [i, setI] = useState(index);
    const { jumpTo, toast } = useStore();
    const cur = items[i];
    const { url, loading, error } = useBlob(cur?.id);
    const go = (d) => setI((x) => Math.min(items.length - 1, Math.max(0, x + d)));
    useEffect(() => {
        const h = (e) => { if (e.key === 'Escape') onClose(); if (e.key === 'ArrowRight') go(1); if (e.key === 'ArrowLeft') go(-1); };
        window.addEventListener('keydown', h);
        return () => window.removeEventListener('keydown', h);
    }, [items.length]); // eslint-disable-line
    if (!cur) return null;
    return (
        <div role="dialog" aria-modal="true" aria-label="Image viewer" className="fixed inset-0 z-50 flex flex-col bg-black/90" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
            <div className="flex items-center gap-2 p-3 text-white">
                <span className="min-w-0 flex-1 truncate text-sm">{cur.fileName} <span className="opacity-60">({i + 1}/{items.length})</span></span>
                {cur.messageId && <button className={`${btnGhost} !bg-white/10 !text-white`} onClick={() => { jumpTo(cur.conversationId, cur.messageId); onClose(); }}>Go to message</button>}
                <button aria-label="Download" className="rounded-lg p-2 hover:bg-white/10" onClick={() => downloadAttachment(cur, toast)}><Download size={18} /></button>
                <button aria-label="Close viewer" className="rounded-lg p-2 hover:bg-white/10" onClick={onClose}><X size={20} /></button>
            </div>
            <div className="relative flex min-h-0 flex-1 items-center justify-center p-2" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
                {i > 0 && <button aria-label="Previous image" className="absolute left-2 rounded-full bg-white/10 p-2 text-white hover:bg-white/20" onClick={() => go(-1)}><ChevronLeft size={24} /></button>}
                {loading && <Skeleton className="h-64 w-64" />}
                {error && <p className="text-sm text-white/70">This image is no longer available.</p>}
                {url && <img src={url} alt={cur.fileName} className="max-h-full max-w-full rounded-lg object-contain" />}
                {i < items.length - 1 && <button aria-label="Next image" className="absolute right-2 rounded-full bg-white/10 p-2 text-white hover:bg-white/20" onClick={() => go(1)}><ChevronRight size={24} /></button>}
            </div>
        </div>
    );
}

function AudioPlayer({ a }) {
    const [requested, setRequested] = useState(false);
    const { url, error } = useBlob(a.id, requested);
    const ref = useRef(null);
    const [playing, setPlaying] = useState(false);
    const [t, setT] = useState(0);
    const [dur, setDur] = useState((a.durationMs || 0) / 1000);
    const [rate, setRate] = useState(1);
    useEffect(() => { if (url && ref.current) ref.current.play().catch(() => {}); }, [url]);
    useEffect(() => { if (ref.current) ref.current.playbackRate = rate; }, [rate, url]);
    const toggle = () => { if (!requested) { setRequested(true); return; } const el = ref.current; if (!el) return; if (el.paused) el.play().catch(() => {}); else el.pause(); };
    if (error) return <Unavailable text="Audio unavailable" />;
    return (
        <div className="flex w-60 items-center gap-2" aria-label={a.kind === 'VOICE' ? 'Voice message' : a.fileName}>
            <button aria-label={playing ? 'Pause' : 'Play'} className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/20 hover:bg-white/30" onClick={toggle}>{playing ? <Pause size={16} /> : <Play size={16} />}</button>
            <div className="min-w-0 flex-1">
                <input type="range" aria-label="Seek" min={0} max={dur || 1} step={0.1} value={t} className="h-1 w-full accent-emerald-400"
                       onChange={(e) => { if (ref.current) { ref.current.currentTime = Number(e.target.value); setT(Number(e.target.value)); } }} />
                <div className="flex justify-between text-[10px] opacity-80"><span>{fmtTime(t)}</span><span>{a.kind === 'VOICE' ? 'Voice' : a.fileName.slice(0, 18)} · {fmtTime(dur)}</span></div>
            </div>
            <button aria-label="Playback speed" className="rounded-md bg-white/10 px-1.5 py-0.5 text-[11px] hover:bg-white/20" onClick={() => setRate((r) => (r === 1 ? 1.5 : r === 1.5 ? 2 : 1))}>{rate}x</button>
            {url && <audio ref={ref} src={url} preload="auto" onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)} onEnded={() => setPlaying(false)}
                           onTimeUpdate={(e) => setT(e.currentTarget.currentTime)} onLoadedMetadata={(e) => { if (Number.isFinite(e.currentTarget.duration)) setDur(e.currentTarget.duration); }} />}
        </div>
    );
}

export function PreviewModal({ a, onClose }) {
    const { url, loading, error } = useBlob(a.id);
    const toast = useStore((s) => s.toast);
    const isPdf = a.mimeType === 'application/pdf';
    return (
        <Modal title={a.fileName} onClose={onClose} wide>
            {loading && <Skeleton className="h-48 w-full" />}
            {error && <p className="text-sm text-mute">This file is no longer available.</p>}
            {url && a.kind === 'VIDEO' && <video src={url} controls autoPlay className="max-h-[60vh] w-full rounded-xl bg-black" onError={() => toast('This video format cannot be played in your browser. Download it instead.', 'error')} />}
            {url && (a.kind === 'AUDIO' || a.kind === 'VOICE') && <audio src={url} controls autoPlay className="w-full" />}
            {url && isPdf && <iframe title={a.fileName} src={url} className="h-[65vh] w-full rounded-xl border border-line" />}
            <div className="mt-3 flex justify-end"><button className={btnGhost} onClick={() => downloadAttachment(a, toast)}><Download size={16} /> Download</button></div>
        </Modal>
    );
}

function ImageThumb({ a, onOpen }) {
    const { url, loading, error } = useBlob(a.id);
    if (error) return <Unavailable text="Image unavailable" />;
    if (loading || !url) return <Skeleton className="h-40 w-48" />;
    return <button onClick={onOpen} aria-label={`Open image ${a.fileName}`} className="block overflow-hidden rounded-xl focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-400"><img src={url} alt={a.fileName} className="max-h-64 max-w-full object-cover" /></button>;
}

/** Renders the attachment of a chat message. */
export function AttachmentView({ m }) {
    const a = m.attachment;
    const toast = useStore((s) => s.toast);
    const [lb, setLb] = useState(null);
    const [preview, setPreview] = useState(false);
    if (!a) return null;
    if (a.kind === 'IMAGE') {
        const open = () => {
            const list = (useStore.getState().messages[m.conversationId] || []).filter((x) => !x.deleted && x.attachment?.kind === 'IMAGE');
            setLb({ items: list.map((x) => ({ ...x.attachment, messageId: x.id, conversationId: x.conversationId })), index: Math.max(0, list.findIndex((x) => x.id === m.id)) });
        };
        return <><ImageThumb a={a} onOpen={open} />{lb && <Lightbox items={lb.items} index={lb.index} onClose={() => setLb(null)} />}</>;
    }
    if (a.kind === 'AUDIO' || a.kind === 'VOICE') return <AudioPlayer a={a} />;
    if (a.kind === 'VIDEO') {
        return (
            <>
                <button onClick={() => setPreview(true)} className="flex h-28 w-52 flex-col items-center justify-center gap-1 rounded-xl bg-black/30 text-xs hover:bg-black/40" aria-label={`Play video ${a.fileName}`}>
                    <Film size={28} /><span className="max-w-[11rem] truncate">{a.fileName}</span><span className="opacity-70">{fmtSize(a.sizeBytes)} · tap to play</span>
                </button>
                {preview && <PreviewModal a={a} onClose={() => setPreview(false)} />}
            </>
        );
    }
    return (
        <>
            <div className="flex w-60 items-center gap-3 rounded-xl bg-black/20 p-2">
                <FileText size={28} className="shrink-0" />
                <div className="min-w-0 flex-1"><div className="truncate text-sm font-medium">{a.fileName}</div><div className="text-xs opacity-70">{a.mimeType === 'application/pdf' ? 'PDF' : a.fileName.split('.').pop().toUpperCase()} · {fmtSize(a.sizeBytes)}</div></div>
                {a.mimeType === 'application/pdf' && <button className="rounded-lg px-2 py-1 text-xs hover:bg-white/10" onClick={() => setPreview(true)}>Preview</button>}
                <button aria-label={`Download ${a.fileName}`} className="rounded-lg p-1.5 hover:bg-white/10" onClick={() => downloadAttachment(a, toast)}><Download size={16} /></button>
            </div>
            {preview && <PreviewModal a={a} onClose={() => setPreview(false)} />}
        </>
    );
}

/** Paperclip + microphone buttons and their progress/recording panel. Nothing is uploaded until the user confirms. */
export function ComposerTools({ convId, caption, onSent }) {
    const { toast, addMessage, replyTo, setReplyTo } = useStore();
    const fileRef = useRef(null);
    const abortRef = useRef(null);
    const [upload, setUpload] = useState(null); // {name, pct}
    const [rec, setRec] = useState({ state: 'idle', secs: 0, levels: [], blob: null, url: null, ms: 0 });
    const rt = useRef({}); // recorder internals

    const post = async (path, form, name) => {
        const ctl = new AbortController(); abortRef.current = ctl;
        setUpload({ name, pct: 0 });
        try {
            const { data } = await api.post(path, form, { signal: ctl.signal, onUploadProgress: (e) => e.total && setUpload({ name, pct: Math.round((e.loaded / e.total) * 100) }) });
            addMessage(data); setReplyTo(null); onSent?.(); return true;
        } catch (e) { if (e.code !== 'ERR_CANCELED') toast(errMsg(e), 'error'); return false; }
        finally { setUpload(null); abortRef.current = null; }
    };

    const onFile = async (e) => {
        const f = e.target.files?.[0]; e.target.value = '';
        if (!f) return;
        if (f.size > MAX_BYTES) return toast(`File is too large (limit ${MAX_BYTES / 1048576} MB)`, 'error');
        const fd = new FormData();
        fd.append('file', f);
        if (caption?.trim()) fd.append('caption', caption.trim());
        if (replyTo?.id) fd.append('replyToId', replyTo.id);
        await post(`/api/conversations/${convId}/attachments`, fd, f.name);
    };

    const cleanupRec = () => {
        const r = rt.current;
        clearInterval(r.timer); clearInterval(r.meter);
        r.stream?.getTracks().forEach((t) => t.stop());
        r.ctx?.close?.().catch(() => {});
        rt.current = {};
    };
    useEffect(() => () => { cleanupRec(); }, []); // eslint-disable-line

    const startRec = async () => {
        if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') return toast('Voice recording is not supported in this browser', 'error');
        let stream;
        try { stream = await navigator.mediaDevices.getUserMedia({ audio: true }); } // permission is requested only now, on click
        catch (e) { return toast(e.name === 'NotAllowedError' ? 'Microphone permission was denied' : 'No microphone could be used', 'error'); }
        const mime = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus'].find((t) => MediaRecorder.isTypeSupported(t));
        const mr = mime ? new MediaRecorder(stream, { mimeType: mime }) : new MediaRecorder(stream);
        const chunks = []; const started = performance.now();
        const r = { stream, mr, cancelled: false };
        try { // level meter for the waveform
            const ctx = new (window.AudioContext || window.webkitAudioContext)();
            const an = ctx.createAnalyser(); an.fftSize = 64; ctx.createMediaStreamSource(stream).connect(an);
            const buf = new Uint8Array(an.frequencyBinCount);
            r.ctx = ctx;
            r.meter = setInterval(() => { an.getByteFrequencyData(buf); const lv = Array.from(buf.slice(0, 16)).map((v) => v / 255); setRec((s) => ({ ...s, levels: lv })); }, 100);
        } catch { /* waveform is optional */ }
        mr.ondataavailable = (e) => { if (e.data.size) chunks.push(e.data); };
        mr.onstop = () => {
            const ms = Math.round(performance.now() - started);
            const cancelled = rt.current.cancelled;
            cleanupRec();
            if (cancelled) { setRec({ state: 'idle', secs: 0, levels: [], blob: null, url: null, ms: 0 }); return; }
            if (ms < 700) { toast('Recording too short', 'error'); setRec({ state: 'idle', secs: 0, levels: [], blob: null, url: null, ms: 0 }); return; }
            const blob = new Blob(chunks, { type: mr.mimeType || 'audio/webm' });
            setRec({ state: 'review', secs: Math.round(ms / 1000), levels: [], blob, url: URL.createObjectURL(blob), ms });
        };
        r.timer = setInterval(() => { const s = (performance.now() - started) / 1000; setRec((x) => ({ ...x, secs: s })); if (s >= 600) mr.stop(); }, 250);
        rt.current = r;
        mr.start();
        setRec({ state: 'recording', secs: 0, levels: [], blob: null, url: null, ms: 0 });
    };
    const stopRec = () => rt.current.mr?.state === 'recording' && rt.current.mr.stop();
    const cancelRec = () => { if (rt.current.mr) { rt.current.cancelled = true; if (rt.current.mr.state === 'recording') rt.current.mr.stop(); } };
    const discard = () => { if (rec.url) URL.revokeObjectURL(rec.url); setRec({ state: 'idle', secs: 0, levels: [], blob: null, url: null, ms: 0 }); };
    const sendVoice = async () => {
        const ext = rec.blob.type.includes('mp4') ? 'm4a' : rec.blob.type.includes('ogg') ? 'ogg' : 'webm';
        const fd = new FormData();
        fd.append('file', rec.blob, `voice.${ext}`); fd.append('durationMs', rec.ms);
        if (replyTo?.id) fd.append('replyToId', replyTo.id);
        if (await post(`/api/conversations/${convId}/voice`, fd, 'Voice message')) discard();
    };

    const busy = !!upload || rec.state !== 'idle';
    return (
        <>
            <input ref={fileRef} type="file" accept={ACCEPT} className="hidden" onChange={onFile} aria-hidden tabIndex={-1} />
            <button type="button" className={iconBtn} aria-label="Attach file" title="Attach file" disabled={busy} onClick={() => fileRef.current?.click()}><Paperclip size={20} /></button>
            <button type="button" className={iconBtn} aria-label="Record voice message" title="Record voice message" disabled={busy} onClick={startRec}><Mic size={20} /></button>
            {busy && (
                <div role="status" className="absolute inset-x-3 bottom-full mb-2 rounded-2xl border border-line bg-panel p-3 shadow-xl md:inset-x-6">
                    {upload && (
                        <div className="flex items-center gap-3 text-sm">
                            <span className="min-w-0 flex-1 truncate">Uploading {upload.name}…</span>
                            <div className="h-1.5 w-32 overflow-hidden rounded-full bg-panel2"><div className="h-full bg-emerald-400 transition-all" style={{ width: `${upload.pct}%` }} /></div>
                            <span className="w-9 text-right text-xs text-mute">{upload.pct}%</span>
                            <button type="button" aria-label="Cancel upload" className={iconBtn} onClick={() => abortRef.current?.abort()}><X size={16} /></button>
                        </div>
                    )}
                    {!upload && rec.state === 'recording' && (
                        <div className="flex items-center gap-3">
                            <span className="h-2.5 w-2.5 animate-pulse rounded-full bg-red-500" aria-hidden />
                            <span className="w-12 text-sm tabular-nums">{fmtTime(rec.secs)}</span>
                            <div className="flex h-8 flex-1 items-center gap-0.5" aria-hidden>{(rec.levels.length ? rec.levels : Array(16).fill(0.05)).map((v, i) => <span key={i} className="w-1 rounded bg-violet-400" style={{ height: `${Math.max(8, v * 100)}%` }} />)}</div>
                            <button type="button" aria-label="Cancel recording" className={iconBtn} onClick={cancelRec}><Trash2 size={18} /></button>
                            <button type="button" className={btnPrimary} onClick={stopRec}><Square size={14} /> Stop</button>
                        </div>
                    )}
                    {!upload && rec.state === 'review' && (
                        <div className="flex flex-wrap items-center gap-3">
                            <audio src={rec.url} controls className="h-9 min-w-0 flex-1" />
                            <button type="button" className={btnGhost} onClick={discard}>Discard</button>
                            <button type="button" className={btnPrimary} onClick={sendVoice}><Send size={14} /> Send</button>
                        </div>
                    )}
                </div>
            )}
        </>
    );
}

const TABS = [['ALL', 'All'], ['IMAGE', 'Images'], ['VIDEO', 'Videos'], ['AUDIO', 'Audio'], ['DOCUMENT', 'Files']];

export function GalleryDialog({ convId, onClose }) {
    const { jumpTo, toast } = useStore();
    const [kind, setKind] = useState('ALL');
    const [items, setItems] = useState(null);
    const [more, setMore] = useState(false);
    const [lb, setLb] = useState(null);
    const [prev, setPrev] = useState(null);
    const load = async (append) => {
        const before = append && items?.length ? items[items.length - 1].messageId : undefined;
        try {
            const { data } = await api.get(`/api/conversations/${convId}/media`, { params: { kind, before, size: 24 } });
            setItems((cur) => (append ? [...(cur || []), ...data] : data)); setMore(data.length === 24);
        } catch (e) { toast(errMsg(e), 'error'); setItems((c) => c || []); }
    };
    useEffect(() => { setItems(null); load(false); }, [kind, convId]); // eslint-disable-line
    const images = (items || []).filter((x) => x.kind === 'IMAGE').map((x) => ({ ...x, conversationId: convId }));
    return (
        <Modal title="Shared media" onClose={onClose} wide>
            <div className="mb-3 flex gap-1 overflow-x-auto rounded-xl bg-panel2 p-1 text-sm" role="tablist">
                {TABS.map(([k, label]) => <button key={k} role="tab" aria-selected={kind === k} onClick={() => setKind(k)} className={`whitespace-nowrap rounded-lg px-3 py-1.5 ${kind === k ? 'bg-panel shadow' : 'text-mute hover:text-ink'}`}>{label}</button>)}
            </div>
            {items === null && <Skeleton className="h-32 w-full" />}
            {items && items.length === 0 && <p className="p-8 text-center text-sm text-mute">Nothing shared here yet.</p>}
            {items && items.length > 0 && (
                <ul className="grid grid-cols-3 gap-2 sm:grid-cols-4">
                    {items.map((x) => x.kind === 'IMAGE' ? (
                        <li key={x.id}><GalleryThumb x={x} onOpen={() => setLb({ items: images, index: images.findIndex((y) => y.id === x.id) })} /></li>
                    ) : (
                        <li key={x.id} className="col-span-3 sm:col-span-4">
                            <div className="flex items-center gap-3 rounded-xl border border-line p-2 text-sm">
                                {x.kind === 'VIDEO' ? <Film size={22} /> : x.kind === 'VOICE' || x.kind === 'AUDIO' ? <Mic size={22} /> : <FileText size={22} />}
                                <div className="min-w-0 flex-1"><div className="truncate font-medium">{x.fileName}</div><div className="truncate text-xs text-mute">{x.senderName} · {new Date(x.createdAt).toLocaleDateString()} · {fmtSize(x.sizeBytes)}</div></div>
                                {(x.kind !== 'DOCUMENT' || x.mimeType === 'application/pdf') && <button className="text-xs text-violet-400 hover:underline" onClick={() => setPrev(x)}>{x.kind === 'DOCUMENT' ? 'Preview' : 'Play'}</button>}
                                <button aria-label={`Download ${x.fileName}`} className={iconBtn} onClick={() => downloadAttachment(x, toast)}><Download size={16} /></button>
                                <button className="text-xs text-violet-400 hover:underline" onClick={() => { jumpTo(convId, x.messageId); onClose(); }}>Go to message</button>
                            </div>
                        </li>
                    ))}
                </ul>
            )}
            {more && <div className="mt-3 text-center"><button className={btnGhost} onClick={() => load(true)}>Load more</button></div>}
            {lb && <Lightbox items={lb.items} index={lb.index} onClose={() => setLb(null)} />}
            {prev && <PreviewModal a={prev} onClose={() => setPrev(null)} />}
        </Modal>
    );
}

function GalleryThumb({ x, onOpen }) {
    const { url, loading, error } = useBlob(x.id);
    if (error) return <div className="flex aspect-square items-center justify-center rounded-xl bg-panel2 text-xs text-mute">Unavailable</div>;
    if (loading || !url) return <Skeleton className="aspect-square w-full" />;
    return <button onClick={onOpen} aria-label={`Open ${x.fileName}`} className="block aspect-square w-full overflow-hidden rounded-xl focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-400"><img src={url} alt={x.fileName} className="h-full w-full object-cover" /></button>;
}