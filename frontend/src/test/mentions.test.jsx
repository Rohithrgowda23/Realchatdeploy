import { act, render, renderHook } from '@testing-library/react';
import { vi } from 'vitest';
import { MessageText, useMentions } from '../mentions.jsx';
import { fmtSize } from '../media.jsx';

vi.mock('../api', () => ({ default: { get: vi.fn(), post: vi.fn() }, errMsg: (e) => e?.message }));

describe('MessageText', () => {
    it('renders plain text when nobody is mentioned', () => {
        const { container } = render(<MessageText m={{ content: 'hello <b>world</b>', mentions: [] }} me={1} />);
        expect(container.textContent).toBe('hello <b>world</b>'); // shown as text, never as markup
        expect(container.querySelector('b')).toBeNull();
        expect(container.querySelectorAll('mark')).toHaveLength(0);
    });
    it('highlights mentions, with a different colour for the current user', () => {
        const { container } = render(<MessageText m={{ content: 'hi @Ann Lee and @Bob', mentions: [{ userId: 1, name: 'Ann Lee' }, { userId: 2, name: 'Bob' }] }} me={1} />);
        const marks = [...container.querySelectorAll('mark')];
        expect(marks.map((m) => m.textContent)).toEqual(['@Ann Lee', '@Bob']);
        expect(marks[0].className).toContain('emerald');
        expect(marks[1].className).toContain('violet');
    });
    it('handles names with regex characters safely', () => {
        const { container } = render(<MessageText m={{ content: 'ping @A.B (dev) now', mentions: [{ userId: 3, name: 'A.B (dev)' }] }} me={9} />);
        expect(container.querySelector('mark').textContent).toBe('@A.B (dev)');
    });
    it('highlights by the name typed at send time, so a later rename cannot break it', () => {
        const { container } = render(<MessageText m={{ content: 'hello @OldName', mentions: [{ userId: 5, name: 'OldName' }] }} me={9} />);
        expect(container.querySelectorAll('mark')).toHaveLength(1);
    });
});

describe('useMentions', () => {
    const people = [{ userId: 1, name: 'Ann' }, { userId: 2, name: 'Bob' }, { userId: 3, name: 'Annabel' }];

    it('opens on @, filters by what is typed and closes on a space', () => {
        const { result } = renderHook(() => useMentions(people));
        act(() => result.current.onChange('hi @an', 6));
        expect(result.current.open).toBe(true);
        expect(result.current.matches.map((p) => p.name)).toEqual(['Ann', 'Annabel']);
        act(() => result.current.onChange('hi @an ', 7));
        expect(result.current.open).toBe(false);
    });
    it('does not open for an @ inside a word such as an email address', () => {
        const { result } = renderHook(() => useMentions(people));
        act(() => result.current.onChange('mail me at x@an', 15));
        expect(result.current.open).toBe(false);
    });
    it('inserts the chosen name and only reports people still mentioned in the final text', () => {
        const { result } = renderHook(() => useMentions(people));
        act(() => result.current.onChange('hi @B', 5));
        let text;
        act(() => { text = result.current.choose(people[1], 'hi @B'); });
        expect(text).toBe('hi @Bob ');
        expect(result.current.collect(text)).toEqual([2]);
        expect(result.current.collect('hi there')).toEqual([]); // the user deleted the mention
    });
    it('supports keyboard selection and Escape', () => {
        const { result } = renderHook(() => useMentions(people));
        act(() => result.current.onChange('@a', 2));
        const setValue = vi.fn();
        const down = { key: 'ArrowDown', preventDefault: vi.fn() };
        act(() => { result.current.onKeyDown(down, '@a', setValue); });
        expect(down.preventDefault).toHaveBeenCalled();
        act(() => { result.current.onKeyDown({ key: 'Enter', preventDefault: vi.fn() }, '@a', setValue); });
        expect(setValue).toHaveBeenCalledWith('@Annabel ');
        act(() => result.current.onChange('@a', 2));
        act(() => { result.current.onKeyDown({ key: 'Escape', preventDefault: vi.fn() }, '@a', setValue); });
        expect(result.current.open).toBe(false);
    });
    it('lets Enter send the message when no menu is open', () => {
        const { result } = renderHook(() => useMentions(people));
        expect(result.current.onKeyDown({ key: 'Enter', preventDefault: vi.fn() }, 'hello', vi.fn())).toBe(false);
    });
});

describe('fmtSize', () => {
    it('formats file sizes', () => {
        expect(fmtSize(512)).toBe('512 B');
        expect(fmtSize(2048)).toBe('2.0 KB');
        expect(fmtSize(5 * 1048576)).toBe('5.0 MB');
    });
});