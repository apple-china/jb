<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BarChart3, BookOpen, CalendarPlus, CalendarRange, Check, ChevronDown, ChevronRight, ChevronUp, Copy, Download, ImagePlus, KeyRound, Plus, RotateCcw, Send, Settings2, SlidersHorizontal, UserCog, UsersRound, X } from 'lucide-vue-next'
import AppDatePicker from '../components/AppDatePicker.vue'
import BrandMark from '../components/BrandMark.vue'
import AppSheet from '../components/AppSheet.vue'
import AppToast from '../components/AppToast.vue'
import BookingRulesSheet from '../components/BookingRulesSheet.vue'
import DateTabs from '../components/DateTabs.vue'
import PolishedSelect from '../components/PolishedSelect.vue'
import TimeWheel from '../components/TimeWheel.vue'
import TimeText from '../components/TimeText.vue'
import { useAppToast } from '../composables/useAppToast'
import { api, ApiError } from '../api'
import type { AnalyticsData, Appointment, BookingOptions, CurrentUser, MakeupArtist, Team } from '../types'
import { avatarInitial } from '../utils/display'
import { dateRangePreset, type RangePreset } from '../utils/dateRanges'
import { formatLastSeen } from '../utils/lastSeen'
import { restoreSession, signOut } from '../auth'

const router = useRouter()
const route = useRoute()
const { toast, showToast, showApiError } = useAppToast()
const mockLoginEnabled = import.meta.env.VITE_ENABLE_MOCK_LOGIN !== 'false' && import.meta.env.MODE !== 'production'
const user = ref<CurrentUser | null>(null)
const tab = ref<'appointments' | 'create' | 'analytics' | 'settings'>('appointments')
const loading = ref(true)
const appointments = ref<Appointment[]>([])
const makeupArtists = ref<MakeupArtist[]>([])
const teams = ref<Team[]>([])
const streamers = ref<any[]>([])
const accounts = ref<any[]>([])
const setting = ref<any>(null)
const today = shanghaiDate()
const range = ref({ startDate: today, endDate: today })
const activePreset = ref<RangePreset | ''>('')
const filterDate = ref(today)
const filterOpen = ref(false)
const filterApplied = ref(false)
const filters = ref({ streamer: '', makeupArtist: '', attendanceStatus: '', status: '' })
const appliedFilters = ref({ streamer: '', makeupArtist: '', attendanceStatus: '', status: '' })
const appliedRange = ref({ startDate: today, endDate: today })
const page = ref(1)
const pageSize = ref(30)
const total = ref(0)
const totalPages = ref(0)
const detail = ref<any>(null)
const action = ref<'modify' | 'cancel' | null>(null)
const rulesOpen = ref(false)
const logoutOpen = ref(false)
const cardDateOpen = ref(false)
const cardDate = ref<'today' | 'tomorrow' | null>('today')
const cardStatuses = ref<Array<{key:'today'|'tomorrow';bookingDate:string;hasSuccessfulDelivery:boolean;firstDeliveredAt?:string}>>([])
const cardStatusLoading = ref(false)
const cardSending = ref(false)
const exportOpen = ref(false)
const exporting = ref(false)
const dateMode = ref<'today' | 'tomorrow' | null>('today')
const createSlots = ref<Array<{time:string;available:boolean;reason?:string}>>([])
const createForm = ref({ streamerUserId: '', makeupArtistId: '', teamId: '', startTime: '', reason: '' })
const editForm = ref({ makeupArtistId: '', teamId: '', startTime: '', reason: '' })
const editSlots = ref<Array<{time:string;available:boolean;conflict?:boolean;reason?:string}>>([])
const bookingOptions = ref<BookingOptions | null>(null)
const overlapConfirmAction = ref<'create'|'modify'|null>(null)
const analytics = ref<AnalyticsData|null>(null)
const analyticsLoading = ref(false)
const analyticsRange = ref({startDate:shanghaiDate(-29),endDate:shanghaiDate()})
const analyticsPreset = ref<'today'|'7d'|'30d'|'month'|'custom'>('30d')
const rankingMetric = ref<'appointments'|'late'|'notArrived'|'modifications'|'cancellations'>('appointments')
const resourceEditor = ref<{ kind: 'makeupArtist' | 'team'; item: any | null } | null>(null)
const resourceForm = ref<any>({})
const accountEditor = ref<any | null>(null)
const accountForm = ref<any>({})
const accountCreateKind = ref<'MAKEUP' | 'STREAMER' | 'ADMIN'>('STREAMER')
const dingTalkQuery = ref('')
const dingTalkEmployees = ref<Array<{dingTalkUserId:string;dingTalkUsername:string;label:string}>>([])
const dingTalkLoading = ref(false)
const dingTalkOpen = ref(false)
const dingTalkSearching = ref(false)
const makeupArtistEditorItem = ref<any | null>(null)
const assignedCredential = ref<{username:string;password:string} | null>(null)
const revokePasswordOpen = ref(false)
const expandedSections = ref<Record<string, boolean>>({})

const isAdmin = computed(() => user.value?.role === 'SUPER_ADMIN' || user.value?.role === 'OPERATOR')
const canAnalytics = computed(() => ['SUPER_ADMIN','OPERATOR','OBSERVER'].includes(user.value?.role ?? ''))
const isSuperAdmin = computed(() => user.value?.role === 'SUPER_ADMIN')
const historical = computed(() => user.value?.role !== 'MAKEUP')
const canCreate = computed(() => user.value?.role === 'SUPER_ADMIN' || (['OPERATOR', 'MAKEUP'].includes(user.value?.role ?? '') && !!user.value?.canCreateAppointments))
const canModify = computed(() => !!user.value?.canModifyAppointments)
const canCancel = computed(() => !!user.value?.canCancelAppointments)
const selectedDate = computed(() => dateMode.value ? shanghaiDate(dateMode.value === 'today' ? 0 : 1) : '')
const streamerOptions = computed(() => streamers.value.map(x => ({ value: x.userId, label: x.nickname })))
const createStreamerOptions = computed(() => (bookingOptions.value?.streamers ?? []).map(x => ({value:x.userId,label:x.nickname})))
const allMakeupArtistOptions = computed(() => makeupArtists.value.map(x => ({ value: x.id, label: x.name })))
const makeupArtistOptions = computed(() => makeupArtists.value.filter(x => x.active && x.attending).map(x => ({ value: x.id, label: x.name })))
const teamOptions = computed(() => teams.value.filter(x => x.active).map(x => ({ value: x.id, label: x.name })))
const createMakeupOptions = computed(() => (bookingOptions.value?.makeupArtists ?? []).map(x=>({value:x.id,label:x.name})))
const createTeamOptions = computed(() => (bookingOptions.value?.teams ?? []).map(x=>({value:x.id,label:x.name})))
const rankedStreamers = computed(() => [...(analytics.value?.streamers ?? [])].sort((a,b)=>b[rankingMetric.value]-a[rankingMetric.value]||b.appointments-a.appointments).slice(0,10))
const analyticsMetrics = computed(()=>analytics.value?[{l:'总预约',v:analytics.value.summary.total,s:''},{l:'有效',v:analytics.value.summary.active,s:''},{l:'已取消',v:analytics.value.summary.cancelled,s:''},{l:'迟到',v:analytics.value.summary.late,s:''},{l:'未到',v:analytics.value.summary.notArrived,s:''},{l:'修改次数',v:analytics.value.summary.modifications,s:''},{l:'取消次数',v:analytics.value.summary.cancellations,s:''},{l:'平均迟到',v:analytics.value.summary.averageLateMinutes,s:'分钟'}]:[])
const selectedCreateSlot = computed(()=>createSlots.value.find(slot=>slot.time===createForm.value.startTime))
const selectedEditSlot = computed(()=>editSlots.value.find(slot=>slot.time===editForm.value.startTime))
const detailSubtitle = computed(()=>detail.value?.bookingNumber?`No.${String(detail.value.bookingNumber).split('-')[0]}`:'')
const hasOtherDetail = computed(()=>!!detail.value&&(detail.value.attendanceEvidenceAt||detail.value.cancelledAt||detail.value.cancelledByName||detail.value.cancelReason||detail.value.conflictOverride))
const resourceCanSave = computed(() => !!resourceForm.value.name?.trim())
const streamerAccounts = computed(() => accounts.value.filter(account => account.role === 'STREAMER'))
const administratorAccounts = computed(() => accounts.value.filter(account => ['SUPER_ADMIN', 'OPERATOR', 'OBSERVER'].includes(account.role)))
const appointmentDates = computed(() => [{ label: '今天', value: shanghaiDate() }, { label: '明天', value: shanghaiDate(1) }])
const selectedCardStatus = computed(() => cardStatuses.value.find(item => item.key === cardDate.value))
const cardWasDelivered = computed(() => !!selectedCardStatus.value?.hasSuccessfulDelivery)
const cardSubmitDisabled = computed(() => !cardDate.value || cardStatusLoading.value || cardSending.value)
const exportSummary = computed(() => ({
  date: historical.value && filterApplied.value ? `${appliedRange.value.startDate} 至 ${appliedRange.value.endDate}` : filterDate.value,
  streamer: streamerOptions.value.find(item => item.value === (filterApplied.value ? appliedFilters.value.streamer : ''))?.label ?? '全部',
  makeupArtist: allMakeupArtistOptions.value.find(item => item.value === (filterApplied.value ? appliedFilters.value.makeupArtist : ''))?.label ?? '全部',
  attendance: attendanceOptions.find(item => item.value === (filterApplied.value ? appliedFilters.value.attendanceStatus : ''))?.label ?? '全部',
  status: statusOptions.find(item => item.value === (filterApplied.value ? appliedFilters.value.status : ''))?.label ?? '全部',
}))
const timeOptions = computed(() => {
  const artistId = user.value?.role === 'MAKEUP' ? user.value.makeupArtistId : createForm.value.makeupArtistId
  const artist = makeupArtists.value.find(item => item.id === artistId)
  const start = artist?.scheduleEnabled === false ? 0 : 8 * 60
  const count = artist?.scheduleEnabled === false ? 144 : 73
  return Array.from({ length: count }, (_, i) => { const n = start + i * 10; const value = String(Math.floor(n / 60)).padStart(2, '0') + ':' + String(n % 60).padStart(2, '0'); return { value, label: value } })
})
const statusOptions = [{ value: 'ACTIVE', label: '有效' }, { value: 'CANCELLED', label: '已取消' }]
const attendanceOptions = [{ value: 'PENDING', label: '待到司' }, { value: 'ARRIVED', label: '已到司' }, { value: 'NOT_ARRIVED', label: '未到' }, { value: 'LATE', label: '迟到' }]
const editableRoleOptions = computed(() => [
  { value: 'STREAMER', label: '主播' },
  { value: 'MAKEUP', label: '化妆师' },
  ...(user.value?.role === 'SUPER_ADMIN' ? [{ value: 'OPERATOR', label: '运营' }] : []),
  { value: 'OBSERVER', label: '观察员' },
])
const createRoleOptions = computed(() => accountCreateKind.value === 'ADMIN'
  ? [...(isSuperAdmin.value ? [{ value: 'OPERATOR', label: '运营' }] : []), { value: 'OBSERVER', label: '观察员' }]
  : editableRoleOptions.value.filter(option => option.value === accountCreateKind.value))
