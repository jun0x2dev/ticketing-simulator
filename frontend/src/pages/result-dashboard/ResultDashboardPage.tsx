import type { Event, Payment, Seat } from '../../shared/api/client'
import { SeatSummary } from '../../shared/ui/SeatSummary'
import { useLiveSeats } from '../../shared/websocket/useLiveSeats'

interface Props {
  event: Event
  seat: Seat
  payment: Payment
  onRetry: () => void
  onRestart: () => void
}

/** 내 결제 결과를 티켓으로 보여주고, 이벤트 전체 좌석 판매 현황을 실시간으로 보여준다. */
export function ResultDashboardPage({ event, seat, payment, onRetry, onRestart }: Props) {
  const { seats } = useLiveSeats(event.id)
  const success = payment.status === 'SUCCESS'

  return (
    <section className="page page-result">
      <h1 className="title">{success ? '예매가 완료됐어요' : '결제가 거절됐어요'}</h1>
      <article className={`ticket ${success ? '' : 'is-void'}`}>
        <div className="ticket-main">
          <p className="ticket-event">{event.name}</p>
          <p className="ticket-seat">
            <span>{seat.seatNumber}</span>번 좌석
          </p>
        </div>
        <div className="ticket-stub">
          <p className="ticket-stub-label">{success ? '입장권' : '무효'}</p>
          <p className="ticket-code">No. {payment.id}</p>
        </div>
        {!success && (
          <p className="void-stamp" aria-hidden="true">
            결제 거절
          </p>
        )}
      </article>
      <p className="lead">
        {success
          ? '좌석이 확정되었습니다. 아래에서 다른 관객들의 예매 현황을 실시간으로 볼 수 있어요.'
          : '좌석은 다른 관객에게 다시 풀렸습니다. 남은 좌석으로 다시 시도할 수 있어요.'}
      </p>
      <h2 className="section-title">전체 판매 현황</h2>
      <SeatSummary seats={seats} />
      <div className="actions">
        {!success && (
          <button type="button" onClick={onRetry}>
            남은 좌석 다시 고르기
          </button>
        )}
        <button type="button" className="secondary" onClick={onRestart}>
          새 공연으로 다시 하기
        </button>
      </div>
    </section>
  )
}
