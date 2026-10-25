import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useStore } from './store';
import { Login, Register } from './Auth.jsx';
import Chat from './Chat.jsx';
import OAuthCallback from './OAuthCallback.jsx';
import ForgotPassword from './ForgotPassword.jsx';
import JoinPage from './JoinPage.jsx';
import ResetPassword from './ResetPassword.jsx';
import { Toasts } from './components.jsx';

function Guard({ children }) {
    const token = useStore((s) => s.token);
    const loc = useLocation();
    if (!token) {
        sessionStorage.setItem('returnTo', loc.pathname + loc.search + loc.hash); // come back here (e.g. an invite link) after signing in
        return <Navigate to="/login" replace />;
    }
    return children;
}
function Guest({ children }) {
    const token = useStore((s) => s.token);
    return token ? <Navigate to="/" replace /> : children;
}

export default function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/login" element={<Guest><Login /></Guest>} />
                <Route path="/register" element={<Guest><Register /></Guest>} />
                <Route path="/oauth/callback" element={<OAuthCallback />} />
                <Route path="/forgot-password" element={<ForgotPassword />} />
                <Route path="/reset-password" element={<ResetPassword />} />
                <Route path="/join" element={<Guard><JoinPage /></Guard>} />
                <Route path="/" element={<Guard><Chat /></Guard>} />
                <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
            <Toasts />
        </BrowserRouter>
    );
}