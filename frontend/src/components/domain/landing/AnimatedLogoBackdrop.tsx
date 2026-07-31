import hoverOrbitPreview from './would-you-in-hover-orbit-fixed.html?raw';
import './animatedLogoBackdrop.css';

const blendedHoverOrbitPreview = hoverOrbitPreview
  .replaceAll('background: #000;', 'background: #0e1017;')
  .replace('<rect width="60" height="60" fill="black"/>', '')
  .replace(
    /<rect\s+x="38"\s+y="8"\s+width="17"\s+height="17"\s+fill="url\(#paint0_radial_64_3523\)"\s*\/>/,
    '',
  )
  .replace(/<rect([\s\S]*?)fill="black"\s*\/>/g, '<rect$1fill="#0e1017"/>')
  .replace('<div class="hint">로고 위에 마우스를 올려보세요</div>', '');

/** 원본 hover 동작 위에 랜딩 전용 바깥 궤도 두 개를 더한다. */
const AnimatedLogoBackdrop = () => (
  <div className="landing-logo-backdrop">
    <div className="landing-extra-orbit landing-extra-orbit--one">
      <span className="landing-extra-orbit-dot" />
    </div>
    <div className="landing-extra-orbit landing-extra-orbit--two" />

    <iframe
      className="landing-logo-frame"
      srcDoc={blendedHoverOrbitPreview}
      title="우주인 인터랙티브 로고"
    />
  </div>
);

export default AnimatedLogoBackdrop;
