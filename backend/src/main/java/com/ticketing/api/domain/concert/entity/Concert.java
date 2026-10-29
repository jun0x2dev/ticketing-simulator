package com.ticketing.api.domain.concert.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 고를 수 있는 공연 템플릿. 좌석 수와 가격은 서버가 정하며,
 * 사용자가 공연을 고를 때마다 이 템플릿으로 새 회차(Event)를 만든다.
 */
@Entity
@Table(name = "concerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Concert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 100)
    private String performer;

    @Column(nullable = false, length = 30)
    private String genre;

    @Column(nullable = false, length = 100)
    private String venue;

    @Column(nullable = false)
    private int seatCount;

    @Column(nullable = false)
    private int price;

    private Concert(String title, String performer, String genre, String venue, int seatCount, int price) {
        this.title = title;
        this.performer = performer;
        this.genre = genre;
        this.venue = venue;
        this.seatCount = seatCount;
        this.price = price;
    }

    /**
     * 공연 템플릿을 생성하는 정적 팩토리 메서드.
     *
     * @param title     공연명
     * @param performer 출연
     * @param genre     장르
     * @param venue     공연장
     * @param seatCount 회차당 좌석 수
     * @param price     좌석 1석 가격(원)
     * @return 생성된 Concert
     */
    public static Concert create(String title, String performer, String genre, String venue, int seatCount, int price) {
        return new Concert(title, performer, genre, venue, seatCount, price);
    }
}
