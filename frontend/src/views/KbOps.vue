<template>
  <div class="zk-page">
    <PageHeader
      title="知识运营"
      description="把「未命中问题 → 新词条」的闭环、知识健康度、查重冲突与行业知识包导入集中在一处，持续提升 AI 自主解决率。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="refreshing" @click="reloadAll">刷新</el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="tab" class="ops-tabs" @tab-change="onTabChange">
      <!-- ---------------- 未命中问题 ---------------- -->
      <el-tab-pane label="未命中问题" name="unmatched">
        <section class="zk-card ops-panel">
          <div class="zk-toolbar">
            <label for="unmatched-search" class="sr-only">搜索未命中问题</label>
            <el-input
              id="unmatched-search"
              v-model="filters.keyword"
              class="toolbar-search"
              placeholder="搜索访客问法"
              :prefix-icon="Search"
              clearable
              @keyup.enter="search"
              @clear="search"
            />
            <el-select
              v-model="filters.status"
              placeholder="全部状态"
              clearable
              aria-label="按处理状态筛选"
              @change="search"
            >
              <el-option
                v-for="item in STATUS_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-select
              v-model="filters.channel"
              placeholder="全部渠道"
              clearable
              aria-label="按来源渠道筛选"
              @change="search"
            >
              <el-option label="站内会话" value="SITE" />
              <el-option label="网页接入" value="EMBED" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="search">查询</el-button>
            <el-button @click="reset">重置</el-button>
            <div class="grow" />
            <el-button
              v-if="canManage"
              :disabled="!selection.length"
              :loading="batchLoading"
              @click="batchIgnore"
            >
              批量忽略{{ selection.length ? `（${selection.length}）` : '' }}
            </el-button>
            <span class="toolbar-meta" aria-live="polite">
              共 <b class="zk-num">{{ total }}</b> 条
            </span>
          </div>

          <div
            v-if="!isCompact"
            class="table-scroll"
            :class="{ 'table-result-fade': resultFade }"
            v-loading="loading && loaded"
          >
            <ZkSkeleton v-if="!loaded" variant="table" :rows="10" :cols="6" class="table-skeleton" />
            <el-table
              v-else
              class="zk-table-fade-in"
              :data="rows"
              row-key="id"
              aria-label="未命中问题列表"
              :row-class-name="rowClassName"
              @selection-change="onSelectionChange"
            >
              <el-table-column v-if="canManage" type="selection" width="46" />
              <el-table-column label="访客问法" min-width="260">
                <template #default="{ row }">
                  <div class="cell-question">{{ row.question }}</div>
                  <div class="cell-sub">
                    最近得分 {{ scoreText(row.lastScore) }}
                    <template v-if="row.entryQuestion">· 已转：{{ row.entryQuestion }}</template>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="渠道" width="104">
                <template #default="{ row }">
                  <StatusPill
                    :label="channelLabel(row.channel)"
                    :tone="row.channel === 'EMBED' ? 'teal' : 'indigo'"
                  />
                </template>
              </el-table-column>
              <el-table-column label="客户之声主题" width="140">
                <template #default="{ row }">
                  <div v-if="row.topicName" class="topic-cell">
                    <span class="topic-name" :title="row.topicName">{{ row.topicName }}</span>
                    <span v-if="row.clusterScore != null" class="cell-sub">
                      相似度 {{ row.clusterScore }}%
                    </span>
                  </div>
                  <span v-else class="cell-sub">未归类</span>
                </template>
              </el-table-column>
              <el-table-column label="出现次数" width="132" align="right">
                <template #default="{ row }">
                  <div class="miss-cell">
                    <span class="hit-num zk-num"><CountNum :value="row.askCount ?? 0" /></span>
                    <span class="hit-bar" aria-hidden="true">
                      <i :style="{ width: askPercent(row.askCount) }" />
                    </span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <StatusPill :label="statusLabel(row.status)" :tone="statusTone(row.status)" />
                </template>
              </el-table-column>
              <el-table-column label="最近出现" width="150">
                <template #default="{ row }">
                  <span class="cell-time">{{ formatDateTime(row.lastAskTime) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="132" fixed="right">
                <template #default="{ row }">
                  <div class="row-ops">
                    <el-button
                      v-if="canManage && row.status !== 'CONVERTED'"
                      link
                      type="primary"
                      @click="openConvert(row)"
                    >
                      转词条
                    </el-button>
                    <el-button
                      v-if="canManage && row.status === 'PENDING'"
                      link
                      @click="ignoreOne(row)"
                    >
                      忽略
                    </el-button>
                    <el-button
                      v-if="canManage && row.status === 'IGNORED'"
                      link
                      @click="restoreOne(row)"
                    >
                      恢复
                    </el-button>
                    <el-button
                      v-if="canManage && row.status === 'CONVERTED'"
                      link
                      type="success"
                      @click="goEntry()"
                    >
                      查看词条
                    </el-button>
                  </div>
                </template>
              </el-table-column>
              <template #empty>
                <ZkEmpty
                  size="sm"
                  :title="error ? '未命中问题加载失败' : hasFilter ? '没有匹配的未命中问题' : '还没有采集到未命中问题'"
                  :hint="
                    error
                      ? '请稍后重新加载，或确认后端服务是否正常'
                      : '访客提问未被知识库覆盖时会自动采集，可在此一键转为词条'
                  "
                >
                  <el-button v-if="error" @click="load">重新加载</el-button>
                </ZkEmpty>
              </template>
            </el-table>
          </div>

          <div v-else v-loading="loading && loaded" class="ops-list">
            <ZkSkeleton v-if="!loaded" variant="list" :rows="4" />
            <template v-else>
              <article v-for="row in rows" :key="row.id" class="ops-item">
                <div class="ops-item-top">
                  <span class="cell-question">{{ row.question }}</span>
                  <StatusPill :label="statusLabel(row.status)" :tone="statusTone(row.status)" />
                </div>
                <div class="ops-item-meta">
                  <span>{{ channelLabel(row.channel) }}</span>
                  <span aria-hidden="true">·</span>
                  <span><CountNum :value="row.askCount ?? 0" /> 次</span>
                  <span aria-hidden="true">·</span>
                  <span>{{ row.topicName || '未归类' }}</span>
                  <span aria-hidden="true">·</span>
                  <span>{{ formatDateTime(row.lastAskTime) }}</span>
                </div>
                <div v-if="canManage" class="ops-item-ops">
                  <el-button
                    v-if="row.status !== 'CONVERTED'"
                    size="small"
                    type="primary"
                    @click="openConvert(row)"
                  >
                    转词条
                  </el-button>
                  <el-button
                    v-if="row.status === 'PENDING'"
                    size="small"
                    @click="ignoreOne(row)"
                  >
                    忽略
                  </el-button>
                  <el-button
                    v-if="row.status === 'IGNORED'"
                    size="small"
                    @click="restoreOne(row)"
                  >
                    恢复
                  </el-button>
                </div>
              </article>
              <ZkEmpty
                v-if="!rows.length"
                size="sm"
                :title="error ? '未命中问题加载失败' : hasFilter ? '没有匹配的未命中问题' : '还没有采集到未命中问题'"
                hint="访客提问未被知识库覆盖时会自动采集，可一键转为词条"
              >
                <el-button v-if="error" @click="load">重新加载</el-button>
              </ZkEmpty>
            </template>
          </div>

          <div class="zk-pager">
            <el-pagination
              background
              :layout="isCompact ? 'prev, pager, next' : 'total, sizes, prev, pager, next'"
              :total="total"
              :current-page="pageNum"
              :page-size="pageSize"
              :page-sizes="[10, 20, 50]"
              aria-label="未命中问题分页"
              @current-change="changePage"
              @size-change="changeSize"
            />
          </div>
        </section>
      </el-tab-pane>

      <!-- ---------------- 健康度 ---------------- -->
      <el-tab-pane label="知识健康度" name="health">
        <el-skeleton v-if="healthLoading" :rows="8" animated />
        <div v-else-if="healthError" class="ops-empty zk-card">
          <p>{{ healthError }}</p>
          <el-button @click="loadHealth">重新加载</el-button>
        </div>
        <div v-else-if="health" class="health">
          <section class="metric-grid">
            <article v-for="card in healthCards" :key="card.label" class="metric-card">
              <div class="metric-label">{{ card.label }}</div>
              <div class="metric-value">
                <span class="zk-num"><CountNum :value="card.value" /></span>
                <span v-if="card.unit" class="metric-unit">{{ card.unit }}</span>
              </div>
              <div class="metric-foot">{{ card.foot }}</div>
            </article>
          </section>

          <div class="health-cols">
            <section class="zk-card ops-panel">
              <div class="ops-panel-head">
                <h2 class="ops-title">命中排行 Top 10</h2>
                <span class="ops-panel-desc">被访客真实命中的高频问题</span>
              </div>
              <ol v-if="health.topHits?.length" class="rank-list">
                <li v-for="(item, index) in health.topHits" :key="item.id" class="rank-item">
                  <span class="rank-no">{{ index + 1 }}</span>
                  <span class="rank-q" :title="item.question">{{ item.question }}</span>
                  <span class="rank-cat">{{ item.categoryName || '未分类' }}</span>
                  <span class="rank-val zk-num">{{ item.hitCount ?? 0 }}</span>
                </li>
              </ol>
              <p v-else class="ops-empty-text">暂无命中数据</p>
            </section>

            <section class="zk-card ops-panel">
              <div class="ops-panel-head">
                <h2 class="ops-title">未命中高频 Top 10</h2>
                <span class="ops-panel-desc">补充这些问法可最快提升覆盖率</span>
              </div>
              <ol v-if="health.topUnmatched?.length" class="rank-list">
                <li v-for="(item, index) in health.topUnmatched" :key="item.id" class="rank-item">
                  <span class="rank-no">{{ index + 1 }}</span>
                  <span class="rank-q" :title="item.question">{{ item.question }}</span>
                  <span class="rank-cat">{{ channelLabel(item.channel) }}</span>
                  <span class="rank-val zk-num">{{ item.askCount ?? 0 }}</span>
                </li>
              </ol>
              <p v-else class="ops-empty-text">暂无未命中记录</p>
            </section>
          </div>

          <section class="zk-card ops-panel">
            <div class="ops-panel-head">
              <h2 class="ops-title">近 14 天新增未命中</h2>
              <span class="ops-panel-desc">按问题首次出现日期聚合</span>
            </div>
            <div class="trend" role="img" aria-label="近 14 天新增未命中趋势">
              <div v-for="item in health.trend || []" :key="item.date" class="trend-col">
                <span class="trend-bar-wrap">
                  <span
                    class="trend-bar"
                    :style="{ height: trendHeight(item.count) }"
                    :title="`${item.date}：${item.count} 条`"
                  />
                </span>
                <span class="trend-num zk-num">{{ item.count }}</span>
                <span class="trend-label">{{ shortDate(item.date) }}</span>
              </div>
              <p v-if="!health.trend?.length" class="ops-empty-text">暂无趋势数据</p>
            </div>
          </section>

          <section class="zk-card ops-panel">
            <div class="ops-panel-head">
              <h2 class="ops-title">分类覆盖度</h2>
              <span class="ops-panel-desc">零命中文占比过高说明该类内容需要补充</span>
            </div>
            <el-table :data="health.categories || []" size="small" aria-label="分类覆盖度">
              <el-table-column label="分类" min-width="140">
                <template #default="{ row }">
                  <span>{{ row.categoryName || '未分类' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="启用词条" width="100" align="right">
                <template #default="{ row }">
                  <span class="zk-num">{{ row.entryCount }}</span>
                </template>
              </el-table-column>
              <el-table-column label="零命中文" width="100" align="right">
                <template #default="{ row }">
                  <span class="zk-num">{{ row.zeroHitCount }}</span>
                </template>
              </el-table-column>
              <el-table-column label="覆盖率" width="180">
                <template #default="{ row }">
                  <div class="cov">
                    <span class="cov-bar" aria-hidden="true">
                      <i :style="{ width: `${coveragePercent(row)}%` }" />
                    </span>
                    <span class="zk-num">{{ coveragePercent(row) }}%</span>
                  </div>
                </template>
              </el-table-column>
              <template #empty>
                <p class="ops-empty-text">还没有可统计的分类</p>
              </template>
            </el-table>
          </section>
        </div>
      </el-tab-pane>

      <!-- ---------------- 查重冲突 ---------------- -->
      <el-tab-pane label="查重与冲突" name="duplicates">
        <section class="zk-card ops-panel">
          <div class="zk-toolbar">
            <span class="ops-hint">
              取最近更新的前 300 条启用词条两两比对：相似度 ≥ 75% 记为疑似重复，≥ 85% 且答案不同记为口径冲突
            </span>
            <div class="grow" />
            <el-button :icon="Refresh" :loading="dupLoading" @click="loadDuplicates">重新检测</el-button>
          </div>
          <ZkSkeleton
            v-if="!dupLoaded"
            variant="table"
            :rows="6"
            :cols="6"
            class="table-skeleton"
          />
          <el-table
            v-else
            v-loading="dupLoading"
            class="zk-table-fade-in"
            :data="duplicates"
            aria-label="疑似重复与冲突词条"
          >
            <el-table-column label="词条 A" min-width="200">
              <template #default="{ row }">
                <span class="dup-q">{{ row.entryAQuestion }}</span>
              </template>
            </el-table-column>
            <el-table-column label="词条 B" min-width="200">
              <template #default="{ row }">
                <span class="dup-q">{{ row.entryBQuestion }}</span>
              </template>
            </el-table-column>
            <el-table-column label="分类" width="120">
              <template #default="{ row }">
                <span class="cell-sub">{{ row.categoryName || '未分类' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="相似度" width="96" align="right">
              <template #default="{ row }">
                <span class="zk-num">{{ percentText(row.similarity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="判定" width="110">
              <template #default="{ row }">
                <StatusPill
                  :label="row.conflict ? '答案冲突' : '疑似重复'"
                  :tone="row.conflict ? 'red' : 'amber'"
                />
              </template>
            </el-table-column>
            <el-table-column label="处理建议" min-width="240">
              <template #default="{ row }">
                <span class="dup-reason">{{ row.reason }}</span>
              </template>
            </el-table-column>
            <template #empty>
              <ZkEmpty
                size="sm"
                :title="dupError || '没有检测到疑似重复或冲突的词条'"
                hint="保持一条问题对应一个口径，答案越唯一，AI 回复越稳定"
              />
            </template>
          </el-table>
        </section>
      </el-tab-pane>

      <!-- ---------------- 知识包导入 ---------------- -->
      <el-tab-pane label="知识包导入" name="import">
        <div class="import-grid">
          <section class="zk-card ops-panel">
            <div class="ops-panel-head">
              <h2 class="ops-title">粘贴知识包 JSON</h2>
              <span class="ops-panel-desc">分类不存在时自动创建顶级分类，同分类同问题自动跳过</span>
            </div>
            <el-input
              v-model="packageName"
              class="import-name"
              maxlength="128"
              show-word-limit
              placeholder="知识包名称，例如：电商售后通用知识包"
            />
            <el-input
              v-model="importText"
              type="textarea"
              :rows="14"
              resize="vertical"
              placeholder='支持 { "packageName": "…", "entries": [ … ] } 或直接粘贴词条数组'
              class="import-text"
            />
            <div class="ops-actions">
              <el-button size="small" @click="fillSample">填入示例</el-button>
              <div class="grow" />
              <el-button
                type="primary"
                :loading="importing"
                :disabled="!importText.trim()"
                @click="submitImport"
              >
                开始导入
              </el-button>
            </div>
            <div v-if="importError" class="ops-error">{{ importError }}</div>
            <div v-if="importResult" class="import-result">
              <div class="import-result-top">
                <StatusPill
                  :label="importStatusLabel(importResult.status)"
                  :tone="importStatusTone(importResult.status)"
                />
                <span class="zk-num">
                  成功 {{ importResult.successCount }} / 失败 {{ importResult.failCount }} / 共
                  {{ importResult.totalCount }}
                </span>
              </div>
              <p v-if="importResult.createdCategories?.length" class="import-line">
                新建分类：{{ importResult.createdCategories.join('、') }}
              </p>
              <ul v-if="importResult.errors?.length" class="import-errors">
                <li v-for="(message, index) in importResult.errors" :key="index">{{ message }}</li>
              </ul>
            </div>
          </section>

          <section class="zk-card ops-panel">
            <div class="ops-panel-head">
              <h2 class="ops-title">导入批次</h2>
              <span class="ops-panel-desc">最近 20 次导入记录</span>
            </div>
            <el-skeleton v-if="tasksLoading" :rows="4" animated />
            <div v-else-if="tasks.length" class="task-list">
              <div v-for="task in tasks" :key="task.id" class="task-item">
                <div class="task-top">
                  <span class="task-name" :title="task.packageName">{{ task.packageName }}</span>
                  <StatusPill
                    :label="importStatusLabel(task.status)"
                    :tone="importStatusTone(task.status)"
                  />
                </div>
                <div class="task-meta">
                  <span>成功 {{ task.successCount ?? 0 }}</span>
                  <span aria-hidden="true">·</span>
                  <span>失败 {{ task.failCount ?? 0 }}</span>
                  <span aria-hidden="true">·</span>
                  <span>共 {{ task.totalCount ?? 0 }}</span>
                </div>
                <div class="task-time">{{ formatDateTime(task.createTime) }}</div>
                <p v-if="task.errorMessage" class="task-error">{{ task.errorMessage }}</p>
              </div>
            </div>
            <p v-else class="ops-empty-text">暂无导入记录</p>
          </section>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 转词条 -->
    <el-dialog
      v-model="convertVisible"
      title="未命中问题转知识词条"
      width="620px"
      append-to-body
      @closed="resetConvert"
    >
      <div class="convert-origin">
        <span class="ops-label">访客问法</span>
        <p class="convert-question">{{ convertRecord?.question }}</p>
        <span class="convert-times">
          出现 {{ convertRecord?.askCount ?? 0 }} 次 · {{ channelLabel(convertRecord?.channel) }}
        </span>
      </div>
      <el-form
        ref="convertFormRef"
        :model="convertForm"
        :rules="convertRules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item label="所属分类" prop="categoryId">
          <el-select v-model="convertForm.categoryId" placeholder="请选择分类" class="full-width">
            <el-option
              v-for="option in categoryOptions"
              :key="option.id"
              :label="option.name"
              :value="option.id"
              :disabled="Number(option.status) === 0"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标准问题" prop="question">
          <el-input v-model="convertForm.question" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="标准答案" prop="answer">
          <el-input
            v-model="convertForm.answer"
            type="textarea"
            :rows="5"
            maxlength="2000"
            show-word-limit
            resize="none"
            placeholder="给出可执行的完整回答，包含步骤、时效与注意事项"
          />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="关键词（逗号分隔）">
            <el-input
              v-model="convertForm.keywords"
              maxlength="255"
              placeholder="退款,退货,申请退款"
            />
          </el-form-item>
          <el-form-item label="标签（逗号分隔）">
            <el-input v-model="convertForm.tags" maxlength="255" placeholder="售后,退款" />
          </el-form-item>
        </div>
        <el-form-item>
          <el-checkbox v-model="convertForm.keepAsSimilar">
            把原问法同时登记为相似问法
          </el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="convertVisible = false">取消</el-button>
        <el-button type="primary" :loading="convertSubmitting" @click="submitConvert">
          创建词条
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import {
  batchIgnoreKbUnmatchedQuestions,
  convertKbUnmatchedQuestion,
  getKbCategories,
  getKbDuplicates,
  getKbHealth,
  getKbImportTasks,
  getKbUnmatchedQuestions,
  ignoreKbUnmatchedQuestion,
  importKbPackage,
  restoreKbUnmatchedQuestion
} from '@/api'
import { getUser } from '@/api/http'
import { prefersReducedMotion, useCountUp } from '@/composables/motion'
import { usePagedList } from '@/composables/usePagedList'
import { formatDateTime, percent, shortDate } from '@/utils/dict'

const router = useRouter()
const canManage = (getUser()?.roleCode || '') === 'ADMIN'

/* 频次条与健康度数字滚动：数据到达后从 0 滚到实际值 */
const CountNum = defineComponent({
  props: {
    value: { type: Number, default: 0 },
    decimals: { type: Number, default: 0 }
  },
  setup(props) {
    const { display } = useCountUp(computed(() => Number(props.value) || 0), {
      decimals: props.decimals,
      separator: false,
      duration: 640
    })
    return () => h('span', null, String(display.value))
  }
})

const STATUS_DICT = {
  PENDING: { label: '待处理', tone: 'amber' },
  CONVERTED: { label: '已转词条', tone: 'teal' },
  IGNORED: { label: '已忽略', tone: 'slate' }
}

const STATUS_OPTIONS = [
  { label: '待处理', value: 'PENDING' },
  { label: '已转词条', value: 'CONVERTED' },
  { label: '已忽略', value: 'IGNORED' }
]

const statusLabel = (status) => STATUS_DICT[status]?.label || status || '—'
const statusTone = (status) => STATUS_DICT[status]?.tone || 'slate'
const channelLabel = (channel) =>
  channel === 'EMBED' ? '网页接入' : channel === 'SITE' ? '站内会话' : '—'
const percentText = (value) => `${Math.round((Number(value) || 0) * 100)}%`
const scoreText = (value) => (value === null || value === undefined ? '—' : percentText(value))

/* ---------- 未命中问题 ---------- */
const {
  filters,
  rows,
  total,
  pageNum,
  pageSize,
  loading,
  loaded,
  error,
  load,
  search,
  reset,
  changePage,
  changeSize
} = usePagedList(getKbUnmatchedQuestions, { status: 'PENDING', channel: '', keyword: '' })

const hasFilter = computed(
  () =>
    Boolean(filters.keyword) ||
    Boolean(filters.channel) ||
    (filters.status !== '' && filters.status !== 'PENDING')
)

const selection = ref([])
const batchLoading = ref(false)

/* 出现次数频次条：按当前页最大值归一，宽度过渡由全局 .hit-bar 提供 */
const maxAsk = computed(() => Math.max(1, ...rows.value.map((row) => Number(row.askCount) || 0)))

function askPercent(count) {
  const value = Number(count) || 0
  return `${Math.max(value > 0 ? 8 : 0, Math.round((value / maxAsk.value) * 100))}%`
}

/* 首屏骨架屏；筛选结果 160ms 淡入；忽略后行先淡出再移除 */
const resultFade = ref(false)
const removingId = ref(null)
let resultFadeTimer = 0

const rowClassName = ({ row }) => (row.id === removingId.value ? 'row-removing' : '')

/* 行先 160ms 淡出，数据刷新后再解除标记，避免列表跳位与闪现 */
async function removeRowAfterFade(id) {
  removingId.value = id
  await new Promise((resolve) => {
    window.setTimeout(resolve, prefersReducedMotion() ? 0 : 160)
  })
  await load()
  removingId.value = null
}

watch(loading, (now, before) => {
  if (!before || now || prefersReducedMotion()) return
  resultFade.value = false
  requestAnimationFrame(() => {
    resultFade.value = true
    window.clearTimeout(resultFadeTimer)
    resultFadeTimer = window.setTimeout(() => {
      resultFade.value = false
    }, 200)
  })
})

function onSelectionChange(list) {
  selection.value = list || []
}

async function ignoreOne(row) {
  try {
    await ElMessageBox.confirm(`忽略后该问题不再出现在待处理列表：${row.question}`, '忽略未命中问题', {
      type: 'warning',
      confirmButtonText: '忽略',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await ignoreKbUnmatchedQuestion(row.id)
    ElMessage.success('已忽略该问题')
    await removeRowAfterFade(row.id)
  } catch (err) {
    ElMessage.error(err?.message || '操作失败，请稍后重试')
  }
}

async function restoreOne(row) {
  try {
    await restoreKbUnmatchedQuestion(row.id)
    ElMessage.success('已恢复为待处理')
    load()
  } catch (err) {
    ElMessage.error(err?.message || '操作失败，请稍后重试')
  }
}

async function batchIgnore() {
  if (!selection.value.length) return
  try {
    await ElMessageBox.confirm(
      `确认忽略选中的 ${selection.value.length} 条未命中问题？`,
      '批量忽略',
      { type: 'warning', confirmButtonText: '忽略', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  batchLoading.value = true
  try {
    const data = await batchIgnoreKbUnmatchedQuestions(selection.value.map((item) => item.id))
    ElMessage.success(`已忽略 ${data?.count ?? 0} 条问题`)
    selection.value = []
    load()
  } catch (err) {
    ElMessage.error(err?.message || '批量忽略失败，请稍后重试')
  } finally {
    batchLoading.value = false
  }
}

function goEntry() {
  router.push({ name: 'admin-knowledge' })
}

/* ---------- 转词条 ---------- */
const convertVisible = ref(false)
const convertSubmitting = ref(false)
const convertRecord = ref(null)
const convertFormRef = ref(null)
const categoryList = ref([])

const convertForm = reactive({
  categoryId: '',
  question: '',
  answer: '',
  keywords: '',
  tags: '',
  keepAsSimilar: true
})

const convertRules = {
  categoryId: [{ required: true, message: '请选择所属分类', trigger: 'change' }],
  question: [
    { required: true, message: '请填写标准问题', trigger: 'blur' },
    { min: 2, max: 255, message: '标准问题长度须为 2-255 个字符', trigger: 'blur' }
  ],
  answer: [
    { required: true, message: '请填写标准答案', trigger: 'blur' },
    { min: 2, max: 2000, message: '标准答案长度须为 2-2000 个字符', trigger: 'blur' }
  ]
}

const categoryOptions = computed(() =>
  categoryList.value.map((item) => ({
    id: item.id,
    name: item.name,
    status: item.status
  }))
)

async function loadCategories() {
  try {
    const data = await getKbCategories({ tree: false })
    categoryList.value = Array.isArray(data) ? data : data?.list || []
  } catch {
    categoryList.value = []
  }
}

function openConvert(row) {
  convertRecord.value = row
  convertForm.categoryId = ''
  convertForm.question = row.question || ''
  convertForm.answer = ''
  convertForm.keywords = ''
  convertForm.tags = ''
  convertForm.keepAsSimilar = true
  convertVisible.value = true
}

function resetConvert() {
  convertRecord.value = null
  convertFormRef.value?.clearValidate()
}

async function submitConvert() {
  if (!convertRecord.value) return
  try {
    await convertFormRef.value.validate()
  } catch {
    return
  }
  convertSubmitting.value = true
  try {
    const data = await convertKbUnmatchedQuestion(convertRecord.value.id, {
      categoryId: convertForm.categoryId,
      question: convertForm.question.trim(),
      answer: convertForm.answer.trim(),
      keywords: convertForm.keywords.trim(),
      tags: convertForm.tags.trim(),
      keepAsSimilar: convertForm.keepAsSimilar
    })
    ElMessage.success(`已创建词条「${data?.entryQuestion || convertForm.question}」`)
    convertVisible.value = false
    load()
    if (loadedTabs.health) loadHealth()
  } catch (err) {
    ElMessage.error(err?.message || '转词条失败，请稍后重试')
  } finally {
    convertSubmitting.value = false
  }
}

/* ---------- 健康度 ---------- */
const health = ref(null)
const healthLoading = ref(false)
const healthError = ref('')

async function loadHealth() {
  healthLoading.value = true
  healthError.value = ''
  try {
    health.value = await getKbHealth()
    loadedTabs.health = true
  } catch (err) {
    health.value = null
    healthError.value = err?.message || '健康度数据加载失败，请稍后重试'
  } finally {
    healthLoading.value = false
  }
}

const healthCards = computed(() => {
  const data = health.value || {}
  const enabled = Number(data.entryEnabled) || 0
  const entryTotal = Number(data.entryTotal) || 0
  const similarTotal = Number(data.similarTotal) || 0
  return [
    {
      label: '词条总数',
      value: entryTotal,
      unit: '条',
      foot: `启用 ${enabled} 条`
    },
    {
      label: '相似问法',
      value: similarTotal,
      unit: '条',
      foot: enabled ? `平均每个词条 ${(similarTotal / enabled).toFixed(1)} 条` : '暂无启用词条'
    },
    {
      label: '待处理未命中',
      value: Number(data.unmatchedPending) || 0,
      unit: '条',
      foot: `累计采集 ${Number(data.unmatchedTotal) || 0} 条`
    },
    {
      label: '零命中文词条',
      value: Number(data.zeroHitEntries) || 0,
      unit: '条',
      foot: enabled ? `占启用词条 ${percent(data.zeroHitEntries, enabled)}%` : '暂无启用词条'
    },
    {
      label: '疑似重复 / 冲突',
      value: Number(data.duplicatePairs) || 0,
      unit: '对',
      foot: '相似度 ≥ 75% 的问法对'
    },
    {
      label: '覆盖分类',
      value: Array.isArray(data.categories) ? data.categories.length : 0,
      unit: '个',
      foot: '含启用词条的分类数'
    }
  ]
})

const trendMax = computed(() =>
  Math.max(1, ...(health.value?.trend || []).map((item) => Number(item.count) || 0))
)

function trendHeight(count) {
  const value = Number(count) || 0
  return `${Math.max(value > 0 ? 6 : 2, Math.round((value / trendMax.value) * 100))}%`
}

function coveragePercent(row) {
  const entryCount = Number(row?.entryCount) || 0
  if (!entryCount) return 0
  const zeroHit = Number(row?.zeroHitCount) || 0
  return Math.max(0, Math.min(100, Math.round(((entryCount - zeroHit) / entryCount) * 100)))
}

/* ---------- 查重 ---------- */
const duplicates = ref([])
const dupLoading = ref(false)
const dupLoaded = ref(false)
const dupError = ref('')

async function loadDuplicates() {
  dupLoading.value = true
  dupError.value = ''
  try {
    const data = await getKbDuplicates({ limit: 100 })
    duplicates.value = Array.isArray(data) ? data : []
    loadedTabs.duplicates = true
  } catch (err) {
    duplicates.value = []
    dupError.value = err?.message || '查重检测失败，请稍后重试'
  } finally {
    dupLoaded.value = true
    dupLoading.value = false
  }
}

/* ---------- 知识包导入 ---------- */
const packageName = ref('')
const importText = ref('')
const importing = ref(false)
const importError = ref('')
const importResult = ref(null)
const tasks = ref([])
const tasksLoading = ref(false)

const IMPORT_STATUS_DICT = {
  SUCCESS: { label: '全部成功', tone: 'teal' },
  PARTIAL: { label: '部分成功', tone: 'amber' },
  FAILED: { label: '导入失败', tone: 'red' }
}

const importStatusLabel = (status) => IMPORT_STATUS_DICT[status]?.label || status || '—'
const importStatusTone = (status) => IMPORT_STATUS_DICT[status]?.tone || 'slate'

async function loadTasks() {
  tasksLoading.value = true
  try {
    const data = await getKbImportTasks({ limit: 20 })
    tasks.value = Array.isArray(data) ? data : []
    loadedTabs.import = true
  } catch {
    tasks.value = []
  } finally {
    tasksLoading.value = false
  }
}

const SAMPLE_PACKAGE = {
  packageName: '电商售后通用知识包',
  entries: [
    {
      categoryName: '退款售后',
      question: '如何申请退款？',
      answer: '进入「我的订单」找到对应订单，点击「申请退款」，选择退款原因并提交。审核通过后 1-3 个工作日原路退回。',
      keywords: ['退款', '退货', '申请退款'],
      tags: ['售后'],
      similarQuestions: ['退款怎么操作', '我要退货怎么弄']
    },
    {
      categoryName: '物流配送',
      question: '订单什么时候发货？',
      answer: '现货商品在付款后 24 小时内发出，预售商品以商品页标注的发货时间为准。',
      keywords: ['发货', '什么时候发货'],
      tags: ['物流']
    }
  ]
}

function fillSample() {
  importText.value = JSON.stringify(SAMPLE_PACKAGE, null, 2)
  if (!packageName.value.trim()) packageName.value = SAMPLE_PACKAGE.packageName
  importError.value = ''
}

function parsePackage() {
  const text = importText.value.trim()
  if (!text) throw new Error('请先粘贴知识包 JSON')
  let parsed
  try {
    parsed = JSON.parse(text)
  } catch {
    throw new Error('JSON 解析失败，请检查双引号、逗号与括号是否配对')
  }
  let name = packageName.value.trim()
  let entries = []
  if (Array.isArray(parsed)) {
    entries = parsed
  } else if (parsed && Array.isArray(parsed.entries)) {
    entries = parsed.entries
    if (!name && parsed.packageName) name = String(parsed.packageName).trim()
  } else {
    throw new Error('知识包格式应为 { packageName, entries: [] } 或词条数组')
  }
  if (!name) throw new Error('请填写知识包名称（或在 JSON 中提供 packageName）')
  if (!entries.length) throw new Error('知识包中没有词条')
  if (entries.length > 500) throw new Error('单次最多导入 500 条词条')
  return { packageName: name, entries }
}

async function submitImport() {
  importError.value = ''
  importResult.value = null
  let payload
  try {
    payload = parsePackage()
  } catch (err) {
    importError.value = err.message
    return
  }
  importing.value = true
  try {
    const data = await importKbPackage(payload)
    importResult.value = data
    ElMessage.success(`导入完成：成功 ${data?.successCount ?? 0} 条，失败 ${data?.failCount ?? 0} 条`)
    loadTasks()
    if (loadedTabs.health) loadHealth()
  } catch (err) {
    importError.value = err?.message || '导入失败，请稍后重试'
  } finally {
    importing.value = false
  }
}

/* ---------- 标签页与响应式 ---------- */
const tab = ref('unmatched')
const loadedTabs = reactive({ health: false, duplicates: false, import: false })
const refreshing = ref(false)
const isCompact = ref(false)
let compactQuery = null

function syncCompact(event) {
  isCompact.value = event.matches
}

function onTabChange(name) {
  if (name === 'health' && !loadedTabs.health) loadHealth()
  if (name === 'duplicates' && !loadedTabs.duplicates) loadDuplicates()
  if (name === 'import' && !loadedTabs.import) loadTasks()
}

async function reloadAll() {
  refreshing.value = true
  try {
    load()
    if (tab.value === 'health') await loadHealth()
    if (tab.value === 'duplicates') await loadDuplicates()
    if (tab.value === 'import') await loadTasks()
    if (canManage) loadCategories()
  } finally {
    refreshing.value = false
  }
}

onMounted(() => {
  load()
  if (canManage) loadCategories()
  compactQuery = window.matchMedia('(max-width: 768px)')
  isCompact.value = compactQuery.matches
  compactQuery.addEventListener('change', syncCompact)
})

onBeforeUnmount(() => {
  window.clearTimeout(resultFadeTimer)
  compactQuery?.removeEventListener('change', syncCompact)
})
</script>

<style scoped>
.ops-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}

.ops-panel {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ops-panel-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.ops-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--zk-text);
}

.ops-panel-desc,
.ops-hint {
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

.ops-empty {
  display: grid;
  place-items: center;
  gap: 8px;
  padding: 32px 16px;
  text-align: center;
  font-size: 12.5px;
  color: var(--zk-text-2);
}

.ops-empty p {
  margin: 0;
}

.ops-empty-hint,
.ops-empty-text {
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

.ops-empty-text {
  margin: 0;
  padding: 10px 0;
}

.ops-panel .zk-toolbar {
  padding: 0 0 10px;
}

.ops-panel :deep(.toolbar-search) {
  width: 220px;
}

.table-scroll {
  overflow-x: auto;
}

.table-skeleton {
  padding: 12px 16px 6px;
}

/* 筛选结果淡入：160ms，只动位移与透明度 */
.table-result-fade {
  animation: zk-rise-sm 160ms var(--zk-ease-soft) both;
}

/* 忽略后行先淡出 160ms 再移除，列表不突然跳位 */
.table-scroll :deep(.el-table__row.row-removing > td.el-table__cell) {
  opacity: 0;
  transition: opacity 160ms var(--zk-ease-in);
}

/* 未命中频次条：数字与条同步出现，宽度过渡由全局 .hit-bar 提供 */
.miss-cell {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.cell-question {
  font-size: 13px;
  font-weight: 550;
  line-height: 1.5;
  color: var(--zk-text);
  word-break: break-word;
}

.cell-sub {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.topic-cell {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.topic-name {
  font-size: 12.5px;
  font-weight: 550;
  color: var(--zk-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-time {
  font-size: 12px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.row-ops {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 手机端卡片列表 */
.ops-list {
  display: flex;
  flex-direction: column;
}

.ops-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--zk-border);
}

.ops-item:last-child {
  border-bottom: 0;
}

.ops-item-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.ops-item-meta {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 6px;
  font-size: 11px;
  color: var(--zk-text-3);
}

.ops-item-ops {
  margin-top: 8px;
  display: flex;
  gap: 6px;
}

/* 健康度 */
.health {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}

.metric-card {
  padding: 14px 16px;
  background: var(--zk-surface);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  box-shadow: var(--zk-shadow-sm);
}

.metric-label {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.metric-value {
  margin-top: 6px;
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--zk-text);
}

.metric-value .zk-num {
  font-size: 24px;
}

.metric-unit {
  font-size: 11.5px;
  font-weight: 500;
  color: var(--zk-text-3);
}

.metric-foot {
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--zk-text-2);
}

.health-cols {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 14px;
}

.rank-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px dashed var(--zk-border);
}

.rank-item:last-child {
  border-bottom: 0;
}

.rank-no {
  flex: 0 0 18px;
  font-size: 11.5px;
  font-weight: 650;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.rank-q {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 12.5px;
  color: var(--zk-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-cat {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--zk-text-3);
}

.rank-val {
  flex: 0 0 auto;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-teal-dark);
}

.trend {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  min-height: 132px;
  padding-top: 8px;
  overflow-x: auto;
}

.trend-col {
  flex: 1 0 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.trend-bar-wrap {
  display: flex;
  align-items: flex-end;
  width: 100%;
  height: 88px;
}

.trend-bar {
  display: block;
  width: 100%;
  max-width: 22px;
  margin: 0 auto;
  border-radius: var(--zk-radius-xs);
  background: var(--zk-accent-soft);
  border: 1px solid var(--zk-accent-line);
  border-bottom-color: var(--zk-accent);
  transition: height var(--zk-dur-base) var(--zk-ease-out);
}

.trend-num {
  font-size: 10.5px;
  color: var(--zk-text-2);
}

.trend-label {
  font-size: 10px;
  color: var(--zk-text-3);
  white-space: nowrap;
}

.cov {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  color: var(--zk-text-2);
}

.cov-bar {
  flex: 1 1 auto;
  height: 4px;
  border-radius: var(--zk-radius-xs);
  background: #ececef;
  overflow: hidden;
}

.cov-bar i {
  display: block;
  height: 100%;
  border-radius: var(--zk-radius-xs);
  background: var(--zk-teal-dark);
}

/* 查重 */
.dup-q {
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--zk-text);
}

.dup-reason {
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-2);
}

/* 导入 */
.import-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.import-name {
  max-width: 420px;
}

.import-text :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
  line-height: 1.6;
}

.ops-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ops-error {
  font-size: 12px;
  line-height: 1.6;
  color: #b4123e;
  background: #fff0f3;
  border: 1px solid #f7c8d3;
  border-radius: var(--zk-radius);
  padding: 8px 10px;
}

.import-result {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border: 1px dashed var(--zk-border);
  border-radius: var(--zk-radius);
  background: var(--zk-surface-2);
}

.import-result-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--zk-text-2);
}

.import-line {
  margin: 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-2);
}

.import-errors {
  margin: 0;
  padding-left: 18px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--zk-text-3);
}

.task-list {
  display: flex;
  flex-direction: column;
}

.task-item {
  padding: 10px 0;
  border-bottom: 1px dashed var(--zk-border);
}

.task-item:last-child {
  border-bottom: 0;
}

.task-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.task-name {
  font-size: 12.5px;
  font-weight: 550;
  color: var(--zk-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-meta {
  margin-top: 4px;
  display: flex;
  gap: 6px;
  font-size: 11px;
  color: var(--zk-text-3);
}

.task-time {
  margin-top: 2px;
  font-size: 11px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.task-error {
  margin: 4px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: #b4123e;
}

/* 转词条 */
.convert-origin {
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  background: var(--zk-surface-2);
}

.ops-label {
  font-size: 11px;
  color: var(--zk-text-3);
  letter-spacing: 0.4px;
}

.convert-question {
  margin: 4px 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.6;
  color: var(--zk-text);
}

.convert-times {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.full-width {
  width: 100%;
}

@media (max-width: 1100px) {
  .import-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  .ops-panel {
    padding: 12px;
  }

  .metric-value,
  .metric-value .zk-num {
    font-size: 20px;
  }

  .form-row {
    grid-template-columns: 1fr;
  }

  .import-name {
    max-width: none;
  }

  .trend {
    gap: 4px;
  }
}
</style>
