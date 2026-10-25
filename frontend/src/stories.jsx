import { useEffect, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, Eye, Plus, Settings as Cog, Trash2, X } from 'lucide-react';
import api, { errMsg } from './api';
import { useStore } from './store';
import { Avatar, Modal, Skeleton, UserPicker, btnDanger, btnGhost, btnPrimary, iconBtn, inputCls } from './components.jsx';

const COLORS = ['#4c1d95', '#065f46', '#9f1239', '#1e3a8a', '#92400e', '#111827'];
const ago = (iso) => { const m = Math.max(0, Math.round((Date.now() - new Date(iso)) / 60000)); return m < 1 ? 'just now' : m < 60 ? `${m} min ago` : `${Math.round(m / 60)} h ago`; };
const left = (iso) => { const m = Math.round((new Date(iso) - Date.now()) / 60000); return m < 60 ? `${Math.max(m, 0)} min left` : `${Math.round(m / 60)} h left`; };

// Story media is only served to people allowed to see it, so it is fetched with the auth header and shown from a blob URL.
function useStoryBlob(id, enabled) {
    const [st, setSt] = useState({ url: null, error: false });
    useEffect(() => {
        if (!enabled) return undefined;
        let url; let dead = false;
        setSt({ url: null, error: false });
        api.get(`/stories/${id}/media`, { responseType: 'blob' })
            .then((r) => { if (!dead) { url = URL.createObjectURL(r.data); setSt({ url, error: false }); } })
            .catch(() => { if (!dead) setSt({ url: null, error: true }); });
        return () => { dead = true; if (url) URL.revokeObjectURL(url); };
    }, [id, enabled]);
    return st;
}

