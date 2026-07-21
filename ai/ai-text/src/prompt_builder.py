from pathlib import Path
import re

DEFAULT_TITLE_PATTERN = re.compile(r"^텍스트-\d{4}\.\d{2}\.\d{2}-\d{4}$")
def load_prompt(path: Path) -> str: return path.read_text(encoding="utf-8")
def build_prompt(template: str, content: str, categories: list[str], title: str = "") -> str:
    if "{{CONTENT}}" not in template: raise ValueError("프롬프트에 {{CONTENT}}가 없습니다.")
    if "{{CATEGORIES}}" not in template: raise ValueError("프롬프트에 {{CATEGORIES}}가 없습니다.")
    if "{{TITLE_CONTEXT}}" not in template: raise ValueError("프롬프트에 {{TITLE_CONTEXT}}가 없습니다.")
    meaningful_title = title.strip() if title and not DEFAULT_TITLE_PATTERN.fullmatch(title.strip()) else ""
    title_context = f"입력 제목:\n{meaningful_title}\n\n" if meaningful_title else ""
    return (template.replace("{{CATEGORIES}}", ", ".join(categories))
            .replace("{{TITLE_CONTEXT}}", title_context).replace("{{CONTENT}}", content))
