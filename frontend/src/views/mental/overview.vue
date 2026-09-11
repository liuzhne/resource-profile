<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card>
          <div class="stat-item">
            <div class="stat-icon" style="background: #f6ffed; color: #52c41a">
              <el-icon :size="32"><Smile /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value" style="color: #52c41a">{{ overviewData.goodRate }}%</div>
              <div class="stat-label">心理状态良好</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card>
          <div class="stat-item">
            <div class="stat-icon" style="background: #fff7e6; color: #faad14">
              <el-icon :size="32"><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value" style="color: #faad14">{{ overviewData.attentionRate }}%</div>
              <div class="stat-label">需要关注</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card>
          <div class="stat-item">
            <div class="stat-icon" style="background: #fff1f0; color: #f5222d">
              <el-icon :size="32"><BellFilled /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value" style="color: #f5222d">
                {{ overviewData.interventionRate }}%
              </div>
              <div class="stat-label">需要干预</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card>
          <div class="stat-item">
            <div class="stat-icon" style="background: #e6f7ff; color: #1890ff">
              <el-icon :size="32"><DocumentChecked /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value" style="color: #1890ff">{{ overviewData.todayCompleted }}</div>
              <div class="stat-label">今日完成问卷</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 预警列表 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span
                ><el-icon><WarningFilled /></el-icon> 心理预警</span
              >
              <el-button type="primary" link>查看全部</el-button>
            </div>
          </template>
          <el-table :data="warningList" stripe>
            <el-table-column prop="name" label="姓名" />
            <el-table-column prop="dept" label="学院" />
            <el-table-column prop="level" label="预警级别" width="100">
              <template #default="{ row }">
                <el-tag :type="row.level === '高危' ? 'danger' : 'warning'">{{ row.level }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="time" label="时间" width="120" />
            <el-table-column label="操作" width="80">
              <template #default>
                <el-button link type="primary">处理</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span
                ><el-icon><TrendCharts /></el-icon> 趋势分析</span
              >
            </div>
          </template>
          <div ref="trendChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getMentalOverview } from '@/api/mental'

const trendChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null

const overviewData = reactive({
  goodRate: 0,
  attentionRate: 0,
  interventionRate: 0,
  todayCompleted: 0
})

const warningList = ref<any[]>([])
const trendData = ref<any[]>([])

const initTrendChart = () => {
  if (!trendChartRef.value) return

  trendChart = echarts.init(trendChartRef.value)

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

  const option = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['良好', '关注', '干预'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: months
    },
    yAxis: { type: 'value' },
    series: [
      {
        name: '良好',
        type: 'line',
        data: goodData,
        itemStyle: { color: '#52c41a' }
      },
      {
        name: '关注',
        type: 'line',
        data: attentionData,
        itemStyle: { color: '#faad14' }
      },
      {
        name: '干预',
        type: 'line',
        data: interventionData,
        itemStyle: { color: '#f5222d' }
      }
    ]
  }
  trendChart.setOption(option)
}

const fetchData = async () => {
  try {
    const res = await getMentalOverview()
    const data = res.data
    overviewData.goodRate = data.goodRate || 0
    overviewData.attentionRate = data.attentionRate || 0
    overviewData.interventionRate = data.interventionRate || 0
    overviewData.todayCompleted = data.todayCompleted || 0
    warningList.value = data.warningList || []
    trendData.value = data.trendData || []
    initTrendChart()
  } catch (e) {
    console.error('获取心理概览数据失败', e)
  }
}

onMounted(() => {
  fetchData()
})

onUnmounted(() => {
  trendChart?.dispose()
})
</script>

<style scoped lang="scss">
.stat-item {
  display: flex;
  align-items: center;

  .stat-icon {
    width: 60px;
    height: 60px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: 16px;
  }

  .stat-info {
    .stat-value {
      font-size: 28px;
      font-weight: 600;
      line-height: 1;
    }

    .stat-label {
      margin-top: 8px;
      color: rgba(0, 0, 0, 0.45);
    }
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .el-icon {
    margin-right: 4px;
  }
}
</style>
