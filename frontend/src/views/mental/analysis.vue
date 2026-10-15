<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>心理分析报告</span>
          <el-button type="primary" :loading="exporting" @click="exportReport">导出报告</el-button>
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
import { getMentalAnalysis, exportMentalAnalysis } from '@/api/mental'
import { ElMessage } from 'element-plus'

const deptChartRef = ref<HTMLElement>()
const gradeChartRef = ref<HTMLElement>()
const genderChartRef = ref<HTMLElement>()

let deptChart: echarts.ECharts | null = null
let gradeChart: echarts.ECharts | null = null
let genderChart: echarts.ECharts | null = null

const loading = ref(false)
const exporting = ref(false)
const focusGroups = ref<any[]>([])

const initDeptChart = (data: any[]) => {
  if (!deptChartRef.value) return
  deptChart = echarts.init(deptChartRef.value)

  deptChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['良好', '关注', '干预'] },
    grid: { bottom: 55, containLabel: true },
    xAxis: {
      type: 'category',
      data: data.map((row) => row.dept || '未提供学院'),
      axisLabel: { rotate: 20 }
    },
    yAxis: { type: 'value', name: '测评记录数' },
    series: [
      ['good', '良好', '#34c759'],
      ['attention', '关注', '#ff9500'],
      ['intervention', '干预', '#ff3b30']
    ].map(([key, name, color]) => ({
      name,
      type: 'bar',
      stack: 'assessments',
      data: data.map((row) => Number(row[key]) || 0),
      itemStyle: { color }
    }))
  })
}

const exportReport = async () => {
  exporting.value = true
  try {
    const response = await exportMentalAnalysis()
    if (
      !(response.data instanceof Blob) ||
      !String(response.headers['content-type']).startsWith('text/csv')
    )
      throw new Error('报告未生成，请刷新后重试')
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = '心理分析报告.csv'
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(error.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

const initGradeChart = (data: any[]) => {
  if (!gradeChartRef.value) return
  gradeChart = echarts.init(gradeChartRef.value)

  const grades = data.map((item: any) => item.grade)
  const rates = data.map((item: any) => Number(item.rate))
  const barColors = rates.map((r) => (r >= 80 ? '#34c759' : r >= 60 ? '#ff9500' : '#ff3b30'))

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
  const genderColors: Record<number, string> = { 0: '#f08ca8', 1: '#007aff' }

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
