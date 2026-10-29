/** 지금 예매 중인 회차에 k6 가상 관객을 함께 보내는 방법을 안내한다. */
export function LoadHint({ eventId }: { eventId: number }) {
  return (
    <details className="load-hint">
      <summary>다른 관객과 경쟁해 보기</summary>
      <p>아래 명령을 실행하면 가상 관객이 이 회차(#{eventId})에 함께 몰려들어 좌석이 실시간으로 팔려 나갑니다.</p>
      <pre>
        <code>{`k6 run -e EVENT_ID=${eventId} -e VUS=100 load-generator/queue.js`}</code>
      </pre>
    </details>
  )
}
