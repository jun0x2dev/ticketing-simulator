import { useCallback, useEffect, useRef, useState } from 'react'
import { api, type Concert, type Event } from '../../shared/api/client'
import { LoadHint } from '../../shared/ui/LoadHint'
import { useStompSubscription } from '../../shared/websocket/useStompSubscription'

interface Props {
  concert: Concert
  event: Event
  onGranted: (token: string) => void
}

/** 입장 요청을 대기열에 넣고, 입장이 허용되면 토큰을 받아 좌석 선택으로 넘어간다. */
export function WaitingRoomPage({ concert, event, onGranted }: Props) {
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

  const minutes = String(Math.floor(elapsed / 60)).padStart(2, '0')
  const seconds = String(elapsed % 60).padStart(2, '0')

  return (
    <section className="waiting">
      <div className="waiting-panel">
        <p className="waiting-concert">{concert.title}</p>
        <h1 className="waiting-title">예매 대기 중입니다</h1>
        {error ? (
          <p className="alert" role="alert">
            {error}
          </p>
        ) : (
          <>
            <div className="waiting-time">
              <span>나의 대기 시간</span>
              <strong>
                {minutes}:{seconds}
              </strong>
            </div>
            <div className="indeterminate" aria-hidden="true">
              <span />
            </div>
            <ul className="notice-list">
              <li>순서가 되면 좌석 선택 화면으로 자동 이동합니다.</li>
              <li>새로고침하거나 창을 닫으면 대기 순서가 초기화됩니다.</li>
            </ul>
          </>
        )}
      </div>
      <LoadHint eventId={event.id} />
    </section>
  )
}
