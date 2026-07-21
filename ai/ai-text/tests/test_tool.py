import csv, json
from pathlib import Path
import pytest
from src.auto_evaluator import evaluate, speed_scores
from src.dataset_loader import load_categories, load_memo_dataset
from src.prompt_builder import build_prompt
from src.report_generator import generate_final_report
from src.response_parser import output_schema, parse_response

VALID={"summary":"S3 업로드와 파일명 중복 처리를 구현한다.","category":"개발·IT","tags":["Spring Boot","S3","업로드"],"keywords":["이미지 업로드","파일명","중복 처리"]}
CATEGORIES=["개발·IT","학습·지식","업무·프로젝트","취업·커리어","생활·할 일","쇼핑·제품","음식·맛집","여행·장소","건강·운동","문화·콘텐츠","기타"]
def raw(value=VALID): return json.dumps(value,ensure_ascii=False)
def test_normal_json(): assert parse_response(raw(),CATEGORIES)["schemaValid"]
def test_markdown_json(): assert parse_response(f"```json\n{raw()}\n```",CATEGORIES)["jsonValid"]
def test_extra_text():
    r=parse_response("설명\n"+raw()+"\n끝",CATEGORIES); assert r["jsonValid"] and r["extraTextDetected"]
def test_think_tag():
    r=parse_response("<think>비공개 사고</think>"+raw(),CATEGORIES); assert r["schemaValid"] and r["thinkingTagDetected"]
@pytest.mark.parametrize("change",[
    {"keywords":None},{"category":"미분류"},{"tags":["a","b"]},{"tags":["a","a","b"]}
])
def test_schema_failures(change):
    value=VALID.copy()
    if change.get("keywords", "present") is None: value.pop("keywords")
    else: value.update(change)
    assert not parse_response(raw(value),CATEGORIES)["schemaValid"]
def test_keyword_recall_and_forbidden():
    info=parse_response(raw(),CATEGORIES); expected={"categories":["개발·IT"],"requiredKeywords":["S3","없는 말"],"summaryPoints":[],"forbiddenClaims":["파일명 중복 처리"]}
    e=evaluate(info,expected,CATEGORIES); assert e["requiredKeywordRecall"]==.5 and e["possibleHallucination"]
def test_speed_score(): assert speed_scores({"a":100,"b":200})=={"a":5.0,"b":2.5}

def test_category_folders_drive_prompt_and_schema(tmp_path):
    (tmp_path / "새 카테고리").mkdir()
    (tmp_path / "기타").mkdir()
    categories = load_categories(tmp_path)
    prompt = build_prompt("분류: {{CATEGORIES}}\n{{TITLE_CONTEXT}}내용: {{CONTENT}}", "메모", categories)
    schema = output_schema(categories)
    assert "새 카테고리" in prompt
    assert schema["properties"]["category"]["enum"] == categories
    assert "title" not in schema["properties"]

def test_default_title_is_excluded_and_user_title_is_included():
    template = "분류: {{CATEGORIES}}\n{{TITLE_CONTEXT}}내용: {{CONTENT}}"
    default_prompt = build_prompt(template, "본문", CATEGORIES, "텍스트-2026.07.20-0905")
    user_prompt = build_prompt(template, "본문", CATEGORIES, "JWT 재발급 구조 정리")
    assert "텍스트-2026.07.20-0905" not in default_prompt
    assert "입력 제목:\nJWT 재발급 구조 정리" in user_prompt

def test_load_memos_with_explicit_and_automatic_ids(tmp_path):
    development = tmp_path / "개발·IT"
    career = tmp_path / "취업·커리어"
    development.mkdir(); career.mkdir()
    (development / "002-S3 업로드.txt").write_text("S3 업로드 메모", encoding="utf-8")
    (career / "면접 준비.txt").write_text("백엔드 면접 준비", encoding="utf-8")
    rows = load_memo_dataset(tmp_path)
    by_path = {row["sourcePath"]: row for row in rows}
    assert by_path["개발·IT/002-S3 업로드.txt"]["testId"] == "TEXT-002"
    assert by_path["개발·IT/002-S3 업로드.txt"]["idAutoAssigned"] is False
    assert by_path["취업·커리어/면접 준비.txt"]["testId"] == "TEXT-001"
    assert by_path["취업·커리어/면접 준비.txt"]["idAutoAssigned"] is True
    assert by_path["취업·커리어/면접 준비.txt"]["expected"]["categories"] == ["취업·커리어"]

def test_duplicate_memo_id_is_rejected(tmp_path):
    development = tmp_path / "개발·IT"
    career = tmp_path / "취업·커리어"
    development.mkdir(); career.mkdir()
    (development / "001-첫 메모.txt").write_text("첫 메모", encoding="utf-8")
    (career / "001-둘째 메모.txt").write_text("둘째 메모", encoding="utf-8")
    with pytest.raises(ValueError, match="중복 메모 ID"):
        load_memo_dataset(tmp_path)
def _write_csv(path: Path, rows: list[dict]):
    with path.open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.DictWriter(f,fieldnames=rows[0]); w.writeheader(); w.writerows(rows)
def test_manual_incomplete(tmp_path):
    (tmp_path/'detail').mkdir(); _write_csv(tmp_path/'manual-evaluation.csv',[{"model":"m","summaryQuality":"","factuality":"","tagQuality":"","readability":"","hallucinationLevel":""}])
    assert not generate_final_report(tmp_path)
def test_manual_complete(tmp_path):
    (tmp_path/'detail').mkdir(); _write_csv(tmp_path/'manual-evaluation.csv',[{"model":"m","summaryQuality":"5","factuality":"5","tagQuality":"5","readability":"5","hallucinationLevel":"NONE"}])
    _write_csv(tmp_path/'detail'/'auto-evaluation.csv',[{"model":"m","runType":"WARM","requestFailed":"False","totalDurationMs":"100","categoryCorrect":"True","requiredKeywordRecall":"1","schemaValid":"True","tokensPerSecond":"10"}])
    assert generate_final_report(tmp_path) and (tmp_path/'ai-text-comparison-final.csv').exists()
    rows = list(csv.DictReader((tmp_path/'ai-text-comparison-final.csv').open(encoding='utf-8-sig')))
    assert float(rows[0]['total']) == 100.0
