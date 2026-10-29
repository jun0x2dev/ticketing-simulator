import { useCallback, useState } from 'react'
import type { Event, Payment, Seat } from './shared/api/client'
import { PaymentPage } from './pages/payment/PaymentPage'
import { ResultDashboardPage } from './pages/result-dashboard/ResultDashboardPage'
import { SeatSelectionPage } from './pages/seat-selection/SeatSelectionPage'
import { SimulationSetupPage } from './pages/simulation-setup/SimulationSetupPage'
import { WaitingRoomPage } from './pages/waiting-room/WaitingRoomPage'

// 설정 → 대기 → 좌석 선택 → 결제 → 결과 순서로만 이동하므로 라우터 대신 단계 상태 하나로 관리한다.
type Step =
  | { name: 'setup' }
  | { name: 'waiting'; event: Event }
  | { name: 'seats'; event: Event; token: string }
  | { name: 'payment'; event: Event; token: string; seat: Seat }
  | { name: 'result'; event: Event; token: string; seat: Seat; payment: Payment }

const STEPS = ['설정', '대기', '좌석 선택', '결제', '결과']
const STEP_INDEX: Record<Step['name'], number> = { setup: 0, waiting: 1, seats: 2, payment: 3, result: 4 }

function App() {
  const [step, setStep] = useState<Step>({ name: 'setup' })
  const current = STEP_INDEX[step.name]

  const handleGranted = useCallback(
    (token: string) => setStep((prev) => (prev.name === 'waiting' ? { name: 'seats', event: prev.event, token } : prev)),
    [],
  )

  return (
    <div className="app">
      <header className="header">
        <p className="brand">티케팅 시뮬레이터</p>
        <ol className="steps" aria-label="진행 단계">
          {STEPS.map((label, index) => (
            <li
              key={label}
              className={index === current ? 'is-current' : index < current ? 'is-done' : undefined}
              aria-current={index === current ? 'step' : undefined}
            >
              <span className="step-number">{index + 1}</span>
              {label}
            </li>
          ))}
        </ol>
      </header>
      <main className="stage">
        {step.name === 'setup' && <SimulationSetupPage onCreated={(event) => setStep({ name: 'waiting', event })} />}
        {step.name === 'waiting' && <WaitingRoomPage event={step.event} onGranted={handleGranted} />}
        {step.name === 'seats' && (
          <SeatSelectionPage
            event={step.event}
            token={step.token}
            onHeld={(seat) => setStep({ name: 'payment', event: step.event, token: step.token, seat })}
          />
        )}
        {step.name === 'payment' && (
          <PaymentPage
            event={step.event}
            seat={step.seat}
            onPaid={(payment) => setStep({ name: 'result', event: step.event, token: step.token, seat: step.seat, payment })}
            onBack={() => setStep({ name: 'seats', event: step.event, token: step.token })}
          />
        )}
        {step.name === 'result' && (
          <ResultDashboardPage
            event={step.event}
            seat={step.seat}
            payment={step.payment}
            onRetry={() => setStep({ name: 'seats', event: step.event, token: step.token })}
            onRestart={() => setStep({ name: 'setup' })}
          />
        )}
      </main>
    </div>
  )
}

export default App
