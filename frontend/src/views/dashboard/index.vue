<template>
  <div class="dashboard-container">
    <!-- 统计卡片 -->
    <div v-loading="statsLoading" class="stats-grid">
      <StatisticCard
        v-for="s in statCards"
        :key="s.title"
        :title="s.title"
        :value="s.value"
        :icon="s.icon"
        :color="s.color"
        :tint="s.tint"
        :suffix="s.suffix"
      />
    </div>

    <!-- 趋势 + 分布 -->
    <div class="chart-grid">
      <section class="glass-panel">
        <header class="panel-head">
          <span class="panel-title">师生增长趋势</span>
          <div class="seg-control">
            <button
              v-for="p in periods"
              :key="p.key"
              type="button"
              class="seg-item"
              :class="{ 'is-active': trendPeriod === p.key }"
              @click="trendPeriod = p.key"
            >
              {{ p.label }}
            </button>
          </div>
        </header>

        <div class="panel-body">
          <div class="legend">
            <span class="legend-item">
              <i class="dot" style="background: var(--primary-color)"></i>教师
            </span>
            <span class="legend-item">
              <i class="dot" style="background: var(--success-color)"></i>学生
            </span>
          </div>

          <ChartState
            :loading="trendLoading"
            :error="trendError"
            :empty="!trendLoading && !trendError && !trendData.days.length"
            height="300px"
            @retry="loadTrendData"
          >
            <div ref="trendChartRef" class="chart-container" style="height: 300px"></div>
          </ChartState>
        </div>
      </section>

      <section class="glass-panel dist-panel">
        <header class="panel-head">
          <span class="panel-title">师生分布</span>
        </header>

        <div class="panel-body dist-body">
          <ChartState
            :loading="distLoading"
            :error="distError"
            :empty="!distLoading && !distError && !distributionData.length"
            height="240px"
            @retry="loadDistributionData"
          >
            <div class="donut-wrap">
              <div ref="pieChartRef" class="donut"></div>
              <div class="donut-center">
                <span class="total tnum">{{ distTotal.toLocaleString('en-US') }}</span>
                <span class="total-label">在册总人数</span>
              </div>
            </div>

            <ul class="dist-legend">
              <li v-for="(d, i) in distributionData" :key="d.name">
                <i class="swatch" :style="{ background: PIE_COLORS[i % PIE_COLORS.length] }"></i>
                <span class="name">{{ d.name }}</span>
                <span class="count tnum">{{ d.value.toLocaleString('en-US') }}</span>
                <span class="pct tnum">{{ pct(d.value) }}</span>
              </li>
            </ul>
          </ChartState>
        </div>
      </section>
    </div>

    <!-- 最近登录 + 待处理 -->
    <div class="activity-grid">
      <section class="glass-panel">
        <header class="panel-head">
          <span class="panel-title">最近登录</span>
        </header>
        <div class="panel-body table-body">
          <ChartState
            :loading="loginsLoading"
            :error="loginsError"
            :empty="!loginsLoading && !loginsError && !recentLogins.length"
            empty-text="暂无登录记录"
            height="220px"
            @retry="loadRecentLogins"
          >
            <table class="plain-table">
              <thead>
                <tr>
                  <th>用户</th>
                  <th>角色</th>
                  <th>时间</th>
                  <th>IP地址</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(r, i) in recentLogins" :key="i">
                  <td class="strong">{{ r.username }}</td>
                  <td>{{ r.role }}</td>
                  <td class="tnum">{{ r.time }}</td>
                  <td class="tnum">{{ r.ip }}</td>
                </tr>
              </tbody>
            </table>
          </ChartState>
        </div>
      </section>

      <section class="glass-panel">
        <header class="panel-head">
          <span class="panel-title">待处理事项</span>
        </header>
        <div class="panel-body todo-body">
          <div v-for="(a, index) in activities" :key="index" class="todo-item">
            <span
              class="dot"
              :style="{ background: a.color, boxShadow: `0 0 0 3px ${a.halo}` }"
            ></span>
            <div class="todo-main">
              <div class="todo-content">{{ a.content }}</div>
              <div class="todo-time">{{ a.time }}</div>
            </div>
            <el-icon :size="11" class="todo-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import StatisticCard from './components/StatisticCard.vue'
import ChartState from './components/ChartState.vue'
import { getStatistics, getTrendData, getDistributionData, getRecentLogins } from '@/api/dashboard'

const PIE_COLORS = ['#007aff', '#34c759', '#ff9500', '#ff3b30', '#5856d6']

const periods = [
  { key: 'week', label: '本周' },
  { key: 'month', label: '本月' },
  { key: 'year', label: '全年' }
]

// 统计数据
const stats = ref({
  teacherCount: 0,
  studentCount: 0,
  questionnaireCount: 0,
  warningCount: 0
})

