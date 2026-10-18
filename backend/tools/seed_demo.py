"""一键灌入演示数据（不依赖课程原始 xlsx，供其他环境部署后快速演示）。
用法：python backend/tools/seed_demo.py --base-url http://127.0.0.1:8080
需要 BOOTSTRAP 管理员已能登录（用户名 admin，密码=application-local.yml 的 app.bootstrap.password，
经环境变量 ADMIN_PASSWORD 传入）。幂等：已存在同名内容/账号/图书时跳过。仅用 Python 标准库。
演示账号：director/Director@123（所长）、librarian/Librarian@123（图书管理教师）、
teacher/Teacher@123、student/Student@123。
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request

NEWS = [
    ("新闻动态", "研究所承办 2026 年网络空间安全青年学者论坛", "综合办公室", "2026-10-16 16:30",
     "<p>2026 年网络空间安全青年学者论坛在北京理工大学成功举办，围绕人工智能安全、漏洞挖掘、网络靶场等方向开展交流。</p>"),
    ("通知通告", "关于组织 2026 年秋季研究生学位论文开题工作的通知", "教务办公室", "2026-10-15 10:00",
     "<p>请各位研究生按规定时间在本系统提交开题材料，逾期不再受理。</p>"),
    ("学术活动", "学术报告：大模型驱动的漏洞挖掘技术前沿", "复杂网络与安全研究实验室", "2026-10-14 14:00",
     "<p>本期报告介绍大语言模型辅助漏洞挖掘的最新进展及其在攻防对抗场景中的应用前景。</p>"),
    ("新闻动态", "研究所与行业单位签署网络安全联合研究合作协议", "综合办公室", "2026-10-12 09:20",
     "<p>研究所与多家行业单位将在网络靶场建设与安全评测领域开展长期合作。</p>"),
    ("学术活动", "第九届网络攻防对抗技术研究生学术论坛征文通知", "研究生会", "2026-10-10 15:45",
     "<p>论坛面向全体研究生征集论文，欢迎踊跃投稿。</p>"),
]
ABOUT = [
    ("研究所简介",
     "<p>北京理工大学网络攻防对抗技术研究所依托计算机学院建设，长期从事网络攻防对抗、网络靶场与安全评测、漏洞挖掘与利用、复杂网络安全等方向的科学研究与人才培养工作。</p>"
     "<p>研究所在复杂网络与安全研究实验室基础上建设，将师资队伍、学生信息、教学科研、研究成果等信息全面纳入数据库统一管理。</p>"),
    ("联系我们",
     "<p><strong>地址：</strong>北京市海淀区中关村南大街 5 号（邮编 100081）</p><p><strong>邮箱：</strong>nacams@bit.edu.cn</p><p><strong>电话：</strong>010-68918482</p>"),
]
ADMISSIONS = [
    ("本科", "本科生招生", "依托计算机学院大类培养。技术要求：扎实的数学与程序设计基础。联系人：闫怀志，010-68918482。"),
    ("硕士", "硕士研究生招生", "欢迎计算机、网络空间安全相关专业背景同学报考。联系人：闫怀志，nacams@bit.edu.cn。"),
    ("博士", "博士研究生招生", "欢迎有网络安全方向科研经历的同学联系报考。联系人：闫怀志，nacams@bit.edu.cn。"),
    ("留学生", "留学生招生", "International students are welcome. 请提前联系确认中文名登记与语言要求。"),
]
COOPS = [
    ("南洋理工大学", "新加坡", "网络空间安全联合培养项目"),
    ("某网络安全龙头企业", "中国", "联合实验室与实习基地建设项目"),
    ("莫纳什大学", "澳大利亚", "复杂网络安全合作研究项目"),
]
BOOKS = [
    ("2026001", "网络攻防对抗技术", "闫怀志", "研究所文件柜A-01", 2024),
    ("2026002", "网络安全评测与靶场技术", "李老师", "研究所文件柜A-02", 2025),
    ("2026003", "计算机网络安全（第2版）", "王勇", "研究所文件柜B-01", 2023),
    ("2026004", "漏洞挖掘与利用原理", "胡昌振", "研究所文件柜B-02", 2024),
    ("2026005", "密码编码学与网络安全", "薛静锋", "研究所文件柜B-03", 2022),
    ("2026006", "Web 安全深度剖析", "赵小林", "研究所文件柜C-01", 2023),
    ("2026007", "软件质量保证和管理", "马锐", "研究所文件柜C-02", 2024),
    ("2026008", "深度学习安全与实践", "郑军", "研究所文件柜C-03", 2025),
]
DEMO_USERS = [
    ("director", "Director@123", "teacher", "闫所长", {"isDirector": True, "employeeNo": "6120100001", "teacherType": "full"}),
    ("librarian", "Librarian@123", "teacher", "图书管理教师", {"isLibrarian": True, "employeeNo": "6120100002", "teacherType": "full"}),
    ("teacher", "Teacher@123", "teacher", "示例教师", {"employeeNo": "6120100003", "teacherType": "full"}),
    ("student", "Student@123", "student", "示例学生", {"studentNo": "3120260001", "status": "在读", "enrollmentYear": 2026}),
]


def call(base, method, path, token=None, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(base.rstrip("/") + "/api/v1" + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode("utf-8"))


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--base-url", default="http://127.0.0.1:8080")
    args = ap.parse_args()
    admin_password = os.environ.get("ADMIN_PASSWORD")
    if not admin_password:
        sys.exit("请经环境变量 ADMIN_PASSWORD 提供管理员密码")
    login = call(args.base_url, "POST", "/auth/login", payload={"username": "admin", "password": admin_password})
    if login.get("code") != 0:
        sys.exit("管理员登录失败：" + str(login.get("message")))
    token = login["data"]["token"]

    # 1) 演示账号（存在即跳过）
    for username, password, identity, name, extra in DEMO_USERS:
        payload = {"username": username, "password": password, "identityType": identity, "name": name}
        payload.update(extra)
        r = call(args.base_url, "POST", "/admin/users", token, payload)
        print(("OK  " if r.get("code") == 0 else "SKIP ") + "用户 " + username + " " + str(r.get("message", ""))[:30])

    # 2) 图书（librarian 身份）
    lib = call(args.base_url, "POST", "/auth/login", payload={"username": "librarian", "password": "Librarian@123"})
    lib_token = lib["data"]["token"] if lib.get("code") == 0 else token
    for code, title, author, location, year in BOOKS:
        r = call(args.base_url, "POST", "/admin/books", lib_token,
                 {"code": code, "title": title, "author": author, "location": location, "publicationYear": year})
        print(("OK  " if r.get("code") == 0 else "SKIP ") + "图书 " + code + " " + title)

    # 3) 门户内容（admin 身份）
    for category, title, author, at, body in NEWS:
        r = call(args.base_url, "POST", "/admin/content/news", token,
                 {"category": category, "title": title, "author": author, "publishedAt": at, "body": body})
        print(("OK  " if r.get("code") == 0 else "SKIP ") + "动态 " + title[:18])
    for category, body in ABOUT:
        r = call(args.base_url, "POST", "/admin/content/about", token,
                 {"category": category, "title": category, "body": body, "author": "综合办公室",
                  "publishedAt": "2026-10-01 09:00"})
        print(("OK  " if r.get("code") == 0 else "SKIP ") + "关于 " + category)
    for category, title, body in ADMISSIONS:
        r = call(args.base_url, "POST", "/admin/content/admissions", token,
                 {"category": category, "title": title, "body": body, "author": "研究生秘书",
                  "publishedAt": "2026-09-10 08:00"})
        print(("OK  " if r.get("code") == 0 else "SKIP ") + "招生 " + category)
    for name, country, project in COOPS:
        r = call(args.base_url, "POST", "/admin/content/cooperations", token,
                 {"category": "国际合作", "title": name, "body": project, "country": country,
                  "author": "", "publishedAt": "2026-05-01 08:00"})
        print(("OK  " if r.get("code") == 0 else "SKIP ") + "合作 " + name)
    print("演示数据灌入完成。")


if __name__ == "__main__":
    main()
