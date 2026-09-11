import unittest

from app.services.llm_payload import build_chat_payload


class LlmProviderCompatibilityTest(unittest.TestCase):
    def test_cache_prompt_defaults_to_enabled_for_llama_cpp(self):
        payload = build_chat_payload(
            model="qwen", messages=[], temperature=0.3, max_tokens=128,
            cache_prompt_enabled=True)

        self.assertTrue(payload["cache_prompt"])

    def test_cache_prompt_is_omitted_for_standard_provider(self):
        payload = build_chat_payload(
            model="qwen", messages=[], temperature=0.3, max_tokens=128,
            cache_prompt_enabled=False)

        self.assertNotIn("cache_prompt", payload)


if __name__ == "__main__":
    unittest.main()
