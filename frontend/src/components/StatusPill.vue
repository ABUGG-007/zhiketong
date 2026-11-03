<template>
  <span class="pill" :class="[`pill--${tone}`, { 'is-solid': solid }]">
    <i v-if="dot" class="pill-dot" :class="{ 'zk-dot-live': live }" aria-hidden="true" />
    {{ label }}
  </span>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  tone: { type: String, default: 'slate' },
  dot: { type: Boolean, default: false },
  // 实色底白字：用于高优先级/强告警标签，一眼可识别
  solid: { type: Boolean, default: false },
  // 进行中状态（在线 / 忙碌 / 接待中）：状态点呼吸，静止状态保持不闪
  live: { type: Boolean, default: false }
})
</script>

<style scoped>
.pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border-radius: var(--zk-radius-sm, 6px);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
  white-space: nowrap;
  border: 1px solid transparent;
  font-variant-numeric: tabular-nums;
  /* tone 切换时背景 / 边框 / 文字同步过渡，避免状态变为另一色时硬切 */
  transition: color var(--zk-dur-base) var(--zk-ease-out),
    background-color var(--zk-dur-base) var(--zk-ease-out),
    border-color var(--zk-dur-base) var(--zk-ease-out);
}

.pill-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  flex: 0 0 5px;
}

.pill--teal {
  color: #15803d;
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.pill--indigo {
  color: #4338ca;
  background: #eef0ff;
  border-color: #d5d8fb;
}

.pill--amber {
  color: #a34d08;
  background: #fff7e6;
  border-color: #f7ddab;
}

.pill--red {
  color: #b4123e;
  background: #fff0f3;
  border-color: #f7c8d3;
}

.pill--green {
  color: #046c4e;
  background: #e9fbf3;
  border-color: #bdebd7;
}

.pill--slate {
  color: #475569;
  background: #f4f4f5;
  border-color: #e4e4e7;
}

/* 实色底白字变体：优先级 / 告警强化 */
.pill--red.is-solid {
  color: #fff;
  background: #be123c;
  border-color: #be123c;
}

.pill--amber.is-solid {
  color: #fff;
  background: #b45309;
  border-color: #b45309;
}

.pill--teal.is-solid {
  color: #fff;
  background: #15803d;
  border-color: #15803d;
}

.pill--green.is-solid {
  color: #fff;
  background: #047857;
  border-color: #047857;
}

.pill--indigo.is-solid {
  color: #fff;
  background: #4338ca;
  border-color: #4338ca;
}

.pill--slate.is-solid {
  color: #fff;
  background: #4b5563;
  border-color: #4b5563;
}

.pill--slate.is-solid .pill-dot,
.pill.is-solid .pill-dot {
  background: currentColor;
}
</style>
