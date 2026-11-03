import http from './http'

/**
 * 统一接口层。
 * 约定：http 拦截器已把 {code,message,data} 解包为 data；
 * silent: true 表示由页面自行展示空态/错误态，不弹全局 Toast。
 */

/* ---------- 认证 ---------- */
export const login = (payload) => http.post('/auth/login', payload)
export const logout = () => http.post('/auth/logout', null, { silent: true })
export const getMe = () => http.get('/auth/me', { silent: true })
export const changePassword = (payload) => http.put('/auth/password', payload)

/* ---------- 健康检查（公开） ---------- */
export const getHealth = () => http.get('/health', { silent: true })

/* ---------- 管理端：用户管理（仅 ADMIN） ---------- */
export const getUsers = (params) => http.get('/admin/users', { params, silent: true })
export const createUser = (payload) => http.post('/admin/users', payload)
export const updateUser = (id, payload) => http.put(`/admin/users/${id}`, payload)
export const deleteUser = (id) => http.delete(`/admin/users/${id}`)
export const updateUserStatus = (id, status) =>
  http.put(`/admin/users/${id}/status`, { status })
export const resetUserPassword = (id, newPassword) =>
  http.put(`/admin/users/${id}/password`, { newPassword })

/* ---------- 管理端：角色与登录日志（仅 ADMIN） ---------- */
export const getRoles = (params) => http.get('/admin/roles', { params, silent: true })
export const getLoginLogs = (params) =>
  http.get('/admin/login-logs', { params, silent: true })

/* ---------- 坐席状态（仅 AGENT） ---------- */
export const agentOnline = (payload = {}) => http.post('/agent/online', payload)
export const agentOffline = () => http.post('/agent/offline')
export const agentSetStatus = (onlineStatus) =>
  http.put('/agent/status', { onlineStatus })
export const getAgentStatus = () => http.get('/agent/status', { silent: true })

/* ---------- 坐席端自助数据 ---------- */
export const getAgentSessions = (params) =>
  http.get('/agent/sessions', { params, silent: true })
export const getAgentOrders = (params) =>
  http.get('/agent/orders', { params, silent: true })
export const acceptAgentSession = (sessionId) =>
  http.post(`/agent/sessions/${sessionId}/accept`)
export const closeAgentSession = (sessionId, payload = {}) =>
  http.post(`/agent/sessions/${sessionId}/close`, payload)
export const transferAgentSession = (sessionId, reason) =>
  http.post(`/agent/sessions/${sessionId}/transfer`, { reason })
export const getWorkbenchStats = () =>
  http.get('/agent/workbench/stats', { silent: true })

/* ---------- 用户端自助数据（仅 USER 本人） ---------- */
export const getUserSessions = (params) =>
  http.get('/user/sessions', { params, silent: true })
export const getUserOrders = (params) =>
  http.get('/user/orders', { params, silent: true })
export const getUserProfile = () => http.get('/user/profile', { silent: true })

/* ---------- 知识库管理（ADMIN / AGENT） ---------- */
export const getKbCategories = (params) =>
  http.get('/kb/categories', { params: { tree: false, ...params }, silent: true })
export const getKbCategoryTree = () =>
  http.get('/kb/categories', { params: { tree: true }, silent: true })
export const getKbEntries = (params) => http.get('/kb/entries', { params, silent: true })
export const getKbEntryDetail = (id) => http.get(`/kb/entries/${id}`)
export const createKbEntry = (payload) => http.post('/kb/entries', payload)
export const updateKbEntry = (id, payload) => http.put(`/kb/entries/${id}`, payload)
export const deleteKbEntry = (id) => http.delete(`/kb/entries/${id}`)
export const updateKbEntryStatus = (id, status) =>
  http.put(`/kb/entries/${id}/status`, { status })
export const searchKb = (params) => http.get('/kb/entries/search', { params, silent: true })
export const getKbRanking = (params) =>
  http.get('/kb/entries/ranking', { params, silent: true })
