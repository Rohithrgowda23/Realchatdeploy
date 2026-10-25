import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

let client = null;

export function connectWs({ token, onEvent, onPresence, onError, onConnect, onDisconnect }) {
  disconnectWs();
  client = new Client({
    webSocketFactory: () => new SockJS(import.meta.env.VITE_WS_URL || '/ws'),
    connectHeaders: { Authorization: `Bearer ${token}` }, // JWT travels in the STOMP frame, not the URL
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      client.subscribe('/user/queue/events', (f) => onEvent(JSON.parse(f.body)));
      client.subscribe('/user/queue/errors', (f) => onError(JSON.parse(f.body).message));
      client.subscribe('/topic/presence', (f) => onPresence(JSON.parse(f.body)));
      onConnect();
    },
    onWebSocketClose: () => { if (onDisconnect) onDisconnect(); },
    onStompError: (f) => {
      if (/unauthorized/i.test(f.headers?.message || '')) onError('Session expired, please sign in again', true);
    },
  });
  client.activate();
}

export function disconnectWs() {
  if (client) client.deactivate();
  client = null;
}

export function sendWs(destination, body) {
  if (!client || !client.connected) return false;
  client.publish({ destination, body: JSON.stringify(body) });
  return true;
}