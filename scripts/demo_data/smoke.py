#!/usr/bin/env python3
"""Bounded production reads; the only mutations are real login/logout.

Credentials and all API/dataset rows remain in memory. Public output contains
only fixed check names, counts, and booleans. No Agent/model/feedback/PDF calls.
"""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from decimal import Decimal, ROUND_HALF_UP
import datetime as dt
import json
import math
import os
from pathlib import Path
import ssl
import stat
import urllib.error
import urllib.parse
import urllib.request

MAX_BODY = 4 * 1024 * 1024
ROLES = ("admin", "teacher", "student")
ID_KEYS = {"id", "studentId", "teacherId", "userId", "questionnaireId", "responseId",
           "creatorId", "sourceId", "questionId", "actorId", "warningHandledBy"}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def integer(value):
    if isinstance(value, bool):
        return None
    try:
        number = int(str(value))
        return number if str(number) == str(value) else None
    except (TypeError, ValueError):
        return None


def number(value):
    if isinstance(value, bool):
        return None
    try:
        result = Decimal(str(value))
        return result if result.is_finite() else None
    except Exception:
        return None


def normalize_ids(value):
    # common/JacksonConfig intentionally serializes Java Long as a JSON string.
    # Normalize only documented identifier fields; retain text, enums and scores.
    if isinstance(value, list):
        return [normalize_ids(item) for item in value]
    if isinstance(value, dict):
        return {key: integer(item) if key in ID_KEYS and isinstance(item, str) and integer(item) is not None
                else normalize_ids(item) for key, item in value.items()}
    return value


def equal_number(first, second):
    if first is None or second is None:
        return first is None and second is None
    return number(first) is not None and number(first) == number(second)


def object_json(value):
    try:
        return json.loads(value) if isinstance(value, str) else value
    except (ValueError, TypeError):
        return None


def day(value):
    return dt.date.fromisoformat(str(value)[:10])


def safe_template(data, expected_ids):
    if not isinstance(data, dict) or not isinstance(data.get("questionnaire"), dict):
        return False
    questions = data.get("questions")
    if not isinstance(questions, list) or len(questions) != 20 or data.get("levelRules") != []:
        return False
    if data["questionnaire"].get("levelRules") is not None or data["questionnaire"].get("creatorId") is not None:
        return False
    if {integer(q.get("id")) for q in questions if isinstance(q, dict)} != set(expected_ids):
        return False
    for question in questions:
        if not isinstance(question, dict) or question.get("scoringRules") is not None:
            return False
        if question.get("questionType") != "scale" or question.get("scaleMin") != 0 or question.get("scaleMax") != 5:
            return False
        options = object_json(question.get("options"))
        if options is not None and (not isinstance(options, list) or any(not isinstance(o, dict) or set(o) != {"label"} for o in options)):
            return False
    return True


def private_json(path):
    if path.is_symlink():
        raise ValueError("private input rejected")
    mode = path.stat().st_mode
    if not stat.S_ISREG(mode) or mode & 0o077:
        raise ValueError("private input rejected")
    # Inputs are local VM files; never fetched or logged by this script.
    return json.loads(path.read_text(encoding="utf-8"))


