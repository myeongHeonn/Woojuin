/** 메모 카드 안쪽 — 본문을 회색 줄로 흉내낸다 (실제 content 를 넣지 않는다, 목업 dashboard.html) */
const MemoSkeleton = () => (
  <div className="flex h-full flex-col justify-center gap-2 p-[18px]">
    {['100%', '70%', '100%', '55%'].map((w, i) => (
      <span key={i} className="block h-[3px] rounded-[2px] bg-[#aeb4c0]/65" style={{ width: w }} />
    ))}
  </div>
);

export default MemoSkeleton;
