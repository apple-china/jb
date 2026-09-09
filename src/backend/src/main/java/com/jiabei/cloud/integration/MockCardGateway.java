package com.jiabei.cloud.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Profile({"local","test"}) @Component
public class MockCardGateway implements CardGateway {
  private final JdbcTemplate jdbc;private final ObjectMapper json;
  public MockCardGateway(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
  @Override public void create(CardPayload p){try{fault();}catch(CardGatewayException e){log(p,"CREATE",e.code());throw e;}jdbc.update("INSERT INTO mock_card_delivery(out_track_id,group_id,business_date,card_data,private_data,content_version,status) VALUES (?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,'ACTIVE') ON CONFLICT(out_track_id) DO UPDATE SET card_data=CASE WHEN mock_card_delivery.content_version<=excluded.content_version THEN excluded.card_data ELSE mock_card_delivery.card_data END,private_data=CASE WHEN mock_card_delivery.content_version<=excluded.content_version THEN excluded.private_data ELSE mock_card_delivery.private_data END,content_version=GREATEST(mock_card_delivery.content_version,excluded.content_version),status='ACTIVE',updated_at=now()",p.outTrackId(),p.groupId(),p.businessDate(),toJson(p.cardData()),toJson(p.privateData()),p.contentVersion());log(p,"CREATE","OK");}
  @Override public void update(CardPayload p){try{fault();}catch(CardGatewayException e){if(e.unrecoverable())jdbc.update("UPDATE mock_card_delivery SET status='DELETED',updated_at=now() WHERE out_track_id=?",p.outTrackId());log(p,"UPDATE",e.code());throw e;}Integer exists=jdbc.queryForObject("SELECT count(*) FROM mock_card_delivery WHERE out_track_id=? AND status='ACTIVE'",Integer.class,p.outTrackId());if(exists==null||exists==0){log(p,"UPDATE","CARD_DELETED");throw new CardGatewayException("CARD_DELETED",false,true);}jdbc.update("UPDATE mock_card_delivery SET card_data=CASE WHEN content_version<=? THEN CAST(? AS jsonb) ELSE card_data END,private_data=CASE WHEN content_version<=? THEN CAST(? AS jsonb) ELSE private_data END,content_version=GREATEST(content_version,?),updated_at=now() WHERE out_track_id=?",p.contentVersion(),toJson(p.cardData()),p.contentVersion(),toJson(p.privateData()),p.contentVersion(),p.outTrackId());log(p,"UPDATE","OK");}
  private void fault(){List<String> keys=jdbc.query("WITH selected AS (SELECT fault_key FROM mock_fault_setting WHERE enabled AND remaining_count<>0 ORDER BY fault_key LIMIT 1 FOR UPDATE), changed AS (UPDATE mock_fault_setting f SET remaining_count=CASE WHEN f.remaining_count>0 THEN f.remaining_count-1 ELSE f.remaining_count END,enabled=CASE WHEN f.remaining_count=1 THEN false ELSE f.enabled END,updated_at=now() FROM selected s WHERE f.fault_key=s.fault_key RETURNING f.fault_key) SELECT fault_key FROM changed",(rs,n)->rs.getString(1));if(keys.isEmpty())return;String key=keys.getFirst();switch(key){case "NETWORK_TIMEOUT"->throw new CardGatewayException(key,true,false);case "HTTP_5XX"->throw new CardGatewayException(key,true,false);case "CARD_RESULT_UNKNOWN"->throw new CardGatewayException(key,true,false);case "CARD_DELETED"->throw new CardGatewayException(key,false,true);case "TOKEN_EXPIRED"->throw new CardGatewayException(key,true,false);case "OUT_OF_ORDER"->throw new CardGatewayException(key,true,false);default->throw new CardGatewayException("MOCK_FAILURE",false,false);}}
  private void log(CardPayload p,String operation,String result){jdbc.update("INSERT INTO mock_card_call_log(id,out_track_id,operation,content_version,result_code) VALUES (?,?,?,?,?)",UUID.randomUUID(),p.outTrackId(),operation,p.contentVersion(),result);}
  private String toJson(Object o){try{return json.writeValueAsString(o);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
}
