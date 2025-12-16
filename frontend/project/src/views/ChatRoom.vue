<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { chatApi } from '../api/chat';

interface Message {
  type: 'ai' | 'user';
  content: string;
}

const route = useRoute();
const router = useRouter();
const roomId = ref<number>(Number(route.params.roomId));
const messages = ref<Message[]>([]);
const inputMessage = ref('');
const loading = ref(false);
const gameStarted = ref(false);
const gameEnded = ref(false);
const chatContainer = ref<HTMLElement | null>(null);
const historyRooms = ref<any[]>([]);

// —— Local storage helpers ——
const STORAGE_KEY = 'chat_history_v1';
type HistoryMap = Record<string, { messages: Message[]; updatedAt: number }>; // key: roomId

const loadAllHistory = (): HistoryMap => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as HistoryMap) : {};
  } catch {
    return {};
  }
};

const saveAllHistory = (map: HistoryMap) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(map));
  } catch (e) {
    // Swallow quota errors
    console.warn('保存历史失败:', e);
  }
};

const loadRoomMessages = (rid: number): Message[] => {
  const map = loadAllHistory();
  const entry = map[String(rid)];
  return entry?.messages ?? [];
};

const saveRoomMessages = (rid: number, msgs: Message[]) => {
  const map = loadAllHistory();
  map[String(rid)] = {
    messages: msgs,
    updatedAt: Date.now()
  };
  saveAllHistory(map);
};

const scrollToBottom = () => {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
    }
  });
};

