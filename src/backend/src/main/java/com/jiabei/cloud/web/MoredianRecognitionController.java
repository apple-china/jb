package com.jiabei.cloud.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiabei.cloud.integration.MoredianSignatureVerifier;
import com.jiabei.cloud.service.AttendanceService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/integrations/moredian")
public class MoredianRecognitionController {
  private final AttendanceService attendance;private final ObjectMapper json;private final MoredianSignatureVerifier signatures;private final String expectedOrg;private final String key;private final String expectedDevice;private final boolean requireSignature;
  public MoredianRecognitionController(AttendanceService attendance,ObjectMapper json,MoredianSignatureVerifier signatures,@Value("${jiabei.moredian.org-id}") String expectedOrg,@Value("${jiabei.moredian.org-auth-key:}") String key,@Value("${jiabei.moredian.device-sn}") String expectedDevice,@Value("${jiabei.moredian.require-signature:true}") boolean requireSignature){this.attendance=attendance;this.json=json;this.signatures=signatures;this.expectedOrg=expectedOrg;this.key=key;this.expectedDevice=expectedDevice;this.requireSignature=requireSignature;}
  @PostMapping("/recognition-events") public Map<String,String> receive(@RequestParam String orgId,@RequestParam String signVersion,@RequestParam String timestamp,@RequestParam String nonce,@RequestParam(required=false) String signature,@RequestBody String raw){
    if(raw.getBytes(StandardCharsets.UTF_8).length>64*1024)throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE,"PAYLOAD_TOO_LARGE","回调内容过大。");if(!expectedOrg.equals(orgId))return ok();if(requireSignature&&!signatures.verify(raw,nonce,orgId,signVersion,timestamp,signature,key))throw new BusinessException(HttpStatus.FORBIDDEN,"INVALID_SIGNATURE","回调签名无效。");
    try{JsonNode root=json.readTree(raw),data=root.path("data");if(!"REC_SUCCESS".equals(root.path("callbackTag").asText()))return ok();String member=data.path("memberId").asText(),device=data.path("deviceSn").asText(),recognized=data.path("recognizeTime").asText();if(member.isBlank()||recognized.isBlank())throw new IllegalArgumentException();if(!expectedDevice.equals(device))return ok();String dedup=sha256(orgId+"|"+member+"|"+device+"|"+recognized);attendance.ingest(dedup,orgId,device,member,Instant.ofEpochMilli(Long.parseLong(recognized)).atZone(ZoneId.of("Asia/Shanghai")),raw,"moredian-"+dedup.substring(0,12));return ok();}catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_CALLBACK","回调字段无效。");}
  }
  private Map<String,String> ok(){return Map.of("result","0","message","操作成功");}private String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
