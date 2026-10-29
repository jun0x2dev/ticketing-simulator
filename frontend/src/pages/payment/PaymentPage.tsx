import { useEffect, useState } from 'react'
import { api, type Concert, type Event, type Payment, type Seat } from '../../shared/api/client'
import { BookingSteps } from '../../shared/ui/BookingSteps'
import { formatPrice } from '../../shared/ui/format'

// 서버의 선점 유지 시간(Seat.HOLD_TTL)과 같은 값. 화면 타이머는 선점 응답을 받은 시점부터 센다.
const HOLD_SECONDS = 5 * 60
const PAYMENT_METHODS = ['신용카드', '간편결제', '무통장입금']

interface Props {
  concert: Concert
  event: Event
  seat: Seat
  onPaid: (payment: Payment) => void
  onBack: () => void
}

/** 예매 정보를 확인하고 결제 제한시간 안에 모의 결제를 진행한다. */
export function PaymentPage({ concert, event, seat, onPaid, onBack }: Props) {
  const [remaining, setRemaining] = useState(HOLD_SECONDS)
  const [method, setMethod] = useState(PAYMENT_METHODS[0])
  const [agreed, setAgreed] = useState(false)
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
      setPaying(false)
    }
  }

  const expired = remaining === 0
  const minutes = String(Math.floor(remaining / 60)).padStart(2, '0')
  const seconds = String(remaining % 60).padStart(2, '0')

  return (
    <section className="booking">
      <BookingSteps current={1} />
      <div className={`timer-bar${remaining <= 60 ? ' is-urgent' : ''}`} role="timer">
        {expired ? (
          '결제 시간이 지나 좌석이 곧 해제됩니다. 좌석을 다시 선택해 주세요.'
        ) : (
          <>
            남은 결제 시간 <strong>{minutes}:{seconds}</strong> 이 안에 결제하지 않으면 좌석이 자동으로 취소됩니다.
          </>
        )}
      </div>
      <div className="booking-layout">
        <div className="payment-main">
          <h2 className="section-title">예매 정보</h2>
          <dl className="info-table">
            <div>
              <dt>공연명</dt>
              <dd>{concert.title}</dd>
            </div>
            <div>
              <dt>장소</dt>
              <dd>{concert.venue}</dd>
            </div>
            <div>
              <dt>회차</dt>
              <dd>#{event.id}</dd>
            </div>
            <div>
              <dt>좌석</dt>
              <dd>{seat.seatNumber}번</dd>
            </div>
          </dl>

          <h2 className="section-title">결제 수단</h2>
          <div className="method-list" role="radiogroup" aria-label="결제 수단">
            {PAYMENT_METHODS.map((name) => (
              <label key={name} className={method === name ? 'is-checked' : undefined}>
                <input type="radio" name="method" value={name} checked={method === name} onChange={() => setMethod(name)} />
                {name}
              </label>
            ))}
          </div>
          <p className="detail-note">모의 결제입니다. 실제로 결제되지 않으며, 일정 확률로 승인이 거절됩니다.</p>

          <label className="agree">
            <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} />
            예매 및 취소 규정을 확인했으며 이에 동의합니다.
          </label>
        </div>

        <aside className="booking-panel">
          <h2 className="section-title">결제 금액</h2>
          <dl className="price-table">
            <div>
              <dt>티켓 금액</dt>
              <dd>{formatPrice(concert.price)}</dd>
            </div>
            <div className="price-total">
              <dt>총 결제 금액</dt>
              <dd>{formatPrice(concert.price)}</dd>
            </div>
          </dl>
          {error && (
            <p className="alert" role="alert">
              {error}
            </p>
          )}
          <button type="button" className="btn btn-primary btn-large btn-block" onClick={() => void pay()} disabled={!agreed || paying || expired}>
            {paying ? '결제 진행 중…' : `${formatPrice(concert.price)} 결제하기`}
          </button>
          <button type="button" className="btn btn-ghost btn-block" onClick={onBack} disabled={paying}>
            좌석 다시 선택
          </button>
        </aside>
      </div>
    </section>
  )
}
