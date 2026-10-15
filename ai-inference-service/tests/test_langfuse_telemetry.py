import json
import os
import unittest
from functools import wraps
from types import SimpleNamespace
from unittest.mock import Mock, patch

from app.services import langfuse_telemetry as module


def config(capture=False, **overrides):
    values = dict(LANGFUSE_PUBLIC_KEY="pk-lf-offline-test",
                  LANGFUSE_SECRET_KEY="sk-lf-offline-test",
                  LANGFUSE_HOST="https://langfuse.invalid/",
                  LANGFUSE_CAPTURE_CONTENT=capture)
    values.update(overrides)
    return SimpleNamespace(**values)


class RecordingSDK:
    def __init__(self):
        self.client = Mock()
        self.constructor = Mock(return_value=self.client)
        self.decorator_options = []
        self.error_seen_by_decorator = None
        self.fail_before = False
        self.fail_after = False

    def observe(self, **options):
        self.decorator_options.append(options)

        def decorate(function):
            @wraps(function)
            async def wrapper(*args, **kwargs):
                if self.fail_before:
                    raise RuntimeError("SDK unavailable")
                try:
                    result = await function(*args, **kwargs)
                except Exception as error:
                    self.error_seen_by_decorator = error
                    raise
                if self.fail_after:
                    raise RuntimeError("export unavailable")
                return result
            return wrapper
        return decorate

    def telemetry(self, capture=False):
        with patch.object(module, "_sdk_symbols", return_value=(self.constructor, self.observe)):
            return module.LangfuseTelemetry(config(capture))


class LangfuseTelemetryTest(unittest.IsolatedAsyncioTestCase):
    async def test_partial_or_blank_configuration_never_initializes_sdk(self):
        for field in ("LANGFUSE_PUBLIC_KEY", "LANGFUSE_SECRET_KEY", "LANGFUSE_HOST"):
            for value in ("", "   "):
                with self.subTest(field=field, value=value):
                    with patch.object(module, "_sdk_symbols") as sdk:
                        telemetry = module.LangfuseTelemetry(config(**{field: value}))
                        sdk.assert_not_called()
                        function = Mock()
                        self.assertIs(telemetry.observe_generation(function), function)

    async def test_missing_sdk_and_failed_initialization_are_noops(self):
        for failure in (ModuleNotFoundError("langfuse"), RuntimeError("SDK unavailable")):
            with patch.object(module, "_sdk_symbols", side_effect=failure):
                telemetry = module.LangfuseTelemetry(config())

            @telemetry.observe_generation
            async def business():
                return {"result": 42}

            self.assertEqual(await business(), {"result": 42})
            telemetry.start_call(route="risk", model="test", messages=[], cache_prompt=False)
            telemetry.shutdown()
        constructor = Mock(side_effect=RuntimeError("SDK construction failed"))
        with patch.object(module, "_sdk_symbols", return_value=(constructor, Mock())):
            telemetry = module.LangfuseTelemetry(config())
        self.assertIsNone(telemetry._client)

    async def test_explicit_host_and_default_capture_keep_content_out(self):
        sdk = RecordingSDK()
        telemetry = sdk.telemetry()
        sdk.constructor.assert_called_once_with(public_key="pk-lf-offline-test",
                                                secret_key="sk-lf-offline-test",
                                                base_url="https://langfuse.invalid",
                                                tracing_enabled=True)

        @telemetry.observe_generation
        async def business(messages):
            telemetry.start_call(route="risk", model="test-model", messages=messages, cache_prompt=False)
            response = {"choices": [{"message": {"content": "PRIVATE-RESPONSE"}}],
                        "usage": {"prompt_tokens": 11, "completion_tokens": 7},
                        "timings": {"cached_n": 3, "prompt_ms": 12.5},
                        "unexpected_metadata": "PRIVATE-STUDENT"}
            telemetry.record_response(route="PRIVATE-STUDENT", response=response)
            return response

        result = await business([{"role": "user", "content": "PRIVATE-STUDENT"}])
        self.assertEqual(result["choices"][0]["message"]["content"], "PRIVATE-RESPONSE")
        self.assertEqual(sdk.decorator_options, [dict(as_type="generation", capture_input=False, capture_output=False)])
        fields = [call.kwargs for call in sdk.client.update_current_generation.call_args_list]
        self.assertNotIn("input", fields[0])
        self.assertNotIn("output", fields[1])
        self.assertEqual(fields[1]["usage_details"], {"input": 11, "output": 7, "total": 18})
        self.assertEqual(fields[1]["metadata"], {"route": "other", "cached_n": 3, "prefill_ms": 12.5})
        serialized = json.dumps(fields)
        self.assertNotIn("PRIVATE-STUDENT", serialized)
        self.assertNotIn("PRIVATE-RESPONSE", serialized)

    async def test_opt_in_content_is_explicit_and_does_not_capture_raw_response(self):
        sdk = RecordingSDK()
        telemetry = sdk.telemetry(capture=True)
        messages = [{"role": "user", "content": "synthetic input"}]

        @telemetry.observe_generation
        async def business():
            telemetry.start_call(route="plan", model="test", messages=messages, cache_prompt=True)
            telemetry.record_response(route="plan", response={
                "choices": [{"message": {"content": "synthetic output", "reasoning": "unnecessary"}}],
                "usage": {"prompt_tokens": 2, "completion_tokens": 3}})

        await business()
        fields = [call.kwargs for call in sdk.client.update_current_generation.call_args_list]
        self.assertEqual(fields[0]["input"], messages)
        self.assertEqual(fields[1]["output"], "synthetic output")
        self.assertNotIn("unnecessary", json.dumps(fields))
        self.assertFalse(sdk.decorator_options[0]["capture_output"])

    async def test_sdk_failures_do_not_repeat_or_change_business_calls(self):
        for before, after, updates in ((True, False, False), (False, True, False), (False, False, True)):
            with self.subTest(before=before, after=after, updates=updates):
                sdk = RecordingSDK()
                sdk.fail_before, sdk.fail_after = before, after
                if updates:
                    sdk.client.update_current_generation.side_effect = RuntimeError("SDK error")
                telemetry = sdk.telemetry()
                calls = []

                @telemetry.observe_generation
                async def business():
                    calls.append("provider request")
                    telemetry.start_call(route="risk", model="test", messages=[], cache_prompt=False)
                    return "result"

                self.assertEqual(await business(), "result")
                self.assertEqual(calls, ["provider request"])

    async def test_business_exception_preserved_without_exporting_its_text(self):
        sdk = RecordingSDK()
        telemetry = sdk.telemetry()
        error = RuntimeError("PRIVATE-STUDENT provider response")

        @telemetry.observe_generation
        async def business():
            raise error

        with self.assertRaises(RuntimeError) as raised:
            await business()
        self.assertIs(raised.exception, error)
        self.assertIsNone(sdk.error_seen_by_decorator)
        sdk.client.update_current_generation.assert_called_once_with(level="ERROR", status_message="llm-call-failed")
        self.assertNotIn("PRIVATE-STUDENT", str(sdk.client.update_current_generation.call_args))

    async def test_bad_metrics_and_shutdown_failure_do_not_escape(self):
        sdk = RecordingSDK()
        telemetry = sdk.telemetry()
        telemetry.record_response(route="risk", response={"usage": {"prompt_tokens": "bad"}})
        sdk.client.update_current_generation.assert_not_called()
        telemetry.record_response(route="risk", response={"timings": {"prompt_ms": float("nan")}})
        self.assertEqual(sdk.client.update_current_generation.call_args.kwargs["metadata"]["prefill_ms"], 0.0)
        sdk.client.shutdown.side_effect = RuntimeError("export unavailable")
        telemetry.shutdown()
        sdk.client.shutdown.assert_called_once()


