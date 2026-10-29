import { useState } from 'react'
import { api, type Event, type Seat } from '../../shared/api/client'
import { SeatSummary } from '../../shared/ui/SeatSummary'
import { useLiveSeats } from '../../shared/websocket/useLiveSeats'

interface Props {
  event: Event
  token: string
  onHeld: (seat: Seat) => void
}

const STATUS_LABEL: Record<Seat['status'], string> = {
  AVAILABLE: '선택 가능',
  HELD: '결제 대기',
  CONFIRMED: '판매 완료',
}

/** 실시간 좌석 현황을 보여주고, 선택 가능한 좌석을 클릭하면 선점을 시도한다. */
export function SeatSelectionPage({ event, token, onHeld }: Props) {
  const { seats, error } = useLiveSeats(event.id)
  const [pendingSeatId, setPendingSeatId] = useState<number | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const hold = async (seat: Seat) => {
    setPendingSeatId(seat.id)
    setMessage(null)
    try {
      onHeld(await api.holdSeat(seat.id, token))
    } catch (e) {
      setMessage((e as Error).message)
    } finally {
      setPendingSeatId(null)
    }
  }

  return (
    <section className="page page-seats">
      <p className="event-name">{event.name}</p>
      <h1 className="title">좌석을 고르세요</h1>
      <SeatSummary seats={seats} />
      {(error ?? message) && (
        <p className="error" role="alert">
          {error ?? message}
        </p>
      )}
      <div className="hall">
        <div className="stage-bar">무대</div>
        <div className="seat-grid">
          {seats.map((seat) => (
            <button
              key={seat.id}
              type="button"
              className={`seat seat-${seat.status.toLowerCase()}`}
              disabled={seat.status !== 'AVAILABLE' || pendingSeatId !== null}
              aria-label={`${seat.seatNumber}번 좌석, ${STATUS_LABEL[seat.status]}`}
              onClick={() => void hold(seat)}
            >
              {seat.seatNumber}
            </button>
          ))}
        </div>
        <ul className="legend">
          {Object.entries(STATUS_LABEL).map(([status, label]) => (
            <li key={status}>
              <span className={`swatch seat-${status.toLowerCase()}`} />
              {label}
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}
