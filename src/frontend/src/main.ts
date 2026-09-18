import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import LoginView from './views/LoginView.vue'
import StreamerView from './views/StreamerView.vue'
import AdminView from './views/AdminView.vue'
import ForbiddenView from './views/ForbiddenView.vue'
import CardDebugView from './views/CardDebugView.vue'
import AuthLandingView from './views/AuthLandingView.vue'
import { authUi, authenticateDingTalk, canAutoDingTalkLogin, clearAuthenticated, destinationFor, isSessionError, restoreSession, showAuthMessage } from './auth'
import './styles.css'
import './v02.css'
import './v03.css'
import './v04.css'
import './v05.css'
import './v06.css'
import './v07.css'
import './v08.css'
import './v09.css'
import './v10.css'
import './v11.css'
import './v12.css'
import './v13.css'
import './v14.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: AuthLandingView },
    { path: '/login', component: LoginView },
    { path: '/booking', component: StreamerView },
    { path: '/admin', component: AdminView },
    { path: '/forbidden', component: ForbiddenView },
    { path: '/mock/cards', component: CardDebugView },
  ],
})

router.beforeEach(async to => {
  const requested = typeof to.query.redirect === 'string' ? to.query.redirect : undefined
  if (to.path === '/forbidden') return true
  if (to.path === '/login') {
    if (sessionStorage.getItem('jiabei-explicit-logout') === '1') return true
    authUi.checking = true
    try {
      const user = await restoreSession()
      return destinationFor(user, requested)
    } catch (error) {
      if (canAutoDingTalkLogin()) {
        try {
          const user = await authenticateDingTalk()
          showAuthMessage(requested ? '已登录' : `已登录，${user.nickname}`, 'success')
          return destinationFor(user, requested)
        } catch { showAuthMessage('免登异常，请联系管理员', 'error', 5000) }
      }
      return true
    } finally { authUi.checking = false }
  }

  authUi.checking = true
  try {
    let user
    try { user = await restoreSession() }
    catch (error) {
      if (!isSessionError(error)) throw error
      if (canAutoDingTalkLogin()) {
        try {
          user = await authenticateDingTalk()
          showAuthMessage(to.path === '/' ? `已登录，${user.nickname}` : '已登录', 'success')
        } catch {
          showAuthMessage('免登异常，请联系管理员', 'error', 5000)
          return { path: '/login', query: { redirect: to.fullPath } }
        }
      } else {
        if (to.path !== '/') showAuthMessage('登录已过期', 'warning')
        return { path: '/login', query: { redirect: to.fullPath } }
      }
    }
    const destination = destinationFor(user, to.fullPath)
    return to.path === '/' || destination !== to.fullPath ? destination : true
  } catch {
    showAuthMessage('请求失败，请稍后重试', 'error')
    return { path: '/login', query: { redirect: to.fullPath } }
  } finally { authUi.checking = false }
})

let recoveringExpiredSession = false
window.addEventListener('jiabei:session-expired', async () => {
  if (recoveringExpiredSession) return
  recoveringExpiredSession = true; clearAuthenticated(); authUi.checking = true
  const redirect = router.currentRoute.value.fullPath
  try {
    if (canAutoDingTalkLogin()) {
      await authenticateDingTalk(); showAuthMessage('已登录', 'success'); await router.replace(redirect)
    } else {
      showAuthMessage('登录已过期', 'warning'); await router.replace({ path: '/login', query: { redirect } })
    }
  } catch {
    showAuthMessage('免登异常，请联系管理员', 'error', 5000); await router.replace({ path: '/login', query: { redirect } })
  } finally { authUi.checking = false; recoveringExpiredSession = false }
})

createApp(App).use(router).mount('#app')
