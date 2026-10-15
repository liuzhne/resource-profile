"""Deterministic, entirely synthetic teacher/student portraits; no IO or models.

GPA, population ratios, curricula, attendance patterns, evaluations and biographies
are demonstration conventions, not measurements of a real school. Rows use SQL
column names; DATE/DATETIME values are ISO strings for the caller's SQL writer.
"""

from __future__ import annotations

from collections import Counter, defaultdict
from datetime import date, datetime, timedelta
from decimal import Decimal, ROUND_HALF_UP
import random
import re


MAJORS = (
    ("计算机学院", "计算机科学与技术", "CS", "080901"),
    ("计算机学院", "软件工程", "SE", "080902"),
    ("数据科学与人工智能学院", "人工智能", "AI", "080717T"),
    ("数据科学与人工智能学院", "数据科学与大数据技术", "DS", "080910T"),
    ("信息与通信工程学院", "电子信息工程", "EE", "080701"),
    ("信息与通信工程学院", "通信工程", "CE", "080703"),
    ("数学与统计学院", "信息与计算科学", "IC", "070102"),
    ("数学与统计学院", "应用统计学", "AS", "071202"),
)
COHORTS = (2023, 2024, 2025, 2026)
TABLES = (
    "student_info", "teacher_info", "student_academic_record",
    "student_attendance", "teacher_portrait_record", "teacher_evaluation",
    "student_quality_record",
)
GENERAL = (
    ("大学英语Ⅰ", "思想道德与法治", "大学体育Ⅰ", "军事理论"),
    ("大学英语Ⅱ", "中国近现代史纲要", "大学体育Ⅱ", "军事技能"),
    ("大学英语Ⅲ", "马克思主义基本原理", "大学体育Ⅲ", "劳动教育Ⅰ"),
    ("大学英语Ⅳ", "毛泽东思想和中国特色社会主义理论体系概论", "大学体育Ⅳ", "劳动教育Ⅱ"),
    ("专业英语", "习近平新时代中国特色社会主义思想概论", "创新创业基础", "美育实践"),
    ("人文与科技写作", "科学技术与社会", "就业指导", "社会实践"),
    ("工程与研究伦理", "专业前沿研讨", "毕业设计准备", "职业发展实践"),
)
PROFESSIONAL = {
    "CS": (
        ("高等数学Ⅰ", "程序设计基础", "计算机导论", "程序设计实验"),
        ("高等数学Ⅱ", "线性代数", "离散数学", "面向对象程序设计"),
        ("数据结构", "计算机组成原理", "概率论与数理统计", "数字逻辑"),
        ("操作系统", "数据库原理", "计算机网络", "算法设计与分析"),
        ("编译原理", "软件工程", "Web开发", "信息安全基础"),
        ("分布式系统", "人工智能基础", "数据分析", "软件综合实践"),
        ("云计算系统", "系统安全", "人工智能应用", "工程项目研修"),
    ),
    "SE": (
        ("高等数学Ⅰ", "程序设计基础", "软件工程导论", "程序设计实验"),
        ("高等数学Ⅱ", "线性代数", "离散数学", "面向对象程序设计"),
        ("数据结构", "计算机组成原理", "数据库原理", "软件需求分析"),
        ("操作系统", "计算机网络", "软件设计", "算法设计与分析"),
        ("软件架构", "软件测试", "Web开发", "软件项目管理"),
        ("分布式系统", "移动应用开发", "软件质量保障", "软件综合实践"),
        ("服务计算", "软件工程案例", "软件安全", "工程项目研修"),
    ),
    "AI": (
        ("高等数学Ⅰ", "程序设计基础", "人工智能导论", "计算思维实践"),
        ("高等数学Ⅱ", "线性代数", "离散数学", "Python程序设计"),
        ("数据结构", "概率论与数理统计", "计算机组成原理", "数据库原理"),
        ("操作系统", "算法设计与分析", "最优化方法", "人工智能原理"),
        ("机器学习", "数据挖掘", "计算机视觉", "智能系统设计"),
        ("深度学习", "自然语言处理", "知识工程", "智能应用综合实践"),
        ("可信人工智能", "多模态计算", "智能系统部署", "工程项目研修"),
    ),
    "DS": (
        ("高等数学Ⅰ", "程序设计基础", "数据科学导论", "数据处理实践"),
        ("高等数学Ⅱ", "线性代数", "Python程序设计", "离散数学"),
        ("数据结构", "概率论与数理统计", "数据库原理", "统计计算"),
        ("操作系统", "计算机网络", "回归分析", "数据采集与清洗"),
        ("机器学习", "数据挖掘", "大数据存储", "数据可视化"),
        ("分布式计算", "时间序列分析", "数据治理", "大数据综合实践"),
        ("流式数据处理", "数据安全", "数据产品设计", "工程项目研修"),
    ),
    "EE": (
        ("高等数学Ⅰ", "程序设计基础", "电子信息导论", "工程制图"),
        ("高等数学Ⅱ", "大学物理Ⅰ", "电路分析", "电路实验"),
        ("模拟电子技术", "数字电子技术", "大学物理Ⅱ", "电子技术实验"),
        ("信号与系统", "电磁场与电磁波", "微机原理", "电子测量"),
        ("数字信号处理", "嵌入式系统", "通信原理", "传感器技术"),
        ("数字图像处理", "电子系统设计", "射频技术基础", "电子综合实践"),
        ("智能感知系统", "工业电子应用", "电子系统可靠性", "工程项目研修"),
    ),
    "CE": (
        ("高等数学Ⅰ", "程序设计基础", "通信工程导论", "工程制图"),
        ("高等数学Ⅱ", "大学物理Ⅰ", "电路分析", "电路实验"),
        ("模拟电子技术", "数字电子技术", "大学物理Ⅱ", "电子技术实验"),
        ("信号与系统", "电磁场与电磁波", "微机原理", "通信电路"),
        ("通信原理", "数字信号处理", "信息论与编码", "计算机网络"),
        ("移动通信", "光纤通信", "通信网络规划", "通信综合实践"),
        ("无线网络优化", "通信系统安全", "物联网通信", "工程项目研修"),
    ),
    "IC": (
        ("数学分析Ⅰ", "高等代数Ⅰ", "程序设计基础", "专业导论"),
        ("数学分析Ⅱ", "高等代数Ⅱ", "离散数学", "程序设计实验"),
        ("数学分析Ⅲ", "概率论", "常微分方程", "数据结构"),
        ("数值分析", "数理统计", "数据库原理", "优化方法"),
        ("算法设计与分析", "运筹学", "偏微分方程", "科学计算"),
        ("数学建模", "计算方法", "数据分析", "科学计算综合实践"),
        ("计算数学专题", "数值优化", "应用数学案例", "数学项目研修"),
    ),
    "AS": (
        ("数学分析Ⅰ", "高等代数Ⅰ", "程序设计基础", "统计学导论"),
        ("数学分析Ⅱ", "高等代数Ⅱ", "概率论", "数据整理实践"),
        ("数理统计", "常微分方程", "统计计算", "数据库基础"),
        ("应用回归分析", "抽样调查", "随机过程", "统计软件"),
        ("应用多元统计分析", "应用时间序列分析", "试验设计", "数据挖掘"),
        ("贝叶斯统计", "统计学习", "风险统计", "统计综合实践"),
        ("统计咨询", "社会调查方法", "因果推断基础", "统计项目研修"),
    ),
}
RESEARCH_AREAS = {
    "CS": "分布式系统、软件可靠性、校园数据平台",
    "SE": "软件工程、软件测试、教育应用系统",
    "AI": "机器学习、可信人工智能、智能教育应用",
    "DS": "数据挖掘、数据治理、教育数据分析",
    "EE": "嵌入式系统、智能传感、信号处理",
    "CE": "无线通信、物联网通信、通信网络优化",
    "IC": "数值计算、优化方法、数学建模",
    "AS": "应用统计、时间序列分析、统计学习",
}
TITLE_BOUNDS = {"教授": (43, 60, 12), "副教授": (35, 55, 7),
                "讲师": (28, 45, 2), "助教": (26, 33, 1)}


