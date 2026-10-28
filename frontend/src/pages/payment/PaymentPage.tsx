import { useEffect, useState } from 'react'
import { api, type Payment, type Seat } from '../../shared/api/client'

// 서버의 선점 유지 시간(Seat.HOLD_TTL)과 같은 값. 화면 타이머는 선점 응답을 받은 시점부터 센다.
const HOLD_SECONDS = 5 * 60

interface Props {
  seat: Seat
  onPaid: (payment: Payment) => void
  onBack: () => void
}

/** 결제 제한시간 타이머를 보여주고 Mock 결제를 진행한다. */
export function PaymentPage({ seat, onPaid, onBack }: Props) {
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
    <section className="card center">
      <h2>{seat.seatNumber}번 좌석 결제</h2>
      <p className={`timer ${remaining <= 60 ? 'timer-urgent' : ''}`}>
        {minutes}:{seconds}
      </p>
      <p className="muted">
        {expired ? '결제 제한시간이 지나 좌석이 곧 해제됩니다.' : '제한시간 안에 결제하지 않으면 좌석이 자동으로 해제됩니다.'}
      </p>
      {error && <p className="error">{error}</p>}
      <div className="actions">
        <button type="button" onClick={() => void pay()} disabled={paying || expired}>
          {paying ? '결제 처리 중…' : '결제하기'}
        </button>
        <button type="button" className="secondary" onClick={onBack} disabled={paying}>
          좌석 다시 고르기
        </button>
      </div>
    </section>
  )
}
