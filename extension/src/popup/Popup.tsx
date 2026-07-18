import { useEffect, useState } from 'react';

/** 페이지 전체 저장 (FR-011): 현재 탭 URL을 원클릭 저장 */
export default function Popup() {
  const [url, setUrl] = useState('');

  useEffect(() => {
    chrome.tabs.query({ active: true, currentWindow: true }).then(([tab]) => {
      setUrl(tab?.url ?? '');
    });
  }, []);

  const handleSave = () => {
    // TODO: 로그인 토큰 + 워크스페이스 선택(FR-042) + 저장 API 호출
    console.log('save:', url);
  };

  return (
    <main style={{ padding: 16 }}>
      <h1 style={{ fontSize: 16 }}>우주인에 저장</h1>
      <p style={{ fontSize: 12, wordBreak: 'break-all' }}>{url}</p>
      <button onClick={handleSave}>저장하기</button>
    </main>
  );
}
