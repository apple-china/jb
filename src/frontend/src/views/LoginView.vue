<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Eye, Palette, ShieldCheck, Sparkles, UserCog, UsersRound } from 'lucide-vue-next'
import BrandMark from '../components/BrandMark.vue'
import AppToast from '../components/AppToast.vue'
import { useAppToast } from '../composables/useAppToast'
import { requestDingTalkAuthCode } from '../integrations/dingtalk'
import { api } from '../api'
import type { CurrentUser } from '../types'
const router=useRouter(),loading=ref(''),username=ref(''),password=ref(''),forcedUser=ref<CurrentUser|null>(null),newPassword=ref(''),confirmPassword=ref('')
const {toast,showToast,showApiError}=useAppToast()
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
async function dingTalkLogin(){loading.value='dingtalk';try{const {authCode,corpId}=await requestDingTalkAuthCode();const user=await api.dingTalkLogin(authCode,corpId);await router.replace(target(user))}catch(e){const clientError=e as {code?:string;message?:string};if(clientError.code&&['NOT_IN_DINGTALK','DINGTALK_NOT_CONFIGURED','DINGTALK_CORP_MISMATCH'].includes(clientError.code))showToast(clientError.message||'钉钉免登暂不可用。','warning');else showApiError(e,'钉钉免登失败，请重试。')}finally{loading.value=''}}
async function finishPasswordChange(){if(newPassword.value.length<6||newPassword.value.length>100||!/[A-Za-z]/.test(newPassword.value)){showApiError({status:422,message:'新密码须为6–100位且至少包含一个字母。'},'密码格式不正确。');return}if(newPassword.value!==confirmPassword.value){showApiError({status:422,message:'两次输入的新密码不一致。'},'密码不一致。');return}loading.value='change';try{await api.changePassword(password.value,newPassword.value);const user=await api.passwordLogin(username.value,newPassword.value);password.value='';forcedUser.value=null;await router.replace(target(user))}catch(e){showApiError(e,'密码修改失败。')}finally{loading.value=''}}
</script>
<template><main class="login-page"><section class="login-card"><BrandMark/><template v-if="forcedUser"><div class="login-copy"><span class="eyebrow">账号安全</span><h1>请先修改密码</h1><p>首次登录或管理员重置密码后，完成修改才能继续使用。</p></div><div class="password-login"><input v-model="newPassword" autocomplete="new-password" type="password" placeholder="新密码（6–100位，至少含一个字母）"/><input v-model="confirmPassword" autocomplete="new-password" type="password" placeholder="再次输入新密码"/><button class="button primary full" :disabled="newPassword.length<6||newPassword.length>100||!/[A-Za-z]/.test(newPassword)||newPassword!==confirmPassword||!!loading" @click="finishPasswordChange">确认修改并进入</button></div></template><template v-else><div class="login-copy"><span class="eyebrow">本地 Mock</span><h1>选择测试身份</h1><p>五种固定角色均由服务端校验。</p></div><div class="account-list"><button v-for="account in accounts" :key="account.id" :disabled="!!loading" @click="login(account.id)"><span class="account-icon"><component :is="account.icon" :size="21"/></span><span><strong>{{ account.name }}</strong><small>{{ account.note }}</small></span><span class="account-enter">{{ loading===account.id?'进入中…':'进入' }}</span></button></div><div class="password-login"><h2>登录</h2><input v-model="username" autocomplete="username" placeholder="账号"/><input v-model="password" autocomplete="current-password" type="password" placeholder="密码"/><button class="button primary login-submit full" :disabled="username.trim().length < 6 || password.length < 6 || !!loading" @click="passwordLogin">登录</button><button class="button dingtalk-login full" :disabled="!!loading" @click="dingTalkLogin">{{ loading==='dingtalk'?'免登中…':'钉钉免登' }}</button></div></template><div class="login-foot"><UsersRound :size="16"/> 固定测试数据，不包含真实个人信息</div></section><AppToast :message="toast.message" :kind="toast.kind" /></main></template>
