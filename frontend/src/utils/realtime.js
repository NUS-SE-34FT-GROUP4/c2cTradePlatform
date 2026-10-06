import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import emitter from '@/eventBus';

// One STOMP connection for the whole app. ChatView and the notification
// toasts in App.vue both ride on it instead of each opening their own.
let client = null;
let readyCallbacks = [];

function build() {
  const token = localStorage.getItem('jwt_token');
  const c = new Client({
    webSocketFactory: () => new SockJS(`${process.env.VUE_APP_WS_URL || ''}/ws`),
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    reconnectDelay: 5000,
    onConnect: () => {
      // Both queues feed the same bus. App.vue filters system messages by
      // their flag; ChatView matches the active conversation id.
      c.subscribe('/user/queue/messages', (frame) => {
        emitter.emit('chat-message', JSON.parse(frame.body));
      });
      c.subscribe('/user/queue/system', (frame) => {
        emitter.emit('chat-message', JSON.parse(frame.body));
      });
      const pending = readyCallbacks;
      readyCallbacks = [];
      pending.forEach((cb) => cb(c));
    },
  });
  return c;
}

export default {
  current() {
    return client;
  },
  /** Connect once per login; later callers reuse the same client. */
  ensure() {
    if (!client) {
      client = build();
      client.activate();
    }
    return client;
  },
  /** Run cb as soon as the connection is live (immediately if it already is). */
  whenConnected(cb) {
    if (client && client.connected) {
      cb(client);
    } else {
      this.ensure();
      readyCallbacks.push(cb);
    }
  },
  disconnect() {
    if (client) {
      client.deactivate();
      client = null;
      readyCallbacks = [];
    }
  },
};