function Viewer({ groups, start, onClose, onViewed, onDeleted }) {
    const toast = useStore((s) => s.toast);
    const [g, setG] = useState(start);
    const [i, setI] = useState(0);
    const [progress, setProgress] = useState(0);
    const [panel, setPanel] = useState(null); // null | 'viewers' | 'delete'
    const [viewers, setViewers] = useState(null);
    const group = groups[g];
    const story = group.stories[i];
    const media = story.type !== 'TEXT';
    const { url, error } = useStoryBlob(story.id, media);
    const videoRef = useRef(null);

    const next = () => { if (i < group.stories.length - 1) { setI(i + 1); setProgress(0); } else if (g < groups.length - 1) { setG(g + 1); setI(0); setProgress(0); } else onClose(); };
    const prev = () => { if (progress > 0.15 || (i === 0 && g === 0)) setProgress(0); else if (i > 0) { setI(i - 1); setProgress(0); } else if (g > 0) { setG(g - 1); setI(groups[g - 1].stories.length - 1); setProgress(0); } };

    useEffect(() => { // record the view (the server ignores your own stories)
        setPanel(null); setViewers(null);
        if (!group.own && !story.viewed) api.post(`/stories/${story.id}/view`).then(() => onViewed(story.id)).catch(() => {});
    }, [story.id]); // eslint-disable-line

    useEffect(() => { // images and text advance after 6 s; video follows its own length
        if (panel || story.type === 'VIDEO' || (media && !url && !error)) return undefined;
        const t = setInterval(() => setProgress((p) => Math.min(1, p + 50 / 6000)), 50);
        return () => clearInterval(t);
    }, [story.id, panel, url, error]); // eslint-disable-line
    useEffect(() => { if (progress >= 1) next(); }, [progress]); // eslint-disable-line
    useEffect(() => {
        const h = (e) => { if (e.key === 'Escape') onClose(); if (e.key === 'ArrowRight') next(); if (e.key === 'ArrowLeft') prev(); };
        window.addEventListener('keydown', h);
        return () => window.removeEventListener('keydown', h);
    });

    const openViewers = async () => {
        setPanel('viewers');
        try { const { data } = await api.get(`/stories/${story.id}/viewers`); setViewers(data); } catch (e) { toast(errMsg(e), 'error'); setViewers([]); }
    };
    const remove = async () => { try { await api.delete(`/stories/${story.id}`); toast('Story deleted'); onDeleted(); } catch (e) { toast(errMsg(e), 'error'); } };

    return (
        <div role="dialog" aria-modal="true" aria-label={`${group.name}'s story`} className="fixed inset-0 z-50 flex flex-col bg-black text-white">
            <div className="absolute inset-x-0 top-0 z-10 bg-gradient-to-b from-black/70 to-transparent p-3">
                <div className="mb-2 flex gap-1" aria-hidden>{group.stories.map((s, k) => <div key={s.id} className="h-1 flex-1 overflow-hidden rounded bg-white/30"><div className="h-full bg-white" style={{ width: `${k < i ? 100 : k === i ? progress * 100 : 0}%` }} /></div>)}</div>
                <div className="flex items-center gap-2"><Avatar name={group.name} url={group.avatarUrl} size={32} /><div className="min-w-0 flex-1 text-sm"><b>{group.own ? 'Your story' : group.name}</b> <span className="opacity-70">{ago(story.createdAt)} · {left(story.expiresAt)}</span></div>
                    <button aria-label="Close stories" className="rounded-lg p-2 hover:bg-white/10" onClick={onClose}><X size={22} /></button></div>
            </div>

            <div className="relative flex flex-1 items-center justify-center overflow-hidden">
                {story.type === 'TEXT' && <div className="flex h-full w-full items-center justify-center p-8 text-center" style={{ background: story.bgColor || '#4c1d95' }}><p className="max-w-md whitespace-pre-wrap break-words text-2xl font-semibold">{story.text}</p></div>}
                {story.type === 'IMAGE' && (error ? <p className="text-sm opacity-70">This story is no longer available.</p> : url ? <img src={url} alt={story.text || 'Story'} className="max-h-full max-w-full object-contain" /> : <Skeleton className="h-64 w-64" />)}
                {story.type === 'VIDEO' && (error ? <p className="text-sm opacity-70">This story is no longer available.</p> : url ? (
                    <video ref={videoRef} src={url} autoPlay playsInline className="max-h-full max-w-full" onEnded={next}
                           onTimeUpdate={(e) => e.currentTarget.duration && setProgress(e.currentTarget.currentTime / e.currentTarget.duration)}
                           onError={() => toast('This video cannot be played in your browser', 'error')} />) : <Skeleton className="h-64 w-64" />)}
                {story.type !== 'TEXT' && story.text && <p className="absolute inset-x-0 bottom-20 mx-auto max-w-md rounded-xl bg-black/50 p-3 text-center text-sm">{story.text}</p>}
                {!panel && <><button aria-label="Previous story" className="absolute inset-y-16 left-0 w-1/3" onClick={prev} /><button aria-label="Next story" className="absolute inset-y-16 right-0 w-2/3" onClick={next} /></>}
                <button aria-hidden tabIndex={-1} className="hidden"><ChevronLeft /><ChevronRight /></button>
            </div>

            {group.own && (
                <div className="relative z-10 bg-gradient-to-t from-black/80 to-transparent p-3">
                    {panel === 'viewers' && (
                        <div className="mb-2 max-h-48 overflow-y-auto rounded-xl bg-neutral-900 p-3 text-sm">
                            <div className="mb-2 flex items-center justify-between"><b>Viewed by {viewers ? viewers.length : '…'}</b><button aria-label="Close viewers" onClick={() => setPanel(null)}><X size={16} /></button></div>
                            {viewers && viewers.length === 0 && <p className="opacity-70">No views yet.</p>}
                            {viewers?.map((v) => <div key={v.userId} className="flex items-center gap-2 py-1"><Avatar name={v.name} url={v.avatarUrl} size={24} /><span className="flex-1">{v.name}</span><span className="text-xs opacity-60">{ago(v.viewedAt)}</span></div>)}
                        </div>
                    )}
                    {panel === 'delete' && <div className="mb-2 flex items-center gap-2 rounded-xl bg-neutral-900 p-3 text-sm"><span className="flex-1">Delete this story for everyone?</span><button className={btnGhost} onClick={() => setPanel(null)}>Cancel</button><button className={btnDanger} onClick={remove}>Delete</button></div>}
                    <div className="flex items-center gap-3 text-sm"><button className="flex items-center gap-1.5 rounded-lg px-2 py-1 hover:bg-white/10" onClick={openViewers}><Eye size={16} /> {story.viewCount} views</button><button aria-label="Delete story" className="ml-auto rounded-lg p-2 hover:bg-white/10" onClick={() => setPanel('delete')}><Trash2 size={18} /></button></div>
                </div>
            )}
        </div>
    );
}