// 卡片配色对齐原型：胶囊底用 tint，图标前景用 -deep 变体
const statCards = computed(() => [
  {
    title: '教师总数',
    value: stats.value.teacherCount,
    icon: 'UserFilled',
    color: 'var(--primary-color)',
    tint: 'var(--primary-tint)',
    suffix: '人'
  },
  {
    title: '学生总数',
    value: stats.value.studentCount,
    icon: 'Reading',
    color: 'var(--success-deep)',
    tint: 'var(--success-tint)',
    suffix: '人'
  },
  {
    title: '今日问卷完成',
    value: stats.value.questionnaireCount,
    icon: 'DocumentChecked',
    color: 'var(--warning-deep)',
    tint: 'var(--warning-tint)',
    suffix: '份'
  },
  {
    title: '预警关注',
    value: stats.value.warningCount,
    icon: 'WarningFilled',
    color: 'var(--error-deep)',
    tint: 'var(--error-tint)',
    suffix: '人'
  }
])

// 图表相关
const trendChartRef = ref<HTMLElement>()
const pieChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null
const trendPeriod = ref('month')

const trendData = ref({
  days: [] as string[],
  teacherData: [] as number[],
  studentData: [] as number[]
})

const distributionData = ref([] as { name: string; value: number }[])
const distTotal = computed(() => distributionData.value.reduce((a, b) => a + (b.value || 0), 0))
const pct = (v: number) => (distTotal.value ? Math.round((v / distTotal.value) * 100) + '%' : '0%')

// 最近登录
const recentLogins = ref([] as { username: string; role: string; time: string; ip: string }[])

// 各区块的加载 / 错误态
const statsLoading = ref(false)
const trendLoading = ref(false)
const trendError = ref(false)
const distLoading = ref(false)
const distError = ref(false)
const loginsLoading = ref(false)
const loginsError = ref(false)

// 待处理事项（暂用静态数据，后端无对应接口）
const activities = ref([
  {
    content: '新增5份心理健康预警',
    time: '10分钟前',
    color: '#ff9500',
    halo: 'rgba(255,149,0,0.14)'
  },
  {
    content: '张三老师提交了科研项目',
    time: '30分钟前',
    color: '#34c759',
    halo: 'rgba(52,199,89,0.14)'
  },
  {
    content: '系统检测到异常登录',
    time: '1小时前',
    color: '#ff3b30',
    halo: 'rgba(255,59,48,0.14)'
  },
  {
    content: '李四同学完成了心理问卷',
    time: '2小时前',
    color: '#007aff',
    halo: 'rgba(0,122,255,0.13)'
  }
])

// 加载统计数据
const loadStatistics = async () => {
  statsLoading.value = true
  try {
    const res = await getStatistics()
    if (res.data) {
      stats.value = {
        teacherCount: res.data.teacherCount || 0,
        studentCount: res.data.studentCount || 0,
        questionnaireCount: res.data.questionnaireCount || 0,
        warningCount: res.data.warningCount || 0
      }
    }
  } catch (error) {
    console.error('加载统计数据失败', error)
  } finally {
    statsLoading.value = false
  }
}

// 加载趋势数据
const loadTrendData = async () => {
  trendLoading.value = true
  trendError.value = false
  try {
    const res = await getTrendData(trendPeriod.value)
    if (res.data) {
      trendData.value = {
        days: res.data.days || [],
        teacherData: res.data.teacherData || [],
        studentData: res.data.studentData || []
      }
    }
  } catch (error) {
    console.error('加载趋势数据失败', error)
    trendError.value = true
  } finally {
    trendLoading.value = false
  }

  // ChartState 用 v-if 控制插槽，容器要等 loading 落下、DOM 挂载后才存在
  await nextTick()
  if (!trendError.value && trendData.value.days.length) updateTrendChart()
}

// 加载分布数据
const loadDistributionData = async () => {
  distLoading.value = true
  distError.value = false
  try {
    const res = await getDistributionData()
    if (res.data?.data) {
      distributionData.value = res.data.data
    }
  } catch (error) {
    console.error('加载分布数据失败', error)
    distError.value = true
  } finally {
    distLoading.value = false
  }

  await nextTick()
  if (!distError.value && distributionData.value.length) updatePieChart()
}

// 加载最近登录
const loadRecentLogins = async () => {
  loginsLoading.value = true
  loginsError.value = false
  try {
    const res = await getRecentLogins()
    if (res.data) {
      recentLogins.value = res.data
    }
  } catch (error) {
    console.error('加载最近登录失败', error)
    loginsError.value = true
  } finally {
    loginsLoading.value = false
  }
}

// 折线渐变填充：顶部 0.28 → 底部 0.02，对齐原型的面积渐隐
const areaGradient = (rgb: string) =>
  new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: `rgba(${rgb}, 0.28)` },
    { offset: 1, color: `rgba(${rgb}, 0.02)` }
  ])

