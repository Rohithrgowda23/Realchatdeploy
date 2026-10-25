// Where to send the user after signing in (e.g. back to an invite link). Only same-site paths are accepted.
export function consumeReturnTo() {
    const r = sessionStorage.getItem('returnTo');
    sessionStorage.removeItem('returnTo');
    return r && r.startsWith('/') && !r.startsWith('//') ? r : '/';
}