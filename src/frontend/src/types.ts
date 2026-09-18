export type Role = 'SUPER_ADMIN' | 'OPERATOR' | 'OBSERVER' | 'MAKEUP' | 'STREAMER'
export type AppointmentStatus = 'ACTIVE' | 'CANCELLED'
export type AttendanceStatus = 'PENDING' | 'ARRIVED' | 'NOT_ARRIVED' | 'LATE'
export interface CurrentUser { userId:string;dingTalkUserId?:string;dingTalkUsername?:string;nickname:string;role:Role;makeupArtistId?:string;canModifyAppointments:boolean;canCancelAppointments:boolean;canCreateAppointments:boolean;mustChangePassword:boolean;csrfToken:string;testMode?:boolean }
export interface MakeupArtist { id:string;name:string;avatarUrl?:string;workDays?:number[];workStart?:string;workEnd?:string;scheduleEnabled?:boolean;active?:boolean;attending?:boolean;version?:number;updatedAt?:string }
export interface Team { id:string;teamNo?:number|null;name:string;logoUrl?:string;active?:boolean;version?:number;updatedAt?:string }
export interface AppointmentOperation {type:'CREATE'|'MODIFY'|'CANCEL';actorName?:string;createdAt:string;reason?:string;attendanceSnapshot?:AttendanceStatus;changes:Array<{field:string;before?:string;after?:string}>}
export interface Appointment { id:string;bookingNumber?:string;bookingDate:string;startTime:string;streamerName:string;makeupArtistId:string;makeupArtistName:string;teamId:string;teamName:string;status:AppointmentStatus;attendanceStatus:AttendanceStatus;attendanceFrozen?:boolean;attendanceEvidenceAt?:string;createdAt?:string;cancelledAt?:string;modifiedAt?:string;hasModification?:boolean;operations?:AppointmentOperation[];conflictOverride?:boolean;version:number;changed?:boolean;selfOperationAllowed?:boolean;resourceModificationAllowed?:boolean;timeModificationAllowed?:boolean }
export interface AnalyticsSummary {total:number;active:number;cancelled:number;arrived:number;late:number;notArrived:number;modifications:number;cancellations:number;averageEarlyMinutes:number;averageLateMinutes:number}
export interface AnalyticsDay {date:string;total:number;active:number;cancelled:number;arrived:number;late:number;notArrived:number}
export interface AnalyticsData {startDate:string;endDate:string;summary:AnalyticsSummary;daily:AnalyticsDay[];streamers:Array<{streamerId:string;streamerName:string;appointments:number;arrived:number;late:number;notArrived:number;modifications:number;cancellations:number}>;makeupArtists:Array<{makeupArtistId:string;makeupArtistName:string;activeAppointments:number}>;teams:Array<{teamId:string;teamName:string;activeAppointments:number}>}
export interface BookingOptions {streamers:Array<{userId:string;nickname:string}>;makeupArtists:MakeupArtist[];teams:Team[]}
export interface BookingContext {
  selectedDate:string;recommendedDate:string;writeEnabled:boolean;myAppointment:Appointment|null;cancelledAppointments:Appointment[]
  dailySchedule:Array<{startTime:string;makeupArtistName:string;teamName:string;streamerName:string;isMine:boolean;booked:boolean;attendanceStatus:AttendanceStatus;conflictOverride:boolean}>
  makeupArtists:MakeupArtist[];teams:Team[];defaults:{makeupArtistId?:string;teamId?:string;startTime?:string;message?:string}
  rules:{stepMinutes:number;durationMinutes:number;leadMinutes:number;cancelLimit:number;modifyLimit:number};operationCounts:{cancelCount:number;modifyCount:number}
}
