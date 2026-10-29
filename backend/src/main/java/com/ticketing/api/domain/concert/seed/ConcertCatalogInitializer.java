package com.ticketing.api.domain.concert.seed;

import com.ticketing.api.domain.concert.service.ConcertService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 시작 시 기본 공연 목록을 등록한다. 이미 등록되어 있으면 건너뛴다.
 */
@Component
@RequiredArgsConstructor
public class ConcertCatalogInitializer implements ApplicationRunner {

    private final ConcertService concertService;

    @Override
    public void run(ApplicationArguments args) {
        concertService.initializeCatalog();
    }
}
