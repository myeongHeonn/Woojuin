from __future__ import annotations
import argparse, json, platform, sys
from datetime import datetime
from pathlib import Path
import yaml
from src.auto_evaluator import evaluate
from src.dataset_loader import load_categories, load_dataset, load_memo_dataset, validate_expected_categories
from src.ollama_client import OllamaClient, OllamaError, performance
from src.prompt_builder import build_prompt, load_prompt
from src.report_generator import generate_auto_report
from src.response_parser import output_schema, parse_response
from src.result_writer import append_jsonl, write_csv

ROOT=Path(__file__).resolve().parent
def args():
    p=argparse.ArgumentParser(); p.add_argument('--models',nargs='+'); p.add_argument('--test-ids',nargs='+'); p.add_argument('--repeat',type=int)
    p.add_argument('--include-memos',action='store_true',help=argparse.SUPPRESS)
    p.add_argument('--include-json',action='store_true',help='메모 데이터와 기존 JSON 회귀 데이터를 함께 실행')
    p.add_argument('--memo-only',action='store_true',help='dataset/memo의 메모만 실행')
    return p.parse_args()
def model_installed(wanted: str, installed: list[str]) -> bool: return wanted in installed or any(x.split(':')[0]==wanted for x in installed)
def flat(record: dict) -> dict:
    p=record.get('parsedResponse') or {}; e=record.get('evaluation') or {}; perf=record.get('performance') or {}
    return {"testId":record['testId'],"title":record.get('title',''),"sourcePath":record.get('sourcePath',''),"idAutoAssigned":record.get('idAutoAssigned',False),"model":record['model'],"runType":record['runType'],"runNumber":record['runNumber'],"promptVersion":record['promptVersion'],"generatedSummary":p.get('summary',''),"generatedCategory":p.get('category',''),"generatedTags":" | ".join(p.get('tags',[])),"generatedKeywords":" | ".join(p.get('keywords',[])),**e,**perf,"requestFailed":record['error']['requestFailed'],"errorType":record['error']['errorType'],"errorMessage":record['error']['errorMessage'],"executedAt":record['executedAt']}
def representative(rows: list[dict], datasets: dict[str,dict]) -> list[dict]:
    selected={}
    for r in rows:
        if r['runType']=='WARM' and not r['requestFailed'] and (r['model'],r['testId']) not in selected: selected[(r['model'],r['testId'])]=r
    result=[]
    for (model,tid),r in selected.items(): result.append({"testId":tid,"model":model,"input":datasets[tid]['input'],"expectedCategory":" | ".join(datasets[tid]['expected']['categories']),**r})
    return result
def manual_rows(reps: list[dict]) -> list[dict]:
    fields=("testId","model","title","input","expectedCategory","generatedSummary","generatedCategory","generatedTags","generatedKeywords","categoryCorrect","requiredKeywordRecall","forbiddenClaimCount")
    return [{**{k:r.get(k,'') for k in fields},"summaryQuality":"","factuality":"","tagQuality":"","readability":"","hallucinationLevel":"","reviewer":"","reviewNote":""} for r in reps]
