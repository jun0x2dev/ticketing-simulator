import { useCallback, useEffect, useRef, useState } from 'react'
import { api, type Event } from '../../shared/api/client'
import { useStompSubscription } from '../../shared/websocket/useStompSubscription'

interface Props {
  event: Event
  onGranted: (token: string) => void
}

/** 입장 요청을 대기열에 넣고, 입장이 허용되면 토큰을 받아 다음 단계로 넘긴다. */
export function WaitingRoomPage({ event, onGranted }: Props) {
  const [requestId, setRequestId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [elapsed, setElapsed] = useState(0)
  // StrictMode의 이중 effect 실행이나 알림+폴링 중복으로 입장 요청/전환이 두 번 일어나지 않게 막는다.
  const requested = useRef(false)
  const granted = useRef(false)

  useEffect(() => {
    if (requested.current) return
    requested.current = true
    api
      .requestEntry(event.id)
      .then((response) => setRequestId(response.requestId))
      .catch((e: Error) => setError(e.message))
  }, [event.id])

  const checkStatus = useCallback(async () => {
    if (!requestId || granted.current) return
    const status = await api.getEntryStatus(requestId)
    if (status.status === 'GRANTED' && status.token && !granted.current) {
      granted.current = true
      onGranted(status.token)
    }
  }, [requestId, onGranted])

  useStompSubscription(requestId ? `/topic/entries/${requestId}` : null, () => void checkStatus())

  // 구독이 붙기 전에 이미 허용된 경우를 놓치지 않도록 주기적으로도 확인한다.
  useEffect(() => {
    if (!requestId) return
    void checkStatus()
    const timer = setInterval(() => void checkStatus(), 2000)
    return () => clearInterval(timer)
  }, [requestId, checkStatus])

  useEffect(() => {
    const timer = setInterval(() => setElapsed((s) => s + 1), 1000)
    return () => clearInterval(timer)
  }, [])

  return (
    <section className="card center">
      <h2>{event.name} 대기열</h2>
      {error ? (
        <p className="error">{error}</p>
      ) : (
        <>
          <div className="spinner" aria-hidden="true" />
          <p className="lead">입장 순서를 기다리는 중입니다</p>
          <p className="muted">대기 시간 {elapsed}초 · 차례가 되면 자동으로 좌석 선택 화면으로 이동합니다.</p>
        </>
      )}
    </section>
  )
}
