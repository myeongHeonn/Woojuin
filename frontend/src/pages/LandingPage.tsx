import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import AnimatedLogoBackdrop from '@/components/domain/landing/AnimatedLogoBackdrop';
import LandingFeatureSections from '@/components/domain/landing/LandingFeatureSections';
import LandingHeader from '@/components/domain/landing/LandingHeader';

const LandingPage = () => {
  const heroRef = useRef<HTMLElement>(null);
  const [countdownRun, setCountdownRun] = useState(0);

  useEffect(() => {
    const hero = heroRef.current;
    if (!hero) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) setCountdownRun((run) => run + 1);
      },
      { threshold: 0.45 },
    );

    observer.observe(hero);
    return () => observer.disconnect();
  }, []);

  return (
    <div className="min-h-dvh overflow-x-hidden bg-space text-text-1">
      <LandingHeader />

      <section ref={heroRef} className="relative min-h-dvh overflow-hidden px-6">
      <AnimatedLogoBackdrop />

      <main className="pointer-events-none relative z-[12] mx-auto flex min-h-dvh max-w-3xl flex-col items-center pb-14 pt-[48vh] text-center desktop:pt-[58vh]">
        <h1 className="text-hero font-extrabold tracking-[-0.02em]">
          저장은{' '}
          <span key={countdownRun} className="landing-countdown" aria-label="1">
            <span className="landing-countdown-five">5</span>
            <span className="landing-countdown-four">4</span>
            <span className="landing-countdown-three">3</span>
            <span className="landing-countdown-two">2</span>
            <span className="landing-countdown-one">1</span>
          </span>
          초, 정리는 AI가
        </h1>
        <p className="mt-4 max-w-2xl text-[16px] leading-7 text-text-2 desktop:text-lg">
          흩어진 링크·사진·메모를 하나의 우주에 모아보세요.
        </p>
      </main>
      </section>

      <LandingFeatureSections />

      <Link
        to="/signup"
        className="fixed bottom-5 left-1/2 z-30 flex -translate-x-1/2 items-center gap-2.5 whitespace-nowrap rounded-full border border-white/10 bg-accent px-6 py-3.5 text-sm font-extrabold tracking-[0.08em] text-white shadow-modal transition hover:-translate-y-1 hover:bg-accent-hover desktop:bottom-7 desktop:px-8"
      >
        WOULD YOU IN?
        <span aria-hidden="true" className="text-lg leading-none">→</span>
      </Link>
    </div>
  );
};

export default LandingPage;
