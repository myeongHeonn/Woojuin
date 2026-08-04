import { useEffect, useRef, useState } from 'react';
import mainIcon from '@/assets/mainIcon.svg';
import astronaut from '@/assets/landing/astronaut.webp';
import discordImage from '@/assets/landing/coming-soon/discord-cutout.png';
import galaxyWatchImage from '@/assets/landing/coming-soon/galaxy-watch-clean.webp';
import kakaoTalkImage from '@/assets/landing/coming-soon/kakaotalk.png';
import mattermostImage from '@/assets/landing/coming-soon/mattermost-cutout.png';
import { LinkArrowIcon, PlanetIcon, UserPlusIcon } from '@/assets/icons';

import './landingFeatureSections.css';

const PhotoIcon = ({ className = '' }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
    <rect x="3" y="4" width="18" height="16" rx="3" stroke="currentColor" strokeWidth="1.8" />
    <circle cx="9" cy="9" r="2" fill="currentColor" />
    <path d="m5.5 17 4.2-4.2 3.1 3.1 2.1-2.1 3.6 3.2" stroke="currentColor" strokeWidth="1.8" />
  </svg>
);

const TextIcon = ({ className = '' }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
    <path
      d="M6 6h12M6 11h12M6 16h8"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
    />
  </svg>
);

const MapPinIcon = ({ className = '' }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
    <path
      d="M12 21s6-5.2 6-11a6 6 0 1 0-12 0c0 5.8 6 11 6 11Z"
      fill="currentColor"
      stroke="white"
      strokeWidth="1.5"
      strokeLinejoin="round"
    />
    <circle cx="12" cy="10" r="2.2" fill="white" />
  </svg>
);

const saveTickerItems = [
  { label: '링크', icon: <LinkArrowIcon className="h-5 w-5" /> },
  { label: '사진', icon: <PhotoIcon className="h-5 w-5" /> },
  { label: '텍스트', icon: <TextIcon className="h-5 w-5" /> },
] as const;

const SaveTickerRow = ({ reverse = false }: { reverse?: boolean }) => (
  <div className={`landing-ticker-row ${reverse ? 'landing-ticker-row-reverse' : ''}`}>
    <div className="landing-ticker-track">
      {[...saveTickerItems, ...saveTickerItems, ...saveTickerItems].map((item, index) => (
        <span key={`${item.label}-${index}`} className="landing-ticker-chip">
          <span className="text-accent">{item.icon}</span>
          {item.label}
        </span>
      ))}
    </div>
  </div>
);

const SavePreview = () => (
  <div className="landing-visual relative grid min-h-[360px] place-items-center overflow-hidden">
    <div className="landing-ticker-stack">
      <SaveTickerRow />
      <SaveTickerRow reverse />
    </div>
    <div className="landing-save-core">
      <span className="landing-spaceman-glow" />
      <img
        src={astronaut}
        alt="정보를 모으는 우주인"
        width="256"
        height="335"
        loading="lazy"
        decoding="async"
        className="relative h-28 w-28 object-contain"
      />
    </div>
  </div>
);

const HeartIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <path
      d="M20.8 4.8a5.5 5.5 0 0 0-7.8 0L12 5.9l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.4a5.5 5.5 0 0 0 0-7.8Z"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinejoin="round"
    />
  </svg>
);

const CommentIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <path
      d="M21 11.5a8.5 8.5 0 1 1-4.2-7.3"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
    />
    <path d="m5.5 18.5-1 3 3.2-1" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
  </svg>
);

const PaperPlaneIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <path
      d="m21 3-8.2 18-2.2-8.1L3 9.4 21 3Z"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinejoin="round"
    />
    <path d="m10.6 12.9 4.6-4.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
  </svg>
);

const ChromeExtensionVisual = () => (
  <div className="landing-extension-window" aria-label="Chrome 확장 프로그램 저장 예시">
    <div className="landing-browser-bar">
      <span className="landing-browser-dot" />
      <span className="landing-browser-address">woojuin.store/article</span>
      <img src={mainIcon} alt="" />
    </div>
    <div className="landing-extension-popup">
      <strong>우주인에 저장</strong>
      <span>My Space</span>
      <button type="button" tabIndex={-1}>
        현재 페이지 저장
      </button>
    </div>
    <svg className="landing-demo-cursor" viewBox="0 0 24 32" aria-hidden="true">
      <path d="M2 2v24l6.2-6.1 4.1 9.1 4.2-1.9-4.1-8.9H21L2 2Z" />
    </svg>
    <span className="landing-extension-success">✓ 우주인으로 보냈어요</span>
  </div>
);

