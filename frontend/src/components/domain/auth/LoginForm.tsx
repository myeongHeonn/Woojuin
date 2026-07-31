import { Link } from 'react-router-dom';
import GoogleAuthButton from './GoogleAuthButton';

/**
 * 로그인 폼 — /login 페이지와 모바일 랜딩이 함께 쓴다.
 *
 * 페이지 레이아웃(가운데 정렬·배경)은 쓰는 쪽이 정하고 여기서는 폼만 그린다.
 * autoFocus 를 두지 않는 이유: 랜딩에서는 데스크톱용 마크업과 함께 DOM 에 있고
 * CSS 로만 감춰지므로, 자동 포커스가 엉뚱한 곳으로 튈 수 있다.
 */
const LoginForm = () => {
  return (
    <div className="flex w-full flex-col gap-4">
      {/* 이메일·비밀번호 로그인은 현재 사용하지 않는다.
      <form className="flex flex-col gap-3">
        <FormTextField type="email" placeholder="이메일" name="email" />
        <FormTextField type="password" placeholder="비밀번호" name="password" />
        <SubmitButton>로그인</SubmitButton>
      </form>
      */}

      <GoogleAuthButton />

      {/* 구글은 첫 로그인이 곧 가입이라(OAuthAccountService.findOrCreateUser) 여기에도 고지한다 */}
      <p className="text-center text-xs text-text-3">
        로그인하면{' '}
        <Link to="/privacy" className="underline underline-offset-2 hover:text-text-2">
          개인정보처리방침
        </Link>
        에 동의하는 것으로 봅니다.
      </p>

      {/* 이메일 회원가입은 현재 사용하지 않는다.
      <p className="text-center text-sm text-text-3">
        계정이 없으신가요?{' '}
        <Link to="/signup" className="font-semibold text-accent hover:text-accent-hover">
          회원가입
        </Link>
      </p>
      */}
    </div>
  );
};

export default LoginForm;
