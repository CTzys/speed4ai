import { createRouter, createWebHistory } from 'vue-router'
import { hasSession } from './api'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, top: 25 }
    return { top: 0 }
  },
  routes: [
    { path: '/', component: () => import('./views/LandingView.vue') },
    { path: '/login', component: () => import('./views/LoginView.vue') },
    { path: '/register', component: () => import('./views/RegisterView.vue') },
    { path: '/dashboard', component: () => import('./views/DashboardView.vue'), meta: { auth: true } },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach((to) => {
  if (import.meta.env.DEV && to.path === '/dashboard' && to.query.preview === '1') return
  if (to.meta.auth && !hasSession()) return '/login'
  if ((to.path === '/login' || to.path === '/register') && hasSession()) return '/dashboard'
})

router.afterEach((to) => {
  document.title = to.path === '/' ? 'SpeedNet · 连接更轻松，探索更多可能' : 'SpeedNet 客户中心'
})

export default router
