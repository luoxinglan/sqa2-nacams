# 运行说明 · SQA2 网络攻防对抗技术研究所管理系统

> 2026-10-18 联调通过版本（全栈真实运行：前端 5173 ＋ 后端 8080 ＋ MySQL 8）。
> 本说明面向评审与其他组运行（合同 F-01：可运行工程）。

## 一、环境要求

| 组件 | 版本 | 备注 |
|---|---|---|
| JDK | 17 或 21 | 后端编译运行 |
| Maven | 3.9+ | `mvn -f backend/pom.xml …` |
| MySQL | 8.0+ | 需 utf8mb4_0900_ai_ci（8.0 系排序规则） |
| Node.js | 18+（开发机用 24 实测通过） | 前端 `npm run dev` |
| Python | 3.8+（仅标准库） | 基础数据导入脚本（可选） |

国内镜像建议：JDK 用南大镜像 `mirror.nju.edu.cn/adoptium`，Maven 用 `mirror.nju.edu.cn/apache/maven`，MySQL 用华为云 `mirrors.huaweicloud.com/mysql/Downloads/MySQL-8.0/`，Maven 仓库用 `maven.aliyun.com/repository/public`（`~/.m2/settings.xml` 配 mirror）。

## 二、首次初始化（三步）

1. **建库**（root 执行；或直接跑 `scripts\init-db.cmd`）：
   ```sql
   CREATE DATABASE sqa2_nacams CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE USER 'sqa2'@'localhost' IDENTIFIED BY '<密码>';
   GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,REFERENCES,INDEX,ALTER ON sqa2_nacams.* TO 'sqa2'@'localhost';
   ```
   ⚠️ 权限必须含 **REFERENCES**（schema.sql 建表带外键，只有 CREATE 会报 `REFERENCES command denied`——10-18 联调实测）。
2. **后端本地配置**：复制 `backend/src/main/resources/application-local.yml.example` 为 `application-local.yml`，填数据库密码和 `app.bootstrap.password`（首次管理员 admin 的初始密码，不提供会拒绝启动）。该文件已 gitignore，不入库。
3. **前端依赖**：`cd frontend && npm install`。

## 三、启动

- **一键**（Windows）：`scripts\start-all.cmd` —— 自动起 MySQL（若 3306 未占用）＋后端＋前端。
- **手动**：
  ```bash
  mvn -f backend/pom.xml spring-boot:run     # 后端 8080，首启自动建表
  cd frontend && npm run dev                  # 前端 5173（/api 代理到 8080）
  ```
- 首页 <http://localhost:5173>｜接口文档 <http://localhost:8080/swagger-ui.html>

## 四、基础数据导入（可选，演示建议做）

```bash
python backend/tools/import_baseline.py \
  --source-dir "<课程文件夹>/01_老师下发/选择1基础数据" --purchase-year 2026
```

环境变量：`ADMIN_PASSWORD`（管理员密码）、`IMPORT_INITIAL_PASSWORD`（导入用户初始密码，≥8 位）。
2026-10-18 实测：2806 行 → **2138 入库 / 668 隔离**；隔离行全部为校验规则拦截（640 个学生学号不符合 10 位编码[毕业生老学号]、18 个论文缺标题、5 个姓名非汉字、4 个借阅人无法唯一匹配、1 个教师工号前缀不符），明细在 `runtime/import-report.json`，管理端 `GET /admin/import/rows?status=rejected` 可查。**不导入也能跑**，门户栏目会有演示内容但无师生/图书真实数据。

## 五、演示账号

导入后所有师生以学号/工号相关用户名＋`IMPORT_INITIAL_PASSWORD` 登录；管理员=admin（application-local.yml 里的 bootstrap 密码）。门户演示内容（新闻/简介/招生/国际合作，虚构口径经老师 3.5.A-4 允许）由管理端 `POST /admin/content/{type}` 录入，本组已灌 14 条。

## 六、已知口径备忘

- 图书编号 B001 风格 → 导入时按 `--purchase-year 2026` 换算为 7 位编号（2026001），`legacy_code` 保留映射；
- 借阅原始表无日期：仅生成 `datesSynthetic=true` 的已归还历史演示记录，不产生在借/违规；
- 站点元数据（英文名/LOGO/版权）来自 `application-local.yml` 的 `app.site.*`（部署期配置口径）；
- 前端联调开关：`frontend/.env.development` 的 `VITE_USE_MOCK`（0=真实后端，1=mock 假数据）；生产构建固定走真实后端。
