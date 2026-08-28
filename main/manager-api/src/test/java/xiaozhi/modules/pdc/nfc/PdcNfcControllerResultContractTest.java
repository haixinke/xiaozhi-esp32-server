package xiaozhi.modules.pdc.nfc;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import xiaozhi.common.utils.Result;

/**
 * NFC 模块 controller 返回值约定测试。
 * 背景：preview/confirm 曾返回裸 VO，小程序 request.js 按 envelope.code 判断成败，
 * 裸 VO 无 code 字段被判"服务响应异常"。此处用反射把"必须返回 Result 信封"固化为约定。
 */
class PdcNfcControllerResultContractTest {

    /** 合法返回类型：Result 信封、文件下载 void、显式响应实体、流式响应 */
    private static final List<Class<?>> ALLOWED_RETURN_TYPES = List.of(
            Result.class, void.class, ResponseEntity.class, StreamingResponseBody.class);

    @Test
    @DisplayName("NFC controller 映射方法必须返回 Result 信封或流式/void")
    void mappedMethodsMustReturnResultEnvelope() throws Exception {
        List<Class<?>> controllers = findNfcControllers();
        // 防空扫描假绿：包路径写错导致一个 controller 都没扫到时必须报错
        assertThat(controllers).as("NFC controller 扫描结果不能为空").isNotEmpty();
        List<String> violations = new ArrayList<>();
        for (Class<?> controller : controllers) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isMappedEndpoint(method)) {
                    continue;
                }
                boolean allowed = ALLOWED_RETURN_TYPES.stream()
                        .anyMatch(type -> type.isAssignableFrom(method.getReturnType()));
                if (!allowed) {
                    violations.add(controller.getSimpleName() + "#" + method.getName()
                            + " 返回 " + method.getReturnType().getSimpleName());
                }
            }
        }
        assertThat(violations)
                .as("NFC controller 端点必须返回 Result<T> 信封（文件下载可用 void/ResponseEntity）")
                .isEmpty();
    }

    /** 扫描 nfc controller 包下所有 @RestController 类 */
    private List<Class<?>> findNfcControllers() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        CachingMetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resolver);
        List<Class<?>> controllers = new ArrayList<>();
        for (org.springframework.core.io.Resource resource : resolver
                .getResources("classpath*:xiaozhi/modules/pdc/nfc/controller/*.class")) {
            String className = readerFactory.getMetadataReader(resource).getClassMetadata().getClassName();
            Class<?> clazz = Class.forName(className);
            if (clazz.isAnnotationPresent(RestController.class)) {
                controllers.add(clazz);
            }
        }
        return controllers;
    }

    /** 判断方法是否声明了任一 HTTP 映射注解 */
    private boolean isMappedEndpoint(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(RequestMapping.class);
    }
}
