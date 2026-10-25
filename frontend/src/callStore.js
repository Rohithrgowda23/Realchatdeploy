import { create } from 'zustand';
import api from './api';
import { sendWs } from './ws';
import { useStore } from './store';

// One-to-one WebRTC calls. Signalling goes through the existing authenticated STOMP connection; media goes browser to browser.
let pc = null;
let localStream = null;
let pendingIce = [];
let timer = null;
let disconnTimer = null;
let ringtone = null;
let signalQueue = Promise.resolve();

const toast = (t, k) => useStore.getState().toast(t, k);
export const callsSupported = () => !!(typeof navigator !== 'undefined' && navigator.mediaDevices?.getUserMedia && typeof window !== 'undefined' && window.RTCPeerConnection);

const REASONS = {
    rejected: 'Call declined', busy: 'The user is busy', unavailable: 'The user is not available', cancelled: 'Call cancelled', timeout: 'No answer',
    ended: 'Call ended', failed: 'Call failed: the connection was lost', disconnected: 'Call ended: the connection was lost',
};

const initial = { status: 'idle', call: null, muted: false, camOff: false, seconds: 0, connState: '', local: null, remote: null };

export const useCallStore = create((set, get) => ({
    ...initial,

    startCall: async (conversationId, peer, type) => {
        if (get().status !== 'idle') return toast('You are already in a call', 'error');
        if (!callsSupported()) return toast('Calls are not supported in this browser', 'error');
        try { localStream = await getMedia(type); } catch (e) { return toast(e.message, 'error'); } // permission is requested only now, when you press call
        set({ status: 'outgoing', local: localStream, muted: false, camOff: false, call: { callId: null, conversationId, peerId: peer.id, peerName: peer.name, peerAvatar: peer.avatarUrl, type, direction: 'outgoing' } });
        if (!sendWs('/app/call.start', { conversationId, type })) { teardown(); toast('You are offline. Reconnecting…', 'error'); }
    },

    acceptCall: async () => {
        const { call, status } = get();
        if (status !== 'incoming' || !call) return;
        stopRingtone();
        if (!callsSupported()) { sendWs('/app/call.reject', { callId: call.callId }); teardown(); return toast('Calls are not supported in this browser', 'error'); }
        try { localStream = await getMedia(call.type); } catch (e) { sendWs('/app/call.reject', { callId: call.callId }); teardown(); return toast(e.message, 'error'); }
        set({ status: 'connecting', local: localStream });
        await createPc(call.callId);          // ready before we answer, so the caller's offer cannot arrive too early
        sendWs('/app/call.accept', { callId: call.callId });
    },

    rejectCall: () => { const { call } = get(); if (call) sendWs('/app/call.reject', { callId: call.callId }); teardown(); },

    /** Hang up from any state. */
    endCall: (failed = false) => {
        const { status, call } = get();
        if (!call) return teardown();
        if (status === 'incoming') sendWs('/app/call.reject', { callId: call.callId });
        else if (status === 'outgoing' && !call.callId) { /* not registered yet: just drop it locally */ }
        else if (status === 'outgoing') sendWs('/app/call.cancel', { callId: call.callId });
        else sendWs('/app/call.end', { callId: call.callId, failed });
        teardown();
    },

    toggleMute: () => { const muted = !get().muted; localStream?.getAudioTracks().forEach((t) => { t.enabled = !muted; }); set({ muted }); },
    toggleCamera: () => { const camOff = !get().camOff; localStream?.getVideoTracks().forEach((t) => { t.enabled = !camOff; }); set({ camOff }); },

    /** Entry point for CALL_* events pushed by the server. */
    handle: (ev) => {
        const d = ev.data;
        const { call, status } = get();
        switch (ev.type) {
            case 'CALL_RINGING':
                if (status === 'outgoing' && call) set({ call: { ...call, callId: d.callId } });
                break;
            case 'CALL_INCOMING':
                if (status !== 'idle') return; // the server already answers "busy" to the caller
                set({ status: 'incoming', muted: false, camOff: false, call: { callId: d.callId, conversationId: d.conversationId, peerId: d.callerId, peerName: d.callerName, peerAvatar: d.callerAvatar, type: d.type, direction: 'incoming' } });
                startRingtone();
                break;
            case 'CALL_ACCEPTED':
                if (call && call.callId === d.callId && status === 'outgoing') {
                    set({ status: 'connecting' });
                    createPc(d.callId).then(async () => {
                        const offer = await pc.createOffer();
                        await pc.setLocalDescription(offer);
                        sendWs('/app/call.signal', { callId: d.callId, kind: 'offer', payload: JSON.stringify(pc.localDescription) });
                    }).catch(() => get().endCall(true));
                }
                break;
            case 'CALL_SIGNAL':
                if (call && call.callId === d.callId) signalQueue = signalQueue.then(() => onSignal(d)).catch(() => {});
                break;
            case 'CALL_ENDED':
                if (call && call.callId === d.callId) { const msg = d.status === 'MISSED' && call.direction === 'incoming' ? 'Missed call' : REASONS[d.reason] || 'Call ended'; teardown(); toast(msg, d.status === 'COMPLETED' ? 'info' : 'error'); }
                break;
            default: break;
        }
    },
}));

