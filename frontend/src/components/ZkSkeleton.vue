<template>
  <div
    class="zk-skeleton-block"
    :class="`is-${variant}`"
    :style="{ '--sk-cols': cols }"
    aria-hidden="true"
  >
    <!-- 文本行 -->
    <template v-if="variant === 'lines'">
      <span
        v-for="index in rows"
        :key="index"
        class="zk-skeleton line"
        :style="{ width: index === rows ? lastWidth : '100%' }"
      />
    </template>

    <!-- 表格：与真实表格同样行高，加载前后不跳 -->
    <template v-else-if="variant === 'table'">
      <div class="sk-head">
        <span v-for="col in cols" :key="`h${col}`" class="zk-skeleton cell" />
      </div>
      <div v-for="row in rows" :key="row" class="sk-row">
        <span v-for="col in cols" :key="`r${row}-${col}`" class="zk-skeleton cell" />
      </div>
    </template>

    <!-- 列表项：头像 + 两行 -->
    <template v-else-if="variant === 'list'">
      <div v-for="row in rows" :key="row" class="sk-item">
        <span class="zk-skeleton avatar" />
        <span class="lines">
          <span class="zk-skeleton line" style="width: 42%" />
          <span class="zk-skeleton line" style="width: 72%" />
        </span>
      </div>
    </template>

    <!-- 卡片：标题 + 正文 + 数据块 -->
    <template v-else-if="variant === 'card'">
      <span class="zk-skeleton line" style="width: 34%; height: 14px" />
      <span class="zk-skeleton block" />
      <span v-for="index in rows" :key="index" class="zk-skeleton line" :style="{ width: index % 2 ? '92%' : '68%' }" />
    </template>

    <!-- 指标卡：小标题 + 大数字 + 辅助说明 -->
    <template v-else-if="variant === 'stat'">
      <span class="zk-skeleton line" style="width: 38%; height: 10px" />
      <span class="zk-skeleton line" style="width: 58%; height: 26px" />
      <span class="zk-skeleton line" style="width: 46%; height: 10px" />
    </template>

    <!-- 对话气泡：左右交替，尾部预留输入区高度 -->
    <template v-else-if="variant === 'chat'">
      <div
        v-for="row in rows"
        :key="row"
        class="sk-msg"
        :class="row % 2 ? 'is-peer' : 'is-user'"
      >
        <span class="zk-skeleton bubble" :style="{ width: row % 3 === 0 ? '46%' : '62%' }" />
      </div>
    </template>

    <!-- 图表：刻度线 + 一排柱子 -->
    <template v-else-if="variant === 'chart'">
      <div class="sk-chart">
        <span v-for="row in 4" :key="`g${row}`" class="sk-grid" />
        <div class="sk-bars">
          <span
            v-for="bar in cols"
            :key="bar"
            class="zk-skeleton col"
            :style="{ height: `${28 + ((bar * 37) % 64)}%` }"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
/**
 * 通用骨架屏
 *
 * 规则（ui-motion 15 骨架屏扫光）：
 * · 形状/尺寸/位置和真实内容一致，加载前后版面不跳；
 * · 一页共用同一道斜向光带（.zk-skeleton 统一 animation，起点相同）；
 * · 屏幕阅读器只听到一句「正在加载」，骨架本身 aria-hidden。
 */
const props = defineProps({
  variant: {
    type: String,
    default: 'lines',
    validator: (value) => ['lines', 'table', 'list', 'card', 'stat', 'chat', 'chart'].includes(value)
  },
  rows: { type: Number, default: 0 },
  cols: { type: Number, default: 5 },
  lastWidth: { type: String, default: '64%' }
})

const defaultRows = {
  lines: 3,
  table: 6,
  list: 4,
  card: 3,
  stat: 1,
  chat: 4,
  chart: 7
}

const rows = props.rows || defaultRows[props.variant] || 3
</script>

<style scoped>
.zk-skeleton-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  padding: 2px 0;
}

.line {
  height: 12px;
  border-radius: var(--zk-radius-xs);
}

.block {
  height: 88px;
  border-radius: var(--zk-radius-sm);
}

/* ---------- 表格骨架 ---------- */
.sk-head,
.sk-row {
  display: grid;
  grid-template-columns: repeat(var(--sk-cols, 5), minmax(0, 1fr));
  gap: 12px;
  align-items: center;
}

.sk-head {
  padding: 8px 0;
  border-bottom: 1px solid var(--zk-border);
}

.sk-head .cell {
  height: 10px;
}

.sk-row {
  height: 34px;
  border-bottom: 1px solid #f4f4f6;
}

.sk-row .cell {
  height: 11px;
}

.sk-row .cell:first-child {
  width: 72%;
}

/* ---------- 列表骨架 ---------- */
.sk-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
}

.avatar {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: 50%;
}

.sk-item .lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

/* ---------- 对话骨架 ---------- */
.sk-msg {
  display: flex;
  padding: 4px 0;
}

.sk-msg.is-user {
  justify-content: flex-end;
}

.bubble {
  height: 38px;
  border-radius: 2px 10px 10px 10px;
}

.sk-msg.is-user .bubble {
  border-radius: 10px 2px 10px 10px;
}

/* ---------- 图表骨架 ---------- */
.sk-chart {
  position: relative;
  height: 200px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.sk-grid {
  display: block;
  height: 1px;
  background: var(--zk-grid);
}

.sk-bars {
  position: absolute;
  inset: 12px 0 0;
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.sk-bars .col {
  flex: 1;
  border-radius: 3px 3px 0 0;
}

@media (prefers-reduced-motion: reduce) {
  .zk-skeleton-block {
    opacity: 0.9;
  }
}
</style>
