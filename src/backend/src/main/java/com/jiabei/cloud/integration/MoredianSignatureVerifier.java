package com.jiabei.cloud.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class MoredianSignatureVerifier {
  public boolean verify(String rawBody,String nonce,String orgId,String signVersion,String timestamp,String signature,String key){
    if(key==null||key.isBlank()||signature==null)return false;
    try{String bodyMd5=HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(rawBody.getBytes(StandardCharsets.UTF_8)));String text=nonce+orgId+signVersion+timestamp+bodyMd5;Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA1"));byte[] expected=Base64.getEncoder().encode(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));return MessageDigest.isEqual(expected,signature.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException("无法校验魔点签名",e);}
  }
}
