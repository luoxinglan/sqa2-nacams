# 后端运行与协作说明

韦政治负责框架、数据库、认证与用户管理、门户与内容管理、批量导入、签到统计、数据备份恢复。Java 17编译目标；Spring Boot 3.5.7、MyBatis-Plus 3.5.12、MySQL 8。主类为`cn.sqa2.nacams.NacamsApplication`。

## 首次运行

1. 安装JDK 17或21、Maven 3.9、MySQL 8。执行`java -version`和`mvn -version`，两者均须使用JDK 17以上。Windows默认Java 8时，在当前终端设置`$env:JAVA_HOME='你的JDK17目录'`和`$env:Path="$env:JAVA_HOME/bin;$env:Path"`。
2. 用MySQL管理工具创建专用数据库`CREATE DATABASE sqa2_nacams CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`，为专用数据库用户授予该库的SELECT/INSERT/UPDATE/DELETE/CREATE/**REFERENCES/INDEX/ALTER**权限（`schema.sql`建表带外键，仅有CREATE会报`REFERENCES command denied`——2026-10-18联调实测）；勿连接现有其他项目库。示例：`CREATE USER 'sqa2'@'localhost' IDENTIFIED BY '<密码>'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,REFERENCES,INDEX,ALTER ON sqa2_nacams.* TO 'sqa2'@'localhost';`
3. 在`backend/src/main/resources/`中把`application-local.yml.example`复制为`application-local.yml`，填写连接与首次管理员密码。也可使用环境变量`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`BOOTSTRAP_PASSWORD`。首次启动未提供有效初始管理员密码会拒绝启动。默认用户名admin，无默认密码。
4. 在仓库根执行`mvn -f backend/pom.xml spring-boot:run`。首次启动自动执行`schema.sql`创建缺失表，重启不删除数据。默认端口8080，可用`PORT`覆盖。
5. Swagger为`http://localhost:8080/swagger-ui.html`，机器可读接口为`http://localhost:8080/v3/api-docs`。公开站点信息为`GET http://localhost:8080/api/v1/site/info`。

前端默认允许`http://localhost:5173`和`http://127.0.0.1:5173`跨域；其他来源显式设置`CORS_ORIGINS`，逗号分隔。站点英文名、LOGO、版权分别配置`SITE_ENGLISH_NAME`、`SITE_LOGO`、`SITE_COPYRIGHT`，由组长提供真实值；未确定时保持空值，不编造。

## 构建与验证

```powershell
mvn -f backend/pom.xml test
mvn -f backend/pom.xml package
java -jar backend/target/nacams-1.0.0-SNAPSHOT.jar
```

集成测试使用独立内存H2数据库验证HTTP权限、隐私、导入事务与备份恢复。H2只用于测试，生产始终使用MySQL。MySQL验证证据与限制见`docs/后端验收记录_SQA2_20261017.md`。

`runtime/`、`target/`、本地配置、备份、原始材料均不进Git。生产运行文件位置由`APP_STORAGE`控制，默认当前目录`runtime/`。终端启动目录改变时须设置绝对`APP_STORAGE`以免切换媒体目录。

## 基础数据导入

启动后在仓库根运行（Python3，仅标准库；密码交互输入或从环境变量读取）：

```powershell
python backend/tools/import_baseline.py --source-dir '课程文件夹/01_老师下发/选择1基础数据' --purchase-year 2026
```

脚本按教师→学生→图书→借阅→论文→专利→项目→授课→教材顺序读取本地原件，通过管理员API导入。原件不会复制进仓库。每行结果保存在被Git忽略的`runtime/import-report.json`，失败行必须人工核对，不会伪报全部成功。重跑相同原始行按内容指纹跳过已成功行；失败行可修正后重试。

本次原件共2806条非空业务行，2140条成功、666条隔离；用户已确认暂无补充编码口径，继续按现行规则隔离。管理员可通过`GET /api/v1/admin/import/rows?status=rejected`分页查看原始行及原因。详情见验收记录，完整明细只保存在本地，不提交公开仓库。

- B001→2026001的年份2026来自上述参数，是缺失购买年份的演示假设。`legacy_code`永久保留B001映射。
- 借阅原始表没有日期；导入仅创建带`datesSynthetic=true`的**已归还历史演示记录**，不生成在借/违规记录。无法唯一匹配人员的原始行记入`source_import_rows`并报告失败，不凭姓名杜撰账号。
- 电话、生日、邮箱、照片等缺失字段保持缺失；演示补充应另行标明虚构来源。导入不自动授予所长或图书管理教师，管理员按实际指定。
- 论文（学位论文）投影到科研论文时保留`paperKind=学位论文`，避免当作期刊论文。原始数据分别存9张表与来源日志，公开门户使用受控投影。
- 常规用户Excel/CSV导入使用英文API字段名作表头，至少`username,password,identityType,name`及身份所需字段；每行单独事务，失败不影响其他行。不会将密码或密码哈希返回给前端。

## 现有网站数据导入

支持管理员将旧站内容导出为UTF-8 CSV或XLSX，再上传`POST /api/v1/admin/content/{type}/import`，multipart字段`file`。列与JSON内容字段一致：`title,category,body,author,publishedAt`，其他列作为扩展字段保存；媒体先经`/admin/media`上传，将返回的URL放入`images/videos/attachments`字段（JSON数组应使用JSON单条录入方式）。旧站URL/凭据和原始导出文件未提供，不进行未经核对的抓取，也不声称已迁移真实旧站。可用`keyword`检索验证导入。

## 陈彬模块接入约定

`library/`图书规则、违规队列、提醒、下载专区、帮助及`/validate`、`/photos/check`控制器由陈彬实现；本提交不占用这些路由。共享数据库结构已提供，新增迁移须按规约协商。

- 注入`Access`：`user()`需登录，`admin()`需系统管理员，`librarian()`只认管理员授予的`isLibrarian`标记，管理员角色不会自动获得图书权限。
- `CurrentUser`包含当前身份。所有接口返回`Api.ok(data)`，业务错误抛`ApiException(code,message,data)`。
- `ProfileValidator`是保存与导入的唯一校验扩展点。当前`CoreProfileValidator`只做必填、工号/学号格式、邮箱日期基础检查，**不是完整姓名条例与电话语义引擎**。陈彬新增`@Primary`实现并委托基础检查或替换它；不得仅前端校验。
- `/me/photo`已强制大小≤2MB、比例33:48和图片可读性检查；背景/着装提示由陈彬检测接口补充。
- 下载文件必须调用`MediaService.savePrivate(file)`；该文件的`/media/{id}`仅登录可访问。门户附件调用`save(file)`为公开媒体。
- 诚信记录不提供DELETE；停借阈值按需求原文和合同为**超过3次（4次起）**，原接口v0.4的≥3为勘误项，见接口文档修订记录。

## 备份与恢复

每日北京时间02:00自动生成JSON逻辑快照，含19张受控业务表、媒体原文件和格式版本；认证会话不备份。立即备份`POST /api/v1/admin/backup`，列表`GET /api/v1/admin/backups`，下载`GET /api/v1/admin/backups/{fileName}/file`，均需管理员。

`POST /api/v1/admin/restore`以multipart上传`file`并显式提交`confirm=true`。恢复前检查格式、表集合、字段集合、管理员、媒体大小，自动另存当前快照；数据库恢复在单事务中执行，出错回滚。恢复成功使全部令牌失效，需使用**被恢复备份中的密码**重新登录。备份含敏感数据和密码哈希，保存在本机被忽略的runtime目录；不自动删除，课程交付期人工归档。

50MB为当前HTTP上传上限；超出该规模须评审调整上限或增加流式离线恢复工具。快照不是整个MySQL实例的物理备份，不含MySQL用户、权限和服务器配置。
