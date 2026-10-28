import { useEffect, useState } from 'react'
import { api, type Seat, type SeatStatus } from '../api/client'
import { useStompSubscription } from './useStompSubscription'

interface SeatStatusMessage {
  eventId: number
  seatId: number
  status: SeatStatus
}

/** 이벤트의 좌석 목록을 한 번 불러온 뒤, WebSocket 좌석 상태 변경을 실시간으로 반영한다. */
export function useLiveSeats(eventId: number) {
  const [seats, setSeats] = useState<Seat[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.getSeats(eventId).then(setSeats).catch((e: Error) => setError(e.message))
  }, [eventId])

  useStompSubscription<SeatStatusMessage>(`/topic/events/${eventId}/seats`, (message) =>
    setSeats((prev) => prev.map((seat) => (seat.id === message.seatId ? { ...seat, status: message.status } : seat))),
  )

  return { seats, error }
}
