package com.ticketing.api.domain.queue.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketing.api.domain.queue.dto.EntryStatusResponse;
import com.ticketing.api.domain.queue.service.EntryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 대기열 우회 API(entries/direct)가 로컬/부하 측정 프로필에서만 열리고, 그 밖의 환경에서는 공통 포맷의 4xx로 막히는지 검증한다.
 */
class DirectEntryControllerProfileTest {

    /** 프로필이 없는(운영과 같은) 환경. */
    @Nested
    @WebMvcTest(controllers = {EntryController.class, DirectEntryController.class})
    class WithoutSimulationProfile {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private EntryService entryService;

        // 경로가 상태 조회(GET .../entries/{requestId})와 겹치므로 500이 아닌 405로 거절되어야 한다
        @Test
        @DisplayName("프로필이 없으면 직접 입장 API가 없어 405 METHOD_NOT_ALLOWED로 응답한다")
        void directEntry_rejected() throws Exception {
            mockMvc.perform(post("/api/v1/events/1/entries/direct"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
        }

        // 없는 경로는 500이 아닌 404로 응답해야 한다
        @Test
        @DisplayName("없는 경로는 404 ENTITY_NOT_FOUND로 응답한다")
        void unknownPath_returnsNotFound() throws Exception {
            mockMvc.perform(get("/api/v1/unknown"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("ENTITY_NOT_FOUND"));
        }
    }

    /** k6 기준선 측정용 bench 프로필. */
    @Nested
    @WebMvcTest(controllers = {EntryController.class, DirectEntryController.class})
    @ActiveProfiles("bench")
    class WithBenchProfile {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private EntryService entryService;

        // bench 프로필에서는 대기열 없이 바로 토큰을 받아야 한다
        @Test
        @DisplayName("bench 프로필이면 직접 입장 API가 토큰을 발급한다")
        void directEntry_grantsToken() throws Exception {
            when(entryService.grantEntryDirectly(1L)).thenReturn(EntryStatusResponse.granted("request-1", "token-1"));

            mockMvc.perform(post("/api/v1/events/1/entries/direct"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").value("token-1"));
        }
    }
}
