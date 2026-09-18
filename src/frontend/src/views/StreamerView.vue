<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CalendarDays, ChevronRight, Clock3, Info, Pencil, Sparkles, UsersRound, X } from 'lucide-vue-next'
import BrandMark from '../components/BrandMark.vue'
import AppSheet from '../components/AppSheet.vue'
import AppToast from '../components/AppToast.vue'
import BookingRulesSheet from '../components/BookingRulesSheet.vue'
import DateTabs from '../components/DateTabs.vue'
import PolishedSelect from '../components/PolishedSelect.vue'
import TimeText from '../components/TimeText.vue'
import TimeWheel from '../components/TimeWheel.vue'
import { useAppToast } from '../composables/useAppToast'
import { api, ApiError } from '../api'
import type { BookingContext, CurrentUser } from '../types'
import { restoreSession, signOut } from '../auth'
import streamerAvatar from '../assets/主播.png'

const router = useRouter()
const user = ref<CurrentUser | null>(null)
const context = ref<BookingContext | null>(null)
const selectedDate = ref('')
const loading = ref(true)
const submitting = ref(false)
const {toast,showToast,showApiError}=useAppToast()
const bookingOpen = ref(false)
const bookingMode = ref<'create'|'modify'>('create')
const cancelOpen = ref(false)
const logoutOpen = ref(false)
const rulesOpen = ref(false)
const makeupArtistId = ref('')
const teamId = ref('')
const startTime = ref('')
const slots = ref<Array<{time:string;available:boolean;conflict?:boolean;reason?:string}>>([])
const slotsLoading = ref(false)
let slotsRequest=0

function shanghaiDate(offset = 0) {
  const now = new Date(Date.now() + offset * 86400000)
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year:'numeric', month:'2-digit', day:'2-digit' }).formatToParts(now)
  const get = (type:string) => parts.find(p => p.type === type)?.value
  return `${get('year')}-${get('month')}-${get('day')}`
}
const dates = computed(() => [{ label:'今天', value: shanghaiDate() }, { label:'明天', value: shanghaiDate(1) }])
const selectedMakeupArtist = computed(() => context.value?.makeupArtists.find(t => t.id === makeupArtistId.value))
const makeupArtistOptions = computed(() => context.value?.makeupArtists.map(item => ({value:item.id,label:item.name})) ?? [])
const teamOptions = computed(() => context.value?.teams.map(item => ({value:item.id,label:item.name})) ?? [])
const modifyUnchanged = computed(()=>bookingMode.value==='modify'&&!!context.value?.myAppointment&&makeupArtistId.value===context.value.myAppointment.makeupArtistId&&teamId.value===context.value.myAppointment.teamId&&startTime.value===context.value.myAppointment.startTime)
const canSubmit = computed(() => !!makeupArtistId.value && !!teamId.value && !!startTime.value && !submitting.value && !slotsLoading.value && !modifyUnchanged.value)
const emptyText = computed(() => selectedDate.value === shanghaiDate() ? '今天还未预约，选个时间从容准备吧。' : '明天还未预约，提前安排会更从容。')
const scheduleTitle = computed(() => selectedDate.value === shanghaiDate() ? '今天' : '明天')
const bookingTitle = computed(() => `${selectedDate.value===shanghaiDate()?'今天':'明天'} · 妆造安排`)
const greeting = computed(() => {
  const hour = Number(new Intl.DateTimeFormat('en-GB', { timeZone:'Asia/Shanghai', hour:'2-digit', hourCycle:'h23' }).format(new Date()))
  return hour < 12 ? '早上好' : hour < 18 ? '下午好' : '晚上好'
})
function attendanceLabel(value:string){return ({PENDING:'待签到',ARRIVED:'已签到',NOT_ARRIVED:'未到',LATE:'迟到'} as Record<string,string>)[value]??value}
function scheduleAttendanceLabel(value:string){return ({PENDING:'待签到',ARRIVED:'已签到',NOT_ARRIVED:'未到',LATE:'迟到'} as Record<string,string>)[value]??value}

async function initialize() {
  loading.value = true
  try {
    user.value = await restoreSession()
    if (user.value.role !== 'STREAMER') { await router.replace('/admin'); return }
    const initial = await api.bookingContext()
    selectedDate.value = initial.recommendedDate
    context.value = initial.recommendedDate === initial.selectedDate ? initial : await api.bookingContext(initial.recommendedDate)
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) await router.replace('/login')
    else showApiError(e,'加载失败，请稍后重试。')
  } finally { loading.value = false }
}

