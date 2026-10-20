<template>
  <span class="pill" :class="`pill--${tone}`">
    <i v-if="dot" class="pill-dot" />
    {{ label }}
  </span>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  tone: { type: String, default: 'slate' },
  dot: { type: Boolean, default: false }
})
</script>

<style scoped>
.pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
  white-space: nowrap;
  border: 1px solid transparent;
}

.pill-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.pill--teal {
  color: #0f766e;
  background: #e6fbf7;
  border-color: #bff0e7;
}

.pill--indigo {
  color: #4338ca;
  background: #eef0ff;
  border-color: #d8dafb;
}

.pill--amber {
  color: #b45309;
  background: #fff7e6;
  border-color: #fde3b4;
}

.pill--red {
  color: #be123c;
  background: #fff0f3;
  border-color: #fbd0da;
}

.pill--green {
  color: #047857;
  background: #e9fbf3;
  border-color: #c3f0dd;
}

.pill--slate {
  color: #4b5b70;
  background: #f1f5fa;
  border-color: #e2e8f0;
}
</style>
