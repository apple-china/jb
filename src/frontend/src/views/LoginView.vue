<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Eye, Palette, ShieldCheck, Sparkles, UserCog, UsersRound } from 'lucide-vue-next'
import BrandMark from '../components/BrandMark.vue'
import PasswordInput from '../components/PasswordInput.vue'
import AppToast from '../components/AppToast.vue'
import { useAppToast } from '../composables/useAppToast'
import { currentDingTalkCorpId, missingAuthCodeMessage, requestDingTalkAuthCode } from '../integrations/dingtalk'
import { api } from '../api'
import type { CurrentUser } from '../types'
const router=useRouter(),loading=ref(''),username=ref(''),password=ref(''),forcedUser=ref<CurrentUser|null>(null),newPassword=ref(''),confirmPassword=ref('')
const {toast,showToast,showApiError}=useAppToast()
const mockLoginEnabled=import.meta.env.VITE_ENABLE_MOCK_LOGIN!=='false'&&import.meta.env.MODE!=='production'
const autoDingTalkLogin=import.meta.env.VITE_DINGTALK_AUTO_LOGIN==='true'
const loginReady=computed(()=>/^[A-Za-z0-9]{6,12}$/.test(username.value.trim())&&password.value.length>=6&&password.value.length<=12)
const changedPasswordReady=computed(()=>newPassword.value.length>=6&&newPassword.value.length<=12&&newPassword.value===confirmPassword.value)
const dingTalkDiagnostics=import.meta.env.VITE_DINGTALK_TEST_DIAGNOSTICS==='true'
const accounts=[
  {id:'admin01',name:'超管',note:'全部管理权限',icon:ShieldCheck},
  {id:'operator01',name:'运营',note:'日常维护与代预约',icon:UserCog},
  {id:'observer01',name:'观察员',note:'只读查看，不可导出',icon:Eye},
  {id:'makeup01',name:'化妆师',note:'查看排期并为本人代约',icon:Palette},
  {id:'streamer01',name:'主播',note:'预约、修改和取消本人安排',icon:Sparkles},
]
function target(user:CurrentUser){return user.role==='STREAMER'?'/booking':'/admin'}
async function login(userId:string){loading.value=userId;try{const user=await api.mockLogin(userId);await router.replace(target(user))}catch(e){showApiError(e,'暂时无法登录。')}finally{loading.value=''}}
async function passwordLogin(){loading.value='password';try{const user=await api.passwordLogin(username.value,password.value);if(user.mustChangePassword){forcedUser.value=user;return}await router.replace(target(user))}catch(e){showApiError(e,'登录失败。')}finally{loading.value=''}}
async function dingTalkLogin(automatic=false){if(loading.value)return;loading.value='dingtalk';try{const {authCode,corpId}=await requestDingTalkAuthCode();const user=await api.dingTalkLogin(authCode,corpId);if(dingTalkDiagnostics){showToast(`免登码：${authCode}　钉钉用户ID：${user.dingTalkUserId??''}`,'success',3000);await new Promise(resolve=>setTimeout(resolve,3000))}await router.replace(target(user))}catch(e){const clientError=e as {code?:string;message?:string};if(clientError.code==='USER_NOT_AUTHORIZED'){await router.replace('/forbidden')}else if(clientError.code==='DINGTALK_CODE_EMPTY'||(automatic&&clientError.code==='NOT_IN_DINGTALK'))showToast(missingAuthCodeMessage(currentDingTalkCorpId()),'warning',5000);else if(clientError.code&&['NOT_IN_DINGTALK','DINGTALK_NOT_CONFIGURED','DINGTALK_CORP_MISMATCH'].includes(clientError.code))showToast(clientError.message||'钉钉免登暂不可用。','warning');else if(!automatic)showApiError(e,'钉钉免登失败，请重试。')}finally{loading.value=''}}
async function finishPasswordChange(){if(newPassword.value.length<6||newPassword.value.length>12){showApiError({status:422,message:'新密码须为6–12位。'},'密码格式不正确。');return}if(newPassword.value!==confirmPassword.value){showApiError({status:422,message:'两次输入的新密码不一致。'},'密码不一致。');return}loading.value='change';try{await api.changePassword(password.value,newPassword.value);const user=await api.passwordLogin(username.value,newPassword.value);password.value='';forcedUser.value=null;await router.replace(target(user))}catch(e){showApiError(e,'密码修改失败。')}finally{loading.value=''}}
onMounted(()=>{if(autoDingTalkLogin)void dingTalkLogin(true)})
</script>
<template><main class="login-page"><section class="login-card"><BrandMark/><template v-if="forcedUser"><div class="login-copy"><span class="eyebrow">账号安全</span><h1>请先修改密码</h1><p>首次登录或管理员重置密码后，完成修改才能继续使用。</p></div><div class="password-login"><PasswordInput v-model="newPassword" autocomplete="new-password" placeholder="新密码（6–12位）" :maxlength="12"/><PasswordInput v-model="confirmPassword" autocomplete="new-password" placeholder="再次输入新密码" :maxlength="12"/><button class="button primary full" :disabled="!changedPasswordReady||!!loading" @click="finishPasswordChange">确认修改并进入</button></div></template><template v-else><div v-if="mockLoginEnabled" class="login-copy"><span class="eyebrow">本地 Mock</span><h1>选择测试身份</h1><p>五种固定角色均由服务端校验。</p></div><div v-else class="login-copy"><span class="eyebrow">加贝云</span><h1>账号登录</h1><p>可使用钉钉免登或管理员分配的账号密码。</p></div><div v-if="mockLoginEnabled" class="account-list"><button v-for="account in accounts" :key="account.id" :disabled="!!loading" @click="login(account.id)"><span class="account-icon"><component :is="account.icon" :size="21"/></span><span><strong>{{ account.name }}</strong><small>{{ account.note }}</small></span><span class="account-enter">{{ loading===account.id?'进入中…':'进入' }}</span></button></div><div class="password-login"><h2>登录</h2><input v-model="username" autocomplete="username" placeholder="账号"/><PasswordInput v-model="password" autocomplete="current-password" placeholder="密码" :maxlength="12"/><button class="button primary login-submit full" :disabled="!loginReady||!!loading" @click="passwordLogin">登录</button><button class="button dingtalk-login full" :disabled="!!loading" @click="dingTalkLogin()">{{ loading==='dingtalk'?'免登中…':'钉钉免登' }}</button></div></template><div v-if="mockLoginEnabled" class="login-foot"><UsersRound :size="16"/> 固定测试数据，不包含真实个人信息</div></section><AppToast :message="toast.message" :kind="toast.kind" /></main></template>
