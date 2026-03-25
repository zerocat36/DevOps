<template>
  <div class="game-wrap" @click="handleInput" @touchstart.prevent="handleInput">
    <canvas ref="canvas" class="game-canvas" />

    <!-- 점수 오버레이 -->
    <div class="score-overlay">
      <span class="score-label">SCORE</span>
      <span class="score-value">{{ score.toString().padStart(6, '0') }}</span>
    </div>
    <div class="hi-overlay">
      <span class="score-label">HI</span>
      <span class="score-value hi">{{ hiScore.toString().padStart(6, '0') }}</span>
    </div>

    <!-- 게임 오버 패널 -->
    <Transition name="fade">
      <div class="over-panel" v-if="phase === 'over'">
        <p class="over-title">GAME OVER</p>
        <p class="over-score">{{ score.toString().padStart(6, '0') }}</p>
        <p class="over-hint">press space / tap to restart</p>
      </div>
    </Transition>

    <!-- 시작 대기 패널 -->
    <Transition name="fade">
      <div class="over-panel" v-if="phase === 'idle'">
        <p class="over-title">READY?</p>
        <p class="over-hint">press space / tap to start</p>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const props = defineProps<{ nickname: string }>()
const emit = defineEmits<{
  'game-over': [score: number]
  'score-update': [score: number]
}>()

// ── Canvas & 상태 ────────────────────────────────────────────────
const canvas = ref<HTMLCanvasElement | null>(null)
let ctx: CanvasRenderingContext2D | null = null

const score    = ref(0)
const hiScore  = ref(Number(localStorage.getItem('dino-hi') || 0))
const phase    = ref<'idle' | 'running' | 'over'>('idle')

// ── 게임 오브젝트 ────────────────────────────────────────────────
const GROUND_Y   = 200   // 지면 Y (공룡 발 위치)
const FEET_LINE  = GROUND_Y + 36 // 발·장애물 하단이 맞닿는 선 (draw와 동일)
const DINO_X     = 80    // 공룡 고정 X
const GRAVITY    = 0.6
const JUMP_VEL   = -13

/** drawDino()와 동일한 축 정렬 AABB (머리·몸·꼬리·다리 포함) */
function dinoHitbox(y: number, grounded: boolean): { l: number; r: number; t: number; b: number } {
  const legBottom = grounded ? y + 28 + 10 : y + 28 + 8
  return {
    l: DINO_X,
    r: DINO_X + 32,
    t: y - 10,
    b: legBottom,
  }
}

/** draw()의 장애물 3블록과 동일한 히트박스 */
function obstacleHitboxes(o: Obstacle): Array<{ l: number; r: number; t: number; b: number }> {
  const oy = FEET_LINE - o.h
  const h = o.h
  return [
    { l: o.x, r: o.x + o.w, t: oy, b: FEET_LINE },
    { l: o.x - 5, r: o.x + 1, t: oy + 8, b: oy + 8 + h * 0.5 },
    { l: o.x + o.w - 1, r: o.x + o.w + 5, t: oy + 12, b: oy + 12 + h * 0.4 },
  ]
}

function aabbOverlap(
  a: { l: number; r: number; t: number; b: number },
  b: { l: number; r: number; t: number; b: number },
): boolean {
  return a.l < b.r && a.r > b.l && a.t < b.b && a.b > b.t
}

let dinoY   = GROUND_Y
let dinoVY  = 0
let onGround = true

interface Obstacle { x: number; w: number; h: number }
let obstacles: Obstacle[] = []
let obstacleTimer = 0
let obstacleInterval = 90  // 프레임 간격 (점점 줄어듦)

let frameId = 0
let tick = 0
let speed = 5   // 초기 장애물 속도

// ── 색상 팔레트 ──────────────────────────────────────────────────
const C = {
  sky:      '#080b10',
  ground:   '#1e2a38',
  dino:     '#00ffcc',
  obstacle: '#ff3355',
  text:     '#4a6070',
  star:     '#2a3d52',
}

