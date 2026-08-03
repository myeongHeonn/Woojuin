import './errorScreen.css';

/**
 * 배경에 흩뿌린 별 — 순수 장식.
 *
 * 배치를 고정 시드로 만드는 이유: Math.random 을 쓰면 리렌더마다 별이 튀고(에러 화면은
 * 재시도·리렌더가 잦은 자리다) 테스트에서도 매번 다른 그림이 나온다. 시드가 고정이면
 * "우연히 보기 좋은 배치"를 한 번 골라 그대로 고정할 수 있다.
 *
 * DOM 노드 44개는 span 뿐이고 애니메이션은 opacity·scale 만 건드려서
 * 레이아웃을 다시 계산하지 않는다(합성 단계에서 끝난다).
 */

/**
 * 별은 흰색뿐이다.
 *
 * 성좌 팔레트(라벤더·블루·옐로)를 섞어 봤지만 뺐다 — 이 화면은 "빛 없는 검은 우주"가
 * 컨셉이라, 색이 들어가면 그 인상이 깨진다. 밝기·크기만 흩어 놓는다.
 */
const STAR_COLOR = 'var(--color-star-white)';

const STARS = (() => {
  // 선형 합동 생성기 — 시드가 같으면 항상 같은 수열이 나온다
  let seed = 20260803;
  const random = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  };

  return Array.from({ length: 44 }, () => ({
    left: `${(random() * 100).toFixed(2)}%`,
    top: `${(random() * 100).toFixed(2)}%`,
    size: `${(1 + random() * 1.8).toFixed(2)}px`,
    // 다 같이 깜빡이면 심장박동처럼 보인다 — 주기와 시작점을 흩어 놓는다
    duration: `${(2.6 + random() * 3.4).toFixed(2)}s`,
    delay: `${(random() * -6).toFixed(2)}s`,
    restingOpacity: (0.3 + random() * 0.45).toFixed(2),
  }));
})();

const StarField = () => (
  <div aria-hidden className="pointer-events-none absolute inset-0 overflow-hidden">
    {STARS.map((star, index) => (
      <span
        key={index}
        className="error-star"
        style={{
          left: star.left,
          top: star.top,
          width: star.size,
          height: star.size,
          color: STAR_COLOR,
          // 애니메이션이 꺼졌을 때(동작 줄이기) 보이는 밝기
          opacity: star.restingOpacity,
          ['--error-star-duration' as string]: star.duration,
          ['--error-star-delay' as string]: star.delay,
        }}
      />
    ))}
  </div>
);

export default StarField;
