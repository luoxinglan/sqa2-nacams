"""对已启动的独立MySQL验收实例检查真实HTTP链路；会创建测试账号/内容并执行恢复，请仅用于专用测试库。"""
import argparse
import io
import json
import os
from pathlib import Path
import urllib.request
import urllib.error
import uuid
from import_baseline import multipart, request


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--base-url", default="http://127.0.0.1:18080")
    p.add_argument("--confirm-test-database", action="store_true", required=True)
    p.add_argument("--output", type=Path, default=Path("runtime/mysql-smoke.json"))
    args = p.parse_args()
    password = os.environ["ADMIN_PASSWORD"]
    checks = []

    def call(method, path, payload=None, token=None, expected=200):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        body = json.dumps(payload).encode() if payload is not None else None
        req = urllib.request.Request(args.base_url + "/api/v1" + path, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=120) as result:
                status, data = result.status, json.load(result)
        except urllib.error.HTTPError as error:
            status, data = error.code, json.loads(error.read())
        assert status == expected, (path, status, data.get("message"))
        assert data["code"] == (0 if expected == 200 else expected)
        return data["data"]

    admin = call("POST", "/auth/login", {"username": "admin", "password": password})["token"]
    checks.append("管理员MyBatis账户查询与令牌认证成功")
    call("GET", "/admin/users", expected=401)
    checks.append("匿名访问管理接口401")
    suffix = uuid.uuid4().hex[:10]
    student = call("POST", "/admin/users", {
        "username": "smoke-" + suffix, "password": password, "identityType": "student", "name": "演示学生",
        "studentNo": "312026" + str(int(suffix, 16) % 10000).zfill(4), "status": "在读", "mobile": "13800000000",
        "birthDate": "2001-01-02", "email": "smoke@example.invalid",
    }, admin)
    public = call("GET", "/students/" + str(student["id"]))
    assert "mobile" not in public and "birthDate" not in public and public["email"] == "smoke@example.invalid"
    checks.append("MySQL用户创建与公开详情敏感字段缺省")
    token = call("POST", "/auth/login", {"username": student["username"], "password": password})["token"]
    call("GET", "/admin/users", token=token, expected=403)
    call("PUT", "/me/profile", {"isDirector": True}, token, expected=403)
    checks.append("普通用户管理越权和自改权限均403")
    content = call("POST", "/admin/content/news", {"title": "MySQL验收-" + suffix, "category": "通知通告", "body": "备份恢复探针"}, admin)
    assert call("GET", "/news?keyword=" + suffix)["total"] == 1
    checks.append("门户内容写入与游客keyword查询成功")
    snapshot = call("POST", "/admin/backup", token=admin)
    req = urllib.request.Request(args.base_url + "/api/v1/admin/backups/" + snapshot["fileName"] + "/file", headers={"Authorization": "Bearer " + admin})
    with urllib.request.urlopen(req, timeout=120) as result:
        saved = result.read()
    raw = json.loads(saved)
    assert len(raw["tables"]) == 19 and raw["format"] == "SQA2-NACAMS"
    call("DELETE", "/admin/content/news/" + str(content["id"]), token=admin)
    assert call("GET", "/news?keyword=" + suffix)["total"] == 0
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.parent / ("restore-" + suffix + ".json")
    temporary.write_bytes(saved)
    try:
        data, mime = multipart(temporary, {"confirm": "true"})
        assert request(args.base_url, "/admin/restore", data, mime, admin)["restored"]
    finally:
        temporary.unlink(missing_ok=True)
    call("GET", "/auth/me", token=admin, expected=401)
    admin = call("POST", "/auth/login", {"username": "admin", "password": password})["token"]
    assert call("GET", "/news?keyword=" + suffix)["total"] == 1
    checks.append("19张业务表真实MySQL备份恢复成功，恢复后旧令牌失效且内容复原")
    call("DELETE", "/admin/content/news/" + str(content["id"]), token=admin)
    call("DELETE", "/admin/users/" + str(student["id"]), token=admin)
    call("POST", "/auth/logout", token=admin)
    with urllib.request.urlopen(args.base_url + "/v3/api-docs", timeout=120) as result:
        spec = json.load(result)
    assert "/api/v1/admin/restore" in spec["paths"]
    checks.append("OpenAPI可读取且包含备份恢复路由")
    args.output.write_text(json.dumps({"passed": len(checks), "failed": 0, "checks": checks,
                                      "openapiPathCount": len(spec["paths"])}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"passed": len(checks), "failed": 0, "openapiPathCount": len(spec["paths"])}, ensure_ascii=False))


if __name__ == "__main__":
    main()