def _decimal(value) -> Decimal:
    return Decimal(str(value))


def _round2(value: Decimal) -> float:
    return float(value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


def _grade_point(score: int) -> Decimal:
    # Explicit synthetic 4.0 convention, not a claim about all universities.
    return Decimal(0) if score < 60 else min(Decimal(4), Decimal(1) + Decimal(score - 60) / 10)


def _years_before(value: date, years: int) -> date:
    try:
        return value.replace(year=value.year - years)
    except ValueError:
        return value.replace(year=value.year - years, day=28)


def _age(birth: date, on: date) -> int:
    return on.year - birth.year - ((on.month, on.day) < (birth.month, birth.day))


def _random_date(rng: random.Random, first: date, last: date) -> date:
    if first > last:
        raise ValueError(f"Impossible synthetic date range: {first}..{last}")
    return first + timedelta(days=rng.randrange((last - first).days + 1))


def _birth_for_age(rng: random.Random, on: date, age: int) -> date:
    return _random_date(rng, _years_before(on, age + 1) + timedelta(days=1), _years_before(on, age))


def _courses(code: str, semester: int) -> list[dict]:
    if not 1 <= semester <= 7:
        raise ValueError("Only six completed and one current synthetic semester are supported")
    result = []
    for index, (name, credit) in enumerate(zip(PROFESSIONAL[code][semester - 1], (4, 3, 3, 2)), 1):
        result.append({"course_code": f"DEMO-{code}-S{semester}-{index}", "course_name": name, "credit": credit})
    for index, (name, credit) in enumerate(zip(GENERAL[semester - 1], (3, 3, 1, 1)), 1):
        result.append({"course_code": f"DEMO-GEN-S{semester}-{index}", "course_name": name, "credit": credit})
    return result


def _term(cohort: int, semester: int) -> str:
    start = cohort + (semester - 1) // 2
    return f"{start}-{start + 1}-{1 if semester % 2 else 2}"


def _holiday(day: date) -> bool:
    return (date(2026, 9, 25) <= day <= date(2026, 9, 27)
            or date(2026, 10, 1) <= day <= date(2026, 10, 7))


def _timetable(rng: random.Random, courses: list[dict]) -> dict[int, list[str]]:
    # One row represents a classroom meeting, possibly consecutive teaching periods.
    weekly = rng.choice((12, 14, 16))
    counts = {12: (3, 3, 2, 2, 2), 14: (3, 3, 3, 3, 2), 16: (4, 3, 3, 3, 3)}[weekly]
    codes = [c["course_code"] for c in courses]
    rng.shuffle(codes)
    cursor = 0
    result = {}
    for weekday, count in enumerate(counts):
        result[weekday] = [codes[(cursor + i) % len(codes)] for i in range(count)]
        cursor += count
    return result


def build_portraits(*, as_of: date, rng: random.Random, next_ids: dict[str, int],
                   student_user_ids: list[int], teacher_user_ids: list[int]) -> tuple[dict[str, list[dict]], dict]:
    """Build the confirmed 1200/80 synthetic population and increment next_ids.

The first supplied student account receives a 2024-cohort portrait. No unfinished
grades or model output are generated. Dynamic events stop at the previous day.
"""
    if not isinstance(as_of, date) or isinstance(as_of, datetime) or not date(2026, 9, 2) <= as_of <= date(2026, 12, 31):
        raise ValueError("This fixed 2023–2026 demonstration requires as_of in 2026-09-02..2026-12-31")
    if len(student_user_ids) != 1200 or len(teacher_user_ids) != 80:
        raise ValueError("Confirmed population requires exactly 1200 student and 80 teacher accounts")
    all_users = student_user_ids + teacher_user_ids
    if len(set(all_users)) != 1280 or any(type(user_id) is not int or user_id <= 0 for user_id in all_users):
        raise ValueError("Account IDs must be distinct positive integers across the population")
    for table in TABLES:
        if table not in next_ids or type(next_ids[table]) is not int or next_ids[table] <= 0:
            raise ValueError(f"Missing or invalid next ID for {table}")

    tables = {table: [] for table in TABLES}
    cutoff = as_of - timedelta(days=1)
    cutoff_datetime = datetime.combine(cutoff, datetime.max.time()).replace(microsecond=0)

    def event_times(event: date, max_delay: int = 0) -> dict:
        # Simulated original registration/recording timestamps, disclosed in the manifest.
        recorded_day = min(event + timedelta(days=rng.randint(0, max_delay)), cutoff)
        created = datetime.combine(recorded_day, datetime.min.time()).replace(
            hour=rng.randint(15, 20), minute=rng.randrange(60), second=rng.randrange(60))
        updated = created
        if rng.random() < 0.08:
            updated = min(created + timedelta(days=rng.randint(1, 7), minutes=rng.randint(0, 90)), cutoff_datetime)
        return {"create_time": created.isoformat(sep=" "), "update_time": updated.isoformat(sep=" ")}

    def append(table: str, data: dict) -> dict:
        if "create_time" not in data or "update_time" not in data:
            raise ValueError(f"Explicit simulated event timestamps required for {table}")
        row = {"id": next_ids[table], **data, "deleted": 0}
        next_ids[table] += 1
        tables[table].append(row)
        return row

    used_names = set()
    surnames = tuple("陈林黄张王李刘吴周徐郑赵许郭何曾谢罗梁宋唐邓韩曹冯蒋彭萧蔡潘叶余杜孟程苏陆马朱顾钟沈胡方夏任汪廖秦丁魏严孔侯龙钱白")
    first_chars = tuple("知思嘉景明文清安远若亦子书云晨锦怀星予修和念昕宸宁承柏时卓舒晓")
    second_chars = tuple("宁然川禾言语舟衡澄辰溪扬文熙岚岳庭雅晴瑜枫尧嘉琪竹涵桐轩霖心")

    def name() -> str:
        for _ in range(10000):
            value = rng.choice(surnames) + rng.choice(first_chars) + rng.choice(second_chars)
            if value not in used_names:
                used_names.add(value)
                return value
        raise ValueError("Synthetic name pool exhausted")

    genders = [1] * 624 + [0] * 576  # 52/48% is a disclosed demo assumption.
    rng.shuffle(genders)
    class_schedules = {}
    assignments = []
    # First account (the caller's dedicated demonstration student) has full history.
    for cohort in (2024, 2023, 2025, 2026):
        cohort_position = COHORTS.index(cohort)
        for major_index, major in enumerate(MAJORS):
            count = 36 if (cohort_position + major_index) % 2 == 0 else 39
            assignments.extend([(cohort, major_index, major)] * count)

    for index, (cohort, major_index, (dept, major, code, _official_code)) in enumerate(assignments):
        enrollment = date(cohort, 9, 1)
        entry_age = 19 if rng.random() < 0.10 else 18
        completed_semesters = 2 * (2026 - cohort)
        student = append("student_info", {
            "user_id": student_user_ids[index], "student_id": f"DEMO-S{cohort}{major_index + 1:02d}{index + 1:04d}",
            "name": name(), "gender": genders[index], "birth_date": _birth_for_age(rng, enrollment, entry_age).isoformat(),
            "dept_id": 9001 + major_index // 2, "dept_name": dept,
            "major_id": 9101 + major_index, "major_name": major,
            "grade": str(cohort), "class_name": f"{major}{cohort}-1班",
            "enrollment_date": enrollment.isoformat(), "expected_graduation": date(cohort + 4, 6, 30).isoformat(),
            "gpa": None, "credits": 0, "status": 1, **event_times(enrollment),
        })
        p = rng.random()
        ability = rng.gauss(67, 3) if p < 0.04 else rng.gauss(87, 2.5) if p < 0.16 else rng.gauss(78.5, 4)
        downward = completed_semesters >= 4 and rng.random() < 0.035
        weighted_gp = Decimal(0)
        total_credit = Decimal(0)
        earned_credit = 0
        for semester in range(1, completed_semesters + 1):
            term = _term(cohort, semester)
            shift = 0.3 * (semester - 1)
            if downward and semester >= completed_semesters - 1:
                shift -= 10
            for course in _courses(code, semester):
                difficulty = -2 if course["credit"] == 4 else 1 if course["credit"] == 1 else 0
                score = max(42, min(98, int(round(rng.gauss(ability + shift + difficulty, 5)))))
                gp = _grade_point(score)
                credit = course["credit"]
                append("student_academic_record", {
                    "student_id": student["id"], "term": term, **course,
                    "credit": float(credit), "score": float(score), "grade_point": float(gp),
                    **event_times(date(cohort + (semester + 1) // 2, 1 if semester % 2 else 7, 25 if semester % 2 else 5), 7),
                })
                weighted_gp += gp * credit
                total_credit += credit
                if score >= 60:
                    earned_credit += credit
        student["gpa"] = _round2(weighted_gp / total_credit) if total_credit else None
        student["credits"] = earned_credit
        current_semester = completed_semesters + 1
        schedule_key = (cohort, code)
        if schedule_key not in class_schedules:
            class_schedules[schedule_key] = _timetable(rng, _courses(code, current_semester))
        schedule = class_schedules[schedule_key]
        attendance_group = rng.random()
        present_probability = rng.uniform(0.60, 0.74) if attendance_group < 0.035 else rng.uniform(0.80, 0.90) if attendance_group < 0.145 else rng.uniform(0.95, 0.995)
        day = date(2026, 9, 1)
        while day <= cutoff:
            if day.weekday() < 5 and not _holiday(day):
                for course_code in schedule[day.weekday()]:
                    draw = rng.random()
                    if draw < present_probability:
                        status, remark = 0, None
                    else:
                        non_present = rng.random()
                        if non_present < 0.45:
                            status, remark = 1, "课堂签到迟到（合成）"
                        elif non_present < 0.72:
                            status, remark = 2, "未完成课堂签到，待人工核实（合成）"
                        else:
                            status, remark = 3, rng.choice(("事假已报备（合成）", "病假已报备（合成）", "校内活动请假（合成）"))
                    append("student_attendance", {"student_id": student["id"], "attend_date": day.isoformat(),
                           "status": status, "course_code": course_code, "remark": remark, **event_times(day, 1)})
            day += timedelta(days=1)

    quality_students = []
    for cohort, recipients in ((2023, 240), (2024, 225), (2025, 180), (2026, 60)):
        candidates = [s for s in tables["student_info"] if s["grade"] == str(cohort)]
        rng.shuffle(candidates)
        for student in candidates[:recipients]:
            count = 1 if cohort == 2026 else rng.choice((1, 1, 2, 2, 3, 3, 4))
            quality_students.extend([student] * count)
    quality_count = len(quality_students)
    volunteer_count = round(quality_count * 0.60)
    honor_count = round(quality_count * 0.25)
    kinds = ["VOLUNTEER"] * volunteer_count + ["HONOR"] * honor_count + ["COMPETITION"] * (quality_count - volunteer_count - honor_count)
    rng.shuffle(kinds)
    for student, kind in zip(quality_students, kinds):
        start = max(date.fromisoformat(student["enrollment_date"]), cutoff - timedelta(days=600))
        event = _random_date(rng, start, cutoff)
        if kind == "VOLUNTEER":
            title = rng.choice(("校园图书整理服务", "社区数字技能课堂", "迎新志愿服务", "校园绿色行动"))
            description = f"参与服务{rng.choice((2, 4, 6, 8))}小时；为合成活动记录，不自动折算学分。"
        elif kind == "HONOR":
            title = "学院迎新服务表彰" if student["grade"] == "2026" else rng.choice(("学院学习进步表彰", "校内实践项目表彰", "学院志愿服务表彰"))
            description = "院内合成表彰，展示个人成长经历；不是国家级或真实学校颁发的证书。"
        else:
            title = "校园创新交流赛" if student["grade"] == "2026" else rng.choice(("校园数据应用挑战赛", "学院工程设计交流赛", "校内数学建模实践赛"))
            description = rng.choice(("合成校内活动：完成团队作品展示。", "合成院内活动：获得优秀项目展示名额。", "合成校内活动：参与问题分析与方案汇报。"))
        append("student_quality_record", {"student_id": student["id"], "kind": kind,
               "title": f"{title}（合成）", "event_date": event.isoformat(), "description": description, **event_times(event, 3)})

    teacher_specs = []
    for dept_index in range(4):
        # 20 teachers/college: globally 16 professors, 28 associates, 30 lecturers, 6 assistants.
        lecturer_count = 8 if dept_index < 2 else 7
        assistant_count = 1 if dept_index < 2 else 2
        specs = [("教授", "博士")] * 4 + [("副教授", "博士")] * 7
        specs += [("讲师", "博士")] * 4 + [("讲师", "硕士")] * (lecturer_count - 4)
        specs += [("助教", "硕士")] * assistant_count
        rng.shuffle(specs)
        teacher_specs.extend((dept_index, title, education) for title, education in specs)
    teacher_genders = [0] * 40 + [1] * 40
    rng.shuffle(teacher_genders)
    evaluation_counts = [12, 16, 20, 24, 28] * 16
    rng.shuffle(evaluation_counts)
    satisfaction_values = [True] * 1280 + [False] * 160 + [None] * 160
    rng.shuffle(satisfaction_values)
    evaluation_cursor = 0
    for index, (dept_index, title, education) in enumerate(teacher_specs):
        dept, major, code, _ = MAJORS[2 * dept_index + index % 2]
        lower_age, upper_age, minimum_tenure = TITLE_BOUNDS[title]
        minimum_join_age = 27 if education == "博士" else 24
        # Reserve a full year of slack for birthdays and the previous-day cutoff.
        lower_age = max(lower_age, minimum_join_age + minimum_tenure + 1)
        age = rng.randint(lower_age, upper_age)
        birth = _birth_for_age(rng, as_of, age)
        earliest_join = _years_before(birth, -minimum_join_age)
        latest_join = min(_years_before(cutoff, minimum_tenure), date(2025, 8, 31))
        join = _random_date(rng, earliest_join, latest_join)
        teacher = append("teacher_info", {
            "user_id": teacher_user_ids[index], "employee_id": f"DEMO-T{dept_index + 1:02d}{index + 1:04d}",
            "name": name(), "gender": teacher_genders[index], "birth_date": birth.isoformat(),
            "dept_id": 9001 + dept_index, "dept_name": dept, "title": title, "education": education,
            "school": None if rng.random() < 0.06 else rng.choice(("澄川大学（合成）", "明湖理工大学（合成）", "青岚师范大学（合成）")),
            "major": major, "research_area": RESEARCH_AREAS[code], "join_date": join.isoformat(),
            "political": None, "phone": None, "email": f"teacher.{index + 1:04d}@educare-demo.example", "status": 1,
            **event_times(join),
        })
        teaching_count = rng.randint(2, 5)
        research_count = rng.randint(2, 4) if title in ("教授", "副教授") else rng.randint(1, 3)
        lower_event = max(join + timedelta(days=30), cutoff - timedelta(days=900))
        course_pool = [course for semester in (3, 4, 5, 6) for course in _courses(code, semester)[:4]]
        for event_index in range(teaching_count):
            course = rng.choice(course_pool)
            event = _random_date(rng, lower_event, cutoff)
            append("teacher_portrait_record", {"teacher_id": teacher["id"], "kind": "TEACHING",
                   "title": f"《{course['course_name']}》{rng.choice(('课程案例建设', '教学设计研讨', '课堂实践改进'))}（合成）",
                   "event_date": event.isoformat(), "description": "校内合成教研记录：围绕课程内容、学习任务和实践反馈进行改进；不代表真实项目立项或获奖。", **event_times(event, 3)})
        for event_index in range(research_count):
            event = _random_date(rng, lower_event, cutoff)
            topic = rng.choice(RESEARCH_AREAS[code].split("、"))
            append("teacher_portrait_record", {"teacher_id": teacher["id"], "kind": "RESEARCH",
                   "title": f"{topic}{rng.choice(('应用方法研究', '原型验证项目', '校内协作课题'))}（合成）",
                   "event_date": event.isoformat(), "description": "合成校内研究经历，记录方法探索与原型验证；没有真实基金批准号、论文DOI或经费凭证。", **event_times(event, 3)})
        for evaluation_index in range(evaluation_counts[index]):
            satisfied = satisfaction_values[evaluation_cursor]
            evaluation_cursor += 1
            score = rng.gauss(90, 4) if satisfied is True else rng.gauss(73, 7) if satisfied is False else rng.gauss(85, 6)
            score = _round2(_decimal(max(55, min(99, score))))
            course = rng.choice(course_pool)
            event = _random_date(rng, date(2026, 7, 5), min(date(2026, 7, 20), cutoff))
            comment = rng.choice(("课程案例清楚，实践反馈及时。", "课堂组织有条理，希望继续保留讨论环节。")) if satisfied is True else rng.choice(("建议增加难点讲解和答疑时间。", "实践任务进度较快，希望增加分阶段反馈。")) if satisfied is False else "完成课程评价，未回答是否满意。"
            append("teacher_evaluation", {"teacher_id": teacher["id"], "title": f"2025-2026-2《{course['course_name']}》课程评教（合成）",
                   "event_date": event.isoformat(), "score": score, "satisfied": None if satisfied is None else int(satisfied),
                   "comment": f"{comment}（合成意见）", **event_times(event, 2)})

    validate_portraits(tables, as_of)
    attendance = Counter(row["status"] for row in tables["student_attendance"])
    gpas = [s["gpa"] for s in tables["student_info"] if s["gpa"] is not None]
    summary = {
        "synthetic": True, "as_of": as_of.isoformat(), "events_through": cutoff.isoformat(),
        "table_counts": {table: len(rows) for table, rows in tables.items()},
        "cohort_counts": dict(Counter(s["grade"] for s in tables["student_info"])),
        "major_counts": dict(Counter(s["major_name"] for s in tables["student_info"])),
        "class_size_distribution": dict(Counter(Counter(s["class_name"] for s in tables["student_info"]).values())),
        "student_gender": {"male": 624, "female": 576, "provenance": "synthetic assumption"},
        "teacher_titles": dict(Counter(t["title"] for t in tables["teacher_info"])),
        "teacher_education": dict(Counter(t["education"] for t in tables["teacher_info"])),
        "gpa": {"known": len(gpas), "unknown": 1200 - len(gpas), "min": min(gpas), "max": max(gpas)},
        "failed_course_records": sum(row["score"] < 60 for row in tables["student_academic_record"]),
        "attendance": {"total": sum(attendance.values()), "present": attendance[0], "late": attendance[1],
                       "absent": attendance[2], "leave": attendance[3], "from": "2026-09-01", "to": cutoff.isoformat()},
        "quality_kind_counts": dict(Counter(row["kind"] for row in tables["student_quality_record"])),
        "quality_recipients": len({row["student_id"] for row in tables["student_quality_record"]}),
        "evaluation": {"total": 1600, "satisfied": 1280, "not_satisfied": 160, "unanswered": 160},
        "grading_policy": "Demo only: score<60 =>0; otherwise min(4,1+(score-60)/10); credit-weighted HALF_UP GPA.",
        "timestamp_policy": "Simulated original enrollment/join and record creation history; not actual production usage history.",
        "weekly_class_meetings_distribution": dict(Counter(sum(map(len, schedule.values())) for schedule in class_schedules.values())),
    }
    return tables, summary


def validate_portraits(tables: dict[str, list[dict]], as_of: date) -> None:
    """Independently recompute ownership, chronology and aggregates; raise on error."""
    def require(condition: bool, message: str) -> None:
        if not condition:
            raise ValueError(f"Portrait validation failed: {message}")

    require(set(TABLES).issubset(tables), "missing portrait table")
    cutoff = as_of - timedelta(days=1)
    timestamp_cutoff = datetime.combine(cutoff, datetime.max.time()).replace(microsecond=0)
    lengths = {
        "name": 50, "student_id": 50, "employee_id": 50, "dept_name": 100, "major_name": 100,
        "grade": 20, "class_name": 50, "title": 200, "education": 50, "school": 100,
        "major": 100, "research_area": 500, "political": 100, "phone": 30, "email": 100,
        "term": 32, "course_code": 64, "course_name": 128, "remark": 255,
        "description": 2000, "comment": 2000,
    }
    for table in TABLES:
        rows = tables[table]
        ids = [row["id"] for row in rows]
        require(len(ids) == len(set(ids)) and all(type(i) is int and i > 0 for i in ids), f"{table} IDs")
        for row in rows:
            require(row["deleted"] == 0, f"{table} logical-delete marker")
            for key, limit in lengths.items():
                value = row.get(key)
                if isinstance(value, str):
                    require(len(value) <= limit, f"{table}.{key} length")
            created = datetime.fromisoformat(row["create_time"])
            updated = datetime.fromisoformat(row["update_time"])
            require(created <= updated <= timestamp_cutoff, f"{table} timestamp chronology")
    students = {s["id"]: s for s in tables["student_info"]}
    teachers = {t["id"]: t for t in tables["teacher_info"]}
    require(len(students) == 1200 and len(teachers) == 80, "population size")
    people = list(students.values()) + list(teachers.values())
    require(len({p["user_id"] for p in people}) == 1280, "shared account ownership")
    require(all(type(p["user_id"]) is int and p["user_id"] > 0 for p in people), "account ID domain")
    require(len({p["name"] for p in people}) == 1280, "synthetic name uniqueness")
    require(all(p["name"] and not any(bad in p["name"] for bad in ("同学", "老师", "测试", "X")) for p in people), "human-readable names")
    require(len({s["student_id"] for s in students.values()}) == 1200, "student numbers")
    require(len({t["employee_id"] for t in teachers.values()}) == 80, "employee numbers")
    require(Counter(s["gender"] for s in students.values()) == Counter({1: 624, 0: 576}), "52/48 gender assumption")
    require(Counter(s["grade"] for s in students.values()) == Counter({str(y): 300 for y in COHORTS}), "cohort counts")
    require(Counter(s["major_name"] for s in students.values()) == Counter({m[1]: 150 for m in MAJORS}), "major counts")
    cohort_major_counts = Counter((s["grade"], s["major_name"]) for s in students.values())
    major_by_name = {major: (index, dept, code) for index, (dept, major, code, _) in enumerate(MAJORS)}
    for student in students.values():
        cohort = int(student["grade"])
        index, dept, code = major_by_name[student["major_name"]]
        require(student["status"] == 1 and student["dept_name"] == dept, "student status/department")
        expected_size = 36 if (COHORTS.index(cohort) + index) % 2 == 0 else 39
        require(cohort_major_counts[(student["grade"], student["major_name"])] == expected_size, "cohort-major size")
        enrollment = date.fromisoformat(student["enrollment_date"])
        birth = date.fromisoformat(student["birth_date"])
        graduation = date.fromisoformat(student["expected_graduation"])
        require(enrollment == date(cohort, 9, 1) and birth < enrollment <= cutoff, "enrollment chronology")
        require(datetime.fromisoformat(student["create_time"]).date() >= enrollment, "student registration timestamp")
        require(_age(birth, enrollment) in (18, 19), "student entry age")
        require(graduation == date(cohort + 4, 6, 30), "four-year expected graduation")

    academics = defaultdict(list)
    seen_courses = set()
    failed = 0
    for row in tables["student_academic_record"]:
        require(row["student_id"] in students, "academic ownership")
        student = students[row["student_id"]]
        cohort = int(student["grade"])
        match = re.fullmatch(r"(\d{4})-(\d{4})-([12])", row["term"])
        require(match is not None, "term format")
        year, end_year, part = map(int, match.groups())
        require(end_year == year + 1 and cohort <= year < 2026, "completed term dates")
        semester = 2 * (year - cohort) + part
        semester_end = date(end_year, 1, 20) if part == 1 else date(end_year, 6, 30)
        require(datetime.fromisoformat(row["create_time"]).date() >= semester_end, "grade recording after semester completion")
        _, _, code = major_by_name[student["major_name"]]
        catalog = {c["course_code"]: c for c in _courses(code, semester)}
        require(row["course_code"] in catalog, "academic curriculum")
        reference = catalog[row["course_code"]]
        credit = _decimal(row["credit"])
        require(row["course_name"] == reference["course_name"] and credit == reference["credit"], "course code/name/credit mismatch")
        require(credit > 0 and credit.as_tuple().exponent >= -1, "credit range/precision")
        score = _decimal(row["score"])
        gp = _decimal(row["grade_point"])
        require(score.is_finite() and 0 <= score <= 100 and score == int(score), "integer score range")
        require(gp == _grade_point(int(score)), "score/GPA mapping")
        signature = (row["student_id"], row["term"], row["course_code"])
        require(signature not in seen_courses, "duplicate academic record")
        seen_courses.add(signature)
        academics[row["student_id"]].append(row)
        failed += score < 60
    require(len(tables["student_academic_record"]) == 28800, "completed-course count")
    require(0 < failed < 28800 * 0.05, "small disclosed synthetic failure proportion")
    for student in students.values():
        rows = academics[student["id"]]
        completed = 2 * (2026 - int(student["grade"]))
        require(len(rows) == 8 * completed, "completed semesters by cohort")
        if not rows:
            require(student["gpa"] is None and student["credits"] == 0, "freshman unknown GPA and zero earned credits")
            continue
        credit_total = sum((_decimal(r["credit"]) for r in rows), Decimal(0))
        weighted = sum((_decimal(r["credit"]) * _decimal(r["grade_point"]) for r in rows), Decimal(0))
        expected_gpa = weighted / credit_total
        require(credit_total == completed * 20, "synthetic term credit total")
        require(_decimal(student["gpa"]) == _decimal(_round2(expected_gpa)), "profile weighted GPA")
        earned = sum(int(r["credit"]) for r in rows if r["score"] >= 60)
        require(type(student["credits"]) is int and student["credits"] == earned, "earned credits")

    seen_attendance = set()
    attendance_dates = defaultdict(set)
    for row in tables["student_attendance"]:
        require(row["student_id"] in students, "attendance ownership")
        student = students[row["student_id"]]
        day = date.fromisoformat(row["attend_date"])
        require(date(2026, 9, 1) <= day <= cutoff and day.weekday() < 5 and not _holiday(day), "attendance calendar")
        require(datetime.fromisoformat(row["create_time"]).date() >= day, "attendance recording timestamp")
        require(row["status"] in (0, 1, 2, 3), "attendance status")
        _, _, code = major_by_name[student["major_name"]]
        current = 2 * (2026 - int(student["grade"])) + 1
        current_codes = {c["course_code"] for c in _courses(code, current)}
        require(row["course_code"] in current_codes, "current registered course for attendance")
        signature = (row["student_id"], day, row["course_code"])
        require(signature not in seen_attendance, "duplicate attendance meeting")
        seen_attendance.add(signature)
        attendance_dates[(student["id"], day)].add(row["course_code"])
    require(all(len(codes) <= 4 for codes in attendance_dates.values()), "daily classroom meeting cap")
    require({sid for sid, _ in attendance_dates} == set(students), "attendance exists for every student")
    # Same cohort/course's weekday timetable is stable; all complete teaching weeks have 12–16 meetings.
    by_student_week = Counter((sid, day - timedelta(days=day.weekday())) for sid, day, _course in seen_attendance)
    for (sid, monday), count in by_student_week.items():
        week_days = [monday + timedelta(days=i) for i in range(5)]
        if all(date(2026, 9, 1) <= day <= cutoff and not _holiday(day) for day in week_days):
            require(count in (12, 14, 16), "complete teaching week's meetings")

    require(Counter(t["title"] for t in teachers.values()) == Counter({"教授": 16, "副教授": 28, "讲师": 30, "助教": 6}), "title quota")
    require(Counter(t["education"] for t in teachers.values()) == Counter({"博士": 60, "硕士": 20}), "education quota")
    require(Counter(t["dept_name"] for t in teachers.values()) == Counter({MAJORS[2 * i][0]: 20 for i in range(4)}), "teacher college counts")
    for teacher in teachers.values():
        birth = date.fromisoformat(teacher["birth_date"])
        join = date.fromisoformat(teacher["join_date"])
        low, high, tenure = TITLE_BOUNDS[teacher["title"]]
        require(low <= _age(birth, as_of) <= high, "teacher current age")
        require(birth < join <= _years_before(cutoff, tenure), "teacher tenure chronology")
        require(datetime.fromisoformat(teacher["create_time"]).date() >= join, "teacher registration timestamp")
        require(_age(birth, join) >= (27 if teacher["education"] == "博士" else 24), "teacher entry/education chronology")
        require(teacher["status"] == 1 and teacher["phone"] is None, "teacher status/contact safety")
        require(teacher["email"].endswith(".example") and "@" in teacher["email"], "reserved teacher email")
        require(teacher["dept_name"] == major_by_name[teacher["major"]][1], "teacher major/department")

    for table in ("teacher_portrait_record", "teacher_evaluation"):
        for row in tables[table]:
            require(row["teacher_id"] in teachers, f"{table} ownership")
            teacher = teachers[row["teacher_id"]]
            event = date.fromisoformat(row["event_date"])
            require(date.fromisoformat(teacher["join_date"]) <= event <= cutoff, f"{table} event chronology")
            require(datetime.fromisoformat(row["create_time"]).date() >= event, f"{table} recording timestamp")
            require(row["title"] and "合成" in row["title"], f"{table} synthetic label")
            if table == "teacher_portrait_record":
                require(row["kind"] in ("TEACHING", "RESEARCH"), "teacher record kind")
            else:
                score = _decimal(row["score"])
                require(score.is_finite() and 0 <= score <= 100 and score.as_tuple().exponent >= -2, "evaluation score")
                require(row["satisfied"] is None or (type(row["satisfied"]) in (int, bool) and row["satisfied"] in (0, 1)), "nullable satisfaction")
    evaluations = tables["teacher_evaluation"]
    require(len(evaluations) == 1600, "evaluation count")
    require(sum(row["satisfied"] == 1 for row in evaluations) == 1280, "satisfied quota")
    require(sum(row["satisfied"] == 0 for row in evaluations) == 160, "unsatisfied quota")
    require(sum(row["satisfied"] is None for row in evaluations) == 160, "unanswered quota")
    evaluations_by_teacher = Counter(row["teacher_id"] for row in evaluations)
    require(Counter(evaluations_by_teacher.values()) == Counter({count: 16 for count in (12, 16, 20, 24, 28)}), "variable evaluation counts")
    for row in tables["student_quality_record"]:
        require(row["student_id"] in students, "quality ownership")
        student = students[row["student_id"]]
        event = date.fromisoformat(row["event_date"])
        require(date.fromisoformat(student["enrollment_date"]) <= event <= cutoff, "quality event chronology")
        require(datetime.fromisoformat(row["create_time"]).date() >= event, "quality recording timestamp")
        require(row["kind"] in ("COMPETITION", "HONOR", "VOLUNTEER") and row["title"] and "合成" in row["title"], "quality type/title")
    recipients = {row["student_id"] for row in tables["student_quality_record"]}
    require(len(recipients) == 705, "quality recipient quota")
    require(Counter(students[sid]["grade"] for sid in recipients) == Counter({"2023": 240, "2024": 225, "2025": 180, "2026": 60}), "quality cohort quotas")
