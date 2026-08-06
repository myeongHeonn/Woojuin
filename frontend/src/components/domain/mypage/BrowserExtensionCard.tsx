import { useState } from 'react';
import Modal from '@/components/ui/Modal';

const EXTENSION_URL =
  'https://chromewebstore.google.com/detail/%EC%9A%B0%EC%A3%BC%EC%9D%B8-%EC%9B%90%ED%81%B4%EB%A6%AD-%EC%8A%A4%ED%81%AC%EB%9E%A9/aifkmpjpjedlamliamnloliencimdfco?hl=ko';

/**
 * "연결된 앱" 그룹의 한 행 — 채팅 앱 연동 바로 아래.
 * 행을 누르면 바로 웹스토어로 보내지 않고, 채팅 앱 연동처럼 상세 모달로 들어가
 * 그 안의 버튼으로 설치 페이지로 이동한다. 채움·구분선은 부모가 만든다.
 */
const BrowserExtensionCard = () => {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="flex w-full items-center gap-3 px-4 py-3.5 text-left hover:bg-surface-2"
      >
        <span className="flex-1 text-sm font-semibold text-text-1">크롬 확장 프로그램 설치</span>
        <span aria-hidden="true" className="text-lg leading-none text-text-3">
          ›
        </span>
      </button>

      <Modal open={open} onClose={() => setOpen(false)} title="크롬 확장 프로그램 설치">
        <p className="text-[13px] leading-relaxed text-text-2">
          우주인 원클릭 스크랩으로 보고 있는 페이지를 저장해요.
        </p>
        <div className="mt-3 rounded-[14px] bg-surface-2 px-4 py-3 text-xs leading-5 text-text-3">
          아래 버튼에서 크롬 웹스토어로 이동해 설치하세요.
        </div>

        <a
          href={EXTENSION_URL}
          target="_blank"
          rel="noreferrer"
          className="mt-4 block w-full rounded-lg bg-accent px-4 py-3 text-center text-sm font-bold text-white transition-colors hover:bg-accent-hover"
        >
          설치 페이지로 이동
        </a>
      </Modal>
    </>
  );
};

export default BrowserExtensionCard;
