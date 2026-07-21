from collections import defaultdict
from pathlib import Path
import csv, math, statistics
from .auto_evaluator import speed_scores
from .result_writer import write_csv

def percentile(values: list[float], q: float) -> float:
    if not values: return 0.0
    values = sorted(values); index = (len(values)-1)*q; lo, hi = math.floor(index), math.ceil(index)
    return values[lo] if lo == hi else values[lo] + (values[hi]-values[lo])*(index-lo)

def _truth(value: object) -> bool:
    return value is True or (isinstance(value, str) and value.strip().lower() in ("true", "1", "yes"))
def _pct(rows: list[dict], key: str) -> float: return 100 * sum(_truth(x.get(key)) for x in rows) / len(rows) if rows else 0
def _avg(rows: list[dict], key: str) -> float:
    vals = [float(x[key]) for x in rows if x.get(key) not in (None, "")]
    return statistics.mean(vals) if vals else 0
def _table(headers: list[str], rows: list[list]) -> str:
    return "| " + " | ".join(headers) + " |\n|" + "|".join(["---"] + ["---:" for _ in headers[1:]]) + "|\n" + "\n".join("| " + " | ".join(map(str, r)) + " |" for r in rows)

def generate_auto_report(rows: list[dict], output: Path) -> None:
    groups = defaultdict(list)
    for r in rows: groups[r["model"]].append(r)
    summary, constraints, speeds, failures, hallucinations = [], [], [], [], []
    for model, items in groups.items():
        warm = [x for x in items if x["runType"] == "WARM" and not x.get("requestFailed")]
        durations = [float(x.get("totalDurationMs", 0)) for x in warm]
        summary.append([model,len(items),f"{_pct(items,'responseReceived'):.1f}%",f"{_pct(items,'jsonValid'):.1f}%",f"{_pct(items,'schemaValid'):.1f}%",f"{_pct(items,'categoryCorrect'):.1f}%",f"{_avg(items,'requiredKeywordRecall')*100:.1f}%",sum(int(x.get('forbiddenClaimCount',0)) for x in items),f"{_avg(warm,'totalDurationMs'):.1f}",f"{percentile(durations,.95):.1f}",f"{_avg(warm,'tokensPerSecond'):.1f}"])
        constraints.append([model]+[f"{_pct(items,k):.1f}%" for k in ('summarySentenceCountValid','categoryValueValid','tagCountValid','tagDuplicateFree','keywordCountValid','keywordDuplicateFree')])
        cold = [x for x in items if x["runType"] == "COLD"]
        speeds.append([model,f"{_avg(cold,'totalDurationMs'):.1f}",f"{_avg(warm,'totalDurationMs'):.1f}",f"{statistics.median(durations) if durations else 0:.1f}",f"{percentile(durations,.95):.1f}",f"{_avg(warm,'promptEvalCount'):.1f}",f"{_avg(warm,'evalCount'):.1f}",f"{_avg(warm,'tokensPerSecond'):.1f}"])
        failures.extend([[model,x['testId'],x['runNumber'],x.get('errorType',''),x.get('errorMessage','')] for x in items if x.get('requestFailed')])
        hallucinations.extend([[model,x['testId'],x.get('detectedForbiddenClaims',''),x.get('generatedSummary','')] for x in items if x.get('possibleHallucination')])
    text = "# 로컬 LLM 자동 비교 보고서\n\n## 모델별 자동 평가 요약\n\n"+_table(["모델","호출 수","응답 성공률","JSON 성공률","스키마 성공률","카테고리 정확도","필수 키워드 재현율","금지 표현 발생","평균 응답 시간(ms)","P95(ms)","평균 TPS"],summary)+"\n\n## 모델별 출력 제약 준수율\n\n"+_table(["모델","요약 문장 수","카테고리 값","태그 개수","태그 중복 없음","키워드 개수","키워드 중복 없음"],constraints)+"\n\n## 모델별 속도 비교\n\n"+_table(["모델","콜드 스타트(ms)","평균 웜 응답(ms)","중앙값","P95","입력 토큰","출력 토큰","TPS"],speeds)+"\n\n## 실패 내역\n\n"+_table(["모델","testId","실행 번호","오류 유형","오류 메시지"],failures)+"\n\n## 환각 가능성 내역\n\n"+_table(["모델","testId","감지된 금지 표현","생성 요약"],hallucinations)
    output.write_text(text+"\n", encoding="utf-8")

