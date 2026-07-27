import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import EnzymesPage from '@/pages/EnzymesPage.vue'
import ImportsPage from '@/pages/ImportsPage.vue'
import LibraryEntryPage from '@/pages/LibraryEntryPage.vue'
import LiteraturePage from '@/pages/LiteraturePage.vue'
import PredictionPage from '@/pages/PredictionPage.vue'
import MiniFoldPage from '@/pages/MiniFoldPage.vue'
import NvidiaFoldPage from '@/pages/NvidiaFoldPage.vue'
import TrRosettaRnaPage from '@/pages/TrRosettaRnaPage.vue'
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
    component: LibraryEntryPage,
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
      librarySourceType: 'PREDICTED',
      libraryTitle: '预测成果库',
      librarySubtitle: '展示你确认并正式入库的所有预测结果（包括 MiniFold、NVIDIA ESMFold、trRosettaRNA 等），独立于 Accession 导入条目。',
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
    meta: {
      aiConfigProvider: 'minifold',
    },
  },
  {
    path: '/prediction/nvidia',
    name: 'prediction-nvidia',
    component: NvidiaFoldPage,
    meta: {
      aiConfigProvider: 'nvidia',
    },
  },
  {
    path: '/prediction/trrosettarna',
    name: 'prediction-trrosettarna',
    component: TrRosettaRnaPage,
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