const accountCanSave = computed(() => !!accountForm.value.nickname?.trim() && (accountEditor.value?.id || (!!accountForm.value.dingTalkUserId && !!accountForm.value.dingTalkUsername)))
const accountFields = ['nickname', 'role', 'active', 'attending', 'canModifyAppointments', 'canCancelAppointments', 'canCreateAppointments'] as const
const accountDirty = computed(() => !!accountEditor.value?.id && accountFields.some(key => key === 'nickname' ? accountForm.value.nickname?.trim() !== accountEditor.value.nickname?.trim() : accountForm.value[key] !== accountEditor.value[key]))
const resourceDirty = computed(() => {
  const item = makeupArtistEditorItem.value
  if (!item) return false
  return resourceForm.value.imageUrl !== (item.avatarUrl ?? '') || JSON.stringify(resourceForm.value.workDays ?? []) !== JSON.stringify(item.workDays ?? []) || resourceForm.value.workStart !== minute(item.workStart) || resourceForm.value.workEnd !== minute(item.workEnd) || resourceForm.value.scheduleEnabled !== (item.scheduleEnabled ?? true) || resourceForm.value.attending !== item.attending
})
const resourceEditorDirty = computed(() => {
  const item = resourceEditor.value?.item
  if (!item) return true
  return resourceForm.value.name.trim() !== item.name || resourceForm.value.imageUrl !== (item.avatarUrl ?? item.logoUrl ?? '') || resourceForm.value.active !== item.active || (resourceEditor.value?.kind === 'makeupArtist' && (JSON.stringify(resourceForm.value.workDays ?? []) !== JSON.stringify(item.workDays ?? []) || resourceForm.value.workStart !== minute(item.workStart) || resourceForm.value.workEnd !== minute(item.workEnd) || resourceForm.value.scheduleEnabled !== (item.scheduleEnabled ?? true) || resourceForm.value.attending !== item.attending))
})
const accountSubmitLabel = computed(() => !accountEditor.value?.id ? '添加' : accountDirty.value || resourceDirty.value ? '保存' : '确定')
const resourceSubmitLabel = computed(() => !resourceEditor.value?.item ? '添加' : resourceEditorDirty.value ? '保存' : '确定')
const accountEditorTitle = computed(() => {
  if (!accountEditor.value?.id) return '添加 ' + roleLabel(accountForm.value.role ?? '')
  return accountEditor.value.nickname
})
const resourceEditorTitle = computed(() => resourceEditor.value?.item?.name ?? ('添加 ' + (resourceEditor.value?.kind === 'makeupArtist' ? '化妆师' : '团队')))
const accountEditorSubtitle = computed(()=>accountEditor.value?.id?[accountEditor.value.dingTalkUserId?`@${accountEditor.value.dingTalkUserId}`:'',formatLastSeen(accountEditor.value.lastLoginAt)].filter(Boolean).join(' · '):'')
const resourceEditorSubtitle = computed(()=>resourceEditor.value?.item?.id?`ID ${resourceEditor.value.item.id}`:'')
const createReasonOptions = ['无法自行预约', '迟到现场补录', '临时加急安排', '特殊资源协调', '其他特殊情况'].map(value => ({ value, label: value }))
const weekdays = [{ v: 1, l: '一' }, { v: 2, l: '二' }, { v: 3, l: '三' }, { v: 4, l: '四' }, { v: 5, l: '五' }, { v: 6, l: '六' }, { v: 7, l: '日' }]

function shanghaiDate(offset = 0) { const d = new Date(Date.now() + offset * 86_400_000); return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(d) }
function minute(value?: string) { return value?.slice(0, 5) ?? '—' }
function shortDate(value?: string) { return value?.slice(5) ?? '' }
function statusLabel(value: string) { return value === 'ACTIVE' ? '有效' : '已取消' }
function attendanceLabel(value: string) { return ({ PENDING: '待到司', ARRIVED: '已到司', NOT_ARRIVED: '未到', LATE: '迟到' } as Record<string, string>)[value] ?? value }
function sourceLabel(value?: string) { return value === 'STREAMER' ? '本人预约' : '代预约' }
function dateTime(value?: string) { if(!value)return '—';const parts=new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).formatToParts(new Date(value));const get=(type:string)=>parts.find(p=>p.type===type)?.value??'';return `${get('month')}-${get('day')} ${get('hour')}:${get('minute')}` }
function roleLabel(value: string) { return ({ SUPER_ADMIN: '超管', OPERATOR: '运营', OBSERVER: '观察员', MAKEUP: '化妆师', STREAMER: '主播' } as Record<string, string>)[value] ?? value }
function roleAvatarClass(role?: string) { return role === 'STREAMER' ? 'avatar-streamer' : role === 'MAKEUP' ? 'avatar-makeup' : 'avatar-system' }
function makeupArtistAccount(makeupArtistId: string) { return accounts.value.find(account => account.makeupArtistId === makeupArtistId) }
function canTestSwitch(account: any) { return mockLoginEnabled && !!account && !!(account.dingTalkUserId || account.username) }
async function switchTestAccount(account: any) {
  if (!canTestSwitch(account)) return
  try {
    const next = await api.mockLogin(account.dingTalkUserId || account.username)
    window.location.assign(next.role === 'STREAMER' ? '/booking' : '/admin')
  } catch (error) { showApiError(error, '测试账号切换失败。') }
}
function visibleRows<T>(rows: T[], section: string) { return expandedSections.value[section] ? rows : rows.slice(0, 5) }
async function toggleSection(section: string) { const anchor=document.activeElement instanceof HTMLElement?document.activeElement:null;const before=anchor?.getBoundingClientRect().top??0;expandedSections.value[section] = !expandedSections.value[section];await nextTick();if(anchor){const delta=anchor.getBoundingClientRect().top-before;if(Math.abs(delta)>1)window.scrollBy({top:delta,behavior:'smooth'})} }
function enabledCount(rows: Array<{ active?: boolean }>) { return rows.filter(row => row.active).length }

async function init() {
  try {
    user.value = await restoreSession()
    if (user.value.role === 'STREAMER') { await router.replace('/booking'); return }
    const requestedTab = typeof route.query.tab === 'string' ? route.query.tab : 'appointments'
    tab.value = requestedTab === 'create' && canCreate.value ? 'create' : requestedTab === 'analytics' && canAnalytics.value ? 'analytics' : requestedTab === 'settings' && user.value.role !== 'MAKEUP' ? 'settings' : 'appointments'
    if(route.query.tab!==tab.value)await router.replace({query:{...route.query,tab:tab.value}})
    if (user.value.role === 'MAKEUP' && user.value.makeupArtistId) filters.value.makeupArtist = user.value.makeupArtistId
    await Promise.all([loadAppointments(), loadResources(),tab.value==='analytics'?loadAnalytics():Promise.resolve()])
    if(tab.value==='create')await loadBookingOptions()
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) await router.replace('/login')
    else showApiError(error, '加载失败。')
  } finally { loading.value = false }
}

