import { useCallback, useEffect, useRef, useState } from 'react'
import { api, type Concert, type EntryStatus, type Event } from '../../shared/api/client'
import { LoadHint } from '../../shared/ui/LoadHint'
import { useStompSubscription } from '../../shared/websocket/useStompSubscription'

interface Props {
  concert: Concert
  event: Event
  onGranted: (token: string) => void
}

// 순번이 바뀌는 모습을 바로 보여 주기 위해 1초마다 상태를 확인한다. 입장 허용은 WebSocket 알림으로도 즉시 확인한다.
const POLL_INTERVAL_MS = 1000

function formatWait(seconds: number) {
  if (seconds < 60) return `약 ${seconds}초`
  return `약 ${Math.floor(seconds / 60)}분 ${seconds % 60}초`
}

/** 입장 요청을 대기열에 넣고 대기 순번을 보여주다가, 입장이 허용되면 토큰을 받아 좌석 선택으로 넘어간다. */
export function WaitingRoomPage({ concert, event, onGranted }: Props) {
  const [requestId, setRequestId] = useState<string | null>(null)
  const [status, setStatus] = useState<EntryStatus | null>(null)
  const [error, setError] = useState<string | null>(null)
  // StrictMode의 이중 effect 실행이나 알림+폴링 중복으로 입장 요청/전환이 두 번 일어나지 않게 막는다.
  const requested = useRef(false)
  const granted = useRef(false)
  // 진행률 막대의 기준이 되는 처음 받은 순번
  const [initialPosition, setInitialPosition] = useState<number | null>(null)

  useEffect(() => {
    if (requested.current) return
    requested.current = true
    api
      .requestEntry(event.id)
      .then((response) => setRequestId(response.requestId))
      .catch((e: Error) => setError(e.message))
  }, [event.id])

  // 입장 처리가 최종 실패하면 새 요청으로 줄 맨 뒤에 다시 선다.
  const retry = () => {
    setStatus(null)
    setInitialPosition(null)
    setRequestId(null)
    api
      .requestEntry(event.id)
      .then((response) => setRequestId(response.requestId))
      .catch((e: Error) => setError(e.message))
  }

  const checkStatus = useCallback(async () => {
    if (!requestId || granted.current) return
    const next = await api.getEntryStatus(event.id, requestId)
    if (next.status === 'GRANTED' && next.token && !granted.current) {
      granted.current = true
      onGranted(next.token)
      return
    }
    setStatus(next)
    if (next.position !== null) setInitialPosition((prev) => prev ?? next.position)
  }, [event.id, requestId, onGranted])

  useStompSubscription(requestId ? `/topic/entries/${requestId}` : null, () => void checkStatus())

  const failed = status?.status === 'FAILED'

  useEffect(() => {
    if (!requestId || failed) return
    const timer = setInterval(() => void checkStatus(), POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [requestId, checkStatus, failed])

  const position = status?.position ?? null
  const progress = position !== null && initialPosition ? Math.round((1 - (position - 1) / initialPosition) * 100) : 0

  return (
    <section className="waiting">
      <div className="waiting-panel">
        <p className="waiting-concert">{concert.title}</p>
        <h1 className="waiting-title">예매 대기 중입니다</h1>
        {error ? (
          <p className="alert" role="alert">
            {error}
          </p>
        ) : failed ? (
          <>
            <p className="alert" role="alert">
              접속이 많아 입장 처리에 실패했습니다. 다시 줄을 서 주세요.
            </p>
            <button type="button" className="btn btn-primary btn-large" onClick={retry}>
              다시 줄 서기
            </button>
          </>
        ) : (
          <>
            <div className="waiting-time" aria-live="polite">
              <span>나의 대기순서</span>
              <strong>
                {/* 첫 상태를 받기 전에는 확인 중, 대기 중인데 순번이 없으면 줄에서 막 빠져 입장 처리 중이다 */}
                {status === null ? '확인 중' : position !== null ? `${position.toLocaleString('ko-KR')}번째` : '곧 입장합니다'}
              </strong>
            </div>
            <div className="progress waiting-progress" role="progressbar" aria-label="대기 진행률" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}>
              <span style={{ width: `${progress}%` }} />
            </div>
            <dl className="waiting-stats">
              <div>
                <dt>내 뒤 대기 인원</dt>
                <dd>{status?.behind != null ? `${status.behind.toLocaleString('ko-KR')}명` : '-'}</dd>
              </div>
              <div>
                <dt>예상 대기 시간</dt>
                <dd>{status?.estimatedSeconds != null ? formatWait(status.estimatedSeconds) : '-'}</dd>
              </div>
            </dl>
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
