import { computed, reactive, ref } from 'vue'
import { getUserList } from '@/api/user'

export const useUserList = () => {
  const loading = ref(false)
  const loadError = ref(false)
  const userList = ref([])
  const total = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(10)
  const searchForm = reactive({ username: '', status: '', role: '' })
  let latestRequest = 0

  const rangeStart = computed(() =>
    userList.value.length ? (currentPage.value - 1) * pageSize.value + 1 : 0
  )
  const rangeEnd = computed(() =>
    userList.value.length ? Math.min(rangeStart.value + userList.value.length - 1, total.value) : 0
  )

  const fetchList = async () => {
    const requestId = ++latestRequest
    loading.value = true
    loadError.value = false
    try {
      const response = await getUserList({
        page: currentPage.value,
        size: pageSize.value,
        username: searchForm.username.trim() || undefined,
        ...(searchForm.role ? { role: searchForm.role } : {}),
        status:
          searchForm.status === '' || searchForm.status == null ? undefined : searchForm.status
      })
      if (requestId !== latestRequest) return
      const data = response.data
      const count = Number(data?.total)
      if (
        !Array.isArray(data?.records) ||
        !Number.isInteger(count) ||
        count < data.records.length
      ) {
        throw new Error('Invalid user page')
      }
      userList.value = data.records
      total.value = count
    } catch {
      if (requestId !== latestRequest) return
      userList.value = []
      total.value = 0
      loadError.value = true
    } finally {
      if (requestId === latestRequest) loading.value = false
    }
  }

  const handleSearch = () => {
    currentPage.value = 1
    return fetchList()
  }

  const handleReset = () => {
    searchForm.username = ''
    searchForm.status = ''
    searchForm.role = ''
    return handleSearch()
  }

  const handlePageChange = (page) => {
    if (currentPage.value === page) return
    currentPage.value = page
    return fetchList()
  }

  const handleSizeChange = (size) => {
    if (pageSize.value === size) return
    pageSize.value = size
    currentPage.value = 1
    return fetchList()
  }

  return {
    loading,
    loadError,
    userList,
    total,
    currentPage,
    pageSize,
    searchForm,
    rangeStart,
    rangeEnd,
    fetchList,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange
  }
}