function appointmentParams(exporting = false) {
  const q = new URLSearchParams()
  const effectiveFilters = historical.value ? (filterApplied.value ? appliedFilters.value : { streamer:'', makeupArtist:'', attendanceStatus:'', status:'' }) : filters.value
  if (historical.value && filterApplied.value) { q.set('startDate', appliedRange.value.startDate); q.set('endDate', appliedRange.value.endDate) }
  else q.set('date', filterDate.value)
  if (effectiveFilters.streamer) q.set('streamer', effectiveFilters.streamer)
  if (effectiveFilters.makeupArtist) q.set('makeupArtist', effectiveFilters.makeupArtist)
  if (effectiveFilters.attendanceStatus) q.set('attendanceStatus', effectiveFilters.attendanceStatus)
  if (effectiveFilters.status) q.set('status', effectiveFilters.status)
  if (!exporting) { q.set('page', String(page.value)); q.set('size', String(pageSize.value)) }
  return q
}

async function loadAppointments() {
  if (historical.value && range.value.startDate > range.value.endDate) { showToast('开始日期不能晚于截止日期。', 'warning'); return }
  try {
    const data = await api.adminAppointments(appointmentParams())
    appointments.value = data.items
    total.value = data.total ?? data.items.length
    page.value = data.page ?? 1
    pageSize.value = data.size ?? pageSize.value
    totalPages.value = data.totalPages ?? Math.max(1, Math.ceil(total.value / pageSize.value))
  } catch (error) { showApiError(error, '预约加载失败。') }
}

function applyPreset(preset: RangePreset) { activePreset.value = preset; range.value = dateRangePreset(preset) }
async function applyFilters() { appliedFilters.value={...filters.value};appliedRange.value={...range.value};const noExtra=!Object.values(filters.value).some(Boolean);const quick=range.value.startDate===range.value.endDate&&(range.value.startDate===shanghaiDate()||range.value.startDate===shanghaiDate(1));if(noExtra&&quick){filterDate.value=range.value.startDate;filterApplied.value=false}else filterApplied.value=true;filterOpen.value = false; page.value = 1; await loadAppointments() }
async function clearFilters() {
  filters.value = { streamer: '', makeupArtist: user.value?.role === 'MAKEUP' ? user.value.makeupArtistId ?? '' : '', attendanceStatus: '', status: '' }
  appliedFilters.value={...filters.value};range.value = { startDate: filterDate.value, endDate: filterDate.value };appliedRange.value={...range.value}; activePreset.value = ''; filterApplied.value = false; filterOpen.value = true; page.value = 1; pageSize.value = 30; await loadAppointments()
}
async function changePage(next: number) { page.value = next; await loadAppointments() }
async function changePageSize() { page.value = 1; await loadAppointments() }
async function selectAppointmentDate(date: string) { filterDate.value = date; range.value = { startDate: date, endDate: date }; activePreset.value = ''; filterApplied.value = false; filterOpen.value = false; page.value = 1; await loadAppointments() }
function selectCreateDate(mode: 'today' | 'tomorrow') { dateMode.value = mode }
function selectTab(value:'appointments'|'create'|'analytics'|'settings'){tab.value=value;void router.replace({query:{...route.query,tab:value}});if(value==='analytics'&&!analytics.value)void loadAnalytics()}
function openCreateTab() { dateMode.value = filterApplied.value ? null : filterDate.value === shanghaiDate(1) ? 'tomorrow' : 'today'; createForm.value={streamerUserId:'',makeupArtistId:'',teamId:'',startTime:'',reason:''}; selectTab('create'); void loadBookingOptions() }
async function loadBookingOptions(){if(!selectedDate.value){bookingOptions.value=null;return}try{bookingOptions.value=await api.adminBookingOptions(selectedDate.value);if(user.value?.role==='MAKEUP')createForm.value.makeupArtistId=user.value.makeupArtistId??''}catch(error){showApiError(error,'可预约资源加载失败。')}}
async function loadCreateAvailability() {
  const artistId = user.value?.role === 'MAKEUP' ? user.value.makeupArtistId : createForm.value.makeupArtistId
  if (!selectedDate.value || !artistId) { createSlots.value = []; createForm.value.startTime = ''; return }
  try { const result = await api.adminAvailability(selectedDate.value, artistId); createSlots.value = result.slots; if (!result.slots.some(slot => slot.available && slot.time === createForm.value.startTime)) createForm.value.startTime = '' }
  catch (error) { createSlots.value = []; createForm.value.startTime = ''; showApiError(error, '可预约时间加载失败。') }
}

