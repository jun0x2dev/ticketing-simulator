import { useEffect, useState } from 'react'
import { api, type Event, type Payment, type Seat } from '../../shared/api/client'

// 서버의 선점 유지 시간(Seat.HOLD_TTL)과 같은 값. 화면 타이머는 선점 응답을 받은 시점부터 센다.
const HOLD_SECONDS = 5 * 60

interface Props {
  event: Event
  seat: Seat
  onPaid: (payment: Payment) => void
  onBack: () => void
}

/** 결제 제한시간 타이머를 보여주고 Mock 결제를 진행한다. */
export function PaymentPage({ event, seat, onPaid, onBack }: Props) {
  const [remaining, setRemaining] = useState(HOLD_SECONDS)
  const [paying, setPaying] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const timer = setInterval(() => setRemaining((s) => Math.max(0, s - 1)), 1000)
    return () => clearInterval(timer)
  }, [])

  const pay = async () => {
    setPaying(true)
    setError(null)
    try {
      onPaid(await api.pay(seat.id))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setPaying(false)
    }
  }

  const expired = remaining === 0
  const minutes = String(Math.floor(remaining / 60)).padStart(2, '0')
  const seconds = String(remaining % 60).padStart(2, '0')

  return (
    <section className="page page-payment">
      <h1 className="title">좌석을 잡았어요. 결제를 마쳐 주세요</h1>
      <article className="ticket">
        <div className="ticket-main">
          <p className="ticket-event">{event.name}</p>
          <p className="ticket-seat">
            <span>{seat.seatNumber}</span>번 좌석
          </p>
        </div>
        <div className={`ticket-stub ${remaining <= 60 ? 'is-urgent' : ''}`}>
          <p className="ticket-stub-label">남은 결제 시간</p>
          <p className="timer" role="timer">
            {minutes}:{seconds}
          </p>
        </div>
      </article>
      <p className="lead">
        {expired
          ? '결제 시간이 지났습니다. 좌석이 곧 다른 관객에게 풀립니다.'
          : '시간 안에 결제하지 않으면 좌석이 자동으로 풀립니다.'}
      </p>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="actions">
        <button type="button" onClick={() => void pay()} disabled={paying || expired}>
          {paying ? '결제하는 중…' : '결제하기'}
        </button>
        <button type="button" className="secondary" onClick={onBack} disabled={paying}>
          다른 좌석 고르기
        </button>
      </div>
    </section>
  )
}
