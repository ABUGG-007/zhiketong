<template>
  <div class="page-head">
    <div class="head-main">
      <div v-if="eyebrow" class="zk-eyebrow">{{ eyebrow }}</div>
      <h1>
        <span>{{ title }}</span>
        <!-- badge 入场弹一下（220ms），文案变化时按同一路径重播 -->
        <transition name="zk-pop" mode="out-in" appear>
          <span v-if="badge" :key="badge" class="head-badge">{{ badge }}</span>
        </transition>
      </h1>
      <p v-if="description" class="zk-page-sub">{{ description }}</p>
    </div>
    <div class="head-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  eyebrow: { type: String, default: '' },
  badge: { type: String, default: '' }
})
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.head-main h1 {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.2px;
  line-height: 1.3;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--zk-text);
  text-wrap: balance;
}

.head-badge {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0;
  color: var(--zk-text-2);
  background: var(--zk-surface-3);
  border: 1px solid var(--zk-border);
  padding: 2px 7px;
  border-radius: var(--zk-radius-sm);
  white-space: nowrap;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 720px) {
  .page-head {
    align-items: flex-start;
  }

  .head-main h1 {
    font-size: 18px;
  }

  .head-actions {
    width: 100%;
  }
}
</style>
