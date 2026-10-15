"""Offline, explicitly synthetic mental screening and Agent display fixtures.

Public API:
    build_mental(students, *, as_of, rng, next_ids, actor_id) -> (tables, summary)
    validate_mental(tables, students, as_of) -> None

No database, network, model, tracing, login, export, or audit-log operations occur.
SQL DATE/DATETIME values are ISO strings; JSON database values are encoded strings.
``next_ids`` is mutated in place, using SQL table names as keys.
"""

from __future__ import annotations

import datetime as dt
import json
import math
import random
from collections import Counter, defaultdict
from typing import Any


MARKER = "[演示合成]"
BUSINESS_TIMEZONE = "Asia/Shanghai"
TABLES = (
    "mental_questionnaire", "mental_question", "mental_response",
    "mental_assessment", "agent_task", "intervention_feedback",
)
LEVELS = (
    ("正常", 85, 100, 0.80),
    ("轻度", 70, 84, 0.15),
    ("中度", 50, 69, 0.04),
    ("重度", 30, 49, 0.01),
)
WAVES = (
    ("2025-10", dt.date(2025, 10, 1), dt.date(2025, 10, 31), 0.90),
    ("2026-04", dt.date(2026, 4, 1), dt.date(2026, 4, 30), 0.90),
    ("2026-09/10", dt.date(2026, 9, 28), dt.date(2026, 10, 23), 0.75),
)
ITEMS = (
    "我可以安排适合自己的学习时间",
    "我知道遇到课程困难时可以向谁求助",
    "我能找到课程所需的学习材料",
    "我可以为较复杂的任务安排分步骤计划",
    "我有机会与同学讨论学习中的疑问",
    "我知道如何了解学校提供的学习支持",
    "我可以在日常安排中留出休息时间",
    "我能及时了解课程作业和考试的要求",
    "我能够获得适合自己的学习环境",
    "我知道如何与授课教师交流课程问题",
    "我可以把学习目标拆成可完成的小目标",
    "我能找到自己感兴趣的校园活动信息",
    "我愿意在需要时了解可用的支持资源",
    "我可以与同伴保持日常交流",
    "我有机会参加自己愿意参与的集体活动",
    "我知道如何获取校园资源的公开说明",
    "我可以按照自己的节奏调整学习安排",
    "我能够了解适合自己的课程辅导方式",
    "我能为下一周安排合理的学习和休息",
    "我知道在哪里了解正规的校内咨询资源",
)


def _json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), allow_nan=False)


def _day(value: Any, label: str = "date") -> dt.date:
    if isinstance(value, dt.datetime):
        return value.date()
    if isinstance(value, dt.date):
        return value
    if isinstance(value, str):
        try:
            return dt.date.fromisoformat(value[:10])
        except ValueError as exc:
            raise ValueError(f"{label}: invalid ISO date {value!r}") from exc
    raise ValueError(f"{label}: enrollment/as-of date is required")


def _moment(value: Any, label: str = "timestamp") -> dt.datetime:
    if isinstance(value, dt.datetime):
        result = value
    elif isinstance(value, str):
        try:
            result = dt.datetime.fromisoformat(value)
        except ValueError as exc:
            raise ValueError(f"{label}: invalid timestamp") from exc
    else:
        raise ValueError(f"{label}: timestamp is required")
    if result.tzinfo is not None:
        raise ValueError(f"{label}: SQL business timestamps must be naive UTC+08")
    return result


def _stamp(day: dt.date, hour: int = 9, minute: int = 0, second: int = 0) -> str:
    return dt.datetime.combine(day, dt.time(hour, minute, second)).isoformat(sep=" ")


def _id(next_ids: dict[str, int], table: str) -> int:
    value = next_ids.get(table)
    if type(value) is not int or value < 1:
        raise ValueError(f"next_ids[{table!r}] must be a positive maximum-ID-plus-one integer")
    next_ids[table] = value + 1
    return value


