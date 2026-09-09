package com.jiabei.cloud.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.jiabei.cloud.web.AccountController;
import com.jiabei.cloud.web.AdminAppointmentController;
import com.jiabei.cloud.web.AdminResourceController;
import com.jiabei.cloud.web.AppointmentController;
import com.jiabei.cloud.web.AuthController;
import com.jiabei.cloud.web.DingTalkAuthController;
import com.jiabei.cloud.web.DingTalkDirectoryController;
import com.jiabei.cloud.web.ImageUploadController;
import com.jiabei.cloud.web.MockAuthController;
import com.jiabei.cloud.web.MockCardController;
import com.jiabei.cloud.web.MoredianRecognitionController;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * OpenAPI 文档覆盖守卫。
 *
 * <p>测试从控制器注解推导真实路由，而不是重复手写数量；任何新增、删除或改路径的接口
 * 都必须同步更新中文契约登记，否则此测试会给出缺失的“HTTP 方法 + 路径”。</p>
 */
class OpenApiDocumentationTest {
  private static final Class<?>[] CONTROLLERS={
    AuthController.class,AppointmentController.class,AdminAppointmentController.class,
    AdminResourceController.class,AccountController.class,DingTalkAuthController.class,
    DingTalkDirectoryController.class,ImageUploadController.class,
    MoredianRecognitionController.class,MockAuthController.class,MockCardController.class
  };

  @Test
  void everyControllerOperationHasCompleteChineseDocumentation(){
    Set<String> actual=controllerOperations();

    assertThat(actual).hasSize(41);
    assertThat(OpenApiDocumentation.documentedOperations()).containsExactlyInAnyOrderElementsOf(actual);
    assertThat(OpenApiDocumentation.incompleteOperations()).isEmpty();
  }

  private Set<String> controllerOperations(){
    Set<String> operations=new LinkedHashSet<>();
    for(Class<?> controller:CONTROLLERS){
      RequestMapping root=AnnotatedElementUtils.findMergedAnnotation(controller,RequestMapping.class);
      String base=firstPath(root);
      for(Method method:controller.getDeclaredMethods()){
        RequestMapping mapping=AnnotatedElementUtils.findMergedAnnotation(method,RequestMapping.class);
        if(mapping==null)continue;
        String path=normalize(base+firstPath(mapping));
        for(RequestMethod httpMethod:mapping.method())operations.add(httpMethod.name()+" "+path);
      }
    }
    return operations;
  }

  private String firstPath(RequestMapping mapping){
    if(mapping==null)return "";
    if(mapping.path().length>0)return mapping.path()[0];
    if(mapping.value().length>0)return mapping.value()[0];
    return "";
  }

  private String normalize(String path){
    String normalized=path.replaceAll("/{2,}","/");
    return normalized.isEmpty()?"/":normalized;
  }
}
