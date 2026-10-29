// 백엔드 공통 응답({ success, data, error })을 풀어서 data만 돌려주는 fetch 래퍼.

export type SeatStatus = 'AVAILABLE' | 'HELD' | 'CONFIRMED'

export interface Concert {
  id: number
  title: string
  performer: string
  genre: string
  venue: string
  seatCount: number
  price: number
}

export interface Event {
  id: number
  name: string
  seatCount: number
  openAt: string
}

export interface Seat {
  id: number
  eventId: number
  seatNumber: number
  status: SeatStatus
}

export interface EntryStatus {
  requestId: string
  status: 'WAITING' | 'GRANTED' | 'FAILED'
  token: string | null
  // 대기 중일 때만 채워진다. 줄에서 막 빠져 입장 처리 중이면 null이다.
  position: number | null
  behind: number | null
  estimatedSeconds: number | null
}

export interface Payment {
  id: number
  seatId: number
  status: 'SUCCESS' | 'FAILED'
  createdAt: string
}

export interface EventStats {
  eventId: number
  granted: number
  confirmed: number
  paymentFailed: number
  ttlExpired: number
}

interface ApiResponse<T> {
  success: boolean
  data: T
  error: { code: string; message: string } | null
}

/** 서버가 내려준 에러 코드를 담은 예외. 화면에서 코드별로 분기할 때 쓴다. */
export class ApiError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.code = code
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  const body = (await response.json()) as ApiResponse<T>
  if (!body.success || body.error) {
    throw new ApiError(body.error?.code ?? 'UNKNOWN', body.error?.message ?? '요청에 실패했습니다.')
  }
  return body.data
}

function postJson<T>(path: string, payload?: unknown, headers?: Record<string, string>): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  })
}

export const api = {
  getConcerts: () => request<Concert[]>('/api/v1/concerts'),
  startRun: (concertId: number) => postJson<Event>(`/api/v1/concerts/${concertId}/runs`),
  requestEntry: (eventId: number) =>
    postJson<{ requestId: string }>(`/api/v1/events/${eventId}/entries`),
  getEntryStatus: (eventId: number, requestId: string) =>
    request<EntryStatus>(`/api/v1/events/${eventId}/entries/${requestId}`),
  getSeats: (eventId: number) => request<Seat[]>(`/api/v1/events/${eventId}/seats`),
  holdSeat: (seatId: number, token: string) =>
    postJson<Seat>(`/api/v1/seats/${seatId}/hold`, undefined, { 'X-Entry-Token': token }),
  releaseSeat: (seatId: number, token: string) =>
    postJson<null>(`/api/v1/seats/${seatId}/release`, undefined, { 'X-Entry-Token': token }),
  pay: (seatId: number, token: string) =>
    postJson<Payment>('/api/v1/payments', { seatId }, { 'X-Entry-Token': token }),
  getStats: (eventId: number) => request<EventStats>(`/api/v1/events/${eventId}/stats`),
}
