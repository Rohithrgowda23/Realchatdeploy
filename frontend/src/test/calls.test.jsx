import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { sendWs } from '../ws';
import { useStore } from '../store';
import { useCallStore, teardown, __test } from '../callStore';
import { CallOverlay } from '../calls.jsx';
import { resetStore, toastTexts } from './helpers';

vi.mock('../api', () => ({ default: { get: vi.fn(() => Promise.resolve({ data: { iceServers: [{ urls: ['stun:test'] }] } })), post: vi.fn(), put: vi.fn(), delete: vi.fn() }, errMsg: (e) => e?.message }));
vi.mock('../ws', () => ({ sendWs: vi.fn(() => true) }));

let tracks;
let lastPc;

function installWebRtc({ deny = false, supported = true } = {}) {
    tracks = [];
    const mkTrack = (kind) => { const t = { kind, enabled: true, stop: vi.fn() }; tracks.push(t); return t; };
    Object.defineProperty(navigator, 'mediaDevices', {
        configurable: true,
        value: {
            getUserMedia: vi.fn(() => {
                if (deny) return Promise.reject(Object.assign(new Error('denied'), { name: 'NotAllowedError' }));
                const a = mkTrack('audio'); const v = mkTrack('video');
                return Promise.resolve({ getTracks: () => [a, v], getAudioTracks: () => [a], getVideoTracks: () => [v] });
            }),
        },
    });
    class FakePC {
        constructor(cfg) { lastPc = this; this.cfg = cfg; this.remoteDescription = null; this.localDescription = null; this.candidates = []; this.closed = false; this.senders = 0; }
        addTrack() { this.senders += 1; }
        async createOffer() { return { type: 'offer', sdp: 'offer-sdp' }; }
        async createAnswer() { return { type: 'answer', sdp: 'answer-sdp' }; }
        async setLocalDescription(d) { this.localDescription = d; }
        async setRemoteDescription(d) { this.remoteDescription = d; }
        async addIceCandidate(c) { if (!this.remoteDescription) throw new Error('no remote description'); this.candidates.push(c); }
        close() { this.closed = true; }
    }
    if (supported) window.RTCPeerConnection = FakePC; else delete window.RTCPeerConnection;
}

const store = () => useCallStore.getState();
const sent = (path, kind) => sendWs.mock.calls.filter(([p, b]) => p === path && (!kind || b.kind === kind));

beforeEach(() => { resetStore({ user: { id: 1 } }); installWebRtc(); lastPc = null; teardown(); sendWs.mockClear(); sendWs.mockReturnValue(true); });
afterEach(() => teardown());

describe('starting a call', () => {
    it('does nothing but explain when the browser has no WebRTC', async () => {
        installWebRtc({ supported: false });
        await store().startCall(7, { id: 2, name: 'Bob' }, 'AUDIO');
        expect(store().status).toBe('idle');
        expect(sendWs).not.toHaveBeenCalled();
        expect(toastTexts().some((t) => t.includes('not supported'))).toBe(true);
    });
    it('does not ring anyone when microphone permission is denied', async () => {
        installWebRtc({ deny: true });
        await store().startCall(7, { id: 2, name: 'Bob' }, 'AUDIO');
        expect(store().status).toBe('idle');
        expect(sendWs).not.toHaveBeenCalled();
        expect(toastTexts().some((t) => t.toLowerCase().includes('permission'))).toBe(true);
    });
    it('asks for the camera only on video calls', async () => {
        await store().startCall(7, { id: 2, name: 'Bob' }, 'AUDIO');
        expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({ audio: true, video: false });
        teardown();
        await store().startCall(7, { id: 2, name: 'Bob' }, 'VIDEO');
        expect(navigator.mediaDevices.getUserMedia).toHaveBeenLastCalledWith({ audio: true, video: true });
    });
    it('registers the call with the server and shows "calling"', async () => {
        await store().startCall(7, { id: 2, name: 'Bob' }, 'VIDEO');
        expect(sendWs).toHaveBeenCalledWith('/app/call.start', { conversationId: 7, type: 'VIDEO' });
        expect(store().status).toBe('outgoing');
    });
    it('gives up cleanly and frees the microphone if the connection is down', async () => {
        sendWs.mockReturnValue(false);
        await store().startCall(7, { id: 2, name: 'Bob' }, 'AUDIO');
        expect(store().status).toBe('idle');
        expect(tracks.every((t) => t.stop.mock.calls.length === 1)).toBe(true);
    });
    it('refuses a second call while one is in progress', async () => {
        await store().startCall(7, { id: 2, name: 'Bob' }, 'AUDIO');
        sendWs.mockClear();
        await store().startCall(8, { id: 3, name: 'Cy' }, 'AUDIO');
        expect(sendWs).not.toHaveBeenCalled();
    });
});

