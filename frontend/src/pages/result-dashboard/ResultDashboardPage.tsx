import type { Event, Payment } from '../../shared/api/client'
import { SeatSummary } from '../../shared/ui/SeatSummary'
import { useLiveSeats } from '../../shared/websocket/useLiveSeats'

interface Props {
  event: Event
  payment: Payment
  onRetry: () => void
  onRestart: () => void
}

/** 내 결제 결과와 이벤트 전체 좌석 판매 현황을 실시간으로 보여준다. */
export function ResultDashboardPage({ event, payment, onRetry, onRestart }: Props) {
  const { seats } = useLiveSeats(event.id)
  const success = payment.status === 'SUCCESS'

  return (
    <section className="card">
      <h2>{event.name} 결과</h2>
      <p className={`result ${success ? 'result-success' : 'result-failed'}`}>
        {success ? '결제가 완료되어 좌석이 확정되었습니다.' : '결제가 거절되어 좌석이 해제되었습니다.'}
      </p>
      <h3>전체 판매 현황</h3>
      <SeatSummary seats={seats} />
      <div className="actions">
        {!success && (
          <button type="button" onClick={onRetry}>
            다른 좌석 고르기
          </button>
        )}
        <button type="button" className="secondary" onClick={onRestart}>
          새 시뮬레이션
        </button>
      </div>
    </section>
  )
}
