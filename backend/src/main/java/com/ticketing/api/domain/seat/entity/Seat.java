package com.ticketing.api.domain.seat.entity;

import com.ticketing.api.domain.event.entity.Event;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 특정 이벤트(공연)에 속한 좌석 1석을 나타내는 엔티티.
 * 좌석 선점의 동시성 제어 자체는 Redis 분산 락이 담당하고, 이 엔티티는
 * 락을 통과한 이후의 "현재 상태"만 영속화한다.
 */
@Entity
@Table(name = "seats", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "seat_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    // 결제 제한시간과 동일한 선점 유지 시간. Redis 락 TTL과 DB HELD 만료 기준이 모두 이 값을 따른다.
    public static final Duration HOLD_TTL = Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    // 선점 시각. TTL 만료 스케줄러가 HELD 좌석의 만료 여부를 판단하는 기준이다.
    private LocalDateTime heldAt;

    // 선점한 사용자의 입장 토큰. 결제/해제 요청이 선점한 본인의 것인지 확인하는 기준이다.
    @Column(name = "held_by", length = 36)
    private String heldBy;

    private Seat(Event event, int seatNumber) {
        this.event = event;
        this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE;
    }

    /**
     * 선택 가능 상태의 좌석을 생성하는 정적 팩토리 메서드.
     *
     * @param event      소속 이벤트
     * @param seatNumber 좌석 번호 (이벤트 내에서 유일)
     * @return 생성된 Seat (상태: AVAILABLE)
     */
    public static Seat create(Event event, int seatNumber) {
        return new Seat(event, seatNumber);
    }

    /**
     * 좌석을 선점 상태로 전환한다. Redis 락을 먼저 획득한 뒤 호출되는 것을 전제로 하며,
     * 엔티티 레벨에서도 상태를 검증해 이중 방어선을 둔다.
     *
     * @param holderToken 선점하는 사용자의 입장 토큰
     */
    public void hold(String holderToken) {
        if (this.status != SeatStatus.AVAILABLE) {
            throw new CustomException(ErrorCode.SEAT_ALREADY_HELD);
        }
        this.status = SeatStatus.HELD;
        this.heldAt = LocalDateTime.now();
        this.heldBy = holderToken;
    }

    /**
     * 주어진 입장 토큰으로 선점 중인 좌석인지 확인한다. 결제/해제 전에 호출해서 남의 좌석을 건드리지 못하게 한다.
     *
     * @param holderToken 요청한 사용자의 입장 토큰
     */
    public void verifyHolder(String holderToken) {
        if (this.status != SeatStatus.HELD) {
            throw new CustomException(ErrorCode.SEAT_NOT_HELD);
        }
        if (holderToken == null || !holderToken.equals(this.heldBy)) {
            throw new CustomException(ErrorCode.SEAT_HOLDER_MISMATCH);
        }
        // TTL 스케줄러는 몇 초 간격으로만 돌기 때문에, 제한시간이 지난 선점은 스케줄러가 풀기 전이라도 여기서 거절한다.
        if (this.heldAt.plus(HOLD_TTL).isBefore(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.SEAT_HOLD_EXPIRED);
        }
    }

    /**
     * 결제 완료로 좌석을 확정한다. HELD 상태에서만 가능하다.
     */
    public void confirm() {
        if (this.status != SeatStatus.HELD) {
            throw new CustomException(ErrorCode.SEAT_NOT_HELD);
        }
        this.status = SeatStatus.CONFIRMED;
        this.heldAt = null;
        this.heldBy = null;
    }

    /**
     * 결제 실패/TTL 만료 등으로 선점을 해제하고 다시 선택 가능 상태로 되돌린다.
     */
    public void release() {
        if (this.status != SeatStatus.HELD) {
            throw new CustomException(ErrorCode.SEAT_NOT_HELD);
        }
        this.status = SeatStatus.AVAILABLE;
        this.heldAt = null;
        this.heldBy = null;
    }
}