async function exportAppointments() {
  try {
    exporting.value = true
    const result = await api.exportAppointments(appointmentParams(true))
    const url = URL.createObjectURL(result.blob)
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = result.filename; anchor.click()
    URL.revokeObjectURL(url)
    exportOpen.value = false; showToast('预约记录已导出。', 'success')
  } catch (error) { showApiError(error, '导出失败。') }
  finally { exporting.value = false }
}
async function openCardDialog() {
  cardDate.value = filterApplied.value ? null : filterDate.value === shanghaiDate(1) ? 'tomorrow' : 'today'
  cardDateOpen.value = true; cardStatusLoading.value = true
  try { cardStatuses.value = (await api.scheduleCardStatus()).dates } catch (error) { showApiError(error, '卡片状态加载失败。') } finally { cardStatusLoading.value = false }
}
async function sendCard() {
  if (!cardDate.value) return
  try {
    cardSending.value = true
    await api.sendScheduleCard(shanghaiDate(cardDate.value === 'today' ? 0 : 1))
    cardDateOpen.value = false; showToast('安排卡片已进入发送队列。', 'success')
  } catch (error) { showApiError(error, '卡片触发失败。') } finally { cardSending.value = false }
}
async function openDetail(item: Appointment) { try { detail.value = await api.adminAppointmentDetail(item.id) } catch (error) { showApiError(error, '详情加载失败。') } }
async function openAction(kind: 'modify' | 'cancel', item: any) { detail.value = item; action.value = kind; editForm.value = { makeupArtistId: item.makeupArtistId, teamId: item.teamId, startTime: item.startTime, reason: '' };if(kind==='modify'){try{bookingOptions.value=await api.adminBookingOptions(item.bookingDate,item.id);await loadEditAvailability()}catch(error){showApiError(error,'可修改资源加载失败。')}} }
async function loadEditAvailability(){if(!detail.value||!editForm.value.makeupArtistId){editSlots.value=[];return}try{const result=await api.adminAvailability(detail.value.bookingDate,editForm.value.makeupArtistId,detail.value.id);editSlots.value=result.slots;if(!result.slots.some(slot=>slot.available&&slot.time===editForm.value.startTime))editForm.value.startTime=''}catch(error){editSlots.value=[];showApiError(error,'可预约时间加载失败。')}}
async function runAction() {
  if (!detail.value || !action.value) return
  if(action.value==='modify'&&selectedEditSlot.value?.conflict){overlapConfirmAction.value='modify';return}
  await executeAction()
}
async function executeAction(){
  if(!detail.value||!action.value)return
  try {
    if (action.value === 'modify') await api.adminUpdate(detail.value.id, { ...editForm.value, version: detail.value.version })
    else await api.adminCommand(`${detail.value.id}/cancel`, { version: detail.value.version, reason: editForm.value.reason })
    showToast(action.value === 'modify' ? '预约已修改。' : '预约已取消。', 'success'); action.value = null; detail.value = null; await loadAppointments()
  } catch (error) { showApiError(error, '操作失败。') }
}
async function adminCreate() {
  if (!selectedDate.value) return
  if(selectedCreateSlot.value?.conflict){overlapConfirmAction.value='create';return}
  await executeCreate()
}
async function executeCreate(){
  if(!selectedDate.value)return
  try {
    await api.adminCreate({ ...createForm.value, makeupArtistId: user.value?.role === 'MAKEUP' ? user.value.makeupArtistId : createForm.value.makeupArtistId, bookingDate: selectedDate.value })
    createForm.value = { streamerUserId: '', makeupArtistId: '', teamId: '', startTime: '', reason: '' }
    bookingOptions.value=null;showToast('代预约成功。', 'success'); selectTab('appointments'); await loadAppointments()
  } catch (error) { showApiError(error, '代预约失败。') }
}
async function confirmOverlap(){const pending=overlapConfirmAction.value;overlapConfirmAction.value=null;if(pending==='create')await executeCreate();else if(pending==='modify')await executeAction()}
function analyticsPresetRange(preset:'today'|'7d'|'30d'|'month'){analyticsPreset.value=preset;const now=shanghaiDate();if(preset==='today')analyticsRange.value={startDate:now,endDate:now};else if(preset==='month')analyticsRange.value={startDate:`${now.slice(0,7)}-01`,endDate:now};else analyticsRange.value={startDate:shanghaiDate(preset==='7d'?-6:-29),endDate:now};void loadAnalytics()}
async function loadAnalytics(){if(!canAnalytics.value)return;analyticsLoading.value=true;try{analytics.value=await api.adminAnalytics(analyticsRange.value.startDate,analyticsRange.value.endDate)}catch(error){showApiError(error,'统计数据加载失败。')}finally{analyticsLoading.value=false}}
async function loadResources() {
  try {
    const basic = await Promise.all([api.makeupArtists(), api.teams(), api.streamers()])
    makeupArtists.value = basic[0]; teams.value = basic[1]; streamers.value = basic[2]
    if (user.value?.role !== 'MAKEUP') { const extra = await Promise.all([api.accounts(), api.systemSetting()]); accounts.value = extra[0]; setting.value = extra[1] }
  } catch (error) { showApiError(error, '基础数据加载失败。') }
}
async function toggleSystem() { if (!isAdmin.value || !setting.value) return; try { setting.value = await api.updateSystemSetting({ enabled: !setting.value.enabled, version: setting.value.version }); showToast('系统状态已更新。', 'success') } catch (error) { showApiError(error, '系统设置失败。') } }
function openResource(kind: 'makeupArtist' | 'team', item: any = null) {
  resourceEditor.value = { kind, item }
  resourceForm.value = resourceValues(kind, item)
}
function resourceValues(kind: 'makeupArtist' | 'team', item: any = null) {
  return kind === 'makeupArtist'
    ? { name: item?.name ?? '', imageUrl: item?.avatarUrl ?? '', workDays: item?.workDays ?? [1, 2, 3, 4, 5, 6, 7], workStart: minute(item?.workStart) === '—' ? '08:00' : minute(item.workStart), workEnd: minute(item?.workEnd) === '—' ? '20:00' : minute(item.workEnd), scheduleEnabled: item?.scheduleEnabled ?? true, active: item?.active ?? true, attending: item?.attending ?? true, version: item?.version ?? 0 }
    : { name: item?.name ?? '', imageUrl: item?.logoUrl ?? '', active: item?.active ?? true, version: item?.version ?? 0 }
}
function toggleWorkday(day: number) { if (resourceForm.value.scheduleEnabled === false) return; const days: number[] = resourceForm.value.workDays ?? []; resourceForm.value.workDays = days.includes(day) ? days.filter(value => value !== day) : [...days, day].sort((a, b) => a - b) }
function validateSchedule() {
  if (resourceForm.value.scheduleEnabled === false) return true
  if (!resourceForm.value.workDays?.length) { showToast('请至少选择一个工作日。', 'warning'); return false }
  if (!resourceForm.value.workStart || !resourceForm.value.workEnd || resourceForm.value.workStart >= resourceForm.value.workEnd) { showToast('上班时间必须早于下班时间。', 'warning'); return false }
  return true
}
async function uploadResource(event: Event) { const file = (event.target as HTMLInputElement).files?.[0]; if (!file) return; try { resourceForm.value.imageUrl = (await api.uploadImage(file)).url; showToast('图片已上传。', 'success') } catch (error) { showApiError(error, '图片上传失败。') } }
async function saveResource() {
  if (!resourceEditor.value || !resourceCanSave.value) return
  const { kind, item } = resourceEditor.value
  if (item && !resourceEditorDirty.value) { resourceEditor.value = null; return }
  if (kind === 'makeupArtist' && !validateSchedule()) return
  try {
    if (kind === 'makeupArtist') {
      const body = { name: resourceForm.value.name.trim(), avatarUrl: resourceForm.value.imageUrl, workDays: resourceForm.value.workDays, workStart: resourceForm.value.workStart, workEnd: resourceForm.value.workEnd, scheduleEnabled: resourceForm.value.scheduleEnabled, active: resourceForm.value.active, attending: resourceForm.value.attending, version: resourceForm.value.version }
      if (item) await api.updateMakeupArtist(item.id, body); else await api.createMakeupArtist(body)
    } else {
      const body = { name: resourceForm.value.name.trim(), logoUrl: resourceForm.value.imageUrl, active: resourceForm.value.active, version: resourceForm.value.version }
      if (item) await api.updateTeam(item.id, body); else await api.createTeam(body)
    }
    resourceEditor.value = null; showToast((kind === 'makeupArtist' ? '化妆师' : '团队') + (item ? '已保存。' : '已添加。'), 'success'); await loadResources()
  } catch (error) { showApiError(error, '资源保存失败。') }
}
async function openAccount(item: any = null, role = 'STREAMER') {
  accountEditor.value = item ?? {}
  makeupArtistEditorItem.value = null
  assignedCredential.value = null
  revokePasswordOpen.value = false
  dingTalkOpen.value = false
  dingTalkSearching.value = false
  if (item) {
    accountForm.value = { ...item, canCreateAppointments: item.canCreateAppointments ?? ['MAKEUP', 'OPERATOR'].includes(item.role) }
    resourceForm.value = resourceValues('makeupArtist')
    dingTalkQuery.value = item.dingTalkUsername ? `${item.dingTalkUsername} @${item.dingTalkUserId}` : ''
    return
  }
  accountCreateKind.value = role === 'OBSERVER' || role === 'OPERATOR' ? 'ADMIN' : role as 'MAKEUP' | 'STREAMER'
  const initialRole = accountCreateKind.value === 'ADMIN' ? 'OBSERVER' : accountCreateKind.value
  accountForm.value = { dingTalkUserId: '', dingTalkUsername: '', nickname: '', role: initialRole, active: true, attending: true, canModifyAppointments: false, canCancelAppointments: false, canCreateAppointments: ['MAKEUP', 'OPERATOR'].includes(initialRole) }
  resourceForm.value = resourceValues('makeupArtist')
  dingTalkQuery.value = ''
}
async function openMakeupArtistEditor(item: any) {
  const account = makeupArtistAccount(item.id)
  if (!account) { openResource('makeupArtist', item); return }
  await openAccount(account)
  makeupArtistEditorItem.value = item
  resourceForm.value = resourceValues('makeupArtist', item)
}
function closeAccountEditor() {
  accountEditor.value = null
  makeupArtistEditorItem.value = null
  assignedCredential.value = null
  dingTalkOpen.value = false
}
async function loadDingTalkEmployees() {
  dingTalkLoading.value = true
  try { dingTalkEmployees.value = await api.dingTalkEmployees(dingTalkQuery.value.trim()) }
  catch (error) { dingTalkEmployees.value = []; showApiError(error, '钉钉员工加载失败。') }
  finally { dingTalkLoading.value = false }
}
async function searchDingTalk() {
  dingTalkOpen.value = true
  accountForm.value.dingTalkUserId = ''
  accountForm.value.dingTalkUsername = ''
  await loadDingTalkEmployees()
}
async function openDingTalkOptions() { dingTalkOpen.value = true; await loadDingTalkEmployees() }
function selectDingTalk(employee: {dingTalkUserId:string;dingTalkUsername:string;label:string}) {
  accountForm.value.dingTalkUserId = employee.dingTalkUserId
  accountForm.value.dingTalkUsername = employee.dingTalkUsername
  accountForm.value.nickname = employee.dingTalkUsername
  dingTalkQuery.value = employee.label
  dingTalkOpen.value = false
}
function onAccountRoleChange(role: string) {
  const previous = accountForm.value.role
  accountForm.value.role = role
  if (['MAKEUP', 'OPERATOR'].includes(role) && previous !== role) accountForm.value.canCreateAppointments = true
  if (role === 'OPERATOR' && previous !== role) { accountForm.value.canModifyAppointments = false; accountForm.value.canCancelAppointments = false }
  if (['MAKEUP', 'STREAMER'].includes(role) && !['MAKEUP', 'STREAMER'].includes(previous)) accountForm.value.attending = true
}
function toggleAccountAttendance() {
  if (accountForm.value.role === 'MAKEUP') resourceForm.value.attending = !resourceForm.value.attending
  else accountForm.value.attending = !accountForm.value.attending
}
async function saveAccount() {
  const nickname = accountForm.value.nickname?.trim() ?? ''
  const duplicate = accounts.value.find(item => item.id !== accountEditor.value?.id && item.nickname?.trim().toLocaleLowerCase() === nickname.toLocaleLowerCase())
  if (duplicate) { showToast('昵称已存在，请使用其他昵称。', 'warning'); return }
  if (accountForm.value.role === 'MAKEUP' && !validateSchedule()) return
  try {
    if (!accountEditor.value?.id) {
      const created = await api.createAccount({ dingTalkUserId: accountForm.value.dingTalkUserId, dingTalkUsername: accountForm.value.dingTalkUsername, nickname, role: accountForm.value.role, active: accountForm.value.active, attending: accountForm.value.role === 'MAKEUP' ? resourceForm.value.attending : accountForm.value.attending, canModifyAppointments: accountForm.value.canModifyAppointments, canCancelAppointments: accountForm.value.canCancelAppointments, canCreateAppointments: accountForm.value.canCreateAppointments })
      if (accountForm.value.role === 'MAKEUP' && created.makeupArtistId) await api.updateMakeupArtist(created.makeupArtistId, { name: nickname, avatarUrl: resourceForm.value.imageUrl, workDays: resourceForm.value.workDays, workStart: resourceForm.value.workStart, workEnd: resourceForm.value.workEnd, scheduleEnabled: resourceForm.value.scheduleEnabled, active: accountForm.value.active, attending: resourceForm.value.attending, version: 0 })
      closeAccountEditor(); showToast('账号已添加。', 'success'); await loadResources(); return
    }
    if (!accountDirty.value && !resourceDirty.value) { closeAccountEditor(); return }
    const accountBody = { nickname: accountForm.value.nickname.trim(), role: accountForm.value.role, active: accountForm.value.active, attending: accountForm.value.attending, canModifyAppointments: accountForm.value.canModifyAppointments, canCancelAppointments: accountForm.value.canCancelAppointments, canCreateAppointments: accountForm.value.canCreateAppointments, version: accountForm.value.version }
    const accountChanged = accountDirty.value
    const originalResource = makeupArtistEditorItem.value
    const resourceBody = originalResource ? { name: accountBody.nickname, avatarUrl: resourceForm.value.imageUrl, workDays: resourceForm.value.workDays, workStart: resourceForm.value.workStart, workEnd: resourceForm.value.workEnd, scheduleEnabled: resourceForm.value.scheduleEnabled, active: accountBody.active, attending: resourceForm.value.attending, version: resourceForm.value.version } : null
    if (resourceDirty.value && resourceBody) await api.updateMakeupArtist(originalResource.id, resourceBody)
    const updated = accountChanged ? await api.updateAccount(accountEditor.value.id, accountBody) : accountEditor.value
    if (!originalResource && accountForm.value.role === 'MAKEUP' && updated.makeupArtistId) await api.updateMakeupArtist(updated.makeupArtistId, { name: accountBody.nickname, avatarUrl: resourceForm.value.imageUrl, workDays: resourceForm.value.workDays, workStart: resourceForm.value.workStart, workEnd: resourceForm.value.workEnd, scheduleEnabled: resourceForm.value.scheduleEnabled, active: accountBody.active, attending: resourceForm.value.attending, version: 0 })
    closeAccountEditor(); showToast('账号已保存。', 'success'); await loadResources()
  } catch (error) { showApiError(error, '账号保存失败。') }
}
async function refreshEditedAccount() {
  await loadResources()
  const refreshed = accounts.value.find(item => item.id === accountEditor.value?.id)
  if (refreshed) { accountEditor.value = refreshed; accountForm.value = { ...refreshed } }
}
async function assignPassword() {
  if (!accountEditor.value?.id) return
  try { assignedCredential.value = await api.assignAccountPassword(accountEditor.value.id); await refreshEditedAccount(); showToast('账号密码已分配。', 'success') }
  catch (error) { showApiError(error, '密码分配失败。') }
}
async function copyCredential() {
  if (!assignedCredential.value) return
  try { await navigator.clipboard.writeText(`称呼：${accountForm.value.nickname}\n账号：${assignedCredential.value.username}\n密码：${assignedCredential.value.password}\n登录：${window.location.origin}`); showToast('账号密码已复制。', 'success') }
  catch { showToast('复制失败，请手动复制。', 'error') }
}
async function revokePassword() {
  if (!accountEditor.value?.id) return
  try { await api.revokeAccountPassword(accountEditor.value.id); revokePasswordOpen.value = false; assignedCredential.value = null; await refreshEditedAccount(); showToast('账号密码已回收。', 'success') }
  catch (error) { showApiError(error, '密码回收失败。') }
}
async function logout() { await signOut(); await router.replace('/login') }
watch(dateMode,async()=>{createForm.value.streamerUserId='';createForm.value.makeupArtistId=user.value?.role==='MAKEUP'?user.value.makeupArtistId??'':'';createForm.value.teamId='';createForm.value.startTime='';await loadBookingOptions();await loadCreateAvailability()})
watch(()=>createForm.value.makeupArtistId,loadCreateAvailability)
watch(()=>editForm.value.makeupArtistId,()=>{if(action.value==='modify')void loadEditAvailability()})
watch(()=>route.query.tab,value=>{if(value==='appointments'||value==='create'||value==='analytics'||value==='settings')tab.value=value})
onMounted(init)
</script>