def _student_map(students: list[dict[str, Any]]) -> dict[int, dict[str, Any]]:
    result: dict[int, dict[str, Any]] = {}
    for student in students:
        sid = student.get("id")
        if type(sid) is not int or sid < 1 or sid in result:
            raise ValueError("students require distinct positive integer student_info.id values")
        _day(student.get("enrollment_date"), f"student {sid} enrollment_date")
        result[sid] = student
    return result


def _enrolled(student: dict[str, Any]) -> dt.date:
    return _day(student.get("enrollment_date"), "student enrollment_date")


def _sample_size(size: int, fraction: float) -> int:
    return min(size, math.floor(size * fraction + 0.5))


def _level_counts(size: int) -> dict[str, int]:
    # Largest remainder keeps exact sample size without losing small demo groups.
    raw = [(level, size * fraction) for level, _, _, fraction in LEVELS]
    counts = {level: math.floor(count) for level, count in raw}
    remainder = size - sum(counts.values())
    order = sorted(range(len(raw)), key=lambda i: (-(raw[i][1] % 1), i))
    for index in order[:remainder]:
        counts[raw[index][0]] += 1
    return counts


def _level(score: int) -> str:
    return next((name for name, minimum, _, _ in LEVELS if score >= minimum), "高危")


def _rules() -> list[dict[str, Any]]:
    return [
        {"level": name, "minScore": minimum,
         "suggestion": f"{MARKER} 自编筛查分类为{name}，仅展示学习支持与人工复核流程，非诊断；不代表真实个人状况。"}
        for name, minimum in [(name, low) for name, low, _, _ in LEVELS] + [("高危", 0)]
    ]


def _values(score: int, rng: random.Random) -> list[int]:
    values = [rng.randint(0, 5) for _ in ITEMS]
    delta = score - sum(values)
    while delta:
        indexes = [i for i, value in enumerate(values) if value < 5] if delta > 0 else [i for i, value in enumerate(values) if value > 0]
        index = rng.choice(indexes)
        step = 1 if delta > 0 else -1
        values[index] += step
        delta -= step
    return values


def _random_day(start: dt.date, end: dt.date, rng: random.Random) -> dt.date:
    if start > end:
        raise ValueError("empty synthetic event time window")
    return start + dt.timedelta(days=rng.randint(0, (end - start).days))


def _safe_snapshot(questionnaire: dict[str, Any], questions: list[dict[str, Any]], published_at: str) -> str:
    metadata = {
        "id": questionnaire["id"], "title": questionnaire["title"],
        "type": questionnaire["type"], "description": questionnaire["description"],
        "questions": len(questions), "startTime": questionnaire["start_time"],
        "endTime": questionnaire["end_time"], "status": 1, "levelRules": None,
        "creatorId": None, "deleted": 0,
        "createTime": questionnaire["create_time"].replace(" ", "T"),
        "updateTime": published_at.replace(" ", "T"),
    }
    safe_questions = []
    for question in questions:
        safe_questions.append({
            "id": question["id"], "questionnaireId": question["questionnaire_id"],
            "sortOrder": question["sort_order"], "content": question["content"],
            "questionType": "scale", "options": None, "scoringRules": None,
            "scaleMin": 0, "scaleMax": 5, "scaleLabels": question["scale_labels"],
            "required": 1, "deleted": 0,
            "createTime": question["create_time"].replace(" ", "T"),
            "updateTime": question["update_time"].replace(" ", "T"),
        })
    return _json({"questionnaire": metadata, "questions": safe_questions, "levelRules": []})


def _warning(assessment: dict[str, Any], *, as_of: dt.date, actor_id: int, rng: random.Random) -> None:
    assessment.update(warning_status="OPEN", warning_note=None,
                      warning_handled_by=None, warning_handled_at=None)
    if assessment["level"] not in {"中度", "重度", "高危"}:
        return
    submitted = _day(assessment["create_time"])
    age = (as_of - submitted).days
    draw = rng.random()
    if age >= 7 and draw >= 0.80:
        state, min_days = "RESOLVED", 7
        text = "模拟完成支持资源说明与工作流复核；这不是实际咨询、诊断或康复记录"
    elif age >= 1 and draw >= 0.50:
        state, min_days = "IN_PROGRESS", 1
        text = "模拟安排授权专业角色复核；没有发生真实会谈或咨询"
    else:
        return
    handled = _random_day(submitted + dt.timedelta(days=min_days), as_of, rng)
    assessment.update(warning_status=state,
                      warning_note=f"{MARKER} 离线生成的模拟处置：{text}。",
                      warning_handled_by=actor_id,
                      warning_handled_at=_stamp(handled, 17, rng.randint(0, 59)))


