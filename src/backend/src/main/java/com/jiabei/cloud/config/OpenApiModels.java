package com.jiabei.cloud.config;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 仅用于 OpenAPI 展示，不参与业务序列化。 */
public final class OpenApiModels {
  private OpenApiModels(){}

  public record ErrorInfo(
    @Schema(title="错误码",description="稳定的业务错误码，客户端应优先依据该值处理错误。",example="VALIDATION_FAILED") String code,
    @Schema(title="错误消息",description="面向用户或开发者的中文错误说明。",example="请求参数不符合要求。") String message){}
  public record ErrorData(@Schema(title="错误详情",description="校验失败字段或其他补充信息；无详情时为空。",nullable=true) Object details){}

  public record CurrentUser(
    @Schema(title="用户标识",description="账号登录名或钉钉用户 ID。",example="operator01") String userId,
    @Schema(title="钉钉用户 ID",description="已绑定的钉钉身份；密码账号兼容数据可能为空。",example="operator01",nullable=true) String dingTalkUserId,
    @Schema(title="昵称",description="系统内展示名称。",example="运营一号") String nickname,
    @Schema(title="角色",description="当前账号角色。",allowableValues={"SUPER_ADMIN","OPERATOR","OBSERVER","MAKEUP","STREAMER"},example="OPERATOR") String role,
    @Schema(title="化妆师资源 ID",description="化妆师账号关联的资源 ID；其他角色为空。",format="uuid",nullable=true) UUID makeupArtistId,
    @Schema(title="允许修改预约",description="是否具有管理端修改预约权限。",example="true") boolean canModifyAppointments,
    @Schema(title="允许取消预约",description="是否具有管理端取消预约权限。",example="true") boolean canCancelAppointments,
    @Schema(title="允许代预约",description="是否允许创建代预约。",example="true") boolean canCreateAppointments,
    @Schema(title="必须修改密码",description="为 true 时只能访问当前用户、退出和修改密码接口。",example="false") boolean mustChangePassword,
    @Schema(title="CSRF 令牌",description="写请求需通过 X-CSRF-Token 请求头原样提交。",example="csrf-token-example") String csrfToken){}

  public record Appointment(
    @Schema(title="数据 ID",description="预约数据库主键。",format="uuid") UUID id,
    @Schema(title="预约号",description="面向业务展示的预约编号。",example="090810001-streamer01") String bookingNumber,
    @Schema(title="预约日期",description="上海时区预约日期。",format="date",example="2026-09-08") LocalDate bookingDate,
    @Schema(title="开始时间",description="上海时区预约开始时间。",type="string",format="time",example="19:00") LocalTime startTime,
    @Schema(title="主播姓名",description="预约创建时保存的主播姓名快照。",example="玲玲") String streamerName,
    @Schema(title="主播身份",description="主播钉钉 ID 或兼容账号。",example="streamer01") String streamerDingTalkUserId,
    @Schema(title="化妆师 ID",description="关联化妆师资源主键。",format="uuid") UUID makeupArtistId,
    @Schema(title="化妆师姓名",description="预约创建或修改时保存的化妆师姓名快照。",example="小贝老师") String makeupArtistName,
    @Schema(title="团队 ID",description="关联团队主键。",format="uuid") UUID teamId,
    @Schema(title="团队名称",description="预约创建或修改时保存的团队名称快照。",example="星河一团") String teamName,
    @Schema(title="预约状态",description="预约是否仍有效。",allowableValues={"ACTIVE","CANCELLED"},example="ACTIVE") String status,
    @Schema(title="签到状态",description="考勤判定状态。",allowableValues={"PENDING","ARRIVED","NOT_ARRIVED","LATE"},example="PENDING") String attendanceStatus,
    @Schema(title="签到已冻结",description="取消后签到结果是否冻结。",example="false") boolean attendanceFrozen,
    @Schema(title="签到证据时间",description="匹配到的人脸识别时间；尚未匹配时为空。",format="date-time",nullable=true) OffsetDateTime attendanceEvidenceAt,
    @Schema(title="允许时间重叠",description="创建或修改时是否由有权角色覆盖了时段冲突。",example="false") boolean conflictOverride,
    @Schema(title="版本号",description="乐观锁版本，修改或取消时必须回传最新值。",example="0") int version,
    @Schema(title="是否修改过",description="版本号大于零时为 true。",example="false") boolean changed,
    @Schema(title="创建时间",description="预约创建时间。",format="date-time") OffsetDateTime createdAt){}

