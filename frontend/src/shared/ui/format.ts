/** 금액을 "154,000원" 형식으로 보여준다. */
export const formatPrice = (price: number) => `${price.toLocaleString('ko-KR')}원`

/** 결제 ID로 사용자에게 보여줄 예매번호를 만든다. */
export const formatBookingNumber = (paymentId: number) => `T${String(paymentId).padStart(8, '0')}`
