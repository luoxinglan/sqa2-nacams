"""从课程文件夹读取9份Excel，通过管理员API导入；不复制原件到仓库。仅依赖Python标准库。"""
import argparse
import getpass
import json
import mimetypes
import os
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

SOURCES = [
    ("teachers", "教师信息2026.xlsx"), ("students", "学生信息2026.xlsx"),
    ("books", "图书信息2026.xlsx"), ("borrows", "借阅信息2026.xlsx"),
    ("theses", "论文信息2026.xlsx"), ("patents", "专利信息2026.xlsx"),
    ("projects", "项目信息2026.xlsx"), ("courses", "授课信息2026.xlsx"),
    ("textbooks", "教材信息2026.xlsx"),
]


def request(base, path, data, content_type, token=None):
    headers = {"Content-Type": content_type}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(base.rstrip("/") + "/api/v1" + path, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=600) as response:
            return json.load(response)["data"]
    except urllib.error.HTTPError as error:
        body = json.loads(error.read().decode("utf-8"))
        raise RuntimeError(f"HTTP {error.code}: {body.get('message')}") from None


def multipart(path, fields):
    boundary = "sqa2-" + uuid.uuid4().hex
    parts = []
    for key, value in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
    # ASCII传输文件名，原件路径与完整行来源由调用方报告记录。
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="source{path.suffix}"\r\nContent-Type: {mimetypes.guess_type(path.name)[0] or "application/octet-stream"}\r\n\r\n'.encode())
    parts.extend([path.read_bytes(), f"\r\n--{boundary}--\r\n".encode()])
    return b"".join(parts), "multipart/form-data; boundary=" + boundary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--purchase-year", type=int, required=True, help="B001转换的购买年份；是演示假设，不是原始事实")
    parser.add_argument("--demo-date", default="2026-10-17", help="缺失借阅日期的演示基准日")
    parser.add_argument("--output", type=Path, default=Path("runtime/import-report.json"))
    args = parser.parse_args()
    missing = [name for _, name in SOURCES if not (args.source_dir / name).is_file()]
    if missing:
        parser.error("缺少原始文件：" + ", ".join(missing))
    admin_password = os.environ.get("ADMIN_PASSWORD") or getpass.getpass("管理员密码: ")
    initial_password = os.environ.get("IMPORT_INITIAL_PASSWORD") or getpass.getpass("导入用户初始密码（至少8位，交付后重置）: ")
    login = json.dumps({"username": os.environ.get("ADMIN_USERNAME", "admin"), "password": admin_password}).encode()
    token = request(args.base_url, "/auth/login", login, "application/json")["token"]
    reports = {}
    try:
        for kind, name in SOURCES:
            body, content_type = multipart(args.source_dir / name, {
                "kind": kind, "purchaseYear": args.purchase_year,
                "demoDate": args.demo_date, "initialPassword": initial_password,
            })
            report = request(args.base_url, "/admin/import/baseline", body, content_type, token)
            reports[kind] = report
            print(f"{kind}: 总数={report['total']} 新增={report['success']} 跳过={report['skipped']} 失败={report['failed']}", flush=True)
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(reports, ensure_ascii=False, indent=2), encoding="utf-8")
    finally:
        request(args.base_url, "/auth/logout", b"{}", "application/json", token)
    return 1 if any(v["failed"] for v in reports.values()) else 0


if __name__ == "__main__":
    sys.exit(main())
