package com.jiabei.cloud.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * 集中维护 Swagger 的中文契约。
 *
 * <p>业务控制器大量返回动态 Map。这里用“文档专用模型 + OpenAPI 定制器”描述真实 JSON，
 * 不改变控制器签名和 Jackson 序列化结果。新增接口时必须同步加入 {@link #SPECS}；
 * 覆盖测试会阻止缺少中文名称或说明的接口进入主分支。</p>
 */
@Configuration
public class OpenApiDocumentation {
  static final String SESSION="JBY_SESSION";
  static final String CSRF="X-CSRF-Token";
  static final Map<String,Spec> SPECS=specs();

  @Bean
  OpenAPI jiabeiOpenApi(){
    Components components=new Components()
      .addSecuritySchemes(SESSION,new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name(SESSION).description("登录成功后由服务端写入的 HttpOnly 会话 Cookie；Swagger UI 会使用浏览器当前会话。"))
      .addSecuritySchemes(CSRF,new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(CSRF).description("除 GET/HEAD 外的受保护请求必须携带；值来自登录或 GET /api/v1/me 响应中的 csrfToken。"));
    return new OpenAPI().components(components)
      .info(new Info().title("加贝云·妆造预约 API").version("v1").description("加贝云妆造预约系统接口。所有业务日期和时间均按 Asia/Shanghai 解释；JSON 字段名保持英文，Schema 标题和说明使用中文。"))
      .tags(List.of(
        tag("认证与会话","登录、当前用户、首次改密和退出登录。"),
        tag("预约","主播预约上下文、可用时段和本人预约操作。"),
        tag("预约管理","管理端查询、代预约、修改、取消、导出和钉钉群卡片。"),
        tag("账号管理","账号添加、修改以及临时密码分配和回收。"),
        tag("资源与设置","化妆师、团队、主播列表、系统入口和审计记录。"),
        tag("钉钉通讯录","查询尚未注册的钉钉员工。"),
        tag("文件上传","管理端图片上传。"),
        tag("魔点回调","魔点人脸识别设备回调。"),
        tag("本地调试","仅 Local/Test Profile 可用，不得用于生产环境。")));
  }

  @Bean
  OpenApiCustomizer chineseApiDocumentation(){
    return openApi->{
      openApi.getPaths().forEach((path,item)->item.readOperationsMap().forEach((method,operation)->{
        Spec spec=SPECS.get(method.name()+" "+path);
        if(spec==null)return; // 覆盖测试会报告新增但未登记的接口。
        operation.setSummary(spec.summary());
        operation.setDescription(spec.description());
        operation.setTags(List.of(spec.tag()));
        operation.setResponses(responses(spec,openApi.getComponents()));
        documentParameters(operation.getParameters());
        if(operation.getRequestBody()!=null&&!spec.requestNote().isBlank())operation.getRequestBody().setDescription(spec.requestNote());
        if(spec.protectedRoute()){
          SecurityRequirement security=new SecurityRequirement().addList(SESSION);
          if(spec.write())security.addList(CSRF);
          operation.setSecurity(List.of(security));
        }else operation.setSecurity(List.of());
      }));
      documentReferencedFields(openApi.getComponents());
    };
  }

  /**
   * OpenAPI 3.0 的 $ref 不能可靠保留字段位置上的 title/description。
   * swagger-core 因而会丢掉少数引用字段的注解；在组件生成后补回字段语义，
   * 让阅读者无需跳转到目标类型也能理解当前位置的业务含义。
   */
  private void documentReferencedFields(Components components){
    fieldDoc(components,"BookingContext","myAppointment","本人有效预约","当前主播在选中日期的有效预约；没有时为空。");
    fieldDoc(components,"BookingContext","defaults","上次选择","主播最近一次仍有效的化妆师、团队和时间默认值。");
    fieldDoc(components,"BookingContext","rules","预约规则","时间步长、服务时长、提前量及修改取消次数限制。");
    fieldDoc(components,"BookingContext","operationCounts","操作次数","当前主播在选中日期已修改和取消的次数。");
    timeField(components,"SelfAppointmentCreateRequest","startTime","开始时间","按 10 分钟步长选择的开始时间。","19:00");
    timeField(components,"SelfAppointmentModifyRequest","startTime","开始时间","修改后的开始时间。","19:00");
    timeField(components,"AdminAppointmentCreateRequest","startTime","开始时间","按 10 分钟步长选择的开始时间。","19:00");
    timeField(components,"AdminAppointmentModifyRequest","startTime","开始时间","修改后的开始时间。","19:00");
    timeField(components,"MakeupArtistRequest","workStart","上班时间","排班启用时必须早于下班时间。","08:00");
    timeField(components,"MakeupArtistRequest","workEnd","下班时间","排班启用时必须晚于上班时间，不支持跨午夜。","20:00");
    // LocalTime 的 hour/minute/second/nano 是 Java 实现细节，不属于 JSON 协议。
    if(components.getSchemas()!=null)components.getSchemas().remove("LocalTime");
  }

  private void fieldDoc(Components components,String schemaName,String field,String title,String description){
    Schema<?> schema=components.getSchemas()==null?null:components.getSchemas().get(schemaName);
    if(schema==null||schema.getProperties()==null)return;
    Schema<?> property=(Schema<?>)schema.getProperties().get(field);
    if(property!=null){property.setTitle(title);property.setDescription(description);}
  }

  private void timeField(Components components,String schemaName,String field,String title,String description,String example){
    Schema<?> schema=components.getSchemas()==null?null:components.getSchemas().get(schemaName);
    if(schema==null||schema.getProperties()==null)return;
    schema.getProperties().put(field,new StringSchema().format("time").title(title).description(description).example(example));
  }

  private ApiResponses responses(Spec spec,Components components){
    ApiResponses responses=new ApiResponses();
    if(spec.binary()){
      Schema<?> file=new BinarySchema().description("Excel 工作簿二进制内容。");
      responses.addApiResponse("200",new ApiResponse().description("导出成功。").content(new Content().addMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",new MediaType().schema(file))));
    }else{
      Schema<?> data=spec.data()==Void.class?new ObjectSchema().nullable(true):schema(spec.data(),components);
      if(spec.array())data=new ArraySchema().items(data).description("数据列表。");
      if(spec.detail())data=new ComposedSchema().addAllOfItem(schema(OpenApiModels.Appointment.class,components)).addAllOfItem(schema(OpenApiModels.AppointmentDetail.class,components));
      Schema<?> body=spec.raw()?data:successEnvelope(data,components);
      responses.addApiResponse("200",new ApiResponse().description(spec.success()).content(json(body)));
    }
    errorDescriptions(spec.errors()).forEach((status,description)->responses.addApiResponse(status,new ApiResponse().description(description).content(json(errorEnvelope(components)))));
    return responses;
  }

  /** 将文档模型交给 swagger-core 解析，同时把其引用的嵌套模型注册到 components。 */
  private Schema<?> schema(Class<?> type,Components components){
    ResolvedSchema resolved=ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true));
    resolved.referencedSchemas.forEach(components::addSchemas);
    return resolved.schema;
  }

  private Schema<?> successEnvelope(Schema<?> data,Components components){
    schema(OpenApiModels.ErrorInfo.class,components);
    return new ObjectSchema().name("成功响应")
      .addProperty("success",new io.swagger.v3.oas.models.media.BooleanSchema().description("请求是否成功；成功响应固定为 true。").example(true))
      .addProperty("data",data.description("接口业务数据；无返回数据的操作为 null。"))
      .addProperty("error",new Schema<>().$ref("#/components/schemas/ErrorInfo").description("成功时固定为 null。").nullable(true))
      .addProperty("traceId",new StringSchema().description("服务端链路追踪 ID，排查问题时请提供。").example("d2b5233e-323e-4ecc-b3c9-f425688d685c"));
  }

  private Schema<?> errorEnvelope(Components components){
    schema(OpenApiModels.ErrorInfo.class,components);schema(OpenApiModels.ErrorData.class,components);
    return new ObjectSchema().name("错误响应")
      .addProperty("success",new io.swagger.v3.oas.models.media.BooleanSchema().description("错误响应固定为 false。").example(false))
      .addProperty("data",new Schema<>().$ref("#/components/schemas/ErrorData").description("可选错误详情。").nullable(true))
      .addProperty("error",new Schema<>().$ref("#/components/schemas/ErrorInfo").description("业务错误码和中文消息。"))
      .addProperty("traceId",new StringSchema().description("服务端链路追踪 ID。").example("d2b5233e-323e-4ecc-b3c9-f425688d685c"));
  }

  private Content json(Schema<?> schema){return new Content().addMediaType("application/json",new MediaType().schema(schema));}

  private Map<String,String> errorDescriptions(ErrorProfile profile){
    Map<String,String> errors=new LinkedHashMap<>();
    switch(profile){
      case LOGIN->{errors.put("400","VALIDATION_FAILED：账号、密码或授权码格式错误。");errors.put("401","INVALID_CREDENTIALS / DINGTALK_AUTH_CODE_INVALID：登录凭据无效。");errors.put("403","USER_NOT_AUTHORIZED：账号未启用或无系统权限。");errors.put("429","LOGIN_RATE_LIMITED：同一来源登录过于频繁。");}
      case READ->{errors.put("401","AUTH_REQUIRED / SESSION_INVALIDATED：会话缺失、过期或已失效。");errors.put("403","FORBIDDEN / PASSWORD_CHANGE_REQUIRED：角色权限不足或必须先修改初始密码。");}
      case WRITE->{errors.putAll(errorDescriptions(ErrorProfile.READ));errors.put("400","VALIDATION_FAILED：请求体、路径或请求头格式错误。");errors.put("403","CSRF_INVALID / ORIGIN_NOT_ALLOWED / FORBIDDEN：安全校验或角色权限不满足。");}
      case BOOKING->{errors.putAll(errorDescriptions(ErrorProfile.WRITE));errors.put("404","APPOINTMENT_NOT_FOUND：预约不存在或当前账号不可见。");errors.put("409","DAILY_APPOINTMENT_EXISTS / MAKEUP_ARTIST_SLOT_CONFLICT / VERSION_CONFLICT / APPOINTMENT_TERMINAL / REQUEST_IN_PROGRESS：预约状态或并发条件冲突。");errors.put("422","BOOKING_DATE_NOT_ALLOWED / MIN_LEAD_TIME_NOT_MET / TIME_STEP_INVALID / MAKEUP_ARTIST_UNAVAILABLE / TEAM_UNAVAILABLE / STREAMER_UNAVAILABLE：预约业务条件不满足。");}
      case RESOURCE->{errors.putAll(errorDescriptions(ErrorProfile.WRITE));errors.put("404","USER_NOT_FOUND / MAKEUP_ARTIST_NOT_FOUND / TEAM_NOT_FOUND：目标资源不存在。");errors.put("409","VERSION_CONFLICT / ACCOUNT_CONFLICT：版本或唯一性冲突。");errors.put("422","ACCOUNT_DATA_INVALID / RESOURCE_DATA_INVALID：账号、排班或资源字段不符合业务规则。");}
      case UPLOAD->{errors.putAll(errorDescriptions(ErrorProfile.WRITE));errors.put("413","UPLOAD_FILE_TOO_LARGE：文件超过 2MB。");errors.put("422","UPLOAD_FILE_INVALID：文件不是有效 PNG/JPG 或尺寸超过 4096×4096。");errors.put("500","UPLOAD_FAILED：服务器无法保存文件。");}
      case CALLBACK->{errors.put("400","INVALID_CALLBACK：回调 JSON 或必填字段无效。");errors.put("403","INVALID_SIGNATURE：签名验证失败。");errors.put("413","PAYLOAD_TOO_LARGE：请求体超过 64KB。");}
      case MOCK->{errors.putAll(errorDescriptions(ErrorProfile.WRITE));errors.put("404","FAULT_NOT_FOUND：指定 Mock 故障键不存在。");}
    }
    return errors;
  }

  private void documentParameters(List<Parameter> parameters){
    if(parameters==null)return;
    parameters.forEach(parameter->{
      ParameterDoc doc=PARAMETERS.get(parameter.getName());
      if(doc==null)return;
      parameter.setDescription(doc.description());
      if(parameter.getSchema()!=null){parameter.getSchema().setTitle(doc.title());if(doc.example()!=null)parameter.getSchema().setExample(doc.example());}
    });
  }

  private static Tag tag(String name,String description){return new Tag().name(name).description(description);}
  private static String key(HttpMethod method,String path){return method.name()+" "+path;}
  private static Map<String,Spec> specs(){
    Map<String,Spec> m=new LinkedHashMap<>();
    add(m,HttpMethod.GET,"/api/v1/me","获取当前用户","返回当前会话用户、角色、权限和 CSRF 令牌。登录后可用；首次修改密码期间仍允许调用。","认证与会话",OpenApiModels.CurrentUser.class,false,false,ErrorProfile.READ,"获取成功。","");
    add(m,HttpMethod.POST,"/api/v1/logout","退出登录","销毁当前会话并清除 JBY_SESSION Cookie。","认证与会话",Void.class,false,false,ErrorProfile.WRITE,"退出成功。","无需请求体。");
    add(m,HttpMethod.POST,"/api/v1/auth/password-login","账号密码登录","账号去除首尾空格后为 6–12 位字母或数字，密码为 6–12 位；成功后写入会话 Cookie。","认证与会话",OpenApiModels.CurrentUser.class,false,false,ErrorProfile.LOGIN,"登录成功。","提交密码登录凭据。");
    add(m,HttpMethod.POST,"/api/v1/auth/dingtalk-login","钉钉免登","使用钉钉授权码换取系统会话；授权码由钉钉客户端提供。","认证与会话",OpenApiModels.CurrentUser.class,false,false,ErrorProfile.LOGIN,"登录成功。","提交钉钉免登授权信息。");
    add(m,HttpMethod.POST,"/api/v1/auth/mock-login","本地模拟登录","仅 Local/Test Profile 可用；按预置身份创建模拟会话，不得用于生产环境。","本地调试",OpenApiModels.CurrentUser.class,false,false,ErrorProfile.LOGIN,"模拟登录成功。","提交预置 Mock 用户标识。");
    add(m,HttpMethod.POST,"/api/v1/auth/change-password","修改首次密码","修改当前密码。新密码为 6–12 位；成功后解除首次改密限制并使旧凭据版本失效。","认证与会话",Void.class,false,false,ErrorProfile.WRITE,"密码修改成功。","提交当前密码和新密码。");

    add(m,HttpMethod.GET,"/api/v1/booking-context","获取预约页面上下文","返回选中日期、推荐日期、本人预约、日程、资源、默认选择和规则。日期按上海时区解释。","预约",OpenApiModels.BookingContext.class,false,false,ErrorProfile.READ,"上下文获取成功。","");
    add(m,HttpMethod.GET,"/api/v1/availability","查询化妆师可用时段","按 10 分钟步长返回化妆师在指定日期的时段和不可用原因；排班关闭时覆盖全天时段。","预约",OpenApiModels.Availability.class,false,false,ErrorProfile.READ,"时段查询成功。","");
    add(m,HttpMethod.POST,"/api/v1/appointments","主播创建本人预约","为当前主播创建预约；每天仅允许一条有效预约，需满足提前量、资源状态、排班和时段冲突规则。","预约",OpenApiModels.AppointmentMutation.class,false,false,ErrorProfile.BOOKING,"预约创建成功。","streamerUserId、role、nickname 为兼容字段，服务端忽略；必须提供 Idempotency-Key。");
    add(m,HttpMethod.PATCH,"/api/v1/appointments/{id}","主播修改本人预约","修改当前主播的有效预约；日期不可修改，每日最多自行修改 3 次。","预约",OpenApiModels.AppointmentMutation.class,false,false,ErrorProfile.BOOKING,"预约修改成功。","必须回传最新 version 并提供 Idempotency-Key。");
    add(m,HttpMethod.POST,"/api/v1/appointments/{id}/cancel","主播取消本人预约","取消当前主播的有效预约并冻结签到结果；每日最多自行取消 2 次。","预约",OpenApiModels.AppointmentMutation.class,false,false,ErrorProfile.BOOKING,"预约取消成功。","必须回传最新 version 并提供 Idempotency-Key。");

    add(m,HttpMethod.GET,"/api/v1/admin/appointments","分页查询预约记录","管理员、观察员或化妆师查询可见预约。date 存在时优先于 startDate/endDate；未给区间时默认本月一日至今天。","预约管理",OpenApiModels.AppointmentPage.class,false,false,ErrorProfile.READ,"查询成功。","");
    add(m,HttpMethod.GET,"/api/v1/admin/appointments/options","查询代预约候选资源","按日期返回可代预约主播、符合排班的化妆师和启用团队；修改时可传 appointmentId 排除自身冲突。","预约管理",Map.class,false,false,ErrorProfile.READ,"候选资源获取成功。","");
    add(m,HttpMethod.GET,"/api/v1/admin/appointments/availability","查询管理端可用时段","返回管理端可选时段、冲突标记和不可用原因；appointmentId 仅用于修改时排除当前预约。","预约管理",OpenApiModels.Availability.class,false,false,ErrorProfile.READ,"时段获取成功。","");
    add(m,HttpMethod.GET,"/api/v1/admin/analytics","查询预约统计","超管、运营和观察员按预约日期区间查看汇总、每日趋势和主播统计。","预约管理",Map.class,false,false,ErrorProfile.READ,"统计获取成功。","");
    add(m,HttpMethod.GET,"/api/v1/admin/appointments/export","导出预约记录","管理员按筛选条件导出 XLSX；响应文件名包含实际日期区间。","预约管理",Void.class,false,true,ErrorProfile.READ,"导出成功。","");
    add(m,HttpMethod.GET,"/api/v1/admin/appointments/{id}","查看预约详情","返回预约双 ID、创建来源、取消信息、结构化修改记录和兼容审计数据；化妆师只能查看自己的预约。","预约管理",OpenApiModels.AppointmentDetail.class,false,false,ErrorProfile.READ,"详情获取成功。","");
    add(m,HttpMethod.POST,"/api/v1/admin/appointments","创建代预约","有代预约权限的管理员或化妆师为主播创建预约；化妆师只能使用自己的资源。","预约管理",OpenApiModels.AppointmentMutation.class,false,false,ErrorProfile.BOOKING,"代预约创建成功。","必须提供 Idempotency-Key；reason 为代预约原因。");
    add(m,HttpMethod.PATCH,"/api/v1/admin/appointments/{id}","管理端修改预约","有修改权限的账号调整时间、化妆师或团队；bookingDate 不可修改，传入即返回 422。","预约管理",OpenApiModels.AppointmentMutation.class,false,false,ErrorProfile.BOOKING,"预约修改成功。","必须回传最新 version；reason 为可选修改原因。");
    add(m,HttpMethod.POST,"/api/v1/admin/appointments/{id}/cancel","管理端取消预约","有取消权限的账号取消有效预约并记录操作人、原因和审计快照。","预约管理",OpenApiModels.AppointmentMutation.class,false,false,ErrorProfile.BOOKING,"预约取消成功。","必须回传最新 version；reason 为可选取消原因。");
    add(m,HttpMethod.GET,"/api/v1/admin/appointments/schedule-card/status","查询群卡片发送状态","返回上海时区今天和明天是否曾被钉钉网关成功送达；进入队列不算成功。","预约管理",OpenApiModels.CardStatus.class,false,false,ErrorProfile.READ,"状态查询成功。","");
    add(m,HttpMethod.POST,"/api/v1/admin/appointments/schedule-card","发送钉钉群卡片","为今天或明天创建/刷新群卡片任务。成功响应仅表示已排队，实际送达状态需查询状态接口。","预约管理",OpenApiModels.Queued.class,false,false,ErrorProfile.WRITE,"任务已进入发送队列。","提交 bookingDate，仅允许上海时区今天或明天。");

    add(m,HttpMethod.GET,"/api/v1/admin/accounts","查询账号列表","返回当前管理员可管理的账号、角色、权限、关联资源和版本信息。","账号管理",OpenApiModels.Account.class,true,false,ErrorProfile.READ,"账号列表获取成功。","");
    add(m,HttpMethod.POST,"/api/v1/admin/accounts","添加账号","使用未注册钉钉身份添加主播、化妆师、运营或观察员；化妆师会同步创建资源。","账号管理",OpenApiModels.Account.class,false,false,ErrorProfile.RESOURCE,"账号添加成功。","钉钉用户 ID 和姓名创建后不可修改。");
    add(m,HttpMethod.PATCH,"/api/v1/admin/accounts/{id}","修改账号","修改昵称、角色、启用状态和角色专属权限；角色切换会同步化妆师资源状态。","账号管理",OpenApiModels.Account.class,false,false,ErrorProfile.RESOURCE,"账号修改成功。","仅提交需要变更的可空字段，并回传最新 version。");
    add(m,HttpMethod.POST,"/api/v1/admin/accounts/{id}/assign-password","分配登录密码","为账号生成密码登录账号并返回一次性密码；不会限制钉钉免登，也不要求首次登录修改密码。","账号管理",OpenApiModels.Credential.class,false,false,ErrorProfile.RESOURCE,"密码分配成功。","响应中的密码必须安全交付，服务端不会再次返回。");
    add(m,HttpMethod.POST,"/api/v1/admin/accounts/{id}/revoke-password","回收密码","删除账号的密码登录能力并使现有密码会话失效；钉钉登录不受影响。","账号管理",Void.class,false,false,ErrorProfile.RESOURCE,"密码回收成功。","无需请求体。");

    add(m,HttpMethod.GET,"/api/v1/admin/makeup-artists","查询化妆师资源","返回可见化妆师的排班、出勤、启用状态和版本。化妆师角色仅能看到自己的资源。","资源与设置",OpenApiModels.MakeupArtist.class,true,false,ErrorProfile.READ,"化妆师列表获取成功。","");
    add(m,HttpMethod.POST,"/api/v1/admin/makeup-artists","添加化妆师资源","管理员添加未绑定账号的化妆师资源；排班启用时必须选择工作日且上班早于下班。","资源与设置",OpenApiModels.MakeupArtist.class,false,false,ErrorProfile.RESOURCE,"化妆师添加成功。","scheduleEnabled、active、attending 省略时默认为 true。");
    add(m,HttpMethod.PATCH,"/api/v1/admin/makeup-artists/{id}","修改化妆师资源","修改化妆师名称、图片、排班、出勤或启用状态，使用 version 进行乐观锁校验。","资源与设置",OpenApiModels.MakeupArtist.class,false,false,ErrorProfile.RESOURCE,"化妆师修改成功。","排班关闭时工作日可为空，时间限制被忽略但保留最近有效时间。");
    add(m,HttpMethod.GET,"/api/v1/admin/teams","查询团队列表","返回全部团队及启用、图片和版本信息。","资源与设置",OpenApiModels.Team.class,true,false,ErrorProfile.READ,"团队列表获取成功。","");
    add(m,HttpMethod.POST,"/api/v1/admin/teams","添加团队","管理员添加团队；名称不能为空，停用团队不能用于新预约。","资源与设置",OpenApiModels.Team.class,false,false,ErrorProfile.RESOURCE,"团队添加成功。","active 省略时默认为 true。");
    add(m,HttpMethod.PATCH,"/api/v1/admin/teams/{id}","修改团队","修改团队名称、图标和启用状态，使用 version 进行乐观锁校验。","资源与设置",OpenApiModels.Team.class,false,false,ErrorProfile.RESOURCE,"团队修改成功。","未提交的可空字段保持原值。");
    add(m,HttpMethod.GET,"/api/v1/admin/streamers","查询可预约主播","返回当前启用的主播、出勤状态及钉钉绑定状态，供代预约选择。","资源与设置",OpenApiModels.Streamer.class,true,false,ErrorProfile.READ,"主播列表获取成功。","");
    add(m,HttpMethod.GET,"/api/v1/admin/system-setting","查询系统入口","返回预约系统入口启用状态和版本。","资源与设置",OpenApiModels.Setting.class,false,false,ErrorProfile.READ,"系统入口获取成功。","");
    add(m,HttpMethod.PATCH,"/api/v1/admin/system-setting","修改系统入口","管理员开启或关闭普通用户预约入口；管理端仍可进入。","资源与设置",OpenApiModels.Setting.class,false,false,ErrorProfile.RESOURCE,"系统入口更新成功。","必须回传最新 version。");
    add(m,HttpMethod.GET,"/api/v1/admin/audit-logs","查询实体审计记录","按实体类型和实体 ID 返回创建、修改及权限操作审计快照，按时间倒序排列。","资源与设置",OpenApiModels.Audit.class,true,false,ErrorProfile.READ,"审计记录获取成功。","");

    add(m,HttpMethod.GET,"/api/v1/admin/dingtalk-employees","搜索未注册钉钉员工","管理员按姓名或钉钉 ID 搜索通讯录，并排除已注册员工。","钉钉通讯录",OpenApiModels.Employee.class,true,false,ErrorProfile.READ,"员工列表获取成功。","");
    add(m,HttpMethod.POST,"/api/v1/admin/uploads/images","上传资源图片","管理员上传 PNG/JPG 图片；文件不超过 2MB，宽高均不超过 4096 像素。","文件上传",OpenApiModels.Upload.class,false,false,ErrorProfile.UPLOAD,"图片上传成功。","使用 multipart/form-data，字段名为 file。");
    add(m,HttpMethod.POST,"/api/v1/integrations/moredian/recognition-events","接收魔点识别回调","校验组织、设备、签名和 64KB 大小限制；仅 REC_SUCCESS 事件进入签到匹配，重复事件按摘要幂等。","魔点回调",OpenApiModels.MoredianResult.class,false,false,ErrorProfile.CALLBACK,"回调已接收。","请求体为魔点原始 JSON，签名基于未经改写的原文计算。",true);

    add(m,HttpMethod.GET,"/api/v1/mock/cards","查询 Mock 卡片","仅 Local/Test Profile 可用；按 userId 投影卡片私有数据，不得用于生产环境。","本地调试",OpenApiModels.MockCard.class,true,false,ErrorProfile.READ,"Mock 卡片获取成功。","");
    add(m,HttpMethod.GET,"/api/v1/mock/card-calls","查询 Mock 卡片调用","仅 Local/Test Profile 可用；返回最近 100 条创建或更新调用记录。","本地调试",OpenApiModels.MockCall.class,true,false,ErrorProfile.READ,"调用记录获取成功。","");
    add(m,HttpMethod.POST,"/api/v1/mock/faults/{key}","设置 Mock 故障","仅 Local/Test Profile 可用；配置卡片网关故障类型及剩余触发次数。","本地调试",OpenApiModels.Fault.class,false,false,ErrorProfile.MOCK,"故障配置成功。","remainingCount 为 -1 时持续触发，正数表示剩余次数。");
    add(m,HttpMethod.POST,"/api/v1/mock/cards/run","执行一次卡片任务","仅 Local/Test Profile 可用；同步触发一次待处理卡片任务轮询。","本地调试",OpenApiModels.Processed.class,false,false,ErrorProfile.MOCK,"任务轮询完成。","无需请求体。");
    return Map.copyOf(m);
  }

  private static void add(Map<String,Spec> m,HttpMethod method,String path,String summary,String description,String tag,Class<?> data,boolean array,boolean binary,ErrorProfile errors,String success,String requestNote){add(m,method,path,summary,description,tag,data,array,binary,errors,success,requestNote,false);}
  private static void add(Map<String,Spec> m,HttpMethod method,String path,String summary,String description,String tag,Class<?> data,boolean array,boolean binary,ErrorProfile errors,String success,String requestNote,boolean raw){m.put(key(method,path),new Spec(summary,description,tag,data,array,binary,raw,errors,success,requestNote,path.endsWith("/{id}")&&method==HttpMethod.GET));}

  /** 供覆盖测试比较控制器真实路由，防止新增接口遗漏中文文档。 */
  static java.util.Set<String> documentedOperations(){return SPECS.keySet();}

  /** 返回缺少必要中文契约信息的接口键；正常情况下始终为空。 */
  static List<String> incompleteOperations(){
    return SPECS.entrySet().stream()
      .filter(entry->entry.getValue().summary().isBlank()||entry.getValue().description().isBlank()||entry.getValue().tag().isBlank()||entry.getValue().success().isBlank())
      .map(Map.Entry::getKey).toList();
  }

  private record Spec(String summary,String description,String tag,Class<?> data,boolean array,boolean binary,boolean raw,ErrorProfile errors,String success,String requestNote,boolean detail){
    boolean protectedRoute(){return errors!=ErrorProfile.LOGIN&&errors!=ErrorProfile.CALLBACK;}
    boolean write(){return errors==ErrorProfile.WRITE||errors==ErrorProfile.BOOKING||errors==ErrorProfile.RESOURCE||errors==ErrorProfile.UPLOAD||errors==ErrorProfile.MOCK;}
  }
  private enum ErrorProfile{LOGIN,READ,WRITE,BOOKING,RESOURCE,UPLOAD,CALLBACK,MOCK}
  private record ParameterDoc(String title,String description,Object example){}
  private static final Map<String,ParameterDoc> PARAMETERS=Map.ofEntries(
    Map.entry("id",new ParameterDoc("资源 ID","目标预约、账号、化妆师或团队的 UUID。","65d31b4f-b244-4a50-9a18-1e9a192f5a22")),
    Map.entry("date",new ParameterDoc("预约日期","上海时区日期；存在时覆盖开始和截止日期。","2026-09-08")),
    Map.entry("startDate",new ParameterDoc("开始日期","区间起始日期，包含当天。","2026-09-01")),
    Map.entry("endDate",new ParameterDoc("截止日期","区间截止日期，包含当天。","2026-09-08")),
    Map.entry("streamer",new ParameterDoc("主播筛选","主播钉钉 ID、账号或名称筛选值。","streamer01")),
    Map.entry("makeupArtist",new ParameterDoc("化妆师筛选","化妆师资源 UUID。","65d31b4f-b244-4a50-9a18-1e9a192f5a22")),
    Map.entry("makeupArtistId",new ParameterDoc("化妆师 ID","化妆师资源 UUID。","65d31b4f-b244-4a50-9a18-1e9a192f5a22")),
    Map.entry("attendanceStatus",new ParameterDoc("签到状态","PENDING、ARRIVED、NOT_ARRIVED 或 LATE。","PENDING")),
    Map.entry("status",new ParameterDoc("预约状态","ACTIVE 或 CANCELLED。","ACTIVE")),
    Map.entry("page",new ParameterDoc("页码","从 1 开始，默认 1。",1)),
    Map.entry("size",new ParameterDoc("每页条数","默认 30，服务端限制允许范围。",30)),
    Map.entry("Idempotency-Key",new ParameterDoc("幂等键","同一用户和操作内唯一；重试相同请求必须复用，相同键不得提交不同内容。","550e8400-e29b-41d4-a716-446655440000")),
    Map.entry("query",new ParameterDoc("搜索词","钉钉员工姓名或用户 ID；空字符串返回默认候选。","张三")),
    Map.entry("entityType",new ParameterDoc("实体类型","审计实体类型，例如 APPOINTMENT、ACCOUNT、TEAM。","APPOINTMENT")),
    Map.entry("entityId",new ParameterDoc("实体 ID","被审计实体 UUID。","65d31b4f-b244-4a50-9a18-1e9a192f5a22")),
    Map.entry("userId",new ParameterDoc("用户标识","用于查看对应 Mock 卡片私有数据。","streamer01")),
    Map.entry("key",new ParameterDoc("故障键","Mock 故障类型，例如 HTTP_5XX、CARD_DELETED。","HTTP_5XX")),
    Map.entry("orgId",new ParameterDoc("组织 ID","魔点组织唯一标识。","mock-org")),
    Map.entry("signVersion",new ParameterDoc("签名版本","魔点签名算法版本。","1")),
    Map.entry("timestamp",new ParameterDoc("时间戳","魔点回调签名时间戳。","1788844800000")),
    Map.entry("nonce",new ParameterDoc("随机串","参与签名计算的随机值。","nonce-example")),
    Map.entry("signature",new ParameterDoc("签名","生产环境必填；基于原始请求体及查询参数计算。","signature-example")),
    Map.entry("file",new ParameterDoc("图片文件","PNG 或 JPG，最大 2MB，宽高不超过 4096 像素。","avatar.png")));
}
