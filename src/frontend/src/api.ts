import type { BookingContext, CurrentUser } from './types'

const API = '/api/v1'
let csrfToken = sessionStorage.getItem('jiabei-csrf') ?? ''

export class ApiError extends Error {
  constructor(public code: string, message: string, public status: number, public data?: unknown) {
    super(message)
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD'].includes((init.method ?? 'GET').toUpperCase()) && csrfToken) headers.set('X-CSRF-Token', csrfToken)
  const response = await fetch(`${API}${path}`, { credentials: 'include', ...init, headers })
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new ApiError(payload?.error?.code ?? 'NETWORK_ERROR', payload?.error?.message ?? '请求失败，请稍后重试。', response.status, payload?.data)
  return payload.data as T
}

export const api = {
  async me() {
    const user = await request<CurrentUser>('/me')
    csrfToken = user.csrfToken
    sessionStorage.setItem('jiabei-csrf', csrfToken)
    return user
  },
  async mockLogin(userId: string) {
    const user = await request<CurrentUser>('/auth/mock-login', { method: 'POST', body: JSON.stringify({ userId }) })
    csrfToken = user.csrfToken
    sessionStorage.setItem('jiabei-csrf', csrfToken)
    return user
  },
  async passwordLogin(username:string,password:string){
    const user=await request<CurrentUser>('/auth/password-login',{method:'POST',body:JSON.stringify({username,password})})
    csrfToken=user.csrfToken;sessionStorage.setItem('jiabei-csrf',csrfToken);return user
  },
  async dingTalkLogin(authCode:string,corpId:string){
    const user=await request<CurrentUser>('/auth/dingtalk-login',{method:'POST',body:JSON.stringify({authCode,corpId})})
    csrfToken=user.csrfToken;sessionStorage.setItem('jiabei-csrf',csrfToken);return user
  },
  changePassword:(currentPassword:string,newPassword:string)=>request<void>('/auth/change-password',{method:'POST',body:JSON.stringify({currentPassword,newPassword})}),
  logout: () => request<void>('/logout', { method: 'POST' }),
  bookingContext: (date?: string) => request<BookingContext>(`/booking-context${date ? `?date=${date}` : ''}`),
  availability: (date: string, makeupArtistId: string) => request<{ slots: Array<{ time: string; available: boolean; conflict?:boolean; reason?: string }>;conflictAllowed:boolean }>(`/availability?date=${date}&makeupArtistId=${makeupArtistId}`),
  createAppointment: (body: object) => request('/appointments', { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify(body) }),
  cancelAppointment: (id: string, version: number) => request(`/appointments/${id}/cancel`, { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ version }) }),
  modifyAppointment:(id:string,body:object)=>request(`/appointments/${id}`,{method:'PATCH',headers:{'Idempotency-Key':crypto.randomUUID()},body:JSON.stringify(body)}),
  adminAppointments: (params:URLSearchParams) => request<any>(`/admin/appointments?${params}`),
  async exportAppointments(params:URLSearchParams){
    const response=await fetch(`${API}/admin/appointments/export?${params}`,{credentials:'include'})
    if(!response.ok){const payload=await response.json().catch(()=>null);throw new ApiError(payload?.error?.code??'EXPORT_FAILED',payload?.error?.message??'导出失败，请稍后重试。',response.status,payload?.data)}
    const disposition=response.headers.get('Content-Disposition')??''
    const encoded=disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
    return {blob:await response.blob(),filename:encoded?decodeURIComponent(encoded):'加贝云·妆造预约记录.xlsx'}
  },
  adminAppointmentDetail: (id: string) => request<any>(`/admin/appointments/${id}`),
  adminCreate: (body: object) => request('/admin/appointments', { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify(body) }),
  adminCommand: (path: string, body: object) => request(`/admin/appointments/${path}`, { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify(body) }),
  adminUpdate: (id: string, body: object) => request(`/admin/appointments/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  makeupArtists: () => request<any[]>('/admin/makeup-artists'),
  teams: () => request<any[]>('/admin/teams'),
  accounts:()=>request<any[]>('/admin/accounts'),
  dingTalkEmployees:(query='')=>request<Array<{dingTalkUserId:string;dingTalkUsername:string;label:string}>>(`/admin/dingtalk-employees?query=${encodeURIComponent(query)}`),
  createAccount:(body:object)=>request<any>('/admin/accounts',{method:'POST',body:JSON.stringify(body)}),
  updateAccount:(id:string,body:object)=>request<any>(`/admin/accounts/${id}`,{method:'PATCH',body:JSON.stringify(body)}),
  assignAccountPassword:(id:string)=>request<{username:string;password:string}>(`/admin/accounts/${id}/assign-password`,{method:'POST'}),
  revokeAccountPassword:(id:string)=>request<void>(`/admin/accounts/${id}/revoke-password`,{method:'POST'}),
  streamers: () => request<Array<{userId:string;nickname:string}>>('/admin/streamers'),
  systemSetting: () => request<any>('/admin/system-setting'),
  updateSystemSetting: (body: object) => request('/admin/system-setting', { method: 'PATCH', body: JSON.stringify(body) }),
  createMakeupArtist: (body: object) => request<any>('/admin/makeup-artists', { method: 'POST', body: JSON.stringify(body) }),
  updateMakeupArtist: (id: string, body: object) => request<any>(`/admin/makeup-artists/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  createTeam: (body: object) => request<any>('/admin/teams', { method: 'POST', body: JSON.stringify(body) }),
  updateTeam: (id: string, body: object) => request<any>(`/admin/teams/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  sendScheduleCard:(bookingDate:string)=>request<any>('/admin/appointments/schedule-card',{method:'POST',body:JSON.stringify({bookingDate})}),
  scheduleCardStatus:()=>request<{dates:Array<{key:'today'|'tomorrow';bookingDate:string;hasSuccessfulDelivery:boolean;firstDeliveredAt?:string}>}>('/admin/appointments/schedule-card/status'),
  uploadImage: (file: File) => { const body=new FormData(); body.append('file',file); return request<{url:string;width:number;height:number}>('/admin/uploads/images', { method:'POST', body }) },
}
