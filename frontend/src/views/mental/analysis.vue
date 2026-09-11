<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>心理分析报告</span>
          <el-button type="primary">导出报告</el-button>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :xs="24" :lg="8">
          <h4>各学院心理状况分布</h4>
          <div ref="deptChartRef" style="height: 300px; margin-top: 16px"></div>
        </el-col>
        <el-col :xs="24" :lg="8">
          <h4>各年级心理状况对比</h4>
          <div ref="gradeChartRef" style="height: 300px; margin-top: 16px"></div>
        </el-col>
        <el-col :xs="24" :lg="8">
          <h4>性别差异分析</h4>
          <div ref="genderChartRef" style="height: 300px; margin-top: 16px"></div>
        </el-col>
      </el-row>

      <el-divider />

      <h4>重点人群分析</h4>
      <el-table v-loading="loading" :data="focusGroups" stripe style="margin-top: 16px">
        <el-table-column type="index" width="50" />
        <el-table-column prop="group" label="人群类型" />
        <el-table-column prop="count" label="人数" width="100" />
        <el-table-column prop="risk" label="风险等级" width="120">
          <template #default="{ row }">
            <el-tag
              :type="row.risk === '高' ? 'danger' : row.risk === '中' ? 'warning' : 'success'"
            >
              {{ row.risk }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getMentalAnalysis } from '@/api/mental'

const deptChartRef = ref<HTMLElement>()
const gradeChartRef = ref<HTMLElement>()
const genderChartRef = ref<HTMLElement>()

let deptChart: echarts.ECharts | null = null
let gradeChart: echarts.ECharts | null = null
let genderChart: echarts.ECharts | null = null

const loading = ref(false)
const focusGroups = ref<any[]>([])

const initDeptChart = (data: any[]) => {
  if (!deptChartRef.value) return
  deptChart = echarts.init(deptChartRef.value)

  // 汇总各学院数据为饼图
  let totalGood = 0,
    totalAttention = 0,
    totalIntervention = 0
  data.forEach((item: any) => {
    totalGood += Number(item.good) || 0
    totalAttention += Number(item.attention) || 0
    totalIntervention += Number(item.intervention) || 0
  })

  deptChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', right: '5%', top: 'center' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        data: [
          { value: totalGood, name: '良好', itemStyle: { color: '#52c41a' } },
          { value: totalAttention, name: '关注', itemStyle: { color: '#faad14' } },
          { value: totalIntervention, name: '干预', itemStyle: { color: '#f5222d' } }
        ]
      }
    ]
  })
}

const initGradeChart = (data: any[]) => {
  if (!gradeChartRef.value) return
  gradeChart = echarts.init(gradeChartRef.value)

  const grades = data.map((item: any) => item.grade)
  const rates = data.map((item: any) => Number(item.rate))
  const barColors = rates.map((r) => (r >= 80 ? '#52c41a' : r >= 60 ? '#faad14' : '#f5222d'))

  gradeChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: grades },
    yAxis: { type: 'value', max: 100 },
    series: [
      {
        type: 'bar',
        data: rates.map((v, i) => ({ value: v, itemStyle: { color: barColors[i] } }))
      }
    ]
  })
}

const initGenderChart = (data: any[]) => {
  if (!genderChartRef.value) return
  genderChart = echarts.init(genderChartRef.value)

  const series: any[] = []
  const genderLabels: Record<number, string> = { 0: '女生', 1: '男生' }
  const genderColors: Record<number, string> = { 0: '#eb2f96', 1: '#1890ff' }

  data.forEach((item: any) => {
    const gender = Number(item.gender)
    series.push({
      name: genderLabels[gender] || `性别${gender}`,
      type: 'bar',
      data: [Number(item.good) || 0, Number(item.attention) || 0, Number(item.intervention) || 0],
      itemStyle: { color: genderColors[gender] || '#999' }
    })
  })

  genderChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: series.map((s) => s.name) },
    xAxis: { type: 'category', data: ['良好', '关注', '干预'] },
    yAxis: { type: 'value' },
    series
  })
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getMentalAnalysis()
    const data = res.data
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

onMounted(() => {
  fetchData()
})

onUnmounted(() => {
  deptChart?.dispose()
  gradeChart?.dispose()
  genderChart?.dispose()
})
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

h4 {
  font-size: 16px;
  color: rgba(0, 0, 0, 0.85);
}
</style>