export const createKbCategory = (payload) => http.post('/kb/categories', payload)
export const updateKbCategory = (id, payload) => http.put(`/kb/categories/${id}`, payload)
export const deleteKbCategory = (id) => http.delete(`/kb/categories/${id}`)
export const updateKbCategoryStatus = (id, status) =>
  http.put(`/kb/categories/${id}/status`, { status })

/* ---------- 知识库运营：相似问法 / 版本 / 未命中 / 诊断 / 知识包（S1） ---------- */
export const getKbSimilarQuestions = (entryId) =>
  http.get(`/kb/entries/${entryId}/similar-questions`, { silent: true })
export const createKbSimilarQuestions = (entryId, payload) =>
  http.post(`/kb/entries/${entryId}/similar-questions`, payload)
export const expandKbSimilarQuestions = (entryId) =>
  http.post(`/kb/entries/${entryId}/similar-questions/expand`, null, { silent: true })
export const deleteKbSimilarQuestion = (id) => http.delete(`/kb/similar-questions/${id}`)
export const updateKbSimilarQuestionStatus = (id, status) =>
  http.put(`/kb/similar-questions/${id}/status`, { status })
export const getKbEntryVersions = (entryId) =>
  http.get(`/kb/entries/${entryId}/versions`, { silent: true })
export const rollbackKbEntryVersion = (versionId) =>
  http.post(`/kb/versions/${versionId}/rollback`)
export const getKbUnmatchedQuestions = (params) =>
  http.get('/kb/unmatched-questions', { params, silent: true })
export const ignoreKbUnmatchedQuestion = (id) =>
  http.put(`/kb/unmatched-questions/${id}/ignore`)
export const batchIgnoreKbUnmatchedQuestions = (ids) =>
  http.put('/kb/unmatched-questions/batch-ignore', { ids })
export const restoreKbUnmatchedQuestion = (id) =>
  http.put(`/kb/unmatched-questions/${id}/restore`)
export const convertKbUnmatchedQuestion = (id, payload) =>
  http.post(`/kb/unmatched-questions/${id}/convert`, payload)
export const getKbHealth = () => http.get('/kb/health', { silent: true })
export const getKbDuplicates = (params) =>
  http.get('/kb/duplicates', { params, silent: true })
export const importKbPackage = (payload) => http.post('/kb/import', payload)
export const getKbImportTasks = (params) =>
  http.get('/kb/import/tasks', { params, silent: true })

/* ---------- 用户端常见问题（仅启用条目/分类） ---------- */
export const getUserKbCategories = () =>
  http.get('/user/knowledge/categories', { silent: true })
export const getUserKbEntries = (params) =>
  http.get('/user/knowledge/entries', { params, silent: true })

/* ---------- 会话（访客/用户端） ---------- */
export const getSessions = (params) => http.get('/chat/sessions', { params, silent: true })
export const createChatSession = () => http.post('/chat/sessions')
export const getCurrentSession = () =>
  http.get('/chat/sessions/current', { silent: true })
export const getSessionDetail = (id) => http.get(`/chat/sessions/${id}`)
export const getSessionMessages = (id, params) =>
  http.get(`/chat/sessions/${id}/messages`, { params, silent: true })
export const sendSessionMessage = (id, payload) =>
  http.post(`/chat/sessions/${id}/messages`, payload)
export const transferSession = (id, reason, skillGroupId) =>
  http.post(`/chat/sessions/${id}/transfer`, { reason, skillGroupId })
export const closeChatSession = (id, payload = {}) =>
  http.post(`/chat/sessions/${id}/close`, payload)
export const rateChatSession = (id, payload) =>
  http.post(`/chat/sessions/${id}/rating`, payload)

/* ---------- 工单 ---------- */
export const getWorkOrders = (params) => http.get('/workorders', { params, silent: true })
export const getMyWorkOrders = (params) =>
  http.get('/workorders/my', { params, silent: true })
