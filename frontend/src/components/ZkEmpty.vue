<template>
  <div class="zk-empty-box" :class="`is-${size}`" role="status">
    <div class="zk-empty-stack">
      <div class="zk-empty-icon" aria-hidden="true">
        <svg viewBox="0 0 40 40" width="26" height="26" fill="none">
          <rect
            x="6.5"
            y="9.5"
            width="27"
            height="21"
            rx="3"
            stroke="currentColor"
            stroke-width="1.4"
          />
          <path d="M12 16h9M12 21h16M12 26h11" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
        </svg>
      </div>
      <p class="zk-empty-title">{{ title }}</p>
      <p v-if="hint" class="zk-empty-hint">{{ hint }}</p>
      <div v-if="$slots.default" class="zk-empty-actions">
        <slot />
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 通用空状态
 *
 * 规则（ui-motion 63 空状态引导）：
 * · 图标—标题—说明—按钮依次浮现（0/50/100/140ms），不是整块蹦出来；
 * · 有明确的「从这开始」入口，而不是只说「暂无数据」；
 * · 尺寸通过 size 控制（sm 用于表格内，md 用于整块内容区）。
 */
defineProps({
  title: { type: String, default: '暂无数据' },
  hint: { type: String, default: '' },
  size: { type: String, default: 'md' }
})
</script>

<style scoped>
.zk-empty-box {
  display: grid;
  place-items: center;
  padding: 44px 20px 40px;
  text-align: center;
}

.zk-empty-box.is-sm {
  padding: 26px 16px 24px;
}

.zk-empty-stack {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  max-width: 460px;
}

.zk-empty-icon {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  border-radius: var(--zk-radius);
  color: var(--zk-accent-hover);
  background: var(--zk-accent-soft);
  border: 1px solid var(--zk-accent-line);
  margin-bottom: 2px;
}

.is-sm .zk-empty-icon {
  width: 42px;
  height: 42px;
}

.zk-empty-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--zk-text);
  line-height: 1.5;
}

.zk-empty-hint {
  font-size: 12.5px;
  color: var(--zk-text-3);
  line-height: 1.65;
}

.zk-empty-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  flex-wrap: wrap;
  justify-content: center;
}
</style>
