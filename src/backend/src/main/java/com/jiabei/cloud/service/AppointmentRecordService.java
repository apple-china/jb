package com.jiabei.cloud.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.security.CurrentUser;
import com.jiabei.cloud.web.BusinessException;
import java.io.ByteArrayOutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AppointmentRecordService {
  private static final Set<Integer> PAGE_SIZES=Set.of(30,50,100);
  private static final Set<String> APPOINTMENT_STATUSES=Set.of("ACTIVE","CANCELLED");
  private static final Set<String> ATTENDANCE_STATUSES=Set.of("PENDING","ARRIVED","NOT_ARRIVED","LATE");
  private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final ZoneId zone;

  public AppointmentRecordService(JdbcTemplate jdbc,ObjectMapper json,BookingProperties props){this.jdbc=jdbc;this.json=json;this.zone=props.zoneId();}

  public record Filter(LocalDate startDate,LocalDate endDate,String streamer,String makeupArtist,String attendanceStatus,String status,int page,int size){}
  private record SqlParts(String where,List<Object> args){}
  private record ExportRow(UUID id,LocalDate bookingDate,OffsetDateTime startAt,String makeupArtistName,String teamName,String streamerName,String status,String attendanceStatus,OffsetDateTime attendanceAt,OffsetDateTime createdAt,String source,String creatorName,String streamerIdentity){}

  public Map<String,Object> list(CurrentUser actor,Filter filter){
    validate(actor,filter,true);SqlParts parts=where(actor,filter);int total=jdbc.queryForObject("SELECT count(*) FROM appointment a JOIN app_user u ON u.id=a.streamer_user_id "+parts.where(),Integer.class,parts.args().toArray());
    List<Object> args=new ArrayList<>(parts.args());args.add(filter.size());args.add((filter.page()-1)*filter.size());
    String sql="SELECT a.*,coalesce(u.dingtalk_user_id,u.username,u.id::text) identity,(SELECT count(*) FROM appointment p WHERE p.booking_date=a.booking_date AND (p.created_at<a.created_at OR (p.created_at=a.created_at AND p.id<=a.id))) daily_sequence FROM appointment a JOIN app_user u ON u.id=a.streamer_user_id "+parts.where()+" ORDER BY a.booking_date DESC,a.start_at,a.created_at,a.id LIMIT ? OFFSET ?";
    List<Map<String,Object>> items=jdbc.query(sql,this::mapAppointment,args.toArray());int pages=total==0?0:(total+filter.size()-1)/filter.size();
    return Map.of("items",items,"total",total,"page",filter.page(),"size",filter.size(),"totalPages",pages);
  }

  public byte[] export(CurrentUser actor,Filter filter){
    if(!actor.isAdministrator())throw BusinessException.forbidden();validate(actor,filter,false);SqlParts parts=where(actor,filter);
    String sql="SELECT a.id,a.booking_date,a.start_at,a.makeup_artist_name_snapshot,a.team_name_snapshot,a.streamer_name_snapshot,a.status,a.attendance_status,a.attendance_evidence_at,a.created_at,a.source,coalesce((SELECT al.actor_name_snapshot FROM audit_log al WHERE al.entity_type='APPOINTMENT' AND al.entity_id=a.id AND al.action='CREATE' ORDER BY al.created_at LIMIT 1),creator.nickname),coalesce(u.dingtalk_user_id,u.username,u.id::text) FROM appointment a JOIN app_user u ON u.id=a.streamer_user_id LEFT JOIN app_user creator ON creator.id=a.created_by_user_id "+parts.where()+" ORDER BY a.booking_date DESC,a.start_at,a.created_at,a.id";
    List<ExportRow> rows=jdbc.query(sql,this::mapExport,parts.args().toArray());Map<String,String> makeupArtists=names("makeup_artist"),teams=names("team");
    try(XSSFWorkbook workbook=new XSSFWorkbook();ByteArrayOutputStream output=new ByteArrayOutputStream()){
      Sheet sheet=workbook.createSheet("预约记录");sheet.createFreezePane(0,1);String[] headers={"序号","预约日期","预约时间","化妆师","团队","主播名字","预约状态","签到状态","迟到分钟","修改记录","提交时间","打卡时间","代预约人","主播ID","数据ID"};
      CellStyle header=workbook.createCellStyle();header.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());header.setFillPattern(FillPatternType.SOLID_FOREGROUND);header.setAlignment(HorizontalAlignment.CENTER);org.apache.poi.ss.usermodel.Font font=workbook.createFont();font.setBold(true);header.setFont(font);
      CellStyle wrap=workbook.createCellStyle();wrap.setWrapText(true);Row title=sheet.createRow(0);for(int i=0;i<headers.length;i++){title.createCell(i).setCellValue(headers[i]);title.getCell(i).setCellStyle(header);}
      int index=1;for(ExportRow item:rows){Row row=sheet.createRow(index);String[] values={String.valueOf(index),item.bookingDate().format(DATE),local(item.startAt()).substring(11,16),item.makeupArtistName(),item.teamName(),item.streamerName(),status(item.status()),attendance(item.attendanceStatus()),lateMinutes(item),modifications(item.id(),makeupArtists,teams),local(item.createdAt()),item.attendanceAt()==null?"":local(item.attendanceAt()),"STREAMER".equals(item.source())?"主播本人":safe(item.creatorName()),safe(item.streamerIdentity()),item.id().toString()};for(int i=0;i<values.length;i++){row.createCell(i).setCellValue(values[i]);if(i==9)row.getCell(i).setCellStyle(wrap);}index++;}
      int[] widths={8,13,10,15,15,15,13,13,12,52,21,21,16,24,39};for(int i=0;i<widths.length;i++)sheet.setColumnWidth(i,widths[i]*256);sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,Math.max(0,index-1),0,headers.length-1));workbook.write(output);return output.toByteArray();
    }catch(Exception e){throw new IllegalStateException("预约记录导出失败。",e);}
  }

  private void validate(CurrentUser actor,Filter f,boolean paged){
    if(f.startDate()==null||f.endDate()==null||f.startDate().isAfter(f.endDate()))throw invalid("开始日期不能晚于截止日期。");
    if(paged&&(f.page()<1||!PAGE_SIZES.contains(f.size())))throw invalid("分页参数无效。");
    if(text(f.status())&&!APPOINTMENT_STATUSES.contains(f.status()))throw invalid("预约状态无效。");if(text(f.attendanceStatus())&&!ATTENDANCE_STATUSES.contains(f.attendanceStatus()))throw invalid("签到状态无效。");
    if(actor.role()==CurrentUser.Role.MAKEUP){LocalDate today=LocalDate.now(zone);if(!f.startDate().equals(f.endDate())||f.startDate().isBefore(today)||f.startDate().isAfter(today.plusDays(1)))throw BusinessException.forbidden();}
    else if(!actor.isAdministrator()&&actor.role()!=CurrentUser.Role.OBSERVER)throw BusinessException.forbidden();
  }

  private SqlParts where(CurrentUser actor,Filter f){
    StringBuilder sql=new StringBuilder("WHERE a.booking_date BETWEEN ? AND ?");List<Object> args=new ArrayList<>(List.of(f.startDate(),f.endDate()));
    if(text(f.streamer())){sql.append(" AND (u.dingtalk_user_id=? OR u.username=?)");args.add(f.streamer());args.add(f.streamer());}
    if(actor.role()==CurrentUser.Role.MAKEUP){sql.append(" AND a.makeup_artist_id=?");args.add(actor.makeupArtistId());}else if(text(f.makeupArtist())){try{sql.append(" AND a.makeup_artist_id=?");args.add(UUID.fromString(f.makeupArtist()));}catch(IllegalArgumentException e){throw invalid("化妆师参数无效。");}}
    if(text(f.attendanceStatus())){sql.append(" AND a.attendance_status=?");args.add(f.attendanceStatus());}if(text(f.status())){sql.append(" AND a.status=?");args.add(f.status());}return new SqlParts(sql.toString(),args);
  }

  private Map<String,Object> mapAppointment(ResultSet rs,int n)throws SQLException{Map<String,Object> m=new LinkedHashMap<>();LocalDate d=rs.getObject("booking_date",LocalDate.class);String identity=rs.getString("identity");m.put("id",rs.getObject("id",UUID.class));m.put("bookingNumber",String.format("%02d%02d%05d-%s",d.getMonthValue(),d.getDayOfMonth(),10000+rs.getInt("daily_sequence"),identity));m.put("bookingDate",d);m.put("startTime",rs.getObject("start_at",OffsetDateTime.class).atZoneSameInstant(zone).toLocalTime().toString());m.put("streamerName",rs.getString("streamer_name_snapshot"));m.put("streamerDingTalkUserId",identity);m.put("makeupArtistId",rs.getObject("makeup_artist_id",UUID.class));m.put("makeupArtistName",rs.getString("makeup_artist_name_snapshot"));m.put("teamId",rs.getObject("team_id",UUID.class));m.put("teamName",rs.getString("team_name_snapshot"));m.put("status",rs.getString("status"));m.put("attendanceStatus",rs.getString("attendance_status"));m.put("attendanceFrozen",rs.getBoolean("attendance_frozen"));m.put("attendanceEvidenceAt",rs.getObject("attendance_evidence_at"));m.put("conflictOverride",rs.getBoolean("conflict_override"));m.put("version",rs.getInt("version"));m.put("createdAt",rs.getObject("created_at",OffsetDateTime.class));m.put("changed",rs.getInt("version")>0);return m;}
  private ExportRow mapExport(ResultSet rs,int n)throws SQLException{return new ExportRow(rs.getObject(1,UUID.class),rs.getObject(2,LocalDate.class),rs.getObject(3,OffsetDateTime.class),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getObject(9,OffsetDateTime.class),rs.getObject(10,OffsetDateTime.class),rs.getString(11),rs.getString(12),rs.getString(13));}
  private Map<String,String> names(String table){return jdbc.query("SELECT id::text,name FROM "+table,(rs,n)->Map.entry(rs.getString(1),rs.getString(2))).stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));}
  private String modifications(UUID id,Map<String,String> makeupArtists,Map<String,String> teams){List<String> entries=jdbc.query("SELECT created_at,actor_name_snapshot,reason,before_data::text,after_data::text FROM audit_log WHERE entity_type='APPOINTMENT' AND entity_id=? AND action='MODIFY' ORDER BY created_at",(rs,n)->{String changes=diff(rs.getString(4),rs.getString(5),makeupArtists,teams),reason=rs.getString(3);return local(rs.getObject(1,OffsetDateTime.class))+"｜"+rs.getString(2)+"｜"+changes+(reason==null?"":"｜原因："+reason);},id);return entries.isEmpty()?"无":String.join("\n",entries);}
  private String diff(String beforeText,String afterText,Map<String,String> makeupArtists,Map<String,String> teams){try{JsonNode before=json.readTree(beforeText),after=json.readTree(afterText);List<String> changes=new ArrayList<>();append(changes,"时间",before,after,"startTime",Map.of());append(changes,"化妆师",before,after,"makeupArtistId",makeupArtists);append(changes,"团队",before,after,"teamId",teams);return changes.isEmpty()?safe(beforeText)+" → "+safe(afterText):String.join("；",changes);}catch(Exception e){return safe(beforeText)+" → "+safe(afterText);}}
  private void append(List<String> changes,String label,JsonNode before,JsonNode after,String field,Map<String,String> names){String left=value(before,field,names),right=value(after,field,names);if(!left.equals(right))changes.add(label+" "+left+"→"+right);}
  private String value(JsonNode node,String field,Map<String,String> names){if(node==null||node.get(field)==null||node.get(field).isNull())return "—";String raw=node.get(field).asText();return names.getOrDefault(raw,raw);}
  private String lateMinutes(ExportRow row){if(!"LATE".equals(row.attendanceStatus())||row.attendanceAt()==null)return "";return String.valueOf(Math.max(0,Duration.between(row.startAt(),row.attendanceAt()).toMinutes()));}
  private String local(OffsetDateTime value){return value.atZoneSameInstant(zone).format(DATE_TIME);}
  private String status(String value){return "ACTIVE".equals(value)?"有效":"已取消";}private String attendance(String value){return Map.of("PENDING","待到司","ARRIVED","已到司","NOT_ARRIVED","未到","LATE","迟到").getOrDefault(value,value);}
  private boolean text(String value){return value!=null&&!value.isBlank();}private String safe(String value){return value==null?"":value;}private BusinessException invalid(String message){return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_RECORD_FILTER",message);}
}
