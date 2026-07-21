# 로컬 LLM 텍스트 처리 비교 도구

Ollama의 텍스트 모델에 동일한 한국어 데이터와 프롬프트를 입력해 JSON 안정성, 정확도, 환각 가능성, 속도를 비교하는 독립 도구입니다. 이미지·OCR·임베딩·외부 API·서비스 연동은 포함하지 않습니다.

## 설치 (Windows PowerShell)

```powershell
cd tools/ai-text-test
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
ollama --version
ollama pull qwen3:4b
ollama pull qwen3:8b
```

Ollama 서버가 실행 중이어야 합니다. 모델은 자동 다운로드하지 않습니다.

## 실행

```powershell
python run_tests.py
python run_tests.py --models qwen3:4b
python run_tests.py --models qwen3:4b qwen3:8b
python run_tests.py --test-ids TEXT-001 TEXT-002
python run_tests.py --models qwen3:4b --test-ids TEXT-001 --repeat 1
python -m pytest
```

AI 출력 스키마는 `summary`, `category`, `tags`, `keywords`만 포함합니다. 제목은 메모 파일명에서 읽어 결과 확인용 메타데이터로만 저장하며 AI가 생성하거나 평가하지 않습니다. `텍스트-YYYY.MM.DD-HHmm` 형식의 서버 기본 제목은 AI 입력에서 제외되고, 그 외 사용자 제목만 참고 정보로 프롬프트에 전달됩니다.

`config.yaml`에서 모델, 반복 횟수, temperature, Ollama 주소, 연결/읽기 제한시간, keep-alive, 프롬프트 버전과 참고 모델 크기를 바꿀 수 있습니다. Chat API에는 Pydantic에서 만든 JSON Schema를 structured output의 `format`으로 전달합니다. 구형 Ollama가 스키마를 지원하지 않으면 `src/ollama_client.py`의 `format`을 `"json"`으로 바꿔야 합니다.

## 데이터와 결과

### 카테고리별 실제 메모

실제 메모는 `dataset/memo/<카테고리>/` 아래의 UTF-8 `.txt` 파일로 저장합니다. 현재 폴더는 `개발·IT`, `학습·지식`, `업무·프로젝트`, `취업·커리어`, `생활·할 일`, `쇼핑·제품`, `음식·맛집`, `여행·장소`, `건강·운동`, `문화·콘텐츠`, `기타`입니다. 폴더명이 자동 평가의 기대 카테고리가 됩니다.

카테고리 목록은 코드에 고정되어 있지 않습니다. 실행할 때 `dataset/memo`의 1단계 하위 폴더를 읽어 공통 프롬프트의 `{{CATEGORIES}}`, Ollama structured output JSON Schema의 category enum, 응답 검증에 동일하게 적용합니다. 폴더를 추가하거나 이름을 바꾸면 별도 코드 수정 없이 반영됩니다. 단, `text-test-data.json`의 데이터별 기대 카테고리는 정답이므로 자동 추론하지 않으며, 현재 폴더에 없는 기대값이 있으면 시작 검증에서 오류로 알려줍니다.

```text
dataset/memo/
├── 개발·IT/
│   ├── 001-S3 이미지 업로드.txt
│   └── Docker 이미지 최적화.txt
└── 취업·커리어/
    └── 003-백엔드 면접 준비.txt
```

권장 파일명은 `{번호}-{자유 제목}.txt`입니다. 명시된 번호는 전체 카테고리에서 고유해야 하며 결과에는 `TEXT-001` 형태로 기록됩니다. 번호가 없는 파일은 경로를 정렬한 후 아직 사용되지 않은 가장 작은 양의 정수를 실행 중 부여합니다. 원본 파일명은 변경하지 않습니다. 파일 추가·삭제 시 자동 번호가 달라질 수 있으므로 장기 추적할 메모에는 번호를 직접 지정하십시오.

```powershell
# 기본 실행: 카테고리 폴더의 메모 22개 실행
python run_tests.py

# 기존 JSON 회귀 데이터도 함께 실행
python run_tests.py --include-json

# 메모만 실행
python run_tests.py --memo-only

# 자동 또는 명시 ID로 일부 메모만 실행
python run_tests.py --memo-only --test-ids TEXT-001 TEXT-003
```

메모 파일은 폴더 카테고리 정확도, JSON 안정성, 출력 제약, 성능 및 수동 평가 대상입니다. 별도 정답이 없는 필수 키워드·요약 핵심·금지 표현 목록은 비어 있는 상태로 평가됩니다.

`dataset/text-test-data.json`에는 20개 샘플이 있습니다. 같은 구조로 고유 `testId`, `type`, `input`, 그리고 `expected.categories`, `requiredKeywords`, `summaryPoints`, `forbiddenClaims`를 추가하십시오. 공통 프롬프트는 `prompts/text-prompt-v1.txt`이며 `{{CONTENT}}` 자리를 유지해야 합니다.

실행 결과는 `results/YYYYMMDD-HHMMSS/` 아래에 생성됩니다.

- `raw/results.jsonl`: 요청, 원본 응답, 파싱 결과, Ollama 메타데이터
- `detail/auto-evaluation.csv`: 호출별 자동 평가(UTF-8 BOM)
- `detail/representative-results.csv`: 모델·데이터별 첫 정상 웜 결과
- `manual-evaluation.csv`: 사람이 입력할 평가표
- `ai-text-comparison-auto.md`: 자동 비교 보고서

수동 평가표에 1~5점(`summaryQuality`, `factuality`, `tagQuality`, `readability`), `hallucinationLevel`(`NONE`, `MINOR`, `MAJOR`), 평가자와 의견을 입력한 뒤 실행합니다. 제목은 참고 정보이며 점수에 포함되지 않습니다.

```powershell
python generate_report.py --result-dir results\20260720-143000
```

수동 평가가 모두 채워지지 않으면 최종 점수를 계산하지 않고 `수동 평가 미완료`로 기록합니다. 완료되면 요약 품질 25점, 카테고리 20점, 태그·키워드 20점, 사실성·환각 방지 20점, JSON 안정성 10점, 응답 속도 5점으로 환산해 총 100점으로 계산합니다.

## 제한사항

핵심 표현 평가는 정규화된 부분 문자열 기반이라 의미적 동의어를 완전히 판단하지 못합니다. 금지 표현 목록 밖의 환각은 수동 평가가 필요합니다. 콜드 스타트 언로드는 Ollama 동작과 시스템 캐시에 영향을 받습니다. 외부 AI API를 추가하려면 `OllamaClient`와 동일한 반환 계약을 갖는 클라이언트를 추가하고 `run_tests.py`의 클라이언트 선택부를 확장하십시오.
