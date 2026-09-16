export type Role = 'SUPER_ADMIN' | 'OPERATOR' | 'OBSERVER' | 'MAKEUP' | 'STREAMER'
export type AppointmentStatus = 'ACTIVE' | 'CANCELLED'
export type AttendanceStatus = 'PENDING' | 'ARRIVED' | 'NOT_ARRIVED' | 'LATE'
export interface CurrentUser { userId:string;dingTalkUserId?:string;dingTalkUsername?:string;nickname:string;role:Role;makeupArtistId?:string;canModifyAppointments:boolean;canCancelAppointments:boolean;canCreateAppointments:boolean;mustChangePassword:boolean;csrfToken:string;testMode?:boolean }
export interface MakeupArtist { id:string;name:string;avatarUrl?:string;workDays?:number[];workStart?:string;workEnd?:string;scheduleEnabled?:boolean;active?:boolean;attending?:boolean;version?:number;updatedAt?:string }
export interface Team { id:string;name:string;logoUrl?:string;active?:boolean;version?:number;updatedAt?:string }
export interface Appointment { id:string;bookingNumber?:string;bookingDate:string;startTime:string;streamerName:string;makeupArtistId:string;makeupArtistName:string;teamId:string;teamName:string;status:AppointmentStatus;attendanceStatus:AttendanceStatus;attendanceFrozen?:boolean;attendanceEvidenceAt?:string;conflictOverride?:boolean;version:number;changed?:boolean }
export interface AnalyticsSummary {total:number;active:number;cancelled:number;late:number;notArrived:number;modifications:number;cancellations:number;averageLateMinutes:number}
export interface AnalyticsData {startDate:string;endDate:string;summary:AnalyticsSummary;daily:Array<{date:string;total:number;late:number;notArrived:number}>;streamers:Array<{streamerId:string;streamerName:string;appointments:number;late:number;notArrived:number;modifications:number;cancellations:number}>}
export interface BookingOptions {streamers:Array<{userId:string;nickname:string}>;makeupArtists:MakeupArtist[];teams:Team[]}
export interface BookingContext {
  selectedDate:string;recommendedDate:string;writeEnabled:boolean;myAppointment:Appointment|null;cancelledAppointments:Appointment[]
  dailySchedule:Array<{startTime:string;makeupArtistName:string;teamName:string;streamerName:string;isMine:boolean;booked:boolean;attendanceStatus:AttendanceStatus;conflictOverride:boolean}>
  makeupArtists:MakeupArtist[];teams:Team[];defaults:{makeupArtistId?:string;teamId?:string;startTime?:string;message?:string}
  rules:{stepMinutes:number;durationMinutes:number;leadMinutes:number;cancelLimit:number;modifyLimit:number};operationCounts:{cancelCount:number;modifyCount:number}
}
