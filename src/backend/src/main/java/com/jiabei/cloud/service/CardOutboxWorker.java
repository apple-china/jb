package com.jiabei.cloud.service;

import com.jiabei.cloud.config.BookingProperties;
import com.jiabei.cloud.integration.CardGateway;
import com.jiabei.cloud.integration.CardGateway.CardPayload;
import com.jiabei.cloud.integration.CardGatewayException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
/**
 * 钉钉卡片 Outbox 消费器。
 *
 * <p>业务事务只负责落库任务；本类异步调用外部网关并负责退避重试，
 * 从而避免第三方超时拖长或回滚预约事务。</p>
 */
public class CardOutboxWorker {
  private final JdbcTemplate jdbc;private final CardProjectionService projection;private final CardGateway gateway;private final BookingProperties props;private final TransactionTemplate transactions;
  public CardOutboxWorker(JdbcTemplate jdbc,CardProjectionService projection,CardGateway gateway,BookingProperties props,TransactionTemplate transactions){this.jdbc=jdbc;this.projection=projection;this.gateway=gateway;this.props=props;this.transactions=transactions;}
  @Scheduled(fixedDelayString="${jiabei.booking.outbox-poll-ms:2000}") public void poll(){List<UUID> ids=transactions.execute(status->claim());if(ids!=null)ids.forEach(this::execute);}
  private List<UUID> claim(){List<UUID> ids=jdbc.query("SELECT id FROM integration_job WHERE status IN ('PENDING','RETRY_WAIT') AND next_attempt_at<=now() AND (locked_at IS NULL OR locked_at<now()-interval '2 minutes') ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT 10",(rs,n)->rs.getObject(1,UUID.class));ids.forEach(id->jdbc.update("UPDATE integration_job SET status='RUNNING',locked_at=now(),locked_by='local-worker',updated_at=now() WHERE id=?",id));return ids;}
  /**
   * 网关调用成功后才写 first_delivered_at；“任务已入队”或“已领取”都不算成功发送。
   * 首次成功时间使用 COALESCE 保留，后续重新发送不会改变历史判断。
   */
  void execute(UUID id){Map<String,Object> job=jdbc.queryForMap("SELECT job_type,business_key,attempt_count,max_attempts FROM integration_job WHERE id=?",id);String type=(String)job.get("job_type"),key=(String)job.get("business_key");try{CardPayload payload;if("CARD_REFRESH".equals(type)){String[] parts=key.split("\\|");LocalDate date=LocalDate.parse(parts[parts.length-1]);String group=String.join("|",Arrays.copyOf(parts,parts.length-1));payload=projection.projectSchedule(date,group);Integer deliveredVersion=jdbc.queryForObject("SELECT delivered_version FROM daily_card WHERE out_track_id=?",Integer.class,payload.outTrackId());if(deliveredVersion!=null&&deliveredVersion>0)gateway.update(payload);else gateway.create(payload);jdbc.update("UPDATE daily_card SET status='ACTIVE',delivered_version=GREATEST(delivered_version,?),first_delivered_at=COALESCE(first_delivered_at,now()),last_error_code=NULL,updated_at=now() WHERE out_track_id=?",payload.contentVersion(),payload.outTrackId());}else if("LATE_REMINDER".equals(type)){UUID appointmentId=UUID.fromString(key);Integer stillEligible=jdbc.queryForObject("SELECT count(*) FROM appointment WHERE id=? AND status='ACTIVE' AND attendance_status='LATE' AND attendance_event_id IS NULL",Integer.class,appointmentId);if(stillEligible==null||stillEligible==0){succeed(id);return;}payload=projection.projectLate(appointmentId);gateway.create(payload);jdbc.update("UPDATE late_notification SET status='SENT',sent_at=now(),updated_at=now() WHERE appointment_id=?",appointmentId);jdbc.update("UPDATE appointment SET late_reminded_at=now(),updated_at=now() WHERE id=?",appointmentId);}else throw new IllegalArgumentException("unknown integration job");succeed(id);}catch(CardGatewayException e){if("CARD_REFRESH".equals(type)&&e.unrecoverable()){replaceCard(key);jdbc.update("UPDATE integration_job SET status='PENDING',attempt_count=attempt_count+1,locked_at=NULL,locked_by=NULL,last_error_code=?,next_attempt_at=now(),updated_at=now() WHERE id=?",e.code(),id);}else retryOrDead(id,job,e.code(),e.retryable());}catch(Exception e){retryOrDead(id,job,"INTERNAL",false);}}
  private void succeed(UUID id){jdbc.update("UPDATE integration_job SET status='SUCCEEDED',attempt_count=attempt_count+1,locked_at=NULL,locked_by=NULL,updated_at=now() WHERE id=?",id);}
  private void retryOrDead(UUID id,Map<String,Object> job,String code,boolean retryable){int attempt=((Number)job.get("attempt_count")).intValue()+1,max=((Number)job.get("max_attempts")).intValue();String status=retryable&&attempt<max?"RETRY_WAIT":"DEAD";int delay=Math.min(300,(int)Math.pow(2,attempt));jdbc.update("UPDATE integration_job SET status=?,attempt_count=?,next_attempt_at=now()+(?||' seconds')::interval,locked_at=NULL,locked_by=NULL,last_error_code=?,last_error_message='Card gateway failure',updated_at=now() WHERE id=?",status,attempt,Integer.toString(delay),code,id);if("LATE_REMINDER".equals(job.get("job_type")))jdbc.update("UPDATE late_notification SET status=?,attempt_count=?,last_error_code=?,updated_at=now() WHERE appointment_id=?","DEAD".equals(status)?"FAILED":"PENDING",attempt,code,UUID.fromString((String)job.get("business_key")));}
  /** 不可恢复的卡片实例错误需要更换 outTrackId，但保留 first_delivered_at 作为历史成功凭据。 */
  private void replaceCard(String key){String[] parts=key.split("\\|");LocalDate date=LocalDate.parse(parts[parts.length-1]);String group=String.join("|",Arrays.copyOf(parts,parts.length-1));String out="schedule-card-"+date+"-replacement-"+UUID.randomUUID().toString().substring(0,8);jdbc.update("UPDATE daily_card SET status='PENDING',out_track_id=?,content_version=content_version+1,delivered_version=0,last_error_code='REPLACED',updated_at=now() WHERE business_date=? AND group_open_conversation_id=?",out,date,group);}
}
