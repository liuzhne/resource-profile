<template>
  <div class="page-container mental-analysis-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">心理分析报告</h1>
        <div class="page-subtitle">
          从学院、年级、性别维度分析心理状态分布，并沉淀重点人群风险清单。
        </div>
      </div>
      <el-button type="primary" plain :icon="Download">导出报告</el-button>
    </div>

    <div class="analysis-grid">
      <el-card class="section-card chart-card">
        <template #header>
          <div class="card-header">
            <span>各学院心理状况分布</span>
            <span class="header-hint">良好 / 关注 / 干预</span>
          </div>
        </template>
        <div ref="deptChartRef" class="chart-box"></div>
      </el-card>

      <el-card class="section-card chart-card">
        <template #header>
          <div class="card-header">
            <span>各年级心理状态对比</span>
            <span class="header-hint">良好率</span>
          </div>
        </template>
        <div ref="gradeChartRef" class="chart-box"></div>
      </el-card>

      <el-card class="section-card chart-card">
        <template #header>
          <div class="card-header">
            <span>性别差异分析</span>
            <span class="header-hint">按风险层级</span>
          </div>
        </template>
        <div ref="genderChartRef" class="chart-box"></div>
      </el-card>
    </div>

    <el-card class="table-card focus-card">
      <template #header>
        <div class="card-header">
          <div>
            <span>重点人群分析</span>
            <span class="result-count">共 {{ focusGroups.length }} 类</span>
          </div>
          <span class="header-hint">用于制定分层干预策略</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="focusGroups" stripe empty-text="暂无重点人群数据">
        <el-table-column type="index" width="56" />
        <el-table-column prop="group" label="人群类型" min-width="220">
          <template #default="{ row }">
            <div class="group-name">{{ row.group || '-' }}</div>
            <div class="group-desc">建议纳入专项跟进名单</div>
          </template>
        </el-table-column>
        <el-table-column prop="count" label="人数" width="120" align="center">
          <template #default="{ row }">
            <strong class="count-value">{{ row.count || 0 }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="risk" label="风险等级" width="130">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.risk)" effect="plain">
              {{ row.risk || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="建议动作" min-width="260">
          <template #default="{ row }">{{ actionSuggestion(row.risk) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { Download } from '@element-plus/icons-vue'
import { getMentalAnalysis } from '@/api/mental'
import { init } from '@/utils/charts'

const deptChartRef = ref()
const gradeChartRef = ref()
const genderChartRef = ref()

let deptChart = null
let gradeChart = null
let genderChart = null

const loading = ref(false)
const focusGroups = ref([])

const chartPalette = {
  good: '#2f9e44',
  attention: '#d9822b',
  intervention: '#d64545',
  male: '#1f5fbf',
  female: '#c2417d'
}

const baseGrid = {
  left: 10,
  right: 16,
  top: 44,
  bottom: 12,
  containLabel: true
}

const initDeptChart = (data) => {
  if (!deptChartRef.value) return
  if (!deptChart) deptChart = init(deptChartRef.value)

  let totalGood = 0
  let totalAttention = 0
  let totalIntervention = 0
  data.forEach((item) => {
    totalGood += Number(item.good) || 0
    totalAttention += Number(item.attention) || 0
    totalIntervention += Number(item.intervention) || 0
  })

  deptChart.setOption({
    color: [chartPalette.good, chartPalette.attention, chartPalette.intervention],
    tooltip: { trigger: 'item' },
    legend: {
      bottom: 0,
      left: 'center',
      itemWidth: 10,
      itemHeight: 10
    },
    series: [
      {
        name: '状态分布',
        type: 'pie',
        radius: ['46%', '70%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        label: { formatter: '{b}\n{d}%' },
        data: [
          { value: totalGood, name: '良好' },
          { value: totalAttention, name: '关注' },
          { value: totalIntervention, name: '干预' }
        ]
      }
    ]
  })
}

const initGradeChart = (data) => {
  if (!gradeChartRef.value) return
  if (!gradeChart) gradeChart = init(gradeChartRef.value)

  const grades = data.map((item) => item.grade)
  const rates = data.map((item) => Number(item.rate) || 0)
  const barColors = rates.map((rate) =>
    rate >= 80 ? chartPalette.good : rate >= 60 ? chartPalette.attention : chartPalette.intervention
  )

  gradeChart.setOption({
    tooltip: { trigger: 'axis', valueFormatter: (value) => `${value}%` },
    grid: baseGrid,
    xAxis: {
      type: 'category',
      data: grades,
      axisLine: { lineStyle: { color: '#d8dee8' } },
      axisLabel: { color: '#667085' }
    },
    yAxis: {
      type: 'value',
      max: 100,
      axisLabel: { formatter: '{value}%', color: '#667085' },
      splitLine: { lineStyle: { color: '#edf1f7' } }
    },
    series: [
      {
        name: '良好率',
        type: 'bar',
        barWidth: 28,
        data: rates.map((value, index) => ({
          value,
          itemStyle: { color: barColors[index], borderRadius: [4, 4, 0, 0] }
        }))
      }
    ]
  })
}

const initGenderChart = (data) => {
  if (!genderChartRef.value) return
  if (!genderChart) genderChart = init(genderChartRef.value)

  const genderLabels = { 0: '女生', 1: '男生' }
  const genderColors = { 0: chartPalette.female, 1: chartPalette.male }
  const series = data.map((item) => {
    const gender = Number(item.gender)
    return {
      name: genderLabels[gender] || `性别${gender}`,
      type: 'bar',
      barWidth: 24,
      data: [Number(item.good) || 0, Number(item.attention) || 0, Number(item.intervention) || 0],
      itemStyle: {
        color: genderColors[gender] || '#64748b',
        borderRadius: [4, 4, 0, 0]
      }
    }
  })

  genderChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, right: 0, data: series.map((item) => item.name) },
    grid: baseGrid,
    xAxis: {
      type: 'category',
      data: ['良好', '关注', '干预'],
      axisLine: { lineStyle: { color: '#d8dee8' } },
      axisLabel: { color: '#667085' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#edf1f7' } },
      axisLabel: { color: '#667085' }
    },
    series
  })
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getMentalAnalysis()
    const data = res.data || {}
    focusGroups.value = data.focusGroups || []
    initDeptChart(data.deptDistribution || [])
    initGradeChart(data.gradeComparison || [])
    initGenderChart(data.genderAnalysis || [])
  } catch (e) {
    console.error('获取心理分析数据失败', e)
  } finally {
    loading.value = false
  }
}

