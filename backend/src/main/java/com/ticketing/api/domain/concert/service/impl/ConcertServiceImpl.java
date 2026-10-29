package com.ticketing.api.domain.concert.service.impl;

import com.ticketing.api.domain.concert.dto.ConcertResponse;
import com.ticketing.api.domain.concert.entity.Concert;
import com.ticketing.api.domain.concert.repository.ConcertRepository;
import com.ticketing.api.domain.concert.service.ConcertService;
import com.ticketing.api.domain.event.dto.EventCreateRequest;
import com.ticketing.api.domain.event.dto.EventResponse;
import com.ticketing.api.domain.event.service.EventService;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ConcertService 구현체. 공연 템플릿은 고정 시드 데이터이고, 회차 생성은 EventService에 위임한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertServiceImpl implements ConcertService {

    private static final Logger log = LoggerFactory.getLogger(ConcertServiceImpl.class);

    private final ConcertRepository concertRepository;
    private final EventService eventService;

    // 실존 아티스트/공연장과 겹치지 않는 가상의 공연. 좌석 수는 Event 생성 상한(500석) 안에서 규모를 섞는다.
    private static List<Concert> defaultCatalog() {
        return List.of(
                Concert.create("NOVA 첫 단독 콘서트 〈ORBIT〉", "NOVA", "콘서트", "그랜드 아레나", 500, 154000),
                Concert.create("뮤지컬 〈등대지기〉", "김하람, 윤서진 외", "뮤지컬", "한빛아트센터 대극장", 300, 132000),
                Concert.create("강변 록 페스티벌 나이트", "더 라이트하우스 외 4팀", "페스티벌", "강변 야외무대", 450, 99000),
                Concert.create("발라드 리사이틀 〈겨울편지〉", "정우현", "콘서트", "시민 콘서트홀", 250, 110000),
                Concert.create("영화음악 오케스트라 콘서트", "한빛 필하모닉", "클래식", "한빛아트센터 대극장", 350, 88000),
                Concert.create("힙합 페스타 〈BLOCK PARTY〉", "크루 블록 외 6팀", "페스티벌", "올림 스퀘어", 400, 121000),
                Concert.create("클래식 갈라 〈사계〉", "서울 챔버 앙상블", "클래식", "시민 콘서트홀", 200, 66000),
                Concert.create("어쿠스틱 크리스마스", "소담 트리오", "콘서트", "소담 아트홀", 120, 55000),
                Concert.create("인디 루키 쇼케이스", "신인 밴드 5팀", "콘서트", "망원 라이브홀", 80, 33000),
                Concert.create("재즈 나이트 〈Blue Hour〉", "블루문 쿼텟", "클래식", "라운지 세븐", 50, 44000)
        );
    }

    @Override
    @Transactional
    public void initializeCatalog() {
        if (concertRepository.count() > 0) {
            return;
        }
        List<Concert> saved = concertRepository.saveAll(defaultCatalog());
        log.info("기본 공연 목록 등록 완료 - count: {}", saved.size());
    }

    @Override
    public List<ConcertResponse> getConcerts() {
        return concertRepository.findAllByOrderByIdAsc().stream()
                .map(ConcertResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public EventResponse startRun(Long concertId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONCERT_NOT_FOUND));
        // 회차는 고르는 즉시 오픈되는 시뮬레이션이므로 오픈 시각은 현재 시각이다.
        EventResponse run = eventService.createEvent(
                new EventCreateRequest(concert.getTitle(), concert.getSeatCount(), LocalDateTime.now()));
        log.info("공연 회차 생성 - concertId: {}, eventId: {}", concertId, run.id());
        return run;
    }
}