const startGame = async () => {
  // Disable restart while a round is in progress; allow when ended
  if (loading.value) return;
  if (gameStarted.value && !gameEnded.value) {
    message.warning('游戏已经开始了');
    return;
  }

  try {
    loading.value = true;
    // starting a new round resets end state
    gameEnded.value = false;
    gameStarted.value = true;
    const response = await chatApi.sendMessage(roomId.value, '开始');

    messages.value.push({
      type: 'user',
      content: '开始'
    });

    messages.value.push({
      type: 'ai',
      content: response
    });

    if (response.includes('游戏已结束')) {
      gameEnded.value = true;
      gameStarted.value = false;
    }

    // persist
    saveRoomMessages(roomId.value, messages.value);
    await loadHistoryRooms();

    scrollToBottom();
  } catch (error) {
    message.error('发送消息失败，请检查后端服务是否启动');
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const endGame = async () => {
  if (gameEnded.value) {
    message.warning('游戏已经结束了');
    return;
  }

  try {
    loading.value = true;
    const response = await chatApi.sendMessage(roomId.value, '结束');

    messages.value.push({
      type: 'user',
      content: '结束'
    });

    messages.value.push({
      type: 'ai',
      content: response
    });

    gameEnded.value = true;
    gameStarted.value = false;
    // persist
    saveRoomMessages(roomId.value, messages.value);
    await loadHistoryRooms();
    scrollToBottom();
  } catch (error) {
    message.error('发送消息失败');
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const sendMessage = async () => {
  if (!inputMessage.value.trim()) {
    message.warning('请输入消息内容');
    return;
  }

  // 输入“开始”：仅在未进行中或已结束时才开启新一轮
  if (inputMessage.value.trim() === '开始') {
    if (gameStarted.value && !gameEnded.value) {
      message.warning('游戏正在进行中，请先结束本轮');
    } else {
      await startGame();
    }
    inputMessage.value = '';
    await nextTick(); // ensure UI clears immediately
    return;
  }

  if (!gameStarted.value) {
    message.warning('请先点击开始按钮或输入"开始"');
    return;
  }

  if (gameEnded.value) {
    message.warning('游戏已结束');
    return;
  }

  const userMessage = inputMessage.value.trim();
  inputMessage.value = '';
  await nextTick(); // immediate clear in UI before any async work

  try {
    loading.value = true;

    messages.value.push({
      type: 'user',
      content: userMessage
    });

    scrollToBottom();

    const response = await chatApi.sendMessage(roomId.value, userMessage);

    messages.value.push({
      type: 'ai',
      content: response
    });

    if (response.includes('游戏已结束')) {
      gameEnded.value = true;
      gameStarted.value = false;
    }

    // persist
    saveRoomMessages(roomId.value, messages.value);
    await loadHistoryRooms();

    scrollToBottom();
  } catch (error) {
    message.error('发送消息失败');
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const loadHistoryRooms = async () => {
  // Try backend first; fall back to local storage if API fails or returns empty
  try {
    const rooms = await chatApi.getRoomList();
    if (Array.isArray(rooms) && rooms.length > 0) {
      // normalize id field from various possible keys
      const apiRooms = rooms
        .map((r: any) => {
          const id = r?.id ?? r?.roomId ?? r?.roomID ?? r?.RoomId;
          return typeof id === 'string' || typeof id === 'number'
            ? { id: Number(id), updatedAt: undefined as number | undefined }
            : null;
        })
        .filter((x: any) => x && !Number.isNaN(x.id));

      // merge with local storage entries
      const map = loadAllHistory();
      const localEntries = Object.entries(map).map(([id, v]) => ({ id: Number(id), updatedAt: v.updatedAt }));
      const mergedMap = new Map<number, { id: number; updatedAt?: number }>();
      [...apiRooms, ...localEntries].forEach((r) => {
        if (r && !Number.isNaN(r.id)) {
          const prev = mergedMap.get(r.id);
          if (!prev || (r.updatedAt ?? 0) > (prev.updatedAt ?? 0)) {
            mergedMap.set(r.id, r);
          }
        }
      });
      historyRooms.value = Array.from(mergedMap.values()).sort((a, b) => (b.updatedAt ?? 0) - (a.updatedAt ?? 0));
      return;
    }
  } catch (error) {
    // ignore, fallback to local storage
  }
  const map = loadAllHistory();
  const entries = Object.entries(map)
    .map(([id, v]) => ({ id: Number(id), updatedAt: v.updatedAt }))
    .sort((a, b) => (b.updatedAt ?? 0) - (a.updatedAt ?? 0));
  historyRooms.value = entries;
};

// Infer game flags from message order: compare last '开始' (user) vs last '游戏已结束' (ai)
const loadRoom = (rid: number) => {
  messages.value = loadRoomMessages(rid);
  let lastStart = -1;
  let lastEnd = -1;
  messages.value.forEach((m, idx) => {
    if (m.type === 'user' && m.content.trim() === '开始') lastStart = idx;
    if (m.type === 'ai' && m.content.includes('游戏已结束')) lastEnd = idx;
  });
  if (lastEnd > lastStart) {
    gameEnded.value = true;
    gameStarted.value = false;
  } else if (lastStart > lastEnd) {
    gameEnded.value = false;
    gameStarted.value = true;
  } else {
    gameEnded.value = false;
    gameStarted.value = false;
  }
  scrollToBottom();
};

onMounted(() => {
  // Initialize current room
  loadRoom(roomId.value);
  loadHistoryRooms();
});

// Use keydown.enter in template to send; Shift+Enter inserts newline

const goToRoom = (id: number) => {
  if (typeof id !== 'number' || Number.isNaN(id)) {
    message.warning('该历史记录无效');
    return;
  }
  if (id === roomId.value) return;
  router.push({ path: `/chat/${id}` });
};

watch(
  () => route.params.roomId,
  (val) => {
    const rid = Number(val);
    if (!Number.isNaN(rid)) {
      roomId.value = rid;
      // Reset state and load
      loadRoom(roomId.value);
    }
  }
);
</script>

<template>
  <div class="chat-room">
    <div class="sidebar">
      <div class="sidebar-header">
        <h3>历史对话</h3>
      </div>
      <div class="history-list">
        <div
          v-for="room in historyRooms"
          :key="room.id"
          class="history-item"
          :class="{ active: room.id === roomId }"
          @click="goToRoom(room.id)"
        >
          对话 {{ room.id }}
        </div>
        <div v-if="historyRooms.length === 0" class="empty-history">
          暂无历史记录
        </div>
      </div>
    </div>

    <div class="main-content">
      <div class="chat-header">
        <h2>AI 脑筋急转弯</h2>
        <div class="room-id">房间号: {{ roomId }}</div>
      </div>

      <div ref="chatContainer" class="chat-messages">
        <div v-if="messages.length === 0" class="welcome-message">
          点击"开始"按钮或发送"开始"来开始游戏
        </div>
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="message-wrapper"
          :class="msg.type"
        >
          <div class="message-content">
            <div class="avatar">
              <span v-if="msg.type === 'ai'">🤖</span>
              <span v-else>👤</span>
            </div>
            <div class="message-bubble">
              {{ msg.content }}
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input-area">
        <div class="action-buttons">
          <a-button
            type="primary"
            :disabled="loading || (gameStarted && !gameEnded)"
            :loading="loading && !gameStarted"
            @click="startGame"
          >
            开始
          </a-button>
          <a-button
            danger
            :disabled="!gameStarted || gameEnded"
            :loading="loading && gameStarted && !gameEnded"
            @click="endGame"
          >
            结束游戏
          </a-button>
        </div>
        <div class="input-group">
          <a-textarea
            v-model:value="inputMessage"
            placeholder="请输入内容"
            :auto-size="{ minRows: 1, maxRows: 4 }"
            :disabled="loading"
            @keydown.enter.exact.prevent="sendMessage"
            allow-clear
          />
          <a-button
            type="primary"
            size="large"
            :loading="loading"
            @click="sendMessage"
          >
            发送
          </a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-room {
  display: flex;
  height: 100vh;
  background-color: #f5f5f5;
}

.sidebar {
  width: 250px;
  background-color: white;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid #e8e8e8;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 1.1rem;
  color: #333;
}

.history-list {
  flex: 1;
  overflow-y: auto;
}

.history-item {
  padding: 15px 20px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  transition: background-color 0.2s;
}

.history-item:hover {
  background-color: #f5f5f5;
}

.history-item.active {
  background-color: #e6f7ff;
  color: #1890ff;
}

.empty-history {
  padding: 20px;
  text-align: center;
  color: #999;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: #f5f5f5;
}

.chat-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 20px 30px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.chat-header h2 {
  margin: 0;
  font-size: 1.5rem;
  color: white;
}

.room-id {
  font-size: 1rem;
  opacity: 0.9;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome-message {
  text-align: center;
  color: #999;
  margin-top: 50px;
  font-size: 1.1rem;
}

.message-wrapper {
  display: flex;
  width: 100%;
}

.message-wrapper.ai {
  justify-content: flex-start;
}

.message-wrapper.user {
  justify-content: flex-end;
}

.message-content {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  max-width: 70%;
}

.message-wrapper.user .message-content {
  flex-direction: row-reverse;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  flex-shrink: 0;
  background-color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  word-wrap: break-word;
  white-space: pre-wrap;
  line-height: 1.5;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.message-wrapper.ai .message-bubble {
  background-color: white;
  color: #333;
}

.message-wrapper.user .message-bubble {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.chat-input-area {
  background-color: white;
  padding: 20px;
  border-top: 1px solid #e8e8e8;
}

.action-buttons {
  display: flex;
  gap: 10px;
  margin-bottom: 15px;
}

.input-group {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.input-group :deep(.ant-input) {
  flex: 1;
}

@media (max-width: 768px) {
  .sidebar {
    display: none;
  }

  .message-content {
    max-width: 85%;
  }

  .chat-header h2 {
    font-size: 1.2rem;
  }

  .room-id {
    font-size: 0.9rem;
  }
}
</style>
