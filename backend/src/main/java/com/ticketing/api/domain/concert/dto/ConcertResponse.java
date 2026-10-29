package com.ticketing.api.domain.concert.dto;

import com.ticketing.api.domain.concert.entity.Concert;

/**
 * 공연 목록 응답 DTO.
 */
public record ConcertResponse(
        Long id,
        String title,
        String performer,
        String genre,
        String venue,
        int seatCount,
        int price
) {

    /**
     * Concert 엔티티를 ConcertResponse로 변환한다.
     *
     * @param concert 변환할 엔티티
     * @return 변환된 응답 DTO
     */
    public static ConcertResponse from(Concert concert) {
        return new ConcertResponse(concert.getId(), concert.getTitle(), concert.getPerformer(), concert.getGenre(),
                concert.getVenue(), concert.getSeatCount(), concert.getPrice());
    }
}
