"""B-01 数据正确性检查：抽样比对原始导入行与系统记录，输出抽查清单、不一致数与正确率。

用法（需先启动后端并完成基础数据导入）：
    set ADMIN_PASSWORD=...
    python verify_correctness.py --base-url http://127.0.0.1:8080 --sample 200 --output runtime/b01-correctness.json

抽样量必须不少于 200 条，否则脚本拒绝出具结论并返回退出码 2。
借阅清单接口仅图书管理教师可读；管理员账号不具该权限时，另设环境变量
LIBRARIAN_PASSWORD（默认用户名 librarian）提供图书管理教师会话。
仅依赖 Python 标准库。
"""
import argparse
import datetime
import json
import os
import sys
import urllib.error
import urllib.request

KINDS = ("students", "teachers", "books", "borrows")
REQUIRED_SAMPLE = 200


class Api:
    def __init__(self, base_url, token=None):
        self.base_url = base_url.rstrip("/")
        self.token = token

    def call(self, method, path, payload=None):
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        data = json.dumps(payload).encode() if payload is not None else None
        req = urllib.request.Request(self.base_url + "/api/v1" + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=120) as response:
                return json.load(response)["data"]
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", "replace")
            raise RuntimeError("%s %s -> %s %s" % (method, path, error.code, body))

    def pages(self, path, page_size=200, limit=None):
        items, page = [], 1
        while True:
            separator = "&" if "?" in path else "?"
            data = self.call("GET", "%s%spage=%d&page_size=%d" % (path, separator, page, page_size))
            chunk = data.get("items", [])
            items.extend(chunk)
            if limit and len(items) >= limit:
                return items[:limit]
            if not chunk or len(items) >= data.get("total", 0):
                return items
            page += 1


def text(row, *keys):
    for key in keys:
        value = row.get(key)
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def source_rows(api, kind, limit):
    return api.pages("/admin/import/rows?kind=%s&status=imported" % kind, limit=limit)


def raw_of(row):
    raw = row.get("raw_json")
    if isinstance(raw, dict):
        return raw
    try:
        return json.loads(raw or "{}")
    except json.JSONDecodeError:
        return {}


def compare_user(record, users, kind):
    raw = raw_of(record)
    number = text(raw, "学号" if kind == "students" else "工号")
    name = text(raw, "姓名")
    field = "studentNo" if kind == "students" else "employeeNo"
    match = next((user for user in users if user.get(field) == number), None)
    if match is None:
        return "系统无对应%s（%s）" % ("学号" if kind == "students" else "工号", number)
    if match.get("name") != name:
        return "姓名不一致：原始[%s] 系统[%s]" % (name, match.get("name"))
    return None


def compare_book(record, books, purchase_year):
    raw = raw_of(record)
    legacy = text(raw, "图书编号", "编号")
    title = text(raw, "图书名称", "书名")
    match = next((book for book in books if book.get("legacyCode") == legacy), None)
    if match is None:
        return "系统无对应旧编号图书（%s）" % legacy
    if match.get("title") != title:
        return "书名不一致：原始[%s] 系统[%s]" % (title, match.get("title"))
    expected = "%04d%03d" % (purchase_year, int(legacy[1:])) if legacy.startswith("B") else legacy
    if match.get("code") != expected:
        return "编号换算不一致：期望[%s] 系统[%s]" % (expected, match.get("code"))
    return None