async function switchDate(date:string) {
  selectedDate.value = date; loading.value = true
  try { context.value = await api.bookingContext(date) } catch(e){ showApiError(e,'加载失败，请稍后重试。') }
  finally { loading.value = false }
}

async function openBooking(mode:'create'|'modify'='create') {
  bookingMode.value=mode
  bookingOpen.value = true
  const current=context.value?.myAppointment
  const d = context.value?.defaults
  makeupArtistId.value = mode==='modify'?(current?.makeupArtistId??''):(d?.makeupArtistId ?? '')
  teamId.value = mode==='modify'?(current?.teamId??''):(d?.teamId ?? '')
  startTime.value = mode==='modify'?(current?.startTime??''):(d?.startTime ?? '')
  if (makeupArtistId.value) await loadSlots()
}
async function loadSlots() {
  const request=++slotsRequest
  const previous=startTime.value;startTime.value = ''
  if (!makeupArtistId.value) { slots.value = []; return }
  slotsLoading.value=true
  try {
    const result=await api.availability(selectedDate.value, makeupArtistId.value, bookingMode.value==='modify'?context.value?.myAppointment?.id:undefined)
    if(request!==slotsRequest)return
    slots.value = result.slots
    const preferred = bookingMode.value==='modify'?previous:context.value?.defaults.startTime
    if (preferred && slots.value.some(s => s.time === preferred && s.available)) startTime.value = preferred
  } catch(e){if(request!==slotsRequest)return;slots.value=[];showApiError(e,'可用时间加载失败。') }
  finally{if(request===slotsRequest)slotsLoading.value=false}
}
async function saveBooking() {
  if(modifyUnchanged.value){showToast('预约信息未修改','warning');return}
  if (!canSubmit.value) return
  submitting.value = true
  try {
    if(bookingMode.value==='modify'&&context.value?.myAppointment)await api.modifyAppointment(context.value.myAppointment.id,{makeupArtistId:makeupArtistId.value,teamId:teamId.value,startTime:startTime.value,version:context.value.myAppointment.version})
    else await api.createAppointment({ bookingDate:selectedDate.value, makeupArtistId:makeupArtistId.value, teamId:teamId.value, startTime:startTime.value })
    bookingOpen.value = false; showToast(bookingMode.value==='modify'?'预约已修改。':'预约成功。','success'); await switchDate(selectedDate.value)
  } catch(e){ showApiError(e,'预约失败，请稍后重试。') }
  finally { submitting.value = false }
}
async function cancelBooking() {
  if (!context.value?.myAppointment) return
  submitting.value = true
  try {
    await api.cancelAppointment(context.value.myAppointment.id, context.value.myAppointment.version)
    cancelOpen.value = false; showToast('预约已取消。','success'); await switchDate(selectedDate.value)
  } catch(e){ showApiError(e,'取消失败，请稍后重试。') }
  finally { submitting.value = false }
}
async function logout(){ logoutOpen.value=false;await signOut();await router.replace('/login') }
onMounted(initialize)
</script>

