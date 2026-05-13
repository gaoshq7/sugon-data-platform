# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 仓库定位

`sugon-data-platform`（SDP）是一套类 Ambari 的大数据集群运维 **Java SDK**，按 Maven artifact（`io.github.gaoshq7` 下）分发。**本仓库内没有可运行的应用**——没有 `main`、没有测试、没有可启动的 Spring Boot 应用，一切"运行"都发生在下游依赖方。

下游有两类角色，必须区分清楚：

| 角色 | 依赖 | 写什么 |
|---|---|---|
| **生产者**（SDP 版本包作者，例如 `galaxy-libraries v5.3.1`） | `sdp-core` | 用 `@Sdp` / `@Host` / `@Mode` / `@Serve` / `@Process` / `@Config` 注解定义服务体系；在 `resources/<sdpVersion>/<ServeClass>/` 下放 CSV 字典 |
| **消费者**（Web/Spring Boot 应用） | `sdp-spring-boot-starter` | 覆盖驱动 Bean（`HostDriver` / `RpcDriver` / `ConfigDriver` / `ResourceDriver`），写 Controller 通过 `SdpEnvManager` → `SdpManager` / `HostManager` 操作集群 |

任务一旦涉及 `@Serve` / `@Process` / `@Config` 或新增版本包，就是生产者侧任务；涉及驱动、`SdpEnvManager`、Controller、"Web 层调 SDP" 就是消费者侧任务。仓库内置的两个 skill（`skills/sdp-core/SKILL.md`、`skills/sdp-starter/SKILL.md`）分别覆盖这两侧的深度规范，做相应任务时**先读对应 skill**。

## 模块结构与依赖方向

```
sdp-common              ← 纯 DTO 与工具（不依赖 Spring）
   ▲
   │
sdp-external-driver     ← 驱动 SPI 接口：HostDriver / RpcDriver / ConfigDriver / ResourceDriver / ServeDriver / BroadcastDriver / LogDriver / ProcessDriver / PilotDriver / SshDriver / WormholeDriver
   ▲
   │
sdp-core                ← 反射扫描 + 抽象框架：AbstractServe / AbstractProcess / AbstractConfig / AbstractHost / AbstractSdpManager / AbstractHostManager、注解集、ServeHandler / ProcessHandler 枚举、DagUtil、CSVConverter
   ▲
   │
sdp-spring-boot-starter ← SdpAutoConfigure + SdpEnvManager + META-INF/spring.factories
```

自动装配入口：`cn.gsq.sdp.core.SdpAutoConfigure`（通过 `sdp-spring-boot-starter/src/main/resources/META-INF/spring.factories` 注册）。它注入 `AbstractSdpManager` / `AbstractHostManager` / `SdpEnvManager`，并以 `@ConditionalOnMissingBean` 方式注入 **13 个驱动 Bean**——默认实现是只打日志的空壳，**消费者至少要覆盖 `HostDriver` / `RpcDriver` / `ConfigDriver` / `ResourceDriver`**，否则整个集群什么都不会发生。

启动顺序（由 `SdpEnvManager` 强制要求）：
1. 消费者在 `SdpEnvManager` 实例化**之前**调用 `GalaxySpringUtil.setGlobalArgument("sdp.root.classpath", ...)`，否则启动直接抛 `RuntimeException`。
2. `loadSdp(version)` — 反射扫描版本包，把 `@Serve` / `@Process` / `@Config` / `@Host` / `@Mode` 注册为 Spring Bean。
3. `setMode(mode)` — 选择主机分组模式。
4. `loadEnvResource()` — 通过 `HostDriver.loadHosts()` 拉主机清单，触发各组件 `loadEnvResource()` 钩子。

跳过或乱序会静默得到空集合或 "分组模式不存在" 错误。

## 构建命令

标准 Maven，没有 wrapper、没有测试：

```bash
mvn clean install -DskipTests              # 构建全部 4 个模块并装入本地仓库
mvn -pl sdp-core -am clean install         # 单模块 + 其依赖
mvn -pl sdp-spring-boot-starter -am package
mvn dependency:tree -pl sdp-core           # 排查依赖冲突
```

