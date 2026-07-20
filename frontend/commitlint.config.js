export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    // docs/CONVENTIONS.md 의 타입 목록과 일치
    'type-enum': [
      2,
      'always',
      ['feat', 'fix', 'docs', 'refactor', 'test', 'chore', 'style', 'perf'],
    ],
    'subject-case': [0], // 한글 제목 허용 (대소문자 규칙 끔)
    'subject-full-stop': [2, 'never', '.'], // 제목 끝 마침표 금지
  },
};