<template>
  <main class="admin-app">
    <aside class="admin-sidebar">
      <BrandMark compact />
      <nav>
        <button :class="{ active: tab === 'appointments' }" @click="selectTab('appointments')"><CalendarRange />预约</button>
        <button v-if="canCreate" :class="{ active: tab === 'create' }" @click="openCreateTab"><CalendarPlus />代预约</button>
        <button v-if="canAnalytics" :class="{ active: tab === 'analytics' }" @click="selectTab('analytics')"><BarChart3 />统计</button>
        <button v-if="user?.role !== 'MAKEUP'" :class="{ active: tab === 'settings' }" @click="selectTab('settings')"><Settings2 />设置</button>
      </nav>
      <div class="admin-user">
        <span>{{ roleLabel(user?.role ?? '') }} · {{ user?.nickname }}</span>
        <button class="admin-avatar avatar-menu-button" :class="roleAvatarClass(user?.role)" aria-label="打开账号菜单" @click="logoutOpen = true">{{ avatarInitial(user?.nickname) }}</button>
      </div>
    </aside>
    <section class="admin-main">
      <div v-if="loading" class="loading-state"><span /><span /><span /></div>
      <template v-else>
        <section v-if="tab === 'appointments'" class="record-shell">
          <DateTabs :model-value="filterApplied ? '' : filterDate" :options="appointmentDates" @update:model-value="selectAppointmentDate" />
          <div class="record-results">
            <div class="record-total">共 <b>{{ total }}</b> 条记录</div>
            <div class="record-actions" :class="{ 'limited-actions': !isAdmin }">
              <button v-if="historical" class="record-action" :class="{ active: filterApplied || filterOpen }" aria-label="筛选预约记录" title="筛选" @click="filterOpen = !filterOpen"><SlidersHorizontal :size="17" /><span>筛选</span></button>
              <button class="record-action" aria-label="查看预约规则" title="规则" @click="rulesOpen = true"><BookOpen :size="17" /><span>规则</span></button>
              <button v-if="isAdmin" class="record-action" aria-label="导出预约数据" title="导出数据" @click="exportOpen = true"><Download :size="17" /><span>导出</span></button>
              <button v-if="isAdmin" class="record-action primary-action" aria-label="发送钉钉群卡片" title="发卡" @click="openCardDialog"><Send :size="16" /><span>发卡</span></button>
            </div>
          </div>
          <div v-if="historical && filterOpen" class="record-filter">
            <div class="range-presets">
              <button class="preset-button" :class="{ active: activePreset === 'yesterday' }" @click="applyPreset('yesterday')">昨天</button>
              <button class="preset-button" :class="{ active: activePreset === '7d' }" @click="applyPreset('7d')">近7天</button>
              <button class="preset-button" :class="{ active: activePreset === '30d' }" @click="applyPreset('30d')">近30天</button>
            </div>
            <div class="record-filter-grid">
              <label><span>开始</span><AppDatePicker v-model="range.startDate" label="开始日期" @update:model-value="activePreset = ''" /></label>
              <label><span>截止</span><AppDatePicker v-model="range.endDate" label="截止日期" @update:model-value="activePreset = ''" /></label>
              <label><span>主播</span><PolishedSelect v-model="filters.streamer" :options="streamerOptions" placeholder="全部主播" aria-label="主播筛选" /></label>
              <label><span>化妆师</span><PolishedSelect v-model="filters.makeupArtist" :options="allMakeupArtistOptions" placeholder="全部化妆师" aria-label="化妆师筛选" /></label>
              <label><span>签到状态</span><PolishedSelect v-model="filters.attendanceStatus" :options="attendanceOptions" placeholder="全部签到状态" aria-label="签到状态筛选" /></label>
              <label><span>预约状态</span><PolishedSelect v-model="filters.status" :options="statusOptions" placeholder="全部预约状态" aria-label="预约状态筛选" /></label>
            </div>
            <div class="record-filter-foot"><button class="button secondary" @click="clearFilters">重置</button><button class="button primary" @click="applyFilters">查询</button></div>
          </div>
          <div v-if="appointments.length" class="desktop-table">
            <table><thead><tr><th>日期</th><th>时间</th><th>主播</th><th>团队</th><th>化妆师</th><th>签到</th><th>状态</th><th /></tr></thead>
              <tbody><tr v-for="item in appointments" :key="item.id"><td>{{ item.bookingDate }}</td><td><strong><TimeText :value="item.startTime" /></strong><small v-if="item.conflictOverride" class="overlap-badge">时间重叠</small></td><td>{{ item.streamerName }}</td><td>{{ item.teamName }}</td><td>{{ item.makeupArtistName }}</td><td><span class="attendance-badge" :class="item.attendanceStatus.toLowerCase()">{{ attendanceLabel(item.attendanceStatus) }}</span></td><td><span class="status" :class="item.status.toLowerCase()">{{ statusLabel(item.status) }}</span></td><td><button class="icon-button" aria-label="查看预约详情" @click="openDetail(item)"><ChevronRight :size="18" /></button></td></tr></tbody>
            </table>
          </div>
          <div v-if="appointments.length" class="mobile-admin-list">
            <button v-for="item in appointments" :key="item.id" @click="openDetail(item)">
              <span><TimeText :value="item.startTime" /><small class="mobile-record-date">{{ shortDate(item.bookingDate) }}</small></span>
              <span><b>{{ item.streamerName }}</b><small>{{ item.makeupArtistName }} · {{ item.teamName }}</small><small v-if="item.conflictOverride" class="overlap-badge">时间重叠</small></span>
              <span class="mobile-status-stack"><em class="attendance-badge" :class="item.attendanceStatus.toLowerCase()">{{ attendanceLabel(item.attendanceStatus) }}</em><em class="status" :class="item.status.toLowerCase()">{{ statusLabel(item.status) }}</em></span>
            </button>
          </div>
          <div v-else class="empty-records">当前条件下暂无预约记录</div>
          <div v-if="total > 30" class="pager" aria-label="分页">
            <button class="page-button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button><span>第 {{ page }} / {{ totalPages }} 页</span><button class="page-button" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
            <select v-model.number="pageSize" class="page-size" aria-label="每页条数" @change="changePageSize"><option :value="30">30条/页</option><option :value="50">50条/页</option><option :value="100">100条/页</option></select>
          </div>
        </section>
        <section v-else-if="tab === 'create'" class="admin-form-card">
          <div class="form-grid">
            <div class="admin-date-field"><span>预约日期</span><div class="admin-date-choice"><button v-for="option in [{ value: 'today', label: '今天' }, { value: 'tomorrow', label: '明天' }]" :key="option.value" type="button" :class="{ active: dateMode === option.value }" @click="selectCreateDate(option.value as 'today' | 'tomorrow')"><b>{{ option.label }}</b><small>{{ option.value === 'today' ? shanghaiDate() : shanghaiDate(1) }}</small></button></div><small v-if="!dateMode" class="field-hint">请先选择预约日期</small></div>
            <label><span>主播</span><PolishedSelect v-model="createForm.streamerUserId" :options="createStreamerOptions" placeholder="请选择主播" aria-label="主播" /></label>
            <label v-if="user?.role !== 'MAKEUP'"><span>化妆师</span><PolishedSelect v-model="createForm.makeupArtistId" :options="createMakeupOptions" placeholder="请选择化妆师" aria-label="化妆师" /></label>
            <label><span>团队</span><PolishedSelect v-model="createForm.teamId" :options="createTeamOptions" placeholder="请选择团队" aria-label="团队" /></label>
            <label class="wide"><span>时间</span><TimeWheel v-if="createSlots.length" v-model="createForm.startTime" :slots="createSlots" /><div v-else class="time-wheel-empty">选择化妆师后即可查看可预约时间</div><small v-if="selectedCreateSlot?.conflict" class="conflict-warning">该时段与已有预约重叠，提交前需要再次确认。</small></label>
            <label class="wide reason-field"><span>代预约原因（可选）</span><PolishedSelect v-model="createForm.reason" :options="createReasonOptions" placeholder="请选择原因" aria-label="代预约原因" /></label>
          </div>
          <button class="button primary admin-create-submit" :disabled="!selectedDate || !createForm.streamerUserId || (!createForm.makeupArtistId && user?.role !== 'MAKEUP') || !createForm.startTime || !createForm.teamId" @click="adminCreate">确认代预约</button>
        </section>
        <section v-else-if="tab === 'analytics'" class="analytics-page">
          <header class="analytics-toolbar"><div><h1>数据分析</h1><p>{{ analyticsRange.startDate }} 至 {{ analyticsRange.endDate }}</p></div><div class="analytics-presets"><button :class="{active:analyticsPreset==='today'}" @click="analyticsPresetRange('today')">今日</button><button :class="{active:analyticsPreset==='7d'}" @click="analyticsPresetRange('7d')">近7天</button><button :class="{active:analyticsPreset==='30d'}" @click="analyticsPresetRange('30d')">近30天</button><button :class="{active:analyticsPreset==='month'}" @click="analyticsPresetRange('month')">本月</button></div></header>
          <div class="analytics-custom"><AppDatePicker v-model="analyticsRange.startDate" label="统计开始日期" @update:model-value="analyticsPreset='custom'"/><AppDatePicker v-model="analyticsRange.endDate" label="统计截止日期" @update:model-value="analyticsPreset='custom'"/><button class="button primary" @click="loadAnalytics">查询</button></div>
          <div v-if="analyticsLoading" class="loading-state"><span/><span/></div>
          <template v-else-if="analytics">
            <div class="metric-grid"><article v-for="item in analyticsMetrics" :key="item.l"><span>{{ item.l }}</span><b>{{ item.v }}</b><small v-if="item.s">{{ item.s }}</small></article></div>
            <div v-if="analytics.summary.total>=5" class="analytics-grid">
              <article class="analytics-card"><h2>预约状态</h2><div class="donut" :style="{'--value':`${analytics.summary.total?analytics.summary.active/analytics.summary.total*100:0}%`}"><b>{{ Math.round(analytics.summary.total?analytics.summary.active/analytics.summary.total*100:0) }}%</b><small>有效率</small></div><div class="legend"><span><i class="active"/>有效 {{ analytics.summary.active }}</span><span><i class="cancelled"/>取消 {{ analytics.summary.cancelled }}</span></div></article>
              <article class="analytics-card"><h2>签到表现</h2><div class="horizontal-bars"><div v-for="item in [{l:'迟到',v:analytics.summary.late,c:'late'},{l:'未到',v:analytics.summary.notArrived,c:'absent'}]" :key="item.l"><span>{{ item.l }}</span><i><b :class="item.c" :style="{width:`${analytics.summary.total?Math.max(4,item.v/analytics.summary.total*100):0}%`}"/></i><strong>{{ item.v }}</strong></div></div></article>
              <article class="analytics-card analytics-trend"><h2>每日趋势</h2><div class="trend-bars"><i v-for="day in analytics.daily" :key="day.date" :title="`${day.date}：${day.total} 条`" :style="{height:`${Math.max(3,day.total/Math.max(1,...analytics.daily.map(x=>x.total))*100)}%`}"/></div></article>
              <article class="analytics-card analytics-ranking"><header><h2>主播排行</h2><select v-model="rankingMetric"><option value="appointments">预约次数</option><option value="late">迟到次数</option><option value="notArrived">未到次数</option><option value="modifications">修改次数</option><option value="cancellations">取消次数</option></select></header><ol><li v-for="(row,index) in rankedStreamers" :key="row.streamerId"><em>{{ index+1 }}</em><span>{{ row.streamerName }}</span><b>{{ row[rankingMetric] }}</b><small>预约 {{ row.appointments }} · 迟到 {{ row.late }} · 未到 {{ row.notArrived }} · 修改 {{ row.modifications }} · 取消 {{ row.cancellations }}</small></li></ol></article>
            </div>
            <div v-else class="analytics-insufficient">当前区间少于 5 条预约，汇总数据已保留；样本增多后将显示图表和排行。</div>
          </template>
        </section>
        <section v-else class="settings-grid">
          <article class="setting-card"><header><div class="setting-title"><h2>化妆师</h2><small>启用{{ enabledCount(makeupArtists) }}/{{ makeupArtists.length }}</small></div><button v-if="isAdmin" class="icon-action" aria-label="添加化妆师" title="添加化妆师" @click="openAccount(null, 'MAKEUP')"><Plus :size="18" /></button></header><ul><li v-for="makeupArtist in visibleRows(makeupArtists, 'makeup')" :key="makeupArtist.id" :class="{ inactive: !makeupArtist.active, 'test-switchable': canTestSwitch(makeupArtistAccount(makeupArtist.id)) }" :tabindex="canTestSwitch(makeupArtistAccount(makeupArtist.id)) ? 0 : undefined" @click="switchTestAccount(makeupArtistAccount(makeupArtist.id))" @keydown.enter="switchTestAccount(makeupArtistAccount(makeupArtist.id))"><span class="mini-avatar avatar-makeup">{{ avatarInitial(makeupArtist.name) }}</span><span><b>{{ makeupArtist.name }}</b><small v-if="!makeupArtist.active">已停用</small><small v-else>{{ minute(makeupArtist.workStart) }}–{{ minute(makeupArtist.workEnd) }}{{ makeupArtist.attending ? '' : ' · 休息中···' }}</small></span><button v-if="isAdmin" class="icon-action" :aria-label="`编辑${makeupArtist.name}`" :title="`编辑${makeupArtist.name}`" @click.stop="openMakeupArtistEditor(makeupArtist)"><UserCog :size="17" /></button></li></ul><button v-if="makeupArtists.length > 5" class="section-expander" @click="toggleSection('makeup')"><ChevronUp v-if="expandedSections.makeup" :size="17" /><ChevronDown v-else :size="17" />{{ expandedSections.makeup ? '收起' : `展开 ${makeupArtists.length - 5} 项` }}</button></article>
          <article class="setting-card"><header><div class="setting-title"><h2>主播</h2><small>启用{{ enabledCount(streamerAccounts) }}/{{ streamerAccounts.length }}</small></div><button v-if="isAdmin" class="icon-action" aria-label="添加主播" title="添加主播" @click="openAccount(null, 'STREAMER')"><Plus :size="18" /></button></header><ul><li v-for="account in visibleRows(streamerAccounts, 'streamer')" :key="account.id" :class="{ inactive: !account.active, 'test-switchable': canTestSwitch(account) }" :tabindex="canTestSwitch(account) ? 0 : undefined" @click="switchTestAccount(account)" @keydown.enter="switchTestAccount(account)"><span class="mini-avatar avatar-streamer">{{ avatarInitial(account.nickname) }}</span><span><b>{{ account.nickname }}</b><small v-if="!account.active">已停用</small><small v-else-if="!account.attending">休息中···</small></span><button v-if="isAdmin" class="icon-action" :aria-label="`管理${account.nickname}`" :title="`管理${account.nickname}`" @click.stop="openAccount(account)"><UserCog :size="17" /></button></li></ul><button v-if="streamerAccounts.length > 5" class="section-expander" @click="toggleSection('streamer')"><ChevronUp v-if="expandedSections.streamer" :size="17" /><ChevronDown v-else :size="17" />{{ expandedSections.streamer ? '收起' : `展开 ${streamerAccounts.length - 5} 项` }}</button></article>
          <article class="setting-card"><header><div class="setting-title"><h2>团队</h2><small>启用{{ enabledCount(teams) }}/{{ teams.length }}</small></div><button v-if="isAdmin" class="icon-action" aria-label="添加团队" title="添加团队" @click="openResource('team')"><Plus :size="18" /></button></header><ul><li v-for="team in visibleRows(teams, 'team')" :key="team.id" :class="{ inactive: !team.active }"><span class="mini-avatar team-avatar">{{ avatarInitial(team.name) }}</span><span><b>{{ team.name }}</b><small v-if="!team.active">已停用</small></span><button v-if="isAdmin" class="icon-action" :aria-label="`编辑${team.name}`" :title="`编辑${team.name}`" @click="openResource('team', team)"><UsersRound :size="17" /></button></li></ul><button v-if="teams.length > 5" class="section-expander" @click="toggleSection('team')"><ChevronUp v-if="expandedSections.team" :size="17" /><ChevronDown v-else :size="17" />{{ expandedSections.team ? '收起' : `展开 ${teams.length - 5} 项` }}</button></article>
          <article class="setting-card"><header><div class="setting-title"><h2>系统人员</h2><small>启用{{ enabledCount(administratorAccounts) }}/{{ administratorAccounts.length }}</small></div><button v-if="isAdmin" class="icon-action" aria-label="添加系统人员" title="添加系统人员" @click="openAccount(null, 'OBSERVER')"><Plus :size="18" /></button></header><ul><li v-for="account in visibleRows(administratorAccounts, 'administrator')" :key="account.id" :class="{ inactive: !account.active, 'test-switchable': canTestSwitch(account) }" :tabindex="canTestSwitch(account) ? 0 : undefined" @click="switchTestAccount(account)" @keydown.enter="switchTestAccount(account)"><span class="mini-avatar avatar-system">{{ avatarInitial(account.nickname) }}</span><span><b>{{ roleLabel(account.role) }} · {{ account.nickname }}</b><small v-if="!account.active">已停用</small></span><button v-if="isAdmin && account.role !== 'SUPER_ADMIN' && (isSuperAdmin || account.role !== 'OPERATOR')" class="icon-action" :aria-label="`管理${account.nickname}`" :title="`管理${account.nickname}`" @click.stop="openAccount(account)"><UserCog :size="17" /></button></li></ul><button v-if="administratorAccounts.length > 5" class="section-expander" @click="toggleSection('administrator')"><ChevronUp v-if="expandedSections.administrator" :size="17" /><ChevronDown v-else :size="17" />{{ expandedSections.administrator ? '收起' : `展开 ${administratorAccounts.length - 5} 项` }}</button></article>
          <article v-if="setting" class="setting-card system-entry-card"><header><div><h2>系统入口</h2><p>关闭后仅对管理人员开放。</p></div><button class="switch" :class="{ on: setting.enabled }" :disabled="!isAdmin" @click="toggleSystem"><i /></button></header></article>
        </section>
      </template>
    </section>

    <AppSheet :open="!!detail && !action" title="预约详情" :subtitle="detailSubtitle" @close="detail = null"><div v-if="detail" class="detail-stack detail-sections">
      <div class="detail-hero"><TimeText :value="detail.startTime" /><div class="detail-statuses"><span class="status" :class="detail.status.toLowerCase()">{{ statusLabel(detail.status) }}</span><span class="attendance-badge" :class="detail.attendanceStatus.toLowerCase()">{{ attendanceLabel(detail.attendanceStatus) }}</span></div></div>
      <section><h3>基础信息</h3><dl><div><dt>预约时间</dt><dd>{{ detail.bookingDate.slice(5) }} {{ detail.startTime.slice(0,5) }}</dd></div><div><dt>主播</dt><dd>{{ detail.streamerName }}</dd></div><div><dt>化妆师</dt><dd>{{ detail.makeupArtistName }}</dd></div><div><dt>团队</dt><dd>{{ detail.teamName }}</dd></div></dl></section>
      <section><h3>操作信息</h3><dl><div><dt>创建时间</dt><dd>{{ dateTime(detail.createdAt) }}</dd></div><div v-if="detail.updatedAt&&detail.updatedAt!==detail.createdAt"><dt>修改时间</dt><dd>{{ dateTime(detail.updatedAt) }}</dd></div><div><dt>创建来源</dt><dd>{{ sourceLabel(detail.source) }}</dd></div><div v-if="detail.source && detail.source !== 'STREAMER'"><dt>代预约</dt><dd>{{ detail.createdByName || '—' }}</dd></div></dl></section>
      <section><h3>修改记录</h3><div v-if="detail.modifications?.length" class="modification-list"><article v-for="(record,index) in detail.modifications" :key="index"><header><b>{{ record.actorName || '未知人员' }}</b><time>{{ dateTime(record.createdAt) }}</time></header><p v-if="record.reason">{{ record.reason }}</p><ul><li v-for="change in record.changes" :key="change.field"><span>{{ change.field }}</span><del>{{ change.before || '—' }}</del><i>→</i><ins>{{ change.after || '—' }}</ins></li></ul></article></div><p v-else class="empty-copy">暂无修改记录</p></section>
      <section v-if="hasOtherDetail"><h3>其他数据</h3><dl><div v-if="detail.attendanceEvidenceAt"><dt>签到时间</dt><dd>{{ dateTime(detail.attendanceEvidenceAt) }}</dd></div><div v-if="detail.cancelledAt"><dt>取消时间</dt><dd>{{ dateTime(detail.cancelledAt) }}</dd></div><div v-if="detail.cancelledByName"><dt>取消人</dt><dd>{{ detail.cancelledByName }}</dd></div><div v-if="detail.cancelReason"><dt>取消原因</dt><dd>{{ detail.cancelReason }}</dd></div><div v-if="detail.conflictOverride"><dt>时间重叠</dt><dd>是</dd></div></dl></section>
      <div v-if="detail.status === 'ACTIVE' && (canModify || canCancel)" class="action-row"><button v-if="canModify" class="appointment-action modify" @click="openAction('modify', detail)">修改</button><button v-if="canCancel" class="appointment-action cancel" @click="openAction('cancel', detail)"><X :size="17" />取消</button></div>
    </div></AppSheet>
    <AppSheet :open="!!action" :title="action === 'modify' ? '修改预约' : '取消预约'" :subtitle="detailSubtitle" @close="action = null"><div class="form-stack"><div class="appointment-confirm-card"><b>{{ detail?.streamerName }}</b><span>{{ detail?.bookingDate?.slice(5) }} {{ detail?.startTime?.slice(0,5) }} · {{ detail?.makeupArtistName }} · {{ detail?.teamName }}</span></div><template v-if="action === 'modify'"><label><span>化妆师</span><PolishedSelect v-model="editForm.makeupArtistId" :options="createMakeupOptions" aria-label="化妆师" /></label><label><span>时间</span><TimeWheel v-if="editSlots.length" v-model="editForm.startTime" :slots="editSlots"/><div v-else class="time-wheel-empty">请选择可用化妆师</div><small v-if="selectedEditSlot?.conflict" class="conflict-warning">该时段与已有预约重叠，提交前需要再次确认。</small></label><label><span>团队</span><PolishedSelect v-model="editForm.teamId" :options="createTeamOptions" aria-label="团队" /></label></template><label><span>原因（可选）</span><textarea v-model="editForm.reason" /></label></div><template #footer><button class="button primary full" @click="runAction">确认提交</button></template></AppSheet>
    <AppSheet :open="!!resourceEditor" :title="resourceEditorTitle" :subtitle="resourceEditorSubtitle" @close="resourceEditor = null">
      <div class="form-stack"><label><span>名称</span><input v-model="resourceForm.name" /></label>
        <template v-if="resourceEditor?.kind === 'makeupArtist'">
          <div class="toggle-grid"><div><span>资源启用</span><button class="switch" :class="{ on: resourceForm.active }" @click="resourceForm.active = !resourceForm.active"><i /></button></div><div><span>排班启用</span><button class="switch" :class="{ on: resourceForm.scheduleEnabled }" @click="resourceForm.scheduleEnabled = !resourceForm.scheduleEnabled"><i /></button></div><div><span>在岗出勤</span><button class="switch" :class="{ on: resourceForm.attending }" @click="resourceForm.attending = !resourceForm.attending"><i /></button></div></div>
          <div><span class="field-label">工作日</span><div class="weekday-grid"><button v-for="day in weekdays" :key="day.v" type="button" :disabled="!resourceForm.scheduleEnabled" :class="{ selected: resourceForm.workDays?.includes(day.v) }" @click="toggleWorkday(day.v)"><Check v-if="resourceForm.workDays?.includes(day.v)" :size="12" /><b>周{{ day.l }}</b></button></div></div>
          <div class="time-control-grid"><label><span>上班</span><input v-model="resourceForm.workStart" :disabled="!resourceForm.scheduleEnabled" type="time" step="600" /></label><label><span>下班</span><input v-model="resourceForm.workEnd" :disabled="!resourceForm.scheduleEnabled" type="time" step="600" /></label></div>
        </template>
        <div v-else class="toggle-grid"><div><span>启用选项</span><button class="switch" :class="{ on: resourceForm.active }" @click="resourceForm.active = !resourceForm.active"><i /></button></div></div>
        <label class="upload-control"><input type="file" accept="image/png,image/jpeg" @change="uploadResource" /><ImagePlus /><b>从相册选择图片</b><small>PNG/JPG，≤2MB</small></label>
      </div>
      <template #footer><button class="button primary full" :disabled="!resourceCanSave" @click="saveResource">{{ resourceSubmitLabel }}</button></template>
    </AppSheet>
    <AppSheet :open="!!accountEditor" :title="accountEditorTitle" :subtitle="accountEditorSubtitle" @close="closeAccountEditor">
      <div class="form-stack account-editor-form">
        <template v-if="accountEditor?.id">
          <label><span>昵称</span><input v-model="accountForm.nickname" maxlength="100" placeholder="系统内显示的昵称" /></label>
          <label><span>角色</span><PolishedSelect :model-value="accountForm.role" :options="editableRoleOptions" aria-label="角色" @update:model-value="onAccountRoleChange" /></label>
        </template>
        <template v-else>
          <label v-if="accountCreateKind === 'ADMIN'"><span>角色</span><PolishedSelect :model-value="accountForm.role" :options="createRoleOptions" aria-label="角色" @update:model-value="onAccountRoleChange" /></label>
          <div class="dingtalk-picker"><span class="field-label">钉钉</span><button type="button" class="dingtalk-picker-trigger" @click="openDingTalkOptions">{{ dingTalkQuery || '选择钉钉人员' }}<ChevronDown :size="17"/></button></div>
          <div v-if="dingTalkOpen" class="dingtalk-options" role="listbox" aria-label="钉钉员工选项">
            <button v-if="!dingTalkSearching" type="button" class="dingtalk-search-action" @click="dingTalkSearching=true">搜索人员</button>
            <input v-else v-model="dingTalkQuery" autocomplete="off" placeholder="输入姓名或钉钉 ID" aria-label="搜索钉钉员工" @input="searchDingTalk" />
            <p v-if="dingTalkLoading">正在查询钉钉员工…</p>
            <button v-for="employee in dingTalkEmployees" v-else :key="employee.dingTalkUserId" type="button" role="option" :aria-selected="accountForm.dingTalkUserId === employee.dingTalkUserId" :class="{ selected: accountForm.dingTalkUserId === employee.dingTalkUserId }" @click="selectDingTalk(employee)"><span>{{ employee.dingTalkUsername }}</span><small>@{{ employee.dingTalkUserId }}</small><Check v-if="accountForm.dingTalkUserId === employee.dingTalkUserId" :size="17" /></button>
            <p v-if="!dingTalkLoading && !dingTalkEmployees.length">没有可注册的同事</p>
          </div>
          <label><span>昵称</span><input v-model="accountForm.nickname" maxlength="100" placeholder="系统内显示的昵称" /></label>
        </template>
        <div class="toggle-grid">
          <div><span>账号启用</span><button class="switch" :class="{ on: accountForm.active }" @click="accountForm.active = !accountForm.active"><i /></button></div>
          <div v-if="['MAKEUP', 'STREAMER'].includes(accountForm.role)"><span>在岗出勤</span><button class="switch" :class="{ on: accountForm.role === 'MAKEUP' ? resourceForm.attending : accountForm.attending }" @click="toggleAccountAttendance"><i /></button></div>
          <div v-if="['MAKEUP', 'OPERATOR'].includes(accountForm.role)"><span>代预约</span><button class="switch" :class="{ on: accountForm.canCreateAppointments }" @click="accountForm.canCreateAppointments = !accountForm.canCreateAppointments"><i /></button></div>
          <div v-if="accountForm.role === 'MAKEUP'"><span>排班启用</span><button class="switch" :class="{ on: resourceForm.scheduleEnabled }" @click="resourceForm.scheduleEnabled = !resourceForm.scheduleEnabled"><i /></button></div>
        </div>
        <div v-if="accountForm.role === 'OPERATOR'" class="toggle-grid"><div><span>允许修改</span><button class="switch" :class="{ on: accountForm.canModifyAppointments }" @click="accountForm.canModifyAppointments = !accountForm.canModifyAppointments"><i /></button></div><div><span>允许取消</span><button class="switch" :class="{ on: accountForm.canCancelAppointments }" @click="accountForm.canCancelAppointments = !accountForm.canCancelAppointments"><i /></button></div></div>
        <template v-if="accountForm.role === 'MAKEUP'">
          <div><span class="field-label">工作日</span><div class="weekday-grid"><button v-for="day in weekdays" :key="day.v" type="button" :disabled="!resourceForm.scheduleEnabled" :class="{ selected: resourceForm.workDays?.includes(day.v) }" @click="toggleWorkday(day.v)"><Check v-if="resourceForm.workDays?.includes(day.v)" :size="12" /><b>周{{ day.l }}</b></button></div></div>
          <div class="time-control-grid"><label><span>上班</span><input v-model="resourceForm.workStart" :disabled="!resourceForm.scheduleEnabled" type="time" step="600" /></label><label><span>下班</span><input v-model="resourceForm.workEnd" :disabled="!resourceForm.scheduleEnabled" type="time" step="600" /></label></div>
          <label class="upload-control"><input type="file" accept="image/png,image/jpeg" @change="uploadResource" /><ImagePlus /><b>从相册选择图片</b><small>PNG/JPG，≤2MB</small></label>
        </template>
        <template v-if="accountEditor?.id">
          <div class="credential-actions"><button v-if="!accountForm.username" class="button secondary" @click="assignPassword"><KeyRound :size="17" />分配密码</button><button v-else class="button danger" @click="revokePasswordOpen = true">回收密码</button></div>
          <div v-if="assignedCredential" class="credential-card"><dl><div><dt>账号</dt><dd>{{ assignedCredential.username }}</dd></div><div><dt>密码</dt><dd>{{ assignedCredential.password }}</dd></div></dl><button class="button secondary full" @click="copyCredential"><Copy :size="17" />复制账号</button></div>
        </template>
      </div>
      <template #footer><button class="button primary full" :disabled="!accountCanSave" @click="saveAccount">{{ accountSubmitLabel }}</button></template>
    </AppSheet>
    <AppSheet :open="revokePasswordOpen" title="确认回收密码" @close="revokePasswordOpen = false"><div class="confirm-copy"><div class="warning-icon">!</div><p>回收后仅钉钉登录，账号密码登录需重新分配</p></div><template #footer><div class="two-buttons"><button class="button secondary" @click="revokePasswordOpen = false">暂不回收</button><button class="button danger-solid" @click="revokePassword">确认回收</button></div></template></AppSheet>
    <AppSheet :open="exportOpen" title="确认导出预约数据" @close="exportOpen = false"><dl class="export-summary"><div><dt>时间区间</dt><dd>{{ exportSummary.date }}</dd></div><div><dt>主播</dt><dd>{{ exportSummary.streamer }}</dd></div><div><dt>化妆师</dt><dd>{{ exportSummary.makeupArtist }}</dd></div><div><dt>签到状态</dt><dd>{{ exportSummary.attendance }}</dd></div><div><dt>预约状态</dt><dd>{{ exportSummary.status }}</dd></div></dl><template #footer><div class="two-buttons"><button class="button secondary" :disabled="exporting" @click="exportOpen = false">取消</button><button class="button primary" :disabled="exporting" @click="exportAppointments">{{ exporting ? '导出中…' : '确认导出' }}</button></div></template></AppSheet>
    <AppSheet :open="cardDateOpen" title="钉钉群卡片" @close="cardDateOpen = false"><div class="card-date-options"><button :class="{ active: cardDate === 'today' }" @click="cardDate = 'today'"><b>今天</b><br />{{ shanghaiDate() }}</button><button :class="{ active: cardDate === 'tomorrow' }" @click="cardDate = 'tomorrow'"><b>明天</b><br />{{ shanghaiDate(1) }}</button></div><template #footer><button class="button full card-submit" :class="{ resend: cardWasDelivered }" :disabled="cardSubmitDisabled" @click="sendCard"><RotateCcw v-if="cardWasDelivered" :size="17" />{{ cardWasDelivered ? '刷新卡片' : '确认发送' }}</button></template></AppSheet>
    <AppSheet :open="!!overlapConfirmAction" title="确认重叠预约" @close="overlapConfirmAction=null"><div class="confirm-copy"><div class="warning-icon">!</div><p>所选化妆师在该时段已有预约。确认现场可以协调后再继续提交。</p></div><template #footer><div class="two-buttons"><button class="button secondary" @click="overlapConfirmAction=null">返回调整</button><button class="button primary" @click="confirmOverlap">确认提交</button></div></template></AppSheet>
    <AppSheet :open="logoutOpen" title="退出登录" @close="logoutOpen = false"><p>确定要退出当前账号吗？</p><template #footer><div class="action-row"><button class="button secondary" @click="logoutOpen = false">取消</button><button class="button danger" @click="logout">退出登录</button></div></template></AppSheet>
    <BookingRulesSheet :open="rulesOpen" :role="user?.role ?? 'OBSERVER'" @close="rulesOpen = false" />
    <nav v-if="user?.role !== 'MAKEUP' || canCreate" class="admin-bottom" :class="{'four-tabs':canAnalytics&&canCreate}" aria-label="管理端导航"><button :class="{ active: tab === 'appointments' }" @click="selectTab('appointments')"><CalendarRange />预约</button><button v-if="canCreate" :class="{ active: tab === 'create' }" @click="openCreateTab"><CalendarPlus />代预约</button><button v-if="canAnalytics" :class="{active:tab==='analytics'}" @click="selectTab('analytics')"><BarChart3/>统计</button><button v-if="user?.role !== 'MAKEUP'" :class="{ active: tab === 'settings' }" @click="selectTab('settings')"><Settings2 />设置</button></nav>
    <AppToast :message="toast.message" :kind="toast.kind" />
  </main>
</template>