function Composer({ onDone, onCancel }) {
    const toast = useStore((s) => s.toast);
    const [type, setType] = useState('TEXT');
    const [text, setText] = useState('');
    const [color, setColor] = useState(COLORS[0]);
    const [file, setFile] = useState(null);
    const [preview, setPreview] = useState(null);
    const [ttl, setTtl] = useState('24');
    const [pct, setPct] = useState(null);
    useEffect(() => { if (!file) { setPreview(null); return undefined; } const u = URL.createObjectURL(file); setPreview(u); return () => URL.revokeObjectURL(u); }, [file]);
    const post = async (e) => {
        e.preventDefault();
        const fd = new FormData();
        fd.append('type', type); fd.append('ttlHours', ttl);
        if (text.trim()) fd.append('text', text.trim());
        if (type === 'TEXT') fd.append('bgColor', color); else if (file) fd.append('file', file);
        setPct(0);
        try { await api.post('/stories', fd, { onUploadProgress: (p) => p.total && setPct(Math.round((p.loaded / p.total) * 100)) }); toast('Story posted'); onDone(); }
        catch (x) { toast(errMsg(x), 'error'); }
        setPct(null);
    };
    const can = type === 'TEXT' ? text.trim().length > 0 : !!file;
    return (
        <form onSubmit={post} className="space-y-3">
            <div className="flex gap-1 rounded-xl bg-panel2 p-1 text-sm" role="tablist">{[['TEXT', 'Text'], ['IMAGE', 'Photo'], ['VIDEO', 'Video']].map(([k, l]) => <button type="button" key={k} role="tab" aria-selected={type === k} onClick={() => { setType(k); setFile(null); }} className={`flex-1 rounded-lg px-3 py-1.5 ${type === k ? 'bg-panel shadow' : 'text-mute'}`}>{l}</button>)}</div>
            {type === 'TEXT' ? (
                <>
                    <div className="flex h-40 items-center justify-center rounded-2xl p-4 text-center" style={{ background: color }}><textarea aria-label="Story text" maxLength={500} rows={4} placeholder="Type a status…" className="w-full resize-none bg-transparent text-center text-xl font-semibold text-white outline-none placeholder:text-white/60" value={text} onChange={(e) => setText(e.target.value)} /></div>
                    <div className="flex gap-2" role="radiogroup" aria-label="Background colour">{COLORS.map((c) => <button type="button" key={c} role="radio" aria-checked={color === c} aria-label={`Colour ${c}`} onClick={() => setColor(c)} className={`h-8 w-8 rounded-full ring-offset-2 ring-offset-panel ${color === c ? 'ring-2 ring-violet-400' : ''}`} style={{ background: c }} />)}</div>
                </>
            ) : (
                <>
                    <input aria-label={type === 'IMAGE' ? 'Choose photo' : 'Choose video'} type="file" accept={type === 'IMAGE' ? 'image/png,image/jpeg,image/gif,image/webp' : 'video/mp4,video/webm'} className={inputCls} onChange={(e) => setFile(e.target.files?.[0] || null)} />
                    {preview && (type === 'IMAGE' ? <img src={preview} alt="Preview" className="max-h-56 rounded-xl" /> : <video src={preview} controls className="max-h-56 rounded-xl" />)}
                    <input aria-label="Caption" maxLength={500} className={inputCls} placeholder="Add a caption (optional)" value={text} onChange={(e) => setText(e.target.value)} />
                    <p className="text-xs text-mute">Photos up to 10 MB, videos up to 25 MB (MP4 or WebM).</p>
                </>
            )}
            <label className="block text-sm">Visible for<select className={`${inputCls} mt-1`} value={ttl} onChange={(e) => setTtl(e.target.value)}>{[['6', '6 hours'], ['12', '12 hours'], ['24', '24 hours (default)'], ['48', '48 hours']].map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
            {pct !== null && <div className="h-1.5 overflow-hidden rounded-full bg-panel2"><div className="h-full bg-emerald-400" style={{ width: `${pct}%` }} /></div>}
            <div className="flex justify-end gap-2"><button type="button" className={btnGhost} onClick={onCancel}>Cancel</button><button className={btnPrimary} disabled={!can || pct !== null}>Post story</button></div>
        </form>
    );
}

const AUDIENCES = [['CONTACTS', 'My contacts', 'People I have a direct chat with'], ['CONTACTS_AND_GROUPS', 'Contacts and group members', 'Also people who share a group with me'], ['NOBODY', 'Nobody', 'Only I can see my stories']];

function PrivacySettings({ onBack }) {
    const toast = useStore((s) => s.toast);
    const [s, setS] = useState(null);
    const [adding, setAdding] = useState(false);
    useEffect(() => { api.get('/stories/settings').then((r) => setS(r.data)).catch((e) => toast(errMsg(e), 'error')); }, []); // eslint-disable-line
    const save = async () => {
        try { await api.put('/stories/settings', { audience: s.audience, hiddenFrom: s.hiddenFrom.map((u) => u.id) }); toast('Story privacy saved'); onBack(); } catch (e) { toast(errMsg(e), 'error'); }
    };
    if (!s) return <Skeleton className="h-24 w-full" />;
    return (
        <div className="space-y-3">
            <fieldset><legend className="mb-1 text-sm font-medium">Who can see my stories</legend>
                {AUDIENCES.map(([v, l, d]) => <label key={v} className="flex cursor-pointer items-start gap-3 rounded-xl p-2 hover:bg-panel2"><input type="radio" name="aud" className="mt-1 accent-violet-600" checked={s.audience === v} onChange={() => setS({ ...s, audience: v })} /><span><span className="block text-sm">{l}</span><span className="text-xs text-mute">{d}</span></span></label>)}
            </fieldset>
            <div><div className="mb-1 flex items-center justify-between text-sm font-medium">Hide from<button type="button" className="text-xs text-violet-400 hover:underline" onClick={() => setAdding(!adding)}>{adding ? 'Done' : 'Add people'}</button></div>
                {adding && <UserPicker excludeIds={s.hiddenFrom.map((u) => u.id)} onPick={(u) => setS({ ...s, hiddenFrom: [...s.hiddenFrom, { id: u.id, name: u.name }] })} />}
                <div className="mt-1 flex flex-wrap gap-2">{s.hiddenFrom.length === 0 && <span className="text-xs text-mute">Nobody is hidden.</span>}{s.hiddenFrom.map((u) => <span key={u.id} className="flex items-center gap-1 rounded-full bg-panel2 py-1 pl-3 pr-1 text-xs">{u.name}<button type="button" aria-label={`Stop hiding from ${u.name}`} className="rounded-full p-0.5 hover:bg-line" onClick={() => setS({ ...s, hiddenFrom: s.hiddenFrom.filter((x) => x.id !== u.id) })}><X size={12} /></button></span>)}</div>
                <p className="mt-1 text-xs text-mute">People you block never see your stories.</p></div>
            <div className="flex justify-end gap-2"><button className={btnGhost} onClick={onBack}>Cancel</button><button className={btnPrimary} onClick={save}>Save</button></div>
        </div>
    );
}

export function StoriesDialog({ onClose }) {
    const { toast, loadStoryCount } = useStore();
    const [groups, setGroups] = useState(null);
    const [view, setView] = useState('list'); // list | compose | settings
    const [playing, setPlaying] = useState(null);
    const load = () => api.get('/stories/feed').then((r) => setGroups(r.data)).catch((e) => { toast(errMsg(e), 'error'); setGroups([]); });
    useEffect(() => { load(); }, []); // eslint-disable-line
    const close = () => { loadStoryCount(); onClose(); };
    const own = groups?.find((g) => g.own);
    const markViewed = (id) => setGroups((gs) => gs.map((g) => { const stories = g.stories.map((s) => (s.id === id ? { ...s, viewed: true } : s)); return { ...g, stories, hasUnviewed: !g.own && stories.some((s) => !s.viewed) }; }));

    return (
        <>
            <Modal title={view === 'compose' ? 'New story' : view === 'settings' ? 'Story privacy' : 'Stories'} onClose={close} wide>
                {view === 'compose' && <Composer onCancel={() => setView('list')} onDone={() => { setView('list'); load(); }} />}
                {view === 'settings' && <PrivacySettings onBack={() => setView('list')} />}
                {view === 'list' && (
                    <>
                        <div className="mb-4 flex gap-2"><button className={btnPrimary} onClick={() => setView('compose')}><Plus size={16} /> Add story</button><button className={btnGhost} onClick={() => setView('settings')}><Cog size={16} /> Privacy</button></div>
                        {groups === null && <Skeleton className="h-20 w-full" />}
                        {groups && groups.length === 0 && <p className="p-6 text-center text-sm text-mute">No active stories. Stories disappear after the time you choose (24 hours by default).</p>}
                        <ul className="grid grid-cols-3 gap-3 sm:grid-cols-4">
                            {groups?.map((g, k) => (
                                <li key={g.userId}>
                                    <button onClick={() => setPlaying(k)} className="flex w-full flex-col items-center gap-1 rounded-xl p-2 hover:bg-panel2 focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-400" aria-label={`${g.own ? 'Your story' : `${g.name}'s story`}${g.hasUnviewed ? ', new' : ''}`}>
                                        <span className={`rounded-full p-0.5 ${g.hasUnviewed ? 'bg-gradient-to-tr from-emerald-400 to-violet-500' : 'bg-line'}`}><span className="block rounded-full bg-panel p-0.5"><Avatar name={g.name} url={g.avatarUrl} size={56} /></span></span>
                                        <span className="w-full truncate text-center text-xs">{g.own ? 'Your story' : g.name}</span>
                                        <span className="text-[10px] text-mute">{g.stories.length} {g.stories.length === 1 ? 'story' : 'stories'}</span>
                                    </button>
                                </li>
                            ))}
                        </ul>
                        {!own && groups && groups.length > 0 && <p className="mt-3 text-xs text-mute">You have no active story.</p>}
                    </>
                )}
            </Modal>
            {playing !== null && groups && groups[playing] && <Viewer groups={groups} start={playing} onClose={() => setPlaying(null)} onViewed={markViewed} onDeleted={() => { setPlaying(null); load(); }} />}
        </>
    );
}