def build_mental(
    students: list[dict[str, Any]], *, as_of: dt.date, rng: random.Random,
    next_ids: dict[str, int], actor_id: int,
) -> tuple[dict[str, list[dict[str, Any]]], dict[str, Any]]:
    """Build six SQL table datasets; no writes occur except advancing ``next_ids``.

    Missing/invalid enrollment dates fail closed rather than inventing eligibility.
    Samples are 90%, 90%, 75% of enrolled eligible students, rounded half up.
    Production Agent/feedback datasets remain empty. No offline task, report,
    answer, student, or feedback rows are included in the public summary.
    """
    as_of = _day(as_of, "as_of")
    event_cutoff = as_of - dt.timedelta(days=1)
    if type(actor_id) is not int or actor_id < 1:
        raise ValueError("actor_id must be the authorized existing admin user ID")
    roster = _student_map(students)
    for table in TABLES:
        if type(next_ids.get(table)) is not int or next_ids[table] < 1:
            raise ValueError(f"missing/invalid next ID for {table}")
    tables: dict[str, list[dict[str, Any]]] = {table: [] for table in TABLES}
    ordered = [roster[sid] for sid in sorted(roster)]
    # Stable latent ranks give repeat waves some continuity, without tying mental
    # classifications to gender, poverty, family background, or academic results.
    latent = {student["id"]: rng.random() for student in ordered}
    wave_summaries = []
    for wave, start, end, fraction in WAVES:
        effective_end = min(end, event_cutoff)
        eligible = [student for student in ordered if start <= effective_end and _enrolled(student) <= effective_end]
        sampled = rng.sample(eligible, _sample_size(len(eligible), fraction))
        noisy_rank = {student["id"]: latent[student["id"]] + rng.gauss(0, 0.045) for student in sampled}
        sampled.sort(key=lambda student: (noisy_rank[student["id"]], student["id"]))
        counts = _level_counts(len(sampled))
        created = min(start - dt.timedelta(days=7), event_cutoff)
        published_at = _stamp(created)
        closed = end + dt.timedelta(days=1)
        questionnaire = {
            "id": _id(next_ids, "mental_questionnaire"),
            "title": f"{MARKER} 日常学习与支持体验自编筛查（{wave}）",
            "type": "演示自编筛查",
            "description": "完全合成的20题自编筛查，每题0至5分、总分100分，高分表示演示情景中支持更充分；不是标准医学量表或诊断工具。分布仅为模拟假设，不代表真实患病率。",
            "questions": len(ITEMS), "start_time": start.isoformat(), "end_time": end.isoformat(),
            "status": 0 if as_of < start else 2 if as_of > end else 1,
            "level_rules": _json(_rules()), "creator_id": actor_id, "deleted": 0,
            "template_content": None,
            "create_time": published_at,
            "update_time": _stamp(closed) if closed <= event_cutoff else published_at,
        }
        tables["mental_questionnaire"].append(questionnaire)
        questions = []
        for index, content in enumerate(ITEMS, 1):
            question = {
                "id": _id(next_ids, "mental_question"), "questionnaire_id": questionnaire["id"],
                "sort_order": index, "content": f"{MARKER} 最近两周，{content}。",
                "question_type": "scale", "options": None,
                "scoring_rules": _json({str(value): value for value in range(6)}),
                "scale_min": 0, "scale_max": 5,
                "scale_labels": _json({"min": "支持很少", "max": "支持充分"}),
                "required": 1, "deleted": 0, "create_time": published_at, "update_time": published_at,
            }
            questions.append(question)
            tables["mental_question"].append(question)
        snapshot = _safe_snapshot(questionnaire, questions, published_at)
        offset = 0
        for name, minimum, maximum, _ in LEVELS:
            for student in sampled[offset:offset + counts[name]]:
                score = rng.randint(minimum, maximum)
                values = _values(score, rng)
                submitted = _random_day(max(start, _enrolled(student)), effective_end, rng)
                submitted_at = _stamp(submitted, rng.randint(9, 16), rng.randint(0, 59), rng.randint(0, 59))
                response = {
                    "id": _id(next_ids, "mental_response"), "student_id": student["id"],
                    "questionnaire_id": questionnaire["id"],
                    "answers": _json([{"questionId": question["id"], "optionIndices": [], "text": "", "scaleValue": value}
                                      for question, value in zip(questions, values)]),
                    "questionnaire_snapshot": snapshot, "score": score, "status": 1, "create_time": submitted_at,
                }
                assessment = {
                    "id": _id(next_ids, "mental_assessment"), "student_id": student["id"],
                    "questionnaire_id": questionnaire["id"], "response_id": response["id"],
                    "score": score, "level": name,
                    "result": f"{MARKER} 自编筛查总分{score}，演示分类{name}；非诊断，不代表真实人员状况或患病率。",
                    "suggestion": next(rule["suggestion"] for rule in _rules() if rule["level"] == name),
                    "status": 1, "create_time": submitted_at,
                }
                _warning(assessment, as_of=event_cutoff, actor_id=actor_id, rng=rng)
                tables["mental_response"].append(response)
                tables["mental_assessment"].append(assessment)
            offset += counts[name]
        wave_summaries.append({
            "wave": wave, "questionnaire_id": questionnaire["id"], "eligible_students": len(eligible),
            "sampling_assumption": fraction, "responses": len(sampled), "level_record_counts": counts,
            "actual_submit_end": effective_end.isoformat() if eligible else None,
        })

    validate_mental(tables, students, as_of)
    recent_start = as_of - dt.timedelta(days=30)
    recent = [assessment for assessment in tables["mental_assessment"] if _day(assessment["create_time"]) >= recent_start]
    summary = {
        "synthetic": True, "as_of": as_of.isoformat(), "event_cutoff": event_cutoff.isoformat(), "business_timezone": BUSINESS_TIMEZONE,
        "medical_boundary": "自编筛查分类非诊断；没有医学患病率或真实干预有效性主张",
        "demo_assumptions": {
            "historical_sampling": 0.90, "current_sampling": 0.75,
            "classification_distribution": {name: fraction for name, _, _, fraction in LEVELS},
            "warning_target_weights": {"OPEN": 0.50, "IN_PROGRESS": 0.30, "RESOLVED": 0.20},
            "agent_records_generated": 0, "feedback_records_generated": 0,
        },
        "waves": wave_summaries, "table_counts": {table: len(rows) for table, rows in tables.items()},
        "warning_status_counts": dict(Counter(assessment["warning_status"] for assessment in tables["mental_assessment"]
                                               if assessment["level"] in {"中度", "重度", "高危"})),
        "recent_30_day_assessment_records": len(recent),
        "recent_30_day_distinct_students": len({assessment["student_id"] for assessment in recent}),
        "agent_state_policy": "Production agent_task/intervention_feedback remain empty; actual AI artifacts require separately authorized real execution",
        "preserved_not_generated": ["sys_login_log", "audit_log", "agent_export_task", "intervention_report", "knowledge_citation", "Langfuse traces", "model metrics", "configuration", "credentials"],
    }
    return tables, summary


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(f"mental demo validation: {message}")


