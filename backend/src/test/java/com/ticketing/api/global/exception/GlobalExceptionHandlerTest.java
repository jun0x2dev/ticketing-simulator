package com.ticketing.api.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketing.api.domain.event.controller.EventController;
import com.ticketing.api.domain.event.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 잘못된 클라이언트 입력이 500이 아닌 공통 포맷의 400(INVALID_REQUEST)으로 응답되는지 검증한다.
 */
@WebMvcTest(EventController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    // Bean Validation을 통과하지 못하는 본문은 400이어야 한다
    @Test
    @DisplayName("검증에 실패한 요청 본문은 400 INVALID_REQUEST로 응답한다")
    void invalidBody_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"seatCount\":0,\"openAt\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // 경로 변수 타입이 맞지 않으면 400이어야 한다
    @Test
    @DisplayName("타입이 맞지 않는 경로 변수는 400 INVALID_REQUEST로 응답한다")
    void typeMismatch_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/events/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // JSON으로 읽을 수 없는 본문도 400이어야 한다
    @Test
    @DisplayName("읽을 수 없는 요청 본문은 400 INVALID_REQUEST로 응답한다")
    void unreadableBody_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
}
