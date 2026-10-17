# SQA2 · 网络攻防对抗技术研究所管理系统（开发仓库）

> 北京理工大学 2026 秋《软件质量保障》SQA2 组 · 乙方=北京盾阵科技有限公司。
> **本仓库只放代码与开发文档**；课程材料（需求框架 PDF、基础数据 xlsx、往届样例）在各人本地的项目课程文件夹 `01_老师下发/` 中，**禁止提交到本仓库**。

## 技术栈（2026-10-16 定稿）

- 后端 `backend/`：Java 17 ＋ Spring Boot 3.x（Maven）＋ MyBatis-Plus ＋ MySQL 8（分包 auth/portal/library/admin/validation/backup）
- 前端 `frontend/`：Vue 3 ＋ Vite ＋ Vue Router ＋ Pinia ＋ Element Plus ＋ Axios
- 接口契约：`docs/接口规范_SQA2_20261016.md`（现为v1.0实现基线，待消费方确认冻结，以文档为仲裁）

## 目录

```
sqa2-nacams/
├── docs/                        # 开发期权威文档副本（直接在此修改并 commit）
│   ├── AGENTS.md                #   课程/项目总说明（AI 助手先读它，业务口径以课程文件夹最新版为准）
│   ├── 开发任务分工_SQA2_20261016.md  #   5 人任务清单（认领自己的部分）
│   ├── 接口规范_SQA2_20261016.md      #   前后端接口契约（10-17 12:00 冻结 v1.0）
│   ├── 组员协作规约_SQA2_20261016.md  #   同步/提交/AI登记/红线全规约（组员与其 AI 必读）
│   └── AI使用登记.md             #   凡用 AI 产出，必须在此追加一行后随代码提交
├── backend/                     # Spring Boot后端、schema、导入脚本和集成测试
└── frontend/                    # Vue 3（由前端组员建立）
```

## 协作规约（5 人 4 天，从简从严）

> 完整版见 `docs/组员协作规约_SQA2_20261016.md`（含双层同步机制：GitHub=代码权威，课程文件夹=业务口径权威；组员收到组长同步包后解压覆盖本地课程文件夹）。以下为要点：

1. **单 main 分支＋小步提交**：每完成一个小功能就 commit＋push，不要攒一天；提交信息格式 `模块: 做了什么`（例：`library: 借阅上限校验`、`portal: 新闻列表页`）。
2. **每日 21:00 前 push**（等于进度打卡）；push 前先 `git pull --rebase`。
3. 冲突解决不了不要硬推，群里 @组长 仲裁；接口不符以 `docs/接口规范` 为准改代码，不口头改契约。
4. **禁止提交**：老师下发材料、他组文件、`node_modules/`、`target/`、数据库备份、`.env`/本地配置（模板文件除外）。
5. **AI 使用登记**：用了任何 AI 工具（Copilot/Cursor/Claude/ChatGPT 等），当天在 `docs/AI使用登记.md` 追加一行再提交——这是课程硬要求，交付时要出声明。
6. 敏感信息（数据库密码等）放 `application-local.yml`（已 gitignore），提交 `application-local.yml.example` 模板。

## 快速开始

```bash
# 需要JDK17+、Maven3.9和MySQL8。先创建数据库并配置本地连接与首次管理员密码。
mvn -f backend/pom.xml test
mvn -f backend/pom.xml spring-boot:run
# 前端由三位前端组员按接口文档开发。
```

完整配置、数据导入、API地址、备份恢复和陈彬模块接入说明见[后端运行说明](backend/README.md)。
默认API地址为`http://localhost:8080/api/v1`；Swagger为`http://localhost:8080/swagger-ui.html`。
首次启动须提供`BOOTSTRAP_PASSWORD`，或复制本地配置模板填写，系统没有默认弱口令。
`backend/target/`、`runtime/`和`.local/`均为忽略的本地产物，禁止提交原始Excel、数据库文件与密码。

韦政治部分的验证结果见[后端验收记录](docs/后端验收记录_SQA2_20261017.md)；后端设计和运行章节DOCX初稿位于`docs/交付文档/`，供陈彬统稿。完整图书业务、校验引擎及前端仍由对应成员接入。

## 访问 GitHub 慢的成员

clone/pull 慢时自配代理（Clash 默认混合端口）：
```bash
git config --global http.https://github.com.proxy http://127.0.0.1:7897
```
或改用手机热点；实在不行组长每日打 zip 发群（最后手段，放弃版本同步）。
