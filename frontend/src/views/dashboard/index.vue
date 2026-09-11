<template>
  <div class="dashboard-container">
    <div class="page-header dashboard-header">
      <div>
        <h1 class="page-title">数据面板</h1>
        <div class="page-subtitle">汇总师生资源、问卷完成与风险预警，为日常管理提供统一视图。</div>
      </div>
      <el-radio-group v-model="trendPeriod" size="small">
        <el-radio-button value="week">本周</el-radio-button>
        <el-radio-button value="month">本月</el-radio-button>
        <el-radio-button value="year">全年</el-radio-button>
      </el-radio-group>
    </div>

    <el-row :gutter="16" class="metric-row">
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

    <div class="risk-overview section-card">
      <div>
        <div class="section-eyebrow">风险态势</div>
        <div class="risk-title">当前需重点关注 {{ stats.warningCount }} 人</div>
        <div class="risk-desc">
          结合心理问卷、学业表现与 AI 预警任务，优先处理高风险学生和待审核干预报告。
        </div>
      </div>
      <div class="risk-actions">
        <div class="risk-chip danger">高优先级排查</div>
        <div class="risk-chip warning">干预方案跟进</div>
        <div class="risk-chip info">数据持续更新</div>
      </div>
    </div>

    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="16">
        <el-card class="panel-card">
          <template #header>
            <div class="card-header">
              <span>师生增长趋势</span>
              <span class="card-hint">按所选周期统计新增趋势</span>
            </div>
          </template>
          <div ref="trendChartRef" class="chart-container" style="height: 350px"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card class="panel-card">
          <template #header>
            <div class="card-header">
              <span>师生分布</span>
              <span class="card-hint">教师与学生占比</span>
            </div>
          </template>
          <div ref="pieChartRef" class="chart-container" style="height: 350px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="activity-row">
      <el-col :xs="24" :lg="12">
        <el-card class="panel-card">
          <template #header>
            <div class="card-header">
              <span>最近登录</span>
              <span class="card-hint">平台访问记录</span>
            </div>
          </template>
          <el-table :data="recentLogins" stripe empty-text="暂无登录记录">
            <el-table-column prop="username" label="用户" />
            <el-table-column prop="role" label="角色" />
            <el-table-column prop="time" label="时间" />
            <el-table-column prop="ip" label="IP地址" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="panel-card">
          <template #header>
            <div class="card-header">
              <span>待处理事项</span>
              <span class="card-hint">建议按优先级处理</span>
            </div>
          </template>
          <el-timeline class="work-timeline">
            <el-timeline-item
              v-for="(activity, index) in activities"
              :key="index"
              :type="activity.type"
              :timestamp="activity.time"
            >
              <div class="activity-title">{{ activity.content }}</div>
              <div class="activity-desc">{{ activity.description }}</div>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import StatisticCard from './components/StatisticCard.vue'
import { graphic, init } from '@/utils/charts'
import { getStatistics, getTrendData, getDistributionData, getRecentLogins } from '@/api/dashboard'

// 统计数据
const stats = ref({
  teacherCount: 0,
  studentCount: 0,
  questionnaireCount: 0,
  warningCount: 0
})

// 图表相关
const trendChartRef = ref()
const pieChartRef = ref()
let trendChart = null
let pieChart = null
const trendPeriod = ref('month')

const trendData = ref({
  days: [],
  teacherData: [],
  studentData: []
})

const distributionData = ref([])

const toCount = (value) => Number(value) || 0

// 最近登录
const recentLogins = ref([])

// 待处理事项（暂用静态数据，后端无对应接口）
const activities = ref([
  {
    content: '心理预警名单待复核',
    description: '建议辅导员完成风险等级确认并记录处理意见',
    type: 'warning',
    time: '今日'
  },
  {
    content: '问卷完成率需跟进',
    description: '对未完成班级发起提醒，保障数据样本完整性',
    type: 'primary',
    time: '本周'
  },
  {
    content: 'AI 干预报告待审核',
    description: '合规审核通过后再进入教师跟进流程',
    type: 'success',
    time: '持续'
  },
  {
    content: '基础档案数据巡检',
    description: '关注缺失学院、专业、班级等关键画像字段',
    type: 'info',
    time: '每周'
  }
])

// 加载统计数据
const loadStatistics = async () => {
  try {
    const res = await getStatistics()
    if (res.data) {
      stats.value = {
        teacherCount: toCount(res.data.teacherCount),
        studentCount: toCount(res.data.studentCount),
        questionnaireCount: toCount(res.data.questionnaireCount),
        warningCount: toCount(res.data.warningCount)
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
    trendChart = init(trendChartRef.value)
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
          color: new graphic.LinearGradient(0, 0, 0, 1, [
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
          color: new graphic.LinearGradient(0, 0, 0, 1, [
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
    pieChart = init(pieChartRef.value)
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
        data: distributionData.value.map((item, index) => {
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
  .dashboard-header {
    align-items: center;
  }

  .metric-row {
    margin-bottom: 16px;
  }

  .risk-overview {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 18px;
    padding: 18px 20px;
    margin-bottom: 16px;
    background: linear-gradient(90deg, rgba(31, 95, 191, 0.08), rgba(31, 95, 191, 0)), #fff;

    .section-eyebrow {
      color: var(--primary-color);
      font-size: 12px;
      font-weight: 700;
    }

    .risk-title {
      margin-top: 6px;
      color: var(--text-color);
      font-size: 18px;
      font-weight: 700;
    }

    .risk-desc {
      margin-top: 6px;
      color: var(--text-color-secondary);
      line-height: 1.6;
    }

    .risk-actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 8px;
      min-width: 260px;
    }

    .risk-chip {
      padding: 7px 10px;
      border-radius: 4px;
      font-size: 12px;
      font-weight: 600;

      &.danger {
        color: #b42318;
        background: #fff1f0;
      }

      &.warning {
        color: #9a5b13;
        background: #fff7e6;
      }

      &.info {
        color: var(--primary-color);
        background: var(--primary-color-light);
      }
    }
  }

  .chart-row,
  .activity-row {
    margin-top: 16px;
  }

  .panel-card {
    height: 100%;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
  }

  .card-hint {
    color: var(--text-color-muted);
    font-size: 12px;
    font-weight: 400;
  }

  .chart-container {
    width: 100%;
  }

  .work-timeline {
    padding-top: 4px;

    .activity-title {
      color: var(--text-color);
      font-weight: 600;
      line-height: 1.4;
    }

    .activity-desc {
      margin-top: 4px;
      color: var(--text-color-secondary);
      font-size: 13px;
      line-height: 1.5;
    }
  }
}

@media (max-width: 900px) {
  .dashboard-container {
    .dashboard-header,
    .risk-overview {
      align-items: stretch;
      flex-direction: column;
    }

    .risk-overview .risk-actions {
      justify-content: flex-start;
      min-width: 0;
    }
  }
}
</style>
