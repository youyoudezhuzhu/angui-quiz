#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 data/questions.json 同步到网页 js/questions.js 和安卓 assets/questions.json。"""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "data", "questions.json")

data = json.load(open(DATA, encoding="utf-8"))
meta = data["meta"]
questions = data["questions"]

# ---- 网页 ----
web_js = os.path.join(ROOT, "web", "js", "questions.js")
with open(web_js, "w", encoding="utf-8") as f:
    f.write("/* 由 tools/sync-data.py 从 data/questions.json 自动生成，请勿手工修改 */\n")
    f.write("window.QUIZ_META = " + json.dumps(meta, ensure_ascii=False) + ";\n")
    f.write("window.QUIZ_DATA = " + json.dumps(questions, ensure_ascii=False) + ";\n")

# ---- 安卓 asset（保持 App 读取的字段）----
asset = []
for q in questions:
    asset.append({
        "id": q["id"],
        "seq": q["seq"],
        "category": q["category"],
        "stem": q["stem"],
        "options": q["options"],
        "answer": q.get("answer", ""),
        "answerText": q.get("answerText", ""),
        "answerNote": q.get("answerNote", ""),
        "analysis": q.get("analysis", ""),
        "images": q.get("images", []),
    })
android_json = os.path.join(ROOT, "android", "assets", "questions.json")
with open(android_json, "w", encoding="utf-8") as f:
    json.dump(asset, f, ensure_ascii=False, separators=(",", ":"))

print("questions:", len(questions))
print("web:", web_js)
print("android:", android_json)