describe('outgoing call signalling', () => {
    async function dial() {
        await store().startCall(7, { id: 2, name: 'Bob' }, 'VIDEO');
        store().handle({ type: 'CALL_RINGING', data: { callId: 'c1' } });
    }
    it('sends an offer once the callee accepts', async () => {
        await dial();
        store().handle({ type: 'CALL_ACCEPTED', data: { callId: 'c1' } });
        await vi.waitFor(() => expect(sent('/app/call.signal', 'offer')).toHaveLength(1));
        expect(JSON.parse(sent('/app/call.signal', 'offer')[0][1].payload).sdp).toBe('offer-sdp');
        expect(lastPc.cfg.iceServers[0].urls[0]).toBe('stun:test');
        expect(store().status).toBe('connecting');
    });
    it('applies the answer and ICE candidates that arrive before it', async () => {
        await dial();
        store().handle({ type: 'CALL_ACCEPTED', data: { callId: 'c1' } });
        await vi.waitFor(() => expect(sent('/app/call.signal', 'offer')).toHaveLength(1));
        store().handle({ type: 'CALL_SIGNAL', data: { callId: 'c1', kind: 'ice', payload: JSON.stringify({ candidate: 'early' }) } });
        store().handle({ type: 'CALL_SIGNAL', data: { callId: 'c1', kind: 'answer', payload: JSON.stringify({ type: 'answer', sdp: 'a' }) } });
        await vi.waitFor(() => expect(lastPc.candidates).toEqual([{ candidate: 'early' }]));
        expect(lastPc.remoteDescription.sdp).toBe('a');
    });
    it('cancels a ringing call and releases the microphone and camera', async () => {
        await dial();
        store().endCall();
        expect(sent('/app/call.cancel')).toHaveLength(1);
        expect(store().status).toBe('idle');
        expect(tracks.every((t) => t.stop.mock.calls.length === 1)).toBe(true);
    });
    it('mute and camera toggles switch the tracks off and on', async () => {
        await dial();
        store().toggleMute(); store().toggleCamera();
        expect(tracks.find((t) => t.kind === 'audio').enabled).toBe(false);
        expect(tracks.find((t) => t.kind === 'video').enabled).toBe(false);
        store().toggleMute();
        expect(tracks.find((t) => t.kind === 'audio').enabled).toBe(true);
    });
    it('ignores signals for a different call', async () => {
        await dial();
        store().handle({ type: 'CALL_ACCEPTED', data: { callId: 'someone-elses' } });
        store().handle({ type: 'CALL_ENDED', data: { callId: 'someone-elses', reason: 'ended', status: 'COMPLETED' } });
        expect(store().status).toBe('outgoing');
        expect(sent('/app/call.signal', 'offer')).toHaveLength(0);
    });
    it('shows why the call ended and cleans up', async () => {
        await dial();
        store().handle({ type: 'CALL_ENDED', data: { callId: 'c1', reason: 'rejected', status: 'REJECTED' } });
        expect(store().status).toBe('idle');
        expect(toastTexts()).toContain('Call declined');
        expect(tracks.every((t) => t.stop.mock.calls.length === 1)).toBe(true);
    });
});

