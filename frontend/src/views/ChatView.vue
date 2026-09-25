<template>
  <div class="chat">
    <aside>
      <h2>Conversations</h2>
      <ul>
        <li
          v-for="conversation in conversations"
          :key="conversation.conversationId"
          :class="{ on: conversation.conversationId === activeId }"
          @click="open(conversation)"
        >
          <strong>{{ conversation.senderName || 'Conversation' }}</strong>
          <p>{{ conversation.content }}</p>
        </li>
      </ul>
      <p v-if="!conversations.length" class="empty">No messages yet.</p>
    </aside>

    <section class="thread">
      <div v-if="!activeId" class="empty pad">Pick a conversation.</div>
      <template v-else>
        <div ref="scroller" class="messages">
          <div
            v-for="message in messages"
            :key="message.id"
            class="bubble"
            :class="{ mine: message.senderId === myId }"
          >
            <p>{{ message.content }}</p>
            <time>{{ time(message.createdAt) }}</time>
          </div>
        </div>
        <form @submit.prevent="send">
          <input v-model="draft" placeholder="Write a message" maxlength="2000" />
          <button :disabled="!draft.trim()">Send</button>
        </form>
      </template>
    </section>
  </div>
</template>

<script>
import { chat } from '@/api/marketplaceService';
import { useAuthStore } from '@/store/auth';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

export default {
  name: 'ChatView',
  data() {
    return {
      conversations: [],
      messages: [],
      activeId: null,
      withUserId: null,
      productId: null,
      draft: '',
      stomp: null,
    };
  },
  computed: {
    myId() {
      return useAuthStore().user?.userId;
    },
  },
  async created() {
    await this.loadConversations();
    if (this.$route.query.withUserId) {
      this.open({
        conversationId: 'pending',
        senderId: Number(this.$route.query.withUserId),
        receiverId: this.myId,
        productId: this.$route.query.productId ? Number(this.$route.query.productId) : null,
      });
    }
    this.connect();
  },
  beforeUnmount() {
    if (this.stomp) this.stomp.deactivate();
  },
  methods: {
    time(value) {
      return value ? new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
    },
    async loadConversations() {
      const { data } = await chat.conversations();
      this.conversations = data.conversations;
    },
    async open(conversation) {
      // The other end of the thread, whichever side of it we are on.
      this.withUserId = conversation.senderId === this.myId
        ? conversation.receiverId
        : conversation.senderId;
      this.productId = conversation.productId;
      this.activeId = conversation.conversationId;
      const { data } = await chat.history(this.withUserId, this.productId);
      this.messages = data;
      this.$nextTick(this.scrollToEnd);
    },
    async send() {
      const content = this.draft.trim();
      this.draft = '';
      const { data } = await chat.send(this.withUserId, content, this.productId);
      this.messages.push(data);
      this.activeId = data.conversationId;
      this.$nextTick(this.scrollToEnd);
    },
    connect() {
      const token = localStorage.getItem('jwt_token');
      this.stomp = new Client({
        webSocketFactory: () => new SockJS(`${process.env.VUE_APP_WS_URL || ''}/ws`),
        connectHeaders: { Authorization: `Bearer ${token}` },
        reconnectDelay: 5000,
        onConnect: () => {
          this.stomp.subscribe('/user/queue/messages', (frame) => {
            const message = JSON.parse(frame.body);
            if (message.conversationId === this.activeId) {
              this.messages.push(message);
              this.$nextTick(this.scrollToEnd);
            }
            this.loadConversations();
          });
        },
      });
      this.stomp.activate();
    },
    scrollToEnd() {
      const el = this.$refs.scroller;
      if (el) el.scrollTop = el.scrollHeight;
    },
  },
};
</script>

<style scoped>
.chat { display: flex; gap: 16px; max-width: 980px; margin: 0 auto; padding: 24px 16px;
        height: calc(100vh - 140px); }
aside { width: 260px; border: 1px solid #e3e6ec; border-radius: 8px; overflow-y: auto; }
aside h2 { font-size: 14px; padding: 12px 14px; margin: 0; border-bottom: 1px solid #eef0f4; }
aside ul { list-style: none; margin: 0; padding: 0; }
aside li { padding: 11px 14px; border-bottom: 1px solid #f2f3f6; cursor: pointer; }
aside li.on { background: #e3edf4; }
aside strong { font-size: 14px; display: block; }
aside p { font-size: 12px; color: #6b7280; margin: 3px 0 0; overflow: hidden;
          text-overflow: ellipsis; white-space: nowrap; }
.thread { flex: 1; display: flex; flex-direction: column; border: 1px solid #e3e6ec; border-radius: 8px; }
.messages { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 8px; }
.bubble { max-width: 70%; background: #f2f3f6; padding: 8px 12px; border-radius: 10px; align-self: flex-start; }
.bubble.mine { background: #2f5d7c; color: #fff; align-self: flex-end; }
.bubble p { margin: 0; font-size: 14px; }
.bubble time { font-size: 11px; opacity: .7; }
form { display: flex; gap: 8px; padding: 12px; border-top: 1px solid #eef0f4; }
form input { flex: 1; padding: 9px 11px; border: 1px solid #d5d9e0; border-radius: 6px; font: inherit; }
form button { padding: 9px 18px; border: 0; border-radius: 6px; background: #2f5d7c; color: #fff; cursor: pointer; }
form button:disabled { opacity: .5; cursor: default; }
.empty { color: #6b7280; font-size: 14px; }
.pad { padding: 20px; }
</style>
