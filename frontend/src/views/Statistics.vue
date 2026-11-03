<template>
  <div class="zk-page stat-page">
    <PageHeader
      title="数据统计"
      description="按自然日聚合会话、工单、知识命中与 AI 调用数据，所有指标来自实时统计接口。"
    >
      <template #actions>
        <el-button :icon="Download" @click="exportCsv">导出报表</el-button>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadAll">刷新数据</el-button>
      </template>
    </PageHeader>

    <!-- 筛选 -->
    <section class="filter-bar zk-card" aria-label="统计筛选" v-reveal>
      <div class="quick-ranges" role="group" aria-label="快捷时间范围">
        <button
          v-for="item in presets"
          :key="item.key"
          type="button"
          class="range-chip zk-press"
          :class="{ 'is-active': preset === item.key }"
          :aria-pressed="preset === item.key"
          @click="applyPreset(item.key)"
        >
          {{ item.label }}
        </button>
      </div>
      <el-date-picker
        v-model="range"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        :clearable="false"
        aria-label="自定义统计区间"
        @change="onRangeChange"
      />
      <span class="range-meta">统计区间 {{ rangeLabel }} · 按 Asia/Shanghai 自然日</span>
      <span class="range-meta range-meta--right">
        更新于 {{ updatedAt || '—' }}
        <el-button link type="primary" @click="loadAll">重新统计</el-button>
      </span>
    </section>

    <el-alert
      v-if="error"
      class="stat-alert"
      type="warning"
      show-icon
      :closable="false"
      :title="error"
    >
      <template #default>
        <div class="alert-body">
          <span>已展示成功返回的部分数据，可重试补齐。</span>
          <el-button link type="primary" @click="loadAll">重新加载</el-button>
        </div>
      </template>
    </el-alert>

    <!-- 页签 -->
    <div class="stat-tabs" role="tablist" aria-label="统计维度">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        role="tab"
        class="stat-tab zk-press"
        :class="{ 'is-active': activeTab === tab.key }"
        :aria-selected="activeTab === tab.key"
        @click="switchTab(tab.key)"
      >
        <el-icon aria-hidden="true"><component :is="tab.icon" /></el-icon>
        {{ tab.label }}
      </button>
    </div>

    <!-- 概览 -->
    <div v-show="activeTab === 'overview'" role="tabpanel" aria-label="概览与趋势">
      <section v-if="!overview && loading" class="metric-grid" aria-busy="true">
        <article v-for="n in 8" :key="`sk-${n}`" class="metric-card">
          <ZkSkeleton variant="stat" />
        </article>
      </section>
      <section v-else class="metric-grid" v-reveal.group :aria-busy="loading ? 'true' : 'false'">
        <article v-for="card in overviewCards" :key="card.key" class="metric-card" :class="card.tone">
          <div class="metric-top">
            <span class="metric-label">{{ card.label }}</span>
            <span class="metric-icon" aria-hidden="true">
              <el-icon><component :is="card.icon" /></el-icon>
            </span>
          </div>
          <div class="metric-value">
            <span class="zk-num">
              <CountNum :value="card.raw" :decimals="card.decimals || 0" />
              <template v-if="card.raw2 !== undefined"> / <CountNum :value="card.raw2" /></template>
            </span>
          </div>
          <div class="metric-foot">{{ card.caption }}</div>
        </article>
      </section>

      <section class="panel-grid" v-reveal.group>
        <div class="zk-card chart-card">
          <div class="chart-head">
            <div>
              <div class="chart-title">会话量趋势</div>
              <div class="chart-sub">AI 接待与人工接入按日对比，用于判断服务压力</div>
            </div>
            <div class="trend-totals">
              合计 <b><CountNum :value="trendTotal" /></b> 会话 /
              <b><CountNum :value="trendHumanTotal" /></b> 转人工
            </div>
          </div>

          <div v-if="!trendData && loading" class="trend-skeleton" aria-busy="true">
            <ZkSkeleton variant="chart" :cols="7" />
          </div>
          <div v-else-if="trendPoints.length" class="trend-body">
            <div class="trend-legend">
              <span class="legend-item"><i class="swatch swatch--sessions" />会话总数</span>
              <span class="legend-item"><i class="swatch swatch--orders" />转人工</span>
            </div>
            <div class="trend-chart" :class="{ 'is-ready': chartReady }">
              <div class="trend-axis" aria-hidden="true">
                <span
                  v-for="(label, index) in trendAxis"
                  :key="index"
                  class="axis-label"
                  :style="{ top: `${(index / 4) * 100}%` }"
                >
                  {{ label }}
                </span>
              </div>
              <div class="trend-main">
                <div
                  class="trend-plot"
                  :class="{ 'is-live': barsLive, 'is-morphing': morphing, 'is-snap': snapping }"
                  role="img"
                  :aria-label="trendSummary"
                  :style="{ minWidth: trendPlotWidth }"
                >
                  <span
                    v-for="n in 5"
                    :key="n"
                    class="grid-line"
                    aria-hidden="true"
                    :style="{ top: `${((n - 1) / 4) * 100}%` }"
                  />
                  <div class="trend-cols" :class="{ 'is-dense': trendPoints.length > 14 }">
                    <div
                      v-for="(point, index) in trendPoints"
                      :key="index"
                      class="trend-col"
                      :style="{ '--zk-bar-i': index }"
                    >
                      <div class="trend-bars">
                        <div class="bar-group">
                          <CountNum class="bar-val" :value="point.sessionCount" />
                          <div
                            class="bar bar--sessions"
                            :style="{
                              height: barHeight(point.sessionCount),
                              '--zk-bar-scale': barScale('s', index)
                            }"
                          />
                        </div>
                        <div class="bar-group">
                          <CountNum
                            class="bar-val bar-val--muted"
                            :value="point.humanSessionCount"
                          />
                          <div
                            class="bar bar--orders"
                            :style="{
                              height: barHeight(point.humanSessionCount),
                              '--zk-bar-scale': barScale('o', index)
                            }"
                          />
                        </div>
                      </div>
                      <div class="trend-tip" role="tooltip">
                        <div class="tip-date">{{ point.time }}</div>
                        <div class="tip-row"><i class="dot dot--sessions" />会话 {{ num(point.sessionCount) }}</div>
                        <div class="tip-row"><i class="dot dot--orders" />转人工 {{ num(point.humanSessionCount) }}</div>
                        <div class="tip-row"><i class="dot dot--muted" />已结束 {{ num(point.closedCount) }}</div>
                      </div>
                    </div>
                  </div>
                </div>
                <div class="trend-dates" :style="{ minWidth: trendPlotWidth }">
                  <span v-for="(point, index) in trendPoints" :key="'d' + index" class="trend-date">
                    {{ shortDate(point.time) }}
                  </span>
                </div>
              </div>
            </div>
          </div>
          <ZkEmpty
            v-else
            size="sm"
            title="所选区间暂无会话数据"
            hint="调整时间范围或清除筛选后再查看趋势"
          />

          <div class="trend-foot">
            <div class="mini-stat">
              <span class="mini-label">平均会话时长</span>
              <span class="mini-value zk-num">{{ formatWait(overview?.avgSessionDurationSeconds) }}</span>
            </div>
            <div class="mini-stat">
              <span class="mini-label">平均满意度</span>
              <span class="mini-value zk-num">
                <CountNum
                  :value="Number(overview?.avgRating) > 0 ? overview.avgRating : null"
                  :decimals="1"
                />
              </span>
            </div>
            <div class="mini-stat">
              <span class="mini-label">当前排队</span>
              <span class="mini-value zk-num"><CountNum :value="overview?.sessionWaiting" /></span>
            </div>
            <div class="mini-stat">
              <span class="mini-label">转人工占比</span>
              <span class="mini-value zk-num">{{ trendTransferRate }}</span>
            </div>
          </div>
        </div>

        <div class="zk-card side-card">
          <div class="chart-head">
            <div>
              <div class="chart-title">实时服务状态</div>
              <div class="chart-sub">不随筛选区间变化</div>
            </div>
          </div>
          <ul class="live-list" v-reveal.group>
            <li v-for="item in liveItems" :key="item.label">
              <span class="live-dot" :class="item.tone" aria-hidden="true" />
              <span class="live-label">{{ item.label }}</span>
              <b class="live-value zk-num">
                <CountNum :value="item.raw" />
                <template v-if="item.raw2 !== undefined"> / <CountNum :value="item.raw2" /></template>
              </b>
            </li>
          </ul>
          <div class="side-note">
            在线坐席与排队会实时变化；需要处理等待会话可前往
            <router-link to="/admin/agents">坐席工作台</router-link>。
          </div>
        </div>
      </section>
    </div>

    <!-- 工单 -->
    <div v-show="activeTab === 'orders'" role="tabpanel" aria-label="工单分析">
      <section class="metric-grid metric-grid--three" v-reveal.group>
        <article class="metric-card indigo">
          <div class="metric-top"><span class="metric-label">工单总数</span></div>
          <div class="metric-value">
            <span class="zk-num"><CountNum :value="distribution?.total" /></span>
          </div>
          <div class="metric-foot">所选区间新建工单</div>
        </article>
        <article class="metric-card red">
          <div class="metric-top"><span class="metric-label">超时工单</span></div>
          <div class="metric-value">
            <span class="zk-num"><CountNum :value="distribution?.overdueCount" /></span>
          </div>
          <div class="metric-foot">SLA 已超时且未关闭</div>
        </article>
        <article class="metric-card teal">
          <div class="metric-top"><span class="metric-label">平均解决耗时</span></div>
          <div class="metric-value"><span class="zk-num">{{ formatMinutes(distribution?.avgResolveMinutes) }}</span></div>
          <div class="metric-foot">创建至解决的均值</div>
        </article>
      </section>

      <section class="panel-grid panel-grid--equal" v-reveal.group>
        <div class="zk-card dist-card">
          <div class="chart-head">
            <div>
              <div class="chart-title">状态分布</div>
              <div class="chart-sub">全部状态的工单数量与占比</div>
            </div>
          </div>
          <div v-if="statusItems.length" class="dist-list" v-reveal.group>
            <div v-for="item in statusItems" :key="item.key" class="dist-row">
              <span class="dist-label">{{ item.label }}</span>
              <span class="dist-track">
                <i :class="`tone-${item.tone}`" :style="{ width: widthOf(item.value, statusMax) }" />
              </span>
              <b class="dist-value zk-num"><CountNum :value="item.value" /></b>
            </div>
          </div>
          <ZkEmpty v-else size="sm" title="所选区间暂无工单" hint="调整时间范围或清除筛选后再查看" />
        </div>

        <div class="zk-card dist-card">
          <div class="chart-head">
            <div>
              <div class="chart-title">优先级分布</div>
              <div class="chart-sub">P0 紧急工单应优先关注</div>
            </div>
          </div>
          <div v-if="priorityItems.length" class="dist-list" v-reveal.group>
            <div v-for="item in priorityItems" :key="item.key" class="dist-row">
              <span class="dist-label">{{ item.label }}</span>
              <span class="dist-track">
                <i :class="`tone-${item.tone}`" :style="{ width: widthOf(item.value, priorityMax) }" />
              </span>
              <b class="dist-value zk-num"><CountNum :value="item.value" /></b>
            </div>
          </div>
          <ZkEmpty v-else size="sm" title="所选区间暂无工单" hint="调整时间范围或清除筛选后再查看" />
        </div>
      </section>

      <section class="zk-card dist-card dist-card--wide" v-reveal>
        <div class="chart-head">
          <div>
            <div class="chart-title">分类分布</div>
            <div class="chart-sub">按工单分类聚合，便于定位高频问题</div>
          </div>
        </div>
        <div v-if="categoryItems.length" class="cat-grid" v-reveal.group>
          <div v-for="item in categoryItems" :key="item.label" class="cat-card">
            <div class="cat-name">{{ item.label }}</div>
            <div class="cat-value zk-num"><CountNum :value="item.value" /></div>
            <div class="cat-track">
              <i :style="{ width: widthOf(item.value, categoryMax) }" />
            </div>
          </div>
        </div>
        <ZkEmpty v-else size="sm" title="所选区间暂无分类数据" hint="工单带上分类后这里会自动汇总" />
      </section>
    </div>

    <!-- 知识库 -->
    <div v-show="activeTab === 'knowledge'" role="tabpanel" aria-label="知识命中">
      <section class="metric-grid metric-grid--three" v-reveal.group>
        <article class="metric-card indigo">
          <div class="metric-top"><span class="metric-label">累计命中</span></div>
          <div class="metric-value">
            <span class="zk-num"><CountNum :value="kbHit?.totalHits" /></span>
          </div>
          <div class="metric-foot">知识库条目被命中次数</div>
        </article>
        <article class="metric-card teal">
          <div class="metric-top"><span class="metric-label">直接命中消息</span></div>
          <div class="metric-value">
            <span class="zk-num"><CountNum :value="kbHit?.directAnswerCount" /></span>
          </div>
          <div class="metric-foot">无需转人工直接回答</div>
        </article>
        <article class="metric-card green">
          <div class="metric-top"><span class="metric-label">直接命中率</span></div>
          <div class="metric-value">
            <span class="zk-num"><CountNum :value="kbDirectRate" :decimals="1" suffix="%" /></span>
          </div>
          <div class="metric-foot">越高说明自助服务越充分</div>
        </article>
      </section>

      <section class="zk-card" v-reveal>
        <div class="chart-head">
          <div>
            <div class="chart-title">命中排行 Top 10</div>
            <div class="chart-sub">按累计命中次数排序，可据此补充相似问法</div>
          </div>
          <router-link class="more-link" to="/admin/knowledge">前往知识库 →</router-link>
        </div>
        <ZkSkeleton v-if="!kbHit && loading" variant="table" :rows="6" :cols="4" />
        <el-table v-else :data="kbTop" class="stat-table" empty-text="所选区间暂无命中记录">
          <el-table-column label="排名" width="64">
            <template #default="{ $index }">
              <span class="rank" :class="{ 'is-top': $index < 3 }">{{ $index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="question" label="标准问题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="categoryName" label="分类" width="130">
            <template #default="{ row }">{{ row.categoryName || '—' }}</template>
          </el-table-column>
          <el-table-column label="命中次数" width="150">
            <template #default="{ row }">
              <div class="hit-cell">
                <span class="hit-num"><CountNum :value="row.hitCount" /></span>
                <span class="hit-bar"><i :style="{ width: widthOf(row.hitCount, kbMax) }" /></span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="directAnswerCount" label="直接回答" width="110" align="right">
            <template #default="{ row }">
              <span class="zk-num"><CountNum :value="row.directAnswerCount" /></span>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <!-- 坐席与 AI -->
    <div v-show="activeTab === 'agents'" role="tabpanel" aria-label="坐席与 AI">
      <section class="metric-grid metric-grid--four" v-reveal.group>
        <article class="metric-card indigo">
          <div class="metric-top"><span class="metric-label">AI 调用次数</span></div>
          <div class="metric-value">
            <span class="zk-num"><CountNum :value="aiCalls?.totalCalls" /></span>
          </div>
          <div class="metric-foot">成功 {{ num(aiCalls?.successCalls) }} · 失败 {{ num(aiCalls?.failCalls) }}</div>
        </article>
        <article class="metric-card teal">
          <div class="metric-top"><span class="metric-label">调用成功率</span></div>
          <div class="metric-value">
            <span class="zk-num"><CountNum :value="aiSuccessRate" :decimals="1" suffix="%" /></span>
          </div>
          <div class="metric-foot">含知识库降级 {{ num(aiCalls?.fallbackCount) }} 次</div>
        </article>
        <article class="metric-card amber">
          <div class="metric-top"><span class="metric-label">平均耗时</span></div>
          <div class="metric-value">
            <span class="zk-num"><CountNum :value="aiCalls?.avgLatencyMs" /> <small>ms</small></span>
          </div>
          <div class="metric-foot">模型调用平均响应时间</div>
        </article>
        <article class="metric-card slate">
          <div class="metric-top"><span class="metric-label">Token 消耗</span></div>
          <div class="metric-value"><span class="zk-num"><CountNum :value="tokenTotal" /></span></div>
          <div class="metric-foot">输入 {{ num(aiCalls?.totalPromptTokens) }} · 输出 {{ num(aiCalls?.totalCompletionTokens) }}</div>
        </article>
      </section>

      <section class="zk-card workload-card" v-reveal>
        <div class="chart-head">
          <div>
            <div class="chart-title">坐席工作量</div>
            <div class="chart-sub">接入会话、满意度与工单解决量，用于评估坐席负载</div>
          </div>
          <el-button :icon="Refresh" size="small" @click="loadWorkload">刷新</el-button>
        </div>
        <ZkSkeleton v-if="!workload.list.length && loading" variant="table" :rows="5" :cols="5" />
        <el-table
          v-else
          v-loading="workloadLoading"
          :data="workload.list"
          class="stat-table"
          empty-text="所选区间暂无坐席数据"
        >
          <el-table-column prop="agentName" label="坐席" min-width="120">
            <template #default="{ row }">{{ row.agentName || `坐席 ${row.agentId}` }}</template>
          </el-table-column>
          <el-table-column prop="servedCount" label="接入会话" width="100" align="right">
            <template #default="{ row }"><span class="zk-num">{{ num(row.servedCount) }}</span></template>
          </el-table-column>
          <el-table-column prop="closedCount" label="已结束" width="90" align="right">
            <template #default="{ row }"><span class="zk-num">{{ num(row.closedCount) }}</span></template>
          </el-table-column>
          <el-table-column prop="avgRating" label="满意度" width="96" align="right">
            <template #default="{ row }"><span class="zk-num">{{ formatRating(row.avgRating) }}</span></template>
          </el-table-column>
          <el-table-column prop="avgDurationSeconds" label="平均会话" width="120" align="right">
            <template #default="{ row }"><span class="zk-num">{{ formatWait(row.avgDurationSeconds) }}</span></template>
          </el-table-column>
          <el-table-column prop="avgAcceptSeconds" label="平均等待" width="120" align="right">
            <template #default="{ row }"><span class="zk-num">{{ formatWait(row.avgAcceptSeconds) }}</span></template>
          </el-table-column>
          <el-table-column prop="workOrderResolved" label="解决工单" width="100" align="right">
            <template #default="{ row }"><span class="zk-num">{{ num(row.workOrderResolved) }}</span></template>
          </el-table-column>
        </el-table>
        <div class="zk-pager">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="workload.total"
            :current-page="pageNum"
            :page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            aria-label="坐席工作量分页"
            @current-change="changePage"
            @size-change="changeSize"
          />
        </div>
      </section>

      <section class="zk-card model-card" v-reveal>
        <div class="chart-head">
          <div>
            <div class="chart-title">模型调用明细</div>
            <div class="chart-sub">按模型聚合调用次数与平均耗时</div>
          </div>
        </div>
        <ZkSkeleton v-if="!aiCalls && loading" variant="table" :rows="4" :cols="4" />
        <el-table v-else :data="modelRows" class="stat-table" empty-text="所选区间暂无 AI 调用记录">
          <el-table-column prop="model" label="模型" min-width="180" show-overflow-tooltip />
          <el-table-column prop="calls" label="调用次数" width="120" align="right">
            <template #default="{ row }"><span class="zk-num">{{ num(row.calls) }}</span></template>
          </el-table-column>
          <el-table-column prop="avgLatencyMs" label="平均耗时" width="130" align="right">
            <template #default="{ row }"><span class="zk-num">{{ num(row.avgLatencyMs) }} ms</span></template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <StatusPill :label="row.failed ? '失败' : '成功'" :tone="row.failed ? 'red' : 'teal'" dot />
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <!-- 运营指标（S6-A：DB-01 / DB-04） -->
    <div v-show="activeTab === 'ops'" role="tabpanel" aria-label="运营指标">
      <section class="metric-grid metric-grid--four" v-reveal.group>
        <article class="metric-card indigo">
          <div class="metric-top">
            <span class="metric-label">首响均值</span>
            <span class="metric-icon" aria-hidden="true">
              <el-icon><Timer /></el-icon>
            </span>
          </div>
          <div class="metric-value">
            <span class="zk-num">{{ formatWait(opsMetrics?.avgFirstResponseSeconds) }}</span>
          </div>
          <div class="metric-foot">样本 {{ num(opsMetrics?.firstResponseSample) }} 个会话</div>
        </article>
        <article class="metric-card amber">
          <div class="metric-top">
            <span class="metric-label">首响 P90</span>
            <span class="metric-icon" aria-hidden="true">
              <el-icon><Odometer /></el-icon>
            </span>
          </div>
          <div class="metric-value">
            <span class="zk-num">{{ formatWait(opsMetrics?.p90FirstResponseSeconds) }}</span>
          </div>
          <div class="metric-foot">90% 会话首响快于该值</div>
        </article>
        <article class="metric-card teal">
          <div class="metric-top">
            <span class="metric-label">3 分钟回复率</span>
            <span class="metric-icon" aria-hidden="true">
              <el-icon><ChatDotRound /></el-icon>
            </span>
          </div>
          <div class="metric-value">
            <span class="zk-num">
              <CountNum :value="opsThreeMinRate" :decimals="1" suffix="%" />
            </span>
          </div>
          <div class="metric-foot">
            人工轮次 {{ num(opsMetrics?.withinCount) }} / {{ num(opsMetrics?.roundCount) }}
          </div>
        </article>
        <article class="metric-card green">
          <div class="metric-top">
            <span class="metric-label">AI 自主解决率</span>
            <span class="metric-icon" aria-hidden="true">
              <el-icon><TrendCharts /></el-icon>
            </span>
          </div>
          <div class="metric-value">
            <span class="zk-num">
              <CountNum :value="opsAiResolveRate" :decimals="1" suffix="%" />
            </span>
          </div>
          <div class="metric-foot">
            未接人工即关闭 {{ num(opsMetrics?.aiOnlyCount) }} / {{ num(opsMetrics?.closedCount) }}
          </div>
        </article>
      </section>

      <section class="metric-grid metric-grid--three" v-reveal.group>
        <article class="metric-card indigo">
          <div class="metric-top"><span class="metric-label">平均满意度</span></div>
          <div class="metric-value">
            <span class="zk-num">{{ formatRating(opsMetrics?.avgRating) }}</span>
          </div>
          <div class="metric-foot">
            回收率 {{ percentOf(opsMetrics?.satisfactionRecoveryRate) }} · {{ num(opsMetrics?.ratingCount) }} 条评价
          </div>
        </article>
        <article class="metric-card teal">
          <div class="metric-top"><span class="metric-label">转人工率</span></div>
          <div class="metric-value">
            <span class="zk-num">
              <CountNum :value="opsTransferRate" :decimals="1" suffix="%" />
            </span>
          </div>
          <div class="metric-foot">
            转人工 {{ num(opsMetrics?.transferredCount) }} · 会话 {{ num(opsMetrics?.sessionTotal) }}
          </div>
        </article>
        <article class="metric-card red">
          <div class="metric-top"><span class="metric-label">工单超时率</span></div>
          <div class="metric-value">
            <span class="zk-num">
              <CountNum :value="opsOverdueRate" :decimals="1" suffix="%" />
            </span>
          </div>
          <div class="metric-foot">
            超时 {{ num(opsMetrics?.orderOverdueCount) }} / {{ num(opsMetrics?.orderTotal) }} 单
          </div>
        </article>
      </section>

      <section class="zk-card ops-note-card" v-reveal>
        <div class="chart-head">
          <div>
            <div class="chart-title">指标口径与覆盖率</div>
            <div class="chart-sub">
              首响 = 首条访客消息 → 首次回复（系统欢迎语不计）；3 分钟回复率按人工接待期轮次统计，
              AI 轮次单列；满意度必须同时披露回收率
            </div>
          </div>
          <el-button
            type="primary"
            plain
            :loading="rebuilding"
            @click="rebuildMetrics"
          >
            回填轮次指标
          </el-button>
        </div>
        <ul class="ops-note-list">
          <li>
            <span class="ops-note-label">轮次指标覆盖率</span>
            <b class="zk-num">{{ percentOf(opsMetrics?.metricCoverageRate) }}</b>
            <span class="ops-note-hint">
              已计算 {{ num(opsMetrics?.metricSessionCount) }} / 人工接待 {{ num(opsMetrics?.humanSessionCount) }} 个会话
            </span>
          </li>
          <li>
            <span class="ops-note-label">待回填会话</span>
            <b class="zk-num">{{ num(opsMetrics?.pendingMetricSessionCount) }}</b>
            <span class="ops-note-hint">
              未计算的会话不计入 3 分钟回复率分母，历史数据可点右侧按钮回填
            </span>
          </li>
          <li>
            <span class="ops-note-label">平均会话时长</span>
            <b class="zk-num">{{ formatWait(opsMetrics?.avgSessionDurationSeconds) }}</b>
            <span class="ops-note-hint">人工接入至会话结束</span>
          </li>
        </ul>
      </section>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      统计口径：会话按创建时间、工单按创建时间、AI 调用按调用时间；默认最近 7 天（含今天），区间跨度最多 366 天
    </div>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound,
  DataLine,
  Download,
  Headset,
  Odometer,
  Refresh,
  Tickets,
  Timer,
  TrendCharts
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import { useCountUp, useEnteredOnce } from '@/composables/motion'
import {
  getOpsMetrics,
  getStatsAgentWorkload,
  getStatsAiCalls,
  getStatsKbHit,
  getStatsOverview,
  getStatsSessionTrend,
  getStatsWorkOrderDistribution,
  rebuildOpsMetrics
} from '@/api'
import {
  ORDER_STATUS_DICT,
  PRIORITY_DICT,
  dictTone,
  formatMinutes,
  formatRating,
  formatWait,
  shortDate
} from '@/utils/dict'

const tabs = [
  { key: 'overview', label: '概览与趋势', icon: TrendCharts },
  { key: 'ops', label: '运营指标', icon: Timer },
  { key: 'orders', label: '工单分析', icon: Tickets },
  { key: 'knowledge', label: '知识命中', icon: DataLine },
  { key: 'agents', label: '坐席与 AI', icon: Headset }
]

const presets = [
  { key: '7', label: '近 7 天' },
  { key: '30', label: '近 30 天' },
  { key: '90', label: '近 90 天' },
  { key: 'month', label: '本月' }
]

const activeTab = ref('overview')
const preset = ref('7')
const range = ref(defaultRange(7))
const loading = ref(true)
const error = ref('')
const updatedAt = ref('')

const overview = ref(null)
const trendData = ref(null)
const distribution = ref(null)
const kbHit = ref(null)
const aiCalls = ref(null)
const workload = ref({ list: [], total: 0 })
const workloadLoading = ref(false)
const opsMetrics = ref(null)
const rebuilding = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)

function pad(value) {
  return String(value).padStart(2, '0')
}

function toDateText(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function defaultRange(days) {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - (days - 1))
  return [toDateText(start), toDateText(end)]
}

const rangeLabel = computed(() => `${range.value?.[0] || '—'} 至 ${range.value?.[1] || '—'}`)

const rangeParams = computed(() => ({
  startDate: range.value?.[0],
  endDate: range.value?.[1]
}))

const num = (value) => {
  const n = Number(value)
  return Number.isFinite(n) ? n.toLocaleString('zh-CN') : '—'
}

const percentOf = (value) => {
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  return `${(n * 100).toFixed(1)}%`
}

/**
 * 数字滚动：v-for 里的指标无法逐个调用 useCountUp，这里包一层组件统一节奏。
 * 首播 700ms，数据刷新 500ms；prefers-reduced-motion 由 useCountUp 内置处理。
 */
const CountNum = defineComponent({
  name: 'CountNum',
  props: {
    value: { type: [Number, String], default: null },
    decimals: { type: Number, default: 0 },
    prefix: { type: String, default: '' },
    suffix: { type: String, default: '' }
  },
  setup(props) {
    const valid = computed(
      () => props.value !== null && props.value !== '' && Number.isFinite(Number(props.value))
    )
    const { display } = useCountUp(computed(() => (valid.value ? Number(props.value) : 0)), {
      duration: 700,
      updateDuration: 500,
      decimals: props.decimals,
      prefix: props.prefix,
      suffix: props.suffix
    })
    return () => h('span', valid.value ? display.value : '—')
  }
})

/* ---------- 概览 ---------- */
const overviewCards = computed(() => {
  const data = overview.value || {}
  return [
    {
      key: 'sessions',
      label: '会话总数',
      raw: data.sessionTotal,
      caption: `当前活跃 ${num(data.sessionActive)} · 排队 ${num(data.sessionWaiting)}`,
      tone: 'indigo',
      icon: ChatDotRound
    },
    {
      key: 'aiOnly',
      label: 'AI 独立接待',
      raw: data.sessionAiOnly,
      caption: '全程 AI 接待、未转人工',
      tone: 'teal',
      icon: TrendCharts
    },
    {
      key: 'transferred',
      label: '转人工会话',
      raw: data.sessionTransferred,
      caption: '发生过人工接入',
      tone: 'amber',
      icon: Headset
    },
    {
      key: 'rating',
      label: '平均满意度',
      raw: Number(data.avgRating) > 0 ? data.avgRating : null,
      decimals: 1,
      caption: `平均时长 ${formatWait(data.avgSessionDurationSeconds)}`,
      tone: 'green',
      icon: DataLine
    },
    {
      key: 'orders',
      label: '工单总数',
      raw: data.workOrderTotal,
      caption: `待分配 ${num(data.workOrderPending)} · 处理中 ${num(data.workOrderProcessing)}`,
      tone: 'slate',
      icon: Tickets
    },
    {
      key: 'overdue',
      label: '超时工单',
      raw: data.workOrderOverdue,
      caption: `已解决 ${num(data.workOrderResolved)}`,
      tone: 'red',
      icon: Tickets
    },
    {
      key: 'ai',
      label: 'AI 调用',
      raw: data.aiCalls,
      caption: `成功率 ${percentOf(data.aiSuccessRate)}`,
      tone: 'indigo',
      icon: TrendCharts
    },
    {
      key: 'users',
      label: '用户 / 坐席',
      raw: data.userTotal,
      raw2: data.agentTotal,
      caption: `在线坐席 ${num(data.agentOnline)}`,
      tone: 'teal',
      icon: Headset
    }
  ]
})

const liveItems = computed(() => {
  const data = overview.value || {}
  return [
    { label: '排队等待接入', raw: data.sessionWaiting, tone: 'amber' },
    { label: '当前活跃会话', raw: data.sessionActive, tone: 'teal' },
    { label: '在线坐席', raw: data.agentOnline, raw2: data.agentTotal, tone: 'teal' },
    { label: '待分配工单', raw: data.workOrderPending, tone: 'indigo' },
    { label: '处理中工单', raw: data.workOrderProcessing, tone: 'indigo' }
  ]
})

/* ---------- 会话趋势 ---------- */
const trendPoints = computed(() => {
  const points = trendData.value?.points
  return Array.isArray(points) ? points : []
})

const trendTotal = computed(() =>
  trendPoints.value.reduce((sum, item) => sum + (Number(item.sessionCount) || 0), 0)
)

const trendHumanTotal = computed(() =>
  trendPoints.value.reduce((sum, item) => sum + (Number(item.humanSessionCount) || 0), 0)
)

const trendTransferRate = computed(() => {
  if (!trendTotal.value) return '—'
  return `${((trendHumanTotal.value / trendTotal.value) * 100).toFixed(1)}%`
})

// 区间可到 90 天：每天至少 20px，超出容器时横向滚动
const trendPlotWidth = computed(() =>
  trendPoints.value.length > 14 ? `${trendPoints.value.length * 20}px` : '100%'
)

// 刻度上限（旧数据也要算一次，柱子才能从旧高度接上）
function chartMaxOf(list) {
  const values = list.flatMap((item) => [
    Number(item.sessionCount) || 0,
    Number(item.humanSessionCount) || 0
  ])
  const raw = Math.max(1, ...values, 1)
  const power = Math.pow(10, Math.floor(Math.log10(raw)))
  const base = raw / power
  const step = base <= 1 ? 1 : base <= 2 ? 2 : base <= 5 ? 5 : 10
  return step * power * 4
}

const trendMax = computed(() => chartMaxOf(trendPoints.value))

const trendAxis = computed(() => [4, 3, 2, 1, 0].map((factor) => Math.round((trendMax.value / 4) * factor)))

const trendSummary = computed(() =>
  trendPoints.value.length
    ? `会话趋势共 ${trendPoints.value.length} 个统计点，合计 ${trendTotal.value} 个会话。`
    : '暂无会话趋势数据'
)

const barHeight = (value) => {
  const v = Number(value) || 0
  if (v <= 0) return '0%'
  return `${Math.max(3, Math.min(100, (v / trendMax.value) * 100))}%`
}

/* ---------- 趋势图动效：刻度先淡入 → 柱子从底部长出（只播一次）→ 筛选切换走形态过渡 ---------- */
const entered = useEnteredOnce()
const chartReady = ref(false) // 刻度线 / 网格线先淡入
const barsLive = ref(false) // 首播结束后，后续变化一律走形态过渡
const morphing = ref(false)
const snapping = ref(false) // 形态过渡起始帧：先无过渡地落到旧高度
const morphScale = ref({}) // { s0: 0.42, o0: 0.98 } 旧高度 / 新高度
let morphTimer = 0
let entranceScheduled = false

function pctOf(value, max) {
  const v = Number(value) || 0
  if (!max || v <= 0) return 0
  return Math.max(3, Math.min(100, (v / max) * 100))
}

const barScale = (prefix, index) => {
  if (!barsLive.value) return 0
  return morphScale.value[`${prefix}${index}`] ?? 1
}

// 首播只播一次：刻度淡入 300ms，柱子随后依次生长（错开 68ms）
function playEntrance() {
  if (entranceScheduled || barsLive.value || !entered.value || !trendPoints.value.length) return
  entranceScheduled = true
  nextTick(() => {
    requestAnimationFrame(() => {
      chartReady.value = true
      requestAnimationFrame(() => {
        barsLive.value = true
      })
    })
  })
}
watch([trendPoints, entered], playEntrance)

// 筛选 / 区间切换：同一批柱子从旧高度过渡到新高度（500ms，错开 25ms），不清空重画
watch(trendPoints, async (next, prev) => {
  if (!barsLive.value || !next.length) return
  const prevMax = prev && prev.length ? chartMaxOf(prev) : 0
  const nextMax = chartMaxOf(next)
  const from = {}
  next.forEach((point, index) => {
    const old = prev?.[index]
    // 新增列从 0 长出来，已有列从旧高度接上
    if (!old || !prevMax) {
      from[`s${index}`] = 0
      from[`o${index}`] = 0
      return
    }
    from[`s${index}`] =
      pctOf(old.sessionCount, prevMax) / Math.max(1, pctOf(point.sessionCount, nextMax))
    from[`o${index}`] =
      pctOf(old.humanSessionCount, prevMax) / Math.max(1, pctOf(point.humanSessionCount, nextMax))
  })
  morphing.value = true
  snapping.value = true
  morphScale.value = from
  await nextTick()
  snapping.value = false
  // 双 rAF：先让浏览器画一帧「旧高度」，再解除起始缩放走过渡
  requestAnimationFrame(() =>
    requestAnimationFrame(() => {
      morphScale.value = {}
    })
  )
  window.clearTimeout(morphTimer)
  // 等最后一根柱子落位，再解除「形态过渡中」状态
  morphTimer = window.setTimeout(() => {
    morphing.value = false
  }, 520 + Math.min(13, next.length) * 25)
})

onBeforeUnmount(() => window.clearTimeout(morphTimer))

/* ---------- 工单分布 ---------- */
const mapItems = (items, dict) =>
  (items || []).map((item) => ({
    key: item.key || item.label,
    label: item.label || dict?.[item.key]?.label || item.key || '—',
    value: Number(item.count) || 0,
    tone: dictTone(dict, item.key, 'indigo')
  }))

const statusItems = computed(() => mapItems(distribution.value?.byStatus, ORDER_STATUS_DICT))
const priorityItems = computed(() => mapItems(distribution.value?.byPriority, PRIORITY_DICT))
const categoryItems = computed(() =>
  (distribution.value?.byCategory || []).map((item) => ({
    label: item.label || item.key || '—',
    value: Number(item.count) || 0
  }))
)

const statusMax = computed(() => Math.max(1, ...statusItems.value.map((item) => item.value)))
const priorityMax = computed(() => Math.max(1, ...priorityItems.value.map((item) => item.value)))
const categoryMax = computed(() => Math.max(1, ...categoryItems.value.map((item) => item.value)))

const widthOf = (value, max) => {
  const v = Number(value) || 0
  if (v <= 0) return '0%'
  return `${Math.max(3, Math.min(100, (v / (max || 1)) * 100))}%`
}

/* ---------- 知识命中 ---------- */
const kbTop = computed(() => {
  const list = kbHit.value?.topEntries
  return Array.isArray(list) ? list.slice(0, 10) : []
})
const kbMax = computed(() => Math.max(1, ...kbTop.value.map((item) => Number(item.hitCount) || 0)))

/* ---------- AI / 坐席 ---------- */
const tokenTotal = computed(
  () => (Number(aiCalls.value?.totalPromptTokens) || 0) + (Number(aiCalls.value?.totalCompletionTokens) || 0)
)
const modelRows = computed(() => (Array.isArray(aiCalls.value?.byModel) ? aiCalls.value.byModel : []))

// 比率转百分比数字，交给 CountNum 滚动（无效值显示占位）
const kbDirectRate = computed(() => {
  const n = Number(kbHit.value?.directAnswerRate)
  return Number.isFinite(n) ? n * 100 : null
})
const aiSuccessRate = computed(() => {
  const n = Number(aiCalls.value?.successRate)
  return Number.isFinite(n) ? n * 100 : null
})

/* ---------- 运营指标（S6-A） ---------- */
const asPercent = (value) => {
  const n = Number(value)
  return Number.isFinite(n) ? Number((n * 100).toFixed(2)) : null
}
const opsThreeMinRate = computed(() => asPercent(opsMetrics.value?.threeMinuteReplyRate))
const opsAiResolveRate = computed(() => asPercent(opsMetrics.value?.aiResolveRate))
const opsTransferRate = computed(() => asPercent(opsMetrics.value?.transferRate))
const opsOverdueRate = computed(() => asPercent(opsMetrics.value?.orderOverdueRate))

/* ---------- 数据加载 ---------- */
async function loadOverview() {
  overview.value = await getStatsOverview(rangeParams.value)
}

async function loadTrend() {
  trendData.value = await getStatsSessionTrend({ ...rangeParams.value, granularity: 'day' })
}

async function loadDistribution() {
  distribution.value = await getStatsWorkOrderDistribution(rangeParams.value)
}

async function loadKb() {
  kbHit.value = await getStatsKbHit({ ...rangeParams.value, limit: 10 })
}

async function loadAi() {
  aiCalls.value = await getStatsAiCalls({ ...rangeParams.value, granularity: 'day' })
}

async function loadWorkload() {
  workloadLoading.value = true
  try {
    const data = await getStatsAgentWorkload({
      ...rangeParams.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    workload.value = {
      list: Array.isArray(data?.list) ? data.list : [],
      total: Number(data?.total) || 0
    }
  } catch {
    workload.value = { list: [], total: 0 }
    throw new Error('坐席工作量加载失败')
  } finally {
    workloadLoading.value = false
  }
}

async function loadOps() {
  try {
    opsMetrics.value = await getOpsMetrics(rangeParams.value)
  } catch {
    opsMetrics.value = null
    throw new Error('运营指标加载失败')
  }
}

/** 回填历史会话的轮次指标（仅补齐未计算会话，已计算的不变） */
async function rebuildMetrics() {
  rebuilding.value = true
  try {
    const data = await rebuildOpsMetrics(rangeParams.value)
    const processed = Number(data?.processed) || 0
    ElMessage.success(
      processed ? `已回填 ${processed} 个会话的轮次指标` : '所选区间无需回填'
    )
    await run([loadOps])
  } catch {
    // 错误提示由 http 拦截器统一处理，这里只负责释放 loading
  } finally {
    rebuilding.value = false
  }
}

async function run(tasks) {
  loading.value = true
  error.value = ''
  const results = await Promise.allSettled(tasks.map((task) => task()))
  const failed = results.filter((item) => item.status === 'rejected')
  if (failed.length) {
    error.value =
      failed.length === tasks.length
        ? '统计数据加载失败，请确认后端服务已启动且当前账号为管理员'
        : `有 ${failed.length} 项统计数据加载失败，可点击「重新加载」重试`
  }
  updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  loading.value = false
}

function loadAll() {
  return run([loadOverview, loadTrend, loadDistribution, loadKb, loadAi, loadOps, loadWorkload])
}

function loadSection(tab) {
  if (tab === 'orders') return run([loadDistribution])
  if (tab === 'knowledge') return run([loadKb])
  if (tab === 'agents') return run([loadAi, loadWorkload])
  if (tab === 'ops') return run([loadOps])
  return run([loadOverview, loadTrend])
}

function switchTab(tab) {
  activeTab.value = tab
  // 每次切换页签都重新请求对应维度，避免展示过期数据
  loadSection(tab)
}

function applyPreset(key) {
  preset.value = key
  if (key === 'month') {
    const now = new Date()
    range.value = [toDateText(new Date(now.getFullYear(), now.getMonth(), 1)), toDateText(now)]
  } else {
    range.value = defaultRange(Number(key))
  }
  pageNum.value = 1
  loadAll()
}

function onRangeChange() {
  if (!range.value || range.value.length !== 2) {
    range.value = defaultRange(7)
    preset.value = '7'
  } else {
    preset.value = 'custom'
  }
  pageNum.value = 1
  loadAll()
}

function changePage(page) {
  pageNum.value = page
  run([loadWorkload])
}

function changeSize(size) {
  pageSize.value = size
  pageNum.value = 1
  run([loadWorkload])
}

/* ---------- 导出 ---------- */
function exportCsv() {
  const rows = [
    ['智客通数据统计', `${range.value[0]} 至 ${range.value[1]}`],
    [],
    ['概览指标', '数值'],
    ['会话总数', overview.value?.sessionTotal ?? ''],
    ['AI 独立接待', overview.value?.sessionAiOnly ?? ''],
    ['转人工会话', overview.value?.sessionTransferred ?? ''],
    ['平均满意度', overview.value?.avgRating ?? ''],
    ['工单总数', overview.value?.workOrderTotal ?? ''],
    ['超时工单', overview.value?.workOrderOverdue ?? ''],
    ['AI 调用次数', overview.value?.aiCalls ?? ''],
    ['AI 成功率', percentOf(overview.value?.aiSuccessRate)],
    [],
    ['工单状态', '数量'],
    ...statusItems.value.map((item) => [item.label, item.value]),
    [],
    ['知识条目', '命中次数'],
    ...kbTop.value.map((item) => [item.question, item.hitCount ?? 0]),
    [],
    ['坐席', '接入会话', '已结束', '满意度', '解决工单'],
    ...workload.value.list.map((item) => [
      item.agentName || item.agentId,
      item.servedCount,
      item.closedCount,
      item.avgRating,
      item.workOrderResolved
    ])
  ]
  const csv = `\uFEFF${rows
    .map((row) => row.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(','))
    .join('\r\n')}`
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `智客通数据统计_${range.value[0]}_${range.value[1]}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  ElMessage.success('报表已导出')
}

onMounted(loadAll)
</script>

<style scoped>
/* ---------- 筛选 ---------- */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 14px;
  margin-bottom: 14px;
}

.quick-ranges {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px;
  border-radius: var(--zk-radius-lg);
  background: var(--zk-surface-3);
}

.range-chip {
  height: 28px;
  padding: 0 10px;
  border: 0;
  border-radius: var(--zk-radius-sm);
  background: transparent;
  color: var(--zk-text-2);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out),
    color var(--zk-dur-fast) var(--zk-ease-out);
}

.range-chip:hover {
  color: var(--zk-text);
}

.range-chip.is-active {
  background: var(--zk-surface);
  color: var(--zk-text);
  font-weight: 600;
  box-shadow: 0 0 0 1px var(--zk-border) inset;
}

.range-meta {
  font-size: 11.5px;
  color: var(--zk-text-3);
  font-variant-numeric: tabular-nums;
}

.range-meta--right {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.stat-alert {
  margin-bottom: 14px;
  border-radius: var(--zk-radius);
}

.alert-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12.5px;
}

/* ---------- 页签 ---------- */
.stat-tabs {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  margin-bottom: 14px;
  border-radius: var(--zk-radius-lg);
  border: 1px solid var(--zk-border);
  background: var(--zk-surface);
}

.stat-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 13px;
  border: 0;
  border-radius: var(--zk-radius-sm);
  background: transparent;
  color: var(--zk-text-3);
  font-size: 12.5px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out),
    color var(--zk-dur-fast) var(--zk-ease-out);
}

.stat-tab:hover {
  color: var(--zk-text);
  background: var(--zk-surface-2);
}

.stat-tab.is-active {
  color: var(--zk-accent-hover);
  background: var(--zk-accent-soft);
}

/* ---------- 指标卡 ---------- */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.metric-grid--three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.metric-card {
  background: var(--zk-surface);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  padding: 13px 14px 12px;
  /* 入场只动 opacity / transform；错开延迟只给 opacity，悬停边框立即可响应 */
  transition: opacity 380ms var(--zk-ease-soft), transform 480ms var(--zk-ease-spring),
    border-color var(--zk-dur-fast) var(--zk-ease-out);
  transition-delay: calc(var(--zk-i, 0) * var(--zk-stagger, 45ms)), 0ms, 0ms;
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
  align-items: center;
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

.metric-value.is-loading .sk-bar {
  width: 58%;
  height: 20px;
  border-radius: var(--zk-radius-xs);
  background: linear-gradient(90deg, #f0f0f2 25%, #fafafa 37%, #f0f0f2 63%);
  background-size: 400% 100%;
  animation: stat-shimmer 1.3s ease infinite;
}

@keyframes stat-shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

/* 骨架卡与真实卡同高，数据到达前后版面不跳 */
.metric-card > .zk-skeleton-block {
  min-height: 76px;
  justify-content: center;
}

.trend-skeleton {
  padding: 4px 0 8px;
}

.trend-skeleton .zk-skeleton-block {
  min-height: 288px;
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
.metric-card.red .metric-icon {
  color: var(--zk-red);
  background: var(--zk-red-soft);
}

/* ---------- 布局 ---------- */
.panel-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(0, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}

.panel-grid--equal {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

/* ---------- 图表 ---------- */
.chart-card,
.side-card,
.dist-card,
.workload-card,
.model-card {
  padding: 16px 18px 14px;
}

.chart-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.chart-title {
  font-size: 13.5px;
  font-weight: 650;
  color: var(--zk-text);
}

.chart-sub {
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

/* 图例与数据看板一致：10px 色块 + 11.5px 文本 */
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--zk-text-2);
}

.swatch {
  width: 10px;
  height: 10px;
  border-radius: var(--zk-radius-xs);
  display: inline-block;
}

.swatch--sessions {
  background: var(--zk-chart-sessions);
}

.swatch--orders {
  background: var(--zk-chart-orders);
}

/* ---------- 趋势图：与数据看板「近 7 日会话 / 工单趋势」同一套设计语言 ---------- */
.chart-card {
  display: flex;
  flex-direction: column;
}

.trend-totals {
  font-size: 11.5px;
  color: var(--zk-text-3);
  white-space: nowrap;
}

.trend-totals b {
  color: var(--zk-text);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.trend-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 2px 0 0;
}

.trend-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 2px 0 10px;
}

.trend-chart {
  display: flex;
  gap: 8px;
  flex: 1;
}

.trend-axis {
  position: relative;
  width: 32px;
  height: 240px;
  flex: 0 0 32px;
}

.axis-label {
  position: absolute;
  right: 0;
  transform: translateY(-50%);
  font-size: 11px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
  opacity: 0;
  transition: opacity 300ms var(--zk-ease-out);
}

.axis-label:first-child {
  transform: translateY(0);
}

.axis-label:last-child {
  transform: translateY(-100%);
}

.trend-main {
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.trend-plot {
  position: relative;
  height: 240px;
  min-width: 100%;
  padding: 0 4px;
}

.grid-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background: #ededf0;
  opacity: 0;
  transition: opacity 300ms var(--zk-ease-out);
}

.grid-line:last-of-type {
  background: var(--zk-border-strong);
}

/* 刻度先淡入（300ms），柱子随后依次长出 */
.trend-chart.is-ready .axis-label,
.trend-chart.is-ready .grid-line {
  opacity: 1;
}

.trend-cols {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  gap: 6px;
}

.trend-cols.is-dense {
  gap: 3px;
}

.trend-col {
  position: relative;
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.trend-bars {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 100%;
}

.bar-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
  gap: 3px;
}

.bar-val {
  font-size: 11.5px;
  font-weight: 600;
  color: #3730a3;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  opacity: 0;
  transition: opacity 300ms var(--zk-ease-out) calc(var(--zk-bar-i, 0) * 40ms + 140ms);
}

.bar-val--muted {
  color: var(--zk-text-2);
}

.trend-plot.is-live .bar-val {
  opacity: 1;
}

/* 数据切换时柱顶数字先隐去，柱子落位后再按 25ms 错开回来 */
.trend-plot.is-morphing .bar-val {
  opacity: 0;
  transition: opacity 120ms var(--zk-ease-in) 0ms;
}

.bar {
  width: 18px;
  max-width: 20px;
  border-radius: 2px 2px 0 0;
  transform: scaleY(var(--zk-bar-scale, 1));
  transform-origin: bottom center;
  will-change: transform;
  /* 只动 transform：刻度先淡入，柱子 120ms 后从底部长出，逐根错开 68ms */
  transition: transform 520ms var(--zk-ease-out) calc(var(--zk-bar-i, 0) * 68ms + 120ms),
    filter var(--zk-dur-fast) var(--zk-ease-out);
}

/* 从旧高度变到新高度：500ms，错开 25ms */
.trend-plot.is-morphing .bar {
  transition-duration: 500ms, var(--zk-dur-fast);
  transition-delay: calc(var(--zk-bar-i, 0) * 25ms), 0ms;
}

/* 形态过渡的起始帧不允许有过渡，否则会先反向缩一下 */
.trend-plot.is-snap .bar {
  transition: none !important;
}

.trend-cols.is-dense .trend-bars {
  gap: 2px;
}

.trend-cols.is-dense .bar {
  width: 8px;
}

.trend-cols.is-dense .bar-val {
  display: none;
}

.bar--sessions {
  background: var(--zk-chart-sessions);
}

.bar--orders {
  background: var(--zk-chart-orders);
}

/* hover 交互提示 */
.trend-tip {
  position: absolute;
  bottom: calc(100% - 4px);
  left: 50%;
  transform: translateX(-50%) translateY(4px);
  background: var(--zk-ink-900);
  color: #fff;
  border-radius: var(--zk-radius-sm);
  padding: 8px 10px;
  font-size: 11px;
  line-height: 1.5;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--zk-dur-fast) var(--zk-ease-out),
    transform var(--zk-dur-fast) var(--zk-ease-out);
  z-index: 5;
  box-shadow: var(--zk-shadow-md);
}

.tip-date {
  font-weight: 600;
  margin-bottom: 3px;
  color: #e4e4e7;
}

.tip-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tip-row .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.dot--sessions {
  background: var(--zk-chart-sessions);
}

.dot--orders {
  background: #c7cbff;
}

.dot--muted {
  background: #a1a1aa;
}

.trend-col:hover .trend-tip {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}

.trend-col:hover .bar {
  filter: brightness(0.94);
}

.trend-dates {
  display: flex;
  justify-content: space-around;
  gap: 6px;
  padding: 8px 4px 0;
}

.trend-date {
  flex: 1;
  text-align: center;
  font-size: 11px;
  color: var(--zk-text-2);
  letter-spacing: 0.2px;
}

/* 汇总指标：小指标卡，与图表同属一个模块 */
.trend-foot {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--zk-border);
}

.mini-stat {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 7px 10px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  background: var(--zk-surface-2);
}

.mini-label {
  font-size: 11px;
  color: var(--zk-text-3);
}

.mini-value {
  font-size: 17px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--zk-text);
}

/* ---------- 实时状态 ---------- */
.live-list {
  list-style: none;
  margin: 4px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.live-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--zk-border);
  font-size: 12.5px;
  color: var(--zk-text-2);
}

.live-list li:last-child {
  border-bottom: 0;
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--zk-text-3);
}

.live-dot.amber {
  background: #f59e0b;
}
.live-dot.teal {
  background: #10b981;
}
.live-dot.indigo {
  background: var(--zk-accent);
}

.live-label {
  flex: 1;
}

.live-value {
  font-size: 13px;
  color: var(--zk-text);
}

.side-note {
  margin-top: 12px;
  padding: 9px 11px;
  border-radius: var(--zk-radius-lg);
  background: var(--zk-surface-2);
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--zk-text-3);
}

.side-note a {
  color: var(--zk-accent-hover);
}

/* ---------- 分布 ---------- */
.dist-card--wide {
  padding-bottom: 18px;
}

.dist-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 4px;
}

.dist-row {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr) 52px;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--zk-text-2);
}

.dist-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dist-track {
  height: 4px;
  border-radius: var(--zk-radius-xs);
  background: #ececef;
  overflow: hidden;
}

.dist-track i {
  display: block;
  height: 100%;
  border-radius: var(--zk-radius-xs);
  background: var(--zk-accent);
  /* 既有宽度进度条：与柱子形态过渡同节奏 */
  transition: width 460ms var(--zk-ease-emphasis);
}

.dist-track i.tone-teal {
  background: var(--zk-green);
}
.dist-track i.tone-amber {
  background: var(--zk-amber-edge);
}
.dist-track i.tone-red {
  background: var(--zk-red-edge);
}
.dist-track i.tone-slate {
  background: #a1a1aa;
}

.dist-value {
  text-align: right;
  color: var(--zk-text);
  font-weight: 600;
}

.cat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px;
}

.cat-card {
  padding: 11px 13px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  background: var(--zk-surface-2);
}

.cat-name {
  font-size: 12px;
  color: var(--zk-text-3);
}

.cat-value {
  margin: 4px 0 8px;
  font-size: 20px;
  font-weight: 700;
  color: var(--zk-text);
}

.cat-track {
  height: 4px;
  border-radius: var(--zk-radius-xs);
  background: #ececef;
  overflow: hidden;
}

.cat-track i {
  display: block;
  height: 100%;
  border-radius: var(--zk-radius-xs);
  background: var(--zk-accent);
  transition: width 460ms var(--zk-ease-emphasis);
}

/* ---------- 表格 ---------- */
.stat-table {
  margin-top: 4px;
}

.rank {
  display: inline-grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: var(--zk-radius-sm);
  font-size: 11px;
  font-weight: 600;
  color: var(--zk-text-3);
  background: var(--zk-surface-3);
  font-variant-numeric: tabular-nums;
}

.rank.is-top {
  color: var(--zk-accent-hover);
  background: var(--zk-accent-soft);
}

.hit-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hit-num {
  min-width: 30px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--zk-accent-hover);
  font-variant-numeric: tabular-nums;
}

.hit-bar {
  flex: 1;
  max-width: 62px;
  height: 4px;
  border-radius: var(--zk-radius-xs);
  background: #ececef;
  overflow: hidden;
}

.hit-bar i {
  display: block;
  height: 100%;
  border-radius: var(--zk-radius-xs);
  background: var(--zk-accent);
  transition: width 420ms var(--zk-ease-emphasis);
}

.more-link {
  font-size: 12px;
  color: var(--zk-accent-hover);
  white-space: nowrap;
}

.zk-pager {
  padding-top: 12px;
  display: flex;
  justify-content: flex-end;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1180px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .panel-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .metric-grid,
  .metric-grid--three,
  .metric-grid--four {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .filter-bar {
    padding: 10px 12px;
  }

  .range-meta--right {
    margin-left: 0;
  }

  .stat-tabs {
    width: 100%;
    overflow-x: auto;
  }

  .stat-tab {
    flex: 0 0 auto;
  }

  .metric-grid,
  .metric-grid--three,
  .metric-grid--four {
    grid-template-columns: minmax(0, 1fr);
  }

  .chart-card,
  .side-card,
  .dist-card,
  .workload-card,
  .model-card {
    padding: 14px 13px 12px;
  }

  .panel-grid--equal {
    grid-template-columns: minmax(0, 1fr);
  }
}

/* ---------- 运营指标（S6-A） ---------- */
.ops-note-card {
  margin-top: 14px;
}

.ops-note-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.ops-note-list li {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
  padding: 10px 12px;
  border-radius: var(--zk-radius);
  background: var(--zk-surface-3);
}

.ops-note-label {
  min-width: 96px;
  font-size: 12.5px;
  color: var(--zk-text-2);
}

.ops-note-list b {
  font-size: 16px;
  color: var(--zk-text);
  font-variant-numeric: tabular-nums;
}

.ops-note-hint {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

/* ---------- 减少动效：图形直接给最终值，不靠 JS 绕过 ---------- */
@media (prefers-reduced-motion: reduce) {
  .bar {
    transition: none !important;
  }

  .bar-val,
  .axis-label,
  .grid-line {
    opacity: 1 !important;
    transition: none !important;
  }
}
</style>
