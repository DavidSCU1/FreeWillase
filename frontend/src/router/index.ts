import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import EnzymesPage from '@/pages/EnzymesPage.vue'
import ImportsPage from '@/pages/ImportsPage.vue'
import LiteraturePage from '@/pages/LiteraturePage.vue'
import PredictionPage from '@/pages/PredictionPage.vue'
import MiniFoldPage from '@/pages/MiniFoldPage.vue'
import NvidiaFoldPage from '@/pages/NvidiaFoldPage.vue'
import RNAFoldPage from '@/pages/RNAFoldPage.vue'
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
    redirect: '/library/imported',
  },
  {
    path: '/library/imported',
    name: 'library-imported',
    component: EnzymesPage,
    meta: {
      librarySourceType: 'NCBI_IMPORT',
      libraryTitle: '导入酶库',
      librarySubtitle: '只展示由 accession 导入的酶条目，方便继续看结构、补文献和做后续整理。',
    },
  },
  {
    path: '/library/predicted',
    name: 'library-predicted',
    component: EnzymesPage,
    meta: {
      librarySourceType: 'MINIFOLD_PREDICTION',
      libraryTitle: '预测成果库',
      librarySubtitle: '只展示已经过你确认并正式入库的 MiniFold 预测结果，不和 accession 导入条目混放。',
    },
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
  {
    path: '/prediction/minifold',
    name: 'prediction-minifold',
    component: MiniFoldPage,
  },
  {
    path: '/prediction/nvidia',
    name: 'prediction-nvidia',
    component: NvidiaFoldPage,
  },
  {
    path: '/prediction/rnafold',
    name: 'prediction-rnafold',
    component: RNAFoldPage,
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