async function getMedia(type) {
    try { return await navigator.mediaDevices.getUserMedia({ audio: true, video: type === 'VIDEO' }); }
    catch (e) {
        if (e.name === 'NotAllowedError' || e.name === 'SecurityError') throw new Error('Microphone or camera permission was denied. Allow it in your browser to make calls.');
        if (e.name === 'NotFoundError' || e.name === 'OverconstrainedError') throw new Error('No microphone or camera was found');
        throw new Error('Could not use your microphone or camera');
    }
}

async function createPc(callId) {
    let iceServers = [{ urls: ['stun:stun.l.google.com:19302'] }];
    try { const { data } = await api.get('/calls/ice-config'); if (data.iceServers?.length) iceServers = data.iceServers; } catch { /* fall back to public STUN */ }
    pc = new RTCPeerConnection({ iceServers });
    pendingIce = [];
    localStream.getTracks().forEach((t) => pc.addTrack(t, localStream));
    pc.ontrack = (e) => useCallStore.setState({ remote: e.streams[0] || null });
    pc.onicecandidate = (e) => { if (e.candidate) sendWs('/app/call.signal', { callId, kind: 'ice', payload: JSON.stringify(e.candidate.toJSON ? e.candidate.toJSON() : e.candidate) }); };
    pc.onconnectionstatechange = () => {
        const s = pc?.connectionState;
        useCallStore.setState({ connState: s });
        clearTimeout(disconnTimer);
        if (s === 'connected') markActive();
        else if (s === 'disconnected') disconnTimer = setTimeout(() => useCallStore.getState().endCall(true), 10000); // network blip: give it 10 s to recover
        else if (s === 'failed') useCallStore.getState().endCall(true);
    };
}

async function flushIce() {
    const list = pendingIce; pendingIce = [];
    for (const c of list) { try { await pc.addIceCandidate(c); } catch { /* stale candidate */ } }
}

async function onSignal({ kind, payload }) {
    if (!pc) return;
    let data;
    try { data = JSON.parse(payload); } catch { return; }
    if (kind === 'offer') {
        await pc.setRemoteDescription(data); await flushIce();
        const answer = await pc.createAnswer(); await pc.setLocalDescription(answer);
        sendWs('/app/call.signal', { callId: useCallStore.getState().call.callId, kind: 'answer', payload: JSON.stringify(pc.localDescription) });
    } else if (kind === 'answer') {
        await pc.setRemoteDescription(data); await flushIce();
    } else if (kind === 'ice') {
        if (pc.remoteDescription) { try { await pc.addIceCandidate(data); } catch { /* ignore */ } } else pendingIce.push(data); // may arrive before the offer/answer
    }
}

function markActive() {
    if (useCallStore.getState().status === 'active') return;
    useCallStore.setState({ status: 'active', seconds: 0 });
    clearInterval(timer);
    timer = setInterval(() => useCallStore.setState((s) => ({ seconds: s.seconds + 1 })), 1000);
}

/** Releases microphone/camera, the peer connection, timers and the ringtone, and returns the UI to idle. */
export function teardown() {
    stopRingtone();
    clearInterval(timer); clearTimeout(disconnTimer); timer = null; disconnTimer = null;
    if (pc) { pc.ontrack = null; pc.onicecandidate = null; pc.onconnectionstatechange = null; try { pc.close(); } catch { /* already closed */ } pc = null; }
    if (localStream) { localStream.getTracks().forEach((t) => t.stop()); localStream = null; }
    pendingIce = []; signalQueue = Promise.resolve();
    useCallStore.setState({ ...initial });
}

function startRingtone() {
    try {
        const Ctx = window.AudioContext || window.webkitAudioContext;
        const ctx = new Ctx();
        const beep = () => { const o = ctx.createOscillator(); const g = ctx.createGain(); o.frequency.value = 440; g.gain.value = 0.05; o.connect(g); g.connect(ctx.destination); o.start(); setTimeout(() => { try { o.stop(); } catch { /* done */ } }, 400); };
        beep();
        ringtone = { ctx, iv: setInterval(beep, 2000) };
    } catch { /* no audio available: the screen still rings */ }
}
function stopRingtone() { if (!ringtone) return; clearInterval(ringtone.iv); try { ringtone.ctx.close(); } catch { /* ignore */ } ringtone = null; }

export const __test = { get pc() { return pc; }, get pendingIce() { return pendingIce; } };