  public record ScheduleEntry(
    @Schema(title="开始时间",description="日程开始时间。",type="string",format="time",example="19:00") LocalTime startTime,
    @Schema(title="化妆师姓名",description="日程中的化妆师。") String makeupArtistName,
    @Schema(title="团队名称",description="日程所属团队。") String teamName,
    @Schema(title="主播姓名",description="预约主播。") String streamerName,
    @Schema(title="本人预约",description="该记录是否属于当前登录主播。") boolean isMine,
    @Schema(title="已预约",description="该时段是否已有预约。") boolean booked,
    @Schema(title="签到状态",description="当前签到枚举。",allowableValues={"PENDING","ARRIVED","NOT_ARRIVED","LATE"}) String attendanceStatus,
    @Schema(title="时间重叠",description="是否覆盖时段冲突。") boolean conflictOverride){}
  public record ResourceSummary(
    @Schema(title="资源 ID",description="化妆师或团队主键。",format="uuid") UUID id,
    @Schema(title="名称",description="资源显示名称。") String name,
    @Schema(title="图片地址",description="头像或团队图标地址。",nullable=true) String imageUrl){}
  public record BookingDefaults(
    @Schema(title="默认化妆师",description="上次有效选择的化妆师 ID。",format="uuid",nullable=true) UUID makeupArtistId,
    @Schema(title="默认团队",description="上次有效选择的团队 ID。",format="uuid",nullable=true) UUID teamId,
    @Schema(title="默认时间",description="上次有效选择的开始时间。",type="string",format="time",nullable=true) LocalTime startTime,
    @Schema(title="提示消息",description="上次选择失效时的中文提示。",nullable=true) String message){}
  public record BookingRules(
    @Schema(title="时间步长",description="可选开始时间的分钟间隔。",example="10") int stepMinutes,
    @Schema(title="服务时长",description="单次预约占用分钟数。",example="20") int durationMinutes,
    @Schema(title="最短提前量",description="当前角色预约需提前的分钟数。",example="20") int leadMinutes,
    @Schema(title="取消上限",description="主播每日自行取消次数上限。",example="2") int cancelLimit,
    @Schema(title="修改上限",description="主播每日自行修改次数上限。",example="3") int modifyLimit){}
  public record OperationCounts(
    @Schema(title="取消次数",description="主播在该日期已自行取消次数。") int cancelCount,
    @Schema(title="修改次数",description="主播在该日期已自行修改次数。") int modifyCount){}
  public record BookingContext(
    @Schema(title="选中日期",description="本次上下文对应日期。",format="date") LocalDate selectedDate,
    @Schema(title="推荐日期",description="系统根据可用性推荐的今天或明天。",format="date") LocalDate recommendedDate,
    @Schema(title="允许写入",description="系统入口是否开放。") boolean writeEnabled,
    @Schema(title="我的有效预约",description="当前主播在选中日期的有效预约；不存在时为空。",nullable=true) Appointment myAppointment,
    @ArraySchema(arraySchema=@Schema(title="已取消预约",description="当前主播在选中日期的历史取消记录。"),schema=@Schema(implementation=Appointment.class)) List<Appointment> cancelledAppointments,
    @ArraySchema(arraySchema=@Schema(title="当日日程",description="选中日期的有效预约日程。"),schema=@Schema(implementation=ScheduleEntry.class)) List<ScheduleEntry> dailySchedule,
    @ArraySchema(arraySchema=@Schema(title="可选化妆师",description="当前角色可见且可用的化妆师。"),schema=@Schema(implementation=ResourceSummary.class)) List<ResourceSummary> makeupArtists,
    @ArraySchema(arraySchema=@Schema(title="可选团队",description="当前启用团队。"),schema=@Schema(implementation=ResourceSummary.class)) List<ResourceSummary> teams,
    @Schema(title="上次选择",description="主播最近一次仍有效的预约选择。") BookingDefaults defaults,
    @Schema(title="预约规则",description="当前角色适用的时间和次数规则。") BookingRules rules,
    @Schema(title="操作计数",description="主播选中日期的自行操作计数；管理角色为空对象。") OperationCounts operationCounts){}

