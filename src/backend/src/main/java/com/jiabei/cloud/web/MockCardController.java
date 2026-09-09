package com.jiabei.cloud.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.service.CardOutboxWorker;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@Profile({"local","test"}) @RestController @RequestMapping("/api/v1/mock")
public class MockCardController {
  private final JdbcTemplate jdbc;private final ObjectMapper json;private final CardOutboxWorker worker;
  public MockCardController(JdbcTemplate jdbc,ObjectMapper json,CardOutboxWorker worker){this.jdbc=jdbc;this.json=json;this.worker=worker;}
  @GetMapping("/cards") ApiResponse<List<Map<String,Object>>> cards(@RequestParam String userId,HttpServletRequest request){List<Map<String,Object>> result=new ArrayList<>();jdbc.query("SELECT * FROM mock_card_delivery ORDER BY business_date DESC",rs->{Map<String,Object> m=new LinkedHashMap<>();m.put("outTrackId",rs.getString("out_track_id"));m.put("groupId",rs.getString("group_id"));m.put("businessDate",rs.getObject("business_date"));m.put("cardData",read(rs.getString("card_data")));Map<String,Object> all=read(rs.getString("private_data"));m.put("privateData",all.getOrDefault(userId,Map.of()));m.put("hasPrivateData",all.containsKey(userId));m.put("contentVersion",rs.getLong("content_version"));m.put("status",rs.getString("status"));m.put("updatedAt",rs.getObject("updated_at"));result.add(m);});return ApiResponse.ok(result,Trace.id(request));}
  @GetMapping("/card-calls") ApiResponse<List<Map<String,Object>>> calls(HttpServletRequest r){return ApiResponse.ok(jdbc.query("SELECT out_track_id,operation,content_version,result_code,created_at FROM mock_card_call_log ORDER BY created_at DESC LIMIT 100",(rs,n)->Map.of("outTrackId",rs.getString(1),"operation",rs.getString(2),"contentVersion",rs.getLong(3),"resultCode",rs.getString(4),"createdAt",rs.getObject(5))),Trace.id(r));}
  @Schema(name="MockFaultRequest",title="Mock 故障配置请求",description="仅 Local/Test Profile 使用。")
  record FaultBody(@Schema(title="启用",description="是否启用指定故障。",example="true") boolean enabled,@Schema(title="剩余次数",description="-1 持续触发，0 不触发，正数为剩余触发次数。",example="1") int remainingCount){}
  @PostMapping("/faults/{key}") ApiResponse<Map<String,Object>> fault(@PathVariable String key,@RequestBody FaultBody body,HttpServletRequest r){int n=jdbc.update("UPDATE mock_fault_setting SET enabled=?,remaining_count=?,updated_at=now() WHERE fault_key=?",body.enabled(),body.remainingCount(),key);if(n==0)throw new BusinessException(org.springframework.http.HttpStatus.NOT_FOUND,"FAULT_NOT_FOUND","故障类型不存在。");return ApiResponse.ok(Map.of("key",key,"enabled",body.enabled(),"remainingCount",body.remainingCount()),Trace.id(r));}
  @PostMapping("/cards/run") ApiResponse<Map<String,Object>> run(HttpServletRequest r){worker.poll();return ApiResponse.ok(Map.of("processed",true),Trace.id(r));}
  private Map<String,Object> read(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){return Map.of();}}
}
