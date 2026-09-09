import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import LoginView from './views/LoginView.vue'
import StreamerView from './views/StreamerView.vue'
import AdminView from './views/AdminView.vue'
import ForbiddenView from './views/ForbiddenView.vue'
import CardDebugView from './views/CardDebugView.vue'
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

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: LoginView },
    { path: '/booking', component: StreamerView },
    { path: '/admin', component: AdminView },
    { path: '/forbidden', component: ForbiddenView },
    { path: '/mock/cards', component: CardDebugView },
  ],
})

createApp(App).use(router).mount('#app')