  public record AvailabilitySlot(
    @Schema(title="开始时间",description="候选开始时刻。",type="string",format="time",example="08:00") LocalTime time,
    @Schema(title="是否可用",description="当前账号能否选择该时刻。") boolean available,
    @Schema(title="存在冲突",description="该化妆师是否已有重叠预约。") boolean conflict,
    @Schema(title="不可用原因",description="不可用时的中文原因；可用时为空。",nullable=true) String reason){}
  public record Availability(
    @ArraySchema(arraySchema=@Schema(title="时间列表",description="按十分钟步长返回的候选时刻。"),schema=@Schema(implementation=AvailabilitySlot.class)) List<AvailabilitySlot> slots,
    @Schema(title="允许覆盖冲突",description="当前角色是否允许创建重叠预约。") boolean conflictAllowed){}
  public record AppointmentMutation(
    @Schema(title="预约 ID",description="被创建或操作的预约主键。",format="uuid") UUID id,
    @Schema(title="预约状态",description="操作后的状态。",allowableValues={"ACTIVE","CANCELLED"}) String status,
    @Schema(title="版本号",description="操作后的乐观锁版本。") int version,
    @Schema(title="未发生变化",description="修改内容与原数据一致时为 true。",nullable=true) Boolean unchanged,
    @Schema(title="时间重叠",description="是否覆盖了化妆师时段冲突。",nullable=true) Boolean conflictOverride,
    @Schema(title="修改次数",description="主播当日累计自行修改次数。",nullable=true) Integer modifyCount,
    @Schema(title="取消次数",description="主播当日累计自行取消次数。",nullable=true) Integer cancelCount){}
  public record AppointmentPage(
    @ArraySchema(arraySchema=@Schema(title="预约记录",description="当前页记录。"),schema=@Schema(implementation=Appointment.class)) List<Appointment> items,
    @Schema(title="总条数",description="符合条件的记录总数。") long total,
    @Schema(title="当前页",description="从 1 开始的页码。") int page,
    @Schema(title="每页条数",description="当前分页大小。") int size,
    @Schema(title="总页数",description="按当前分页大小计算的页数。") int totalPages){}
  public record Change(
    @Schema(title="字段",description="发生变化的业务字段中文名。") String field,
    @Schema(title="修改前",description="修改前的展示值。",nullable=true) String before,
    @Schema(title="修改后",description="修改后的展示值。",nullable=true) String after){}
  public record Modification(
    @Schema(title="修改人",description="操作时保存的员工姓名快照。") String actorName,
    @Schema(title="修改时间",description="修改发生时间。",format="date-time") OffsetDateTime createdAt,
    @Schema(title="修改原因",description="管理员填写的原因；可能为空。",nullable=true) String reason,
    @ArraySchema(arraySchema=@Schema(title="变更内容",description="时间、化妆师和团队的前后变化。"),schema=@Schema(implementation=Change.class)) List<Change> changes){}
  public record Audit(
    @Schema(title="操作类型",description="审计动作。",example="MODIFY") String action,
    @Schema(title="操作人",description="操作时的员工姓名快照。") String actorName,
    @Schema(title="原因",description="操作原因；可能为空。",nullable=true) String reason,
    @Schema(title="修改前数据",description="JSON 格式的修改前快照。",nullable=true) String before,
    @Schema(title="修改后数据",description="JSON 格式的修改后快照。",nullable=true) String after,
    @Schema(title="操作时间",description="审计记录创建时间。",format="date-time") OffsetDateTime createdAt){}
  public record AppointmentDetail(
    @Schema(title="创建来源",description="创建预约的角色。",allowableValues={"STREAMER","SUPER_ADMIN","OPERATOR","MAKEUP"}) String source,
    @Schema(title="创建人",description="优先使用创建审计中的员工姓名快照。") String createdByName,
    @Schema(title="取消时间",description="未取消时为空。",format="date-time",nullable=true) OffsetDateTime cancelledAt,
    @Schema(title="取消原因",description="未填写或未取消时为空。",nullable=true) String cancelReason,
    @Schema(title="取消人",description="未取消时为空。",nullable=true) String cancelledByName,
    @ArraySchema(arraySchema=@Schema(title="结构化修改记录",description="按时间倒序排列。"),schema=@Schema(implementation=Modification.class)) List<Modification> modifications,
    @ArraySchema(arraySchema=@Schema(title="原始审计记录",description="为兼容旧客户端保留。"),schema=@Schema(implementation=Audit.class)) List<Audit> audits){}

