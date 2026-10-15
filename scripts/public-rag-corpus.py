#!/usr/bin/env python3
"""Reviewed public summaries for the non-commercial campus demonstration.

``check`` is offline; ``export`` prints reviewed public upsert payloads;
``apply`` writes only these documents through the loopback admin API. No LLM is
called. Embeddings are performed by the configured application provider.
Existing documents, including old probe documents, are not deleted by this tool.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import urllib.error
import urllib.request
from urllib.parse import urlsplit

REVIEWED_AT = "2026-10-11"
SOURCES = {
    "student-rules": {
        "title": "普通高等学校学生管理规定（教育部令第41号）",
        "url": "https://www.gov.cn/gongbao/content/2017/content_5220900.htm",
        "publisher": "教育部／国务院公报", "published": "2017-02-04",
        "basis": "第13、18、19、55、59—60条；政府公报链接部分客户端403，正文交叉核对教育部原文",
        "canonical_url": "https://www.moe.gov.cn/srcsite/A02/s5911/moe_621/201702/t20170216_296385.html",
        "rights": "原创短摘要，未复制政策原文；原始发布内容权利归发布机构",
    },
    "campus-mental": {
        "title": "高等学校学生心理健康教育指导纲要（教党〔2018〕41号）",
        "url": "https://www.csdp.edu.cn/article/4105.html",
        "publisher": "教育部／教育部学校规划建设发展中心", "published": "2018-07-04",
        "basis": "四、主要任务第3、4项；教育部直属中心完整转载",
        "rights": "原创短摘要，未复制政策原文；原始发布内容权利归发布机构",
    },
    "student-aid": {
        "title": "2025年高校学生资助政策手册",
        "url": "https://www.gov.cn/zhengce/202507/content_7032288.htm",
        "publisher": "中国政府网", "published": "2025-07",
        "basis": "国家助学金、助学贷款、勤工助学；搜索索引正文核对，直接抓取403",
        "rights": "原创短事实摘要，不复制报道表达；原文权利归发布机构",
    },
    "who-stress": {
        "title": "Doing What Matters in Times of Stress",
        "url": "https://www.who.int/publications/i/item/9789240003927",
        "publisher": "WHO", "published": "2020-04-29",
        "basis": "Overview；可用中文版本；短日常练习",
        "rights": "非商业演示原创中文短摘要；CC BY-NC-SA 3.0 IGO；非WHO翻译或认可",
    },
    "who-anxiety": {
        "title": "Anxiety disorders",
        "url": "https://who.int/news-room/fact-sheets/detail/anxiety-disorders",
        "publisher": "WHO", "published": "官网动态更新",
        "basis": "Overview、Self-care；无www短链接已验证跳转同一官方原文",
        "rights": "原创短事实摘要，非WHO翻译或认可；原文权利归WHO",
    },
    "who-depression": {
        "title": "Depressive disorder (depression)",
        "url": "https://www.who.int/news-room/fact-sheets/detail/depression",
        "publisher": "WHO", "published": "官网动态更新",
        "basis": "Symptoms and patterns、Self-care；不引入诊断或药物方案",
        "rights": "原创短事实摘要，非WHO翻译或认可；原文权利归WHO",
    },
    "who-activity": {
        "title": "Physical activity",
        "url": "https://who.int/news-room/fact-sheets/detail/physical-activity",
        "publisher": "WHO", "published": "2024-06-26",
        "basis": "Overview、Benefits、How much；无www短链接已验证跳转同一官方原文",
        "rights": "原创短事实摘要，非WHO翻译或认可；原文权利归WHO",
    },
}

# All scenarios are authored examples. Sources support the stated principles,
# never evidence that the fictional person exists or that an intervention worked.
DOCUMENTS = [
    ("policy", "public-academic-records", "课程挂科、补考与重修的核查边界", "student-rules", ["学业", "挂科", "考勤"],
     "原创政策摘要：《普通高等学校学生管理规定》第13、18、19条要求课程考核和成绩如实记录，补考、重修安排由学校规定，不能从一次挂科或缺勤直接推定处分。学业支持先核对课程、学期、请假及成绩记录，再向教务部门核实校内补考重修安排；系统不替学校承诺资格、时间或费用。"),
    ("policy", "public-student-process", "学生陈述申辩及申诉程序", "student-rules", ["学业", "申诉", "权限"],
     "原创政策摘要：《普通高等学校学生管理规定》第55、59—60条规定，作出不利决定前需告知事实、理由和依据，听取学生陈述申辩；学校设学生申诉处理委员会。风险预警仅用于人工核查和支持，不自动生成处分决定；处理或处分争议应引导查阅决定书及学校正式申诉程序。"),
    ("policy", "public-campus-confidentiality", "高校心理咨询保密与专业转介", "campus-mental", ["心理", "保密", "转介"],
     "原创政策摘要：教育部高校心理健康指导纲要要求完善咨询预约、转介和重点反馈制度，保护心理健康数据与学生隐私，建立学校咨询机构至校医院、精神卫生专业机构的转介渠道。模型筛查不能确诊；敏感记录只向承担相应职责的获准人员开放，跟进由有资质人员进行。"),
    ("policy", "public-financial-aid", "经济困难的校园资助咨询渠道", "student-aid", ["经济", "资助", "学业"],
     "原创事实摘要：中国政府网2025年资助手册介绍国家助学金、助学贷款、勤工助学等多种渠道，申请与资格需咨询学校学生资助部门。经济困难导致学习负担时，支持方案应协助学生获得正式政策说明和申请指导，尊重隐私；不虚构校内补助档次，不保证审批、到账时间或金额。金额政策仍需以当前学校公告复核。"),
    ("psychology", "public-stress-support", "日常学业压力与短时练习", "who-stress", ["学业", "压力", "自助"],
     "原创知识摘要：WHO《Doing What Matters in Times of Stress》提供可每日花少量时间练习的压力应对技能，并提供中文材料。适合在本人愿意时作为日常支持资源；学业压力谈话应了解实际困难和可用支持，避免许诺固定周期改善。自助材料不等于心理诊断，持续难以应对时需要寻求专业支持。非WHO翻译或认可；非商业演示摘要，CC BY-NC-SA 3.0 IGO。"),
    ("psychology", "public-anxiety-support", "焦虑、睡眠及学习受影响时的求助", "who-anxiety", ["焦虑", "睡眠", "转介"],
     "原创知识摘要：WHO指出，持续过强的担忧可能干扰日常学习与生活，规律进食、睡眠、身体活动及放松练习可作为支持措施。有明显困扰时应寻求专业评估。校园谈话宜询问影响持续多久及学生希望获得什么帮助；一次考试紧张、问卷标签或缺勤本身不足以诊断焦虑障碍，教师不制定药物治疗。"),
    ("psychology", "public-low-mood-support", "低落、退缩与功能变化的支持边界", "who-depression", ["低落", "退缩", "求助"],
     "原创知识摘要：WHO说明持续低落或兴趣下降可能伴随睡眠、食欲、精力及注意力变化，且存在有效专业治疗。支持时需关注本人感受与日常功能，鼓励联系可信任的人和专业服务；不能由成绩下降或一次自编筛查推断抑郁诊断。AI仅提供人工核查提示，不以运动或学习计划替代必要的专业评估。"),
    ("psychology", "public-activity-support", "身体活动作为健康支持而非惩罚", "who-activity", ["运动", "睡眠", "压力"],
     "原创知识摘要：WHO指出规律身体活动有身心健康益处；步行等不同形式均可计入活动，少量活动也有价值。校园支持可由本人选择可行且安全的活动，从适合自身状况的安排开始，关注可参与性与体验。不把运动作为缺勤处罚，不保证症状消失，身体限制或明显不适应由适当专业人员评估。"),
    ("case", "demo-academic-support", "演示场景：成绩下降与缺课核查", "student-rules", ["学业", "挂科", "考勤"],
     "演示场景（原创假设，无真实个案、无已验证成功结局）：一名本科生本学期两门课未及格，近期缺课增加。辅导员先核对成绩与请假事实，听取时间安排和学习困难，再与学生及任课教师商定课程支持；向教务咨询重修规则并约定跟进。处置流程参考教育部学生管理规定；情景及安排不代表全国统一制度，不推断疾病或自动处分。"),
    ("case", "demo-financial-support", "演示场景：经济负担影响学习", "student-aid", ["经济", "资助", "学业"],
     "演示场景（原创假设，无真实个案、无已验证成功结局）：一名学生表示兼职时间与课程冲突且生活费紧张。支持人员先确认本人意愿，协助联系学生资助部门查阅助学金、贷款及勤工助学规则，同时讨论可行学习安排。政策渠道参考官方资助手册；不公开家庭情况，不保证获得资助，不把困难自动归因为心理疾病。"),
    ("case", "demo-campus-adjustment", "演示场景：新生适应与宿舍沟通", "campus-mental", ["人际", "适应", "保密"],
     "演示场景（原创假设，无真实个案、无已验证成功结局）：一名新生因宿舍作息差异感到孤立。辅导员先听取本人希望解决的问题，征得意愿后协助沟通，并介绍校内预约咨询及自助互助活动；仅记录必要跟进事项。支持渠道与保密原则参考教育部高校心理健康指导纲要，场景由项目作者构造，不声称原政策包含该案例。"),
    ("case", "demo-low-mood-followup", "演示场景：持续低落与学习退缩", "who-depression", ["低落", "考勤", "转介"],
     "演示场景（原创假设，无真实个案、无已验证成功结局）：一名学生反馈持续低落、睡眠变差，并逐渐停止参加课程。支持人员以非评判方式了解日常影响，协助预约校内专业咨询或适当医疗服务，再商定必要的课程协调与跟进。求助原则参考WHO抑郁知识页；该情景不是临床诊断，不包含药物建议或保证改善效果。"),
]


def manifest():
    documents = []
    for collection, doc_id, title, source_key, topics, text in DOCUMENTS:
        source = SOURCES[source_key]
        documents.append({"collection": collection, "doc_id": doc_id, "text": text,
                          "metadata": {"title": title, "source_url": source["url"],
                                       "publisher": source["publisher"], "source_title": source["title"],
                                       "reviewed_at": REVIEWED_AT, "rights": source["rights"],
                                       "topics": topics, "synthetic_scenario": collection == "case"},
                          "chunk_strategy": {"name": "none"}})
    return {"schema_version": 1, "reviewed_at": REVIEWED_AT,
            "scope": "非商业校园演示；短原创摘要及明确虚构情景；无真实个案／成功疗效数据",
            "sources": SOURCES, "documents": documents}


def validate(payload):
    ids = set()
    counts = {key: 0 for key in ("case", "policy", "psychology", "success")}
    for row in payload["documents"]:
        identity = (row["collection"], row["doc_id"])
        if identity in ids or not re.fullmatch(r"[A-Za-z0-9_-]{1,48}", row["doc_id"]):
            raise ValueError("Invalid or duplicate document identity")
        ids.add(identity)
        counts[row["collection"]] += 1
        metadata = row["metadata"]
        source = metadata["source_url"]
        url = urlsplit(source)
        if (url.scheme != "https" or not url.hostname or url.username or url.password
                or len(source.encode()) > 64):
            raise ValueError("Source is not a complete short HTTPS publisher URL")
        if len(metadata["title"].encode()) > 255 or not 40 <= len(row["text"].encode()) <= 4096:
            raise ValueError("Title/content violates the existing Milvus byte limits")
        if row["collection"] == "case" and "无真实个案、无已验证成功结局" not in row["text"]:
            raise ValueError("Scenario must not masquerade as a real or successful case")
    if any(counts[key] < 4 for key in ("case", "policy", "psychology")):
        raise ValueError("Required coverage is absent")
    return counts


def digest(payload):
    return hashlib.sha256(json.dumps(payload, ensure_ascii=False, sort_keys=True,
                                    separators=(",", ":")).encode()).hexdigest()


def _admin_token(path):
    if path:
        file = Path(path)
        if not file.is_file() or file.is_symlink() or stat.S_IMODE(file.stat().st_mode) & 0o077:
            raise ValueError("Admin token file must be a private regular file (0600)")
        token = file.read_text().strip()
    else:
        token = os.environ.get("EDUCARE_ADMIN_TOKEN", "").strip()
    if not token or "\n" in token or "\r" in token:
        raise ValueError("Missing private admin token")
    return token


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, file_pointer, code, message, headers, new_url):
        return None


def apply(payload, base_url, token, opener=None):
    validate(payload)
    url = urlsplit(base_url)
    if (url.scheme != "http" or url.hostname not in ("localhost", "127.0.0.1", "::1")
            or url.username or url.password or url.query or url.fragment or url.path not in ("", "/")):
        raise ValueError("Apply must use the loopback admin API (run in the application pod)")
    opener = opener or urllib.request.build_opener(_NoRedirect()).open
    results = []
    for row in payload["documents"]:
        request = urllib.request.Request(base_url.rstrip("/") + "/api/v1/rag/upsert",
                                         data=json.dumps(row, ensure_ascii=False).encode(), method="POST",
                                         headers={"Content-Type": "application/json", "X-Admin-Token": token})
        try:
            with opener(request, timeout=90) as response:
                result = json.load(response)
        except urllib.error.HTTPError as error:
            raise RuntimeError(f"Upsert rejected for {row['doc_id']} (HTTP {error.code}); response body omitted") from None
        except urllib.error.URLError:
            raise RuntimeError(f"Upsert transport failed for {row['doc_id']}; retry the idempotent command after checking service") from None
        if result.get("doc_id") != row["doc_id"] or not (result.get("skipped") is True or result.get("chunks_written", 0) > 0):
            raise RuntimeError(f"Unexpected upsert result for {row['doc_id']}")
        results.append({"doc_id": row["doc_id"], "collection": row["collection"],
                        "chunks_written": result.get("chunks_written", 0), "skipped": result.get("skipped", False)})
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("check", "export", "apply"))
    parser.add_argument("--base-url", default="http://127.0.0.1:8090")
    parser.add_argument("--admin-token-file")
    parser.add_argument("--expected-sha256", help="Required for apply; use the reviewed check digest")
    args = parser.parse_args()
    payload = manifest()
    counts = validate(payload)
    sha = digest(payload)
    if args.command == "export":
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    elif args.command == "check":
        print(json.dumps({"sha256": sha, "counts": counts, "sources": len(SOURCES),
                          "reviewed_at": REVIEWED_AT, "network_calls": 0}, ensure_ascii=False))
    else:
        if args.expected_sha256 != sha:
            parser.error("apply requires the exact reviewed --expected-sha256")
        results = apply(payload, args.base_url, _admin_token(args.admin_token_file))
        print(json.dumps({"sha256": sha, "results": results, "llm_calls": 0}, ensure_ascii=False))


if __name__ == "__main__":
    main()
