<template>
  <div class="dashboard-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6">
        <StatisticCard
          title="教师总数"
          :value="stats.teacherCount"
          icon="UserFilled"
          color="#1890ff"
          suffix="人"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <StatisticCard
          title="学生总数"
          :value="stats.studentCount"
          icon="Reading"
          color="#52c41a"
          suffix="人"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <StatisticCard
          title="今日问卷完成"
          :value="stats.questionnaireCount"
          icon="DocumentChecked"
          color="#faad14"
          suffix="份"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <StatisticCard
          title="预警关注"
          :value="stats.warningCount"
          icon="WarningFilled"
          color="#f5222d"
          suffix="人"
        />
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>师生增长趋势</span>
              <el-radio-group v-model="trendPeriod" size="small">
                <el-radio-button label="week">本周</el-radio-button>
                <el-radio-button label="month">本月</el-radio-button>
                <el-radio-button label="year">全年</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="trendChartRef" class="chart-container" style="height: 350px"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>师生分布</span>
            </div>
          </template>
          <div ref="pieChartRef" class="chart-container" style="height: 350px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近活动 -->
    <el-row :gutter="16" class="activity-row">
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>
            <span>最近登录</span>
          </template>
          <el-table :data="recentLogins" stripe>
            <el-table-column prop="username" label="用户" />
            <el-table-column prop="role" label="角色" />
            <el-table-column prop="time" label="时间" />
            <el-table-column prop="ip" label="IP地址" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>
            <span>待处理事项</span>
          </template>
          <el-timeline>
            <el-timeline-item
              v-for="(activity, index) in activities"
              :key="index"
              :type="activity.type"
              :timestamp="activity.time"
            >
              {{ activity.content }}
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import StatisticCard from './components/StatisticCard.vue'
import { getStatistics, getTrendData, getDistributionData, getRecentLogins } from '@/api/dashboard'

// 统计数据
const stats = ref({
  teacherCount: 0,
  studentCount: 0,
  questionnaireCount: 0,
  warningCount: 0
})

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

// 最近登录
const recentLogins = ref([] as { username: string; role: string; time: string; ip: string }[])

// 待处理事项（暂用静态数据，后端无对应接口）
const activities = ref([
  { content: '新增5份心理健康预警', type: 'warning', time: '10分钟前' },
  { content: '张三老师提交了科研项目', type: 'success', time: '30分钟前' },
  { content: '系统检测到异常登录', type: 'danger', time: '1小时前' },
  { content: '李四同学完成了心理问卷', type: 'info', time: '2小时前' }
])

// 加载统计数据
const loadStatistics = async () => {
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
  }
}

// 加载趋势数据
const loadTrendData = async () => {
  try {
    const res = await getTrendData(trendPeriod.value)
    if (res.data) {
      trendData.value = {
        days: res.data.days || [],
        teacherData: res.data.teacherData || [],
        studentData: res.data.studentData || []
      }
      updateTrendChart()
    }
  } catch (error) {
    console.error('加载趋势数据失败', error)
  }
}

// 加载分布数据
const loadDistributionData = async () => {
  try {
    const res = await getDistributionData()
    if (res.data?.data) {
      distributionData.value = res.data.data
      updatePieChart()
    }
  } catch (error) {
    console.error('加载分布数据失败', error)
  }
}

// 加载最近登录
const loadRecentLogins = async () => {
  try {
    const res = await getRecentLogins()
    if (res.data) {
      recentLogins.value = res.data
    }
  } catch (error) {
    console.error('加载最近登录失败', error)
  }
}

// 初始化/更新趋势图
const updateTrendChart = () => {
  if (!trendChartRef.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  const option = {
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: ['教师', '学生']
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trendData.value.days
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '教师',
        type: 'line',
        smooth: true,
        data: trendData.value.teacherData,
        itemStyle: { color: '#1890ff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(24, 144, 255, 0.3)' },
            { offset: 1, color: 'rgba(24, 144, 255, 0.05)' }
          ])
        }
      },
      {
        name: '学生',
        type: 'line',
        smooth: true,
        data: trendData.value.studentData,
        itemStyle: { color: '#52c41a' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(82, 196, 26, 0.3)' },
            { offset: 1, color: 'rgba(82, 196, 26, 0.05)' }
          ])
        }
      }
    ]
  }
  trendChart.setOption(option)
}

// 初始化/更新饼图
const updatePieChart = () => {
  if (!pieChartRef.value) return
  if (!pieChart) {
    pieChart = echarts.init(pieChartRef.value)
  }
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: '5%',
      top: 'center'
    },
    series: [
      {
        name: '分布',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        data: distributionData.value.map((item: any, index: number) => {
          const colors = ['#1890ff', '#52c41a', '#faad14', '#f5222d', '#722ed1']
          return {
            ...item,
            itemStyle: { color: colors[index % colors.length] }
          }
        })
      }
    ]
  }
  pieChart.setOption(option)
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
  .chart-row,
  .activity-row {
    margin-top: 16px;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .chart-container {
    width: 100%;
  }
}
</style>
