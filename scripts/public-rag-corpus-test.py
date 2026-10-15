import copy
import importlib.util
import io
import json
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("public_corpus", Path(__file__).with_name("public-rag-corpus.py"))
corpus = importlib.util.module_from_spec(spec)
spec.loader.exec_module(corpus)


class CorpusTest(unittest.TestCase):
    def test_reviewed_corpus_has_distinct_identity_and_complete_publisher_provenance(self):
        payload = corpus.manifest()
        self.assertEqual(corpus.validate(payload), {"case": 4, "policy": 4, "psychology": 4, "success": 0})
        for row in payload["documents"]:
            self.assertTrue(row["metadata"]["publisher"])
            self.assertTrue(row["metadata"]["source_title"])
            self.assertTrue(row["metadata"]["rights"])
        self.assertEqual(corpus.digest(payload), corpus.digest(corpus.manifest()))

    def test_before_network_rejects_truncated_sources_and_false_success_claims(self):
        for mutation in ("long_url", "unlabelled_case"):
            payload = copy.deepcopy(corpus.manifest())
            if mutation == "long_url":
                payload["documents"][0]["metadata"]["source_url"] += "?" + "a" * 70
            else:
                payload["documents"][-1]["text"] = "该学生干预一周后已完全康复。" * 10
            requests = []
            with self.assertRaises(ValueError):
                corpus.apply(payload, "http://127.0.0.1:8090", "private", lambda request, **kw: requests.append(request))
            self.assertEqual(requests, [])

    def test_apply_only_publishes_public_reviewed_payloads_and_reports_safe_metadata(self):
        requests = []

        def fake_open(request, **kwargs):
            row = json.loads(request.data)
            requests.append(row)
            self.assertEqual(kwargs["timeout"], 90)
            self.assertEqual(request.get_header("X-admin-token"), "private")
            return io.BytesIO(json.dumps({"doc_id": row["doc_id"], "chunks_written": 1, "skipped": False}).encode())

        result = corpus.apply(corpus.manifest(), "http://127.0.0.1:8090", "private", fake_open)
        self.assertEqual(len(requests), 12)
        self.assertEqual(len(result), 12)
        self.assertNotIn("private", json.dumps(result))
        self.assertFalse(any("text" in row or "metadata" in row for row in result))

    def test_no_external_api_or_redirect_receives_admin_credential(self):
        for url in ("https://example.org", "http://ai-inference-service:8090", "http://127.0.0.1:8090/unsafe"):
            with self.assertRaises(ValueError):
                corpus.apply(corpus.manifest(), url, "private")
        self.assertIsNone(corpus._NoRedirect().redirect_request(None, None, 302, None, None, "https://example.org"))


if __name__ == "__main__":
    unittest.main()