// 初始化/更新趋势图
const updateTrendChart = () => {
  if (!trendChartRef.value) return
  // 插槽切换会重建 DOM，旧实例绑在已分离节点上，需按当前节点重建
  if (trendChart && trendChart.getDom() !== trendChartRef.value) {
    trendChart.dispose()
    trendChart = null
  }
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  const axisLabel = { color: 'rgba(60,60,67,0.45)', fontSize: 11 }
  const option = {
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
      boundaryGap: false,
      data: trendData.value.days,
      axisLabel,
      axisLine: { lineStyle: { color: 'rgba(60,60,67,0.16)' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLabel,
      axisLine: { show: false },
      axisTick: { show: false },
      // 原型为虚线网格
      splitLine: { lineStyle: { color: 'rgba(60,60,67,0.10)', type: [3, 5] } }
    },
    series: [
      {
        name: '教师',
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: trendData.value.teacherData,
        lineStyle: { width: 2.4, color: '#007aff' },
        itemStyle: { color: '#007aff' },
        areaStyle: { color: areaGradient('0,122,255') }
      },
      {
        name: '学生',
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: trendData.value.studentData,
        lineStyle: { width: 2.4, color: '#34c759' },
        itemStyle: { color: '#34c759' },
        areaStyle: { color: areaGradient('52,199,89') }
      }
    ]
  }
  trendChart.setOption(option, true)
}

// 初始化/更新饼图（环形，中心总数由 HTML 叠加）
const updatePieChart = () => {
  if (!pieChartRef.value) return
  if (pieChart && pieChart.getDom() !== pieChartRef.value) {
    pieChart.dispose()
    pieChart = null
  }
  if (!pieChart) {
    pieChart = echarts.init(pieChartRef.value)
  }
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
      backgroundColor: 'rgba(255,255,255,0.92)',
      borderColor: 'rgba(60,60,67,0.10)',
      textStyle: { color: '#1c1c1e', fontSize: 12 },
      extraCssText:
        'backdrop-filter:blur(12px);border-radius:10px;box-shadow:0 8px 24px -12px rgba(16,24,40,0.34)'
    },
    series: [
      {
        name: '分布',
        type: 'pie',
        radius: ['64%', '88%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 11, borderColor: 'transparent', borderWidth: 3 },
        label: { show: false },
        emphasis: { scale: false, itemStyle: { opacity: 0.88 } },
        data: distributionData.value.map((item, index) => ({
          ...item,
          itemStyle: { color: PIE_COLORS[index % PIE_COLORS.length] }
        }))
      }
    ]
  }
  pieChart.setOption(option, true)
}

// 响应式调整
const handleResize = () => {
  trendChart?.resize()
  pieChart?.resize()
}

onMounted(() => {
  nextTick(() => {
    loadStatistics()
    loadTrendData()
    loadDistributionData()
    loadRecentLogins()
  })
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  pieChart?.dispose()
})

watch(trendPeriod, () => {
  loadTrendData()
})
</script>

<style scoped lang="scss">
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

// 统计卡：自适应列宽，最窄 230px
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 14px;
}

.chart-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 14px;
}

.activity-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
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

.chart-container {
  width: 100%;
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
  }
}

/* ---------- 分布环形图 ---------- */
.dist-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding-bottom: 18px;
}

.donut-wrap {
  position: relative;
  width: 170px;
  max-width: 100%;

  .donut {
    width: 170px;
    height: 170px;
  }

  .donut-center {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    pointer-events: none;

    .total {
      font-size: 28px;
      font-weight: 600;
      line-height: 1;
      color: var(--text-color);
    }

    .total-label {
      font-size: 12px;
      color: rgba(60, 60, 67, 0.65);
      margin-top: 4px;
    }
  }
}

.dist-legend {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;

  li {
    display: flex;
    align-items: center;
    gap: 9px;
    font-size: 13px;
  }

  .swatch {
    width: 9px;
    height: 9px;
    border-radius: 3px;
    display: block;
    flex-shrink: 0;
  }

  .name {
    color: var(--fill-grey-fg);
  }

  .count {
    margin-left: auto;
    font-weight: 600;
    color: var(--text-color);
  }

  .pct {
    color: rgba(60, 60, 67, 0.65);
    width: 38px;
    text-align: right;
  }
}

/* ---------- 最近登录表格 ---------- */
.table-body {
  padding-bottom: 8px;
  overflow-x: auto;
}

/* ---------- 待处理事项 ---------- */
.todo-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: 18px;
}

.todo-item {
  display: flex;
  gap: 12px;
  padding: 11px 10px;
  border-radius: var(--radius-md);
  transition: background 0.14s;
  cursor: pointer;

  &:hover {
    background: rgba(60, 60, 67, 0.04);
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
    margin-top: 6px;
  }

  .todo-main {
    min-width: 0;
    flex: 1;
  }

  .todo-content {
    color: var(--text-color);
    font-size: 13px;
    line-height: 1.5;
  }

  .todo-time {
    color: rgba(60, 60, 67, 0.65);
    font-size: 12px;
    margin-top: 3px;
  }

  .todo-arrow {
    flex-shrink: 0;
    margin-top: 6px;
    color: rgba(60, 60, 67, 0.28);
  }
}

/* ---------- 窄屏降级为单列 ---------- */
@media (max-width: 1100px) {
  .chart-grid,
  .activity-grid {
    grid-template-columns: 1fr;
  }
}
</style>
