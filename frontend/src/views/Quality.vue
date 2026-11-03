<template>
  <div class="zk-page">
    <PageHeader
      title="智能质检"
      description="会话结束后按启用规则全量自动质检：语义与情感类由大模型判定，服务规范与敏感词类由规则引擎判定，得分低于合格线自动标记不合格。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="reloadAll">刷新</el-button>
        <el-button :icon="Aim" :loading="scanning" @click="runScan">立即扫描</el-button>
        <el-button type="primary" :icon="Search" @click="openPicker">发起质检</el-button>
      </template>
    </PageHeader>

    <section class="metric-grid" aria-label="质检概览">
      <article class="metric-card indigo">
        <div class="metric-top">
          <span class="metric-label">已质检会话</span>
          <span class="metric-icon"><el-icon><Collection /></el-icon></span>
        </div>
        <div class="metric-value"><CountNum :value="overview.recordCount" /><small>条</small></div>
        <div class="metric-foot">已结束会话 {{ overview.closedSessionCount }} 条</div>
      </article>
      <article class="metric-card teal">
        <div class="metric-top">
          <span class="metric-label">质检合格率</span>
          <span class="metric-icon"><el-icon><CircleCheck /></el-icon></span>
        </div>
        <div class="metric-value"><CountNum :value="overview.passRate" :decimals="1" /><small>%</small></div>
        <div class="metric-foot">合格 {{ overview.passCount }} · 不合格 {{ overview.failCount }}</div>
      </article>
      <article class="metric-card green">
        <div class="metric-top">
          <span class="metric-label">平均得分</span>
          <span class="metric-icon"><el-icon><Trophy /></el-icon></span>
        </div>
        <div class="metric-value"><CountNum :value="overview.avgScore" :decimals="1" /><small>分</small></div>
        <div class="metric-foot">A≥90 / B≥80 / C≥70 / D&lt;70</div>
      </article>
      <article class="metric-card amber">
        <div class="metric-top">
          <span class="metric-label">待复核</span>
          <span class="metric-icon"><el-icon><Warning /></el-icon></span>
        </div>
        <div class="metric-value"><CountNum :value="overview.pendingCount" /><small>条</small></div>
        <div class="metric-foot">已复核 {{ overview.reviewedCount }} · 已确认 {{ overview.confirmedCount }}</div>
      </article>
      <article class="metric-card indigo">
        <div class="metric-top">
          <span class="metric-label">启用规则</span>
          <span class="metric-icon"><el-icon><SetUp /></el-icon></span>
        </div>
        <div class="metric-value"><CountNum :value="overview.ruleCount" /><small>条</small></div>
        <div class="metric-foot">四类规则：语义 / 情感 / 服务 / 敏感词</div>
      </article>
      <article class="metric-card teal">
        <div class="metric-top">
          <span class="metric-label">质检覆盖率</span>
          <span class="metric-icon"><el-icon><DataLine /></el-icon></span>
        </div>
        <div class="metric-value"><CountNum :value="overview.coverageRate" :decimals="1" /><small>%</small></div>
        <div class="metric-foot">已质检会话 / 已结束会话</div>
      </article>
    </section>

    <section class="insight-grid">
      <article class="zk-card insight-card">
        <header class="insight-head">
          <h3>等级分布</h3>
          <span class="insight-hint">按自动质检得分归档</span>
        </header>
        <ul class="grade-list" style="--zk-stagger: 40ms">
          <li
            v-for="(row, index) in gradeRows"
            :key="row.grade"
            v-reveal="{ index }"
            class="grade-item"
          >
            <div class="grade-meta">
              <StatusPill :label="`${row.grade} ${row.gradeName}`" :tone="dictTone(QUALITY_GRADE_DICT, row.grade)" />
              <span class="grade-count"><CountNum :value="row.count" /> 条</span>
            </div>
            <div class="bar-track">
              <div class="bar-fill" :class="`tone-${dictTone(QUALITY_GRADE_DICT, row.grade)}`" :style="{ width: barWidth(row.count) }" />
            </div>
          </li>
        </ul>
      </article>

      <article class="zk-card insight-card">
        <header class="insight-head">
          <h3>高频命中规则</h3>
          <span class="insight-hint">Top 5 · 命中次数</span>
        </header>
        <ul v-if="topRules.length" class="rank-list" style="--zk-stagger: 40ms">
          <li
            v-for="(row, index) in topRules"
            :key="row.ruleCode"
            v-reveal="{ index }"
            class="rank-item"
          >
            <span class="rank-no" :class="{ 'is-top': index < 3 }">{{ index + 1 }}</span>
            <div class="rank-main">
              <span class="rank-name">{{ row.ruleName }}</span>
              <StatusPill :label="row.ruleTypeName" :tone="dictTone(QUALITY_RULE_TYPE_DICT, row.ruleType)" dot />
            </div>
            <span class="rank-value zk-num"><CountNum :value="row.hitCount" /></span>
          </li>
        </ul>
        <p v-else class="insight-empty">暂无命中数据，完成一次质检后展示</p>
      </article>

      <article class="zk-card insight-card">
        <header class="insight-head">
          <h3>坐席质检表现</h3>
          <span class="insight-hint">按平均分升序</span>
        </header>
        <ul v-if="topAgents.length" class="rank-list" style="--zk-stagger: 40ms">
          <li
            v-for="(row, index) in topAgents"
            :key="row.agentId"
            v-reveal="{ index }"
            class="rank-item"
          >
            <span class="avatar-chip">{{ (row.agentName || '坐').slice(0, 1) }}</span>
            <div class="rank-main">
              <span class="rank-name">{{ row.agentName || `坐席 #${row.agentId}` }}</span>
              <span class="rank-sub">质检 {{ row.recordCount }} 条 · 不合格 {{ row.failCount }} 条</span>
            </div>
            <span class="rank-value zk-num"><CountNum :value="row.avgScore" :decimals="1" /></span>
          </li>
        </ul>
        <p v-else class="insight-empty">暂无坐席数据</p>
      </article>
    </section>

    <section class="zk-card compare-card" aria-label="人机质检对比">
      <header class="insight-head">
        <h3>人机质检对比</h3>
        <span class="insight-hint">
          对比样本 {{ comparison.reviewedCount }} 条（已复核 + 已确认） · 区间内质检 {{ comparison.recordCount }} 条
        </span>
      </header>
      <div class="compare-grid">
        <div class="compare-cell">
          <span class="compare-label">自动判定合格率</span>
          <b class="compare-value">{{ comparison.autoPassRate }}<small>%</small></b>
        </div>
        <div class="compare-cell">
          <span class="compare-label">复核后合格率</span>
          <b class="compare-value">{{ comparison.reviewPassRate }}<small>%</small></b>
        </div>
        <div class="compare-cell">
          <span class="compare-label">平均自动分 / 复核分</span>
          <b class="compare-value">{{ comparison.avgAutoScore }}<small> / {{ comparison.avgReviewScore }}</small></b>
        </div>
        <div class="compare-cell">
          <span class="compare-label">记录一致率</span>
          <b class="compare-value">{{ comparison.consistencyRate }}<small>%</small></b>
          <span class="compare-sub">一致 {{ comparison.sameCount }} · 上调 {{ comparison.upCount }} · 下调 {{ comparison.downCount }}</span>
        </div>
        <div class="compare-cell">
          <span class="compare-label">明细一致率</span>
          <b class="compare-value">{{ comparison.itemAgreeRate }}<small>%</small></b>
          <span class="compare-sub">改判 {{ comparison.itemOverturn }} / {{ comparison.itemTotal }} 行规则</span>
        </div>
        <div class="compare-cell">
          <span class="compare-label">平均分差</span>
          <b class="compare-value" :class="diffToneClass">{{ signedDiff }}<small>分</small></b>
          <span class="compare-sub">正数表示人工上调</span>
        </div>
      </div>
      <div class="compare-body">
        <ul class="diff-list" aria-label="复核分差分布">
          <li v-for="row in comparison.diffs" :key="row.bucket" class="diff-item">
            <span class="diff-label">{{ row.label }}</span>
            <div class="bar-track">
              <div class="bar-fill" :class="`tone-${diffTone(row.bucket)}`" :style="{ width: diffWidth(row.count) }" />
            </div>
            <span class="diff-count zk-num">{{ row.count }}</span>
          </li>
        </ul>
        <div v-if="comparison.rules.length" class="compare-rules">
          <el-table :data="comparison.rules" size="small" max-height="268">
            <el-table-column label="规则" min-width="158">
              <template #default="{ row }">
                <div class="cell-stack">
                  <span class="cell-strong">{{ row.ruleName }}</span>
                  <StatusPill
                    :label="row.ruleTypeName"
                    :tone="dictTone(QUALITY_RULE_TYPE_DICT, row.ruleType)"
                    dot
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="自动命中" width="90" align="right">
              <template #default="{ row }"><span class="zk-num">{{ row.autoHitCount }}</span></template>
            </el-table-column>
            <el-table-column label="复核命中" width="90" align="right">
              <template #default="{ row }"><span class="zk-num">{{ row.reviewHitCount }}</span></template>
            </el-table-column>
            <el-table-column label="改判" width="76" align="right">
              <template #default="{ row }">
                <span class="zk-num" :class="{ 'diff-hit': Number(row.overturnCount) > 0 }">{{ row.overturnCount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="一致率" width="92" align="right">
              <template #default="{ row }"><span class="zk-num">{{ row.agreeRate }}%</span></template>
            </el-table-column>
          </el-table>
        </div>
        <p v-else class="insight-empty">暂无人机对比数据：完成一次人工复核后展示逐规则对比</p>
      </div>
    </section>

    <div class="zk-card">
      <div class="zk-toolbar">
        <el-input
          v-model="filters.keyword"
          class="el-input--wide"
          placeholder="搜索会话编号 / 坐席"
          :prefix-icon="Search"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="filters.grade" class="filter-select" placeholder="全部等级" @change="search">
          <el-option v-for="item in QUALITY_GRADE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.status" class="filter-select" placeholder="全部状态" @change="search">
          <el-option
            v-for="item in QUALITY_RECORD_STATUS_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          class="date-picker"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          unlink-panels
          @change="handleDateChange"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button @click="resetAll">重置</el-button>
        <div class="grow" />
        <span class="toolbar-meta">共 <b class="zk-num">{{ total }}</b> 条质检记录</span>
      </div>

      <div
        class="table-scroll"
        :class="{ 'table-result-fade': resultFade }"
        role="region"
        aria-label="质检记录列表"
        tabindex="0"
      >
        <ZkSkeleton v-if="!loaded" variant="table" :rows="10" :cols="9" class="table-skeleton" />
        <el-table
          v-else
          v-loading="loading"
          class="zk-table-fade-in"
          :data="rows"
          row-key="id"
          aria-label="质检记录列表"
        >
          <el-table-column label="会话 / 坐席" min-width="220">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-strong">{{ row.sessionNo }}</span>
                <span class="cell-muted">{{ row.agentName || '纯机器人会话' }} · 消息 {{ row.messageCount }} 条</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="会话结束" width="158">
            <template #default="{ row }">
              <span class="cell-muted">{{ formatDateTime(row.sessionEndTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="生效得分" width="172">
            <template #default="{ row }">
              <div class="score-cell">
                <span class="score-num">{{ row.effectiveScore }}</span>
                <StatusPill
                  :label="`${row.effectiveGrade} ${row.effectiveGradeName}`"
                  :tone="dictTone(QUALITY_GRADE_DICT, row.effectiveGrade)"
                />
                <span v-if="Number(row.effectiveScore) !== Number(row.score)" class="score-auto">
                  自动 {{ row.score }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="判定" width="104">
            <template #default="{ row }">
              <StatusPill
                :label="Number(row.effectivePassFlag) === 1 ? '合格' : '不合格'"
                :tone="Number(row.effectivePassFlag) === 1 ? 'teal' : 'red'"
              />
            </template>
          </el-table-column>
          <el-table-column label="命中 / 规则" width="118">
            <template #default="{ row }">
              <span class="cell-muted">命中 <b class="zk-num">{{ row.hitCount }}</b> / {{ row.ruleCount }} 条</span>
            </template>
          </el-table-column>
          <el-table-column label="引擎" width="126">
            <template #default="{ row }">
              <StatusPill
                :label="row.engineName || dictLabel(QUALITY_ENGINE_DICT, row.engine)"
                :tone="dictTone(QUALITY_ENGINE_DICT, row.engine)"
                dot
              />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <StatusPill
                :label="row.statusName || dictLabel(QUALITY_RECORD_STATUS_DICT, row.status)"
                :tone="dictTone(QUALITY_RECORD_STATUS_DICT, row.status)"
              />
            </template>
          </el-table-column>
          <el-table-column label="质检时间" width="158">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-muted">{{ formatDateTime(row.evaluateTime) }}</span>
                <span class="cell-muted">{{ row.evaluateBy ? '手动质检' : '自动质检' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="122" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" class="op-btn" @click="openDetail(row)">评分卡</el-button>
              <el-button
                v-if="row.status !== 'CONFIRMED'"
                link
                type="primary"
                class="op-btn"
                @click="openDetail(row)"
              >
                {{ row.status === 'REVIEWED' ? '归档' : '复核' }}
              </el-button>
            </template>
          </el-table-column>

          <template #empty>
            <ZkEmpty
              size="sm"
              :title="error ? '质检记录加载失败' : '暂无质检记录'"
              :hint="
                error
                  ? '请稍后重新加载，或确认后端服务是否正常'
                  : '点击右上角「立即扫描」批量质检已结束会话，或「发起质检」选择单个会话'
              "
            >
              <el-button v-if="error" @click="reloadAll">重新加载</el-button>
              <el-button v-else type="primary" @click="openPicker">发起质检</el-button>
            </ZkEmpty>
          </template>
        </el-table>
      </div>

      <div class="zk-pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :current-page="pageNum"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          @current-change="changePage"
          @size-change="changeSize"
        />
      </div>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      质检对象为已结束且有对话内容的会话，系统欢迎语与内部备注不参与评分 · 得分 = 100 - 命中规则扣分合计（下限 0） · 每条规则的判定证据与改进建议可在评分卡中查看
    </div>

    <!-- 评分卡 -->
    <el-drawer v-model="drawerVisible" size="720px" title="质检评分卡" append-to-body>
      <div v-if="detail" class="detail">
        <div class="card-head">
          <div class="score-ring" :class="`tone-${dictTone(QUALITY_GRADE_DICT, detail.grade)}`">
            <span class="score-ring-num"><CountNum :value="detail.score" /></span>
            <span class="score-ring-label">分</span>
          </div>
          <div class="card-head-main">
            <div class="card-head-tags">
              <StatusPill :label="`${detail.grade} ${detail.gradeName}`" :tone="dictTone(QUALITY_GRADE_DICT, detail.grade)" />
              <StatusPill
                :label="Number(detail.passFlag) === 1 ? '合格' : '不合格'"
                :tone="Number(detail.passFlag) === 1 ? 'teal' : 'red'"
              />
              <StatusPill
                :label="detail.statusName || dictLabel(QUALITY_RECORD_STATUS_DICT, detail.status)"
                :tone="dictTone(QUALITY_RECORD_STATUS_DICT, detail.status)"
              />
              <StatusPill
                :label="detail.engineName || dictLabel(QUALITY_ENGINE_DICT, detail.engine)"
                :tone="dictTone(QUALITY_ENGINE_DICT, detail.engine)"
                dot
              />
            </div>
            <p class="card-head-session">{{ detail.sessionNo }}</p>
            <p class="card-head-sub">
              合格线 {{ detail.passScore }} 分 · 扣分合计 {{ detail.totalDeduct }} · 命中 {{ detail.hitCount }} / {{ detail.ruleCount }} 条规则
            </p>
          </div>
        </div>

        <dl class="meta-grid">
          <div><dt>接待坐席</dt><dd>{{ detail.agentName || '纯机器人会话' }}</dd></div>
          <div><dt>会话结束</dt><dd>{{ formatDateTime(detail.sessionEndTime) }}</dd></div>
          <div><dt>质检时间</dt><dd>{{ formatDateTime(detail.evaluateTime) }}</dd></div>
          <div><dt>质检方式</dt><dd>{{ detail.evaluateBy ? '管理端手动发起' : '定时任务自动质检' }}</dd></div>
          <div><dt>消息构成</dt><dd>客户 {{ detail.customerMsgCount }} · 坐席 {{ detail.agentMsgCount }} · 机器人 {{ detail.aiMsgCount }}</dd></div>
          <div><dt>质检模型</dt><dd>{{ detail.aiModel || '未使用大模型' }}</dd></div>
        </dl>

        <section v-if="detail.aiSummary" class="ai-block" aria-label="大模型质检结论">
          <h4>大模型结论</h4>
          <p>{{ detail.aiSummary }}</p>
        </section>

        <section class="item-block" aria-label="逐规则命中明细">
          <div class="item-head">
            <h4>规则判定明细</h4>
            <span class="item-hint">命中项已扣分，未命中项仅记录判定口径</span>
          </div>
          <ul class="item-list" style="--zk-stagger: 40ms">
            <li
              v-for="(item, index) in detail.items"
              :key="item.id || item.ruleCode"
              v-reveal="{ index: Math.min(index, 9) }"
              class="item-row"
              :class="{ 'is-hit': Number(item.hit) === 1 }"
            >
              <div class="item-main">
                <div class="item-line">
                  <span class="item-name">{{ item.ruleName }}</span>
                  <StatusPill :label="item.ruleTypeName" :tone="dictTone(QUALITY_RULE_TYPE_DICT, item.ruleType)" dot />
                  <StatusPill :label="item.severityName" :tone="dictTone(QUALITY_SEVERITY_DICT, item.severity)" />
                  <StatusPill
                    :label="Number(item.hit) === 1 ? '命中' : '未命中'"
                    :tone="Number(item.hit) === 1 ? 'red' : 'slate'"
                  />
                  <span v-if="Number(item.deductScore) > 0" class="item-deduct">-{{ item.deductScore }} 分</span>
                </div>
                <p v-if="item.evidence" class="item-evidence">{{ item.evidence }}</p>
                <p v-if="item.suggestion" class="item-suggestion">改进建议：{{ item.suggestion }}</p>
              </div>
              <span class="item-source">{{ item.source === 'AI' ? '大模型' : '规则' }}</span>
            </li>
          </ul>
        </section>

        <section class="review-block" aria-label="人工复核">
          <div class="item-head">
            <h4>人工复核</h4>
            <StatusPill
              :label="detail.statusName || dictLabel(QUALITY_RECORD_STATUS_DICT, detail.status)"
              :tone="dictTone(QUALITY_RECORD_STATUS_DICT, detail.status)"
            />
          </div>

          <dl v-if="detail.reviewTime" class="meta-grid">
            <div><dt>复核人</dt><dd>{{ detail.reviewerName || '—' }}</dd></div>
            <div><dt>复核动作</dt><dd>{{ dictLabel(QUALITY_REVIEW_ACTION_DICT, detail.reviewAction, '已复核') }}</dd></div>
            <div><dt>复核得分</dt><dd>{{ detail.reviewScore ?? '—' }}（自动 {{ detail.score }}）</dd></div>
            <div><dt>复核时间</dt><dd>{{ formatDateTime(detail.reviewTime) }}</dd></div>
          </dl>
          <p v-if="detail.reviewComment" class="review-comment">
            {{ detail.status === 'PENDING' ? '驳回原因：' : '复核意见：' }}{{ detail.reviewComment }}
          </p>

          <template v-if="detail.status === 'PENDING'">
            <div class="review-form">
              <div class="review-line">
                <span class="review-label">复核得分</span>
                <el-input-number
                  v-model="reviewForm.reviewScore"
                  :min="0"
                  :max="100"
                  :step="1"
                  :disabled="reviewForm.overturned"
                  controls-position="right"
                />
                <span class="review-hint">
                  {{ reviewForm.overturned ? '已逐条改判，复核分按扣分合计自动核算' : '不改判明细时可直接调整总分' }}
                </span>
              </div>
              <el-input
                v-model="reviewForm.reviewComment"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
                placeholder="填写复核意见：调分依据、改判理由等（必填）"
              />
              <div class="rejudge-head">
                <span>逐条改判</span>
                <span class="review-hint">只改需要修正的规则行，未动摇的行沿用自动结论</span>
              </div>
              <ul class="rejudge-list" aria-label="逐条改判">
                <li
                  v-for="item in detail.items"
                  :key="item.id"
                  class="rejudge-item"
                  :class="{ 'is-dirty': isDirty(item.id) }"
                >
                  <span class="rejudge-name">{{ item.ruleName }}</span>
                  <el-switch
                    :model-value="reviewState[item.id]?.hit ?? 0"
                    :active-value="1"
                    :inactive-value="0"
                    active-text="命中"
                    inactive-text="未命中"
                    size="small"
                    @update:model-value="(value) => onHitChange(item, value)"
                  />
                  <el-input-number
                    v-if="(reviewState[item.id]?.hit ?? 0) === 1"
                    v-model="reviewState[item.id].deductScore"
                    :min="0"
                    :max="30"
                    :step="1"
                    size="small"
                    controls-position="right"
                    @change="syncReviewScore"
                  />
                  <span class="rejudge-auto">
                    自动 {{ Number(item.hit) === 1 ? `命中 -${item.deductScore}` : '未命中' }}
                  </span>
                </li>
              </ul>
            </div>
            <div class="review-actions">
              <el-button type="primary" :loading="reviewing" @click="submitReview">提交复核</el-button>
            </div>
          </template>

          <div v-else-if="detail.status === 'REVIEWED'" class="review-actions">
            <el-button type="primary" :loading="reviewing" @click="confirmReview">确认归档</el-button>
            <el-button :loading="reviewing" @click="rejectReview">驳回退回</el-button>
          </div>

          <p v-else class="review-comment">复核结论已归档，作为考核口径，不可再变更。</p>
        </section>
      </div>
      <div v-else class="detail-loading">
        <ZkSkeleton variant="card" :rows="4" />
      </div>
    </el-drawer>

    <!-- 选择会话发起质检 -->
    <el-dialog v-model="pickerVisible" title="选择会话发起质检" width="760px" append-to-body>
      <div class="picker-toolbar">
        <el-input
          v-model="pickerKeyword"
          class="el-input--wide"
          placeholder="搜索会话编号"
          :prefix-icon="Search"
          clearable
          @keyup.enter="searchPicker"
          @clear="searchPicker"
        />
        <el-button type="primary" :icon="Search" @click="searchPicker">查询</el-button>
        <div class="grow" />
        <span class="toolbar-meta">共 <b class="zk-num">{{ pickerTotal }}</b> 条已结束会话</span>
      </div>
      <div class="picker-scroll">
        <el-table v-loading="pickerLoading" :data="pickerRows" row-key="id" max-height="420">
          <el-table-column label="会话编号" min-width="180">
            <template #default="{ row }">
              <div class="cell-stack">
                <span class="cell-strong">{{ row.sessionNo }}</span>
                <span class="cell-muted">{{ row.agentName || '纯机器人会话' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="结束时间" width="158">
            <template #default="{ row }">
              <span class="cell-muted">{{ formatDateTime(row.endTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="消息数" width="88">
            <template #default="{ row }">
              <span class="zk-num">{{ row.messageCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="质检情况" width="136">
            <template #default="{ row }">
              <StatusPill
                v-if="row.recordId"
                :label="`已质检 ${row.score} 分`"
                :tone="scoreTone(row.score)"
              />
              <StatusPill v-else label="未质检" tone="slate" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="112" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                class="op-btn"
                :loading="evaluatingId === row.id"
                @click="evaluateSession(row)"
              >
                {{ row.recordId ? '重新质检' : '发起质检' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="zk-pager">
        <el-pagination
          small
          background
          layout="total, prev, pager, next"
          :total="pickerTotal"
          :current-page="pickerPage"
          :page-size="8"
          @current-change="changePickerPage"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Aim,
  CircleCheck,
  Collection,
  DataLine,
  Refresh,
  Search,
  SetUp,
  Trophy,
  Warning
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import {
  confirmQualityRecord,
  evaluateQualitySession,
  getQualityComparison,
  getQualityOverview,
  getQualityRecordDetail,
  getQualityRecords,
  getQualitySessions,
  rejectQualityRecord,
  reviewQualityRecord,
  runQualityScan
} from '@/api'
import { prefersReducedMotion, useCountUp } from '@/composables/motion'
import { usePagedList } from '@/composables/usePagedList'
import {
  QUALITY_ENGINE_DICT,
  QUALITY_GRADE_DICT,
  QUALITY_GRADE_OPTIONS,
  QUALITY_RECORD_STATUS_DICT,
  QUALITY_RECORD_STATUS_OPTIONS,
  QUALITY_REVIEW_ACTION_DICT,
  QUALITY_RULE_TYPE_DICT,
  QUALITY_SEVERITY_DICT,
  dictLabel,
  dictTone,
  formatDateTime
} from '@/utils/dict'

/* ---------- 概览 ---------- */
/* 评分与命中数字滚动：数据到达后从 0 滚到实际值，减少动效时直接显示结果 */
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
    return () =>
      h(
        'span',
        null,
        props.decimals > 0 ? String(display.value).replace(/\.0$/, '') : String(display.value)
      )
  }
})

const overview = reactive({
  recordCount: 0,
  passCount: 0,
  failCount: 0,
  passRate: 0,
  avgScore: 0,
  pendingCount: 0,
  reviewedCount: 0,
  confirmedCount: 0,
  ruleCount: 0,
  closedSessionCount: 0,
  coverageRate: 0,
  grades: [],
  rules: [],
  agents: []
})
const overviewLoading = ref(false)

async function loadOverview() {
  overviewLoading.value = true
  try {
    const data = await getQualityOverview()
    Object.assign(overview, {
      recordCount: Number(data?.recordCount ?? 0),
      passCount: Number(data?.passCount ?? 0),
      failCount: Number(data?.failCount ?? 0),
      passRate: Number(data?.passRate ?? 0),
      avgScore: Number(data?.avgScore ?? 0),
      pendingCount: Number(data?.pendingCount ?? 0),
      reviewedCount: Number(data?.reviewedCount ?? 0),
      confirmedCount: Number(data?.confirmedCount ?? 0),
      ruleCount: Number(data?.ruleCount ?? 0),
      closedSessionCount: Number(data?.closedSessionCount ?? 0),
      coverageRate: Number(data?.coverageRate ?? 0),
      grades: Array.isArray(data?.grades) ? data.grades : [],
      rules: Array.isArray(data?.rules) ? data.rules : [],
      agents: Array.isArray(data?.agents) ? data.agents : []
    })
  } catch {
    // 概览失败不阻塞列表，指标保持上一次结果
  } finally {
    overviewLoading.value = false
  }
}

const gradeRows = computed(() => {
  const rows = overview.grades || []
  if (rows.length) return rows
  return ['A', 'B', 'C', 'D'].map((grade) => ({
    grade,
    gradeName: QUALITY_GRADE_DICT[grade]?.label || grade,
    count: 0
  }))
})

const maxGradeCount = computed(() => Math.max(1, ...gradeRows.value.map((row) => Number(row.count) || 0)))
const barWidth = (count) => `${Math.round(((Number(count) || 0) / maxGradeCount.value) * 100)}%`
const topRules = computed(() => (overview.rules || []).slice(0, 5))
const topAgents = computed(() => (overview.agents || []).slice(0, 5))

/* ---------- 人机质检对比（S5-B，QC-07） ---------- */
const comparison = reactive({
  recordCount: 0,
  reviewedCount: 0,
  pendingCount: 0,
  autoPassRate: 0,
  reviewPassRate: 0,
  avgAutoScore: 0,
  avgReviewScore: 0,
  avgDiff: 0,
  sameCount: 0,
  upCount: 0,
  downCount: 0,
  consistencyRate: 0,
  itemTotal: 0,
  itemOverturn: 0,
  itemAgreeRate: 0,
  diffs: [],
  rules: [],
  statuses: []
})

async function loadComparison() {
  try {
    const data = await getQualityComparison()
    Object.assign(comparison, {
      recordCount: Number(data?.recordCount ?? 0),
      reviewedCount: Number(data?.reviewedCount ?? 0),
      pendingCount: Number(data?.pendingCount ?? 0),
      autoPassRate: Number(data?.autoPassRate ?? 0),
      reviewPassRate: Number(data?.reviewPassRate ?? 0),
      avgAutoScore: Number(data?.avgAutoScore ?? 0),
      avgReviewScore: Number(data?.avgReviewScore ?? 0),
      avgDiff: Number(data?.avgDiff ?? 0),
      sameCount: Number(data?.sameCount ?? 0),
      upCount: Number(data?.upCount ?? 0),
      downCount: Number(data?.downCount ?? 0),
      consistencyRate: Number(data?.consistencyRate ?? 0),
      itemTotal: Number(data?.itemTotal ?? 0),
      itemOverturn: Number(data?.itemOverturn ?? 0),
      itemAgreeRate: Number(data?.itemAgreeRate ?? 0),
      diffs: Array.isArray(data?.diffs) ? data.diffs : [],
      rules: Array.isArray(data?.rules) ? data.rules : [],
      statuses: Array.isArray(data?.statuses) ? data.statuses : []
    })
  } catch {
    // 对比报表失败不影响主列表，保持上一次结果
  }
}

const signedDiff = computed(() => {
  const diff = Number(comparison.avgDiff) || 0
  return `${diff > 0 ? '+' : ''}${diff}`
})

const diffToneClass = computed(() => {
  const diff = Number(comparison.avgDiff) || 0
  if (diff > 0) return 'is-up'
  if (diff < 0) return 'is-down'
  return ''
})

const maxDiffCount = computed(() =>
  Math.max(1, ...(comparison.diffs || []).map((row) => Number(row.count) || 0))
)
const diffWidth = (count) => `${Math.round(((Number(count) || 0) / maxDiffCount.value) * 100)}%`
const diffTone = (bucket) => {
  const value = String(bucket || '')
  if (value === 'SAME') return 'teal'
  return value.startsWith('UP_') ? 'indigo' : 'red'
}

/* ---------- 记录列表 ---------- */
const dateRange = ref([])

const { filters, rows, total, pageNum, pageSize, loading, loaded, error, load, search, reset, changePage, changeSize } =
  usePagedList(getQualityRecords, { keyword: '', grade: '', status: '', startDate: '', endDate: '' })

/* 首屏骨架屏占位；筛选后结果区 160ms 淡入，表格不做逐行错开 */
const resultFade = ref(false)
let resultFadeTimer = 0

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

onBeforeUnmount(() => window.clearTimeout(resultFadeTimer))

function handleDateChange(value) {
  if (Array.isArray(value) && value.length === 2) {
    filters.startDate = value[0] || ''
    filters.endDate = value[1] || ''
  } else {
    filters.startDate = ''
    filters.endDate = ''
  }
  search()
}

function resetAll() {
  dateRange.value = []
  reset()
}

function reloadAll() {
  load()
  loadOverview()
  loadComparison()
}

const scoreTone = (score) => {
  const n = Number(score)
  if (!Number.isFinite(n)) return 'slate'
  if (n >= 90) return 'teal'
  if (n >= 80) return 'indigo'
  if (n >= 70) return 'amber'
  return 'red'
}

/* ---------- 立即扫描 ---------- */
const scanning = ref(false)

async function runScan() {
  scanning.value = true
  try {
    const result = await runQualityScan()
    if (result?.locked === false) {
      ElMessage.warning(result?.message || '已有质检任务在执行，请稍后再试')
      return
    }
    const message = result?.message || '本轮质检已完成'
    if (Number(result?.failedCount) > 0) {
      ElMessage.warning(message)
    } else {
      ElMessage.success(message)
    }
    reloadAll()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '扫描失败，请稍后重试')
  } finally {
    scanning.value = false
  }
}

/* ---------- 评分卡抽屉 ---------- */
const drawerVisible = ref(false)
const detail = ref(null)

async function openDetail(row) {
  drawerVisible.value = true
  detail.value = null
  try {
    const data = await getQualityRecordDetail(row.id)
    detail.value = data
    initReviewForm(data)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '评分卡加载失败')
  }
}

/* ---------- 人工复核（S5-B，QC-06） ---------- */
const reviewState = reactive({})
const reviewForm = reactive({ reviewScore: 0, reviewComment: '', overturned: false })
const reviewing = ref(false)

function initReviewForm(data) {
  Object.keys(reviewState).forEach((key) => delete reviewState[key])
  reviewForm.reviewComment = ''
  reviewForm.overturned = false
  reviewForm.reviewScore = Number(data?.effectiveScore ?? data?.score ?? 0)
  for (const item of data?.items || []) {
    reviewState[item.id] = {
      hit: Number(item.effectiveHit) === 1 ? 1 : 0,
      deductScore: Number(item.effectiveDeduct) || 0,
      autoHit: Number(item.hit) === 1 ? 1 : 0,
      autoDeduct: Number(item.deductScore) || 0
    }
  }
}

/* 与自动结论逐行比对，只把真正改过的规则提交给后端 */
const isDirty = (id) => {
  const state = reviewState[id]
  if (!state) return false
  if (state.hit !== state.autoHit) return true
  return state.hit === 1 && Number(state.deductScore) !== state.autoDeduct
}

const dirtyItems = computed(() => (detail.value?.items || []).filter((item) => isDirty(item.id)))

function syncReviewScore() {
  const dirty = dirtyItems.value.length > 0
  reviewForm.overturned = dirty
  if (!dirty) {
    // 改判全部撤掉时，回落到自动分，避免残留上一次核算出的分数
    reviewForm.reviewScore = Number(detail.value?.score ?? 0)
    return
  }
  let deduct = 0
  for (const item of detail.value?.items || []) {
    const state = reviewState[item.id]
    if (state && state.hit === 1) deduct += Number(state.deductScore) || 0
  }
  reviewForm.reviewScore = Math.max(0, 100 - deduct)
}

function onHitChange(item, value) {
  const state = reviewState[item.id]
  if (!state) return
  state.hit = Number(value) === 1 ? 1 : 0
  if (state.hit === 0) {
    state.deductScore = 0
  } else if (!(Number(state.deductScore) > 0)) {
    state.deductScore = state.autoDeduct > 0 ? state.autoDeduct : 5
  }
  syncReviewScore()
}

function reviewPayload() {
  const items = dirtyItems.value.map((item) => ({
    itemId: item.id,
    hit: reviewState[item.id].hit,
    deductScore: reviewState[item.id].hit === 1 ? Number(reviewState[item.id].deductScore) || 0 : 0
  }))
  const payload = { reviewComment: reviewForm.reviewComment.trim() }
  // 有改判时复核分由后端按改判结果核算，避免明细与总分互相矛盾
  if (items.length) {
    payload.items = items
  } else {
    payload.reviewScore = Number(reviewForm.reviewScore) || 0
  }
  return payload
}

async function submitReview() {
  if (!reviewForm.reviewComment.trim()) {
    ElMessage.warning('请填写复核意见')
    return
  }
  reviewing.value = true
  try {
    const data = await reviewQualityRecord(detail.value.id, reviewPayload())
    detail.value = data
    initReviewForm(data)
    ElMessage.success(`复核已提交：生效得分 ${data.effectiveScore}（自动 ${data.score}）`)
    reloadAll()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '提交复核失败')
  } finally {
    reviewing.value = false
  }
}

async function confirmReview() {
  reviewing.value = true
  try {
    const data = await confirmQualityRecord(detail.value.id, {})
    detail.value = data
    initReviewForm(data)
    ElMessage.success('复核结论已归档')
    reloadAll()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '归档失败')
  } finally {
    reviewing.value = false
  }
}

async function rejectReview() {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('填写驳回原因后，记录退回待复核，复核分与改判将被清空', '驳回退回复核结论', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '例如：调分依据不足，请补充证据',
      inputValidator: (value) => (String(value || '').trim() ? true : '驳回原因不能为空'),
      inputErrorMessage: '驳回原因不能为空'
    })
    reason = String(result.value || '').trim()
  } catch {
    return
  }
  reviewing.value = true
  try {
    const data = await rejectQualityRecord(detail.value.id, { comment: reason })
    detail.value = data
    initReviewForm(data)
    ElMessage.success('已退回待复核')
    reloadAll()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '驳回失败')
  } finally {
    reviewing.value = false
  }
}

/* ---------- 选择会话发起质检 ---------- */
const pickerVisible = ref(false)
const pickerLoading = ref(false)
const pickerRows = ref([])
const pickerTotal = ref(0)
const pickerPage = ref(1)
const pickerKeyword = ref('')
const evaluatingId = ref(null)

async function loadPicker() {
  pickerLoading.value = true
  try {
    const params = { pageNum: pickerPage.value, pageSize: 8 }
    if (pickerKeyword.value.trim()) params.keyword = pickerKeyword.value.trim()
    const data = await getQualitySessions(params)
    pickerRows.value = Array.isArray(data?.list) ? data.list : []
    pickerTotal.value = Number(data?.total ?? 0)
  } catch (e) {
    pickerRows.value = []
    pickerTotal.value = 0
    ElMessage.error(e?.response?.data?.message || '会话列表加载失败')
  } finally {
    pickerLoading.value = false
  }
}

function openPicker() {
  pickerKeyword.value = ''
  pickerPage.value = 1
  pickerVisible.value = true
  loadPicker()
}

function searchPicker() {
  pickerPage.value = 1
  loadPicker()
}

function changePickerPage(page) {
  pickerPage.value = page
  loadPicker()
}

async function evaluateSession(row) {
  evaluatingId.value = row.id
  try {
    const record = await evaluateQualitySession(row.id)
    ElMessage.success(`质检完成：${record.sessionNo} 得分 ${record.score}（${record.grade} ${record.gradeName}）`)
    await loadPicker()
    reloadAll()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '质检失败，请稍后重试')
  } finally {
    evaluatingId.value = null
  }
}

onMounted(() => {
  load()
  loadOverview()
  loadComparison()
})
</script>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.metric-card {
  background: var(--zk-surface);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  padding: 13px 14px 12px;
  transition: border-color var(--zk-dur-fast) var(--zk-ease-out);
}

.metric-card:hover {
  border-color: var(--zk-border-strong);
}

.metric-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.metric-label {
  font-size: 12px;
  color: var(--zk-text-3);
}

.metric-icon {
  width: 22px;
  height: 22px;
  border-radius: var(--zk-radius-sm);
  display: grid;
  place-items: center;
  font-size: 12px;
  color: var(--zk-text-3);
  background: var(--zk-surface-3);
}

.metric-value {
  display: flex;
  align-items: baseline;
  min-height: 30px;
  margin-top: 9px;
  font-size: 25px;
  font-weight: 700;
  letter-spacing: -0.5px;
  color: var(--zk-text);
}

.metric-value small {
  margin-left: 4px;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--zk-text-3);
}

.metric-foot {
  margin-top: 3px;
  font-size: 11px;
  color: var(--zk-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-card.indigo .metric-icon {
  color: var(--zk-accent-hover);
  background: var(--zk-accent-soft);
}
.metric-card.teal .metric-icon {
  color: #15803d;
  background: #f0fdf4;
}
.metric-card.green .metric-icon {
  color: var(--zk-green);
  background: var(--zk-green-soft);
}
.metric-card.amber .metric-icon {
  color: var(--zk-amber);
  background: var(--zk-amber-soft);
}

/* ---------- 概览洞察 ---------- */
.insight-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.insight-card {
  padding: 14px 16px 12px;
  min-height: 188px;
}

.insight-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.insight-head h3 {
  margin: 0;
  font-size: 13.5px;
  font-weight: 650;
  color: var(--zk-text);
}

.insight-hint {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.insight-empty {
  margin: 24px 0 0;
  font-size: 12px;
  color: var(--zk-text-3);
  text-align: center;
}

.grade-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.grade-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.grade-count {
  font-size: 11.5px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.bar-track {
  height: 6px;
  border-radius: 999px;
  background: var(--zk-surface-3);
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width var(--zk-dur-base) var(--zk-ease-out);
}

.bar-fill.tone-teal {
  background: var(--zk-green);
}
.bar-fill.tone-indigo {
  background: var(--zk-accent);
}
.bar-fill.tone-amber {
  background: var(--zk-amber);
}
.bar-fill.tone-red {
  background: var(--zk-red);
}
.bar-fill.tone-slate {
  background: var(--zk-text-3);
}

.rank-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.rank-no {
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  border-radius: var(--zk-radius-xs);
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 600;
  color: var(--zk-text-3);
  background: var(--zk-surface-3);
}

.rank-no.is-top {
  color: #fff;
  background: var(--zk-accent);
}

.avatar-chip {
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--zk-accent-hover);
  background: var(--zk-accent-soft);
}

.rank-main {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.rank-name {
  font-size: 12.5px;
  color: var(--zk-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-sub {
  font-size: 11px;
  color: var(--zk-text-3);
}

.rank-value {
  font-size: 13px;
  font-weight: 650;
  color: var(--zk-text);
}

/* ---------- 列表 ---------- */
.filter-select {
  width: 132px;
}

.date-picker {
  width: 250px;
}

.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.table-scroll :deep(.el-table) {
  min-width: 1180px;
}

.table-skeleton {
  padding: 12px 16px 6px;
}

/* 筛选结果淡入：160ms，只动位移与透明度 */
.table-result-fade {
  animation: zk-rise-sm 160ms var(--zk-ease-soft) both;
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.cell-strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-text);
}

.cell-muted {
  font-size: 12px;
  color: var(--zk-text-3);
}

.score-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.score-num {
  font-size: 17px;
  font-weight: 700;
  color: var(--zk-text);
  font-variant-numeric: tabular-nums;
}

/* 复核调分后，表格里补一行自动分对照，弱化处理避免抢占主分数 */
.score-auto {
  font-size: 11px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

/* ---------- 人机质检对比 ---------- */
.compare-card {
  margin-bottom: 14px;
  padding: 14px 16px 12px;
}

.compare-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.compare-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  padding: 9px 10px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  background: var(--zk-surface-2);
}

.compare-label {
  font-size: 11.5px;
  color: var(--zk-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compare-value {
  display: flex;
  align-items: baseline;
  gap: 2px;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.4px;
  color: var(--zk-text);
  font-variant-numeric: tabular-nums;
}

.compare-value small {
  font-size: 11.5px;
  font-weight: 500;
  letter-spacing: 0;
  color: var(--zk-text-3);
}

.compare-value.is-up {
  color: var(--zk-green);
}

.compare-value.is-down {
  color: var(--zk-amber);
}

.compare-sub {
  font-size: 11px;
  color: var(--zk-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compare-body {
  display: grid;
  grid-template-columns: minmax(240px, 0.9fr) minmax(0, 1.6fr);
  gap: 14px;
  align-items: start;
}

.diff-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.diff-item {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 36px;
  align-items: center;
  gap: 8px;
}

.diff-label {
  font-size: 11.5px;
  color: var(--zk-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.diff-count {
  font-size: 12px;
  font-weight: 600;
  text-align: right;
  color: var(--zk-text);
}

/* 逐规则对比表里的改判条数：有改判才染红，避免整列泛红 */
.diff-hit {
  color: var(--zk-red);
  font-weight: 650;
}

.compare-rules {
  min-width: 0;
}

/* ---------- 评分卡 ---------- */
.detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--zk-border);
}

.score-ring {
  width: 92px;
  height: 92px;
  flex: 0 0 92px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 3px solid var(--zk-border);
  background: var(--zk-surface-2);
}

.score-ring.tone-teal {
  border-color: var(--zk-green);
  color: var(--zk-green);
  background: var(--zk-green-soft);
}
.score-ring.tone-indigo {
  border-color: var(--zk-accent);
  color: var(--zk-accent-hover);
  background: var(--zk-accent-soft);
}
.score-ring.tone-amber {
  border-color: var(--zk-amber);
  color: var(--zk-amber);
  background: var(--zk-amber-soft);
}
.score-ring.tone-red {
  border-color: var(--zk-red);
  color: var(--zk-red);
  background: var(--zk-red-soft);
}

.score-ring-num {
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.score-ring-label {
  margin-top: 2px;
  font-size: 11px;
  color: var(--zk-text-3);
}

.card-head-main {
  min-width: 0;
}

.card-head-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.card-head-session {
  margin: 8px 0 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--zk-text);
}

.card-head-sub {
  margin: 3px 0 0;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 10px 16px;
  margin: 0;
}

.meta-grid dt {
  font-size: 11.5px;
  color: var(--zk-text-3);
  margin-bottom: 2px;
}

.meta-grid dd {
  margin: 0;
  font-size: 12.5px;
  color: var(--zk-text);
}

.ai-block,
.item-block,
.review-block {
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  overflow: hidden;
}

.ai-block h4,
.item-block h4,
.review-block h4 {
  margin: 0;
  padding: 9px 12px;
  font-size: 12.5px;
  font-weight: 650;
  color: var(--zk-text);
  background: var(--zk-surface-2);
  border-bottom: 1px solid var(--zk-border);
}

.ai-block p {
  margin: 0;
  padding: 11px 12px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--zk-text-2);
}

.item-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 12px;
  background: var(--zk-surface-2);
  border-bottom: 1px solid var(--zk-border);
}

.item-head h4 {
  padding: 0;
  background: none;
  border: 0;
}

.item-hint {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.item-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.item-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--zk-border);
}

.item-row:last-child {
  border-bottom: 0;
}

.item-row.is-hit {
  background: #fffbf7;
}

.item-main {
  min-width: 0;
  flex: 1;
}

.item-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.item-name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-text);
}

.item-deduct {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--zk-red);
}

.item-evidence {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.65;
  color: var(--zk-text-2);
  word-break: break-word;
}

.item-suggestion {
  margin: 4px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

.item-source {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--zk-text-3);
  padding-top: 2px;
}

.review-block p {
  margin: 0;
  padding: 9px 12px 0;
  font-size: 12.5px;
  color: var(--zk-text-2);
}

.review-comment {
  padding-bottom: 11px !important;
}

.review-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 11px 12px 12px;
}

.review-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 9px;
}

.review-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--zk-text);
}

.review-hint {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.rejudge-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding-top: 3px;
  font-size: 12px;
  font-weight: 600;
  color: var(--zk-text);
  border-top: 1px solid var(--zk-border);
}

.rejudge-head > span:first-child {
  padding-top: 9px;
}

.rejudge-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rejudge-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 7px 9px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-xs);
  background: var(--zk-surface-2);
  transition: border-color var(--zk-dur-fast) var(--zk-ease-out),
    background-color var(--zk-dur-fast) var(--zk-ease-out);
}

/* 被人工改判的行高亮，方便复核人回看自己动了哪几条 */
.rejudge-item.is-dirty {
  border-color: var(--zk-accent);
  background: var(--zk-accent-soft);
}

.rejudge-name {
  flex: 1 1 160px;
  min-width: 0;
  font-size: 12.5px;
  color: var(--zk-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rejudge-auto {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--zk-text-3);
}

.review-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 0 12px 12px;
}

.review-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.detail-loading {
  padding: 8px 0;
}

/* ---------- 发起质检弹窗 ---------- */
.picker-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.picker-scroll {
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  overflow: hidden;
}

@media (max-width: 1180px) {
  .insight-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .compare-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .compare-body {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .metric-grid,
  .insight-grid {
    grid-template-columns: 1fr;
  }

  .filter-select,
  .date-picker {
    width: 100%;
  }

  .compare-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .picker-toolbar {
    flex-wrap: wrap;
  }
}
</style>
