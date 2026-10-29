import type { Seat } from '../api/client'

/** 좌석 상태별 개수와 판매 진행률을 보여준다. */
export function SeatSummary({ seats }: { seats: Seat[] }) {
  const count = (status: Seat['status']) => seats.filter((seat) => seat.status === status).length
  const confirmed = count('CONFIRMED')
  const ratio = seats.length === 0 ? 0 : Math.round((confirmed / seats.length) * 100)

  return (
    <div className="summary">
      <dl className="summary-stats">
        <div className="stat stat-available">
          <dt>선택 가능</dt>
          <dd>{count('AVAILABLE')}</dd>
        </div>
        <div className="stat stat-held">
          <dt>결제 대기</dt>
          <dd>{count('HELD')}</dd>
        </div>
        <div className="stat stat-confirmed">
          <dt>판매 완료</dt>
          <dd>{confirmed}</dd>
        </div>
      </dl>
      <div className="progress" role="progressbar" aria-label="판매율" aria-valuenow={ratio} aria-valuemin={0} aria-valuemax={100}>
        <span style={{ width: `${ratio}%` }} />
      </div>
      <p className="progress-label">{seats.length}석 중 {ratio}% 판매</p>
    </div>
  )
}
