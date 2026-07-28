/** presigned URL 경로에서 확장자 추출(쿼리·해시 제거). 못 찾으면 png */
function extFromUrl(url: string): string {
  const path = url.split(/[?#]/)[0];
  const match = path.match(/\.([a-zA-Z0-9]{2,5})$/);
  return match ? match[1].toLowerCase() : 'png';
}

/**
 * 저장 파일명 — 제목이 있으면 제목, 없으면 "우주인-{id}". 파일명에 못 쓰는 문자는 _ 로 바꾼다.
 * 확장자는 원본 URL 에서 딴다. 순수 함수라 단독으로 테스트한다.
 */
export function imageFilename(title: string | null, itemId: number, url: string): string {
  const base = (title?.trim() || `우주인-${itemId}`).replace(/[\\/:*?"<>|]/g, '_');
  return `${base}.${extFromUrl(url)}`;
}

/**
 * 이미지 URL 을 blob 으로 받아 파일로 저장한다.
 * presigned URL 은 교차 출처라 <a download> 만으론 저장이 안 되므로(브라우저가 무시)
 * blob 으로 받아 object URL 로 내려받는다. CORS 등으로 실패하면 새 탭으로 열어 수동 저장하게 둔다.
 */
export async function downloadImage(url: string, filename: string): Promise<void> {
  try {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const blob = await res.blob();

    const objectUrl = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = objectUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(objectUrl);
  } catch {
    window.open(url, '_blank', 'noopener');
  }
}
