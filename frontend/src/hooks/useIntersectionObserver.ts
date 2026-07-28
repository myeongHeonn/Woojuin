import { useEffect, useRef } from 'react';

//화면을 관찰하면서 ref를 가지고 있는 값이 화면에 들어오면 통신을 진행한다.
export function useIntersectionObserver(onIntersect: () => void) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = ref.current;

    if (!el) return; //요소가 없으면 패스

    //isIntersecting은 해당 요소가 뷰포트에 있나를 관찰한다.
    const observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting) {
        onIntersect();
      }
    });

    observer.observe(el);

    return () => observer.disconnect();
  }, [onIntersect]);

  return ref;
}
