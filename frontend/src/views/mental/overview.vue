<template>
  <div class="mental-container">
    <p>状态比例按最近30天测评记录计算；今日完成量按业务时区 UTC+8 的答卷提交时间计算。</p>
    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div v-for="s in statCards" :key="s.label" class="glass-card stat-tile">
        <div class="tile-head">
          <span class="tile-label">{{ s.label }}</span>
          <span class="icon-chip" :style="{ background: s.tint, color: s.color }">
            <el-icon :size="17"><component :is="s.icon" /></el-icon>
          </span>
        </div>
        <div class="tile-value tnum" :style="{ color: s.color }">
          {{ s.value }}<span v-if="s.unit" class="unit">{{ s.unit }}</span>
        </div>
        <div class="tile-bar">
          <span class="bar-fill" :style="{ width: s.bar, background: s.color }"></span>
        </div>
      </div>
    </div>

    <div class="split-grid">
      <!-- 心理预警 -->
      <section class="glass-panel">
        <header class="panel-head">
          <span class="panel-title">心理预警</span>
          <span class="row-action" @click="openWarnings">查看全部</span>
        </header>
        <div class="panel-body table-scroll-inner">
          <ChartState
            :loading="loading"
            :error="loadError"
            :empty="!loading && !loadError && !warningList.length"
            empty-text="暂无心理预警"
            height="240px"
            @retry="fetchData"
          >
            <table class="plain-table">
              <thead>
                <tr>
                  <th>姓名</th>
                  <th>学院</th>
                  <th>级别</th>
                  <th>时间</th>
                  <th>处置状态</th>
                  <th class="col-right">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(w, i) in warningList" :key="i">
                  <td class="strong">{{ w.name }}</td>
                  <td>{{ w.dept }}</td>
                  <td>
                    <span class="badge" :style="levelStyle(w.level)">{{ w.level }}</span>
                  </td>
                  <td class="tnum">{{ w.time }}</td>
                  <td>{{ warningStatusLabel(w.warningStatus) }}</td>
                  <td class="col-right">
                    <span v-if="canWrite" class="row-action" @click="openHandle(w)">处理</span>
                    <span v-else>仅授权心理人员可处理</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </ChartState>
        </div>
      </section>

      <!-- 趋势分析 -->
      <section class="glass-panel">
        <header class="panel-head">
          <span class="panel-title">趋势分析</span>
        </header>
        <div class="panel-body">
          <div class="legend">
            <span class="legend-item"><i class="dot good"></i>良好</span>
            <span class="legend-item"><i class="dot attention"></i>关注</span>
            <span class="legend-item"><i class="dot intervention"></i>干预</span>
          </div>
          <ChartState
            :loading="loading"
            :error="loadError"
            :empty="!loading && !loadError && !trendData.length"
            height="280px"
            @retry="fetchData"
          >
            <div ref="trendChartRef" class="trend-chart"></div>
          </ChartState>
        </div>
      </section>
    </div>
    <el-dialog v-model="warningsVisible" title="心理预警列表" width="900px">
      <el-select
        v-model="warningFilter"
        clearable
        placeholder="全部状态"
        @change="changeWarningFilter"
      >
        <el-option
          v-for="status in ['OPEN', 'IN_PROGRESS', 'RESOLVED']"
          :key="status"
          :label="warningStatusLabel(status)"
          :value="status"
        />
      </el-select>
      <el-alert v-if="warningsError" type="error" :closable="false" :title="warningsError"
        ><el-button link @click="fetchWarnings">重新加载</el-button></el-alert
      >
      <el-table v-else v-loading="warningsLoading" :data="warningRows">
        <el-table-column prop="name" label="姓名" /><el-table-column prop="dept" label="学院" />
        <el-table-column prop="level" label="级别" width="90" /><el-table-column
          prop="time"
          label="测评时间"
          min-width="160"
        />
        <el-table-column label="状态"
          ><template #default="{ row }">{{
            warningStatusLabel(row.warningStatus)
          }}</template></el-table-column
        >
        <el-table-column
          v-if="canWrite"
          prop="warningNote"
          label="处置说明"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column v-if="canWrite" label="操作"
          ><template #default="{ row }"
            ><el-button link @click="openHandle(row)">处理</el-button></template
          ></el-table-column
        >
      </el-table>
      <el-pagination
        v-model:current-page="warningPage"
        :page-size="10"
        :total="warningTotal"
        layout="total, prev, pager, next"
        @current-change="fetchWarnings"
      />
    </el-dialog>
    <el-dialog v-model="handleVisible" title="处置心理预警" width="540px">
      <el-form label-width="90px">
        <el-form-item label="状态"
          ><el-select v-model="handleForm.status"
            ><el-option
              v-for="status in ['OPEN', 'IN_PROGRESS', 'RESOLVED']"
              :key="status"
              :label="warningStatusLabel(status)"
              :value="status" /></el-select
        ></el-form-item>
        <el-form-item label="处置说明"
          ><el-input
            v-model="handleForm.note"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="handleVisible = false">取消</el-button
        ><el-button type="primary" :loading="handling" @click="saveHandle"
          >保存</el-button
        ></template
      >
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { getMentalOverview, getMentalWarnings, handleMentalWarning } from '@/api/mental'
import { useUserStore } from '@/store/modules/user'
import ChartState from '@/views/dashboard/components/ChartState.vue'