def main() -> int:
    a=args(); cfg=yaml.safe_load((ROOT/'config.yaml').read_text(encoding='utf-8')); models=a.models or cfg['models']; repeat=a.repeat or int(cfg['repeatCount'])
    if sys.version_info < (3,11): print('[FAIL] Python 3.11 이상이 필요합니다.'); return 2
    print(f"[OK] Python {platform.python_version()}")
    try:
        categories=load_categories(ROOT/'dataset'/'memo')
        memo_data=load_memo_dataset(ROOT/'dataset'/'memo', categories)
        json_data=load_dataset(ROOT/'dataset'/'text-test-data.json') if (a.include_json or a.include_memos) else []
        if json_data: validate_expected_categories(json_data, categories)
    except (OSError, ValueError) as exc:
        print(f'[FAIL] 테스트 데이터 검증 실패: {exc}'); return 2
    data=memo_data if a.memo_only or not json_data else json_data + memo_data
    prompt=load_prompt(ROOT/'prompts'/'text-prompt-v1.txt')
    if not data:
        print('[FAIL] 실행할 테스트 데이터가 없습니다.'); return 2
    if a.test_ids:
        unknown=set(a.test_ids)-{x['testId'] for x in data}
        if unknown: print(f"[FAIL] 없는 testId: {sorted(unknown)}"); return 2
        data=[x for x in data if x['testId'] in a.test_ids]
    print(f"[OK] 테스트 데이터 {len(data)}개 로드"); print(f"[OK] 프롬프트 버전 {cfg['promptVersion']}")
    client=OllamaClient(cfg['ollamaBaseUrl'],cfg['connectTimeoutSeconds'],cfg['readTimeoutSeconds'])
    try: installed=client.models(); print('[OK] Ollama 연결 성공')
    except OllamaError as exc: print(f"[FAIL] Ollama 연결 실패 ({exc.error_type}): {exc}"); return 3
    missing=[m for m in models if not model_installed(m,installed)]
    if missing:
        for m in missing: print(f"모델 {m}가 설치되어 있지 않습니다.\n다음 명령어를 실행해주세요.\n\nollama pull {m}")
        return 4
    for m in models: print(f"[OK] {m} 설치 확인")
    out=ROOT/'results'/datetime.now().strftime('%Y%m%d-%H%M%S'); (out/'raw').mkdir(parents=True); (out/'detail').mkdir(); print(f"[OK] 결과 디렉터리: {out}")
    raw_path=out/'raw'/'results.jsonl'; flat_rows=[]; total_models=len(models)
    for mi,model in enumerate(models,1):
        print(f"[{mi}/{total_models} 모델] {model}"); client.unload(model)
        runs=[('COLD',0,data[0])]+[('WARM',n,item) for item in data for n in range(1,repeat+1)]
        for run_type,n,item in runs:
            di=data.index(item)+1; print(f"[{di}/{len(data)} 데이터] {item['testId']}\n[{n if n else 1}/{repeat} 반복] {run_type}")
            request={}; response={}; info=parse_response('',categories); err={"requestFailed":False,"errorType":"","errorMessage":""}; perf={}
            try:
                request,response=client.chat(model,build_prompt(prompt,item['input'],categories,item.get('title','')),cfg['temperature'],cfg['keepAlive'],output_schema(categories)); raw=response.get('message',{}).get('content',''); info=parse_response(raw,categories); perf=performance(response)
                if not raw.strip(): err={"requestFailed":True,"errorType":"EMPTY_RESPONSE","errorMessage":"빈 응답"}
                elif not info['jsonValid']: err={"requestFailed":True,"errorType":"INVALID_JSON","errorMessage":info['validationError']}
                elif not info['schemaValid']: err={"requestFailed":True,"errorType":"SCHEMA_VALIDATION_FAILED","errorMessage":info['validationError']}
            except OllamaError as exc: err={"requestFailed":True,"errorType":exc.error_type,"errorMessage":str(exc)}
            ev=evaluate(info,item['expected'],categories); rec={"testId":item['testId'],"title":item.get('title',''),"sourcePath":item.get('sourcePath',''),"idAutoAssigned":item.get('idAutoAssigned',False),"model":model,"runType":run_type,"runNumber":n,"promptVersion":cfg['promptVersion'],"request":request,"ollamaResponse":{k:response.get(k) for k in ('model','created_at','done','done_reason','total_duration','load_duration','prompt_eval_count','prompt_eval_duration','eval_count','eval_duration')},"rawResponse":info['rawResponse'],"parsedResponse":info['parsedResponse'],"evaluation":ev,"performance":perf,"error":err,"executedAt":datetime.now().isoformat()}; append_jsonl(raw_path,rec); f=flat(rec); flat_rows.append(f)
            print(f"상태: {'FAILED' if err['requestFailed'] else 'SUCCESS'}\nJSON: {'VALID' if info['jsonValid'] else 'INVALID'}\n카테고리: {'정답' if ev['categoryCorrect'] else '오답'}\n응답 시간: {perf.get('totalDurationMs',0):.0f}ms")
    write_csv(out/'detail'/'auto-evaluation.csv',flat_rows); reps=representative(flat_rows,{x['testId']:x for x in data}); write_csv(out/'detail'/'representative-results.csv',reps); write_csv(out/'manual-evaluation.csv',manual_rows(reps)); generate_auto_report(flat_rows,out/'ai-text-comparison-auto.md'); print(f"완료: {out}"); return 0
if __name__=='__main__': raise SystemExit(main())