<template>
  <main class="mobile-app">
    <header class="mobile-header">
      <BrandMark compact />
      <div class="header-identity"><span>{{ greeting }}，{{ user?.nickname }}</span><button class="avatar avatar-streamer" aria-label="打开账号菜单" @click="logoutOpen=true"><img :src="streamerAvatar" alt="" /></button></div>
    </header>

    <div v-if="loading" class="loading-state"><span></span><span></span><span></span></div>
    <template v-else-if="context">
      <DateTabs :model-value="selectedDate" :options="dates" @update:model-value="switchDate" />
      <p v-if="!context.writeEnabled" class="notice"><Info :size="17" />预约服务暂时关闭，请稍后再试。已有安排仍可查看。</p>

      <section class="section-block">
        <div class="section-heading"><div><span class="eyebrow">MY APPOINTMENT</span><h2>我的预约</h2></div></div>
        <article v-if="context.myAppointment" class="appointment-card" :class="context.myAppointment.status.toLowerCase()">
          <div class="appointment-time"><Clock3 :size="19" /><strong><TimeText :value="context.myAppointment.startTime" /></strong><span class="attendance-badge" :class="context.myAppointment.attendanceStatus.toLowerCase()">{{ attendanceLabel(context.myAppointment.attendanceStatus) }}</span></div>
          <dl class="appointment-details"><div><dt>化妆师</dt><dd>{{ context.myAppointment.makeupArtistName }}</dd></div><div><dt>团播组</dt><dd>{{ context.myAppointment.teamName }}</dd></div></dl>
          <div v-if="context.writeEnabled && context.myAppointment.selfOperationAllowed" class="appointment-actions"><button class="appointment-action modify" @click="openBooking('modify')"><Pencil :size="16" />修改</button><button class="appointment-action cancel" @click="cancelOpen=true"><X :size="17" />取消</button></div>
        </article>
        <article v-else class="empty-card"><CalendarDays :size="26" /><p>{{ emptyText }}</p></article>
      </section>

      <button v-if="!context.myAppointment" class="quick-booking" :disabled="!context.writeEnabled" @click="openBooking('create')"><span><Sparkles :size="22" /><b>快速预约</b></span><ChevronRight :size="20" /></button>
      <p v-if="context.operationCounts.cancelCount||context.operationCounts.modifyCount" class="field-hint">本预约日期：已取消 {{ context.operationCounts.cancelCount }}/2 次，已修改 {{ context.operationCounts.modifyCount }}/3 次。</p>

      <section class="section-block">
        <div class="section-heading"><div><span class="eyebrow">DAILY SCHEDULE</span><h2>{{ scheduleTitle }}</h2></div><span>{{ context.dailySchedule.length }} 条</span></div>
        <div v-if="context.dailySchedule.length" class="schedule-list">
          <div v-for="item in context.dailySchedule" :key="item.startTime+item.makeupArtistName+item.streamerName" :class="{mine:item.isMine}"><TimeText :value="item.startTime" /><span class="schedule-person"><b>{{ item.makeupArtistName }}</b><small>{{ item.streamerName }} · {{ item.teamName }}</small></span><em class="attendance-badge" :class="item.attendanceStatus.toLowerCase()">{{ scheduleAttendanceLabel(item.attendanceStatus) }}</em></div>
        </div>
        <div v-else class="plain-empty">暂无预约安排</div>
      </section>
      <div class="mobile-utilities"><button class="rules-link" @click="rulesOpen=true"><Info :size="16" /><span>预约规则</span></button></div>
    </template>

    <AppSheet :open="bookingOpen" :title="bookingTitle" @close="bookingOpen=false">
      <div class="form-stack">
        <label><span>化妆师</span><PolishedSelect v-model="makeupArtistId" :options="makeupArtistOptions" placeholder="请选择化妆师" aria-label="化妆师" @change="loadSlots"><template #leading><Sparkles :size="18"/></template></PolishedSelect></label>
        <label><span>团播组</span><PolishedSelect v-model="teamId" :options="teamOptions" placeholder="请选择团播组" aria-label="团播组"><template #leading><UsersRound :size="18"/></template></PolishedSelect></label>
        <div><span class="field-label">时间</span><div v-if="slotsLoading" class="inline-skeleton"><span/><span/></div><TimeWheel v-else-if="makeupArtistId&&slots.length" v-model="startTime" :slots="slots"/><p v-else class="field-hint time-wheel-empty">{{ makeupArtistId?'暂无可预约时间':'选择化妆师后即可查看可预约时间' }}</p></div>
        <p v-if="modifyUnchanged" class="field-hint">预约信息未修改</p>
        <p v-if="context?.defaults.message" class="field-hint">{{ context.defaults.message }}</p>
      </div>
      <template #footer><button class="button primary full" :disabled="!canSubmit" @click="saveBooking">{{ submitting ? '提交中…' : `${bookingMode==='modify'?'确认修改':'确认预约'}${selectedMakeupArtist ? ` · ${selectedMakeupArtist.name}` : ''}` }}</button></template>
    </AppSheet>

    <AppSheet :open="cancelOpen" title="确认取消预约" @close="cancelOpen=false">
      <div class="confirm-copy"><div class="warning-icon">!</div><p>取消后立即释放化妆师时段和该日期资格，可以重新预约；已使用次数不会清零。</p><strong>本预约日期已取消 {{ context?.operationCounts.cancelCount??0 }}/2 次，是否继续？</strong></div>
      <template #footer><div class="two-buttons"><button class="button secondary" @click="cancelOpen=false">暂不取消</button><button class="button danger-solid" :disabled="submitting" @click="cancelBooking">{{ submitting?'处理中…':'确认取消' }}</button></div></template>
    </AppSheet>

    <BookingRulesSheet :open="rulesOpen" role="STREAMER" @close="rulesOpen=false" />

    <AppSheet :open="logoutOpen" title="退出登录" @close="logoutOpen=false">
      <div class="confirm-copy"><p>确定退出当前账号吗？</p></div>
      <template #footer><div class="two-buttons"><button class="button secondary" @click="logoutOpen=false">暂不退出</button><button class="button danger-solid" @click="logout">确认退出</button></div></template>
    </AppSheet>
    <AppToast :message="toast.message" :kind="toast.kind" />
  </main>
</template>

