package com.jiabei.cloud.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

class ImageUploadControllerTest {
  @TempDir Path directory;
  @Test void acceptsRealPngAndRejectsDisguisedOrOversizedFiles()throws Exception{ImageUploadController controller=new ImageUploadController(directory.toString());ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB),"png",bytes);var ok=controller.upload(new MockMultipartFile("file","avatar.png","image/png",bytes.toByteArray()),new MockHttpServletRequest());assertThat(ok.data().get("url")).asString().endsWith(".png");assertThat(directory.toFile().listFiles()).hasSize(1);assertThatThrownBy(()->controller.upload(new MockMultipartFile("file","fake.png","image/png","not-an-image".getBytes()),new MockHttpServletRequest())).isInstanceOf(BusinessException.class).extracting(e->((BusinessException)e).code()).isEqualTo("UPLOAD_FILE_INVALID");assertThatThrownBy(()->controller.upload(new MockMultipartFile("file","large.jpg","image/jpeg",new byte[2*1024*1024+1]),new MockHttpServletRequest())).isInstanceOf(BusinessException.class).extracting(e->((BusinessException)e).code()).isEqualTo("UPLOAD_FILE_TOO_LARGE");}
}