父 POM `io.github.gaoshq7:parent:1.0.2` 锁定编译器 / 编码 / Spring Boot 版本；本仓库的父级是 `sdp-parent:1.1.4-General`。发布新版本时改根 `pom.xml` 的 `<version>`，子模块继承。

仓库**完全没有单元/集成测试**（不存在 `src/test/` 目录）。不要凭空给出 `mvn test` 流程，也不要声称"测试通过"——验证应在下游消费方进行。

## 不读 skill 就看不出来的关键约定

以下都是反射扫描器强约束，违反会**静默失败**（版本包被丢弃 / Bean 不注册），不会有编译错误：

1. **三方命名必须严格对齐**：`@Sdp(version = "v5.3.1")` ↔ Java 包名末段 `v531`（去掉点号）↔ `resources/v531/` 顶层目录。任一不匹配，该版本包被静默丢弃。
2. **资源目录布局**：`resources/<sdpVersion>/<ServeSimpleClassName>/...`，服务子目录名**严格区分大小写**地等于 `@Serve` 类的简单类名（`HDFS` 不是 `Hdfs`；`PrestoSQL` 不是 `Prestosql`）。
3. **CSV 字典文件必须正好 10 列**：`key, v_dictionary, v_default, description_en, description_ch, isMust, delimiter, labels, authority, isSysConfig`。列数不对会被静默降级为纯文本模式，元数据全丢。单分支字典文件用 `$.csv` 后缀；多分支一般不带 `$`。
4. **一个版本包只能有一个 `@Host` 类**，且必须有 `(String hostname, List<String> groups)` 构造器。
5. **`@Mode` 标注的类必须是枚举且实现 `HostGroup`**，不要覆盖 `mode()` 方法。
6. **`@Function.id`** 在同一服务/进程下必须唯一；`@Available(fid = ...)` 通过此 id 关联。
7. **`@Serve` / `@Process` / `@Config` 的 `order`** 在同层级内不可重复。
8. **`@Process(dynamic = true)`** 必须同时覆盖 `extend(AbstractHost)` 与 `shorten(AbstractHost)`，否则扩缩容看似成功实则空操作。

更深的规则与每条约束背后的源码引用，见 `skills/sdp-core/SKILL.md` 及其 `references/`。

## 按任务类型的首选阅读位置

| 任务 | 先读 |
|---|---|
| 改驱动 SPI 接口签名 | `sdp-external-driver/src/main/java/cn/gsq/sdp/driver/` |
| 改 DTO（Blueprint / ConfigItem / HostInfo / RpcRequest/Respond …） | `sdp-common/src/main/java/cn/gsq/sdp/` |
| 改框架行为（扫描、安装生命周期、DAG 排序、CSV 解析） | `sdp-core/src/main/java/cn/gsq/sdp/core/` |
| 改自动装配或默认驱动空实现 | `sdp-spring-boot-starter/src/main/java/cn/gsq/sdp/core/SdpAutoConfigure.java` |
| 改启动 / 版本加载流程 | `sdp-spring-boot-starter/src/main/java/cn/gsq/sdp/core/SdpEnvManager.java` |
| 在版本包里新增一个大数据服务 | `skills/sdp-core/SKILL.md`（生产者 skill） |
| 把 SDP 接入 Web 应用 / 实现驱动 | `skills/sdp-starter/SKILL.md`（消费者 skill） |
| 注解字段全量参考 / CSV 布局示意图 | 仓库根 `README.md`（中文，截图在 `images/`） |

## 其它

- 仓库文档、提交信息、代码注释主要使用 **中文**，保持一致。
- 当前活跃开发分支是 `general`，`main` 是发版分支。
- `.claude/skills/galaxy-async`、`.claude/skills/galaxy-spring-util` 是指向兄弟仓库 `common-boot` 的 symlink，处理 galaxy 异步/Spring 工具相关下游 API 时按需调用。
