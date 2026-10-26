import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api, { errMsg } from './api';
import { useStore } from './store';

export default function DeleteAccount() {
    const [confirmText, setConfirmText] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const navigate = useNavigate();
    const logout = useStore((state) => state.logout);

    async function handleDelete() {
        if (confirmText !== 'DELETE') {
            setError('Type DELETE to confirm.');
            return;
        }

        const confirmed = window.confirm(
            'Permanently delete your RealChat account? This cannot be undone.'
        );

        if (!confirmed) return;

        setLoading(true);
        setError('');

        try {
            await api.delete('/api/account');

            // Clear local authentication after the server confirms deletion.
            sessionStorage.clear();
            localStorage.removeItem('token');

            // Clear the current app state without calling the logout API.
            useStore.setState({
                token: null,
                user: null,
                conversations: [],
                messages: {},
                details: {},
                activeId: null,
                saved: [],
            });

            navigate('/login', { replace: true });
        } catch (e) {
            setError(errMsg(e));
        } finally {
            setLoading(false);
        }
    }

    return (
        <section className="max-w-xl space-y-4 rounded-xl border border-red-500/30 p-6">
            <h2 className="text-xl font-semibold text-red-500">
                Delete account
            </h2>

            <p>
                Permanently remove your account and personal information.
                Shared chat history will be handled according to the
                application's deletion rules.
            </p>

            <label htmlFor="delete-confirm" className="block text-sm">
                Type DELETE to confirm
            </label>

            <input
                id="delete-confirm"
                value={confirmText}
                onChange={(e) => setConfirmText(e.target.value)}
                className="w-full rounded-lg border bg-transparent p-3"
                autoComplete="off"
            />

            {error && (
                <p role="alert" className="text-sm text-red-500">
                    {error}
                </p>
            )}

            <button
                type="button"
                onClick={handleDelete}
                disabled={loading || confirmText !== 'DELETE'}
                className="rounded-lg bg-red-600 px-4 py-3 text-white disabled:opacity-50"
            >
                {loading ? 'Deleting account...' : 'Permanently delete account'}
            </button>
        </section>
    );
}