def write_report(path, report):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC | getattr(os, "O_NOFOLLOW", 0), 0o600)
    os.fchmod(fd, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        json.dump(report, stream, ensure_ascii=True, indent=2)
        stream.write("\n")


class Smoke:
    def __init__(self, base_url, dataset):
        parsed = urllib.parse.urlsplit(base_url)
        if parsed.scheme != "https" or not parsed.netloc or parsed.username or parsed.password or parsed.query or parsed.fragment or not parsed.path.rstrip("/").endswith("/api"):
            raise ValueError("HTTPS API base required")
        self.base_url = base_url.rstrip("/")
        self.tables = dataset.get("tables", dataset)
        self.as_of = day(dataset.get("as_of", "2026-10-09"))
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect(),
                                                 urllib.request.HTTPSHandler(context=ssl.create_default_context()))
        self.checks = []
        self.tokens = {}
        self.identities = {}
        self.student = None
        self.teacher = None
        self.current = None

    def record(self, name, passed, count=None):
        item = {"check": name, "passed": bool(passed), "count": count if type(count) is int else None}
        self.checks.append(item)
        print(json.dumps(item, ensure_ascii=True), flush=True)

    def request(self, method, path, *, token=None, payload=None):
        # A structural guard prevents accidental business writes or model routes.
        if not path.startswith("/") or ".." in path or "://" in path:
            return None, None
        if method != "GET" and not (method == "POST" and path in {"/auth/login", "/auth/logout"}):
            return None, None
        if path.startswith(("/agent", "/api/v1/agent", "/rag")):
            return None, None
        headers = {"Accept": "application/json", "User-Agent": "ResourceProfile-DemoReadSmoke/1.0"}
        if token:
            headers["Authorization"] = "Bearer " + token
        body = None
        if payload is not None:
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        try:
            request = urllib.request.Request(self.base_url + path, data=body, headers=headers, method=method)
            try:
                response = self.opener.open(request, timeout=20)
            except urllib.error.HTTPError as caught:
                response = caught
            with response:
                code = response.code
                raw = response.read(MAX_BODY + 1)
            if len(raw) > MAX_BODY:
                return None, None
            parsed = json.loads(raw)
            return code, normalize_ids(parsed) if isinstance(parsed, dict) else None
        except Exception:
            # Never print exception messages, URLs, HTTP bodies, or credentials.
            return None, None

    def check(self, name, path, *, role="admin", expected=200, assertion=None, method="GET", payload=None, token=None):
        http, body = self.request(method, path, token=self.tokens.get(role) if token is None else token, payload=payload)
        data = body.get("data") if isinstance(body, dict) else None
        business = integer(body.get("code")) if isinstance(body, dict) else None
        passed = http == 200 and business == 200 if expected == 200 else http in {200, expected} and business == expected and data is None
        if assertion is not None:
            try:
                passed = passed and bool(assertion(data))
            except Exception:
                passed = False
        count = len(data) if isinstance(data, list) else integer(data.get("total")) if isinstance(data, dict) and isinstance(data.get("records"), list) else None
        self.record(name, passed, count)
        return data if passed else None

    def login(self, credentials):
        if set(credentials) != set(ROLES) or not all(isinstance(credentials[role], str) and credentials[role] for role in ROLES):
            raise ValueError("credential schema rejected")
        for role in ROLES:
            login = self.check(role + "_login", "/auth/login", role=role, method="POST",
                               payload={"username": role, "password": credentials[role]},
                               assertion=lambda data: isinstance(data, dict) and isinstance(data.get("token"), str) and bool(data["token"]))
            if login is not None:
                self.tokens[role] = login["token"]
                identity = self.check(role + "_identity", "/auth/userInfo", role=role,
                                      assertion=lambda data, r=role: isinstance(data, dict) and integer(data.get("id")) is not None and isinstance(data.get("roles"), list) and r in data["roles"])
                if identity is not None:
                    self.identities[role] = integer(identity["id"])
        credentials.clear()
        if set(self.identities) != set(ROLES):
            raise ValueError("role identity unavailable")
        students = self.tables["student_info"]
        matched = [row for row in students if integer(row.get("user_id")) == self.identities["student"]]
        teachers = [row for row in self.tables["teacher_info"] if integer(row.get("user_id")) == self.identities["teacher"]]
        self.record("student_unique_binding", len(matched) == 1, len(matched))
        self.record("teacher_unique_binding", len(teachers) == 1, len(teachers))
        if len(matched) != 1 or len(teachers) != 1:
            raise ValueError("identity fixture unavailable")
        self.student, self.teacher = matched[0], teachers[0]

    def page(self, name, path, total, *, page=1, size=100, role="admin"):
        separator = "&" if "?" in path else "?"
        expected_count = max(0, min(size, total - (page - 1) * size))
        data = self.check(name, f"{path}{separator}page={page}&size={size}", role=role,
                          assertion=lambda d: isinstance(d, dict) and integer(d.get("total")) == total
                          and isinstance(d.get("records"), list) and len(d["records"]) == expected_count
                          and integer(d.get("current")) == page and integer(d.get("size")) == size)
        return data["records"] if data is not None else []

    def populations(self):
        expected_students = self.tables["student_info"]
        expected_teachers = self.tables["teacher_info"]
        self.record("dataset_population_targets", len(expected_students) == 1200 and len(expected_teachers) == 80, len(expected_students))
        students = []
        for page in range(1, 13):
            students.extend(self.page(f"admin_student_page_{page:02d}", "/student/list", 1200, page=page))
        self.record("admin_student_pagination_unique", len(students) == 1200 and {integer(row.get("id")) for row in students} == {row["id"] for row in expected_students}, len(students))
        actual_years = Counter(str(row.get("grade")) for row in students)
        expected_years = Counter(str(row["grade"]) for row in expected_students)
        self.record("student_cohorts_four_times_300", actual_years == expected_years and len(actual_years) == 4 and set(actual_years.values()) == {300}, len(actual_years))
        actual_majors = Counter(row.get("majorName") for row in students)
        expected_majors = Counter(row["major_name"] for row in expected_students)
        self.record("student_majors_eight_times_150", actual_majors == expected_majors and len(actual_majors) == 8 and set(actual_majors.values()) == {150}, len(actual_majors))
        for index, grade in enumerate(sorted(expected_years), 1):
            rows = self.page(f"admin_grade_filter_{index:02d}", "/student/list?" + urllib.parse.urlencode({"grade": grade}), 300)
            self.record(f"admin_grade_filter_rows_{index:02d}", len(rows) == 100 and all(str(row.get("grade")) == grade for row in rows), len(rows))
        teachers = self.page("admin_teacher_page", "/teacher/list", 80)
        self.record("admin_teacher_ids", {integer(row.get("id")) for row in teachers} == {row["id"] for row in expected_teachers}, len(teachers))
        user_first = self.page("admin_user_page_first", "/user/list", 1281)
        user_last = self.page("admin_user_page_last", "/user/list", 1281, page=13)
        self.record("admin_user_password_never_returned", bool(user_first) and bool(user_last) and all("password" not in row for row in user_first + user_last), len(user_first) + len(user_last))
        self.record("admin_user_pages_disjoint", not ({row.get("id") for row in user_first} & {row.get("id") for row in user_last}))

    def portraits(self):
        teacher_id = self.teacher["id"]
        records = [row for row in self.tables["teacher_portrait_record"] if row["teacher_id"] == teacher_id]
        evaluations = [row for row in self.tables["teacher_evaluation"] if row["teacher_id"] == teacher_id]
        expected_teaching = {row["id"] for row in records if row["kind"] == "TEACHING"}
        expected_research = {row["id"] for row in records if row["kind"] == "RESEARCH"}
        def portrait_ok(data):
            return isinstance(data, dict) and all(isinstance(data.get(key), list) for key in ("teaching", "research", "evaluations")) and {row["id"] for row in data["teaching"]} == expected_teaching and {row["id"] for row in data["research"]} == expected_research and {row["id"] for row in data["evaluations"]} == {row["id"] for row in evaluations} and integer(data.get("evaluationCount")) == len(evaluations)
        portrait = self.check("teacher_own_portrait_records_evaluations", f"/teacher/{teacher_id}/portrait", role="teacher", assertion=portrait_ok)
        if portrait is not None:
            scores = [number(row["score"]) for row in evaluations if row["score"] is not None]
            average = (sum(scores) / len(scores)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP) if scores else None
            answered = [row for row in evaluations if row["satisfied"] is not None]
            satisfaction = (Decimal(sum(row["satisfied"] == 1 for row in answered) * 100) / len(answered)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP) if answered else None
            self.record("teacher_portrait_derived_metrics", equal_number(portrait.get("evaluationScore"), average) and equal_number(portrait.get("satisfaction"), satisfaction), len(evaluations))
        sid = self.student["id"]
        self.check("student_own_profile_ownership", f"/student/{sid}", role="student",
                   assertion=lambda d: isinstance(d, dict) and integer(d.get("id")) == sid and integer(d.get("userId")) == self.identities["student"] and all(d.get(key) is None for key in ("birthDate", "gpa", "credits")))
        candidates = [row for row in self.tables["student_info"] if row["grade"] == "2026"]
        if not candidates:
            raise ValueError("freshman fixture unavailable")
        freshman = candidates[0]
        self.academic(self.student, "nonfreshman", own=True)
        self.academic(freshman, "freshman", own=False)
        other = next(row for row in self.tables["student_info"] if row["user_id"] != self.identities["student"])
        self.check("student_other_profile_403", f"/student/{other['id']}", role="student", expected=403)
        self.check("student_other_academic_403", f"/student/{other['id']}/academic", role="student", expected=403)

    def dashboard(self):
        pending = [a for a in self.tables["mental_assessment"]
                   if a["level"] in {"中度", "重度", "高危"} and a["warning_status"] in {"OPEN", "IN_PROGRESS"}]
        self.check("dashboard_statistics_match_profiles_and_warnings", "/data/dashboard/statistics",
                   assertion=lambda d: isinstance(d, dict) and integer(d.get("teacherCount")) == 80
                   and integer(d.get("studentCount")) == 1200 and integer(d.get("todayCompletionCount")) == 0
                   and integer(d.get("warningCount")) == len({a["student_id"] for a in pending})
                   and d.get("date") == self.as_of.isoformat() and d.get("timezone") == "Asia/Shanghai")
        self.check("dashboard_distribution_match_profiles", "/data/dashboard/distribution",
                   assertion=lambda d: isinstance(d, dict) and isinstance(d.get("data"), list)
                   and {r.get("name"): integer(r.get("value")) for r in d["data"]} == {"教师": 80, "学生": 1200})
        expected_ids = {a["id"] for a in pending}
        self.check("dashboard_todos_are_actual_pending_rows", "/data/dashboard/todos?limit=10",
                   assertion=lambda d: isinstance(d, dict) and integer(d.get("total")) == len(pending)
                   and isinstance(d.get("items"), list) and len(d["items"]) == min(10, len(pending))
                   and all(r.get("kind") == "MENTAL_WARNING" and integer(r.get("sourceId")) in expected_ids for r in d["items"]))
        for period, length in (("week", 7), ("month", 30), ("year", 12)):
            if period == "year":
                months = [divmod(self.as_of.year*12+self.as_of.month-1-11+i, 12) for i in range(12)]
                labels = [f"{y:04d}-{m+1:02d}" for y, m in months]
                expected = {t: [sum(r["create_time"][:7] == label for r in self.tables[t]) for label in labels]
                            for t in ("teacher_info", "student_info")}
            else:
                days = [self.as_of-dt.timedelta(days=length-1-i) for i in range(length)]
                expected = {t: [sum(day(r["create_time"]) == date for r in self.tables[t]) for date in days]
                            for t in ("teacher_info", "student_info")}
            self.check("dashboard_" + period + "_trend_matches_record_dates", "/data/dashboard/trend?period=" + period,
                       assertion=lambda d, e=expected, n=length: isinstance(d, dict)
                       and isinstance(d.get("days"), list) and len(d["days"]) == n
                       and isinstance(d.get("teacherData"), list) and isinstance(d.get("studentData"), list)
                       and [integer(x) for x in d["teacherData"]] == e["teacher_info"]
                       and [integer(x) for x in d["studentData"]] == e["student_info"])
        self.check("student_dashboard_access_denied", "/data/dashboard/statistics", role="student", expected=403)

    def academic(self, student, case, *, own):
        sid = student["id"]
        expected = [row for row in self.tables["student_academic_record"] if row["student_id"] == sid]
        rows = self.check("admin_" + case + "_academic_records", f"/student/{sid}/academic",
                          assertion=lambda d: isinstance(d, list) and {integer(row.get("id")) for row in d} == {row["id"] for row in expected} and all(integer(row.get("studentId")) == sid for row in d))
        profile = self.check("admin_" + case + "_profile", f"/student/{sid}", assertion=lambda d: isinstance(d, dict) and integer(d.get("id")) == sid and str(d.get("grade")) == str(student["grade"]))
        if rows is not None and profile is not None:
            by_id = {row["id"]: row for row in expected}
            correct = all(equal_number(row.get("credit"), by_id[row["id"]]["credit"]) and equal_number(row.get("score"), by_id[row["id"]]["score"]) and equal_number(row.get("gradePoint"), by_id[row["id"]]["grade_point"]) and row.get("term") == by_id[row["id"]]["term"] for row in rows)
            total_credit = sum((number(row["credit"]) for row in rows), Decimal(0))
            gpa = (sum((number(row["credit"]) * number(row["gradePoint"]) for row in rows), Decimal(0)) / total_credit).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP) if total_credit else None
            credits = sum((number(row["credit"]) for row in rows if number(row["score"]) >= 60), Decimal(0))
            self.record(case + "_grade_gpa_credit_consistency", correct and equal_number(profile.get("gpa"), gpa) and equal_number(profile.get("credits"), credits), len(rows))
            if not expected:
                self.record("freshman_no_academic_unknown_gpa", rows == [] and profile.get("gpa") is None and equal_number(profile.get("credits"), 0), 0)
        if own:
            self.check("student_own_academic_scope", f"/student/{sid}/academic", role="student",
                       assertion=lambda d: isinstance(d, list) and len(d) == len(expected) and all(integer(row.get("studentId")) == sid for row in d))

    def mental(self):
        questionnaires = self.tables["mental_questionnaire"]
        current = [row for row in questionnaires if row["status"] == 1]
        self.record("dataset_questionnaire_three_current_one", len(questionnaires) == 3 and len(current) == 1, len(questionnaires))
        if len(current) != 1:
            raise ValueError("current questionnaire unavailable")
        self.current = current[0]
        qid = self.current["id"]
        question_ids = [row["id"] for row in self.tables["mental_question"] if row["questionnaire_id"] == qid]
        listed = self.page("admin_questionnaire_page", "/mental/questionnaires", 3)
        self.record("admin_questionnaire_metadata", {row["id"] for row in listed} == {row["id"] for row in questionnaires} and sum(row.get("status") == 1 for row in listed) == 1 and all(row.get("questions") == 20 for row in listed), len(listed))
        self.check("student_safe_twenty_question_template", f"/mental/student/questionnaires/{qid}", role="student", assertion=lambda d: safe_template(d, question_ids))
        student_user = self.identities["student"]
        answered = {row["questionnaire_id"] for row in self.tables["mental_response"] if row["student_id"] == self.student["id"]}
        self.check("student_questionnaire_window_and_answered", f"/mental/student/questionnaires?userId={student_user}", role="student",
                   assertion=lambda d: isinstance(d, list) and len(d) == 1 and integer(d[0].get("id")) == qid and d[0].get("inWindow") is True and d[0].get("answered") == (qid in answered))
        expected_response = {row["id"]: row for row in self.tables["mental_response"] if row["questionnaire_id"] == qid}
        expected_assessment = {row["response_id"]: row for row in self.tables["mental_assessment"] if row["questionnaire_id"] == qid}
        self.record("dataset_current_completion_900", len(expected_response) == 900, len(expected_response))
        self.check("admin_current_completion_raw_score", f"/mental/questionnaires/{qid}/completion", assertion=lambda d: isinstance(d, list) and len(d) == 900 and {row["id"] for row in d} == set(expected_response) and all(equal_number(row.get("score"), expected_response[row["id"]]["score"]) and row.get("level") == expected_assessment[row["id"]]["level"] for row in d))
        self.check("teacher_current_completion_score_restricted", f"/mental/questionnaires/{qid}/completion", role="teacher", assertion=lambda d: isinstance(d, list) and len(d) == 900 and all(row.get("score") is None for row in d))
        sid = self.student["id"]
        history = [row for row in self.tables["mental_assessment"] if row["student_id"] == sid]
        self.check("admin_student_mental_history_links", f"/mental/students/{sid}/assessments", assertion=lambda d: isinstance(d, list) and {row["id"] for row in d} == {row["id"] for row in history} and all(integer(row.get("studentId")) == sid and integer(row.get("responseId")) == next(r["response_id"] for r in history if r["id"] == row["id"]) for row in d))
        self.check("student_own_mental_history_scope_and_permissions", f"/mental/student/assessments?userId={student_user}", role="student", assertion=lambda d: isinstance(d, list) and {row["id"] for row in d} == {row["id"] for row in history} and all(integer(row.get("studentId")) == sid and all(row.get(key) is None for key in ("score", "level", "result", "suggestion")) for row in d))
        if history:
            latest = max(history, key=lambda row: (row["create_time"], row["id"]))
            response = next(row for row in self.tables["mental_response"] if row["id"] == latest["response_id"])
            own_ids = [row["id"] for row in self.tables["mental_question"] if row["questionnaire_id"] == latest["questionnaire_id"]]
            self.check("student_own_history_safe_snapshot_and_answers", f"/mental/student/assessments/{latest['id']}?userId={student_user}", role="student",
                       assertion=lambda d: isinstance(d, dict) and d.get("historySnapshotAvailable") is True and isinstance(d.get("assessment"), dict) and integer(d["assessment"].get("responseId")) == response["id"] and safe_template({"questionnaire": d.get("questionnaire"), "questions": d.get("questions"), "levelRules": []}, own_ids) and object_json(d.get("answersJson")) == object_json(response["answers"]))
        else:
            self.record("student_own_history_safe_snapshot_and_answers", False, 0)
        other_user = next(row["user_id"] for row in self.tables["student_info"] if row["user_id"] != student_user)
        self.check("student_other_mental_history_403", f"/mental/student/assessments?userId={other_user}", role="student", expected=403)
        self.check("student_management_mental_history_403", f"/mental/students/{sid}/assessments", role="student", expected=403)
        self.mental_statistics()

    def mental_statistics(self):
        assessments = self.tables["mental_assessment"]
        students = {row["id"]: row for row in self.tables["student_info"]}
        recent = [row for row in assessments if day(row["create_time"]) >= self.as_of - dt.timedelta(days=30)]
        levels = Counter(row["level"] for row in recent)
        total = len(recent)
        def overview_ok(data):
            return isinstance(data, dict) and data.get("businessTimezone") == "Asia/Shanghai" and integer(data.get("recentAssessmentCount")) == total and integer(data.get("todayCompleted")) == 0 and isinstance(data.get("recentStats"), list) and {row["level"]: integer(row["count"]) for row in data["recentStats"]} == dict(levels) and integer(data.get("goodRate")) == ((levels["正常"] + levels["轻度"]) * 100 // total if total else 0) and integer(data.get("attentionRate")) == (levels["中度"] * 100 // total if total else 0) and integer(data.get("interventionRate")) == ((levels["重度"] + levels["高危"]) * 100 // total if total else 0) and isinstance(data.get("warningList"), list) and len(data["warningList"]) <= 10 and all(row.get("level") in {"中度", "重度", "高危"} and row.get("warningStatus") != "RESOLVED" for row in data["warningList"]) and isinstance(data.get("trendData"), list)
        self.check("admin_mental_overview_actual_record_counts", "/mental/overview", assertion=overview_ok)
        expected = defaultdict(lambda: Counter(good=0, attention=0, intervention=0))
        for assessment in recent:
            category = "good" if assessment["level"] in {"正常", "轻度"} else "attention" if assessment["level"] == "中度" else "intervention"
            expected[students[assessment["student_id"]]["dept_name"]][category] += 1
        self.check("admin_mental_analysis_aggregate_consistency", "/mental/analysis", assertion=lambda d: isinstance(d, dict) and all(isinstance(d.get(key), list) for key in ("deptDistribution", "gradeComparison", "focusGroups", "genderAnalysis")) and {row["dept"]: {key: integer(row.get(key)) for key in ("good", "attention", "intervention")} for row in d["deptDistribution"]} == {dept: dict(counts) for dept, counts in expected.items()})
        warnings = [row for row in assessments if row["level"] in {"中度", "重度", "高危"}]
        expected_warnings = {row["id"]: row for row in warnings}
        collected = []
        pages = max(1, math.ceil(len(warnings) / 100))
        for page in range(1, pages + 1):
            collected.extend(self.page(f"admin_warning_page_{page:02d}", "/mental/warnings", len(warnings), page=page))
        self.record("admin_warning_states_and_ownership", {row["id"] for row in collected} == set(expected_warnings) and all(integer(row.get("studentId")) == expected_warnings[row["id"]]["student_id"] and row.get("warningStatus") == expected_warnings[row["id"]]["warning_status"] and row.get("warningNote") == expected_warnings[row["id"]]["warning_note"] for row in collected), len(collected))
        for state in ("OPEN", "IN_PROGRESS", "RESOLVED"):
            count = sum(row["warning_status"] == state for row in warnings)
            self.page("admin_warning_filter_" + state.lower(), "/mental/warnings?status=" + state, count)
        teacher_rows = self.page("teacher_warning_note_restricted_page", "/mental/warnings", len(warnings), role="teacher")
        self.record("teacher_warning_note_restricted", all("warningNote" not in row for row in teacher_rows), len(teacher_rows))

    def boundaries(self):
        self.check("anonymous_business_401", "/student/list?page=1&size=1", role="anonymous", expected=401)
        self.check("student_user_management_403", "/user/list?page=1&size=1", role="student", expected=403)
        self.check("student_mental_management_403", "/mental/overview", role="student", expected=403)

    def cleanup(self):
        for role in ROLES:
            token = self.tokens.get(role)
            if token:
                self.check(role + "_logout", "/auth/logout", role=role, method="POST")
                self.check(role + "_old_token_revoked_401", "/student/list?page=1&size=1", role=role, expected=401)
                self.tokens.pop(role, None)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Publicly redacted read smoke; only real login/logout writes.")
    parser.add_argument("--credentials", type=Path, required=True)
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--base-url", required=True)
    args = parser.parse_args(argv)
    smoke = None
    setup_failed = False
    try:
        dataset = private_json(args.dataset)
        credentials = private_json(args.credentials)
        smoke = Smoke(args.base_url, dataset)
        smoke.login(credentials)
        smoke.populations()
        smoke.dashboard()
        smoke.portraits()
        smoke.mental()
        smoke.boundaries()
    except Exception:
        setup_failed = True
        if smoke is not None:
            smoke.record("setup_or_unexpected_contract_failure", False)
    finally:
        if smoke is not None:
            smoke.cleanup()
    checks = smoke.checks if smoke is not None else [{"check": "setup_or_unexpected_contract_failure", "passed": False, "count": None}]
    if smoke is None:
        print(json.dumps(checks[0]), flush=True)
    failed = sum(not item["passed"] for item in checks)
    report = {"checks": checks, "total": len(checks), "passed": len(checks) - failed,
              "failed": failed, "read_only_except_login_logout": True}
    try:
        write_report(args.output, report)
    except Exception:
        print(json.dumps({"check": "redacted_report_write", "passed": False, "count": None}), flush=True)
        return 1
    return 1 if failed or setup_failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
