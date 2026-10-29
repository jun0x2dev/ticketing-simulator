package com.ticketing.api.domain.queue.waiting;

/**
 * 대기열 안에서의 현재 위치.
 *
 * @param position 나의 대기 순번 (1부터 시작)
 * @param behind   내 뒤에서 기다리는 인원
 */
public record WaitingPosition(
        long position,
        long behind
) {
}