class LangfuseRealSDKTest(unittest.IsolatedAsyncioTestCase):
    async def test_v4_exports_metrics_without_io_to_memory_exporter(self):
        try:
            from langfuse import Langfuse, observe
            from opentelemetry.sdk.trace import TracerProvider
            from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter
        except ImportError:
            self.skipTest("Install tests/requirements-langfuse.txt to exercise the real SDK")
        exporter = InMemorySpanExporter()
        provider = TracerProvider()
        constructor = lambda **kwargs: Langfuse(**kwargs, tracer_provider=provider, span_exporter=exporter)
        # Synthetic configuration only. A memory exporter makes no HTTP request.
        with patch.dict(os.environ, {"LANGFUSE_PUBLIC_KEY": "pk-lf-offline-test",
                                    "LANGFUSE_SECRET_KEY": "sk-lf-offline-test",
                                    "LANGFUSE_HOST": "https://langfuse.invalid",
                                    "LANGFUSE_TRACING_ENABLED": "true"}), \
                patch.object(module, "_sdk_symbols", return_value=(constructor, observe)):
            telemetry = module.LangfuseTelemetry(config())
            self.assertIsNotNone(telemetry._client)
            self.assertIsNotNone(telemetry._observe)

            @telemetry.observe_generation
            async def business(messages):
                telemetry.start_call(route="risk", model="offline-model", messages=messages, cache_prompt=False)
                telemetry.record_response(route="risk", response={
                    "choices": [{"message": {"content": "PRIVATE-RESPONSE"}}],
                    "usage": {"prompt_tokens": 11, "completion_tokens": 7},
                    "timings": {"cached_n": 3, "prompt_ms": 12.5}})
                return "PRIVATE-RESPONSE"

            try:
                self.assertEqual(await business([{"role": "user", "content": "PRIVATE-STUDENT"}]), "PRIVATE-RESPONSE")
                telemetry._client.flush()
                spans = exporter.get_finished_spans()
                self.assertEqual(len(spans), 1)
                span = spans[0]
                self.assertEqual(span.name, "llm.risk")
                self.assertGreater(span.end_time, span.start_time)
                self.assertEqual(span.attributes["langfuse.observation.model.name"], "offline-model")
                usage = json.loads(span.attributes["langfuse.observation.usage_details"])
                self.assertEqual(usage, {"input": 11, "output": 7, "total": 18})
                self.assertNotIn("langfuse.observation.input", span.attributes)
                self.assertNotIn("langfuse.observation.output", span.attributes)
                self.assertNotIn("PRIVATE", json.dumps(dict(span.attributes)))
            finally:
                telemetry.shutdown()


if __name__ == "__main__":
    unittest.main()
