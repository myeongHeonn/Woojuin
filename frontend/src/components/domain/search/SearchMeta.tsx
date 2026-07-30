import { classNames } from '@/utils/classNames';

interface SearchMetaProps {
  /** 모든 단어 매칭 실패 → 일부만 포함한 결과로 폴백 */
  partialMatch?: boolean;
  /** AI 가 뽑아낸 실제 검색어 */
  interpretedQuery?: string;
  /** false 면 규칙 기반 폴백(오타교정·확장 없음) */
  aiPlanned?: boolean;
  /** 배치용(정렬/여백) — 여백은 부모가 정한다(대시보드는 들여쓰기, 성좌는 없음) */
  className?: string;
}

/**
 * 검색 결과 위 안내 — 결과가 예상과 다를 때 사용자가 원인을 알게 한다.
 * AI 해석어·규칙기반 폴백·부분일치를 " · " 로 이어 한 줄로. 알릴 게 없으면 안 그린다.
 * 여백을 박지 않는다 — 성좌·대시보드가 배치가 달라 부모가 className 으로 준다.
 */
const SearchMeta = ({ partialMatch, interpretedQuery, aiPlanned, className }: SearchMetaProps) => {
  const notes: string[] = [];
  if (interpretedQuery) notes.push(`'${interpretedQuery}'(으)로 찾았어요`);
  if (aiPlanned === false) notes.push('규칙 기반으로 찾았어요');
  if (partialMatch) notes.push('일부만 일치하는 결과예요');

  if (notes.length === 0) return null;

  return <p className={classNames('text-xs text-text-3', className)}>{notes.join(' · ')}</p>;
};

export default SearchMeta;
