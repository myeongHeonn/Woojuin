-- 탈퇴한 계정의 로그인 식별자를 파기한다 (User.withdraw() 와 같은 규칙을 기존 데이터에 소급 적용).
--
-- 왜 필요한가: 탈퇴는 소프트 삭제라 행이 남는데, 그 행이 provider_id 를 계속 들고 있으면
-- uk_users_provider UNIQUE (provider, provider_id) 가 같은 구글 계정의 재가입을 영구히 막는다.
-- 게다가 실패가 조용해서(예전 OAuth2LoginFailureHandler 가 사유를 버렸다) 사용자는 로그인
-- 화면만 되돌아 봤다. 코드만 고치면 앞으로 탈퇴하는 사람은 괜찮지만 이미 탈퇴한 사람은 계속
-- 막혀 있다 — 그래서 기존 행도 함께 정리한다.
--
-- 이메일도 같이 파기한다: 개인정보처리방침이 회원 정보(이메일 등)의 보유기간을 "회원 탈퇴
-- 시까지"로 고지하고 있어 탈퇴 후 보관은 그 고지와 어긋나고, LOCAL 계정의 같은 주소 재가입도
-- existsByEmail 에 막혀 있다. `.invalid` 는 RFC 2606 예약 TLD 로 실제 주소가 될 수 없다.
--
-- nickname 은 남긴다 — 로그인 식별자가 아니어서 재가입을 막지 않기 때문이고, 이 변경의 범위를
-- 로그인 식별자로 한정한 것이다. 표시 때문은 아니다: 탈퇴자의 닉네임은 어느 화면에도 나가지
-- 않는다(WorkspaceMemberResponse·WorkspaceMemberActivityResponse 가 "탈퇴한 사용자"로 바꾼다).
--
-- ⚠️ 되돌릴 수 없다. 탈퇴자의 원래 이메일·provider_id 는 이 시점 이후 DB 에 남지 않는다.
UPDATE users
SET email       = 'withdrawn+' || id || '@woojuin.invalid',
    provider_id = NULL
WHERE deleted_at IS NOT NULL
  -- 이미 파기된 행은 건드리지 않는다(마이그레이션 재적용·수동 정리 후에도 안전하게).
  AND email NOT LIKE 'withdrawn+%@woojuin.invalid';