export const getOverdueWorkOrders = (params) =>
  http.get('/workorders/overdue', { params, silent: true })
export const getWorkOrderDetail = (id) => http.get(`/workorders/${id}`)
export const getWorkOrderLogs = (id, params) =>
  http.get(`/workorders/${id}/logs`, { params, silent: true })
export const createWorkOrder = (payload) => http.post('/workorders', payload)
export const updateWorkOrder = (id, payload) => http.put(`/workorders/${id}`, payload)
export const assignWorkOrder = (id, payload) =>
  http.post(`/workorders/${id}/assign`, payload)
export const acceptWorkOrder = (id, remark) =>
  http.post(`/workorders/${id}/accept`, { remark })
export const processWorkOrder = (id, remark) =>
  http.post(`/workorders/${id}/process`, { remark })
export const resolveWorkOrder = (id, remark) =>
  http.post(`/workorders/${id}/resolve`, { remark })
export const closeWorkOrder = (id, payload = {}) =>
  http.post(`/workorders/${id}/close`, payload)
export const reopenWorkOrder = (id, reason) =>
  http.post(`/workorders/${id}/reopen`, { reason })
export const updateWorkOrderPriority = (id, priority) =>
  http.put(`/workorders/${id}/priority`, { priority })
export const rateWorkOrder = (id, payload) =>
  http.post(`/workorders/${id}/rating`, payload)

/* ---------- 工单表单引擎（S3-A：自定义字段 / 工单模板 / 建单表单） ---------- */
export const getOrderFormConfig = () =>
  http.get('/workorders/form', { silent: true })
export const getOrderFields = (params) =>
  http.get('/admin/order-fields', { params, silent: true })
export const createOrderField = (payload) => http.post('/admin/order-fields', payload)
export const updateOrderField = (id, payload) =>
  http.put(`/admin/order-fields/${id}`, payload)
export const updateOrderFieldStatus = (id, status) =>
  http.put(`/admin/order-fields/${id}/status`, { status })
export const deleteOrderField = (id) => http.delete(`/admin/order-fields/${id}`)
export const getOrderTemplates = (params) =>
  http.get('/admin/order-templates', { params, silent: true })
export const createOrderTemplate = (payload) => http.post('/admin/order-templates', payload)
export const updateOrderTemplate = (id, payload) =>
  http.put(`/admin/order-templates/${id}`, payload)
export const updateOrderTemplateStatus = (id, status) =>
  http.put(`/admin/order-templates/${id}/status`, { status })
export const deleteOrderTemplate = (id) => http.delete(`/admin/order-templates/${id}`)

/* ---------- 工单多级 SLA（S3-B：策略配置 / 计时详情 / 停表恢复 / 扫描演练） ---------- */
export const getSlaPolicies = (params) =>
  http.get('/admin/sla-policies', { params, silent: true })
export const getSlaPolicyDetail = (id) => http.get(`/admin/sla-policies/${id}`)
export const createSlaPolicy = (payload) => http.post('/admin/sla-policies', payload)
export const updateSlaPolicy = (id, payload) =>
  http.put(`/admin/sla-policies/${id}`, payload)
export const updateSlaPolicyStatus = (id, status) =>
  http.put(`/admin/sla-policies/${id}/status`, { status })
export const deleteSlaPolicy = (id) => http.delete(`/admin/sla-policies/${id}`)
export const getWorkOrderSla = (id) => http.get(`/workorders/${id}/sla`, { silent: true })
export const pauseWorkOrder = (id, payload = {}) =>
  http.post(`/workorders/${id}/pause`, payload)
export const resumeWorkOrder = (id, payload = {}) =>
  http.post(`/workorders/${id}/resume`, payload)
export const scanWorkOrderSla = (id, payload = {}) =>
  http.post(`/workorders/${id}/sla/scan`, payload, { silent: true })

