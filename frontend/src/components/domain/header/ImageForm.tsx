import { useEffect, useRef, useState } from 'react';
import { classNames } from '@/utils/classNames';
import { saveImage } from '@/services/items';
import { useCreateItem } from '@/hooks/useCreateItem';
import { heicToJpeg, isHeicCandidate } from '@/utils/heicToJpeg';
import SubmitButton from '@/components/ui/button/SubmitButton';

interface ImageFormProps {
  /** 저장이 끝나면 팝오버를 닫는다 */
  onDone: () => void;
}

const HEIC_FAILED_MESSAGE = '이 사진을 변환하지 못했어요. 다른 형식으로 저장해 주세요.';
const NOT_IMAGE_MESSAGE = '이미지 파일만 넣을 수 있어요.';

/**
 * 이미지인지 판별.
 *
 * type 만 보면 HEIC 을 놓친다 — 브라우저가 모르는 형식이면 type 이 빈 문자열로 와서
 * image/ 로 시작하지 않는다. 확장자까지 보는 isHeicCandidate 로 한 번 더 건진다.
 */
const isImageFile = (candidate: File) =>
  candidate.type.startsWith('image/') || isHeicCandidate(candidate);

/** 사진 저장 — 셋 중 유일하게 multipart/form-data 로 나간다 */
const ImageForm = ({ onDone }: ImageFormProps) => {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [converting, setConverting] = useState(false);
  const [fileError, setFileError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const { save, isPending, errorMessage } = useCreateItem(saveImage, onDone);

  /* 변환 중에 다른 파일을 고르면 늦게 끝난 이전 변환이 새 선택을 덮어쓴다 — 마지막 선택만 반영한다 */
  const pickSeq = useRef(0);

  /* 미리보기 URL 은 파일이 바뀔 때마다 새로 만들고 반드시 되돌린다 (누수 방지) */
  useEffect(() => {
    if (!file) {
      setPreview(null);
      return;
    }
    const objectUrl = URL.createObjectURL(file);
    setPreview(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [file]);

  /**
   * HEIC(아이폰 기본 포맷)만 JPEG 로 바꿔서 들고 있는다. 서버가 HEIC 를 못 읽기도 하고,
   * 크롬·파이어폭스는 HEIC 미리보기도 못 그려서 변환 전에는 아래 미리보기가 깨진 채로 뜬다.
   */
  const pick = async (picked: File | null) => {
    const seq = (pickSeq.current += 1);
    setFileError(null);
    if (!picked || !isHeicCandidate(picked)) {
      setConverting(false);
      setFile(picked);
      return;
    }

    setFile(null);
    setConverting(true);
    try {
      const converted = await heicToJpeg(picked);
      if (seq !== pickSeq.current) return;
      setFile(converted);
    } catch {
      if (seq !== pickSeq.current) return;
      setFileError(HEIC_FAILED_MESSAGE);
    } finally {
      if (seq === pickSeq.current) setConverting(false);
    }
  };

  /* 리스너는 한 번만 붙이고 그 안에서는 늘 최신 pick 을 쓴다(매 렌더 새로 만들어지는 함수라서) */
  const pickRef = useRef(pick);
  pickRef.current = pick;

  /**
   * Ctrl+V 로 붙여넣기 — 스크린샷을 찍고 바로 저장하는 흐름이 이 앱에서 잦다.
   *
   * AddModal 이 사진 탭일 때만 이 폼을 렌더하므로 document 에 걸어도 다른 탭에서는
   * 동작하지 않는다. 다만 문서 전체를 듣기 때문에, 입력창에 붙여넣는 중이라면
   * 그쪽 몫으로 두고 비켜야 한다 — 검색창에 텍스트를 붙여넣는데 사진 폼이 끼어들어
   * "이미지가 아니다"라고 하면 엉뚱하다.
   */
  useEffect(() => {
    const onPaste = (event: ClipboardEvent) => {
      const items = event.clipboardData?.items;
      if (!items) return;

      const target = event.target;
      if (
        target instanceof Element &&
        target.closest('input, textarea, [contenteditable="true"]')
      ) {
        return;
      }

      const image = [...items].find(
        (item) => item.kind === 'file' && item.type.startsWith('image/'),
      );
      if (!image) {
        // 사진 탭에서 누른 Ctrl+V 는 사진을 넣으려는 뜻이다 — 왜 안 됐는지 알려준다
        setFileError(NOT_IMAGE_MESSAGE);
        return;
      }

      event.preventDefault();
      void pickRef.current(image.getAsFile());
    };

    document.addEventListener('paste', onPaste);
    return () => document.removeEventListener('paste', onPaste);
  }, []);

  /**
   * 드롭 영역을 살짝 빗나가면 브라우저가 그 파일을 열어 앱을 벗어난다(팝오버가 좁아 자주 생긴다).
   * 사진 탭이 열려 있는 동안만 창 전체의 기본 동작을 막아 실수로 나가지 않게 한다.
   */
  useEffect(() => {
    const prevent = (event: DragEvent) => event.preventDefault();
    window.addEventListener('dragover', prevent);
    window.addEventListener('drop', prevent);
    return () => {
      window.removeEventListener('dragover', prevent);
      window.removeEventListener('drop', prevent);
    };
  }, []);

  return (
    <form
      className="flex flex-col gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        if (file) save(file);
      }}
    >
      <label
        // dragover 에서 preventDefault 를 해야 브라우저가 드롭을 허용한다 — 안 하면 drop 이 아예 안 온다
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        // 자식(미리보기 이미지 등) 위로 들어가도 dragleave 가 나서 테두리가 깜빡인다.
        // 커서가 이 상자 밖으로 나간 경우에만 끈다.
        onDragLeave={(e) => {
          if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDragging(false);
        }}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);

          // 여러 장을 끌어와도 첫 장만 받는다 — 이 폼은 한 번에 한 장을 저장한다
          const dropped = e.dataTransfer.files[0];
          if (!dropped) return;
          if (!isImageFile(dropped)) {
            setFileError(NOT_IMAGE_MESSAGE);
            return;
          }
          void pick(dropped);
        }}
        className={classNames(
          'flex cursor-pointer flex-col items-center justify-center gap-1 rounded-md',
          'border border-dashed px-3 py-6 text-center transition-colors',
          dragging
            ? 'border-accent bg-accent/10'
            : 'border-border bg-surface-2 hover:border-accent',
        )}
      >
        <input
          type="file"
          // image/* 만 두면 데스크톱 파일 대화상자에서 .heic 이 회색으로 잠기는 브라우저가 있다
          accept="image/*,.heic,.heif"
          className="hidden"
          onChange={(e) => void pick(e.target.files?.[0] ?? null)}
        />
        {converting ? (
          <span className="text-sm font-semibold text-text-2">사진 변환 중...</span>
        ) : dragging ? (
          <span className="text-sm font-semibold text-accent">여기에 놓기</span>
        ) : preview ? (
          <img
            src={preview}
            alt={file?.name ?? '선택한 사진'}
            className="max-h-28 rounded-sm object-contain"
          />
        ) : (
          <>
            <span className="text-sm font-semibold text-text-2">사진 선택</span>
            <span className="text-xs text-text-3">클릭 · 끌어다 놓기 · Ctrl+V</span>
          </>
        )}
      </label>

      {file && <p className="truncate text-xs text-text-3">{file.name}</p>}
      {fileError && <p className="text-sm text-red-500">{fileError}</p>}
      {errorMessage && <p className="text-sm text-red-500">{errorMessage}</p>}

      <SubmitButton pending={isPending} disabled={!file || converting} pendingLabel="저장 중...">
        저장
      </SubmitButton>
    </form>
  );
};

export default ImageForm;