// ── 별 배경 (정적) ───────────────────────────────────────────────
const STARS = Array.from({ length: 60 }, () => ({
  x: Math.random() * 800,
  y: Math.random() * 210,
  r: Math.random() * 1.5 + 0.3,
  a: Math.random(),
}))

// ── 입력 처리 ────────────────────────────────────────────────────
function handleInput() {
  if (phase.value === 'idle') { startGame(); return }
  if (phase.value === 'over') { resetGame(); return }
  if (onGround) {
    dinoVY = JUMP_VEL
    onGround = false
  }
}

function onKeyDown(e: KeyboardEvent) {
  if (e.code === 'Space' || e.code === 'ArrowUp') {
    e.preventDefault()
    handleInput()
  }
}

// ── 게임 시작 / 리셋 ─────────────────────────────────────────────
function startGame() {
  phase.value = 'running'
  loop()
}

function resetGame() {
  score.value = 0
  tick = 0
  speed = 5
  obstacleInterval = 90
  dinoY = GROUND_Y
  dinoVY = 0
  onGround = true
  obstacles = []
  obstacleTimer = 0
  phase.value = 'running'
  loop()
}

// ── 메인 루프 ────────────────────────────────────────────────────
function loop() {
  if (phase.value !== 'running') return
  frameId = requestAnimationFrame(loop)
  tick++

  // 점수 & 속도 증가
  if (tick % 6 === 0) {
    score.value++
    emit('score-update', score.value)
  }
  // 매 400점마다 속도 +0.4, 간격 -4프레임
  if (score.value > 0 && score.value % 400 === 0 && tick % 6 === 0) {
    speed = Math.min(speed + 0.4, 18)
    obstacleInterval = Math.max(obstacleInterval - 4, 40)
  }

  // 공룡 물리
  if (!onGround) {
    dinoVY += GRAVITY
    dinoY += dinoVY
    if (dinoY >= GROUND_Y) {
      dinoY = GROUND_Y
      dinoVY = 0
      onGround = true
    }
  }

  // 장애물 생성
  obstacleTimer++
  if (obstacleTimer >= obstacleInterval) {
    obstacleTimer = 0
    const h = 20 + Math.random() * 28
    const w = 12 + Math.random() * 14
    obstacles.push({ x: 820, w, h })
    obstacleInterval = 60 + Math.random() * 50
  }

  // 장애물 이동 & 충돌 검사 (그리기 좌표와 동일한 기준)
  obstacles = obstacles.filter(o => o.x + o.w > 0)
  const dBox = dinoHitbox(dinoY, onGround)
  for (const o of obstacles) {
    o.x -= speed
    for (const hBox of obstacleHitboxes(o)) {
      if (aabbOverlap(dBox, hBox)) {
        gameOver()
        return
      }
    }
  }

  draw()
}

// ── 게임 오버 처리 ───────────────────────────────────────────────
async function gameOver() {
  cancelAnimationFrame(frameId)
  phase.value = 'over'

  if (score.value > hiScore.value) {
    hiScore.value = score.value
    localStorage.setItem('dino-hi', String(score.value))
  }

  emit('game-over', score.value)

  // 점수 API 전송 (닉네임 있을 때만)
  if (props.nickname && score.value > 0) {
    try {
      await fetch('/api/scores', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nickname: props.nickname, score: score.value }),
      })
    } catch { /* 오프라인 환경에서도 게임은 동작해야 함 */ }
  }
}