/* ---------- 工单规则与自动化（S3-C：自动分配 / 事件触发器 / 时间自动化） ---------- */
export const getOrderRules = (params) =>
  http.get('/admin/order-rules', { params, silent: true })
export const getOrderRuleDetail = (id) => http.get(`/admin/order-rules/${id}`)
export const createOrderRule = (payload) => http.post('/admin/order-rules', payload)
export const updateOrderRule = (id, payload) =>
  http.put(`/admin/order-rules/${id}`, payload)
export const updateOrderRuleStatus = (id, status) =>
  http.put(`/admin/order-rules/${id}/status`, { status })
export const deleteOrderRule = (id) => http.delete(`/admin/order-rules/${id}`)
export const getOrderRuleRuns = (id, params) =>
  http.get(`/admin/order-rules/${id}/runs`, { params, silent: true })
export const runOrderRuleOnce = (id) => http.post(`/admin/order-rules/${id}/run`, null)
export const previewOrderRule = (payload) =>
  http.post('/admin/order-rules/preview', payload, { silent: true })
export const getWorkOrderRules = (id) =>
  http.get(`/workorders/${id}/rules`, { silent: true })

/* ---------- 坐席工作台（队列/列表） ---------- */
export const getAgentList = () => http.get('/agent/list', { silent: true })
export const getAgentQueue = (params) => http.get('/agent/queue', { params, silent: true })
export const getOrderPool = (params) =>
  http.get('/agent/orders/pool', { params, silent: true })

/* ---------- 快捷回复（S2：公共话术 / 个人话术） ---------- */
export const getQuickReplies = (params) =>
  http.get('/quick-replies', { params, silent: true })
export const getQuickReplyCategories = () =>
  http.get('/quick-replies/categories', { silent: true })
export const createQuickReply = (payload) => http.post('/quick-replies', payload)
export const updateQuickReply = (id, payload) => http.put(`/quick-replies/${id}`, payload)
export const updateQuickReplyStatus = (id, status) =>
  http.put(`/quick-replies/${id}/status`, { status })
export const deleteQuickReply = (id) => http.delete(`/quick-replies/${id}`)
export const useQuickReply = (id) => http.post(`/quick-replies/${id}/use`)

/* ---------- 技能组（S2：管理端维护 / 坐席我的 / 访客咨询类型） ---------- */
export const getSkillGroups = (params) =>
  http.get('/admin/skill-groups', { params, silent: true })
export const createSkillGroup = (payload) => http.post('/admin/skill-groups', payload)
export const updateSkillGroup = (id, payload) =>
  http.put(`/admin/skill-groups/${id}`, payload)
export const updateSkillGroupStatus = (id, status) =>
  http.put(`/admin/skill-groups/${id}/status`, { status })
export const deleteSkillGroup = (id) => http.delete(`/admin/skill-groups/${id}`)
export const getMySkillGroups = () =>
  http.get('/agent/skill-groups/mine', { silent: true })
export const getChatSkillGroups = () =>
  http.get('/chat/skill-groups', { silent: true })

/* ---------- 坐席辅助（S2：实时推荐 / 进线摘要 / 服务小结） ---------- */
export const getAgentRecommendations = (sessionId, params) =>
  http.get(`/agent/sessions/${sessionId}/recommendations`, { params, silent: true })
export const getSessionBrief = (sessionId) =>
  http.get(`/agent/sessions/${sessionId}/brief`, { silent: true })
export const regenerateSessionBrief = (sessionId) =>
  http.post(`/agent/sessions/${sessionId}/brief/regenerate`, null, { silent: true })
export const getSessionSummary = (sessionId) =>
  http.get(`/agent/sessions/${sessionId}/summary`, { silent: true })
export const generateSessionSummary = (sessionId) =>
  http.post(`/agent/sessions/${sessionId}/summary/generate`, null, { silent: true })
