package com.ticketing.api.domain.concert.seed;

import com.ticketing.api.domain.concert.service.ConcertService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 시작 시 기본 공연 목록을 등록한다. 이미 등록되어 있으면 건너뛴다.
 */
@Component
@RequiredArgsConstructor
public class ConcertCatalogInitializer implements SmartInitializingSingleton {

    private final ConcertService concertService;

    /**
     * 모든 싱글톤 빈이 준비된 직후, 웹 서버가 요청을 받기 시작하기 전에 시드를 넣는다.
     * ApplicationRunner는 웹 서버 기동 이후에 실행되어, 그 사이에 들어온 공연 목록 조회가 빈 목록을 받았다.
     */
    @Override
    public void afterSingletonsInstantiated() {
        concertService.initializeCatalog();
    }
}
