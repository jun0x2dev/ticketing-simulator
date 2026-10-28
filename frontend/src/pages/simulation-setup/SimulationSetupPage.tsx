import { useState, type FormEvent } from 'react'
import { api, type Event } from '../../shared/api/client'

// datetime-local 입력값 형식(YYYY-MM-DDTHH:mm)으로 지금부터 minutes분 뒤 시각을 만든다.
function localDateTimeAfter(minutes: number) {
  const date = new Date(Date.now() + minutes * 60_000)
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset())
  return date.toISOString().slice(0, 16)
}

/** 이벤트명, 좌석 수, 오픈 시각을 입력해서 시뮬레이션할 이벤트를 만든다. */
export function SimulationSetupPage({ onCreated }: { onCreated: (event: Event) => void }) {
  const [name, setName] = useState('콘서트 A')
  const [seatCount, setSeatCount] = useState(100)
  const [openAt, setOpenAt] = useState(() => localDateTimeAfter(5))
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      onCreated(await api.createEvent(name, seatCount, `${openAt}:00`))
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="card">
      <h2>시뮬레이션 설정</h2>
      <form className="form" onSubmit={submit}>
        <label>
          이벤트명
          <input value={name} onChange={(e) => setName(e.target.value)} required />
        </label>
        <label>
          좌석 수
          <input type="number" min={1} max={1000} value={seatCount} onChange={(e) => setSeatCount(Number(e.target.value))} required />
        </label>
        <label>
          오픈 시각
          <input type="datetime-local" value={openAt} onChange={(e) => setOpenAt(e.target.value)} required />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? '생성 중…' : '시뮬레이션 시작'}
        </button>
      </form>
      <p className="hint">
        가상 유저 부하는 <code>load-generator</code>의 k6 스크립트로 함께 발생시킬 수 있습니다.
      </p>
    </section>
  )
}
