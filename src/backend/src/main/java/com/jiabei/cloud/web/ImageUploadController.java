package com.jiabei.cloud.web;

import jakarta.servlet.http.HttpServletRequest;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/v1/admin/uploads")
public class ImageUploadController {
  private static final long MAX_BYTES=2L*1024*1024;private static final int MAX_DIMENSION=4096;
  private final Path directory;
  public ImageUploadController(@Value("${jiabei.upload-directory:uploads}") String directory){this.directory=Path.of(directory).toAbsolutePath().normalize();}
  @PostMapping("/images") ApiResponse<Map<String,Object>> upload(@RequestParam("file") MultipartFile file,HttpServletRequest request){
    if(file.isEmpty())throw invalid();if(file.getSize()>MAX_BYTES)throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE,"UPLOAD_FILE_TOO_LARGE","图片不能超过 2MB。");
    String type=file.getContentType();if(!"image/png".equals(type)&&!"image/jpeg".equals(type))throw invalid();String extension="image/png".equals(type)?".png":".jpg";
    try{BufferedImage image;try(InputStream in=file.getInputStream()){image=ImageIO.read(in);}if(image==null||image.getWidth()>MAX_DIMENSION||image.getHeight()>MAX_DIMENSION)throw invalid();Files.createDirectories(directory);String name=UUID.randomUUID()+extension;Path target=directory.resolve(name).normalize();if(!target.getParent().equals(directory))throw invalid();try(InputStream in=file.getInputStream()){Files.copy(in,target,StandardCopyOption.REPLACE_EXISTING);}return ApiResponse.ok(Map.of("url","/uploads/"+name,"width",image.getWidth(),"height",image.getHeight()),Trace.id(request));}
    catch(BusinessException e){throw e;}catch(IOException e){throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,"UPLOAD_FAILED","图片保存失败，请稍后重试。");}
  }
  private BusinessException invalid(){return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"UPLOAD_FILE_INVALID","请选择有效的 PNG 或 JPG 图片。");}
}
