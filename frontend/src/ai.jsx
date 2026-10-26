import { useState } from 'react';
import { Copy, Sparkles } from 'lucide-react';
import api, { errMsg } from './api';
import { useStore } from './store';
import { Modal, btnGhost, btnPrimary, inputCls } from './components.jsx';

const AiBadge = ({ children }) => <span className="inline-flex items-center gap-1 rounded-full bg-violet-500/20 px-2 py-0.5 text-xs text-violet-300"><Sparkles size={12} />{children}</span>;

export function TranslateDialog({ m, onClose }) {
    const { ai, toast } = useStore();
    const [lang, setLang] = useState(() => localStorage.getItem('aiLang') || 'en');
    const [res, setRes] = useState(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const run = async () => {
        setBusy(true); setError('');
        try { const { data } = await api.post(`/api/messages/${m.id}/translate`, { targetLang: lang }); localStorage.setItem('aiLang', lang); setRes(data); }
        catch (e) { setRes(null); setError(errMsg(e)); }
        setBusy(false);
    };
    const copy = async () => { try { await navigator.clipboard.writeText(res.translatedText); toast('Copied'); } catch { toast('Could not copy', 'error'); } };
    return (
        <Modal title="Translate message" onClose={onClose}>
            <div className="mb-3 rounded-xl bg-panel2 p-3 text-sm"><div className="mb-1 text-xs text-mute">Original (unchanged)</div><div className="whitespace-pre-wrap break-words">{m.content}</div></div>
            <div className="mb-3 flex gap-2">
                <select aria-label="Translate into" className={inputCls} value={lang} onChange={(e) => { setLang(e.target.value); setRes(null); }}>
                    {ai.languages.map((l) => <option key={l.code} value={l.code}>{l.name}</option>)}
                </select>
                <button className={btnPrimary} onClick={run} disabled={busy}>{busy ? 'Translating…' : res ? 'Redo' : 'Translate'}</button>
            </div>
            {error && <p role="alert" className="mb-2 text-sm text-red-400">{error}</p>}
            {res && (
                <div className="rounded-xl border border-violet-500/40 p-3 text-sm">
                    <div className="mb-1 flex items-center gap-2"><AiBadge>AI translation to {res.languageName}</AiBadge>{res.cached && <span className="text-xs text-mute">saved</span>}<button aria-label="Copy translation" className="ml-auto rounded-lg p-1 text-mute hover:text-ink" onClick={copy}><Copy size={14} /></button></div>
                    <div className="whitespace-pre-wrap break-words">{res.translatedText}</div>
                </div>
            )}
            <p className="mt-3 text-xs text-mute">Only this message's text is sent to the AI provider ({ai.provider}). Translations can contain mistakes.</p>
        </Modal>
    );
}

const RANGES = [['m50', 'Last 50 messages'], ['m100', 'Last 100 messages'], ['m200', 'Last 200 messages'], ['h24', 'Last 24 hours'], ['h168', 'Last 7 days']];

export function SummaryDialog({ convId, onClose }) {
    const { ai } = useStore();
    const [range, setRange] = useState('m50');
    const [res, setRes] = useState(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const run = async () => {
        setBusy(true); setError('');
        const body = range.startsWith('m') ? { lastMessages: Number(range.slice(1)) } : { sinceHours: Number(range.slice(1)) };
        try { const { data } = await api.post(`/api/conversations/${convId}/summary`, body); setRes(data); }
        catch (e) { setError(errMsg(e)); }
        setBusy(false);
    };
    return (
        <Modal title="Summarize conversation" onClose={onClose} wide>
            <div className="mb-3 flex flex-wrap gap-2">
                <select aria-label="Range" className={`${inputCls} flex-1`} value={range} onChange={(e) => setRange(e.target.value)}>{RANGES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select>
                <button className={btnPrimary} onClick={run} disabled={busy}><Sparkles size={16} /> {busy ? 'Summarizing…' : res ? 'Regenerate' : 'Summarize'}</button>
            </div>
            {error && <p role="alert" className="mb-2 text-sm text-red-400">{error}</p>}
            {res && (
                <div className="rounded-xl border border-violet-500/40 p-3 text-sm">
                    <div className="mb-2 flex flex-wrap items-center gap-2"><AiBadge>AI-generated summary</AiBadge><span className="text-xs text-mute">{res.messageCount} messages · {new Date(res.from).toLocaleDateString()} to {new Date(res.to).toLocaleDateString()}</span></div>
                    <div className="whitespace-pre-wrap break-words">{res.summary}</div>
                    <p className="mt-2 text-xs text-mute">May contain mistakes. It only reads messages; it cannot change anything.</p>
                </div>
            )}
            <p className="mt-3 text-xs text-mute">The selected text messages from this chat (sender names, times and text) are sent to the AI provider ({ai.provider}, {ai.model}). Attachments, other chats and account details are not.</p>
        </Modal>
    );
}

// Shown in Settings so people can see whether AI is on and what it receives.
export function AiStatusSection() {
    const ai = useStore((s) => s.ai);
    return (
        <section className="mb-6">
            <h3 className="mb-2 text-sm font-medium text-mute">AI features</h3>
            <p className="text-sm">{ai.enabled ? <>On, using <b>{ai.provider}</b> ({ai.model}). Translate a message from its action bar; summarize a chat with the sparkle button.</> : 'Not configured on this server. The app works normally without them.'}</p>
        </section>
    );
}