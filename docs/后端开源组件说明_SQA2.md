# 后端开源组件说明（韦政治移交稿）

日期：2026-10-17。用于陈彬汇总F-04《AI与开源使用说明》，本表依据本次实际pom及Maven下载的对应POM许可证字段整理。版本以`backend/pom.xml`和Spring Boot依赖管理解析结果为准，后续升级须同步登记。

| 组件 | 实际版本 | 用途 | POM许可证声明 |
|---|---|---|---|
| Spring Boot（web/validation/jdbc/test starter） | 3.5.7 | Web服务、配置、数据库访问和测试 | Apache License 2.0 |
| Spring Framework | 6.2.12（Boot管理） | MVC、事务、JdbcTemplate | Apache License 2.0 |
| Spring Security Crypto | 6.5.6（Boot管理） | BCrypt密码哈希 | Apache License 2.0 |
| MyBatis-Plus Boot3 starter | 3.5.12 | 账户映射 | Apache License 2.0 |
| MySQL Connector/J | 9.4.0（Boot管理） | MySQL JDBC驱动 | GNU GPL v2 with Universal FOSS Exception v1.0 |
| springdoc-openapi WebMVC UI | 2.8.14 | OpenAPI和Swagger UI | Apache License 2.0 |
| Apache POI OOXML | 5.4.1 | XLSX数据读取 | Apache License 2.0 |
| Apache Commons CSV | 1.14.1 | CSV读取 | Apache License 2.0 |
| H2 | 2.3.232（仅测试） | 隔离测试数据库 | MPL2.0 / EPL1.0双许可证 |

源码与分发包应保留各组件原有许可证、NOTICE及版权信息；MySQL驱动的例外条款原文随对应发行包保留。本表是直接使用组件及关键框架清单，**不是全部传递依赖许可证扫描结论**。集成交付前由陈彬结合最终后端/前端依赖执行完整清单核对；可用`mvn -f backend/pom.xml dependency:tree`复核后端版本，不能把H2测试依赖称为生产数据库。

Python导入与冒烟脚本只依赖Python标准库。DOCX章节由Codex借助python-docx生成；它是文档制作工具，不是后端运行依赖。老师提供的Excel、需求与合同仅作为本地只读参考，没有复制到公开源码仓库。AI辅助用途已登记`docs/AI使用登记.md`，自主审阅和正式批准由成员据实完成。

官方技术依据：[Spring Boot 3.5运行要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[MyBatis-Plus安装说明](https://baomidou.com/getting-started/install/)、[springdoc v2说明](https://springdoc.org/v2/)。许可证字段来源为本次Maven实际解析的同版本POM，不以网页上的最新版本替换实际版本。
