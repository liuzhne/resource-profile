"""G-4.3：text_splitter 三策略单元测试。

运行方式（任选一）：
  cd ai-inference-service && python -m unittest tests.test_text_splitter
  cd ai-inference-service && pytest tests/test_text_splitter.py    # 若装了 pytest

故意用 stdlib unittest 而非 pytest，让此测试在任何 Python 环境都能跑。
"""
from __future__ import annotations

import os
import sys
import unittest

# 让 `python -m unittest tests.test_text_splitter` 在仓库子目录里也能找到 app/
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.services.text_splitter import (  # noqa: E402
    CONTENT_HARD_LIMIT,
    ChunkParams,
    MAX_CHUNKS,
    split,
)


class TestSplitNone(unittest.TestCase):
    def test_basic(self):
        self.assertEqual(split("hello", ChunkParams(name="none")), ["hello"])

    def test_strip_empty(self):
        self.assertEqual(split("   \n\n  ", ChunkParams(name="none")), [])
        self.assertEqual(split("", ChunkParams(name="none")), [])

    def test_truncates_to_hard_limit(self):
        out = split("a" * (CONTENT_HARD_LIMIT + 500), ChunkParams(name="none"))
        self.assertEqual(len(out), 1)
        self.assertEqual(len(out[0]), CONTENT_HARD_LIMIT)


class TestSplitFixedSize(unittest.TestCase):
    def test_overlap_steps(self):
        # 100 字符 / window=40 / overlap=10 → step=30 → indices 0,30,60,90
        out = split("a" * 100, ChunkParams(name="fixed_size", window=40, overlap=10))
        self.assertEqual(len(out), 3)  # 90 起始的窗口还会取到 10 字符，循环 break
        self.assertEqual(len(out[0]), 40)
        self.assertEqual(len(out[1]), 40)

    def test_invalid_overlap(self):
        with self.assertRaises(ValueError):
            split("abc", ChunkParams(name="fixed_size", window=10, overlap=10))
        with self.assertRaises(ValueError):
            split("abc", ChunkParams(name="fixed_size", window=10, overlap=-1))

    def test_window_zero(self):
        with self.assertRaises(ValueError):
            split("abc", ChunkParams(name="fixed_size", window=0, overlap=0))

    def test_short_text_single_chunk(self):
        out = split("abc", ChunkParams(name="fixed_size", window=40, overlap=10))
        self.assertEqual(out, ["abc"])


class TestSplitSentence(unittest.TestCase):
    def test_chinese_punct(self):
        text = "第一句。第二句！第三句？"
        out = split(text, ChunkParams(name="sentence", max_chars=10))
        # 三个句子各 4 字符，max_chars=10 容不下两句 (4+1+4=9 ok，但加第三句超 → 切)
        # 期望至少 2 chunk
        self.assertGreaterEqual(len(out), 2)
        joined = "".join(out)
        # 所有句子内容都应保留
        for s in ("第一句", "第二句", "第三句"):
            self.assertIn(s, joined)

    def test_paragraph_break(self):
        text = "第一段。\n\n第二段。"
        out = split(text, ChunkParams(name="sentence", max_chars=20))
        joined = " ".join(out)
        self.assertIn("第一段", joined)
        self.assertIn("第二段", joined)

    def test_long_sentence_forced_split(self):
        # 单个无标点的"句子"超 max_chars → 强制按 max_chars 切
        long_run = "a" * 50
        out = split(long_run, ChunkParams(name="sentence", max_chars=10))
        self.assertEqual(len(out), 5)
        for c in out:
            self.assertEqual(len(c), 10)

    def test_invalid_max_chars(self):
        with self.assertRaises(ValueError):
            split("abc", ChunkParams(name="sentence", max_chars=0))


class TestSplitUnknown(unittest.TestCase):
    def test_unknown_raises(self):
        with self.assertRaises(ValueError):
            split("abc", ChunkParams(name="markdown"))  # 未实现


class TestConstants(unittest.TestCase):
    def test_max_chunks_value(self):
        # 与 RAG_UPSERT_DESIGN.md §5 一致
        self.assertEqual(MAX_CHUNKS, 200)

    def test_content_hard_limit(self):
        # Milvus content 字段是 VARCHAR(4096)，限到 4000 留 buffer
        self.assertEqual(CONTENT_HARD_LIMIT, 4000)


if __name__ == "__main__":
    unittest.main()