def _decode(value: Any, label: str) -> Any:
    _require(isinstance(value, str), f"{label} must be stored as JSON text")

    def unique(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result = {}
        for key, item in pairs:
            _require(key not in result, f"{label} has a duplicate JSON key")
            result[key] = item
        return result

    try:
        return json.loads(value, object_pairs_hook=unique,
                          parse_constant=lambda constant: (_ for _ in ()).throw(ValueError(f"invalid JSON number {constant}")))
    except (ValueError, TypeError) as exc:
        raise ValueError(f"mental demo validation: invalid {label}: {exc}") from exc


def _indexed(rows: list[dict[str, Any]], table: str) -> dict[int, dict[str, Any]]:
    result = {}
    for row in rows:
        key = row.get("id")
        _require(type(key) is int and key > 0 and key not in result, f"{table} IDs must be distinct positive integers")
        result[key] = row
    return result


def validate_mental(tables: dict[str, list[dict[str, Any]]], students: list[dict[str, Any]], as_of: dt.date) -> None:
    """Validate these generated datasets, raising ValueError on any inconsistency.

    Other table keys may be present in a combined root dataset. This validator
    only inspects the six tables owned by this module, never existing DB rows.
    """
    as_of = _day(as_of, "as_of")
    event_cutoff = as_of - dt.timedelta(days=1)
    roster = _student_map(students)
    latest_allowed = dt.datetime.combine(as_of, dt.time.min)
    indexes = {table: _indexed(tables.get(table, []), table) for table in TABLES}
    qs = indexes["mental_questionnaire"]
    questions = indexes["mental_question"]
    responses = indexes["mental_response"]
    assessments = indexes["mental_assessment"]
    by_questionnaire: dict[int, list[dict[str, Any]]] = defaultdict(list)
    for question in questions.values():
        _require(question["questionnaire_id"] in qs, "orphan question")
        _require(question["question_type"] == "scale" and question["scale_min"] == 0 and question["scale_max"] == 5, "expected 0..5 scale")
        _require(question["required"] == 1 and question["deleted"] == 0, "questions must be required and active")
        _require(question["options"] is None and _decode(question["scoring_rules"], "scoring_rules") == {str(i): i for i in range(6)}, "invalid scale scoring contract")
        _require(MARKER in question["content"] and len(question["content"]) <= 1000, "unmarked/overlong question")
        labels = _decode(question["scale_labels"], "scale_labels")
        _require(set(labels) == {"min", "max"} and all(isinstance(v, str) for v in labels.values()), "invalid scale labels")
        by_questionnaire[question["questionnaire_id"]].append(question)
    _require(len(qs) == 3, "expected three questionnaire waves")
    wave_by_q = {}
    for questionnaire in qs.values():
        matches = [wave for wave in WAVES if questionnaire["start_time"] == wave[1].isoformat() and questionnaire["end_time"] == wave[2].isoformat()]
        _require(len(matches) == 1, "unknown wave dates")
        wave_by_q[questionnaire["id"]] = matches[0]
        rows = sorted(by_questionnaire[questionnaire["id"]], key=lambda q: q["sort_order"])
        _require(questionnaire["questions"] == 20 and len(rows) == 20 and [q["sort_order"] for q in rows] == list(range(1, 21)), "each questionnaire needs 20 ordered questions")
        _require(_decode(questionnaire["level_rules"], "level_rules") == _rules(), "unexpected classification thresholds")
        _require(MARKER in questionnaire["title"] and ("非" in questionnaire["description"] or "不是" in questionnaire["description"]), "missing screening disclaimer")
        _require(questionnaire["status"] == (0 if as_of < matches[0][1] else 2 if as_of > matches[0][2] else 1), "questionnaire status/date mismatch")
    _require(len({wave[0] for wave in wave_by_q.values()}) == 3, "duplicate wave")
    pairs = set()
    response_counts: Counter[int] = Counter()
    for response in responses.values():
        sid, qid = response["student_id"], response["questionnaire_id"]
        _require(sid in roster and qid in qs, "orphan response")
        _require((sid, qid) not in pairs, "student/questionnaire response must be unique")
        pairs.add((sid, qid))
        questionnaire = qs[qid]
        rows = sorted(by_questionnaire[qid], key=lambda q: q["sort_order"])
        submitted = _moment(response["create_time"])
        _require(max(_day(questionnaire["start_time"]), _enrolled(roster[sid])) <= submitted.date() <= min(_day(questionnaire["end_time"]), event_cutoff), "submission outside enrollment/open window")
        answers = _decode(response["answers"], "answers")
        _require(isinstance(answers, list) and len(answers) == 20, "answers must contain 20 items")
        score = 0
        for question, answer in zip(rows, answers):
            _require(isinstance(answer, dict) and set(answer) == {"questionId", "optionIndices", "text", "scaleValue"}, "invalid answer fields")
            _require(answer["questionId"] == question["id"] and answer["optionIndices"] == [] and answer["text"] == "", "answer/question/type mismatch")
            _require(type(answer["scaleValue"]) is int and 0 <= answer["scaleValue"] <= 5, "scaleValue must be integer 0..5")
            score += answer["scaleValue"]
        _require(type(response["score"]) is int and response["score"] == score and response["status"] == 1, "response score/status mismatch")
        snapshot = _decode(response["questionnaire_snapshot"], "questionnaire_snapshot")
        expected = _decode(_safe_snapshot(questionnaire, rows, questionnaire["create_time"]), "expected snapshot")
        _require(snapshot == expected, "snapshot differs from immutable safe submission template")
        response_counts[qid] += 1
    assessment_response_ids = set()
    level_counts: dict[int, Counter[str]] = defaultdict(Counter)
    for assessment in assessments.values():
        response_id = assessment["response_id"]
        _require(response_id in responses and response_id not in assessment_response_ids, "missing/duplicate assessment response link")
        assessment_response_ids.add(response_id)
        response = responses[response_id]
        _require(all(assessment[key] == response[key] for key in ("student_id", "questionnaire_id", "score", "create_time")), "assessment/response mismatch")
        _require(assessment["level"] == _level(response["score"]) and assessment["status"] == 1, "assessment level/status mismatch")
        _require(all(MARKER in assessment[key] and len(assessment[key]) <= 500 for key in ("result", "suggestion")), "unmarked/overlong assessment text")
        state = assessment["warning_status"]
        _require(state in {"OPEN", "IN_PROGRESS", "RESOLVED"}, "invalid warning state")
        if state == "OPEN":
            _require(all(assessment[key] is None for key in ("warning_note", "warning_handled_by", "warning_handled_at")), "unhandled warning has disposition fields")
        else:
            _require(assessment["level"] in {"中度", "重度", "高危"}, "nonwarning level was handled")
            _require(isinstance(assessment["warning_note"], str) and MARKER in assessment["warning_note"] and len(assessment["warning_note"]) <= 1000, "invalid simulation note")
            _require(type(assessment["warning_handled_by"]) is int and assessment["warning_handled_by"] > 0, "missing handling user")
            _require(_moment(assessment["create_time"]) <= _moment(assessment["warning_handled_at"]) <= latest_allowed, "warning disposition time mismatch")
        level_counts[assessment["questionnaire_id"]][assessment["level"]] += 1
    _require(assessment_response_ids == set(responses), "responses and assessments must be one-to-one")
    for qid, wave in wave_by_q.items():
        _, start, end, fraction = wave
        eligible = sum(start <= min(end, event_cutoff) and _enrolled(student) <= min(end, event_cutoff) for student in roster.values())
        expected_count = _sample_size(eligible, fraction)
        _require(response_counts[qid] == expected_count, "wave sampling count mismatch")
        _require(dict(level_counts[qid]) == {name: count for name, count in _level_counts(expected_count).items() if count}, "wave classification distribution mismatch")
    _require(not indexes["agent_task"] and not indexes["intervention_feedback"],
             "production datasets must not contain offline Agent or feedback history")
    for table, rows in ((table, tables.get(table, [])) for table in TABLES):
        for row in rows:
            for field in ("create_time", "update_time", "created_at", "updated_at", "completed_at", "warning_handled_at"):
                if row.get(field) is not None:
                    _require(_moment(row[field], f"{table}.{field}") < latest_allowed, f"event at/after as_of midnight in {table}.{field}")
            create_field = "create_time" if "create_time" in row else "created_at"
            update_field = "update_time" if "update_time" in row else "updated_at"
            if row.get(create_field) is not None and row.get(update_field) is not None:
                _require(_moment(row[create_field]) <= _moment(row[update_field]), f"reversed timestamps in {table}")