export const saveSessionSummary = (sessionId, payload) =>
  http.put(`/agent/sessions/${sessionId}/summary`, payload)
export const getAgentCustomerCard = (sessionId) =>
  http.get(`/agent/sessions/${sessionId}/customer`, { silent: true })
export const getAgentSuggestions = (sessionId, params) =>
  http.get(`/agent/sessions/${sessionId}/suggestions`, { params, silent: true })
export const getSessionNotes = (sessionId) =>
  http.get(`/agent/sessions/${sessionId}/notes`, { silent: true })
export const createSessionNote = (sessionId, content) =>
  http.post(`/agent/sessions/${sessionId}/notes`, { content })
export const getChatSuggestions = (params) =>
  http.get('/chat/suggestions', { params, silent: true })

/* ---------- 平台端：租户与套餐（S4-B，仅 PLATFORM_ADMIN） ---------- */
export const getPlatformTenants = (params) =>
  http.get('/platform/tenants', { params, silent: true })
export const getPlatformTenantDetail = (id) =>
  http.get(`/platform/tenants/${id}`, { silent: true })
export const createPlatformTenant = (payload) => http.post('/platform/tenants', payload)
export const updatePlatformTenant = (id, payload) =>
  http.put(`/platform/tenants/${id}`, payload)
export const updatePlatformTenantStatus = (id, status) =>
  http.put(`/platform/tenants/${id}/status`, { status })
export const changePlatformTenantPlan = (id, planCode) =>
  http.put(`/platform/tenants/${id}/plan`, { planCode })
export const renewPlatformTenant = (id, expireTime) =>
  http.put(`/platform/tenants/${id}/expire`, { expireTime })
export const deletePlatformTenant = (id) => http.delete(`/platform/tenants/${id}`)
export const refreshPlatformTenantUsage = (id) =>
  http.post(`/platform/tenants/${id}/usage/snapshot`, null, { silent: true })
export const getPlatformPlans = () => http.get('/platform/plans', { silent: true })
export const getPlatformPlanConfigs = () =>
  http.get('/platform/plans/config', { silent: true })

/* ---------- 操作审计（S4-C，PL-06）：平台跨租户 / 租户本域 ---------- */
export const getPlatformAuditLogs = (params) =>
  http.get('/platform/audit-logs', { params, silent: true })
export const getPlatformAuditLogDetail = (id) =>
  http.get(`/platform/audit-logs/${id}`, { silent: true })
export const getAdminAuditLogs = (params) =>
  http.get('/admin/audit-logs', { params, silent: true })
export const getAdminAuditLogDetail = (id) =>
  http.get(`/admin/audit-logs/${id}`, { silent: true })

/* ---------- 智能质检（S5-A，QC-01~QC-05）：规则 / 记录 / 评分卡 / 概览 ---------- */
export const getQualityRules = (params) =>
  http.get('/admin/quality/rules', { params, silent: true })
export const getQualityRuleDetail = (id) =>
  http.get(`/admin/quality/rules/${id}`, { silent: true })
export const createQualityRule = (payload) => http.post('/admin/quality/rules', payload)
export const updateQualityRule = (id, payload) =>
  http.put(`/admin/quality/rules/${id}`, payload)
export const updateQualityRuleStatus = (id, status) =>
  http.put(`/admin/quality/rules/${id}/status`, { status })
export const deleteQualityRule = (id) => http.delete(`/admin/quality/rules/${id}`)
export const getQualityRecords = (params) =>
  http.get('/admin/quality/records', { params, silent: true })
export const getQualityRecordDetail = (id) =>
  http.get(`/admin/quality/records/${id}`, { silent: true })
export const evaluateQualitySession = (sessionId) =>
  http.post(`/admin/quality/sessions/${sessionId}/evaluate`, null)
export const getQualityOverview = (params) =>
  http.get('/admin/quality/overview', { params, silent: true })