  public record CardDayStatus(
    @Schema(title="日期键",description="今天或明天。",allowableValues={"today","tomorrow"}) String key,
    @Schema(title="预约日期",description="上海时区业务日期。",format="date") LocalDate bookingDate,
    @Schema(title="曾成功送达",description="网关至少成功发送过一次时为 true。") boolean hasSuccessfulDelivery,
    @Schema(title="首次送达时间",description="第一次网关成功时间；从未成功时为空。",format="date-time",nullable=true) OffsetDateTime firstDeliveredAt){}
  public record CardStatus(@ArraySchema(arraySchema=@Schema(title="日期状态",description="今天和明天的发送状态。"),schema=@Schema(implementation=CardDayStatus.class)) List<CardDayStatus> dates){}
  public record Queued(@Schema(title="业务日期",description="进入发送队列的日期。",format="date") LocalDate businessDate,@Schema(title="已排队",description="仅表示任务已排队，不代表钉钉网关成功。") boolean queued){}

  public record Account(
    @Schema(title="账号 ID",description="账号数据库主键。",format="uuid") UUID id,
    @Schema(title="密码登录账号",description="尚未分配有效密码时为空。",nullable=true) String username,
    @Schema(title="钉钉用户 ID",description="创建后不可修改。",nullable=true) String dingTalkUserId,
    @Schema(title="钉钉姓名",description="钉钉通讯录姓名快照。",nullable=true) String dingTalkUsername,
    @Schema(title="昵称",description="系统内显示名称。") String nickname,
    @Schema(title="角色",description="账号角色。",allowableValues={"SUPER_ADMIN","OPERATOR","OBSERVER","MAKEUP","STREAMER"}) String role,
    @Schema(title="化妆师资源 ID",description="化妆师账号关联资源；其他角色为空。",format="uuid",nullable=true) UUID makeupArtistId,
    @Schema(title="账号启用",description="账号是否可登录。") boolean active,
    @Schema(title="出勤",description="主播或化妆师是否出勤。") boolean attending,
    @Schema(title="允许修改预约",description="运营修改预约权限。") boolean canModifyAppointments,
    @Schema(title="允许取消预约",description="运营取消预约权限。") boolean canCancelAppointments,
    @Schema(title="允许代预约",description="运营或化妆师的代预约权限。") boolean canCreateAppointments,
    @Schema(title="必须修改密码",description="下次密码登录后是否强制修改密码。") boolean mustChangePassword,
    @Schema(title="版本号",description="乐观锁版本。") int version,
    @Schema(title="更新时间",description="最后更新时间。",format="date-time") OffsetDateTime updatedAt,
    @Schema(title="最近上线时间",description="账号最近一次成功登录时间；从未登录时为空。",format="date-time",nullable=true) OffsetDateTime lastLoginAt){}
  public record Credential(@Schema(title="账号",description="新分配的密码登录账号。") String username,@Schema(title="临时密码",description="仅本次响应返回，首次登录后必须修改。") String password){}
  public record MakeupArtist(
    @Schema(title="化妆师 ID",description="资源主键。",format="uuid") UUID id,@Schema(title="名称",description="化妆师显示名称。") String name,
    @Schema(title="头像地址",description="头像文件地址。",nullable=true) String avatarUrl,@Schema(title="工作日",description="ISO 星期值：1 为周一，7 为周日。") List<Integer> workDays,
    @Schema(title="上班时间",description="排班启用时的工作开始时间。",type="string",format="time",example="08:00") LocalTime workStart,@Schema(title="下班时间",description="排班启用时的工作结束时间。",type="string",format="time",example="20:00") LocalTime workEnd,
    @Schema(title="排班启用",description="关闭时忽略工作日与时间限制，按全天可约处理。") boolean scheduleEnabled,@Schema(title="出勤",description="关闭时不可预约。") boolean attending,
    @Schema(title="资源启用",description="关闭时不可预约。") boolean active,@Schema(title="版本号",description="乐观锁版本。") int version,@Schema(title="更新时间",description="资源更新时间。",format="date-time") OffsetDateTime updatedAt){}
  public record Team(@Schema(title="团队 ID",description="团队主键。",format="uuid") UUID id,@Schema(title="名称",description="团队显示名称。") String name,@Schema(title="图标地址",description="团队图标文件地址。",nullable=true) String logoUrl,@Schema(title="启用",description="停用团队不可用于预约。") boolean active,@Schema(title="版本号",description="乐观锁版本。") int version,@Schema(title="更新时间",description="团队更新时间。",format="date-time") OffsetDateTime updatedAt){}
  public record Streamer(@Schema(title="主播身份",description="钉钉用户 ID 或兼容账号。") String userId,@Schema(title="昵称",description="主播显示名称。") String nickname,@Schema(title="出勤",description="是否允许预约。") boolean attending,@Schema(title="已绑定钉钉",description="是否使用钉钉身份创建。") boolean dingTalkBound){}
  public record Setting(@Schema(title="入口启用",description="系统预约入口是否开放。") boolean enabled,@Schema(title="版本号",description="乐观锁版本。") int version,@Schema(title="更新时间",description="设置更新时间。",format="date-time") OffsetDateTime updatedAt){}
  public record Employee(@Schema(title="钉钉用户 ID",description="未注册员工的钉钉唯一标识。") String dingTalkUserId,@Schema(title="钉钉姓名",description="钉钉通讯录姓名。") String dingTalkUsername,@Schema(title="显示标签",description="姓名与 ID 组合显示文本。") String label){}
  public record Upload(@Schema(title="图片地址",description="上传后可供前端使用的相对 URL。") String url,@Schema(title="宽度",description="图片像素宽度。") int width,@Schema(title="高度",description="图片像素高度。") int height){}
  public record Fault(@Schema(title="故障键",description="Mock 故障类型。") String key,@Schema(title="启用",description="是否启用故障注入。") boolean enabled,@Schema(title="剩余次数",description="-1 表示持续生效，0 表示不再触发。") int remainingCount){}
  public record Processed(@Schema(title="已处理",description="是否已触发一次任务轮询。") boolean processed){}
  public record MockCard(@Schema(title="卡片追踪 ID",description="Mock 卡片唯一追踪标识。") String outTrackId,@Schema(title="群会话 ID",description="目标钉钉群标识。") String groupId,@Schema(title="业务日期",description="卡片对应预约日期。",format="date") LocalDate businessDate,@Schema(title="公共卡片数据",description="卡片公共 JSON 数据。") Map<String,Object> cardData,@Schema(title="当前用户私有数据",description="按 userId 投影后的私有 JSON 数据。") Map<String,Object> privateData,@Schema(title="包含私有数据",description="当前 userId 是否存在私有数据。") boolean hasPrivateData,@Schema(title="内容版本",description="卡片内容版本。") long contentVersion,@Schema(title="状态",description="Mock 卡片状态。") String status,@Schema(title="更新时间",description="最后更新时间。",format="date-time") OffsetDateTime updatedAt){}
  public record MockCall(@Schema(title="卡片追踪 ID",description="被调用卡片的追踪标识。") String outTrackId,@Schema(title="操作",description="CREATE 或 UPDATE。",allowableValues={"CREATE","UPDATE"}) String operation,@Schema(title="内容版本",description="调用使用的内容版本。") long contentVersion,@Schema(title="结果码",description="Mock 网关返回结果。") String resultCode,@Schema(title="调用时间",description="调用记录时间。",format="date-time") OffsetDateTime createdAt){}
  public record MoredianResult(@Schema(title="结果码",description="魔点协议成功结果固定为 0。",example="0") String result,@Schema(title="结果消息",description="魔点协议响应说明。",example="操作成功") String message){}
}
