<template>
  <div class="mental-container">
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
          <span class="row-action" @click="notReady">查看全部</span>
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
                  <td class="col-right">
                    <span class="row-action" @click="notReady">处理</span>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { getMentalOverview } from '@/api/mental'
import ChartState from '@/views/dashboard/components/ChartState.vue'

// 与全局 token 保持一致：实色用于图表与进度条，-deep 变体用于文字
const GOOD = '#34c759'
const ATTENTION = '#ff9500'
const INTERVENTION = '#ff3b30'

const trendChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null

const loading = ref(false)
const loadError = ref(false)

const overviewData = reactive({
  goodRate: 0,
  attentionRate: 0,
  interventionRate: 0,
  todayCompleted: 0
})

const warningList = ref<any[]>([])
const trendData = ref<any[]>([])

const statCards = computed(() => [
  {
    label: '心理状态良好',
    value: overviewData.goodRate,
    unit: '%',
    color: 'var(--success-deep)',
    tint: 'var(--success-tint)',
    icon: 'CircleCheck',
    bar: `${clampPct(overviewData.goodRate)}%`
  },
  {
    label: '需要关注',
    value: overviewData.attentionRate,
    unit: '%',
    color: 'var(--warning-deep)',
    tint: 'var(--warning-tint)',
    icon: 'Warning',
    bar: `${clampPct(overviewData.attentionRate)}%`
  },
  {
    label: '需要干预',
    value: overviewData.interventionRate,
    unit: '%',
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

const notReady = () => {
  ElMessage.info('该功能暂未开放')
}

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