// 与全局 token 保持一致：实色用于图表与进度条，-deep 变体用于文字
const GOOD = '#34c759'
const ATTENTION = '#ff9500'
const INTERVENTION = '#ff3b30'

const trendChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null

const loading = ref(false)
const userStore = useUserStore()
const canWrite = computed(() =>
  userStore.userRoles.some((role: string) => ['admin', 'psychologist'].includes(role))
)
const warningsVisible = ref(false)
const warningRows = ref<any[]>([])
const warningPage = ref(1)
const warningTotal = ref(0)
const warningFilter = ref('')
const warningsLoading = ref(false)
const warningsError = ref('')
let warningSequence = 0
const handleVisible = ref(false)
const handling = ref(false)
const handleForm = reactive({ id: 0, status: 'OPEN', note: '' })
const warningStatusLabel = (status: string) =>
  ({ OPEN: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已处理' })[status] || '待处理'
const fetchWarnings = async () => {
  const sequence = ++warningSequence
  warningsLoading.value = true
  warningsError.value = ''
  try {
    const res = await getMentalWarnings({
      page: warningPage.value,
      size: 10,
      status: warningFilter.value || undefined
    })
    if (sequence === warningSequence) {
      warningRows.value = res.data.records || []
      warningTotal.value = Number(res.data.total) || 0
    }
  } catch (e) {
    if (sequence === warningSequence)
      warningsError.value = (e as any)?.message || '预警列表加载失败，请重试'
  } finally {
    if (sequence === warningSequence) warningsLoading.value = false
  }
}
const changeWarningFilter = () => {
  warningPage.value = 1
  fetchWarnings()
}
const openWarnings = () => {
  warningsVisible.value = true
  fetchWarnings()
}
const openHandle = (row: any) => {
  if (!canWrite.value) return
  handleForm.id = row.id
  handleForm.status = row.warningStatus || 'OPEN'
  handleForm.note = row.warningNote || ''
  handleVisible.value = true
}
const saveHandle = async () => {
  if (!canWrite.value || handling.value) return
  if (handleForm.status !== 'OPEN' && !handleForm.note.trim()) {
    ElMessage.warning('请填写处置说明')
    return
  }
  handling.value = true
  try {
    await handleMentalWarning(handleForm.id, { status: handleForm.status, note: handleForm.note })
    handleVisible.value = false
    ElMessage.success('处置已保存')
    await Promise.all([fetchData(), fetchWarnings()])
  } finally {
    handling.value = false
  }
}
const loadError = ref(false)

const overviewData = reactive({
  goodRate: 0,
  attentionRate: 0,
  interventionRate: 0,
  todayCompleted: 0,
  recentAssessmentCount: 0
})

const warningList = ref<any[]>([])
const trendData = ref<any[]>([])

const statCards = computed(() => [
  {
    label: '心理状态良好',
    value: overviewData.recentAssessmentCount ? overviewData.goodRate : '—',
    unit: overviewData.recentAssessmentCount ? '%' : '',
    color: 'var(--success-deep)',
    tint: 'var(--success-tint)',
    icon: 'CircleCheck',
    bar: `${clampPct(overviewData.goodRate)}%`
  },
  {
    label: '需要关注',
    value: overviewData.recentAssessmentCount ? overviewData.attentionRate : '—',
    unit: overviewData.recentAssessmentCount ? '%' : '',
    color: 'var(--warning-deep)',
    tint: 'var(--warning-tint)',
    icon: 'Warning',
    bar: `${clampPct(overviewData.attentionRate)}%`
  },
  {
    label: '需要干预',
    value: overviewData.recentAssessmentCount ? overviewData.interventionRate : '—',
    unit: overviewData.recentAssessmentCount ? '%' : '',
    color: 'var(--error-deep)',
    tint: 'var(--error-tint)',
    icon: 'Bell',
    bar: `${clampPct(overviewData.interventionRate)}%`
  },
  {
    label: '今日完成问卷',
    value: overviewData.todayCompleted,
    unit: '',
    color: 'var(--primary-color)',
    tint: 'var(--primary-tint)',
    icon: 'DocumentChecked',
    // 份数不是百分比，进度条无意义，按满格处理
    bar: '100%'
  }
])

function clampPct(v: number) {
  const n = Number(v)
  return Number.isFinite(n) ? Math.max(0, Math.min(100, n)) : 0
}

const levelStyle = (level: string) =>
  level === '高危' || level === '重度'
    ? { background: 'var(--error-tint)', color: 'var(--error-deep)' }
    : { background: 'var(--warning-tint)', color: 'var(--warning-deep)' }

const initTrendChart = () => {
  if (!trendChartRef.value) return
  // 插槽切换会重建 DOM，旧实例需按当前节点重建
  if (trendChart && trendChart.getDom() !== trendChartRef.value) {
    trendChart.dispose()
    trendChart = null
  }
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }

  // 按月份分组的趋势数据
  const months = [...new Set(trendData.value.map((item: any) => item.month))]
  const goodData: number[] = []
  const attentionData: number[] = []
  const interventionData: number[] = []

  months.forEach((month) => {
    const monthItems = trendData.value.filter((item: any) => item.month === month)
    let good = 0,
      attention = 0,
      intervention = 0
    monthItems.forEach((item: any) => {
      const count = Number(item.count)
      if (item.level === '正常' || item.level === '轻度') good += count
      else if (item.level === '中度') attention += count
      else if (item.level === '重度' || item.level === '高危') intervention += count
    })
    goodData.push(good)
    attentionData.push(attention)
    interventionData.push(intervention)
  })

  const axisLabel = { color: 'rgba(60,60,67,0.45)', fontSize: 11 }
  const bar = (name: string, data: number[], color: string) => ({
    name,
    type: 'bar',
    data,
    barMaxWidth: 15,
    itemStyle: { color, borderRadius: [3, 3, 0, 0] }
  })

  trendChart.setOption(
    {
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(255,255,255,0.92)',
        borderColor: 'rgba(60,60,67,0.10)',
        textStyle: { color: '#1c1c1e', fontSize: 12 },
        extraCssText:
          'backdrop-filter:blur(12px);border-radius:10px;box-shadow:0 8px 24px -12px rgba(16,24,40,0.34)'
      },
      grid: { left: 10, right: 14, bottom: 6, top: 16, containLabel: true },
      xAxis: {
        type: 'category',
        data: months,
        axisLabel,
        axisLine: { lineStyle: { color: 'rgba(60,60,67,0.16)' } },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value',
        axisLabel,
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: 'rgba(60,60,67,0.10)', type: [3, 5] } }
      },
      series: [
        bar('良好', goodData, GOOD),
        bar('关注', attentionData, ATTENTION),
        bar('干预', interventionData, INTERVENTION)
      ]
    },
    true
  )
}

