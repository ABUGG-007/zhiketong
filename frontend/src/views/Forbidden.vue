<template>
  <div class="forbidden-page">
    <!-- 图标、标题、说明、按钮以 45ms 依次浮现 -->
    <div class="forbidden-card zk-card" v-reveal.group>
      <div class="code" aria-hidden="true">403</div>
      <h1>无权访问</h1>
      <p class="desc">
        当前登录角色为
        <StatusPill :label="roleLabel" :tone="roleTone" />
        ，该页面不在角色可访问范围内。
      </p>
      <p v-if="fromPath" class="from">
        请求路径：<code>{{ fromPath }}</code>
      </p>

      <div class="actions">
        <el-button type="primary" @click="goHome">回到我的首页</el-button>
        <el-button @click="logout">退出登录</el-button>
      </div>

      <p class="hint">如果你认为这是权限配置问题，请联系系统管理员。</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import StatusPill from '@/components/StatusPill.vue'
import { clearAuth, getUser } from '@/api/http'
import { roleHomePath } from '@/router'
import { ROLE_DICT, dictLabel, dictTone } from '@/utils/dict'

const route = useRoute()
const router = useRouter()

const roleCode = getUser()?.roleCode
const roleLabel = computed(() => dictLabel(ROLE_DICT, roleCode, '未知角色'))
const roleTone = computed(() => dictTone(ROLE_DICT, roleCode, 'slate'))
const fromPath = computed(() =>
  typeof route.query.from === 'string' ? route.query.from : ''
)

function goHome() {
  const home = roleHomePath(roleCode)
  if (home === '/login') clearAuth()
  router.replace(home)
}

function logout() {
  clearAuth()
  ElMessage.success('已退出登录')
  router.replace({ name: 'login' })
}
</script>

<style scoped>
.forbidden-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 20px;
  background: var(--zk-bg);
}

.forbidden-card {
  width: 100%;
  max-width: 520px;
  padding: 40px 36px 32px;
  text-align: center;
}

.code {
  font-size: 56px;
  line-height: 1;
  font-weight: 600;
  letter-spacing: -2px;
  color: var(--zk-accent);
  margin-bottom: 16px;
}

h1 {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.2px;
  color: var(--zk-text);
  margin-bottom: 10px;
}

.desc {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 13px;
  line-height: 1.8;
  color: var(--zk-text-2);
}

.from {
  margin-top: 10px;
  font-size: 12px;
  color: var(--zk-text-3);
  word-break: break-all;
}

.from code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11.5px;
  padding: 2px 6px;
  border-radius: 6px;
  background: var(--zk-surface-2);
  border: 1px solid var(--zk-border);
  color: var(--zk-text-2);
}

.actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 26px;
}

.actions .el-button {
  height: 38px;
  padding: 0 18px;
  font-weight: 500;
  border-radius: 6px;
}

.hint {
  margin-top: 20px;
  font-size: 11.5px;
  color: var(--zk-text-3);
  line-height: 1.7;
}

@media (max-width: 480px) {
  .forbidden-card {
    padding: 32px 20px 26px;
  }

  .code {
    font-size: 52px;
  }

  .actions .el-button {
    flex: 1 1 auto;
  }
}
</style>