describe('incoming call', () => {
    const incoming = { callId: 'c9', conversationId: 7, callerId: 2, callerName: 'Bob', callerAvatar: '', type: 'AUDIO' };

    it('rings, and declining tells the server and frees everything', () => {
        store().handle({ type: 'CALL_INCOMING', data: incoming });
        expect(store().status).toBe('incoming');
        store().rejectCall();
        expect(sent('/app/call.reject')).toHaveLength(1);
        expect(store().status).toBe('idle');
    });
    it('is ignored while already in a call (the server answers busy)', async () => {
        await store().startCall(7, { id: 2, name: 'Bob' }, 'AUDIO');
        store().handle({ type: 'CALL_INCOMING', data: incoming });
        expect(store().call.direction).toBe('outgoing');
    });
    it('accepting prepares the connection first, then answers the offer', async () => {
        store().handle({ type: 'CALL_INCOMING', data: incoming });
        await store().acceptCall();
        expect(sent('/app/call.accept')).toHaveLength(1);
        expect(lastPc).not.toBeNull(); // peer connection exists before the offer can arrive
        store().handle({ type: 'CALL_SIGNAL', data: { callId: 'c9', kind: 'offer', payload: JSON.stringify({ type: 'offer', sdp: 'o' }) } });
        await vi.waitFor(() => expect(sent('/app/call.signal', 'answer')).toHaveLength(1));
        expect(lastPc.remoteDescription.sdp).toBe('o');
    });
    it('queues ICE candidates that arrive before the offer and applies them afterwards', async () => {
        store().handle({ type: 'CALL_INCOMING', data: incoming });
        await store().acceptCall();
        store().handle({ type: 'CALL_SIGNAL', data: { callId: 'c9', kind: 'ice', payload: JSON.stringify({ candidate: 'first' }) } });
        await vi.waitFor(() => expect(__test.pendingIce).toHaveLength(1));
        expect(lastPc.candidates).toHaveLength(0);
        store().handle({ type: 'CALL_SIGNAL', data: { callId: 'c9', kind: 'offer', payload: JSON.stringify({ type: 'offer', sdp: 'o' }) } });
        await vi.waitFor(() => expect(lastPc.candidates).toEqual([{ candidate: 'first' }]));
        expect(__test.pendingIce).toHaveLength(0);
    });
    it('declines automatically and explains when the microphone is blocked', async () => {
        installWebRtc({ deny: true });
        store().handle({ type: 'CALL_INCOMING', data: incoming });
        await store().acceptCall();
        expect(sent('/app/call.reject')).toHaveLength(1);
        expect(sent('/app/call.accept')).toHaveLength(0);
        expect(store().status).toBe('idle');
    });
    it('a missed call is announced as missed', () => {
        store().handle({ type: 'CALL_INCOMING', data: incoming });
        store().handle({ type: 'CALL_ENDED', data: { callId: 'c9', reason: 'cancelled', status: 'MISSED' } });
        expect(toastTexts()).toContain('Missed call');
        expect(store().status).toBe('idle');
    });
    it('malformed signals are dropped without breaking the call', async () => {
        store().handle({ type: 'CALL_INCOMING', data: incoming });
        await store().acceptCall();
        store().handle({ type: 'CALL_SIGNAL', data: { callId: 'c9', kind: 'offer', payload: 'not json' } });
        await Promise.resolve();
        expect(store().status).toBe('connecting');
        expect(sent('/app/call.signal', 'answer')).toHaveLength(0);
    });
});

describe('CallOverlay', () => {
    const initialState = useCallStore.getState();
    afterEach(() => useCallStore.setState(initialState, true));

    it('renders nothing when idle', () => {
        const { container } = render(<CallOverlay />);
        expect(container).toBeEmptyDOMElement();
    });
    it('shows accept and decline for an incoming call', async () => {
        const acceptCall = vi.fn(); const rejectCall = vi.fn();
        useCallStore.setState({ status: 'incoming', acceptCall, rejectCall, call: { callId: 'c', peerName: 'Bob', type: 'AUDIO', direction: 'incoming' } });
        render(<CallOverlay />);
        expect(screen.getByRole('dialog', { name: 'Incoming audio call' })).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Accept call' }));
        await userEvent.click(screen.getByRole('button', { name: 'Decline call' }));
        expect(acceptCall).toHaveBeenCalled(); expect(rejectCall).toHaveBeenCalled();
    });
    it('an active audio call shows the timer, mute and hang up but no camera control', async () => {
        const endCall = vi.fn(); const toggleMute = vi.fn();
        useCallStore.setState({ status: 'active', seconds: 75, endCall, toggleMute, call: { callId: 'c', peerName: 'Bob', type: 'AUDIO', direction: 'outgoing' } });
        render(<CallOverlay />);
        expect(screen.getByText('1:15')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /camera/i })).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Mute microphone' }));
        await userEvent.click(screen.getByRole('button', { name: 'End call' }));
        expect(toggleMute).toHaveBeenCalled(); expect(endCall).toHaveBeenCalledWith(false);
    });
    it('a video call offers a camera toggle and reports unstable connections', () => {
        useCallStore.setState({ status: 'active', seconds: 3, connState: 'disconnected', camOff: true, call: { callId: 'c', peerName: 'Bob', type: 'VIDEO', direction: 'outgoing' } });
        render(<CallOverlay />);
        expect(screen.getByRole('button', { name: 'Turn camera on' })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByText(/Connection unstable/)).toBeInTheDocument();
    });
});