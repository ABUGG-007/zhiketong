<template>
  <div class="zk-page">
    <PageHeader
      title="知识库"
      description="AI 接待时按「关键词精准匹配 → 语义相似度」顺序检索，命中即直接回复，未命中转引导话术。可先用命中测试验证覆盖度，再补充词条。"
    >
      <template #actions>
        <el-button v-if="canEditEntry" :icon="Aim" @click="openMatchTest">命中测试</el-button>
        <el-button v-if="canEditEntry" :icon="Plus" type="primary" @click="openEntry()">新增条目</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="reload">刷新</el-button>
      </template>
    </PageHeader>

    <div class="kb-layout">
      <!-- 分类侧栏：宽屏常驻，窄屏改为抽屉 -->
      <aside
        class="zk-card kb-side"
        :class="{ 'is-open': categoryPanel }"
        :inert="isNarrow && !categoryPanel"
        :aria-hidden="isNarrow && !categoryPanel ? 'true' : null"
        aria-label="知识分类"
      >
        <div class="side-head">
          <span class="side-title">知识分类</span>
          <div class="side-head-actions">
            <el-button
              v-if="canManageCategory"
              link
              type="primary"
              :icon="FolderAdd"
              @click="openCategory()"
            >
              新建
            </el-button>
            <el-button
              v-if="isNarrow"
              link
              :icon="Close"
              aria-label="关闭分类面板"
              @click="closeCategoryPanel"
            />
          </div>
        </div>

        <button
          type="button"
          class="side-total"
          :class="{ active: filters.categoryId === '' }"
          @click="selectCategory(null)"
        >
          <span class="side-total-name">全部条目</span>
          <b class="zk-num">{{ totalAll }}</b>
        </button>

        <el-tree
          ref="treeRef"
          :data="categoryTree"
          node-key="id"
          :props="{ label: 'name', children: 'children' }"
          :expand-on-click-node="false"
          :default-expand-all="true"
          highlight-current
          empty-text="还没有分类"
          @node-click="onCategoryClick"
        >
          <template #default="{ data }">
            <div class="cat-node">
              <span class="cat-node-name" :title="data.name">{{ data.name }}</span>
              <span v-if="Number(data.status) === 0" class="cat-off">停用</span>
              <span class="cat-node-count zk-num">{{ data.entryCount ?? 0 }}</span>
              <span v-if="canManageCategory" class="cat-node-ops">
                <el-button
                  link
                  :icon="Plus"
                  :aria-label="`在 ${data.name} 下新建子分类`"
                  title="新建子分类"
                  @click.stop="openCategory(null, data.id)"
                />
                <el-button
                  link
                  :icon="Edit"
                  :aria-label="`编辑分类 ${data.name}`"
                  title="编辑分类"
                  @click.stop="openCategory(data)"
                />
                <el-button
                  link
                  type="danger"
                  :icon="Delete"
                  :aria-label="`删除分类 ${data.name}`"
                  title="删除分类"
                  @click.stop="removeCategory(data)"
                />
              </span>
            </div>
          </template>
        </el-tree>

        <div class="side-foot">
          <template v-if="canManageCategory">
            <el-icon aria-hidden="true"><InfoFilled /></el-icon>
            <span>停用分类后，该分类词条不再参与 AI 匹配</span>
          </template>
          <template v-else-if="isUser">
            <el-icon aria-hidden="true"><InfoFilled /></el-icon>
            <span>仅展示启用中的分类与词条</span>
          </template>
          <template v-else>
            <el-icon aria-hidden="true"><InfoFilled /></el-icon>
            <span>可维护词条；分类与删除仅管理员可操作</span>
          </template>
        </div>
      </aside>

      <div
        v-if="isNarrow"
        class="kb-side-mask"
        :class="{ 'is-open': categoryPanel }"
        aria-hidden="true"
        @click="closeCategoryPanel"
      />

      <!-- 词条主区 -->
      <section class="zk-card kb-main">
        <div class="zk-toolbar">
          <label for="kb-search" class="sr-only">搜索知识条目</label>
          <el-input
            id="kb-search"
            v-model="filters.keyword"
            class="toolbar-search"
            placeholder="搜索问题 / 答案 / 关键词"
            aria-label="搜索知识条目"
            :prefix-icon="Search"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <el-button
            v-if="isNarrow"
            class="toolbar-cat"
            :icon="Filter"
            :title="`筛选分类：${activeCategoryName}`"
            @click="categoryPanel = true"
          >
            {{ activeCategoryName }}
          </el-button>
          <el-select
            v-if="canEditEntry"
            v-model="filters.status"
            placeholder="全部状态"
            clearable
            aria-label="按状态筛选"
            @change="search"
          >
            <el-option label="启用中" :value="1" />
            <el-option label="已停用" :value="0" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
          <div class="grow" />
          <span v-show="!loading || rows.length" class="toolbar-meta" aria-live="polite">
            共 <b class="zk-num">{{ total }}</b> 条
          </span>
        </div>

        <div
          v-if="!isCompact"
          :ref="bindTableWrap"
          class="table-scroll"
          :class="{ 'table-result-fade': resultFade }"
          role="region"
          aria-label="知识库条目列表"
          tabindex="0"
          :aria-busy="loading ? 'true' : 'false'"
        >
          <ZkSkeleton v-if="!loaded" variant="table" :rows="10" :cols="7" class="table-skeleton" />
          <el-table
            v-else
            v-loading="loading"
            class="zk-table-fade-in"
            :data="rows"
            row-key="id"
            aria-label="知识库条目列表"
            :row-class-name="rowClassName"
            @row-click="openDetail"
          >
            <el-table-column v-if="showCategory" label="分类" width="112">
              <template #default="{ row }">
                <span class="cat-name">{{ row.categoryName || '未分类' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="问题 / 答案" min-width="280">
              <template #default="{ row }">
                <div class="cell-question-row">
                  <span v-if="!showCategory" class="cell-cat-mini">
                    {{ row.categoryName || '未分类' }}
                  </span>
                  <span class="cell-question" :title="row.question">{{ row.question }}</span>
                </div>
                <div class="cell-answer" :title="row.answer">{{ row.answer }}</div>
              </template>
            </el-table-column>
            <el-table-column v-if="showKeywords" label="关键词" width="180">
              <template #default="{ row }">
                <div class="kw-list">
                  <span
                    v-for="kw in splitKeywords(row.keywords).slice(0, 3)"
                    :key="kw"
                    class="kw-tag"
                  >
                    {{ kw }}
                  </span>
                  <span v-if="splitKeywords(row.keywords).length > 3" class="kw-more">
                    +{{ splitKeywords(row.keywords).length - 3 }}
                  </span>
                  <span v-if="!splitKeywords(row.keywords).length" class="zk-muted">—</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="showSimilar" label="相似问法" width="96" align="right">
              <template #default="{ row }">
                <span class="zk-num">{{ row.similarCount ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="命中" width="96" align="right">
              <template #default="{ row }">
                <div class="hit-cell" :aria-label="`命中 ${row.hitCount ?? 0} 次`">
                  <span class="hit-num zk-num">{{ row.hitCount ?? 0 }}</span>
                  <span class="hit-bar" aria-hidden="true">
                    <i :style="{ width: hitPercent(row.hitCount) }" />
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="86">
              <template #default="{ row }">
                <StatusPill
                  :label="Number(row.status) === 1 ? '启用' : '停用'"
                  :tone="Number(row.status) === 1 ? 'teal' : 'slate'"
                  :class="{ 'zk-flash': changedId === row.id }"
                />
              </template>
            </el-table-column>
            <el-table-column v-if="showUpdate" label="更新时间" width="146">
              <template #default="{ row }">
                <span class="cell-time">{{ formatDateTime(row.updateTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="168" fixed="right">
              <template #default="{ row }">
                <div class="row-ops">
                  <el-button link type="primary" @click.stop="openDetail(row)">查看</el-button>
                  <el-button v-if="canEditEntry" link type="primary" @click.stop="openEntry(row)">
                    编辑
                  </el-button>
                  <el-dropdown
                    v-if="canDeleteEntry"
                    trigger="click"
                    @command="(command) => onRowCommand(command, row)"
                  >
                    <el-button link type="primary" @click.stop>
                      更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="toggle">
                          {{ Number(row.status) === 1 ? '停用词条' : '启用词条' }}
                        </el-dropdown-item>
                        <el-dropdown-item command="delete" divided>删除词条</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>

            <template #empty>
              <ZkEmpty
                v-if="!loading"
                size="sm"
                :title="
                  error
                    ? '知识库加载失败'
                    : hasFilter
                      ? '没有匹配的知识词条'
                      : '知识库还没有词条'
                "
                :hint="
                  error
                    ? '请稍后重新加载，或确认后端服务是否正常'
                    : hasFilter
                      ? '换个关键词，或切换到「全部条目」'
                      : canEditEntry
                        ? '录入第一条标准问答后，AI 就能按关键词与语义匹配直接回复'
                        : '管理员尚未录入知识条目'
                "
              >
                <el-button v-if="error" @click="reload">重新加载</el-button>
                <el-button v-else-if="hasFilter" @click="resetFilters">清空筛选</el-button>
                <el-button v-else-if="canEditEntry" type="primary" @click="openEntry()">
                  新增条目
                </el-button>
              </ZkEmpty>
            </template>
          </el-table>
        </div>

        <!-- 手机端：表格列过密，换成卡片列表 -->
        <div v-else v-loading="loading && loaded" class="kb-list">
          <ZkSkeleton v-if="!loaded" variant="list" :rows="4" />
          <template v-else>
          <article v-for="row in rows" :key="row.id" class="kb-item" @click="openDetail(row)">
            <div class="kb-item-top">
              <h3 class="kb-item-q">{{ row.question }}</h3>
              <StatusPill
                :label="Number(row.status) === 1 ? '启用' : '停用'"
                :tone="Number(row.status) === 1 ? 'teal' : 'slate'"
              />
            </div>
            <p class="kb-item-a">{{ row.answer }}</p>
            <div class="kb-item-meta">
              <span>{{ row.categoryName || '未分类' }}</span>
              <span aria-hidden="true">·</span>
              <span>命中 {{ row.hitCount ?? 0 }}</span>
              <span aria-hidden="true">·</span>
              <span>相似 {{ row.similarCount ?? 0 }}</span>
              <span class="kb-item-ops">
                <el-button link type="primary" @click.stop="openDetail(row)">查看</el-button>
                <el-button v-if="canEditEntry" link type="primary" @click.stop="openEntry(row)">
                  编辑
                </el-button>
              </span>
            </div>
          </article>
          <ZkEmpty
            v-if="!rows.length && !loading"
            size="sm"
            :title="error ? '知识库加载失败' : hasFilter ? '没有匹配的知识词条' : '知识库还没有词条'"
            hint="换个关键词，或切换到「全部条目」"
          >
            <el-button v-if="hasFilter" size="small" @click="resetFilters">清空筛选</el-button>
            <el-button v-else-if="canEditEntry" size="small" type="primary" @click="openEntry()">
              新增条目
            </el-button>
          </ZkEmpty>
          </template>
        </div>

        <div class="zk-pager">
          <el-pagination
            background
            :layout="isCompact ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
            :total="total"
            :current-page="pageNum"
            :page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            aria-label="知识库分页"
            @current-change="changePage"
            @size-change="changeSize"
          />
        </div>
      </section>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      匹配与应答优先级：关键词精准命中 &gt; 语义相似度直答（阈值由服务端 zhiketong.chat.ai-confidence-threshold 下发，默认 0.60）&gt; 参考知识 + 大模型生成 &gt; 引导话术兜底；单会话连续未命中达阈值后会自动给出转人工与提交工单入口
    </div>

    <!-- 条目详情 -->
    <el-drawer v-model="detailVisible" title="知识条目详情" size="480px">
      <div v-if="detailLoading" class="detail-loading">
        <el-skeleton :rows="6" animated />
      </div>
      <div v-else-if="activeEntry" class="detail">
        <div class="detail-row">
          <span class="detail-label">分类</span>
          <span class="cat-name">{{ activeEntry.categoryName || '未分类' }}</span>
        </div>
        <div class="detail-block">
          <div class="detail-label">标准问题</div>
          <div class="detail-question">{{ activeEntry.question }}</div>
        </div>
        <div class="detail-block">
          <div class="detail-label">标准答案</div>
          <div class="detail-answer">{{ activeEntry.answer }}</div>
        </div>
        <div class="detail-row">
          <span class="detail-label">关键词</span>
          <div class="kw-list" v-reveal.group style="--zk-stagger: 30ms">
            <span v-for="kw in splitKeywords(activeEntry.keywords)" :key="kw" class="kw-tag">
              {{ kw }}
            </span>
            <span v-if="!splitKeywords(activeEntry.keywords).length" class="zk-muted">—</span>
          </div>
        </div>
        <div class="detail-row">
          <span class="detail-label">标签</span>
          <div class="kw-list" v-reveal.group style="--zk-stagger: 30ms">
            <span v-for="tag in splitKeywords(activeEntry.tags)" :key="tag" class="kw-tag is-tag">
              {{ tag }}
            </span>
            <span v-if="!splitKeywords(activeEntry.tags).length" class="zk-muted">—</span>
          </div>
        </div>
        <div class="detail-grid">
          <div>
            <div class="detail-label">累计命中</div>
            <div class="detail-num zk-num">{{ activeEntry.hitCount ?? 0 }}</div>
          </div>
          <div>
            <div class="detail-label">状态</div>
            <StatusPill
              :label="Number(activeEntry.status) === 1 ? '启用' : '停用'"
              :tone="Number(activeEntry.status) === 1 ? 'teal' : 'slate'"
            />
          </div>
          <div>
            <div class="detail-label">更新时间</div>
            <div class="detail-time">{{ formatDateTime(activeEntry.updateTime) }}</div>
          </div>
        </div>
        <div class="detail-block">
          <div class="detail-label">相似问法（{{ similarList.length }} / 20）</div>
          <div class="similar-list" v-reveal.group style="--zk-stagger: 30ms">
            <div v-for="item in similarList" :key="item.id" class="similar-item">
              <span class="similar-q">{{ item.question }}</span>
              <span class="similar-source">{{ similarSourceLabel(item.source) }}</span>
              <el-button
                v-if="canDeleteEntry"
                link
                type="danger"
                :icon="Delete"
                aria-label="删除相似问法"
                @click="removeSimilar(item)"
              />
            </div>
            <span v-if="!similarList.length" class="zk-muted">
              暂无相似问法，同义改写与口语化问法可能匹配不到
            </span>
          </div>
          <div v-if="canEditEntry" class="similar-add">
            <el-input
              v-model="similarInput"
              size="small"
              maxlength="255"
              placeholder="输入一条相似问法，例如：退款怎么操作"
              @keyup.enter="addSimilar"
            />
            <el-button size="small" type="primary" :loading="similarSaving" @click="addSimilar">
              添加
            </el-button>
            <el-button size="small" :loading="expandLoading" @click="aiExpand">AI 扩写</el-button>
          </div>
          <div v-if="expandNote" class="form-hint">{{ expandNote }}</div>
          <div v-if="expandCandidates.length" class="expand-list">
            <el-checkbox-group v-model="expandPicked" v-reveal.group style="--zk-stagger: 40ms">
              <el-checkbox v-for="candidate in expandCandidates" :key="candidate" :label="candidate" />
            </el-checkbox-group>
            <el-button size="small" type="primary" :loading="similarSaving" @click="adoptCandidates">
              加入词条
            </el-button>
          </div>
        </div>
        <div class="detail-block">
          <div class="detail-label">版本记录</div>
          <el-skeleton v-if="versionLoading" :rows="2" animated />
          <div v-else-if="versionList.length" class="version-list">
            <div v-for="version in versionList" :key="version.id" class="version-item">
              <span class="version-no zk-num">V{{ version.versionNo }}</span>
              <span class="version-type">{{ version.changeTypeDesc || version.changeType }}</span>
              <span class="version-op">{{ version.operatorName || '系统' }}</span>
              <span class="version-time">{{ formatDateTime(version.createTime) }}</span>
              <el-button
                v-if="canDeleteEntry && version.beforeContent"
                link
                type="primary"
                @click="rollbackVersion(version)"
              >
                回滚
              </el-button>
            </div>
          </div>
          <span v-else class="zk-muted">暂无版本记录</span>
        </div>
        <div class="detail-footer">
          <el-button v-if="canEditEntry" type="primary" :icon="Edit" @click="openEntry(activeEntry)">
            编辑词条
          </el-button>
          <el-button v-if="canEditEntry" :icon="Aim" @click="openMatchTest(activeEntry.question)">
            用该问题做命中测试
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 词条编辑 -->
    <el-dialog
      v-model="entryVisible"
      :title="entryMode === 'create' ? '新增知识条目' : '编辑知识条目'"
      width="640px"
      append-to-body
      class="kb-dialog"
      @closed="resetEntryForm"
    >
      <el-form ref="entryFormRef" :model="entryForm" :rules="entryRules" label-position="top" @submit.prevent>
        <div class="form-row">
          <el-form-item label="所属分类" prop="categoryId">
            <el-select v-model="entryForm.categoryId" placeholder="请选择分类" class="full-width">
              <el-option
                v-for="option in categoryOptions"
                :key="option.id"
                :label="option.name"
                :value="option.id"
                :disabled="Number(option.status) === 0"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="entryForm.status" class="full-width">
              <el-option label="启用（参与匹配）" :value="1" />
              <el-option label="停用（暂不参与）" :value="0" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="标准问题" prop="question">
          <el-input
            v-model="entryForm.question"
            maxlength="255"
            show-word-limit
            placeholder="用户最常见的问法，例如：如何申请退款？"
          />
        </el-form-item>
        <el-form-item label="标准答案" prop="answer">
          <el-input
            v-model="entryForm.answer"
            type="textarea"
            :rows="5"
            maxlength="2000"
            show-word-limit
            resize="none"
            placeholder="给出可执行的完整回答，包含步骤、时效与注意事项"
          />
        </el-form-item>
        <el-form-item label="关键词" prop="keywords">
          <el-input
            v-model="entryForm.keywords"
            maxlength="255"
            show-word-limit
            placeholder="用逗号分隔，例如：退款,退货,申请退款"
          />
          <div class="form-hint">关键词命中优先级最高，建议覆盖用户口语化说法与业务术语</div>
        </el-form-item>
        <el-form-item label="标签（可选）">
          <el-input
            v-model="entryForm.tags"
            maxlength="255"
            show-word-limit
            placeholder="用逗号分隔，例如：售后,退款"
          />
          <div class="form-hint">标签用于按业务线筛选，不参与匹配打分</div>
        </el-form-item>
        <el-form-item label="相似问法（可选，每行一条）">
          <el-input
            v-model="entryForm.similarText"
            type="textarea"
            :rows="3"
            resize="none"
            placeholder="同义改写 / 口语化问法 / 常见错别字，每行一条，最多 20 条"
          />
          <div class="form-hint">相似问法会与标准问题一起参与相似度打分；编辑时以当前内容为准全量替换</div>
        </el-form-item>
        <el-alert
          v-if="entryMode === 'edit' && entryForm.updateByName"
          type="info"
          :closable="false"
          show-icon
          :title="`最近由 ${entryForm.updateByName} 于 ${formatDateTime(entryForm.updateTime)} 修改`"
        />
      </el-form>
      <template #footer>
        <el-button @click="entryVisible = false">取消</el-button>
        <el-button type="primary" :loading="entrySubmitting" @click="submitEntry">
          {{ entryMode === 'create' ? '创建词条' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 分类编辑 -->
    <el-dialog
      v-model="categoryVisible"
      :title="categoryMode === 'create' ? '新建知识分类' : '编辑知识分类'"
      width="480px"
      append-to-body
      @closed="resetCategoryForm"
    >
      <el-form
        ref="categoryFormRef"
        :model="categoryForm"
        :rules="categoryRules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="categoryForm.name" maxlength="50" show-word-limit placeholder="例如：退款售后" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="上级分类">
            <el-select v-model="categoryForm.parentId" clearable placeholder="不选则为顶级分类" class="full-width">
              <el-option
                v-for="option in categoryOptions"
                :key="option.id"
                :label="option.name"
                :value="option.id"
                :disabled="option.id === categoryForm.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="排序值">
            <el-input-number v-model="categoryForm.sort" :min="0" :max="9999" controls-position="right" class="full-width" />
          </el-form-item>
        </div>
        <el-form-item label="状态">
          <el-radio-group v-model="categoryForm.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="分类说明（可选）">
          <el-input
            v-model="categoryForm.description"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            resize="none"
            placeholder="说明该分类收录哪些问题，便于团队协作"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryVisible = false">取消</el-button>
        <el-button type="primary" :loading="categorySubmitting" @click="submitCategory">
          {{ categoryMode === 'create' ? '创建分类' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 命中测试 -->
    <el-dialog v-model="matchVisible" title="命中测试" width="620px" append-to-body @closed="resetMatch">
      <p class="match-desc">
        输入一句用户可能说的话，查看当前知识库能否命中、以什么方式命中。未命中的高频问法建议直接补充为词条。
      </p>
      <div class="match-form">
        <el-input
          v-model="matchQuery"
          placeholder="例如：我买的课程可以退款吗"
          maxlength="200"
          clearable
          @keyup.enter="runMatch"
        />
        <el-select v-model="matchCategoryId" clearable placeholder="全部分类" class="match-cat">
          <el-option
            v-for="option in categoryOptions"
            :key="option.id"
            :label="option.name"
            :value="option.id"
          />
        </el-select>
        <el-button type="primary" :loading="matchLoading" :disabled="!matchQuery.trim()" @click="runMatch">
          测试
        </el-button>
      </div>

      <div v-if="matchResult" class="match-result">
        <div v-if="matchResult.candidates?.length" class="match-list">
          <div v-for="item in matchResult.candidates" :key="item.id" class="match-item">
            <div class="match-item-head">
              <span class="match-question">{{ item.question }}</span>
              <StatusPill
                :label="item.directAnswer ? '直接回答' : '候选参考'"
                :tone="item.directAnswer ? 'teal' : 'amber'"
              />
            </div>
            <div class="match-meta">
              <span>匹配度 <b class="zk-num">{{ scoreText(item.score) }}</b></span>
              <span v-if="item.categoryName">· {{ item.categoryName }}</span>
              <span v-if="item.hitKeywords?.length">· 命中关键词：{{ item.hitKeywords.join('、') }}</span>
            </div>
            <div class="match-answer">{{ item.answer }}</div>
          </div>
        </div>
        <div v-else class="match-empty">
          <p>没有命中任何词条，线上会返回引导话术并累计未匹配次数。</p>
          <el-button v-if="canEditEntry" type="primary" :icon="Plus" @click="createFromMatch">
            用该问法新增词条
          </el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Aim,
  ArrowDown,
  Close,
  Delete,
  Edit,
  Filter,
  FolderAdd,
  InfoFilled,
  Plus,
  Refresh,
  Search
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import {
  createKbCategory,
  createKbEntry,
  createKbSimilarQuestions,
  deleteKbCategory,
  deleteKbEntry,
  deleteKbSimilarQuestion,
  expandKbSimilarQuestions,
  getKbCategories,
  getKbCategoryTree,
  getKbEntries,
  getKbEntryDetail,
  getKbEntryVersions,
  getUserKbCategories,
  getUserKbEntries,
  rollbackKbEntryVersion,
  searchKb,
  updateKbCategory,
  updateKbEntry,
  updateKbEntryStatus
} from '@/api'
import { getUser } from '@/api/http'
import { prefersReducedMotion } from '@/composables/motion'
import { usePagedList } from '@/composables/usePagedList'
import { formatDateTime } from '@/utils/dict'

const roleCode = getUser()?.roleCode || ''
const isUser = roleCode === 'USER'
const canEditEntry = !isUser
const canDeleteEntry = roleCode === 'ADMIN'
const canManageCategory = roleCode === 'ADMIN'

const fetchEntries = isUser ? getUserKbEntries : getKbEntries

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
} = usePagedList(fetchEntries, { keyword: '', categoryId: '', status: '' })

const categoryTree = ref([])
const treeRef = ref(null)
const allTotal = ref(0)
const categoryPanel = ref(false)

/* 窄桌面不再压缩表格：分类侧栏收进抽屉，表格拿回完整宽度 */
const isNarrow = ref(false)
const isCompact = ref(false)
let narrowQuery = null
let compactQuery = null

/* 按表格容器实测宽度取舍列，避免固定操作列压住状态/时间 */
const tableWrap = ref(null)
const tableWidth = ref(0)
let tableObserver = null
const showCategory = computed(() => tableWidth.value >= 888)
const showKeywords = computed(() => tableWidth.value >= 1068)
const showSimilar = computed(() => tableWidth.value >= 1180)
const showUpdate = computed(() => tableWidth.value >= 820)

function bindTableWrap(el) {
  tableWrap.value = el
  if (!tableObserver) return
  tableObserver.disconnect()
  if (el) tableObserver.observe(el)
}

function syncNarrow(event) {
  isNarrow.value = event.matches
  if (!event.matches) categoryPanel.value = false
}

function syncCompact(event) {
  isCompact.value = event.matches
}

/** 关闭抽屉前先移走焦点，避免 aria-hidden 落在仍含焦点的容器上 */
function closeCategoryPanel() {
  if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
  categoryPanel.value = false
}

const hasFilter = computed(
  () => Boolean(filters.keyword) || filters.categoryId !== '' || filters.status !== ''
)

const totalAll = computed(() => allTotal.value || total.value)

const activeCategoryName = computed(() => {
  if (!filters.categoryId) return '全部分类'
  let name = '全部分类'
  const walk = (nodes) => {
    nodes.forEach((node) => {
      if (String(node.id) === String(filters.categoryId)) name = node.name
      if (node.children?.length) walk(node.children)
    })
  }
  walk(categoryTree.value)
  return name
})

const categoryOptions = computed(() => {
  const list = []
  const walk = (nodes, depth) => {
    nodes.forEach((node) => {
      list.push({
        id: node.id,
        name: `${'　'.repeat(depth)}${node.name}`,
        status: node.status
      })
      if (node.children?.length) walk(node.children, depth + 1)
    })
  }
  walk(categoryTree.value, 0)
  return list
})

const maxHits = computed(() =>
  Math.max(1, ...rows.value.map((row) => Number(row.hitCount) || 0))
)

function hitPercent(hit) {
  const value = Number(hit) || 0
  return `${Math.max(value > 0 ? 8 : 0, Math.round((value / maxHits.value) * 100))}%`
}

/* 首屏骨架屏；筛选结果 160ms 淡入；停用与删除的行先淡出再移除 */
const resultFade = ref(false)
const changedId = ref(null)
const removingId = ref(null)
let resultFadeTimer = 0
let changedTimer = 0

function markChanged(id) {
  changedId.value = id
  window.clearTimeout(changedTimer)
  changedTimer = window.setTimeout(() => {
    changedId.value = null
  }, 700)
}

async function removeRowAfterFade(id) {
  removingId.value = id
  await new Promise((resolve) => {
    window.setTimeout(resolve, prefersReducedMotion() ? 0 : 160)
  })
  await load()
  await loadCategories()
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

function splitKeywords(keywords) {
  if (!keywords) return []
  return String(keywords)
    .split(/[,，;；\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function rowClassName({ row }) {
  return row?.id === removingId.value ? 'is-clickable-row row-removing' : 'is-clickable-row'
}

/** 后端 tree=true 已分层；用户端返回扁平列表，这里统一兜底成树 */
function normalizeTree(list) {
  if (!list.length) return []
  const hasChildren = list.some((item) => Array.isArray(item.children) && item.children.length)
  if (hasChildren) return list
  const map = new Map()
  list.forEach((item) => map.set(item.id, { ...item, children: [] }))
  const roots = []
  map.forEach((item) => {
    const parent = item.parentId ? map.get(item.parentId) : null
    if (parent) parent.children.push(item)
    else roots.push(item)
  })
  return roots
}

function countEntries(nodes) {
  return nodes.reduce(
    (sum, node) => sum + (Number(node.entryCount) || 0) + countEntries(node.children || []),
    0
  )
}

async function loadCategories() {
  try {
    const data = isUser ? await getUserKbCategories() : await getKbCategoryTree()
    categoryTree.value = normalizeTree(Array.isArray(data) ? data : [])
    allTotal.value = countEntries(categoryTree.value)
  } catch {
    categoryTree.value = []
    allTotal.value = 0
  }
}

function selectCategory(node) {
  filters.categoryId = node ? node.id : ''
  if (treeRef.value) treeRef.value.setCurrentKey(node ? node.id : null)
  if (isNarrow.value) closeCategoryPanel()
  search()
}

function onCategoryClick(data) {
  selectCategory(data)
}

function reload() {
  load()
  loadCategories()
}

function resetFilters() {
  reset()
}

/* ---------- 条目详情 ---------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const activeEntry = ref(null)
const similarList = ref([])
const similarInput = ref('')
const similarSaving = ref(false)
const expandLoading = ref(false)
const expandCandidates = ref([])
const expandPicked = ref([])
const expandNote = ref('')
const versionList = ref([])
const versionLoading = ref(false)

function resetSimilarTools() {
  similarInput.value = ''
  expandCandidates.value = []
  expandPicked.value = []
  expandNote.value = ''
}

async function openDetail(row) {
  activeEntry.value = row
  detailVisible.value = true
  detailLoading.value = true
  similarList.value = Array.isArray(row?.similarQuestions) ? row.similarQuestions : []
  versionList.value = []
  resetSimilarTools()
  loadVersions(row.id)
  try {
    const data = await getKbEntryDetail(row.id)
    if (data) {
      activeEntry.value = data
      similarList.value = data.similarQuestions || []
    }
  } catch {
    /* 详情失败时回退到列表行数据 */
  } finally {
    detailLoading.value = false
  }
}

async function loadVersions(entryId) {
  versionLoading.value = true
  try {
    const data = await getKbEntryVersions(entryId)
    versionList.value = Array.isArray(data) ? data : []
  } catch {
    versionList.value = []
  } finally {
    versionLoading.value = false
  }
}

async function refreshSimilar() {
  if (!activeEntry.value?.id) return
  try {
    const data = await getKbEntryDetail(activeEntry.value.id)
    if (data) {
      activeEntry.value = data
      similarList.value = data.similarQuestions || []
    }
  } catch {
    /* 刷新失败保留当前列表 */
  }
}

async function addSimilar() {
  const question = similarInput.value.trim()
  if (!question) return
  if (similarList.value.length >= 20) {
    ElMessage.warning('单个词条最多 20 条相似问法')
    return
  }
  similarSaving.value = true
  try {
    await createKbSimilarQuestions(activeEntry.value.id, {
      questions: [question],
      source: 'MANUAL'
    })
    similarInput.value = ''
    ElMessage.success('相似问法已添加，立即参与匹配')
    await refreshSimilar()
    load()
  } catch (err) {
    ElMessage.error(err?.message || '添加失败，请稍后重试')
  } finally {
    similarSaving.value = false
  }
}

async function removeSimilar(item) {
  try {
    await ElMessageBox.confirm(`删除相似问法「${item.question}」？`, '删除相似问法', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteKbSimilarQuestion(item.id)
    ElMessage.success('相似问法已删除')
    await refreshSimilar()
    load()
  } catch (err) {
    ElMessage.error(err?.message || '删除失败，请稍后重试')
  }
}

async function aiExpand() {
  expandLoading.value = true
  expandNote.value = ''
  expandCandidates.value = []
  expandPicked.value = []
  try {
    const data = await expandKbSimilarQuestions(activeEntry.value.id)
    expandCandidates.value = data?.candidates || []
    expandPicked.value = [...expandCandidates.value]
    expandNote.value = data?.note || (expandCandidates.value.length ? '' : '未返回可用问法，请稍后重试')
  } catch (err) {
    expandNote.value = err?.message || 'AI 扩写暂不可用'
  } finally {
    expandLoading.value = false
  }
}

async function adoptCandidates() {
  const picked = expandPicked.value.filter(Boolean)
  if (!picked.length) {
    ElMessage.warning('请先勾选要入库的问法')
    return
  }
  similarSaving.value = true
  try {
    await createKbSimilarQuestions(activeEntry.value.id, { questions: picked, source: 'AI' })
    ElMessage.success(`已入库 ${picked.length} 条相似问法`)
    expandCandidates.value = []
    expandPicked.value = []
    await refreshSimilar()
    load()
  } catch (err) {
    ElMessage.error(err?.message || '入库失败，请稍后重试')
  } finally {
    similarSaving.value = false
  }
}

async function rollbackVersion(version) {
  try {
    await ElMessageBox.confirm(
      `回滚到版本 V${version.versionNo}？当前内容会被覆盖，并新记录一条「回滚」版本。`,
      '回滚知识词条',
      { type: 'warning', confirmButtonText: '确认回滚', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await rollbackKbEntryVersion(version.id)
    ElMessage.success('已回滚到该版本')
    await refreshSimilar()
    loadVersions(activeEntry.value.id)
    load()
  } catch (err) {
    ElMessage.error(err?.message || '回滚失败，请稍后重试')
  }
}

function similarSourceLabel(source) {
  if (source === 'AI') return 'AI 扩写'
  if (source === 'IMPORT') return '知识包'
  return '手工'
}

/* ---------- 条目编辑 ---------- */
const entryVisible = ref(false)
const entryMode = ref('create')
const entrySubmitting = ref(false)
const entryFormRef = ref(null)
const entryForm = reactive({
  id: null,
  categoryId: null,
  question: '',
  answer: '',
  keywords: '',
  tags: '',
  similarText: '',
  status: 1,
  updateByName: '',
  updateTime: ''
})

const entryRules = {
  categoryId: [{ required: true, message: '请选择所属分类', trigger: 'change' }],
  question: [
    { required: true, message: '请填写标准问题', trigger: 'blur' },
    { min: 2, max: 255, message: '问题长度须为 2-255 个字符', trigger: 'blur' }
  ],
  answer: [
    { required: true, message: '请填写标准答案', trigger: 'blur' },
    { min: 2, max: 2000, message: '答案长度须为 2-2000 个字符', trigger: 'blur' }
  ],
  keywords: [
    { required: true, message: '请至少填写一个关键词', trigger: 'blur' },
    { max: 255, message: '关键词总长度不能超过 255', trigger: 'blur' }
  ]
}

function resetEntryForm() {
  entryFormRef.value?.resetFields()
  Object.assign(entryForm, {
    id: null,
    categoryId: null,
    question: '',
    answer: '',
    keywords: '',
    tags: '',
    similarText: '',
    status: 1,
    updateByName: '',
    updateTime: ''
  })
}

async function openEntry(row) {
  if (!canEditEntry) return
  if (row?.id) {
    entryMode.value = 'edit'
    let detail = row
    if (!Array.isArray(row.similarQuestions)) {
      try {
        detail = (await getKbEntryDetail(row.id)) || row
      } catch {
        detail = row
      }
    }
    Object.assign(entryForm, {
      id: row.id,
      categoryId: detail.categoryId ?? null,
      question: detail.question || '',
      answer: detail.answer || '',
      keywords: detail.keywords || '',
      tags: detail.tags || '',
      similarText: (detail.similarQuestions || []).map((item) => item.question).join('\n'),
      status: Number(detail.status) === 0 ? 0 : 1,
      updateByName: detail.updateByName || '',
      updateTime: detail.updateTime || ''
    })
  } else {
    entryMode.value = 'create'
    Object.assign(entryForm, {
      id: null,
      categoryId: filters.categoryId || categoryOptions.value[0]?.id || null,
      question: typeof row?.question === 'string' ? row.question : '',
      answer: '',
      keywords: '',
      tags: '',
      similarText: '',
      status: 1,
      updateByName: '',
      updateTime: ''
    })
  }
  entryVisible.value = true
}

async function submitEntry() {
  try {
    await entryFormRef.value.validate()
  } catch {
    return
  }
  entrySubmitting.value = true
  try {
    const similarQuestions = entryForm.similarText
      .split(/\r?\n/)
      .map((item) => item.trim())
      .filter(Boolean)
    const payload = {
      categoryId: entryForm.categoryId,
      question: entryForm.question.trim(),
      answer: entryForm.answer.trim(),
      keywords: entryForm.keywords.trim(),
      tags: entryForm.tags.trim(),
      status: entryForm.status
    }
    if (similarQuestions.length) payload.similarQuestions = similarQuestions
    if (entryMode.value === 'create') {
      await createKbEntry(payload)
      ElMessage.success('词条已创建，AI 接待会立即生效')
    } else {
      payload.similarQuestions = similarQuestions
      await updateKbEntry(entryForm.id, payload)
      ElMessage.success('词条已更新')
    }
    entryVisible.value = false
    reload()
  } catch (err) {
    ElMessage.error(err?.message || '保存失败，请稍后重试')
  } finally {
    entrySubmitting.value = false
  }
}

async function onRowCommand(command, row) {
  if (command === 'toggle') {
    await toggleEntry(row)
    return
  }
  if (command === 'delete') {
    await removeEntry(row)
  }
}

async function toggleEntry(row) {
  const next = Number(row.status) === 1 ? 0 : 1
  try {
    await updateKbEntryStatus(row.id, next)
    ElMessage.success(next === 1 ? '词条已启用' : '词条已停用')
    await load()
    loadCategories()
    markChanged(row.id)
  } catch (err) {
    ElMessage.error(err?.message || '操作失败，请稍后重试')
  }
}

async function removeEntry(row) {
  try {
    await ElMessageBox.confirm(
      `删除后「${row.question}」将不再参与 AI 匹配，且不可恢复。确认删除？`,
      '删除知识条目',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteKbEntry(row.id)
    ElMessage.success('词条已删除')
    if (rows.value.length === 1 && pageNum.value > 1) pageNum.value -= 1
    await removeRowAfterFade(row.id)
  } catch (err) {
    ElMessage.error(err?.message || '删除失败，请稍后重试')
  }
}

/* ---------- 分类编辑 ---------- */
const categoryVisible = ref(false)
const categoryMode = ref('create')
const categorySubmitting = ref(false)
const categoryFormRef = ref(null)
const categoryForm = reactive({
  id: null,
  name: '',
  parentId: null,
  sort: 0,
  status: 1,
  description: ''
})

const categoryRules = {
  name: [
    { required: true, message: '请填写分类名称', trigger: 'blur' },
    { min: 2, max: 50, message: '分类名称长度须为 2-50 个字符', trigger: 'blur' }
  ]
}

function resetCategoryForm() {
  categoryFormRef.value?.resetFields()
  Object.assign(categoryForm, {
    id: null,
    name: '',
    parentId: null,
    sort: 0,
    status: 1,
    description: ''
  })
}

function openCategory(row, parentId = null) {
  if (!canManageCategory) return
  if (row?.id) {
    categoryMode.value = 'edit'
    Object.assign(categoryForm, {
      id: row.id,
      name: row.name || '',
      parentId: row.parentId ?? null,
      sort: Number(row.sort) || 0,
      status: Number(row.status) === 0 ? 0 : 1,
      description: row.description || ''
    })
  } else {
    categoryMode.value = 'create'
    Object.assign(categoryForm, {
      id: null,
      name: '',
      parentId: parentId ?? null,
      sort: 0,
      status: 1,
      description: ''
    })
  }
  categoryVisible.value = true
}

async function submitCategory() {
  try {
    await categoryFormRef.value.validate()
  } catch {
    return
  }
  categorySubmitting.value = true
  try {
    const payload = {
      name: categoryForm.name.trim(),
      parentId: categoryForm.parentId || null,
      sort: Number(categoryForm.sort) || 0,
      status: categoryForm.status,
      description: categoryForm.description.trim() || null
    }
    if (categoryMode.value === 'create') {
      await createKbCategory(payload)
      ElMessage.success('分类已创建')
    } else {
      await updateKbCategory(categoryForm.id, payload)
      ElMessage.success('分类已更新')
    }
    categoryVisible.value = false
    reload()
  } catch (err) {
    ElMessage.error(err?.message || '保存失败，请稍后重试')
  } finally {
    categorySubmitting.value = false
  }
}

async function removeCategory(row) {
  try {
    await ElMessageBox.confirm(
      `删除「${row.name}」后，其下词条将变为未分类且不再参与匹配。确认删除？`,
      '删除知识分类',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteKbCategory(row.id)
    ElMessage.success('分类已删除')
    if (Number(filters.categoryId) === Number(row.id)) selectCategory(null)
    reload()
  } catch (err) {
    ElMessage.error(err?.message || '删除失败：请先移除该分类下的子分类与词条')
  }
}

/* ---------- 命中测试 ---------- */
const matchVisible = ref(false)
const matchLoading = ref(false)
const matchQuery = ref('')
const matchCategoryId = ref(null)
const matchResult = ref(null)

function openMatchTest(presetQuery = '') {
  matchQuery.value = typeof presetQuery === 'string' ? presetQuery : ''
  matchCategoryId.value = null
  matchResult.value = null
  matchVisible.value = true
}

function resetMatch() {
  matchQuery.value = ''
  matchCategoryId.value = null
  matchResult.value = null
}

function scoreText(score) {
  const value = Number(score)
  if (Number.isNaN(value)) return '—'
  return `${(value * 100).toFixed(1)}%`
}

async function runMatch() {
  const query = matchQuery.value.trim()
  if (!query) return
  matchLoading.value = true
  try {
    const data = await searchKb({
      keyword: query,
      limit: 5,
      ...(matchCategoryId.value ? { categoryId: matchCategoryId.value } : {})
    })
    matchResult.value = data || { query, candidates: [] }
  } catch (err) {
    matchResult.value = { query, candidates: [] }
    ElMessage.error(err?.message || '命中测试失败，请稍后重试')
  } finally {
    matchLoading.value = false
  }
}

function createFromMatch() {
  const question = matchQuery.value.trim()
  matchVisible.value = false
  openEntry({ question })
}

onMounted(() => {
  narrowQuery = window.matchMedia('(max-width: 1264px)')
  isNarrow.value = narrowQuery.matches
  narrowQuery.addEventListener('change', syncNarrow)
  compactQuery = window.matchMedia('(max-width: 760px)')
  isCompact.value = compactQuery.matches
  compactQuery.addEventListener('change', syncCompact)
  if (typeof ResizeObserver !== 'undefined') {
    tableObserver = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect?.width || 0
      if (width) tableWidth.value = Math.round(width)
    })
    if (tableWrap.value) tableObserver.observe(tableWrap.value)
  }
  reload()
})

onBeforeUnmount(() => {
  window.clearTimeout(resultFadeTimer)
  window.clearTimeout(changedTimer)
  if (narrowQuery) narrowQuery.removeEventListener('change', syncNarrow)
  if (compactQuery) compactQuery.removeEventListener('change', syncCompact)
  if (tableObserver) tableObserver.disconnect()
})
</script>

<style scoped>
.kb-layout {
  display: grid;
  grid-template-columns: 258px minmax(0, 1fr);
  gap: 16px;
  /* 左右两栏等高：左侧分类卡底部与词条卡底部齐平，内部允许留白 */
  align-items: stretch;
}

/* ---------- 分类侧栏 ---------- */
.kb-side {
  padding: 14px 12px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 100%;
}

.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 6px;
}

.side-head-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.toolbar-cat {
  max-width: 168px;
}

.toolbar-cat :deep(span) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-title {
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.4px;
  color: var(--zk-text-2);
}

.side-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: var(--zk-radius-sm);
  background: transparent;
  font-size: 13px;
  color: var(--zk-text);
  cursor: pointer;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

.side-total:hover {
  background: var(--zk-surface-2);
}

.side-total.active {
  background: var(--zk-indigo-soft, rgba(99, 102, 241, 0.1));
  border-color: var(--zk-indigo, #6366f1);
  color: var(--zk-indigo, #6366f1);
  font-weight: 600;
}

.side-total-name {
  font-size: 13px;
}

.kb-side :deep(.el-tree) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background: transparent;
  --el-tree-node-hover-bg-color: var(--zk-surface-2);
  font-size: 13px;
}

.kb-side :deep(.el-tree-node__content) {
  height: 34px;
  border-radius: var(--zk-radius-sm);
  padding-right: 4px;
}

/* 分类树：展开箭头 200ms 旋转；子项 30ms 依次浮现，展开动作有连续感 */
.kb-side :deep(.el-tree-node__expand-icon) {
  transition: transform var(--zk-dur-base) var(--zk-ease-out);
}

.kb-side :deep(.el-tree-node__children > .el-tree-node) {
  animation: zk-rise-sm 240ms var(--zk-ease-soft) both;
}

.kb-side :deep(.el-tree-node__children > .el-tree-node:nth-child(1)) {
  animation-delay: 0ms;
}
.kb-side :deep(.el-tree-node__children > .el-tree-node:nth-child(2)) {
  animation-delay: 30ms;
}
.kb-side :deep(.el-tree-node__children > .el-tree-node:nth-child(3)) {
  animation-delay: 60ms;
}
.kb-side :deep(.el-tree-node__children > .el-tree-node:nth-child(4)) {
  animation-delay: 90ms;
}
.kb-side :deep(.el-tree-node__children > .el-tree-node:nth-child(5)) {
  animation-delay: 120ms;
}
.kb-side :deep(.el-tree-node__children > .el-tree-node:nth-child(6)) {
  animation-delay: 150ms;
}
.kb-side :deep(.el-tree-node__children > .el-tree-node:nth-child(7)) {
  animation-delay: 180ms;
}
.kb-side :deep(.el-tree-node__children > .el-tree-node:nth-child(8)) {
  animation-delay: 210ms;
}
.kb-side :deep(.el-tree-node__children > .el-tree-node:nth-child(n + 9)) {
  animation-delay: 240ms;
}

.kb-side :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--zk-indigo-soft, rgba(99, 102, 241, 0.1));
  color: var(--zk-indigo, #6366f1);
  font-weight: 600;
}

.cat-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
}

.cat-node-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cat-off {
  flex: 0 0 auto;
  font-size: 10px;
  color: var(--zk-text-3);
  border: 1px solid var(--zk-border);
  border-radius: 4px;
  padding: 0 4px;
}

.cat-node-count {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--zk-text-3);
}

.cat-node-ops {
  flex: 0 0 auto;
  display: none;
  align-items: center;
  gap: 0;
}

.cat-node-ops :deep(.el-button) {
  padding: 2px;
  min-height: 24px;
  font-size: 13px;
}

.kb-side :deep(.el-tree-node__content:hover) .cat-node-ops {
  display: inline-flex;
}

.side-foot {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  /* 贴底：分类较少时说明文案与右侧卡片底部对齐 */
  margin-top: auto;
  padding: 8px 6px 2px;
  border-top: 1px dashed var(--zk-border);
  font-size: 11px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

/* ---------- 主区 ---------- */
.kb-main {
  min-width: 0;
}

.toolbar-search {
  width: 260px;
}

.cell-cat-mini {
  flex: 0 0 auto;
  padding: 1px 7px;
  border-radius: 5px;
  background: var(--zk-surface-2);
  font-size: 11px;
  line-height: 18px;
  color: var(--zk-text-3);
  white-space: nowrap;
}

/* 横向滚动交给 el-table 自身，固定列才能贴在可视区右边缘 */
.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  border-radius: 0 0 var(--zk-radius-lg) var(--zk-radius-lg);
  outline-offset: -2px;
}

.table-scroll:focus-visible {
  outline: 2px solid var(--zk-teal-dark);
  outline-offset: -2px;
}

/* ---------- 手机端卡片列表 ---------- */
.kb-list {
  min-height: 180px;
  border-radius: 0 0 var(--zk-radius-lg) var(--zk-radius-lg);
  overflow: hidden;
}

.kb-item {
  padding: 13px 16px;
  border-bottom: 1px solid var(--zk-border);
  cursor: pointer;
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

.kb-item:last-of-type {
  border-bottom: 0;
}

.kb-item:active {
  background: var(--zk-surface-2);
}

.kb-item-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.kb-item-top :deep(.pill) {
  flex: 0 0 auto;
}

.kb-item-q {
  margin: 0;
  font-size: 13.5px;
  font-weight: 550;
  line-height: 1.5;
  color: var(--zk-text);
}

.kb-item-a {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--zk-text-2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.kb-item-meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--zk-text-3);
}

.kb-item-ops {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.kb-item-ops :deep(.el-button) {
  height: 24px;
  padding: 2px 4px;
}

.table-scroll :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: var(--el-table-row-hover-bg-color, #eef1ff);
  transition: background-color var(--zk-dur-fast) var(--zk-ease-out);
}

.table-skeleton {
  padding: 12px 16px 6px;
}

/* 筛选结果淡入：160ms，只动位移与透明度 */
.table-result-fade {
  animation: zk-rise-sm 160ms var(--zk-ease-soft) both;
}

/* 删除/停用后行先淡出 160ms 再移除，列表不突然跳位 */
.table-scroll :deep(.el-table__row.row-removing > td.el-table__cell) {
  opacity: 0;
  transition: opacity 160ms var(--zk-ease-in);
}

.cat-name {
  font-size: 12.5px;
  font-weight: 550;
  color: var(--zk-text-2);
}

.cell-question-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.cell-question {
  display: block;
  flex: 1 1 auto;
  min-width: 0;
  font-size: 13px;
  font-weight: 550;
  line-height: 1.55;
  color: var(--zk-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-answer {
  margin-top: 2px;
  font-size: 12px;
  color: var(--zk-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hit-cell {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.hit-num {
  font-size: 12.5px;
  color: var(--zk-text);
}

.hit-bar {
  flex: 0 0 42px;
  height: 4px;
  border-radius: var(--zk-radius-xs);
  background: #ececef;
  overflow: hidden;
}

.hit-bar i {
  display: block;
  height: 100%;
  border-radius: var(--zk-radius-xs);
  background: var(--zk-teal-dark);
  transition: width var(--zk-dur-slow) var(--zk-ease-out);
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

/* ---------- 详情 ---------- */
.detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-label {
  font-size: 11px;
  color: var(--zk-text-3);
  letter-spacing: 0.4px;
  margin-bottom: 6px;
}

.detail-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-block {
  min-width: 0;
}

.detail-question {
  font-size: 15px;
  font-weight: 600;
  color: var(--zk-text);
  line-height: 1.6;
}

.detail-answer {
  font-size: 13px;
  line-height: 1.9;
  color: var(--zk-text-2);
  background: var(--zk-surface-2);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  padding: 12px 14px;
  white-space: pre-wrap;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  padding-top: 16px;
  border-top: 1px dashed var(--zk-border);
}

.detail-num {
  font-size: 20px;
  font-weight: 700;
  color: var(--zk-teal-dark);
}

.detail-time {
  font-size: 12.5px;
  color: var(--zk-text-2);
  font-variant-numeric: tabular-nums;
}

.detail-footer {
  display: flex;
  gap: 8px;
  padding-top: 14px;
  border-top: 1px dashed var(--zk-border);
}

/* ---------- 表单 ---------- */
.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.form-row :deep(.el-input-number) {
  width: 100%;
}

.full-width {
  width: 100%;
}

.form-hint {
  margin-top: 4px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--zk-text-3);
}

/* ---------- 命中测试 ---------- */
.match-desc {
  margin: 0 0 12px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--zk-text-2);
}

.match-form {
  display: flex;
  gap: 8px;
}

.match-cat {
  flex: 0 0 132px;
}

.match-result {
  margin-top: 14px;
}

.match-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.match-item {
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  padding: 10px 12px;
  background: var(--zk-surface-2);
}

.match-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.match-question {
  font-size: 13px;
  font-weight: 600;
  color: var(--zk-text);
}

.match-meta {
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.match-answer {
  margin-top: 8px;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--zk-text-2);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.match-empty {
  text-align: center;
  padding: 18px 12px;
  border: 1px dashed var(--zk-border);
  border-radius: var(--zk-radius);
  color: var(--zk-text-2);
  font-size: 12.5px;
}

.match-empty p {
  margin: 0 0 10px;
}

.detail-loading {
  padding: 4px;
}

/* ---------- 标签 / 相似问法 / 版本记录 ---------- */
.kw-tag.is-tag {
  color: var(--zk-accent-active);
  background: var(--zk-accent-soft);
  border-color: var(--zk-accent-line);
}

.similar-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius);
  background: var(--zk-surface-2);
}

.similar-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 0;
  border-bottom: 1px dashed var(--zk-border);
}

.similar-item:last-child {
  border-bottom: 0;
}

.similar-q {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--zk-text);
  word-break: break-word;
}

.similar-source {
  flex: 0 0 auto;
  padding: 0 6px;
  font-size: 10.5px;
  line-height: 16px;
  white-space: nowrap;
  color: var(--zk-text-3);
  background: var(--zk-surface);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-xs);
}

.similar-add {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.similar-add :deep(.el-input) {
  flex: 1 1 auto;
  min-width: 0;
}

.similar-add :deep(.el-button) {
  flex: 0 0 auto;
  margin-left: 0;
}

.expand-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 10px;
  border: 1px dashed var(--zk-accent-line);
  border-radius: var(--zk-radius);
  background: var(--zk-accent-soft);
}

.expand-list :deep(.el-checkbox-group) {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.expand-list :deep(.el-checkbox) {
  height: 24px;
  margin-right: 0;
}

.expand-list :deep(.el-checkbox__label) {
  font-size: 12.5px;
  line-height: 1.5;
  white-space: normal;
  color: var(--zk-text);
}

.expand-list :deep(.el-button) {
  align-self: flex-start;
}

.version-list {
  display: flex;
  flex-direction: column;
}

.version-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 8px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--zk-border);
}

.version-item:last-child {
  border-bottom: 0;
}

.version-no {
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 650;
  color: var(--zk-accent-active);
}

.version-type {
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--zk-text);
}

.version-op,
.version-time {
  font-size: 11.5px;
  color: var(--zk-text-3);
}

.version-time {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

.version-item :deep(.el-button) {
  flex: 0 0 auto;
  height: 22px;
  padding: 0 4px;
  margin-left: 0;
}

@media (max-width: 1264px) {
  .kb-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  /* 窄桌面：分类收进抽屉，表格拿回完整宽度，避免右侧列被裁切 */
  .kb-side {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    width: 288px;
    max-width: 84vw;
    min-height: 0;
    z-index: 60;
    padding-top: 16px;
    border-radius: 0 var(--zk-radius-lg) var(--zk-radius-lg) 0;
    border-top: 0;
    border-bottom: 0;
    border-left: 0;
    overflow-y: auto;
    box-shadow: 0 24px 60px rgba(24, 24, 27, 0.18);
    transform: translateX(-102%);
    transition: transform 220ms var(--zk-ease-out);
  }

  .kb-side.is-open {
    transform: none;
  }

  .kb-side-mask {
    position: fixed;
    inset: 0;
    z-index: 55;
    background: rgba(24, 24, 27, 0.32);
    opacity: 0;
    pointer-events: none;
    transition: opacity var(--zk-dur-base) var(--zk-ease-out);
  }

  .kb-side-mask.is-open {
    opacity: 1;
    pointer-events: auto;
  }

  .kb-side :deep(.el-tree-node__content) {
    height: 36px;
  }

  .cat-node-ops {
    display: inline-flex;
  }
}

@media (max-width: 640px) {
  .toolbar-search {
    width: 100%;
  }

  .toolbar-cat {
    flex: 1 1 140px;
    min-width: 0;
    max-width: none;
  }

  .zk-toolbar :deep(.el-select) {
    flex: 1 1 140px;
    width: auto;
    min-width: 0;
  }

  .zk-toolbar .grow {
    display: none;
  }

  .form-row {
    grid-template-columns: 1fr;
  }

  .match-form {
    flex-wrap: wrap;
  }

  .match-cat {
    flex: 1;
  }

  .detail-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .similar-add {
    flex-wrap: wrap;
  }

  .similar-add :deep(.el-input) {
    flex: 1 1 100%;
  }

  .version-time {
    margin-left: 0;
    font-size: 11px;
  }
}
</style>