const PhoneShareVisual = () => (
  <div className="landing-phone-share" aria-label="모바일에서 우주인으로 공유하는 예시">
    <div className="landing-instagram-header">
      <span>Instagram</span>
      <span>•••</span>
    </div>
    <div className="landing-instagram-post">
      <span className="landing-post-sun" />
      <span className="landing-post-hill landing-post-hill-left" />
      <span className="landing-post-hill landing-post-hill-right" />
    </div>
    <div className="landing-instagram-actions">
      <span>
        <HeartIcon />
      </span>
      <span>
        <CommentIcon />
      </span>
      <button type="button" className="landing-share-trigger" tabIndex={-1} aria-label="공유">
        <PaperPlaneIcon />
      </button>
    </div>
    <span className="landing-mobile-tap" aria-hidden="true" />
    <div className="landing-mobile-share-sheet">
      <span className="landing-share-sheet-handle" />
      <div className="landing-share-apps">
        <div className="landing-share-app">
          <img src={kakaoTalkImage} alt="" />
          <span>카카오톡</span>
        </div>
        <div className="landing-share-app">
          <span className="landing-facebook-logo">f</span>
          <span>Facebook</span>
        </div>
        <div className="landing-share-app landing-mobile-woojuin-target">
          <img src={mainIcon} alt="" />
          <strong>우주인에게 보내기</strong>
        </div>
      </div>
    </div>
  </div>
);

const MessengerVisual = () => (
  <div className="landing-messenger-visual" aria-label="Mattermost와 Discord 슬래시 명령 저장 예시">
    <div className="landing-messenger-logos">
      <div className="landing-messenger-half landing-mattermost-mark">
        <img src={mattermostImage} alt="Mattermost" />
      </div>
      <div className="landing-messenger-half landing-discord-mark">
        <img src={discordImage} alt="Discord" />
      </div>
    </div>
    <div className="landing-slash-command">
      <span className="landing-slash-prompt">/</span>
      <span className="landing-slash-typed">save woojuin</span>
      <span className="landing-slash-caret" />
      <span className="landing-slash-enter" aria-hidden="true">
        ↵
      </span>
    </div>
    <div className="landing-slash-sent">
      <span>✓ 우주인으로 보냈어요</span>
    </div>
  </div>
);

const GalaxyWatchVisual = () => (
  <div className="landing-watch-visual">
    <img
      src={galaxyWatchImage}
      alt="Galaxy Watch"
      width="220"
      height="257"
      loading="lazy"
      decoding="async"
      className="landing-galaxy-watch-image"
    />
    <div className="landing-watch-voice" aria-hidden="true">
      <svg viewBox="0 0 24 24" fill="none">
        <rect x="8" y="3" width="8" height="12" rx="4" fill="currentColor" />
        <path
          d="M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v3M8.5 21h7"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
        />
      </svg>
      <span />
      <span />
      <span />
    </div>
  </div>
);

