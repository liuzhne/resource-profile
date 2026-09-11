"""G-4.2：文本切片纯函数。

三种策略，与 RAG_UPSERT_DESIGN.md §5 一致：
  - none        : 一篇 = 一 chunk（超长截到 4000 字符）
  - fixed_size  : window 字符滑窗，相邻 overlap 字符
  - sentence    : 按句号/问号/感叹号/双换行切句，累加到 max_chars 出 chunk

返回 list[str]。空 / 全空白输入返回空列表（调用方做 422 校验）。
"""
from __future__ import annotations

import re
from dataclasses import dataclass

CONTENT_HARD_LIMIT = 4000  # Milvus content 字段是 VARCHAR(4096)，留 buffer
MAX_CHUNKS = 200            # 单次 upsert 上限，超出报 422


@dataclass(frozen=True)
class ChunkParams:
    name: str = "none"
    window: int = 800
    overlap: int = 80
    max_chars: int = 1000


def split(text: str, params: ChunkParams) -> list[str]:
    text = text.strip()
    if not text:
        return []
    if params.name == "none":
        return [text[:CONTENT_HARD_LIMIT]]
    if params.name == "fixed_size":
        return _split_fixed(text, params.window, params.overlap)
    if params.name == "sentence":
        return _split_sentence(text, params.max_chars)
    raise ValueError(f"unknown chunk strategy: {params.name}")


def _split_fixed(text: str, window: int, overlap: int) -> list[str]:
    if window <= 0:
        raise ValueError("window must be > 0")
    if overlap < 0 or overlap >= window:
        raise ValueError("overlap must be in [0, window)")
    chunks: list[str] = []
    step = window - overlap
    for start in range(0, len(text), step):
        chunk = text[start:start + window]
        if not chunk:
            break
        chunks.append(chunk[:CONTENT_HARD_LIMIT])
        if start + window >= len(text):
            break
    return chunks


_SENT_DELIM = re.compile(r"(?<=[。！？!?])|(?:\n\n+)")


def _split_sentence(text: str, max_chars: int) -> list[str]:
    if max_chars <= 0:
        raise ValueError("max_chars must be > 0")
    raw = [s.strip() for s in _SENT_DELIM.split(text) if s and s.strip()]
    chunks: list[str] = []
    buf = ""
    for sent in raw:
        if len(sent) > max_chars:
            # 句子本身过长 —— 强制按 max_chars 切，不再贴前后
            if buf:
                chunks.append(buf[:CONTENT_HARD_LIMIT])
                buf = ""
            for i in range(0, len(sent), max_chars):
                chunks.append(sent[i:i + max_chars][:CONTENT_HARD_LIMIT])
            continue
        if len(buf) + len(sent) + 1 > max_chars and buf:
            chunks.append(buf[:CONTENT_HARD_LIMIT])
            buf = sent
        else:
            buf = f"{buf}{sent}" if not buf else f"{buf} {sent}"
    if buf:
        chunks.append(buf[:CONTENT_HARD_LIMIT])
    return chunks
