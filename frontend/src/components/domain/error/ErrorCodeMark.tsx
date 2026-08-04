import LogoRing from '@/components/domain/error/LogoRing';

interface ErrorCodeMarkProps {
  /**
   * 표시할 코드. `0` 은 로고 링으로 대체된다.
   * 상태 코드가 없는 에러(브라우저에서 터진 예외)면 넘기지 않는다 — 링만 크게 보여 준다.
   */
  code?: string;
}

/**
 * 에러 코드 표시 — `404` 의 `0` 자리에 우주인 로고의 링이 들어간다.
 *
 * 링 지름을 `em` 으로 주는 이유: 글자 크기가 clamp() 로 화면에 따라 변하는데, px 로 박으면
 * 좁은 화면에서 숫자만 작아지고 링은 그대로 남아 어긋난다(기본 지름은 LogoRing 참고 —
 * Pretendard 숫자 높이를 실측한 값이다).
 *
 * 코드를 링으로 바꿔 놓으면 스크린리더가 "40" 으로 읽는다. 그래서 전체를 하나의 이미지로
 * 선언하고 라벨로 코드를 읽어 준다.
 */
const ErrorCodeMark = ({ code }: ErrorCodeMarkProps) => (
  <div
    role="img"
    aria-label={code ? `오류 코드 ${code}` : '오류'}
    className="flex items-baseline justify-center gap-[0.04em] text-[clamp(64px,17vw,124px)] font-extrabold leading-none tracking-tight text-text-1"
  >
    {code ? (
      [...code].map((character, index) =>
        character === '0' ? <LogoRing key={index} /> : <span key={index}>{character}</span>,
      )
    ) : (
      <LogoRing diameter="1em" />
    )}
  </div>
);

export default ErrorCodeMark;
