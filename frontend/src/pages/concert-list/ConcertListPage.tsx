import { useEffect, useRef, useState } from 'react'
import { api, type Concert, type Event } from '../../shared/api/client'
import { formatPrice } from '../../shared/ui/format'
import { Poster } from '../../shared/ui/Poster'

const GENRES = ['전체', '콘서트', '뮤지컬', '클래식', '페스티벌']

interface Props {
  onStart: (concert: Concert, event: Event) => void
}

/** 예매 가능한 공연을 포스터 목록으로 보여주고, 상세 창에서 예매를 시작한다. */
export function ConcertListPage({ onStart }: Props) {
  const [concerts, setConcerts] = useState<Concert[]>([])
  const [genre, setGenre] = useState('전체')
  const [selected, setSelected] = useState<Concert | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    api.getConcerts().then(setConcerts).catch((e: Error) => setError(e.message))
  }, [])

  // 상세 창은 네이티브 dialog로 띄워 ESC 닫기와 포커스 가두기를 브라우저에 맡긴다.
  useEffect(() => {
    if (selected) dialogRef.current?.showModal()
  }, [selected])

  const close = () => dialogRef.current?.close()

  const start = async (concert: Concert) => {
    setStarting(true)
    setError(null)
    try {
      onStart(concert, await api.startRun(concert.id))
    } catch (e) {
      setError((e as Error).message)
      setStarting(false)
    }
  }

  const visible = genre === '전체' ? concerts : concerts.filter((concert) => concert.genre === genre)

  return (
    <section className="concert-list">
      <h1 className="page-title">지금 예매 가능한 공연</h1>
      <div className="genre-tabs" role="tablist" aria-label="장르">
        {GENRES.map((name) => (
          <button
            key={name}
            type="button"
            role="tab"
            aria-selected={genre === name}
            className={genre === name ? 'is-active' : undefined}
            onClick={() => setGenre(name)}
          >
            {name}
          </button>
        ))}
      </div>
      {error && !selected && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}
      <ul className="concert-grid">
        {visible.map((concert) => (
          <li key={concert.id}>
            <button type="button" className="concert-card" onClick={() => setSelected(concert)}>
              <Poster concert={concert} />
              <span className="concert-card-title">{concert.title}</span>
              <span className="concert-card-venue">{concert.venue}</span>
              <span className="concert-card-price">{formatPrice(concert.price)}</span>
            </button>
          </li>
        ))}
      </ul>

      <dialog ref={dialogRef} className="concert-dialog" onClose={() => setSelected(null)} aria-labelledby="concert-dialog-title">
        {selected && (
          <div className="concert-detail">
            <Poster concert={selected} />
            <div className="concert-detail-body">
              <span className="genre-badge">{selected.genre}</span>
              <h2 id="concert-dialog-title">{selected.title}</h2>
              <dl className="info-table">
                <div>
                  <dt>장소</dt>
                  <dd>{selected.venue}</dd>
                </div>
                <div>
                  <dt>출연</dt>
                  <dd>{selected.performer}</dd>
                </div>
                <div>
                  <dt>좌석</dt>
                  <dd>전석 {selected.seatCount}석</dd>
                </div>
                <div>
                  <dt>가격</dt>
                  <dd>{formatPrice(selected.price)}</dd>
                </div>
              </dl>
              <p className="detail-note">예매하기를 누르면 이 공연의 새 회차가 열리고 대기열에 들어갑니다.</p>
              {error && (
                <p className="alert" role="alert">
                  {error}
                </p>
              )}
              <div className="button-row">
                <button type="button" className="btn btn-primary btn-large" disabled={starting} onClick={() => void start(selected)}>
                  {starting ? '대기열 연결 중…' : '예매하기'}
                </button>
                <button type="button" className="btn btn-ghost btn-large" onClick={close}>
                  닫기
                </button>
              </div>
            </div>
          </div>
        )}
      </dialog>
    </section>
  )
}
