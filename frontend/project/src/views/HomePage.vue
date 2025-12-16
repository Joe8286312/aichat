<script setup lang="ts">
import { useRouter } from 'vue-router';

const router = useRouter();

const startGame = () => {
  const roomId = Math.floor(Math.random() * 1000000);
  router.push(`/chat/${roomId}`);
};
</script>

<template>
  <div class="home-page">
    <h1 class="title">AI 脑筋急转弯</h1>
    <a-button
      type="primary"
      size="large"
      class="start-button"
      @click="startGame"
    >
      开始游戏
    </a-button>
  </div>
</template>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.title {
  font-size: 3.5rem;
  color: white;
  margin-bottom: 3rem;
  font-weight: 600;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.2);
}

.start-button {
  padding: 2rem 4rem;
  height: auto;
  font-size: 1.5rem;
  border-radius: 50px;
  border: none;
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.2);
  transition: all 0.3s ease;
}

.start-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.3);
}

@media (max-width: 768px) {
  .title {
    font-size: 2.5rem;
  }

  .start-button {
    padding: 1.5rem 3rem;
    font-size: 1.2rem;
  }
}
</style>