const fetchData = async () => {
  loading.value = true
  loadError.value = false
  try {
    const res = await getMentalOverview()
    const data = res.data
    overviewData.goodRate = data.goodRate || 0
    overviewData.attentionRate = data.attentionRate || 0
    overviewData.interventionRate = data.interventionRate || 0
    overviewData.todayCompleted = data.todayCompleted || 0
    overviewData.recentAssessmentCount = Number(data.recentAssessmentCount) || 0
    warningList.value = data.warningList || []
    trendData.value = data.trendData || []
  } catch (e) {
    loadError.value = true
    console.error('获取心理概览数据失败', e)
  } finally {
    loading.value = false
  }

  // 容器由 ChartState 的 v-if 控制，需等 loading 落下后再初始化
  await nextTick()
  if (!loadError.value && trendData.value.length) initTrendChart()
}

const handleResize = () => trendChart?.resize()

onMounted(() => {
  fetchData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
})
</script>

<style scoped lang="scss">
.mental-container {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 14px;
}

.stat-tile {
  padding: 18px;

  .tile-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;

    .tile-label {
      font-size: 13px;
      color: var(--text-color-secondary);
    }
  }

  .tile-value {
    font-size: 30px;
    font-weight: 600;
    line-height: 1;

    .unit {
      font-size: 15px;
      margin-left: 2px;
    }
  }

  .tile-bar {
    margin-top: 12px;
    height: 5px;
    border-radius: 3px;
    background: rgba(120, 120, 128, 0.16);
    overflow: hidden;

    .bar-fill {
      display: block;
      height: 100%;
      border-radius: 3px;
      transition: width 0.4s ease;
    }
  }
}

.split-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;

  @media (max-width: 1100px) {
    grid-template-columns: 1fr;
  }
}

.glass-panel {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.panel-body {
  padding: 0 18px 16px;
  flex: 1;
  min-width: 0;
}

.table-scroll-inner {
  overflow-x: auto;
}

.trend-chart {
  width: 100%;
  height: 280px;
}

.legend {
  display: flex;
  gap: 18px;
  font-size: 12px;
  color: rgba(60, 60, 67, 0.72);
  margin-bottom: 4px;

  .legend-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    display: block;

    &.good {
      background: var(--success-color);
    }

    &.attention {
      background: var(--warning-color);
    }

    &.intervention {
      background: var(--error-color);
    }
  }
}
</style>