def generate_final_report(result_dir: Path) -> bool:
    manual = list(csv.DictReader((result_dir/"manual-evaluation.csv").open(encoding="utf-8-sig")))
    required = ("summaryQuality","factuality","tagQuality","readability","hallucinationLevel")
    complete = bool(manual) and all(all(r.get(k,"").strip() for k in required) for r in manual)
    if not complete:
        (result_dir/"ai-text-comparison-final.md").write_text("# 최종 비교 보고서\n\n수동 평가 미완료\n",encoding="utf-8")
        write_csv(result_dir/"ai-text-comparison-final.csv", [{"status":"수동 평가 미완료"}]); return False
    groups=defaultdict(list)
    for r in manual: groups[r["model"]].append(r)
    auto=list(csv.DictReader((result_dir/"detail"/"auto-evaluation.csv").open(encoding="utf-8-sig")))
    ag=defaultdict(list)
    for r in auto: ag[r["model"]].append(r)
    avgt={m:_avg([x for x in rows if x['runType']=='WARM' and x.get('requestFailed','').lower()!='true'],'totalDurationMs') for m,rows in ag.items()}; ss=speed_scores(avgt)
    scores=[]
    for m, rs in groups.items():
        summary_score=_avg(rs,'summaryQuality')/5*25; cat=_pct(ag[m],'categoryCorrect')/100*20
        tk=_avg(rs,'tagQuality')/5*10+_avg(ag[m],'requiredKeywordRecall')*10
        hall=statistics.mean({'NONE':5,'MINOR':2.5,'MAJOR':0}[x['hallucinationLevel'].upper()] for x in rs)
        fact=_avg(rs,'factuality')/5*15+hall; js=_pct(ag[m],'schemaValid')/100*10
        total=summary_score+cat+tk+fact+js+ss.get(m,0)
        scores.append({"model":m,"summary":summary_score,"category":cat,"tagKeyword":tk,"factuality":fact,"json":js,"speed":ss.get(m,0),"total":total})
    scores.sort(key=lambda x:x['total'],reverse=True); out=[]
    for i,x in enumerate(scores,1): out.append({"rank":i,**{k:(f"{v:.2f}" if isinstance(v,float) else v) for k,v in x.items()}})
    write_csv(result_dir/"ai-text-comparison-final.csv",out)
    table=[[x['rank'],x['model'],x['summary'],x['category'],x['tagKeyword'],x['factuality'],x['json'],x['speed'],x['total']] for x in out]
    manual_table=[[m,f"{_avg(rs,'summaryQuality'):.2f}",f"{_avg(rs,'factuality'):.2f}",f"{_avg(rs,'tagQuality'):.2f}",f"{_avg(rs,'readability'):.2f}","; ".join(x.get('reviewNote','') for x in rs if x.get('reviewNote',''))] for m,rs in groups.items()]
    (result_dir/"ai-text-comparison-final.md").write_text("# 최종 비교 보고서\n\n## 모델별 최종 점수\n\n"+_table(["순위","모델","요약 품질","카테고리","태그·키워드","사실성","JSON 안정성","속도","총점"],table)+"\n\n## 수동 평가 평균\n\n"+_table(["모델","요약 품질","사실성","태그 품질","가독성","주요 의견"],manual_table)+f"\n\n## 최종 판단\n\n최종 종합 점수가 가장 높은 모델: {out[0]['model']}\n",encoding="utf-8")
    return True
