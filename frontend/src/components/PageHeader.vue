<template>
  <div class="page-head">
    <div class="head-main">
      <div v-if="eyebrow" class="zk-eyebrow">{{ eyebrow }}</div>
      <h1>
        <span>{{ title }}</span>
        <span v-if="badge" class="head-badge">{{ badge }}</span>
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
  gap: 20px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.head-main h1 {
  font-size: 25px;
  font-weight: 720;
  letter-spacing: -0.4px;
  line-height: 1.25;
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--zk-text);
  text-wrap: balance;
}

.head-badge {
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.3px;
  color: var(--zk-teal-deep);
  background: var(--zk-teal-soft);
  border: 1px solid var(--zk-teal-line);
  padding: 3px 8px;
  border-radius: 999px;
  white-space: nowrap;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 720px) {
  .page-head {
    align-items: flex-start;
  }

  .head-main h1 {
    font-size: 21px;
  }

  .head-actions {
    width: 100%;
  }
}
</style>
