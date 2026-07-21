import re
from typing import Any
from .response_parser import sentence_count

def normalize(value: str) -> str: return re.sub(r"\s+", " ", value.strip().casefold())
def contains(haystack: str, needle: str) -> bool:
    a, b = normalize(haystack), normalize(needle)
    return b in a or a in b

def evaluate(info: dict, expected: dict, categories: list[str]) -> dict[str, Any]:
    p = info.get("parsedResponse") if isinstance(info.get("parsedResponse"), dict) else {}
    combined = " ".join(" ".join(map(str, p.get(k, []))) if isinstance(p.get(k), list) else str(p.get(k, "")) for k in ("summary", "tags", "keywords"))
    required, points = expected.get("requiredKeywords", []), expected.get("summaryPoints", [])
    forbidden = [x for x in expected.get("forbiddenClaims", []) if contains(combined, x)]
    tags, keywords = p.get("tags", []), p.get("keywords", [])
    matched = sum(contains(combined, x) for x in required); point_matched = sum(contains(str(p.get("summary", "")), x) for x in points)
    return {
      "responseReceived": bool(info.get("rawResponse", "").strip()), "jsonValid": info.get("jsonValid", False), "schemaValid": info.get("schemaValid", False), "requiredFieldsPresent": info.get("requiredFieldsPresent", False), "extraTextDetected": info.get("extraTextDetected", False), "thinkingTagDetected": info.get("thinkingTagDetected", False),
      "summarySentenceCountValid": isinstance(p.get("summary"), str) and 1 <= sentence_count(p["summary"]) <= 3, "categoryValueValid": p.get("category") in categories,
      "tagCountValid": isinstance(tags, list) and 3 <= len(tags) <= 5, "tagDuplicateFree": isinstance(tags, list) and len({normalize(str(x)) for x in tags}) == len(tags), "keywordCountValid": isinstance(keywords, list) and 3 <= len(keywords) <= 5, "keywordDuplicateFree": isinstance(keywords, list) and len({normalize(str(x)) for x in keywords}) == len(keywords),
      "categoryCorrect": p.get("category") in expected.get("categories", []), "requiredKeywordMatchedCount": matched, "requiredKeywordTotalCount": len(required), "requiredKeywordRecall": matched / len(required) if required else None, "summaryPointMatchedCount": point_matched, "summaryPointTotalCount": len(points), "summaryPointRecall": point_matched / len(points) if points else None, "forbiddenClaimCount": len(forbidden), "detectedForbiddenClaims": " | ".join(forbidden), "possibleHallucination": bool(forbidden)}

def speed_scores(average_ms: dict[str, float]) -> dict[str, float]:
    valid = [v for v in average_ms.values() if v > 0]
    if not valid: return {k: 0.0 for k in average_ms}
    fastest = min(valid)
    return {k: min(5.0, max(0.0, 5 * fastest / v)) if v > 0 else 0.0 for k, v in average_ms.items()}