export const runQualityScan = () => http.post('/admin/quality/scan', null)
export const getQualitySessions = (params) =>
  http.get('/admin/quality/sessions', { params, silent: true })

/* ---------- 智能质检：人工复核与人机对比（S5-B，QC-06 / QC-07） ---------- */
export const reviewQualityRecord = (id, payload) =>
  http.put(`/admin/quality/records/${id}/review`, payload)
export const confirmQualityRecord = (id, payload = {}) =>
  http.put(`/admin/quality/records/${id}/confirm`, payload)
export const rejectQualityRecord = (id, payload) =>
  http.put(`/admin/quality/records/${id}/reject`, payload)
export const getQualityComparison = (params) =>
  http.get('/admin/quality/comparison', { params, silent: true })

/* ---------- 告警规则与告警中心（S6-B，AS-11，仅 ADMIN） ---------- */
export const getAlertRules = (params) =>
  http.get('/admin/alert-rules', { params, silent: true })
export const createAlertRule = (payload) => http.post('/admin/alert-rules', payload)
export const updateAlertRule = (id, payload) =>
  http.put(`/admin/alert-rules/${id}`, payload)
export const updateAlertRuleStatus = (id, status) =>
  http.put(`/admin/alert-rules/${id}/status`, { status })
export const deleteAlertRule = (id) => http.delete(`/admin/alert-rules/${id}`)
export const getAlerts = (params) => http.get('/admin/alerts', { params, silent: true })
export const getAlertSummary = () => http.get('/admin/alerts/summary', { silent: true })
export const scanAlerts = () => http.post('/admin/alerts/scan', null)
export const ackAlert = (id, note) =>
  http.put(`/admin/alerts/${id}/ack`, { note: note || null })
export const closeAlert = (id) => http.put(`/admin/alerts/${id}/close`, null)

/* ---------- 告警规则与告警中心（S6-B，AS-11，坐席本人） ---------- */
export const getMyAlertRules = () => http.get('/agent/alert-rules', { silent: true })
export const createMyAlertRule = (payload) => http.post('/agent/alert-rules', payload)
export const updateMyAlertRule = (id, payload) =>
  http.put(`/agent/alert-rules/${id}`, payload)
export const updateMyAlertRuleStatus = (id, status) =>
  http.put(`/agent/alert-rules/${id}/status`, { status })
export const deleteMyAlertRule = (id) => http.delete(`/agent/alert-rules/${id}`)
export const getMyAlerts = (params) => http.get('/agent/alerts', { params, silent: true })
export const getMyAlertSummary = () => http.get('/agent/alerts/summary', { silent: true })
export const ackMyAlert = (id, note) =>
  http.put(`/agent/alerts/${id}/ack`, { note: note || null })
export const closeMyAlert = (id) => http.put(`/agent/alerts/${id}/close`, null)

/* ---------- 组长监听（S6-B，AS-10，仅 ADMIN） ---------- */
export const getMonitorSessions = (params) =>
  http.get('/admin/monitor/sessions', { params, silent: true })
export const getMonitorDetail = (id) =>
  http.get(`/admin/monitor/sessions/${id}`, { silent: true })
export const supervisorWhisper = (id, content) =>
  http.post(`/admin/monitor/sessions/${id}/whisper`, { content })
export const supervisorTakeover = (id) =>
  http.post(`/admin/monitor/sessions/${id}/takeover`, null)
export const supervisorReassign = (id, payload) =>
  http.post(`/admin/monitor/sessions/${id}/reassign`, payload)
export const getMonitorAgents = () => http.get('/admin/monitor/agents', { silent: true })

/* ---------- 网页接入配置（仅 ADMIN） ---------- */
export const getEmbedConfig = () => http.get('/admin/embed/config', { silent: true })
export const updateEmbedConfig = (payload) => http.put('/admin/embed/config', payload)
export const rotateEmbedSecret = () => http.post('/admin/embed/config/secret')

