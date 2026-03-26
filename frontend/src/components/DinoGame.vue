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

const SPEED_START = 5
const SPEED_MAX = 20
const SPEED_PER_SCORE = 0.032
const SPAWN_X = 840

type Obstacle =
  | { kind: 'cactus_small'; x: number; stemW: number; stemH: number }
  | { kind: 'cactus_double'; x: number; stemW: number; gap: number; h0: number; h1: number }
  | { kind: 'cactus_triple'; x: number; stemW: number; gap: number; h0: number; h1: number; h2: number }
  | { kind: 'ptero'; x: number; y: number; flapPhase: number }

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

function pteroFlapOffset(flapPhase: number): number {
  return Math.round(Math.sin((tick + flapPhase) / 5) * 4)
}

function pteroHitboxesAt(x: number, y: number, f: number): Array<{ l: number; r: number; t: number; b: number }> {
  return [
    { l: x + 19, r: x + 34, t: y + 1, b: y + 11 },
    { l: x + 7, r: x + 31, t: y + 5, b: y + 21 },
    { l: x + 1, r: x + 17, t: y + 7 + f, b: y + 19 + f },
    { l: x + 26, r: x + 44, t: y + 7 + f, b: y + 19 + f },
  ]
}

/** 한 줄기 선인장(본체 + 좌우 돌기) 히트박스 — drawCactusStem과 동일 */
function cactusStemHitboxes(left: number, stemW: number, stemH: number): Array<{ l: number; r: number; t: number; b: number }> {
  const oy = FEET_LINE - stemH
  const h = stemH
  const armH = Math.floor(h * 0.5)
  const armR = Math.floor(h * 0.4)
  return [
    { l: left, r: left + stemW, t: oy, b: FEET_LINE },
    { l: left - 5, r: left + 1, t: oy + 8, b: oy + 8 + armH },
    { l: left + stemW - 1, r: left + stemW + 5, t: oy + 12, b: oy + 12 + armR },
  ]
}

function obstacleHitboxes(o: Obstacle): Array<{ l: number; r: number; t: number; b: number }> {
  switch (o.kind) {
    case 'cactus_small':
      return cactusStemHitboxes(o.x, o.stemW, o.stemH)
    case 'cactus_double':
      return [
        ...cactusStemHitboxes(o.x, o.stemW, o.h0),
        ...cactusStemHitboxes(o.x + o.gap, o.stemW, o.h1),
      ]
    case 'cactus_triple':
      return [
        ...cactusStemHitboxes(o.x, o.stemW, o.h0),
        ...cactusStemHitboxes(o.x + o.gap, o.stemW, o.h1),
        ...cactusStemHitboxes(o.x + o.gap * 2, o.stemW, o.h2),
      ]
    case 'ptero': {
      const f = pteroFlapOffset(o.flapPhase)
      return pteroHitboxesAt(o.x, o.y, f)
    }
  }
}

