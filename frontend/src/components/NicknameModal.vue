<template>
  <div class="modal-overlay" @click.self="tryClose">
    <div class="modal-box">
      <p class="modal-title">ENTER YOUR NAME</p>
      <input
        ref="inputRef"
        v-model="value"
        class="name-input"
        maxlength="12"
        placeholder="PLAYER_01"
        @keydown.enter="confirm"
      />
      <p class="char-count">{{ value.length }} / 12</p>
      <button class="start-btn" @click="confirm" :disabled="!value.trim()">
        START GAME
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const emit = defineEmits<{ confirm: [name: string] }>()

const value    = ref('')
const inputRef = ref<HTMLInputElement | null>(null)

onMounted(() => inputRef.value?.focus())

function confirm() {
  const name = value.value.trim()
  if (!name) return
  emit('confirm', name)
}

// 닉네임 입력 전에는 모달 닫기 불가
function tryClose() { /* intentionally empty */ }
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(8, 11, 16, 0.88);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding: 40px 48px;
  background: var(--bg2);
  border: 1px solid var(--border2);
  border-radius: 4px;
  min-width: 320px;
}

.modal-title {
  font-family: var(--pixel);
  font-size: 13px;
  color: var(--neon);
  letter-spacing: 0.15em;
}

.name-input {
  width: 100%;
  padding: 12px 16px;
  background: var(--bg3);
  border: 1px solid var(--border2);
  border-radius: 3px;
  color: var(--text);
  font-family: var(--pixel);
  font-size: 12px;
  letter-spacing: 0.1em;
  text-align: center;
  outline: none;
  transition: border-color .2s;
}
.name-input::placeholder { color: var(--text2); }
.name-input:focus { border-color: var(--neon); }

.char-count {
  font-size: 10px;
  color: var(--text2);
  letter-spacing: 0.08em;
  align-self: flex-end;
  margin-top: -12px;
}

.start-btn {
  width: 100%;
  padding: 13px;
  background: transparent;
  border: 1px solid var(--neon);
  border-radius: 3px;
  color: var(--neon);
  font-family: var(--pixel);
  font-size: 11px;
  letter-spacing: 0.1em;
  cursor: pointer;
  transition: background .2s, color .2s;
}
.start-btn:hover:not(:disabled) {
  background: var(--neon);
  color: #080b10;
}
.start-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
</style>