package com.ticketing.api.domain.concert.repository;

import com.ticketing.api.domain.concert.entity.Concert;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Concert 템플릿 조회 레포지토리.
 */
public interface ConcertRepository extends JpaRepository<Concert, Long> {

    /**
     * 등록된 공연을 등록 순서대로 조회한다. 시드로 넣는 고정 목록(10개)이라 페이징하지 않는다.
     *
     * @return 공연 목록
     */
    List<Concert> findAllByOrderByIdAsc();
}
