import type { Concert } from '../api/client'

// 실제 포스터 이미지가 없으므로 장르별 색과 공연명으로 포스터를 그린다.
const GENRE_THEME: Record<string, string> = {
  콘서트: 'poster-concert',
  뮤지컬: 'poster-musical',
  클래식: 'poster-classic',
  페스티벌: 'poster-festival',
}

/** 공연 포스터. size가 small이면 예매 정보 패널용 썸네일로 쓴다. */
export function Poster({ concert, size = 'large' }: { concert: Concert; size?: 'large' | 'small' }) {
  return (
    <div className={`poster ${GENRE_THEME[concert.genre] ?? 'poster-concert'} poster-${size}`} aria-hidden="true">
      <span className="poster-performer">{concert.performer}</span>
      <span className="poster-title">{concert.title}</span>
      <span className="poster-venue">{concert.venue}</span>
    </div>
  )
}
