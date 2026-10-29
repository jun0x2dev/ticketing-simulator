import { useState } from 'react'
import { api, type Concert, type Event, type Seat } from '../../shared/api/client'
import { BookingSteps } from '../../shared/ui/BookingSteps'
import { formatPrice } from '../../shared/ui/format'
import { LoadHint } from '../../shared/ui/LoadHint'
import { Poster } from '../../shared/ui/Poster'
import { SeatSummary } from '../../shared/ui/SeatSummary'
import { useLiveSeats } from '../../shared/websocket/useLiveSeats'

interface Props {
  concert: Concert
  event: Event
  token: string
  onHeld: (seat: Seat) => void
}

const STATUS_LABEL: Record<Seat['status'], string> = {
  AVAILABLE: '선택 가능',
  HELD: '결제 진행 중',
  CONFIRMED: '판매 완료',
}

/** 실시간 좌석 배치도에서 좌석을 고르고, 선택 완료 시 좌석을 선점한다. */
export function SeatSelectionPage({ concert, event, token, onHeld }: Props) {
  const { seats, error } = useLiveSeats(event.id)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [holding, setHolding] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  // 고른 좌석을 다른 관객이 먼저 가져가면 선택을 자동으로 풀고 알린다.
  const selected = seats.find((seat) => seat.id === selectedId)
  const lostSelection = selected !== undefined && selected.status !== 'AVAILABLE'
  const activeSeat = lostSelection ? undefined : selected

  const hold = async () => {
    if (!activeSeat) return
    setHolding(true)
    setMessage(null)
    try {
      onHeld(await api.holdSeat(activeSeat.id, token))
    } catch (e) {
      setMessage((e as Error).message)
      setSelectedId(null)
      setHolding(false)
    }
  }

  return (
    <section className="booking">
      <BookingSteps current={0} />
      <div className="booking-layout">
        <div className="seat-map">
          <div className="stage">STAGE</div>
          <div className="seat-grid">
            {seats.map((seat) => {
              const isSelected = seat.id === activeSeat?.id
              return (
                <button
                  key={seat.id}
                  type="button"
                  className={`seat seat-${seat.status.toLowerCase()}${isSelected ? ' is-selected' : ''}`}
                  disabled={seat.status !== 'AVAILABLE' || holding}
                  aria-pressed={isSelected}
                  aria-label={`${seat.seatNumber}번 좌석, ${isSelected ? '선택함' : STATUS_LABEL[seat.status]}`}
                  title={`${seat.seatNumber}번`}
                  onClick={() => {
                    setMessage(null)
                    setSelectedId(isSelected ? null : seat.id)
                  }}
                />
              )
            })}
          </div>
          <ul className="legend">
            <li>
              <span className="swatch seat-available" />
              선택 가능
            </li>
            <li>
              <span className="swatch is-selected" />
              선택한 좌석
            </li>
            <li>
              <span className="swatch seat-held" />
              결제 진행 중
            </li>
            <li>
              <span className="swatch seat-confirmed" />
              판매 완료
            </li>
          </ul>
        </div>

        <aside className="booking-panel">
          <div className="panel-concert">
            <Poster concert={concert} size="small" />
            <div>
              <p className="panel-title">{concert.title}</p>
              <p className="panel-sub">{concert.venue}</p>
              <p className="panel-sub">회차 #{event.id}</p>
            </div>
          </div>
          <SeatSummary seats={seats} />
          <div className="panel-selection">
            <p className="panel-label">선택한 좌석</p>
            {activeSeat ? (
              <p className="panel-seat">
                {activeSeat.seatNumber}번 <span>{formatPrice(concert.price)}</span>
              </p>
            ) : (
              <p className="panel-empty">배치도에서 좌석을 선택하세요.</p>
            )}
          </div>
          {(error ?? message ?? (lostSelection ? '선택한 좌석을 다른 관객이 먼저 예매했습니다. 다른 좌석을 골라 주세요.' : null)) && (
            <p className="alert" role="alert">
              {error ?? message ?? '선택한 좌석을 다른 관객이 먼저 예매했습니다. 다른 좌석을 골라 주세요.'}
            </p>
          )}
          <button type="button" className="btn btn-primary btn-large btn-block" disabled={!activeSeat || holding} onClick={() => void hold()}>
            {holding ? '좌석 확보 중…' : '좌석 선택 완료'}
          </button>
        </aside>
      </div>
      <LoadHint eventId={event.id} />
    </section>
  )
}
