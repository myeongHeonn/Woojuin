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

/** 사진 저장 — 셋 중 유일하게 multipart/form-data 로 나간다 */
const ImageForm = ({ onDone }: ImageFormProps) => {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [converting, setConverting] = useState(false);
  const [convertError, setConvertError] = useState<string | null>(null);
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
    setConvertError(null);
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
      setConvertError(HEIC_FAILED_MESSAGE);
    } finally {
      if (seq === pickSeq.current) setConverting(false);
    }
  };

  return (
    <form
      className="flex flex-col gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        if (file) save(file);
      }}
    >
      <label
        className={classNames(
          'flex cursor-pointer flex-col items-center justify-center gap-1 rounded-md',
          'border border-dashed border-border bg-surface-2 px-3 py-6 text-center',
          'hover:border-accent',
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
        ) : preview ? (
          <img
            src={preview}
            alt={file?.name ?? '선택한 사진'}
            className="max-h-28 rounded-sm object-contain"
          />
        ) : (
          <>
            <span className="text-sm font-semibold text-text-2">사진 선택</span>
            <span className="text-xs text-text-3">클릭해서 파일 고르기</span>
          </>
        )}
      </label>

      {file && <p className="truncate text-xs text-text-3">{file.name}</p>}
      {convertError && <p className="text-sm text-red-500">{convertError}</p>}
      {errorMessage && <p className="text-sm text-red-500">{errorMessage}</p>}

      <SubmitButton pending={isPending} disabled={!file || converting} pendingLabel="저장 중...">
        저장
      </SubmitButton>
    </form>
  );
};

export default ImageForm;
