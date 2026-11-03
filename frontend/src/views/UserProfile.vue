<template>
  <div class="zk-page">
    <PageHeader
      title="个人资料"
      description="当前账号信息只读展示。密码修改与资料自助编辑将在后续里程碑提供。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div class="zk-card profile-card" :aria-busy="loading ? 'true' : 'false'">
      <div v-if="error" class="profile-error">
        <ZkEmpty size="sm" :title="error" hint="请确认登录状态后重试">
          <el-button @click="load">重新加载</el-button>
        </ZkEmpty>
      </div>

      <ZkSkeleton v-else-if="!loaded" variant="lines" :rows="7" class="profile-skeleton" />

      <template v-else>
        <div v-loading="loading" class="profile-body zk-drawer-stagger">
          <div class="profile-head">
            <div class="profile-avatar" aria-hidden="true">{{ avatarText }}</div>
            <div class="profile-identity">
              <h2>{{ profile?.nickname || profile?.username || '—' }}</h2>
              <StatusPill :label="roleLabel" :tone="roleTone" />
            </div>
            <span class="readonly-badge">只读</span>
          </div>

          <dl class="profile-grid">
            <div v-for="item in profileItems" :key="item.label" class="profile-item">
              <dt>{{ item.label }}</dt>
              <dd :class="{ 'is-mono': item.mono }">{{ item.value }}</dd>
            </div>
          </dl>

          <p class="profile-note">
            账号、角色与联系方式由管理员维护；如需修改请联系系统管理员。密码修改功能将在后续里程碑开放。
          </p>
        </div>
      </template>
    </div>

    <div class="page-tip">
      <i aria-hidden="true" />
      账号资料由管理员统一维护，用户端仅展示；如需修改请联系管理员
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'
import ZkSkeleton from '@/components/ZkSkeleton.vue'
import ZkEmpty from '@/components/ZkEmpty.vue'
import { getUserProfile } from '@/api'
import { ROLE_DICT, dictLabel, dictTone, formatDateTime } from '@/utils/dict'

const profile = ref(null)
const loading = ref(false)
const loaded = ref(false)
const error = ref('')

const avatarText = computed(() =>
  (profile.value?.nickname || profile.value?.username || '用').slice(0, 1).toUpperCase()
)
const roleLabel = computed(() =>
  dictLabel(ROLE_DICT, profile.value?.roleCode, '普通用户')
)
const roleTone = computed(() => dictTone(ROLE_DICT, profile.value?.roleCode, 'slate'))

const profileItems = computed(() => {
  const data = profile.value || {}
  return [
    { label: '账号', value: data.username || '—', mono: true },
    { label: '昵称', value: data.nickname || '—' },
    { label: '角色', value: dictLabel(ROLE_DICT, data.roleCode, '—') },
    { label: '手机号', value: data.phone || '—', mono: true },
    { label: '邮箱', value: data.email || '—', mono: true },
    { label: '最后登录', value: formatDateTime(data.lastLoginTime) }
  ]
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getUserProfile()
    profile.value = data || null
    if (!data) error.value = '未获取到账号资料'
  } catch (err) {
    profile.value = null
    error.value =
      err?.response?.status === 403
        ? '当前账号无权查看个人资料'
        : err?.response?.status === 404
          ? '个人资料加载失败，请稍后重试'
          : err?.message || '个人资料加载失败'
  } finally {
    loading.value = false
    loaded.value = true
  }
}

onMounted(load)
</script>

<style scoped>
/* 首屏骨架：按真实资料页的行距排布，加载前后不跳 */
.profile-skeleton {
  min-height: 200px;
}

.profile-error {
  min-height: 200px;
}

.profile-card {
  padding: 24px;
  min-height: 260px;
}

.profile-body {
  display: flex;
  flex-direction: column;
  gap: 22px;
  min-height: 200px;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px dashed var(--zk-border);
}

.profile-avatar {
  width: 48px;
  height: 48px;
  flex: 0 0 48px;
  border-radius: var(--zk-radius);
  display: grid;
  place-items: center;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
  background: var(--zk-accent);
}

.profile-identity {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.profile-identity h2 {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.2px;
  color: var(--zk-text);
}

.readonly-badge {
  margin-left: auto;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3px;
  color: var(--zk-text-3);
  background: var(--zk-surface-2);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-sm);
  padding: 3px 10px;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px 20px;
  margin: 0;
}

.profile-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.profile-item dt {
  font-size: 11.5px;
  color: var(--zk-text-3);
  letter-spacing: 0.3px;
}

.profile-item dd {
  margin: 0;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--zk-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-item dd.is-mono {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--zk-text-2);
}

.profile-note {
  font-size: 12px;
  line-height: 1.8;
  color: var(--zk-text-3);
  padding: 10px 14px;
  border-radius: var(--zk-radius-lg);
  background: var(--zk-surface-2);
  border: 1px dashed var(--zk-border-strong);
}

@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .profile-card {
    padding: 18px 16px;
  }

  .profile-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 14px;
  }

  .readonly-badge {
    display: none;
  }
}
</style>
