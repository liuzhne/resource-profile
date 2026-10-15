import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import permissionDirective from './directives/permission'
import { installServiceWarmup } from './utils/prewarm'
import { wakeServices } from './utils/warmup'
import { installAppIcons } from './icons'

// 显式调用的服务组件不会触发模板按需导入，需加载其全局样式及依赖。
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/message/style/css'
import './styles/index.scss'

const app = createApp(App)

installAppIcons(app)

app.use(createPinia())
app.use(router)
app.use(permissionDirective)

// Only deployments that explicitly enable sleeping services need background wakeups.
if (import.meta.env.VITE_ENABLE_SERVICE_WARMUP === 'true') {
  installServiceWarmup(router, wakeServices)
}

app.mount('#app')
