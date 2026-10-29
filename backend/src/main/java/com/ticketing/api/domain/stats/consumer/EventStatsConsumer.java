package com.ticketing.api.domain.stats.consumer;

import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.queue.dto.EntryGrantedEvent;
import com.ticketing.api.domain.stats.service.EventStatsService;
import com.ticketing.api.global.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 입장 허용/결제 완료/좌석 해제 이벤트를 구독해 회차별 통계를 집계하는 Consumer.
 * 처리 실패 시 공통 에러 핸들러가 재시도 후 각 토픽의 DLT로 보내고, 중복 전달은 서비스가 멱등 키로 걸러낸다.
 * 결제 이벤트 DTO는 queue 패키지 밖에 있으므로, 이 리스너에서만 역직렬화 허용 패키지를 넓힌다.
 */
@Component
@RequiredArgsConstructor
public class EventStatsConsumer {

    private static final String GROUP_ID = "ticketing-stats";
    private static final String TRUSTED_PACKAGES =
            "spring.json.trusted.packages=com.ticketing.api.domain.queue.dto,com.ticketing.api.domain.payment.dto";

    private final EventStatsService eventStatsService;

    /**
     * 입장 허용 이벤트를 집계한다.
     *
     * @param event 입장 허용 이벤트
     */
    @KafkaListener(topics = KafkaTopicConfig.ENTRY_GRANTED, groupId = GROUP_ID, properties = TRUSTED_PACKAGES)
    public void onGranted(EntryGrantedEvent event) {
        eventStatsService.recordGranted(event);
    }

    /**
     * 결제 완료(좌석 확정) 이벤트를 집계한다.
     *
     * @param event 좌석 확정 이벤트
     */
    @KafkaListener(topics = KafkaTopicConfig.SEAT_CONFIRMED, groupId = GROUP_ID, properties = TRUSTED_PACKAGES)
    public void onConfirmed(SeatConfirmedEvent event) {
        eventStatsService.recordConfirmed(event);
    }

    /**
     * 좌석 해제(결제 실패/시간 초과) 이벤트를 집계한다.
     *
     * @param event 좌석 해제 이벤트
     */
    @KafkaListener(topics = KafkaTopicConfig.SEAT_RELEASED, groupId = GROUP_ID, properties = TRUSTED_PACKAGES)
    public void onReleased(SeatReleasedEvent event) {
        eventStatsService.recordReleased(event);
    }
}
