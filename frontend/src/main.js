import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import permissionDirective from './directives/permission'
import { installServiceWarmup } from './utils/prewarm'
import { wakeServices } from './utils/warmup'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import './styles/index.scss'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(permissionDirective)

// Render 免费层各服务独立休眠：打开网站即并行唤醒，使用期间保持清醒（详见 utils/prewarm.js）
installServiceWarmup(router, wakeServices)

app.mount('#app')
