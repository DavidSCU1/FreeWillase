import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import EnzymesPage from '@/pages/EnzymesPage.vue'
import ImportsPage from '@/pages/ImportsPage.vue'
import LiteraturePage from '@/pages/LiteraturePage.vue'
import PredictionPage from '@/pages/PredictionPage.vue'
import LoginPage from '@/pages/LoginPage.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: LoginPage,
  },
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: HomePage,
  },
  {
    path: '/importer',
    name: 'importer',
    component: ImportsPage,
  },
  {
    path: '/library',
    name: 'library',
    component: EnzymesPage,
  },
  {
    path: '/matcher',
    name: 'matcher',
    component: LiteraturePage,
  },
  {
    path: '/prediction',
    name: 'prediction',
    component: PredictionPage,
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    return { top: 0, left: 0 }
  },
})

router.beforeEach((to, from, next) => {
  const publicPages = ['/login']
  const authRequired = !publicPages.includes(to.path)
  const loggedIn = localStorage.getItem('token')

  if (authRequired && !loggedIn) {
    return next('/login')
  }

  next()
})

export default router
