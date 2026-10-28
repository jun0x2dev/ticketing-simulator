import { Client } from '@stomp/stompjs'
import { useEffect, useRef } from 'react'

// Vite 프록시를 거치도록 현재 origin 기준으로 WebSocket 주소를 만든다.
const brokerURL = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`

/**
 * 컴포넌트가 살아 있는 동안 STOMP 토픽 하나를 구독한다.
 * 연결이 끊기면 3초마다 재연결하고, 재연결될 때마다 다시 구독한다.
 */
export function useStompSubscription<T>(destination: string | null, onMessage: (message: T) => void) {
  // 핸들러가 바뀌어도 재연결하지 않도록 최신 핸들러만 ref로 들고 있는다.
  const handlerRef = useRef(onMessage)
  useEffect(() => {
    handlerRef.current = onMessage
  })

  useEffect(() => {
    if (!destination) return
    const client = new Client({
      brokerURL,
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe(destination, (frame) => handlerRef.current(JSON.parse(frame.body) as T))
      },
    })
    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [destination])
}
