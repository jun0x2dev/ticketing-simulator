import { useCallback, useState } from 'react'
import type { Concert, Event, Payment, Seat } from './shared/api/client'
import { ConcertListPage } from './pages/concert-list/ConcertListPage'
import { PaymentPage } from './pages/payment/PaymentPage'
import { ResultDashboardPage } from './pages/result-dashboard/ResultDashboardPage'
import { SeatSelectionPage } from './pages/seat-selection/SeatSelectionPage'
import { WaitingRoomPage } from './pages/waiting-room/WaitingRoomPage'

// 공연 목록 → 대기 → 좌석 선택 → 결제 → 결과 순서로만 이동하므로 라우터 대신 단계 상태 하나로 관리한다.
type Booking = { concert: Concert; event: Event }
type Step =
  | { name: 'list' }
  | ({ name: 'waiting' } & Booking)
  | ({ name: 'seats'; token: string } & Booking)
  | ({ name: 'payment'; token: string; seat: Seat } & Booking)
  | ({ name: 'result'; token: string; seat: Seat; payment: Payment } & Booking)

function App() {
  const [step, setStep] = useState<Step>({ name: 'list' })
  const goHome = () => setStep({ name: 'list' })

  const handleGranted = useCallback(
    (token: string) =>
      setStep((prev) => (prev.name === 'waiting' ? { name: 'seats', concert: prev.concert, event: prev.event, token } : prev)),
    [],
  )

  return (
    <>
      <header className="site-header">
        <div className="inner">
          <button type="button" className="logo" onClick={goHome}>
            티켓시뮬
          </button>
          <span className="header-note">대기열과 좌석 경쟁을 체험하는 모의 예매</span>
        </div>
      </header>
      <main className="inner site-main">
        {step.name === 'list' && <ConcertListPage onStart={(concert, event) => setStep({ name: 'waiting', concert, event })} />}
        {step.name === 'waiting' && <WaitingRoomPage concert={step.concert} event={step.event} onGranted={handleGranted} />}
        {step.name === 'seats' && (
          <SeatSelectionPage
            concert={step.concert}
            event={step.event}
            token={step.token}
            onHeld={(seat) => setStep({ ...step, name: 'payment', seat })}
          />
        )}
        {step.name === 'payment' && (
          <PaymentPage
            concert={step.concert}
            event={step.event}
            seat={step.seat}
            token={step.token}
            onPaid={(payment) => setStep({ ...step, name: 'result', payment })}
            onBack={() => setStep({ name: 'seats', concert: step.concert, event: step.event, token: step.token })}
          />
        )}
        {step.name === 'result' && (
          <ResultDashboardPage
            concert={step.concert}
            event={step.event}
            seat={step.seat}
            payment={step.payment}
            onRetry={() => setStep({ name: 'seats', concert: step.concert, event: step.event, token: step.token })}
            onHome={goHome}
          />
        )}
      </main>
      <footer className="site-footer">
        <div className="inner">실제 결제가 이루어지지 않는 티케팅 시뮬레이터입니다.</div>
      </footer>
    </>
  )
}

export default App
