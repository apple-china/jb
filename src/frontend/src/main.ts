import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import LoginView from './views/LoginView.vue'
import StreamerView from './views/StreamerView.vue'
import AdminView from './views/AdminView.vue'
import ForbiddenView from './views/ForbiddenView.vue'
import CardDebugView from './views/CardDebugView.vue'
import AuthLandingView from './views/AuthLandingView.vue'
import { authUi, authenticateDingTalk, canAutoDingTalkLogin, clearAuthenticated, destinationFor, isSessionError, readDingTalkFailure, rememberDingTalkFailure, restoreSession, showAuthMessage } from './auth'
import { ApiError } from './api'
import { installBrowserDiagnostics, recordDiagnostic } from './diagnostics'
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
import './v15.css'

function syncVisualViewportHeight(){
  const height=window.visualViewport?.height??window.innerHeight
  document.documentElement.style.setProperty('--app-viewport-height',`${height}px`)
}
recordDiagnostic('MODULE_STARTED')
syncVisualViewportHeight()
window.visualViewport?.addEventListener('resize',syncVisualViewportHeight)
window.addEventListener('orientationchange',syncVisualViewportHeight)

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
recordDiagnostic('ROUTER_STARTED')

function dingTalkRecovery(error:unknown,redirect?:string){
  const details=rememberDingTalkFailure(error)
  showAuthMessage(`免登失败：${details.code}（诊断编号 ${details.diagnosticId}）`,'error',8000)
  return {path:'/login',query:{redirect}}
}

router.beforeEach(async to => {
  const requested = typeof to.query.redirect === 'string' ? to.query.redirect : undefined
  if (to.path === '/forbidden') return true
  if (to.path === '/login') {
    if (readDingTalkFailure()) return true
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
        } catch (error) { if(error instanceof ApiError&&['ACCOUNT_DISABLED','ACCOUNT_UNREGISTERED','FORBIDDEN'].includes(error.code))return {path:'/forbidden',query:{reason:error.code}};return dingTalkRecovery(error,requested) }
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
        } catch (error) {
          if(error instanceof ApiError&&['ACCOUNT_DISABLED','ACCOUNT_UNREGISTERED','FORBIDDEN'].includes(error.code))return {path:'/forbidden',query:{reason:error.code}}
          return dingTalkRecovery(error,to.fullPath)
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
  } catch (error) {
    const recovery=dingTalkRecovery(error,redirect); await router.replace(recovery)
  } finally { authUi.checking = false; recoveringExpiredSession = false }
})

window.addEventListener('jiabei:access-restricted',(event)=>{
  const reason=(event as CustomEvent<{reason?:string}>).detail?.reason??'FORBIDDEN'
  void router.replace({path:'/forbidden',query:{reason}})
})

const app=createApp(App)
installBrowserDiagnostics(app)
app.use(router).mount('#app')
recordDiagnostic('APP_MOUNTED')