const riskTagType = (risk) => ({ 高: 'danger', 中: 'warning', 低: 'success' })[risk] || 'info'

const actionSuggestion = (risk) =>
  ({
    高: '建立一对一跟进台账，并同步辅导员与心理中心。',
    中: '纳入阶段性观察名单，结合问卷结果安排回访。',
    低: '保持常规关注，结合班级活动持续观察。'
  })[risk] || '根据实际情况补充跟进策略。'

const resizeCharts = () => {
  deptChart?.resize()
  gradeChart?.resize()
  genderChart?.resize()
}

onMounted(() => {
  fetchData()
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts)
  deptChart?.dispose()
  gradeChart?.dispose()
  genderChart?.dispose()
})
</script>

<style scoped lang="scss">
.analysis-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.chart-card {
  :deep(.el-card__body) {
    padding-top: 0;
  }
}

.chart-box {
  height: 320px;
}

.focus-card {
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

.group-name {
  color: var(--text-color);
  font-weight: 650;
  line-height: 1.5;
}

.group-desc {
  color: var(--text-color-muted);
  font-size: 12px;
}

.count-value {
  color: var(--primary-color);
  font-size: 16px;
}

@media (max-width: 1200px) {
  .analysis-grid {
    grid-template-columns: 1fr;
  }
}
</style>