const SaveMethodsPreview = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const element = containerRef.current;
    if (!element) return;

    const observer = new IntersectionObserver(([entry]) => setIsVisible(entry.isIntersecting), {
      threshold: 0.35,
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  return (
    <div ref={containerRef} className={`landing-method-grid ${isVisible ? 'is-visible' : ''}`}>
      <div className="landing-save-channel-grid">
        {[
          {
            title: 'Chrome 확장',
            description: '보고 있는 페이지에서 바로 저장',
            visual: <ChromeExtensionVisual />,
          },
          {
            title: '모바일 공유',
            description: '휴대폰에서 우주인으로 바로 보내기',
            visual: <PhoneShareVisual />,
          },
          {
            title: '메신저 봇',
            description: 'Mattermost와 Discord에서 슬래시로 저장',
            visual: <MessengerVisual />,
          },
          {
            title: 'Wear OS',
            description: 'Galaxy Watch에서 음성으로 빠르게 저장',
            visual: <GalaxyWatchVisual />,
          },
        ].map((feature, index) => (
          <article
            key={feature.title}
            className="landing-method-card landing-save-channel-card"
            style={{ animationDelay: `${(index + 1) * 0.11}s` }}
          >
            <div className="landing-save-channel-art">{feature.visual}</div>
            <h3>{feature.title}</h3>
            <p>{feature.description}</p>
          </article>
        ))}
      </div>
    </div>
  );
};

const UniversePreview = () => (
  <div className="landing-visual relative min-h-[390px] overflow-hidden">
    <div className="landing-universe-halo" />
    <svg
      viewBox="0 0 620 390"
      className="absolute inset-0 h-full w-full"
      aria-label="연결된 정보 예시"
    >
      <g className="landing-constellation-lines" fill="none" stroke="#777f92" strokeWidth="1.2">
        <path pathLength="1" d="M86 241 185 139 295 205 407 94 533 177" />
        <path pathLength="1" d="M185 139 138 74" />
        <path pathLength="1" d="M295 205 244 306" />
        <path pathLength="1" d="M407 94 478 60" />
        <path pathLength="1" d="M533 177 559 273" />
      </g>
      {[
        [86, 241, 5],
        [185, 139, 7],
        [295, 205, 6],
        [407, 94, 8],
        [533, 177, 6],
        [138, 74, 4],
        [244, 306, 4],
        [478, 60, 4],
        [559, 273, 4],
      ].map(([cx, cy, r], index) => (
        <g
          key={`${cx}-${cy}`}
          className={`landing-star ${[3, 4, 7].includes(index) ? 'landing-star-connected' : ''}`}
          style={{ animationDelay: `${index * 0.18}s` }}
        >
          <circle cx={cx} cy={cy} r={r * 4} fill="#9c86ff" opacity="0.08" />
          <circle cx={cx} cy={cy} r={r} fill="#f8f8ff" />
        </g>
      ))}
    </svg>
    <div className="landing-constellation-label left-[22%] top-[31%]">여행·장소</div>
    <div className="landing-constellation-label right-[15%] top-[18%]">학습·지식</div>
    <div className="landing-constellation-label bottom-[20%] left-[40%]">아이디어·영감</div>
  </div>
);

const MapPreview = () => (
  <div className="landing-visual landing-map relative min-h-[390px] overflow-hidden">
    <svg viewBox="0 0 620 390" className="absolute inset-0 h-full w-full" aria-hidden="true">
      <g fill="none" strokeLinecap="round">
        <path
          d="M-20 305C125 212 181 284 298 173S493 93 650 17"
          stroke="#46505d"
          strokeWidth="10"
        />
        <path d="M-20 305C125 212 181 284 298 173S493 93 650 17" stroke="#252c36" strokeWidth="6" />
        <path d="M56 -20c44 127 129 124 180 220s41 146 18 213" stroke="#39434f" strokeWidth="7" />
        <path d="M414 -10c-34 118-16 160 51 229s91 117 78 193" stroke="#39434f" strokeWidth="7" />
      </g>
    </svg>
    {[
      { left: '25%', top: '59%', label: '전시' },
      { left: '49%', top: '39%', label: '카페' },
      { left: '72%', top: '24%', label: '여행' },
    ].map((pin, index) => (
      <div
        key={pin.label}
        className="landing-map-pin"
        style={{ left: pin.left, top: pin.top, animationDelay: `${index * 0.45}s` }}
      >
        <span className="landing-map-pulse" />
        <MapPinIcon className="landing-pin-marker" />
        <span className="landing-pin-label">{pin.label}</span>
      </div>
    ))}
  </div>
);

const SpacePreview = () => (
  <div className="landing-visual relative grid min-h-[390px] place-items-center overflow-hidden">
    <div className="absolute left-[8%] top-[17%] h-52 w-52 rounded-full border border-accent/15" />
    <div className="landing-space-card landing-space-personal">
      <PlanetIcon className="h-7 w-7 text-accent" />
      <span className="text-xs font-bold tracking-[0.12em] text-text-2">PERSONAL SPACE</span>
    </div>
    <div className="landing-space-card landing-space-workspace">
      <UserPlusIcon className="h-7 w-7 text-[#9eb9ff]" />
      <span className="text-xs font-bold tracking-[0.12em] text-text-2">WORKSPACE</span>
      <div className="ml-auto flex -space-x-2">
        {['W', 'J', 'S'].map((initial, index) => (
          <span
            key={initial}
            className="grid h-7 w-7 place-items-center rounded-full border-2 border-surface text-[9px] font-bold text-space"
            style={{ backgroundColor: ['#c9b8ff', '#8fb4ff', '#b8e6a3'][index] }}
          >
            {initial}
          </span>
        ))}
      </div>
    </div>
    <span className="landing-space-connection" />
  </div>
);

const sections = [
  {
    eyebrow: 'ALL IN ONE',
    title: (
      <>
        <span className="landing-title-word">링크</span>도,{' '}
        <span className="landing-title-word landing-title-word-two">사진</span>도,{' '}
        <span className="landing-title-word landing-title-word-three">메모</span>도 한 번에
      </>
    ),
    description: '형식 구분 없이 필요한 정보를 한곳에 저장할 수 있어요.',
    visual: <SavePreview />,
  },
  {
    eyebrow: 'SAVE ANYWHERE',
    title: (
      <>
        어디에서든 바로{' '}
        <span className="landing-save-title">
          <span className="landing-check-mark">✓</span>
          저장
        </span>
      </>
    ),
    description: '우주인 안에서, 크롬에서, 휴대폰에서 떠오른 순간 바로 모아요.',
    visual: <SaveMethodsPreview />,
  },
  {
    eyebrow: 'UNIVERSE',
    title: (
      <>
        정보가 모이면 나만의 <span className="landing-universe-title">우주</span> 완성
      </>
    ),
    description: '관련된 정보는 별로 연결되어 한눈에 볼 수 있어요.',
    visual: <UniversePreview />,
  },
  {
    eyebrow: 'MAPPING',
    title: (
      <>
        위치가 있는 정보는{' '}
        <span className="landing-map-title">
          <MapPinIcon className="landing-title-pin" />
          지도
        </span>{' '}
        위에서
      </>
    ),
    description: '위치 정보가 있는 링크와 사진은 지도에도 표시돼요.',
    visual: <MapPreview />,
  },
  {
    eyebrow: 'WORKSPACE',
    title: (
      <>
        <span className="landing-personal-title">혼자서도</span>,{' '}
        <span className="landing-together-title">여럿이서도</span>
      </>
    ),
    description: '개인 스페이스에는 내 정보를, 워크스페이스에는 함께 볼 정보를 모아요.',
    visual: <SpacePreview />,
  },
] as const;

const LandingFeatureSections = () => (
  <div className="border-t border-border-soft">
    {sections.map((section, index) => {
      const isTextOnRight = index === 2 || index === 4;

      return (
        <section
          key={section.eyebrow}
          className={`px-6 py-24 desktop:py-32 ${index % 2 === 1 ? 'bg-sidebar/35' : ''}`}
        >
          {index === 1 ? (
            <div className="mx-auto max-w-6xl">
              <div className="landing-save-heading text-center">
                <p className="text-label font-bold tracking-[0.16em] text-accent">
                  {section.eyebrow}
                </p>
                <h2 className="mt-4 break-keep text-3xl font-extrabold leading-tight tracking-tight desktop:text-[42px]">
                  {section.title}
                </h2>
              </div>
              <div className="mt-12">{section.visual}</div>
            </div>
          ) : (
            <div className="mx-auto grid max-w-6xl items-center gap-12 min-[1100px]:grid-cols-2 min-[1100px]:gap-20">
              <div className={isTextOnRight ? 'min-[1100px]:order-2' : ''}>
                <p className="text-label font-bold tracking-[0.16em] text-accent">
                  {section.eyebrow}
                </p>
                <h2 className="mt-4 break-keep text-3xl font-extrabold leading-tight tracking-tight desktop:text-[42px]">
                  {section.title}
                </h2>
              </div>
              <div className={isTextOnRight ? 'min-[1100px]:order-1' : ''}>{section.visual}</div>
            </div>
          )}
        </section>
      );
    })}
    <div className="h-[12vh] min-h-[96px]" aria-hidden="true" />
  </div>
);

export default LandingFeatureSections;
