import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ImageForm from '@/components/domain/header/ImageForm';

// 변환기 자체는 heicToJpeg.test.ts 가 본다. 여기서는 폼이 그 결과를 어떻게 보여주는지만 확인한다
// (실물을 쓰면 libheif wasm 3MB 를 브라우저로 끌고 온다).
const converter = vi.hoisted(() => ({ isHeicCandidate: vi.fn(), heicToJpeg: vi.fn() }));
vi.mock('@/utils/heicToJpeg', () => converter);

const setup = () =>
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { mutations: { retry: false } } })}
    >
      <MemoryRouter initialEntries={['/workspace/7/universe']}>
        <Routes>
          <Route
            path="/workspace/:workspaceId/universe"
            element={<ImageForm onDone={() => {}} />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );

const fileInput = (c: HTMLElement) => c.querySelector<HTMLInputElement>('input[type="file"]')!;
const submitButton = (c: HTMLElement) =>
  c.querySelector<HTMLButtonElement>('button[type="submit"]')!;

const heic = () => new File(['heic'], 'IMG_0001.HEIC', { type: 'image/heic' });
const png = () => new File(['png'], 'shot.png', { type: 'image/png' });

/** 변환이 끝나는 시점을 테스트가 직접 잡을 수 있게 만든 지연 Promise */
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: Error) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

beforeEach(() => {
  converter.isHeicCandidate
    .mockReset()
    .mockImplementation((file: File) => file.type === 'image/heic');
  converter.heicToJpeg.mockReset();
});

describe('ImageForm — HEIC 업로드', () => {
  it('HEIC 를 고르면 변환한 뒤 그 파일을 보여준다', async () => {
    converter.heicToJpeg.mockResolvedValue(
      new File(['jpg'], 'IMG_0001.jpg', { type: 'image/jpeg' }),
    );
    const { container } = await setup();

    await userEvent.upload(fileInput(container), heic());

    await vi.waitFor(() => expect(container.textContent).toContain('IMG_0001.jpg'));
    expect(submitButton(container)).not.toBeDisabled();
  });

  it('변환이 끝날 때까지 안내를 띄우고 저장을 막는다', async () => {
    // 아이폰 사진 한 장 디코딩에 수 초가 걸린다 — 그동안 빈 화면이면 멈춘 것처럼 보인다
    const converting = deferred<File>();
    converter.heicToJpeg.mockReturnValue(converting.promise);
    const { container } = await setup();

    await userEvent.upload(fileInput(container), heic());

    await vi.waitFor(() => expect(container.textContent).toContain('사진 변환 중'));
    expect(submitButton(container)).toBeDisabled();

    converting.resolve(new File(['jpg'], 'IMG_0001.jpg', { type: 'image/jpeg' }));
    await vi.waitFor(() => expect(submitButton(container)).not.toBeDisabled());
  });

  it('변환에 실패하면 안내하고 저장을 막는다', async () => {
    // 그대로 올리면 서버가 형식으로 거절하거나 썸네일 없는 반쪽짜리 아이템이 된다
    converter.heicToJpeg.mockRejectedValue(new Error('디코딩 실패'));
    const { container } = await setup();

    await userEvent.upload(fileInput(container), heic());

    await vi.waitFor(() => expect(container.textContent).toContain('변환하지 못했어요'));
    expect(submitButton(container)).toBeDisabled();
  });

  it('일반 이미지는 변환기를 거치지 않는다', async () => {
    const { container } = await setup();

    await userEvent.upload(fileInput(container), png());

    await vi.waitFor(() => expect(container.textContent).toContain('shot.png'));
    expect(converter.heicToJpeg).not.toHaveBeenCalled();
  });

  it('변환 중에 다른 사진을 고르면 나중 선택이 이긴다', async () => {
    // 늦게 끝난 이전 변환이 새 선택을 덮어쓰면 사용자가 고르지 않은 사진이 올라간다
    const slow = deferred<File>();
    converter.heicToJpeg.mockReturnValue(slow.promise);
    const { container } = await setup();

    await userEvent.upload(fileInput(container), heic());
    await vi.waitFor(() => expect(container.textContent).toContain('사진 변환 중'));

    await userEvent.upload(fileInput(container), png());
    await vi.waitFor(() => expect(container.textContent).toContain('shot.png'));

    slow.resolve(new File(['jpg'], '늦게-끝난.jpg', { type: 'image/jpeg' }));
    await vi.waitFor(() => expect(container.textContent).toContain('shot.png'));
    expect(container.textContent).not.toContain('늦게-끝난.jpg');
  });
});
