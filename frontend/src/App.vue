<template>
  <div class="app-shell">
    <header class="top-bar">
      <span class="logo">DINO<span class="logo-accent">RUN</span></span>
      <span class="subtitle">press space or tap to jump</span>
    </header>

    <div class="game-layout">
      <!-- 게임 영역 -->
      <DinoGame
        :nickname="nickname"
        @game-over="onGameOver"
        @score-update="currentScore = $event"
      />

      <!-- 실시간 랭킹 사이드바 -->
      <RankingPanel
        :current-score="currentScore"
        :nickname="nickname"
      />
    </div>

    <!-- 닉네임 입력 모달 (최초 진입 시) -->
    <NicknameModal
      v-if="!nickname"
      @confirm="nickname = $event"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import DinoGame from './components/DinoGame.vue'
import RankingPanel from './components/RankingPanel.vue'
import NicknameModal from './components/NicknameModal.vue'

const nickname = ref('')
const currentScore = ref(0)

function onGameOver(score: number) {
  currentScore.value = score
}
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Press+Start+2P&family=Share+Tech+Mono&display=swap');

*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

:root {
  --bg:       #080b10;
  --bg2:      #0d1117;
  --bg3:      #151b23;
  --border:   #1e2a38;
  --border2:  #2a3d52;
  --neon:     #00ffcc;
  --neon2:    #00aaff;
  --warn:     #ffcc00;
  --danger:   #ff3355;
  --text:     #c8dce8;
  --text2:    #4a6070;
  --pixel:    'Press Start 2P', monospace;
  --mono:     'Share Tech Mono', monospace;
}

html, body {
  height: 100%;
  background: var(--bg);
  color: var(--text);
  font-family: var(--mono);
  overflow: hidden;
}

.app-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 28px;
  border-bottom: 1px solid var(--border);
  background: var(--bg2);
  flex-shrink: 0;
}

.logo {
  font-family: var(--pixel);
  font-size: 14px;
  color: var(--text);
  letter-spacing: 0.1em;
}
.logo-accent { color: var(--neon); }

.subtitle {
  font-size: 11px;
  color: var(--text2);
  letter-spacing: 0.08em;
}

.game-layout {
  display: grid;
  grid-template-columns: 1fr 260px;
  flex: 1;
  overflow: hidden;
}
</style>