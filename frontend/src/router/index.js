import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { createRoutes } from './routes'
import { createAuthGuard } from './access'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false })

const aiEnabled = import.meta.env.VITE_AI_ENABLED === 'true'

const routes = createRoutes(aiEnabled)

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

const authGuard = createAuthGuard(useUserStore)

router.beforeEach((to) => {
  NProgress.start()
  return authGuard(to)
})

router.afterEach(() => {
  NProgress.done()
})

export { routes }
export default router
