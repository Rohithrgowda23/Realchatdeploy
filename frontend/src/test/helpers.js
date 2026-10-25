import { useStore } from '../store';

export const resetStore = (extra = {}) => {
    useStore.setState({ token: null, user: null, toasts: [], conversations: [], activeId: null, messages: {}, details: {}, hasMore: {}, replyTo: null, saved: [], ...extra });
};
export const toastTexts = () => useStore.getState().toasts.map((t) => t.text);