function obstacleRightEdge(o: Obstacle): number {
  switch (o.kind) {
    case 'cactus_small':
      return o.x + o.stemW + 5
    case 'cactus_double':
      return o.x + o.gap + o.stemW + 5
    case 'cactus_triple':
      return o.x + o.gap * 2 + o.stemW + 5
    case 'ptero':
      return o.x + 44
  }
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

let obstacles: Obstacle[] = []
let obstacleTimer = 0
let obstacleInterval = 90

let frameId = 0
let tick = 0
let speed = SPEED_START

function speedFromScore(s: number): number {
  return Math.min(SPEED_START + s * SPEED_PER_SCORE, SPEED_MAX)
}

/** 다음 스폰까지 프레임 수 (점수가 높을수록 짧아짐) */
function nextSpawnIntervalFrames(s: number): number {
  const base = Math.max(30, 92 - s * 0.11)
  return Math.floor(base * (0.72 + Math.random() * 0.55))
}

function randomStemW(): number {
  return 10 + Math.floor(Math.random() * 5)
}

function spawnObstacle(currentScore: number): Obstacle {
  const r = Math.random()
  const pteroUnlocked = currentScore >= 160
  const pteroWeight = pteroUnlocked ? Math.min(0.32, 0.1 + currentScore / 2200) : 0

  if (pteroUnlocked && r < pteroWeight) {
    const tier = Math.random()
    let y: number
    if (tier < 0.38) {
      y = 86 + Math.random() * 18
    }
    else if (tier < 0.72) {
      y = 136 + Math.random() * 22
    }
    else {
      y = 172 + Math.random() * 16
    }
    return { kind: 'ptero', x: SPAWN_X, y, flapPhase: Math.floor(Math.random() * 60) }
  }

  const cactusRoll = (r - pteroWeight) / (1 - pteroWeight)
  const stemW = randomStemW()
  const gap = 18 + Math.floor(Math.random() * 8)

  if (currentScore < 120) {
    if (cactusRoll < 0.62) {
      const stemH = 22 + Math.floor(Math.random() * 18)
      return { kind: 'cactus_small', x: SPAWN_X, stemW, stemH }
    }
    const h0 = 24 + Math.floor(Math.random() * 16)
    const h1 = 22 + Math.floor(Math.random() * 20)
    return { kind: 'cactus_double', x: SPAWN_X, stemW, gap, h0, h1 }
  }

  if (cactusRoll < 0.28) {
    const stemH = 20 + Math.floor(Math.random() * 22)
    return { kind: 'cactus_small', x: SPAWN_X, stemW, stemH }
  }
  if (cactusRoll < 0.62) {
    const h0 = 22 + Math.floor(Math.random() * 18)
    const h1 = 26 + Math.floor(Math.random() * 18)
    return { kind: 'cactus_double', x: SPAWN_X, stemW, gap, h0, h1 }
  }
  const h0 = 20 + Math.floor(Math.random() * 14)
  const h1 = 28 + Math.floor(Math.random() * 16)
  const h2 = 22 + Math.floor(Math.random() * 18)
  return { kind: 'cactus_triple', x: SPAWN_X, stemW, gap, h0, h1, h2 }
}

// ── 색상 팔레트 (도트 명암) ─────────────────────────────────────
const C = {
  skyTop:   '#05080d',
  skyMid:   '#0c1420',
  skyBot:   '#121c2e',
  ground:   '#2a3d52',
  groundBand: '#0f1622',
  dune:     '#182433',
  dino:     '#00e8c8',
  dinoHi:   '#9dfff0',
  dinoSh:   '#00997a',
  dinoOl:   '#003d35',
  obstacle: '#e83d5c',
  obsHi:    '#ff8a9a',
  obsSh:    '#8c1f36',
  obsOl:    '#3d0f18',
  ptero:    '#f5a84a',
  pteroHi:  '#ffe4a8',
  pteroSh:  '#a86a20',
  pteroOl:  '#4a3010',
  text:     '#4a6070',
  star:     '#3d5570',
  starHi:   '#6a8aaa',
}

// ── 별 배경 (1px 도트 위주) ─────────────────────────────────────
const STARS = Array.from({ length: 90 }, () => ({
  x: Math.floor(Math.random() * 800),
  y: Math.floor(Math.random() * 220),
  big: Math.random() < 0.15,
  a: 0.35 + Math.random() * 0.55,
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
  speed = SPEED_START
  obstacleInterval = nextSpawnIntervalFrames(0)
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

  if (tick % 6 === 0) {
    score.value++
    emit('score-update', score.value)
  }
  // 점수에 비례해 매 프레임 속도 갱신 (크롬 공룡 게임처럼 점진 가속)
  speed = speedFromScore(score.value)

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
    obstacles.push(spawnObstacle(score.value))
    obstacleInterval = nextSpawnIntervalFrames(score.value)
  }

  // 장애물 이동 & 충돌 검사 (그리기 좌표와 동일한 기준)
  obstacles = obstacles.filter(o => obstacleRightEdge(o) > 0)
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

  ctx.imageSmoothingEnabled = false

  const skyGrad = ctx.createLinearGradient(0, 0, 0, Math.min(H * 0.55, FEET_LINE - 20))
  skyGrad.addColorStop(0, C.skyTop)
  skyGrad.addColorStop(0.45, C.skyMid)
  skyGrad.addColorStop(1, C.skyBot)
  ctx.fillStyle = skyGrad
  ctx.fillRect(0, 0, W, H)

  // 먼 지평선 (정적 도트 실루엣)
  ctx.fillStyle = C.dune
  ctx.globalAlpha = 0.55
  for (let i = 0; i < 40; i++) {
    const sx = ((i * 47) % (W + 80)) - 40
    const py = FEET_LINE - 42 + ((i * 13) % 18)
    const pw = 16 + (i % 5) * 4
    ctx.fillRect(sx, py, pw, FEET_LINE - py + 8)
  }
  ctx.globalAlpha = 1

  // 별 (1~2px 도트)
  for (const s of STARS) {
    const sx = ((s.x % W) + W) % W
    ctx.globalAlpha = s.a * 0.55
    ctx.fillStyle = s.big ? C.starHi : C.star
    ctx.fillRect(sx, s.y, s.big ? 2 : 1, s.big ? 2 : 1)
  }
  ctx.globalAlpha = 1

  // 지면 띠
  ctx.fillStyle = C.groundBand
  ctx.fillRect(0, FEET_LINE - 3, W, Math.max(0, H - FEET_LINE + 6))
  ctx.fillStyle = C.ground
  ctx.fillRect(0, FEET_LINE - 2, W, 4)

  drawDino(DINO_X, dinoY)

  for (const o of obstacles) {
    if (o.kind === 'ptero') {
      drawPtero(o)
    }
    else {
      ctx.fillStyle = C.obstacle
      if (o.kind === 'cactus_small') {
        drawCactusStem(o.x, o.stemW, o.stemH)
      }
      else if (o.kind === 'cactus_double') {
        drawCactusStem(o.x, o.stemW, o.h0)
        drawCactusStem(o.x + o.gap, o.stemW, o.h1)
      }
      else {
        drawCactusStem(o.x, o.stemW, o.h0)
        drawCactusStem(o.x + o.gap, o.stemW, o.h1)
        drawCactusStem(o.x + o.gap * 2, o.stemW, o.h2)
      }
    }
  }

  // 지면 점선 (도트 간격)
  ctx.fillStyle = C.text
  ctx.globalAlpha = 0.55
  for (let gx = 0; gx < W; gx += 16) {
    ctx.fillRect(gx, FEET_LINE + 2, 5, 1)
  }
  ctx.globalAlpha = 1
}

function drawCactusStem(left: number, stemW: number, stemH: number) {
  if (!ctx) return
  const oy = FEET_LINE - stemH
  const armH = Math.floor(stemH * 0.5)
  const armR = Math.floor(stemH * 0.4)
  const cx = Math.round(left)
  const L = (col: string, px: number, py: number, w: number, h: number) => {
    ctx!.fillStyle = col
    ctx!.fillRect(px, py, w, h)
  }

  L(C.obsOl, cx - 6, oy + 7, 8, armH + 2)
  L(C.obsSh, cx - 5, oy + 8, 6, armH)
  if (armH > 4) {
    L(C.obsHi, cx - 4, oy + 10, 2, armH - 4)
  }

  L(C.obsOl, cx - 1, oy - 1, stemW + 2, stemH + 2)
  L(C.obsSh, cx, oy + 3, stemW, stemH - 3)
  L(C.obstacle, cx + 1, oy + 1, stemW - 2, stemH - 2)
  L(C.obsHi, cx + 2, oy + 2, 2, Math.max(2, stemH - 8))
  L(C.obsSh, cx + stemW - 3, oy + 4, 2, stemH - 8)

  const topW = Math.max(4, stemW - 4)
  L(C.obsHi, cx + Math.floor((stemW - topW) / 2), oy + 1, topW, 2)

  L(C.obsOl, cx + stemW - 2, oy + 11, 8, armR + 2)
  L(C.obsSh, cx + stemW - 1, oy + 12, 6, armR)
  if (armR > 3) {
    L(C.obsHi, cx + stemW, oy + 13, 2, armR - 3)
  }
}

function drawPtero(o: Obstacle & { kind: 'ptero' }) {
  if (!ctx) return
  const x = o.x
  const y = o.y
  const f = pteroFlapOffset(o.flapPhase)
  const L = (col: string, dx: number, dy: number, w: number, h: number) => {
    ctx!.fillStyle = col
    ctx!.fillRect(x + dx, y + dy, w, h)
  }

  L(C.pteroOl, 1, 7 + f, 16, 12)
  L(C.pteroSh, 26, 7 + f, 18, 12)
  L(C.ptero, 2, 8 + f, 14, 8)
  L(C.pteroHi, 3, 9 + f, 5, 2)
  L(C.ptero, 27, 8 + f, 14, 8)
  L(C.pteroHi, 35, 9 + f, 5, 2)

  L(C.pteroOl, 7, 5, 24, 16)
  L(C.pteroSh, 8, 6, 22, 14)
  L(C.ptero, 9, 7, 20, 12)
  L(C.pteroHi, 11, 8, 9, 3)

  L(C.pteroOl, 19, 1, 14, 10)
  L(C.pteroSh, 20, 2, 12, 8)
  L(C.ptero, 21, 3, 10, 6)
  L(C.pteroOl, 29, 4, 5, 5)
  L(C.pteroHi, 22, 4, 5, 2)

  L('#0a1218', 25, 4, 3, 3)
  L('#e8f4ff', 26, 4, 1, 1)
}

function drawDino(x: number, y: number) {
  if (!ctx) return
  const legA = Math.floor(tick / 6) % 2
  const L = (col: string, dx: number, dy: number, w: number, h: number) => {
    ctx!.fillStyle = col
    ctx!.fillRect(Math.round(x + dx), Math.round(y + dy), w, h)
  }

  if (onGround) {
    ctx.globalAlpha = 0.22
    L('#000000', 6, 38, 22, 2)
    ctx.globalAlpha = 1
  }

  L(C.dinoOl, 0, 11, 7, 10)
  L(C.dinoSh, 1, 12, 5, 8)
  L(C.dino, 2, 12, 4, 7)
  L(C.dinoHi, 3, 12, 2, 4)

  const Lh = onGround ? (legA === 0 ? 10 : 6) : 8
  const Rh = onGround ? (legA === 0 ? 6 : 10) : 8
  L(C.dinoOl, 7, 27, 10, Lh + 2)
  L(C.dinoSh, 8, 28, 8, Lh)
  L(C.dinoOl, 17, 27, 10, Rh + 2)
  L(C.dinoSh, 18, 28, 8, Rh)

  L(C.dinoOl, 3, -1, 26, 30)
  L(C.dinoSh, 4, 0, 24, 28)
  L(C.dino, 5, 1, 22, 26)
  L(C.dinoHi, 6, 2, 5, 16)
  L(C.dinoSh, 22, 5, 4, 18)
  L(C.dinoOl, 17, 6, 3, 14)

  L(C.dino, 14, -2, 12, 8)

  L(C.dinoOl, 14, -11, 18, 16)
  L(C.dinoSh, 15, -10, 16, 14)
  L(C.dino, 16, -9, 14, 12)
  L(C.dinoHi, 17, -9, 9, 3)
  L(C.dinoSh, 24, -5, 8, 7)
  L(C.dino, 25, -4, 6, 5)
  L(C.dinoOl, 23, 0, 9, 2)

  L(C.dinoHi, 25, -7, 5, 5)
  L('#0a1218', 27, -5, 3, 3)
  L('#e8f4ff', 28, -5, 1, 1)

  L(C.dinoOl, 20, 9, 7, 9)
  L(C.dinoSh, 21, 10, 5, 7)
  L(C.dino, 22, 11, 3, 5)
}

// ── 캔버스 크기 맞추기 ───────────────────────────────────────────
function resize() {
  if (!canvas.value) return
  const wrap = canvas.value.parentElement!
  canvas.value.width  = wrap.clientWidth
  canvas.value.height = wrap.clientHeight
  if (ctx) {
    ctx.imageSmoothingEnabled = false
  }
}

onMounted(() => {
  ctx = canvas.value!.getContext('2d', { alpha: false })!
  ctx.imageSmoothingEnabled = false
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
  image-rendering: pixelated;
  image-rendering: crisp-edges;
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