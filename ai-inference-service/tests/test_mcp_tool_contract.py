"""Cross-language MCP tool-name contract tests without importing FastMCP."""

import ast
import unittest
from pathlib import Path


class McpToolContractTest(unittest.TestCase):
    def test_knowledge_rag_exposes_canonical_tool_names(self):
        source_path = Path(__file__).parents[1] / "app" / "mcp" / "tools.py"
        tree = ast.parse(source_path.read_text(encoding="utf-8"))

        names = set()
        for node in tree.body:
            if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                continue
            for decorator in node.decorator_list:
                if not isinstance(decorator, ast.Call):
                    continue
                if not isinstance(decorator.func, ast.Attribute) or decorator.func.attr != "tool":
                    continue
                for keyword in decorator.keywords:
                    if keyword.arg == "name" and isinstance(keyword.value, ast.Constant):
                        names.add(keyword.value.value)

        self.assertEqual(
            names,
            {"search_cases", "search_policies", "search_psychology"},
        )


if __name__ == "__main__":
    unittest.main()
