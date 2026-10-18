import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

export function useTask() {
  const busy = ref(false),
    error = ref(''),
    fields = ref({})
  async function run(action, message) {
    if (busy.value) return false
    busy.value = true
    error.value = ''
    fields.value = {}
    try {
      const result = await action()
      if (message) ElMessage.success(message)
      return { result }
    } catch (e) {
      error.value = e.message || '操作失败，请重试'
      fields.value = e.fields || {}
      return false
    } finally {
      busy.value = false
    }
  }
  return { busy, error, fields, run }
}
export function usePaged(fetcher, filters = {}) {
  const query = reactive({ page: 1, page_size: 20, ...filters })
  const result = ref({ items: [], total: 0 })
  const task = useTask()
  let version = 0
  const loading = ref(false),
    loadError = ref('')
  async function load(reset = false) {
    if (reset) query.page = 1
    const current = ++version
    loading.value = true
    loadError.value = ''
    try {
      const data = await fetcher({ ...query })
      if (current === version) {
        result.value = data
        if (!data.items.length && data.total > 0 && query.page > 1) {
          query.page = Math.ceil(data.total / query.page_size)
          return load()
        }
      }
    } catch (e) {
      if (current === version) loadError.value = e.message || '加载失败'
    } finally {
      if (current === version) loading.value = false
    }
  }
  onMounted(() => load())
  return { query, result, loading, loadError, load, task }
}
export async function confirmAction(message, title = '确认操作') {
  try {
    await ElMessageBox.confirm(message, title, {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
      closeOnClickModal: false,
    })
    return true
  } catch {
    return false
  }
}
