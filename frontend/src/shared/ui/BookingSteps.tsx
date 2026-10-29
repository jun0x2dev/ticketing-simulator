const STEPS = ['좌석 선택', '결제', '예매 완료']

/** 예매 단계(좌석 선택 → 결제 → 예매 완료) 중 현재 위치를 보여준다. */
export function BookingSteps({ current }: { current: 0 | 1 | 2 }) {
  return (
    <ol className="booking-steps" aria-label="예매 단계">
      {STEPS.map((label, index) => (
        <li key={label} className={index === current ? 'is-current' : index < current ? 'is-done' : undefined}
          aria-current={index === current ? 'step' : undefined}>
          <span className="booking-step-number">{index + 1}</span>
          {label}
        </li>
      ))}
    </ol>
  )
}
