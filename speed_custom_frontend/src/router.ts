import { createRouter, createWebHistory } from 'vue-router'
import { hasSession } from './api'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: () => import('./views/LoginView.vue') },
    { path: '/register', component: () => import('./views/RegisterView.vue') },
    { path: '/dashboard', component: () => import('./views/DashboardView.vue'), meta: { auth: true } },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach((to) => {
  if (import.meta.env.DEV && to.path === '/dashboard' && to.query.preview === '1') return
  if (to.meta.auth && !hasSession()) return '/login'
  if ((to.path === '/login' || to.path === '/register') && hasSession()) return '/dashboard'
})

export default router
