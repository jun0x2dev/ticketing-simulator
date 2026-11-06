import { useEffect, useState } from 'react'
import { api, type Concert, type Event, type EventStats, type Payment, type Seat } from '../../shared/api/client'
import { BookingSteps } from '../../shared/ui/BookingSteps'
import { formatBookingNumber, formatPrice } from '../../shared/ui/format'
import { SeatSummary } from '../../shared/ui/SeatSummary'
import { useLiveSeats } from '../../shared/websocket/useLiveSeats'

interface Props {
  concert: Concert
  event: Event
  seat: Seat
  payment: Payment
  token: string
  onCancelled: (payment: Payment) => void
  onRetry: () => void
  onHome: () => void
}

const RESULT_TEXT = {
  SUCCESS: { icon: '✓', title: '예매가 완료되었습니다', sub: '예매 내역을 확인해 주세요.' },
  FAILED: {
    icon: '!',
    title: '결제에 실패했습니다',
    sub: '카드사 승인이 거절되어 좌석이 취소되었습니다. 남은 좌석으로 다시 예매할 수 있습니다.',
  },
  REFUNDED: {
    icon: '↺',
    title: '예매가 취소되었습니다',
    sub: '결제 금액이 환불되었고, 좌석은 다른 관객이 예매할 수 있게 다시 열렸습니다.',
  },
}

/** 예매 결과와 이 회차의 실시간 판매 현황을 보여준다. 예매가 완료되었으면 여기서 취소할 수 있다. */
export function ResultDashboardPage({ concert, event, seat, payment, token, onCancelled, onRetry, onHome }: Props) {
  const { seats } = useLiveSeats(event.id)
  const [stats, setStats] = useState<EventStats | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const success = payment.status === 'SUCCESS'
  const text = RESULT_TEXT[payment.status]

  const cancel = async () => {
    setCancelling(true)
    setError(null)
    try {
      onCancelled(await api.cancelPayment(payment.id, token))
    } catch (e) {
      setError((e as Error).message)
    }
    setCancelling(false)
    setConfirming(false)
  }

  // 통계는 Kafka Consumer가 비동기로 집계하므로 2초마다 다시 읽는다.
  useEffect(() => {
    const load = () => api.getStats(event.id).then(setStats).catch(() => undefined)
    void load()
    const timer = setInterval(() => void load(), 2000)
    return () => clearInterval(timer)
  }, [event.id])

  return (
    <section className="booking">
      <BookingSteps current={2} />
      <div className={`result-panel${success ? '' : ' is-failed'}`}>
        <span className="result-icon" aria-hidden="true">
          {text.icon}
        </span>
        <h1 className="result-title">{text.title}</h1>
        <p className="result-sub">{text.sub}</p>
        <dl className="info-table result-table">
          {payment.status !== 'FAILED' && (
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
            <dd>
              {success && formatPrice(concert.price)}
              {payment.status === 'FAILED' && '0원'}
              {payment.status === 'REFUNDED' && `${formatPrice(concert.price)} 환불`}
            </dd>
          </div>
        </dl>
        {error && (
          <p className="alert" role="alert">
            {error}
          </p>
        )}
        {success && confirming && (
          <div className="cancel-confirm">
            <p>예매를 취소하면 좌석이 바로 다른 관객에게 열립니다. 취소할까요?</p>
            <div className="button-row">
              <button type="button" className="btn btn-primary" onClick={() => void cancel()} disabled={cancelling}>
                {cancelling ? '취소하는 중…' : '예매 취소'}
              </button>
              <button type="button" className="btn btn-ghost" onClick={() => setConfirming(false)} disabled={cancelling}>
                돌아가기
              </button>
            </div>
          </div>
        )}
        <div className="button-row">
          {!success && (
            <button type="button" className="btn btn-primary btn-large" onClick={onRetry}>
              다른 좌석 예매하기
            </button>
          )}
          <button type="button" className={`btn btn-large ${success ? 'btn-primary' : 'btn-ghost'}`} onClick={onHome}>
            다른 공연 보기
          </button>
          {success && !confirming && (
            <button type="button" className="btn btn-ghost btn-large" onClick={() => setConfirming(true)}>
              예매 취소
            </button>
          )}
        </div>
      </div>
      <div className="live-status">
        <h2 className="section-title">이 회차 실시간 판매 현황</h2>
        <SeatSummary seats={seats} />
        <dl className="stats-grid" aria-label="이 회차 예매 통계">
          <div>
            <dt>입장 허용</dt>
            <dd>{stats?.granted ?? '-'}</dd>
          </div>
          <div>
            <dt>결제 완료</dt>
            <dd>{stats?.confirmed ?? '-'}</dd>
          </div>
          <div>
            <dt>결제 실패</dt>
            <dd>{stats?.paymentFailed ?? '-'}</dd>
          </div>
          <div>
            <dt>시간 초과 해제</dt>
            <dd>{stats?.ttlExpired ?? '-'}</dd>
          </div>
          <div>
            <dt>예매 취소</dt>
            <dd>{stats?.cancelled ?? '-'}</dd>
          </div>
        </dl>
      </div>
    </section>
  )
}
