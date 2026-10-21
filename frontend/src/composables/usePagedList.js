import { reactive, ref } from 'vue'

/**
 * 分页列表通用逻辑
 * 按任务契约：pageNum 从 1 开始，返回 { list, total, pageNum, pageSize, pages }
 * 空字符串筛选条件不会发送给后端
 */
export function usePagedList(fetcher, initialFilters = {}) {
  const defaults = { ...initialFilters }

  const filters = reactive({ ...initialFilters })
  const rows = ref([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const pages = ref(0)
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref('')

  function buildParams() {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    for (const [key, value] of Object.entries(filters)) {
      if (value !== '' && value !== null && value !== undefined) {
        params[key] = value
      }
    }
    return params
  }

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const data = await fetcher(buildParams())
      const list = Array.isArray(data) ? data : data?.list || []
      rows.value = list
      total.value = Number(data?.total ?? list.length) || 0
      pageNum.value = Number(data?.pageNum ?? pageNum.value) || 1
      pageSize.value = Number(data?.pageSize ?? pageSize.value) || 10
      pages.value =
        Number(data?.pages ?? Math.ceil(total.value / (pageSize.value || 10))) || 0
    } catch (err) {
      rows.value = []
      total.value = 0
      pages.value = 0
      error.value =
        err?.response?.status === 404
          ? '接口返回 404：后端接口尚未部署，稍后刷新即可。'
          : err?.message || '数据加载失败，请确认后端服务正常运行'
    } finally {
      loaded.value = true
      loading.value = false
    }
  }

  function search() {
    pageNum.value = 1
    load()
  }

  function reset() {
    Object.assign(filters, defaults)
    pageNum.value = 1
    pageSize.value = 10
    load()
  }

  function changePage(page) {
    pageNum.value = page
    load()
  }

  function changeSize(size) {
    pageSize.value = size
    pageNum.value = 1
    load()
  }

  return {
    filters,
    rows,
    total,
    pageNum,
    pageSize,
    pages,
    loading,
    loaded,
    error,
    load,
    search,
    reset,
    changePage,
    changeSize
  }
}
