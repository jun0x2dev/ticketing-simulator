import type { Concert, Event, Payment, Seat } from '../../shared/api/client'
import { BookingSteps } from '../../shared/ui/BookingSteps'
import { formatBookingNumber, formatPrice } from '../../shared/ui/format'
import { SeatSummary } from '../../shared/ui/SeatSummary'
import { useLiveSeats } from '../../shared/websocket/useLiveSeats'

interface Props {
  concert: Concert
  event: Event
  seat: Seat
  payment: Payment
  onRetry: () => void
  onHome: () => void
}

/** 예매 결과와 이 회차의 실시간 판매 현황을 보여준다. */
export function ResultDashboardPage({ concert, event, seat, payment, onRetry, onHome }: Props) {
  const { seats } = useLiveSeats(event.id)
  const success = payment.status === 'SUCCESS'

  return (
    <section className="booking">
      <BookingSteps current={2} />
      <div className={`result-panel${success ? '' : ' is-failed'}`}>
        <span className="result-icon" aria-hidden="true">
          {success ? '✓' : '!'}
        </span>
        <h1 className="result-title">{success ? '예매가 완료되었습니다' : '결제에 실패했습니다'}</h1>
        <p className="result-sub">
          {success
            ? '예매 내역을 확인해 주세요.'
            : '카드사 승인이 거절되어 좌석이 취소되었습니다. 남은 좌석으로 다시 예매할 수 있습니다.'}
        </p>
        <dl className="info-table result-table">
          {success && (
            <div>
              <dt>예매번호</dt>
              <dd className="booking-number">{formatBookingNumber(payment.id)}</dd>
            </div>
          )}
          <div>
            <dt>공연명</dt>
            <dd>{concert.title}</dd>
          </div>
          <div>
            <dt>장소</dt>
            <dd>{concert.venue}</dd>
          </div>
          <div>
            <dt>좌석</dt>
            <dd>
              회차 #{event.id} {seat.seatNumber}번
            </dd>
          </div>
          <div>
            <dt>결제 금액</dt>
            <dd>{success ? formatPrice(concert.price) : '0원'}</dd>
          </div>
        </dl>
        <div className="button-row">
          {!success && (
            <button type="button" className="btn btn-primary btn-large" onClick={onRetry}>
              다른 좌석 예매하기
            </button>
          )}
          <button type="button" className={`btn btn-large ${success ? 'btn-primary' : 'btn-ghost'}`} onClick={onHome}>
            다른 공연 보기
          </button>
        </div>
      </div>
      <div className="live-status">
        <h2 className="section-title">이 회차 실시간 판매 현황</h2>
        <SeatSummary seats={seats} />
      </div>
    </section>
  )
}
