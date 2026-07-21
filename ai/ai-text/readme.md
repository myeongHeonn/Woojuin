# AI 텍스트 모델 테스트 도구

Ollama 모델의 텍스트 분류·요약·검색 메타데이터 생성 성능을 서로 분리하거나 실제 서비스 출력 형태로 통합해 평가합니다. Python 3.11 이상과 실행 중인 Ollama가 필요합니다.

## 준비

```powershell
cd C:\Users\SSAFY\IdeaProjects\S15P11C105\ai\ai-text
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
ollama pull qwen3:4b
ollama pull qwen3:8b
```

현재 PC처럼 Python 3.14만 설치된 경우 `py -3.14`를 사용해도 됩니다.

## 테스트 모드

| 모드 | 목적 | 모델 출력 |
|---|---|---|
| `category-only` | 순수 카테고리 분류와 confidence 평가 | `category`, `confidence` |
| `summary-only` | 요약 생성 품질 평가 | `summary` |
| `metadata-only` | 검색용 태그·키워드 생성 평가 | `tags`, `keywords` |
| `integrated` | 실제 서비스용 통합 출력 검증 | `summary`, `category`, `tags`, `keywords` |

모드별 JSON Schema와 프롬프트는 분리되어 있습니다. 요청하지 않은 필드를 모델이 추가하면 스키마 실패입니다. 모드를 지정하지 않으면 기존 동작과 호환되도록 `integrated`를 사용합니다.

```powershell
# 순수 분류
.\.venv\Scripts\python.exe run_tests.py --category-only --models qwen3:4b --memo-only --repeat 1

# 요약
.\.venv\Scripts\python.exe run_tests.py --summary-only --models qwen3:4b --memo-only --repeat 1

# 태그·키워드
.\.venv\Scripts\python.exe run_tests.py --metadata-only --models qwen3:4b --memo-only --repeat 1

# 통합 출력
.\.venv\Scripts\python.exe run_tests.py --integrated --models qwen3:4b --memo-only --repeat 1

# 기존 명령도 integrated로 실행
.\.venv\Scripts\python.exe run_tests.py --models qwen3:4b --memo-only --repeat 1
```

네 모드를 한 명령으로 순서대로 실행할 수도 있습니다.

```powershell
.\.venv\Scripts\python.exe run_tests.py `
  --all-modes `
  --models qwen3:4b qwen3:8b `
  --memo-only `
  --repeat 1
```

`--all-modes`는 category-only → summary-only → metadata-only → integrated 순서로 실행합니다. 한 개의 상위 실행 폴더 안에 모드별 하위 폴더 4개와 종합 비교 보고서를 생성합니다.

```text
results/20260721-143000-all-modes/
├── category-only/
├── summary-only/
├── metadata-only/
├── integrated/
├── comparison-report.md
└── comparison-summary.json
```

한 모드가 실패하거나 `Ctrl+C`로 중단되면 다음 모드는 실행하지 않으며, 이미 완료된 모드 결과는 상위 폴더에 유지됩니다. 개별 모드 플래그와 `--all-modes`는 동시에 사용할 수 없습니다.

특정 데이터만 빠르게 확인할 수 있습니다.

```powershell
.\.venv\Scripts\python.exe run_tests.py --category-only --models qwen3:4b --test-ids TEXT-001 --repeat 1
```

각 모델은 첫 데이터로 COLD 측정을 한 번 수행한 뒤 전체 데이터를 WARM으로 실행합니다. 데이터 23개, 반복 1회라면 모델당 호출 수는 `COLD 1 + WARM 23 = 24`입니다. 품질 지표는 WARM 결과로 계산하고 COLD 결과는 로딩 성능 측정에 보존합니다.

## 데이터셋

기본값과 `--memo-only`는 `dataset/memo/<카테고리>/*.txt`의 메모 23개를 사용합니다. 카테고리 정확도의 정답은 파일의 상위 폴더명입니다. `텍스트-YYYY.MM.DD-HHmm` 형식의 기본 제목은 모델 입력에서 제외하고 의미 있는 제목만 전달합니다.

`config/categories.json`에는 11개 카테고리의 이름, 설명, 대표 예시가 있습니다. 폴더명과 정의 파일의 카테고리 이름이 다르면 실행을 중단합니다.

`--include-json`은 메모와 `dataset/text-test-data.json`을 함께 사용합니다. JSON 회귀 데이터에는 다음 의미 정답이 있으므로 요약·통합 모드의 상세 자동 평가에 적합합니다.

