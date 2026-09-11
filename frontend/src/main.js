import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import permissionDirective from './directives/permission'
import {
  ArrowDown,
  Avatar,
  Bell,
  BellFilled,
  CircleCloseFilled,
  Close,
  DataLine,
  DocumentChecked,
  DocumentCopy,
  EditPen,
  Expand,
  FirstAidKit,
  Fold,
  Loading,
  Lock,
  MagicStick,
  Plus,
  Reading,
  Refresh,
  Setting,
  CircleCheck,
  SuccessFilled,
  SwitchButton,
  TrendCharts,
  User,
  UserFilled,
  Warning,
  WarningFilled
} from '@element-plus/icons-vue'

import './styles/index.scss'

const app = createApp(App)

const icons = {
  ArrowDown,
  Avatar,
  Bell,
  BellFilled,
  CircleCloseFilled,
  Close,
  DataLine,
  DocumentChecked,
  DocumentCopy,
  EditPen,
  Expand,
  FirstAidKit,
  Fold,
  Loading,
  Lock,
  MagicStick,
  Plus,
  Reading,
  Refresh,
  Setting,
  CircleCheck,
  SuccessFilled,
  SwitchButton,
  TrendCharts,
  User,
  UserFilled,
  Warning,
  WarningFilled
}

Object.entries(icons).forEach(([name, component]) => {
  app.component(name, component)
})

app.use(createPinia())
app.use(router)
app.use(permissionDirective)

app.mount('#app')
