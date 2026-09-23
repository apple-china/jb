<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Eye, Palette, ShieldCheck, Sparkles, UserCog, UsersRound } from 'lucide-vue-next'
import BrandMark from '../components/BrandMark.vue'
import PasswordInput from '../components/PasswordInput.vue'
import AppToast from '../components/AppToast.vue'
import { useAppToast } from '../composables/useAppToast'
import { authUi, authenticateDingTalk, clearDingTalkFailure, destinationFor, isMockLoginEnabled, markAuthenticated, readDingTalkFailure, rememberDingTalkFailure, showAuthMessage } from '../auth'
import { api, ApiError } from '../api'
import type { CurrentUser } from '../types'
const router=useRouter(),route=useRoute(),loading=ref(''),username=ref(''),password=ref(''),forcedUser=ref<CurrentUser|null>(null),newPassword=ref(''),confirmPassword=ref('')
const {toast,showToast,showApiError}=useAppToast()
const mockLoginEnabled=isMockLoginEnabled()
const rememberedFailure=readDingTalkFailure()
const retryCode=ref(rememberedFailure?.code??'')
const retryDiagnostic=ref(rememberedFailure?.diagnosticId??'')
const retryTrace=ref(rememberedFailure?.serverTraceId??'')
const dingTalkRetry=computed(()=>!!retryCode.value)
const loginReady=computed(()=>/^[A-Za-z0-9]{6,12}$/.test(username.value.trim())&&password.value.length>=6&&password.value.length<=12)
const changedPasswordReady=computed(()=>newPassword.value.length>=6&&newPassword.value.length<=12&&newPassword.value===confirmPassword.value)
const accounts=[
  {id:'admin01',name:'超管',note:'全部管理权限',icon:ShieldCheck},
  {id:'operator01',name:'运营',note:'日常维护与代预约',icon:UserCog},
  {id:'observer01',name:'观察员',note:'只读查看，不可导出',icon:Eye},
  {id:'makeup01',name:'化妆师',note:'查看排期并为本人代约',icon:Palette},
  {id:'streamer01',name:'主播',note:'预约、修改和取消本人安排',icon:Sparkles},
]
function requested(){return typeof route.query.redirect==='string'?route.query.redirect:undefined}
async function login(userId:string){loading.value=userId;try{const user=markAuthenticated(await api.mockLogin(userId));showAuthMessage(`已登录，${user.nickname}`,'success');await router.replace(destinationFor(user,requested()))}catch(e){showApiError(e,'暂时无法登录。')}finally{loading.value=''}}
async function passwordLogin(){if(!loginReady.value||loading.value)return;loading.value='password';try{const user=markAuthenticated(await api.passwordLogin(username.value,password.value));if(user.mustChangePassword){forcedUser.value=user;return}showAuthMessage(requested()?'已登录':`已登录，${user.nickname}`,'success');await router.replace(destinationFor(user,requested()))}catch(e){showApiError(e,'登录失败。')}finally{loading.value=''}}
async function dingTalkLogin(){if(loading.value)return;loading.value='dingtalk';authUi.checking=true;try{const user=await authenticateDingTalk();clearDingTalkFailure();retryCode.value='';retryDiagnostic.value='';retryTrace.value='';showAuthMessage(requested()?'已登录':`已登录，${user.nickname}`,'success');await router.replace(destinationFor(user,requested()))}catch(e){if(e instanceof ApiError&&['ACCOUNT_DISABLED','ACCOUNT_UNREGISTERED','FORBIDDEN'].includes(e.code))await router.replace({path:'/forbidden',query:{reason:e.code}});else{const details=rememberDingTalkFailure(e);retryCode.value=details.code;retryDiagnostic.value=details.diagnosticId;retryTrace.value=details.serverTraceId??'';showToast(`免登失败：${details.code}`,'error',5000)}}finally{loading.value='';authUi.checking=false}}
async function finishPasswordChange(){if(newPassword.value.length<6||newPassword.value.length>12){showApiError({status:422,message:'新密码须为6–12位。'},'密码格式不正确。');return}if(newPassword.value!==confirmPassword.value){showApiError({status:422,message:'两次输入的新密码不一致。'},'密码不一致。');return}loading.value='change';try{await api.changePassword(password.value,newPassword.value);const user=markAuthenticated(await api.passwordLogin(username.value,newPassword.value));password.value='';forcedUser.value=null;showAuthMessage(`已登录，${user.nickname}`,'success');await router.replace(destinationFor(user,requested()))}catch(e){showApiError(e,'密码修改失败。')}finally{loading.value=''}}
</script>
<template><main class="login-page"><section class="login-card"><BrandMark hide-subtitle/><template v-if="forcedUser"><div class="login-copy password-change-copy"><h1>请先修改密码</h1><p>请设置新密码后继续使用。</p></div><form class="password-login" autocomplete="off" @submit.prevent="finishPasswordChange"><PasswordInput v-model="newPassword" name="new-password" autocomplete="new-password" placeholder="新密码（6–12位）" :maxlength="12"/><PasswordInput v-model="confirmPassword" name="confirm-password" autocomplete="new-password" placeholder="再次输入新密码" :maxlength="12"/><button type="submit" class="button primary full" :disabled="!changedPasswordReady||!!loading">确认修改并进入</button></form></template><template v-else><div v-if="mockLoginEnabled" class="login-copy"><span class="eyebrow">本地 Mock</span><h1>选择测试身份</h1><p>五种固定角色均由服务端校验。</p></div><div v-if="mockLoginEnabled" class="account-list"><button v-for="account in accounts" :key="account.id" type="button" :disabled="!!loading" @click="login(account.id)"><span class="account-icon"><component :is="account.icon" :size="21"/></span><span><strong>{{ account.name }}</strong><small>{{ account.note }}</small></span><span class="account-enter">{{ loading===account.id?'进入中…':'进入' }}</span></button></div><form class="password-login" autocomplete="on" @submit.prevent="passwordLogin"><input v-model="username" name="username" autocomplete="username" autocapitalize="characters" :spellcheck="false" inputmode="text" placeholder="账号"/><PasswordInput v-model="password" name="password" autocomplete="current-password" placeholder="密码" :maxlength="12"/><button type="submit" class="button primary login-submit full" :disabled="!loginReady||!!loading">{{ loading==='password'?'登录中…':'登录' }}</button><button type="button" class="button dingtalk-login full" :disabled="!!loading" @click="dingTalkLogin">{{ loading==='dingtalk'?'免登中…':dingTalkRetry?'重新免登':'钉钉免登' }}</button><p v-if="dingTalkRetry" class="dingtalk-diagnostic">错误代码：{{ retryCode }}<br/>诊断编号：{{ retryDiagnostic }}<template v-if="retryTrace"><br/>服务端追踪编号：{{ retryTrace }}</template></p></form></template><div v-if="mockLoginEnabled" class="login-foot"><UsersRound :size="16"/> 固定测试数据，不包含真实个人信息</div></section><AppToast :message="toast.message" :kind="toast.kind" /></main></template>