/* ---------- 统计与套餐 ---------- */
export const getDashboard = () => http.get('/statistics/dashboard', { silent: true })
export const getPlanUsage = () => http.get('/plan', { silent: true })
export const getStatsOverview = (params) =>
  http.get('/statistics/overview', { params, silent: true })
export const getStatsSessionTrend = (params) =>
  http.get('/statistics/session-trend', { params, silent: true })
export const getStatsAgentWorkload = (params) =>
  http.get('/statistics/agent-workload', { params, silent: true })
export const getStatsKbHit = (params) =>
  http.get('/statistics/kb-hit', { params, silent: true })
export const getStatsWorkOrderDistribution = (params) =>
  http.get('/statistics/workorder-distribution', { params, silent: true })
export const getStatsAiCalls = (params) =>
  http.get('/statistics/ai-calls', { params, silent: true })

/* ---------- 运营监控：实时大屏与运营指标（S6-A，仅 ADMIN） ---------- */
export const getOpsScreen = () => http.get('/statistics/ops/screen', { silent: true })
export const getOpsMetrics = (params) =>
  http.get('/statistics/ops/metrics', { params, silent: true })
export const rebuildOpsMetrics = (params) =>
  http.post('/statistics/ops/metrics/rebuild', null, { params })
export const getOpsAgents = (params) =>
  http.get('/statistics/ops/agents', { params, silent: true })

/* ---------- 大屏布局配置（S6-C，DB-07，仅 ADMIN） ---------- */
export const getScreenLayout = () => http.get('/statistics/ops/layout', { silent: true })
export const saveScreenLayout = (payload) => http.put('/statistics/ops/layout', payload)
export const resetScreenLayout = () => http.post('/statistics/ops/layout/reset', null)

/* ---------- 客户之声：未命中聚类与热点话题（S6-C，DB-06，仅 ADMIN） ---------- */
export const getVocOverview = () => http.get('/kb/voc/overview', { silent: true })
export const getVocTopics = (params) =>
  http.get('/kb/voc/topics', { params, silent: true })
export const getVocTopic = (id) => http.get(`/kb/voc/topics/${id}`, { silent: true })
export const createVocTopic = (payload) => http.post('/kb/voc/topics', payload)
export const updateVocTopic = (id, payload) => http.put(`/kb/voc/topics/${id}`, payload)
export const deleteVocTopic = (id) => http.delete(`/kb/voc/topics/${id}`)
export const updateVocTopicStatus = (id, status) =>
  http.put(`/kb/voc/topics/${id}/status`, null, { params: { status } })
export const mergeVocTopics = (payload) => http.post('/kb/voc/topics/merge', payload)
export const clusterVocTopics = () => http.post('/kb/voc/cluster', null)
export const getVocWordCloud = (params) =>
  http.get('/kb/voc/word-cloud', { params, silent: true })

/* ---------- AI 直连（仅作为无会话时的降级能力，正式对话走会话接口） ---------- */
export const sendAiMessage = (message) => http.post('/ai/chat', { message })

/* ---------- 大模型配置（仅 ADMIN） ---------- */
export const getAiModelConfigs = () => http.get('/admin/ai/configs', { silent: true })
export const getAiProviderPresets = () => http.get('/admin/ai/presets', { silent: true })
export const createAiModelConfig = (payload) => http.post('/admin/ai/configs', payload)
export const updateAiModelConfig = (id, payload) => http.put(`/admin/ai/configs/${id}`, payload)
export const deleteAiModelConfig = (id) => http.delete(`/admin/ai/configs/${id}`)
export const testAiModelConfig = (id) =>
  http.post(`/admin/ai/configs/${id}/test`, null, { silent: true })
export const setDefaultAiModelConfig = (id) => http.put(`/admin/ai/configs/${id}/default`)
export const toggleAiModelConfig = (id, enabled) =>
  http.put(`/admin/ai/configs/${id}/status`, null, { params: { enabled } })