// ── 렌더링 ───────────────────────────────────────────────────────
function draw() {
  if (!ctx || !canvas.value) return
  const W = canvas.value.width
  const H = canvas.value.height

  // 배경
  ctx.fillStyle = C.sky
  ctx.fillRect(0, 0, W, H)

  // 별
  for (const s of STARS) {
    ctx.globalAlpha = s.a * 0.6
    ctx.fillStyle = C.star
    ctx.beginPath()
    ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2)
    ctx.fill()
  }
  ctx.globalAlpha = 1

  // 지면 선
  ctx.strokeStyle = C.ground
  ctx.lineWidth = 1.5
  ctx.beginPath()
  ctx.moveTo(0, FEET_LINE)
  ctx.lineTo(W, FEET_LINE)
  ctx.stroke()

  // 공룡 (픽셀 스타일 사각형 조합)
  ctx.fillStyle = C.dino
  drawDino(DINO_X, dinoY)

  // 장애물
  ctx.fillStyle = C.obstacle
  for (const o of obstacles) {
    const oy = FEET_LINE - o.h
    // 선인장 느낌의 픽셀 블록
    ctx.fillRect(o.x,     oy,      o.w,     o.h)
    ctx.fillRect(o.x - 5, oy + 8,  6,       o.h * 0.5)
    ctx.fillRect(o.x + o.w - 1, oy + 12, 6, o.h * 0.4)
  }

  // 지면 점선
  ctx.strokeStyle = C.text
  ctx.lineWidth = 0.5
  ctx.setLineDash([4, 12])
  ctx.beginPath()
  ctx.moveTo(0, FEET_LINE + 1)
  ctx.lineTo(W, FEET_LINE + 1)
  ctx.stroke()
  ctx.setLineDash([])
}

function drawDino(x: number, y: number) {
  if (!ctx) return
  // 몸통
  ctx.fillRect(x + 4,  y,      24, 28)
  // 머리
  ctx.fillRect(x + 16, y - 10, 16, 14)
  // 눈
  ctx.fillStyle = '#080b10'
  ctx.fillRect(x + 28, y - 8,  4,  4)
  ctx.fillStyle = C.dino
  // 다리 (달리는 애니메이션)
  const legPhase = Math.floor(tick / 6) % 2
  if (onGround) {
    ctx.fillRect(x + 8,  y + 28, 8, legPhase === 0 ? 10 : 6)
    ctx.fillRect(x + 18, y + 28, 8, legPhase === 0 ? 6  : 10)
  } else {
    ctx.fillRect(x + 8,  y + 28, 8, 8)
    ctx.fillRect(x + 18, y + 28, 8, 8)
  }
  // 꼬리
  ctx.fillRect(x,      y + 10, 6, 8)
}

// ── 캔버스 크기 맞추기 ───────────────────────────────────────────
function resize() {
  if (!canvas.value) return
  const wrap = canvas.value.parentElement!
  canvas.value.width  = wrap.clientWidth
  canvas.value.height = wrap.clientHeight
}

onMounted(() => {
  ctx = canvas.value!.getContext('2d')!
  resize()
  window.addEventListener('resize', resize)
  window.addEventListener('keydown', onKeyDown)
  draw()  // idle 상태 첫 프레임
})

onUnmounted(() => {
  cancelAnimationFrame(frameId)
  window.removeEventListener('resize', resize)
  window.removeEventListener('keydown', onKeyDown)
})
</script>

<style scoped>
.game-wrap {
  position: relative;
  overflow: hidden;
  background: #080b10;
  border-right: 1px solid var(--border);
}

.game-canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.score-overlay {
  position: absolute;
  top: 16px;
  right: 180px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}
.hi-overlay {
  position: absolute;
  top: 16px;
  right: 28px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.score-label {
  font-family: var(--pixel);
  font-size: 8px;
  color: var(--text2);
  letter-spacing: 0.1em;
}
.score-value {
  font-family: var(--pixel);
  font-size: 14px;
  color: var(--text);
  letter-spacing: 0.12em;
}
.score-value.hi { color: var(--warn); }

.over-panel {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  background: rgba(8, 11, 16, 0.75);
}

.over-title {
  font-family: var(--pixel);
  font-size: 22px;
  color: var(--neon);
  letter-spacing: 0.15em;
}
.over-score {
  font-family: var(--pixel);
  font-size: 18px;
  color: var(--warn);
}
.over-hint {
  font-size: 12px;
  color: var(--text2);
  letter-spacing: 0.08em;
}

.fade-enter-active, .fade-leave-active { transition: opacity .25s; }
.fade-enter-from, .fade-leave-to       { opacity: 0; }
</style>