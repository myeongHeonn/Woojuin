/**
 * MV3 백그라운드 서비스워커
 * 우클릭 컨텍스트 메뉴 (FR-015): 이미지 / 선택 텍스트 단위 저장
 */
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: 'save-image',
    title: '이 이미지를 우주인에 저장',
    contexts: ['image'],
  });
  chrome.contextMenus.create({
    id: 'save-selection',
    title: '선택한 텍스트를 우주인에 저장',
    contexts: ['selection'],
  });
});

chrome.contextMenus.onClicked.addListener((info) => {
  if (info.menuItemId === 'save-image' && info.srcUrl) {
    // TODO: 이미지 URL 저장 API 호출 → OCR 파이프라인(FR-022) 진입
    console.log('save image:', info.srcUrl);
  }
  if (info.menuItemId === 'save-selection' && info.selectionText) {
    // TODO: 선택 텍스트를 메모로 저장
    console.log('save selection:', info.selectionText);
  }
});
