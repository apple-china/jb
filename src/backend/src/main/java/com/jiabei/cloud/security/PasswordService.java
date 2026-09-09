package com.jiabei.cloud.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
  private static final int ITERATIONS=210_000;private final SecureRandom random=new SecureRandom();
  public String encode(String password){byte[] salt=new byte[16];random.nextBytes(salt);return "{pbkdf2}"+ITERATIONS+"$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(derive(password,salt,ITERATIONS));}
  public boolean matches(String password,String encoded){if(encoded==null)return false;if(encoded.startsWith("{noop}"))return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),encoded.substring(6).getBytes(StandardCharsets.UTF_8));if(!encoded.startsWith("{pbkdf2}"))return false;try{String[] p=encoded.substring(8).split("\\$");int iterations=Integer.parseInt(p[0]);byte[] salt=Base64.getDecoder().decode(p[1]),expected=Base64.getDecoder().decode(p[2]);return MessageDigest.isEqual(expected,derive(password,salt,iterations));}catch(Exception e){return false;}}
  private byte[] derive(String password,byte[] salt,int iterations){try{PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,iterations,256);return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}catch(Exception e){throw new IllegalStateException(e);}}
}
