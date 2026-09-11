<template>
  <div class="page-container mental-overview-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">心理健康概览</h1>
        <div class="page-subtitle">
          汇总学生心理测评、预警名单与趋势变化，辅助辅导员快速识别重点关注对象。
        </div>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="fetchData"> 刷新 </el-button>
    </div>

    <div class="metric-grid">
      <div class="metric-card is-success">
        <span>心理状态良好</span>
        <strong>{{ overviewData.goodRate }}%</strong>
        <p>正常及轻度状态占比</p>
      </div>
      <div class="metric-card is-warning">
        <span>需要关注</span>
        <strong>{{ overviewData.attentionRate }}%</strong>
        <p>中度风险学生占比</p>
      </div>
      <div class="metric-card is-danger">
        <span>需要干预</span>
        <strong>{{ overviewData.interventionRate }}%</strong>
        <p>重度及高危学生占比</p>
      </div>
      <div class="metric-card is-primary">
        <span>今日完成问卷</span>
        <strong>{{ overviewData.todayCompleted }}</strong>
        <p>当日新增测评记录</p>
      </div>
    </div>

    <div class="overview-content">
      <el-card v-loading="loading" class="table-card warning-card">
        <template #header>
          <div class="card-header">
            <div>
              <span>心理预警名单</span>
              <span class="result-count">共 {{ warningList.length }} 条</span>
            </div>
            <span class="header-hint">建议按风险等级优先处理</span>
          </div>
        </template>

        <el-table :data="warningList" stripe empty-text="暂无心理预警记录">
          <el-table-column prop="name" label="学生" min-width="120">
            <template #default="{ row }">
              <div class="student-name">{{ row.name || '-' }}</div>
              <div class="student-meta">{{ row.dept || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="level" label="预警级别" width="110">
            <template #default="{ row }">
              <el-tag :type="warningLevelType(row.level)" effect="plain">
                {{ row.level || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="time" label="时间" width="130">
            <template #default="{ row }">{{ row.time || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default>
              <el-button link type="primary">处理</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card v-loading="loading" class="section-card trend-card">
        <template #header>
          <div class="card-header">
            <div>
              <span>心理状态趋势</span>
              <span class="result-count">按月份汇总</span>
            </div>
          </div>
        </template>
        <div ref="trendChartRef" class="chart-box"></div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getMentalOverview } from '@/api/mental'
import { init } from '@/utils/charts'

const loading = ref(false)
const trendChartRef = ref()
let trendChart = null

const overviewData = reactive({
  goodRate: 0,
  attentionRate: 0,
  interventionRate: 0,
  todayCompleted: 0
})

const warningList = ref([])
const trendData = ref([])

const initTrendChart = () => {
  if (!trendChartRef.value) return

  if (!trendChart) {
    trendChart = init(trendChartRef.value)
  }

  const months = [...new Set(trendData.value.map((item) => item.month))]
  const goodData = []
  const attentionData = []
  const interventionData = []

  months.forEach((month) => {
    const monthItems = trendData.value.filter((item) => item.month === month)
    let good = 0
    let attention = 0
    let intervention = 0
    monthItems.forEach((item) => {
      const count = Number(item.count) || 0
      if (item.level === '正常' || item.level === '轻度') good += count
      else if (item.level === '中度') attention += count
      else if (item.level === '重度' || item.level === '高危') intervention += count
    })
    goodData.push(good)
    attentionData.push(attention)
    interventionData.push(intervention)
  })

  trendChart.setOption({
    color: ['#2f9e44', '#d9822b', '#d64545'],
    tooltip: { trigger: 'axis' },
    legend: {
      top: 0,
      right: 0,
      data: ['良好', '关注', '干预']
    },
    grid: { left: 10, right: 20, top: 48, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: months,
      axisLine: { lineStyle: { color: '#d8dee8' } },
      axisLabel: { color: '#667085' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#edf1f7' } },
      axisLabel: { color: '#667085' }
    },
    series: [
      {
        name: '良好',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        data: goodData
      },
      {
        name: '关注',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        data: attentionData
      },
      {
        name: '干预',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        data: interventionData
      }
    ]
  })
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getMentalOverview()
    const data = res.data || {}
    overviewData.goodRate = data.goodRate || 0
    overviewData.attentionRate = data.attentionRate || 0
    overviewData.interventionRate = data.interventionRate || 0
    overviewData.todayCompleted = data.todayCompleted || 0
    warningList.value = data.warningList || []
    trendData.value = data.trendData || []
    initTrendChart()
  } catch (e) {
    console.error('获取心理概览数据失败', e)
  } finally {
    loading.value = false
  }
}

const warningLevelType = (level) =>
  ({ 高危: 'danger', 重度: 'danger', 中度: 'warning', 轻度: 'success' })[level] || 'info'

const resizeChart = () => {
  trendChart?.resize()
}

onMounted(() => {
  fetchData()
  window.addEventListener('resize', resizeChart)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeChart)
  trendChart?.dispose()
})
</script>

<style scoped lang="scss">
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.metric-card {
  min-height: 118px;
  padding: 18px;
  background: var(--bg-color-container);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-1);

  span {
    display: block;
    color: var(--text-color-secondary);
    font-size: 13px;
  }

  strong {
    display: block;
    margin-top: 10px;
    color: var(--text-color);
    font-size: 28px;
    line-height: 1;
  }

  p {
    margin: 12px 0 0;
    color: var(--text-color-muted);
    font-size: 12px;
    line-height: 1.5;
  }

  &.is-success strong {
    color: #2f9e44;
  }

  &.is-warning strong {
    color: #d9822b;
  }

  &.is-danger strong {
    color: #d64545;
  }

  &.is-primary strong {
    color: var(--primary-color);
  }
}

.overview-content {
  display: grid;
  grid-template-columns: minmax(420px, 0.95fr) minmax(0, 1.25fr);
  gap: 14px;
}

.warning-card,
.trend-card {
  :deep(.el-card__body) {
    padding-top: 0;
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  .result-count,
  .header-hint {
    color: var(--text-color-muted);
    font-size: 12px;
    font-weight: 400;
  }

  .result-count {
    margin-left: 10px;
  }
}

.student-name {
  color: var(--text-color);
  font-weight: 650;
  line-height: 1.5;
}

.student-meta {
  color: var(--text-color-muted);
  font-size: 12px;
}

.chart-box {
  height: 338px;
}

@media (max-width: 1200px) {
  .metric-grid,
  .overview-content {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .metric-grid,
  .overview-content {
    grid-template-columns: 1fr;
  }
}
</style>
