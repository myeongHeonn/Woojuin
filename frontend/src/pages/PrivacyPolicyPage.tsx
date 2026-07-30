import type { ReactNode } from 'react';
import BackButton from '@/components/ui/button/BackButton';
import BrandMark from '@/components/ui/BrandMark';

/**
 * 개인정보처리방침 — /privacy (로그인 없이 접근 가능한 공개 문서).
 *
 * 본문은 2026-07 시점의 **실제 구현**을 근거로 작성했다. 기능이 바뀌면 방침이
 * 거짓이 되므로, 아래 항목을 건드리는 변경은 이 문서도 함께 고쳐야 한다:
 *   - 가입/로그인 수단 (현재: 이메일, 구글 OAuth — 카카오 로그인은 미구현이라 기재하지 않음)
 *   - 외부 AI 전송 경로 (현재: OpenAI(GMS 프록시)·OpenRouter·카카오 로컬 API)
 *   - 위치정보 수집 경로 (현재: 지도 링크 좌표·사진 EXIF뿐 — 단말 현재 위치 수집 없음)
 *   - 회원 탈퇴 (현재: 앱 내 기능 없음 → 문의처 요청 방식으로 안내 중)
 */
const PrivacyPolicyPage = () => (
  <div className="min-h-dvh bg-space text-text-1">
    <BackButton className="fixed left-4 top-4" />

    <main className="mx-auto max-w-2xl px-6 pb-24 pt-16 desktop:pt-20">
      <BrandMark className="justify-center" />

      <h1 className="mt-10 text-2xl font-extrabold tracking-tight">개인정보처리방침</h1>
      <p className="mt-2 text-[13px] text-text-3">시행일: 2026년 8월 1일</p>

      <p className="mt-6 text-body leading-relaxed text-text-2">
        우주인 팀(이하 &ldquo;팀&rdquo;)은 올인원 AI 스크랩북 서비스 &lsquo;우주인&rsquo;(이하
        &ldquo;서비스&rdquo;)을 운영하며, 「개인정보 보호법」 등 관계 법령에 따라 이용자의
        개인정보를 보호하고 이와 관련한 고충을 신속하게 처리하기 위하여 다음과 같이
        개인정보처리방침을 수립·공개합니다. 본 서비스는 삼성 청년 SW·AI 아카데미(SSAFY) 교육
        과정에서 개발·운영됩니다.
      </p>

      <Section title="제1조 (개인정보의 처리 목적)">
        <p>팀은 다음의 목적을 위해 개인정보를 처리하며, 목적 외의 용도로는 이용하지 않습니다.</p>
        <ol className="flex list-decimal flex-col gap-1 pl-5">
          <li>회원 가입 및 관리 — 본인 식별·인증, 회원 자격 유지·관리, 각종 고지·통지</li>
          <li>
            콘텐츠 저장·정리 서비스 제공 — 이용자가 저장한 링크·사진·메모의 보관, AI를 이용한
            제목·요약·카테고리 자동 생성, 키워드·의미 기반 검색, 지도·성좌 등 시각화 제공
          </li>
          <li>워크스페이스 협업 — 초대 링크를 통한 워크스페이스 공유 및 멤버 관리</li>
          <li>알림 제공 — 저장 항목의 AI 처리 완료 푸시 알림 발송</li>
        </ol>
      </Section>

      <Section title="제2조 (수집하는 개인정보의 항목 및 수집 방법)">
        <p>① 회원 가입 시 다음 항목을 수집합니다.</p>
        <ul className="flex list-disc flex-col gap-1 pl-5">
          <li>이메일 가입: [필수] 이메일 주소, 비밀번호, 닉네임</li>
          <li>
            구글 계정으로 가입: [필수] 구글 계정 고유 식별자, 이메일 주소, 이름(닉네임으로 사용).
            구글이 제공하는 프로필 사진은 저장하지 않습니다.
          </li>
        </ul>
        <p>② 서비스 이용 과정에서 이용자가 직접 입력·업로드하는 정보를 수집합니다.</p>
        <ul className="flex list-disc flex-col gap-1 pl-5">
          <li>
            저장 콘텐츠: 링크(URL)와 해당 페이지의 제목·본문·미리보기 정보, 메모 원문, 이미지 파일.
            업로드한 이미지 파일에 포함된 EXIF 메타데이터(촬영 위치 GPS 좌표 등)는 원본 그대로
            보관됩니다.
          </li>
          <li>프로필 정보: 닉네임, 아바타 색, 프로필 이미지(직접 등록한 경우)</li>
        </ul>
        <p>③ 서비스 이용 과정에서 다음 정보가 자동으로 생성·수집됩니다.</p>
        <ul className="flex list-disc flex-col gap-1 pl-5">
          <li>
            위치정보: 저장한 지도 공유 링크의 좌표 또는 업로드한 사진의 EXIF GPS 좌표에서 추출한
            위도·경도와 이를 변환한 주소. 이용자 단말기의 현재 위치는 수집하지 않습니다.
          </li>
          <li>
            AI 산출물: 콘텐츠 요약, 자동 분류 카테고리, 이미지에서 추출한 텍스트·설명, 검색용 임베딩
            벡터
          </li>
          <li>푸시 알림 사용 시: 기기 푸시 토큰(FCM), 브라우저 정보(User-Agent)</li>
          <li>접속 기록: 서비스 웹서버에 남는 접속 로그(IP 주소, 접속 일시, 요청 URL)</li>
        </ul>
        <p>
          ④ 비밀번호는 수집 즉시 복호화할 수 없는 일방향 암호화(BCrypt)를 거쳐 저장하며, 원문은
          어디에도 보관하지 않습니다.
        </p>
      </Section>

      <Section title="제3조 (개인정보의 처리 및 보유 기간)">
        <p>
          팀은 법령에 따른 보유 기간 또는 수집 시 동의받은 보유 기간 내에서 개인정보를
          처리·보유하며, 기간이 지나면 지체 없이 파기합니다.
        </p>
        <PolicyTable
          head={['항목', '보유 기간']}
          rows={[
            ['회원 정보(이메일·닉네임 등)', '회원 탈퇴(삭제 요청 포함) 시까지'],
            [
              '저장 콘텐츠(링크·메모·이미지·위치정보·AI 산출물)',
              '이용자가 삭제할 때까지. 삭제 시 휴지통으로 이동하며, 휴지통에서 영구 삭제하면 첨부 파일을 포함해 즉시 파기',
            ],
            ['로그인 유지 정보(재발급 토큰)', '최대 14일 (새로 로그인하면 기존 토큰 즉시 무효화)'],
            ['푸시 토큰·브라우저 정보', '알림 등록 해제 또는 회원 탈퇴 시까지'],
            ['웹서버 접속 기록', '3개월'],
          ]}
        />
      </Section>

      <Section title="제4조 (개인정보의 제3자 제공)">
        <p>
          ① 팀은 이용자의 개인정보를 제1조의 목적 범위에서만 처리하며, 이용자의 별도 동의 또는
          법령의 특별한 규정이 있는 경우를 제외하고는 제3자에게 제공하지 않습니다.
        </p>
        <p>
          ② 다만 이용자가 워크스페이스 초대 링크를 다른 사람에게 공유하여 그 사람이 워크스페이스에
          참여하면, 참여한 멤버는 해당 워크스페이스에 저장된 모든 항목(본문·위치정보 포함)을 열람할
          수 있습니다. 이는 이용자의 선택에 따른 공개이므로 초대 링크 공유 시 유의하시기 바랍니다.
        </p>
      </Section>

      <Section title="제5조 (개인정보 처리의 위탁 및 국외 이전)">
        <p>
          ① 서비스의 핵심 기능인 AI 처리 등을 위해 아래와 같이 개인정보 처리를 위탁하고 있으며, 일부
          수탁자는 국외 사업자입니다. 국외 이전은 해당 기능을 이용하는 시점에 정보통신망을 통해
          수시로 이루어집니다.
        </p>
        <PolicyTable
          head={['수탁자 (국가)', '위탁 업무', '이전되는 정보', '보유·이용 기간']}
          rows={[
            [
              'OpenAI, L.L.C. (미국)',
              'AI 검색어 해석',
              'AI 검색 이용 시 입력한 검색 질의문 (SSAFY GMS API 중계 서버를 경유해 전송)',
              '처리 완료 후 수탁자의 데이터 보존 정책에 따라 파기',
            ],
            [
              'OpenRouter, Inc. (미국)',
              '콘텐츠 요약·자동 분류·이미지 분석·검색용 임베딩 생성',
              '저장한 페이지의 본문·제목, 메모 원문, 업로드한 이미지, 의미 검색 시 검색어',
              '처리 완료 후 수탁자의 데이터 보존 정책에 따라 파기',
            ],
            [
              'Google LLC (미국)',
              '소셜 로그인(OAuth), 푸시 알림 발송(Firebase)',
              '로그인 시 구글 계정 정보, 알림 발송 시 푸시 토큰·알림 내용',
              '위탁 업무 종료 시까지',
            ],
            [
              '주식회사 카카오 (대한민국)',
              '좌표의 주소 변환(지오코딩)',
              '저장 항목에서 추출된 위도·경도 좌표 (계정 정보는 포함되지 않음)',
              '처리 완료 즉시 목적 달성',
            ],
          ]}
        />
        <p>
          ② AI 처리·소셜 로그인·푸시 알림은 서비스의 본질적 기능이므로, 국외 이전을 거부하실 경우
          해당 기능 또는 서비스 전체의 이용이 제한될 수 있습니다. 이전을 원하지 않으시면 제10조의
          문의처로 연락해 주시기 바랍니다.
        </p>
      </Section>

      <Section title="제6조 (개인정보의 파기 절차 및 방법)">
        <p>
          ① 팀은 보유 기간이 지났거나 처리 목적이 달성된 개인정보를 지체 없이 파기합니다. 전자적
          파일은 복구할 수 없는 방법으로 삭제하며, 데이터베이스 기록과 저장소의 원본 파일을 함께
          삭제합니다.
        </p>
        <p>
          ② 회원 탈퇴 기능은 현재 앱 안에서 제공을 준비하고 있습니다. 탈퇴(계정과 저장 콘텐츠 전체
          삭제)를 원하시면 제10조의 문의처로 요청해 주시기 바라며, 요청을 받은 날부터 지체 없이
          파기합니다.
        </p>
      </Section>

      <Section title="제7조 (정보주체의 권리·의무 및 행사 방법)">
        <p>
          ① 이용자는 언제든지 자신의 개인정보에 대한 열람·정정·삭제·처리정지를 요구할 수 있습니다.
        </p>
        <ul className="flex list-disc flex-col gap-1 pl-5">
          <li>
            앱 내: 마이페이지에서 프로필 열람·수정, 저장 항목 삭제(휴지통 이동·영구 삭제), 알림 해제
          </li>
          <li>그 외 요구: 제10조의 문의처로 연락하시면 지체 없이 조치합니다.</li>
        </ul>
        <p>
          ② 서비스는 만 14세 미만 아동의 가입을 받지 않으며, 만 14세 미만 아동의 개인정보가 수집된
          사실이 확인되면 지체 없이 파기합니다.
        </p>
      </Section>

      <Section title="제8조 (개인정보의 안전성 확보 조치)">
        <ol className="flex list-decimal flex-col gap-1 pl-5">
          <li>비밀번호의 일방향 암호화(BCrypt) 저장 — 원문 복호화 불가</li>
          <li>전 구간 HTTPS(TLS) 암호화 전송</li>
          <li>데이터베이스·파일 저장소는 외부에서 직접 접근할 수 없는 내부 네트워크에 격리</li>
          <li>업로드한 이미지는 60분 동안만 유효한 서명 URL로만 접근 가능</li>
          <li>
            인증 토큰의 단기 만료(접근 토큰 1시간, 재발급 토큰 14일) 및 새 로그인 시 기존 재발급
            토큰 즉시 무효화
          </li>
          <li>인증 토큰에 회원 번호 외의 개인정보(이메일·닉네임 등) 미포함</li>
        </ol>
      </Section>

      <Section title="제9조 (쿠키 등 자동 수집 장치)">
        <p>① 서비스는 쿠키를 사용하지 않으며, 광고·행태분석 도구도 사용하지 않습니다.</p>
        <p>
          ② 로그인 상태 유지를 위해 인증 토큰을 이용자의 브라우저 저장 공간(localStorage)에
          보관하며, 로그아웃하면 삭제됩니다. 공용 PC에서는 이용 후 반드시 로그아웃해 주시기
          바랍니다.
        </p>
        <p>
          ③ 지도 화면을 여는 경우 브라우저가 지도 타일 제공자(OpenFreeMap, 국외)에 직접 접속하며, 이
          과정에서 IP 주소와 조회한 지도 영역 정보가 해당 제공자에게 전달됩니다.
        </p>
      </Section>

      <Section title="제10조 (개인정보 보호책임자 및 문의처)">
        <p>
          개인정보 처리에 관한 문의·불만·피해구제 요청은 아래 문의처로 연락해 주시기 바랍니다. 팀은
          지체 없이 답변하고 처리합니다.
        </p>
        <ul className="flex list-disc flex-col gap-1 pl-5">
          <li>개인정보 보호책임자: 최동준 (우주인 팀 · SSAFY 15기 공통프로젝트 C105)</li>
          <li>
            이메일:{' '}
            <a href="mailto:woojuin105@gmail.com" className="text-accent hover:text-accent-hover">
              woojuin105@gmail.com
            </a>
          </li>
        </ul>
      </Section>

      <Section title="제11조 (권익침해에 대한 구제 방법)">
        <p>
          개인정보 침해에 대한 신고·상담이 필요하신 경우 아래 기관에 문의하실 수 있습니다. (아래
          기관은 팀과는 별개의 기관입니다.)
        </p>
        <ul className="flex list-disc flex-col gap-1 pl-5">
          <li>개인정보침해 신고센터 (privacy.kisa.or.kr / 국번 없이 118)</li>
          <li>개인정보 분쟁조정위원회 (kopico.go.kr / 1833-6972)</li>
          <li>대검찰청 사이버수사과 (spo.go.kr / 국번 없이 1301)</li>
          <li>경찰청 사이버수사국 (ecrm.police.go.kr / 국번 없이 182)</li>
        </ul>
      </Section>

      <Section title="제12조 (개인정보처리방침의 변경)">
        <p>
          이 방침의 내용이 추가·삭제·수정되는 경우 시행 7일 전(이용자 권리의 중요한 변경은 30일
          전)에 서비스 내 공지로 알립니다.
        </p>
        <p>부칙: 이 개인정보처리방침은 2026년 8월 1일부터 시행됩니다.</p>
      </Section>
    </main>
  </div>
);

/** 조항 하나 — 제목은 "제n조 (…)" 형식으로 통일한다 */
const Section = ({ title, children }: { title: string; children: ReactNode }) => (
  <section className="mt-10">
    <h2 className="text-[15px] font-bold text-text-1">{title}</h2>
    <div className="mt-3 flex flex-col gap-3 text-body leading-relaxed text-text-2">{children}</div>
  </section>
);

/** 보유 기간·위탁 현황 표 — 좁은 화면에서는 표 안에서만 가로 스크롤한다 */
const PolicyTable = ({ head, rows }: { head: string[]; rows: string[][] }) => (
  <div className="overflow-x-auto rounded-md border border-border-soft">
    <table className="w-full min-w-[560px] border-collapse text-[13px]">
      <thead>
        <tr className="bg-surface text-left text-text-1">
          {head.map((label) => (
            <th key={label} className="px-3 py-2 font-semibold">
              {label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row[0]} className="align-top">
            {row.map((cell) => (
              <td key={cell} className="border-t border-border-soft px-3 py-2">
                {cell}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);

export default PrivacyPolicyPage;
