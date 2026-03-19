<template>
  <aside class="ranking-panel">
    <div class="panel-header">
      <span class="panel-title">RANKING</span>
      <span class="live-dot" :class="{ connected }"></span>
    </div>

    <!-- 내 현재 점수 -->
    <div class="my-score-box" v-if="nickname">
      <span class="my-label">{{ nickname }}</span>
      <span class="my-value">{{ currentScore.toString().padStart(6, '0') }}</span>
    </div>

    <!-- 랭킹 리스트 -->
    <ol class="rank-list">
      <TransitionGroup name="rank">
        <li
          v-for="(entry, i) in ranking"
          :key="entry.nickname"
          class="rank-item"
          :class="{
            gold:   i === 0,
            silver: i === 1,
            bronze: i === 2,
            me:     entry.nickname === nickname,
          }"
        >
          <span class="rank-num">{{ (i + 1).toString().padStart(2, '0') }}</span>
          <span class="rank-name">{{ entry.nickname }}</span>
          <span class="rank-score">{{ entry.score.toString().padStart(6, '0') }}</span>
        </li>
      </TransitionGroup>
    </ol>

    <div class="empty" v-if="!ranking.length">
      <span>no scores yet</span>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const props = defineProps<{
  currentScore: number
  nickname: string
}>()

interface RankEntry { nickname: string; score: number }

const ranking   = ref<RankEntry[]>([])
const connected = ref(false)
let sse: EventSource | null = null

// SSE 연결 — 서버가 랭킹 변경 시 push
function connectSSE() {
  sse = new EventSource('/api/ranking/stream')

  sse.onopen = () => { connected.value = true }

  sse.onmessage = (e) => {
    try {
      ranking.value = JSON.parse(e.data)
    } catch { /* 파싱 실패 무시 */ }
  }

  sse.onerror = () => {
    connected.value = false
    // 5초 후 재연결
    setTimeout(connectSSE, 5000)
  }
}

// 초기 랭킹 한 번 조회 (SSE 연결 전 공백 방지)
async function fetchRanking() {
  try {
    const res = await fetch('/api/ranking?limit=10')
    ranking.value = await res.json()
  } catch { /* 오프라인 허용 */ }
}

onMounted(() => {
  fetchRanking()
  connectSSE()
})

onUnmounted(() => {
  sse?.close()
})
</script>

<style scoped>
.ranking-panel {
  display: flex;
  flex-direction: column;
  background: var(--bg2);
  border-left: 1px solid var(--border);
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px 12px;
  border-bottom: 1px solid var(--border);
}
.panel-title {
  font-family: var(--pixel);
  font-size: 10px;
  color: var(--neon);
  letter-spacing: 0.12em;
}
.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text2);
  transition: background .3s;
}
.live-dot.connected {
  background: var(--neon);
  box-shadow: 0 0 6px var(--neon);
  animation: blink 2s ease-in-out infinite;
}
@keyframes blink { 0%,100%{opacity:1} 50%{opacity:.4} }

.my-score-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 18px;
  border-bottom: 1px solid var(--border);
  background: rgba(0, 255, 204, 0.04);
}
.my-label {
  font-size: 11px;
  color: var(--neon);
  font-family: var(--mono);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.my-value {
  font-family: var(--pixel);
  font-size: 11px;
  color: var(--warn);
}

.rank-list {
  list-style: none;
  overflow-y: auto;
  flex: 1;
}
.rank-list::-webkit-scrollbar { width: 3px; }
.rank-list::-webkit-scrollbar-thumb { background: var(--border2); }

.rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  border-bottom: 1px solid var(--border);
  transition: background .2s;
  font-size: 11px;
}
.rank-item:hover { background: var(--bg3); }

.rank-item.me {
  background: rgba(0, 255, 204, 0.05);
  border-left: 2px solid var(--neon);
}

.rank-num {
  font-family: var(--pixel);
  font-size: 9px;
  color: var(--text2);
  flex-shrink: 0;
}
.rank-item.gold   .rank-num { color: #ffd700; }
.rank-item.silver .rank-num { color: #b0c4d8; }
.rank-item.bronze .rank-num { color: #cd7f32; }

.rank-name {
  flex: 1;
  color: var(--text);
  font-family: var(--mono);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank-item.gold   .rank-name { color: #ffd700; }
.rank-item.me     .rank-name { color: var(--neon); }

.rank-score {
  font-family: var(--pixel);
  font-size: 9px;
  color: var(--text2);
  flex-shrink: 0;
}
.rank-item.gold .rank-score { color: #ffd700; }

.empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: var(--text2);
}

/* TransitionGroup 애니메이션 */
.rank-move        { transition: transform .4s ease; }
.rank-enter-active { transition: all .3s ease; }
.rank-leave-active { transition: all .2s ease; position: absolute; }
.rank-enter-from  { opacity: 0; transform: translateX(20px); }
.rank-leave-to    { opacity: 0; transform: translateX(-20px); }
</style>