package xiaozhi.modules.drawing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xiaozhi.modules.drawing.controller.DrawingController;
import xiaozhi.modules.drawing.service.DrawingService;
import xiaozhi.modules.drawing.vo.*;
import xiaozhi.modules.security.user.SecurityUser;

/** HTTP 契约：身份取登录上下文；完成仅传局次 ID，不要求请求体或上传绘画内容。 */
class DrawingControllerTest {
    @Test
    void completesOwnedSessionWithoutRequestBodyOrDrawingUpload() throws Exception {
        var service = mock(DrawingService.class);
        when(service.config(123L, "pet-a")).thenReturn(new DrawingConfigVO(7, 0, 2, 1, true));
        when(service.start(eq(123L), any())).thenReturn(new DrawingStartVO("session-a", "reference-a"));
        when(service.complete(123L, "session-a"))
                .thenReturn(new DrawingCompleteVO("session-a", 7, "NEW_REWARD", 22, new Date(0)));
        var mvc = MockMvcBuilders.standaloneSetup(new DrawingController(service)).build();
        try (var security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(123L);
            mvc.perform(get("/app/drawing/config").param("petId", "pet-a"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingPlays").value(1));
            mvc.perform(post("/app/drawing/start").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"petId\":\"pet-a\",\"requestId\":\"request-a\",\"userId\":999}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.sessionId").value("session-a"))
                    .andExpect(jsonPath("$.data.earnStars").doesNotExist())
                    .andExpect(jsonPath("$.data.consumeStars").doesNotExist());
            mvc.perform(post("/app/drawing/session-a/complete"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.awardedStars").value(7));
            mvc.perform(post("/app/drawing/start").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"petId\":\"pet-a\",\"requestId\":\"x\",\"expectedConsumeStars\":-1}"))
                    .andExpect(status().isBadRequest());
            verify(service).config(123L, "pet-a");
            verify(service, times(1)).start(eq(123L), any());
            verify(service).complete(123L, "session-a");
        }
        assertThat(DrawingController.class.getAnnotation(org.apache.shiro.authz.annotation.RequiresPermissions.class).value())
                .containsExactly("sys:role:normal");
    }
}