- `requiredKeywords`: 결과에 포함돼야 할 핵심 키워드
- `summaryPoints`: 요약에 포함돼야 할 핵심 내용
- `forbiddenClaims`: 생성하면 안 되는 원문 밖 주장

```powershell
.\.venv\Scripts\python.exe run_tests.py --summary-only --models qwen3:4b --include-json --repeat 1
```

## 평가 지표

모든 모드는 응답 성공률, JSON 성공률, 스키마 성공률, 출력 제약 준수율, 평균·중앙값·P95·최소·최대 응답 시간, 평균 TPS, 토큰 수, 모델 로딩 시간과 순수 생성 시간을 기록합니다.

`category-only`와 `integrated`는 Accuracy, Macro Precision, Macro Recall, Macro F1, 카테고리별 Precision·Recall·F1과 혼동 행렬을 생성합니다. `category-only`는 전체·정답·오답 confidence 평균과 confidence 구간별 정확도도 계산합니다.

`summary-only`와 `integrated`는 정답이 있을 때 필수 키워드 재현율, 요약 핵심 내용 재현율과 금지 표현 발생률을 계산합니다. `metadata-only`는 태그·키워드 개수 및 중복 제약과 생성 키워드가 원문에 직접 존재하는 비율을 계산합니다.

다음 차이를 반드시 구분해야 합니다.

- `0%`: 정답 데이터가 있고 실제 측정 결과가 0인 경우
- `N/A`: 정답 데이터가 없거나 해당 모드에서 평가하지 않는 경우

스키마 성공률 100%는 요약과 태그의 의미 품질이 완벽하다는 뜻이 아닙니다.

`requiredKeywords`, `summaryPoints`, `forbiddenClaims`가 비어 있으면 해당 의미 평가는 수행되지 않습니다.

카테고리 정확도는 파일의 상위 폴더명을 정답으로 사용합니다.

## 결과 파일

실행마다 모드가 포함된 새 폴더를 생성하며 기존 결과를 덮어쓰지 않습니다.

```text
results/20260721-143000-category-only/
├── run-metadata.json
├── raw-responses.json
├── evaluation-results.json
├── category-metrics.json       # 분류 모드만
├── confusion-matrix.csv        # 분류 모드만
├── manual-evaluation.csv
├── report.md
├── raw/results.jsonl           # 기존 도구 호환 원본
└── detail/
    ├── auto-evaluation.csv
    └── representative-results.csv
```

실패 응답도 오류 정보와 함께 `raw-responses.json`과 JSONL에 남습니다. 실행 중 `Ctrl+C`로 중단하면 완료된 호출을 기반으로 부분 결과와 보고서를 생성하고 `run-metadata.json`의 상태를 `INTERRUPTED`로 기록합니다.

## 모델 결과 비교

두 개 이상의 새 형식 결과 폴더를 비교할 수 있습니다.

```powershell
.\.venv\Scripts\python.exe run_tests.py --compare-results `
  results\20260721-143000-category-only `
  results\20260721-160000-category-only
```

`results/comparison-실행시각/`에 `comparison-report.md`와 `comparison-summary.json`이 생성됩니다. 평가하지 않은 항목은 `0`이 아닌 `N/A`로 표시됩니다.

## 수동 평가

모드별 `manual-evaluation.csv`에 사람이 평가할 항목만 생성됩니다.

- summary-only: `summaryQuality`, `factuality`, `readability`, `hallucinationLevel`
- metadata-only: `tagQuality`, `keywordQuality`, `searchUsefulness`
- integrated: `summaryQuality`, `factuality`, `tagQuality`, `readability`, `hallucinationLevel`

각 품질 점수는 1~5점으로 작성하고 `hallucinationLevel`은 `NONE`, `MINOR`, `MAJOR` 중 하나를 사용합니다. `reviewer`와 `reviewNote`에 평가자와 근거를 기록합니다.

## 공정한 모델 비교 조건

동일한 데이터셋, 프롬프트 버전, temperature, seed, context length, 반복 횟수와 하드웨어를 사용해야 합니다. `config.yaml`의 공통 옵션을 바꾸지 않고 모델만 바꾸는 방식을 권장합니다. COLD 로딩 시간과 WARM 응답 시간을 구분하고, 의미 품질은 자동 지표와 수동 평가를 함께 확인합니다.

## 코드 테스트

실제 Ollama 호출 없이 평가 로직을 검증합니다.

```powershell
.\.venv\Scripts\python.exe -m pytest
```