def compare_borrow(record, borrows, books):
    raw = raw_of(record)
    legacy = text(raw, "编号")
    borrower = text(raw, "借阅人")
    book = next((item for item in books if item.get("legacyCode") == legacy), None)
    if book is None:
        return "借阅行引用的图书不在系统中（%s）" % legacy
    match = next((item for item in borrows if item.get("bookId") == book.get("id") and item.get("userName") == borrower), None)
    if match is None:
        return "系统无对应借阅记录：图书[%s] 借阅人[%s]" % (legacy, borrower)
    if not match.get("returnedAt"):
        return "原始行为历史已归还记录，系统显示未归还"
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--sample", type=int, default=REQUIRED_SAMPLE)
    parser.add_argument("--purchase-year", type=int, default=2026)
    parser.add_argument("--librarian-username", default="librarian")
    parser.add_argument("--output", default="runtime/b01-correctness.json")
    args = parser.parse_args()
    if args.sample < REQUIRED_SAMPLE:
        print("抽样量 %d 少于验收要求的 %d 条，拒绝出具正确性结论。" % (args.sample, REQUIRED_SAMPLE))
        return 2
    password = os.environ.get("ADMIN_PASSWORD")
    if not password:
        print("请通过环境变量 ADMIN_PASSWORD 提供管理员口令。")
        return 2

    api = Api(args.base_url)
    token = api.call("POST", "/auth/login", {"username": args.username, "password": password})["token"]
    api.token = token

    # 借阅清单接口仅图书管理教师可读；未单独提供其口令时沿用管理员会话。
    librarian_password = os.environ.get("LIBRARIAN_PASSWORD")
    borrow_api = api
    if librarian_password:
        borrow_api = Api(args.base_url)
        borrow_api.token = borrow_api.call("POST", "/auth/login",
                                           {"username": args.librarian_username,
                                            "password": librarian_password})["token"]

    quota = max(1, args.sample // len(KINDS))
    records = {kind: source_rows(api, kind, quota) for kind in KINDS}
    users = api.pages("/admin/users")
    books = api.pages("/books")
    borrows = borrow_api.pages("/admin/borrows")

    mismatches, checked, by_kind = [], 0, {}
    for kind in KINDS:
        rows = records[kind]
        failed = 0
        for record in rows:
            if kind in ("students", "teachers"):
                problem = compare_user(record, users, kind)
            elif kind == "books":
                problem = compare_book(record, books, args.purchase_year)
            else:
                problem = compare_borrow(record, borrows, books)
            checked += 1
            if problem:
                failed += 1
                mismatches.append({"kind": kind, "sheet": record.get("sheet_name"),
                                   "row": record.get("source_row_no"), "sourceKey": record.get("source_key"),
                                   "reason": problem})
        by_kind[kind] = {"sampled": len(rows), "mismatches": failed}

    sample_size = sum(len(value) for value in records.values())
    if sample_size < REQUIRED_SAMPLE:
        print("可用已导入原始行仅 %d 条，少于验收要求的 %d 条，拒绝出具正确性结论。" % (sample_size, REQUIRED_SAMPLE))
        return 2

    borrow_total = len(borrows)
    source_borrow_total = len(api.pages("/admin/import/rows?kind=borrows&status=imported"))
    recomputation = {
        "description": "统计可复算性：系统借阅记录数应等于原始借阅行导入数（演示数据按行一对一入库）",
        "systemBorrows": borrow_total,
        "sourceBorrowRows": source_borrow_total,
        "matches": borrow_total == source_borrow_total,
    }
    accuracy = round((checked - len(mismatches)) / checked * 100, 2) if checked else 0.0
    report = {
        "generatedAt": datetime.datetime.now().isoformat(timespec="seconds"),
        "baseUrl": args.base_url,
        "sampledRows": sample_size,
        "checkedFields": checked,
        "mismatchCount": len(mismatches),
        "accuracyPercent": accuracy,
        "byKind": by_kind,
        "mismatches": mismatches,
        "recomputation": recomputation,
    }
    output = os.path.abspath(args.output)
    os.makedirs(os.path.dirname(output), exist_ok=True)
    with open(output, "w", encoding="utf-8") as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2)
    print("抽样 %d 条，比对 %d 项，不一致 %d 项，正确率 %.2f%%" %
          (sample_size, checked, len(mismatches), accuracy))
    print("统计复算：系统借阅 %d 条 / 原始借阅行 %d 条 -> %s" %
          (borrow_total, source_borrow_total, "一致" if recomputation["matches"] else "不一致"))
    print("报告已写入 " + output)
    return 0 if not mismatches else 1


if __name__ == "__main__":
    sys.exit(main())