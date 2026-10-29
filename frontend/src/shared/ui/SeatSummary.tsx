import type { Seat } from '../api/client'

/** 회차의 잔여석과 판매 진행률을 보여준다. */
export function SeatSummary({ seats }: { seats: Seat[] }) {
  const count = (status: Seat['status']) => seats.filter((seat) => seat.status === status).length
  const sold = count('CONFIRMED')
  const ratio = seats.length === 0 ? 0 : Math.round((sold / seats.length) * 100)

  return (
    <div className="seat-summary">
      <dl>
        <div>
          <dt>잔여석</dt>
          <dd>
            {count('AVAILABLE')}
            <small> / {seats.length}석</small>
          </dd>
        </div>
        <div>
          <dt>결제 진행 중</dt>
          <dd>{count('HELD')}</dd>
        </div>
        <div>
          <dt>판매 완료</dt>
          <dd>{sold}</dd>
        </div>
      </dl>
      <div className="progress" role="progressbar" aria-label="판매율" aria-valuenow={ratio} aria-valuemin={0} aria-valuemax={100}>
        <span style={{ width: `${ratio}%` }} />
      </div>
    </div>
  )
}
