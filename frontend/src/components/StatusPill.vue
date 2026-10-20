<template>
  <span class="pill" :class="`pill--${tone}`">
    <i v-if="dot" class="pill-dot" aria-hidden="true" />
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
  line-height: 1.4;
  white-space: nowrap;
  border: 1px solid transparent;
  font-variant-numeric: tabular-nums;
}

.pill-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  flex: 0 0 5px;
}

.pill--teal {
  color: #0f766e;
  background: #e6fbf7;
  border-color: #b8e9df;
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
  background: #f1f5fa;
  border-color: #dfe6f0;
}
</style>
