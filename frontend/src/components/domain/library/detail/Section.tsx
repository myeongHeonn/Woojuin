import type { ReactNode } from 'react';

/**
 * 상세 모달 공용 — 라벨(작은 대문자) + 본문 텍스트 블록.
 * 요약·OCR·본문처럼 "제목 붙은 글 덩어리"에 쓴다. 줄바꿈은 그대로 보존한다.
 */
const Section = ({ label, children }: { label: string; children: ReactNode }) => (
  <div>
    <p className="mb-1 text-[11px] font-bold uppercase tracking-wider text-text-3">{label}</p>
    <p className="whitespace-pre-wrap text-sm leading-relaxed text-text-2">{children}</p>
  </div>
);

export default Section;
