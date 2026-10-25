import { useEffect, useState } from 'react';
import api, { errMsg } from './api';
import { useStore } from './store';
import { Avatar, Modal, Skeleton } from './components.jsx';

const escapeRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** @mention picker state for the composer. Candidates come from the conversation's own member list (members only). */
export function useMentions(candidates) {
    const [picked, setPicked] = useState({}); // userId -> name typed
    const [q, setQ] = useState(null);         // { start, end, query }
    const [idx, setIdx] = useState(0);
    const matches = q ? candidates.filter((c) => c.name.toLowerCase().includes(q.query.toLowerCase())).slice(0, 6) : [];

    const onChange = (value, caret) => {
        const m = /(^|\s)@([^\s@]{0,30})$/.exec(value.slice(0, caret));
        setQ(m ? { start: caret - m[2].length - 1, end: caret, query: m[2] } : null);
        setIdx(0);
    };
    const choose = (c, value) => {
        setPicked((p) => ({ ...p, [c.userId]: c.name }));
        const out = `${value.slice(0, q.start)}@${c.name} ${value.slice(q.end)}`;
        setQ(null);
        return out;
    };
    const onKeyDown = (e, value, setValue) => {
        if (!q || matches.length === 0) return false;
        if (e.key === 'ArrowDown') { e.preventDefault(); setIdx((i) => (i + 1) % matches.length); return true; }
        if (e.key === 'ArrowUp') { e.preventDefault(); setIdx((i) => (i - 1 + matches.length) % matches.length); return true; }
        if (e.key === 'Enter' || e.key === 'Tab') { e.preventDefault(); setValue(choose(matches[idx], value)); return true; }
        if (e.key === 'Escape') { e.preventDefault(); setQ(null); return true; }
        return false;
    };
    // Only people whose @name is still in the final text are sent as structured mentions.
    const collect = (text) => Object.entries(picked).filter(([, name]) => text.includes(`@${name}`)).map(([id]) => Number(id));
    const reset = () => { setPicked({}); setQ(null); };
    return { matches, open: !!q && matches.length > 0, idx, onChange, onKeyDown, choose, collect, reset };
}

export function MentionMenu({ m, onPick }) {
    return (
        <ul role="listbox" aria-label="Mention a member" className="absolute bottom-full left-3 z-30 mb-2 w-64 overflow-hidden rounded-2xl border border-line bg-panel shadow-xl md:left-6">
            {m.matches.map((c, i) => (
                <li key={c.userId} role="option" aria-selected={i === m.idx}>
                    <button type="button" onMouseDown={(e) => { e.preventDefault(); onPick(c); }} className={`flex w-full items-center gap-2 px-3 py-2 text-left text-sm ${i === m.idx ? 'bg-violet-600/20' : 'hover:bg-panel2'}`}>
                        <Avatar name={c.name} url={c.avatarUrl} size={24} /><span className="truncate">{c.name}</span>
                    </button>
                </li>
            ))}
        </ul>
    );
}

/** Message text with @mentions highlighted (matched on the name typed at send time, so renames cannot break it). */
export function MessageText({ m, me }) {
    const list = m.mentions || [];
    if (!list.length) return <span className="whitespace-pre-wrap break-words">{m.content}</span>;
    const names = [...new Set(list.map((x) => x.name))].sort((a, b) => b.length - a.length);
    const parts = m.content.split(new RegExp(`(@(?:${names.map(escapeRe).join('|')}))`, 'g'));
    return (
        <span className="whitespace-pre-wrap break-words">
      {parts.map((p, i) => {
          const hit = p.startsWith('@') && list.find((x) => `@${x.name}` === p);
          if (!hit) return <span key={i}>{p}</span>;
          const mine = hit.userId === me;
          return <mark key={i} className={`rounded px-0.5 font-medium ${mine ? 'bg-emerald-400/30 text-inherit' : 'bg-violet-400/30 text-inherit'}`}>{p}</mark>;
      })}
    </span>
    );
}

export function MentionsDialog({ convId, onClose }) {
    const { jumpTo, toast } = useStore();
    const [items, setItems] = useState(null);
    const [more, setMore] = useState(false);
    const load = async (append) => {
        const before = append && items?.length ? items[items.length - 1].id : undefined;
        try {
            const { data } = await api.get(`/conversations/${convId}/mentions`, { params: { before, size: 20 } });
            setItems((c) => (append ? [...(c || []), ...data] : data)); setMore(data.length === 20);
        } catch (e) { toast(errMsg(e), 'error'); setItems((c) => c || []); }
    };
    useEffect(() => { load(false); }, [convId]); // eslint-disable-line
    return (
        <Modal title="Mentions of you" onClose={onClose} wide>
            {items === null && <Skeleton className="h-16 w-full" />}
            {items && items.length === 0 && <p className="p-6 text-center text-sm text-mute">Nobody has mentioned you in this conversation.</p>}
            {items && items.length > 0 && (
                <ul className="divide-y divide-line">
                    {items.map((m) => (
                        <li key={m.id}>
                            <button className="w-full rounded-lg p-2 text-left hover:bg-panel2" onClick={() => { jumpTo(convId, m.id); onClose(); }}>
                                <div className="text-xs text-mute"><b className="text-ink">{m.senderName}</b> · {new Date(m.createdAt).toLocaleString()}</div>
                                <div className="line-clamp-2 break-words text-sm">{m.content}</div>
                            </button>
                        </li>
                    ))}
                </ul>
            )}
            {more && <div className="mt-3 text-center"><button className="rounded-xl bg-panel2 px-4 py-2 text-sm hover:bg-line" onClick={() => load(true)}>Load more</button></div>}
        </Modal>
    );
}