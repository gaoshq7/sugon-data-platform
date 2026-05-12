---
name: sdp-starter
description: Use this skill whenever an upstream Spring Boot / web application integrates the sugon-data-platform (SDP) via the `sdp-spring-boot-starter` dependency and needs to operate the cluster from the web layer — managing SDP versions, hosts, services, processes, or configuration files. Required when the user mentions SdpEnvManager, SdpManager, HostManager, AbstractServe, AbstractProcess, AbstractConfig, AbstractHost, loading SDP versions, switching deployment modes, registering/removing hosts, installing/starting/stopping a service, updating service configuration, implementing driver beans (HostDriver / RpcDriver / ConfigDriver / ResourceDriver), or any "operate the underlying cluster from the web layer" task. Covers the mandatory three-step bootstrap (loadSdp → setMode → loadEnvResource), the SDP/host management APIs, the driver beans the host application must override, and the common pitfalls. This skill is for the *consumer* side; if the user is authoring `@Serve` / `@Process` / `@Config` service packs (depending on `sdp-core`), use the `sdp-core` skill instead.
---

# sdp-sdk：sugon-data-platform 上层集成 SDK 使用指南

## 0. 这是什么 / 何时用

`sugon-data-platform`（下称 **SDP**）是一套类 Ambari 的大数据集群运维框架，以 Maven SDK 形式分发。**上层 Web 系统**通过 Spring 注入三个核心 Bean 来操作底层集群：

| Bean | 角色 | 类比 Ambari |
|---|---|---|
| `SdpEnvManager` | **环境管理**：SDP 版本扫描/切换、部署模式选择、整体初始化 | Stack 切换 + Cluster 初始化 |
| `AbstractSdpManager` | **SDP 生态管理**：服务（`AbstractServe`）、进程（`AbstractProcess`）、配置（`AbstractConfig`）的查询与生命周期操作 | Service / Component / Config |
| `AbstractHostManager` | **主机管理**：主机注册/删除、分组、远程操作（`AbstractHost`） | Host / HostComponent |

三者**强绑定**——上层任何对集群的操作都从这里开始。

**何时调用本 skill：**

- 上层项目首次引入 SDP SDK，需要打通 Spring 集成；
- 上层要写 Controller / Service / 后台任务调用 SDP API（无论是查服务、改配置、加主机、启停进程……）；
- 怀疑环境未初始化导致空集合、`NullPointerException`、`RuntimeException("...不存在")`；
- 需要覆盖 SDP 的驱动 Bean（`HostDriver` / `RpcDriver` / `ConfigDriver` / `ResourceDriver` 等）对接自家持久层/通信层。

---

## 1. 总体架构与调用关系

```
                    ┌────────────────────────────┐
   Spring 注入       │     SdpEnvManager          │  ← 入口
   ───────────►     │  (环境管理 / 三步初始化)     │
                    └──────┬──────────┬──────────┘
                           │          │
                  getSdp   │          │  getHost
                  Manager  ▼          ▼  Manager
                 ┌──────────────┐  ┌──────────────────┐
                 │ AbstractSdp  │  │ AbstractHost     │
                 │ Manager      │  │ Manager          │
                 │ (服务/进程/   │  │ (主机/分组)       │
                 │  配置)        │  │                  │
                 └──────┬───────┘  └─────────┬────────┘
                        │                    │
              getServes │                    │ getHosts
                        ▼                    ▼
              ┌────────────────┐    ┌────────────────┐
              │ AbstractServe  │    │ AbstractHost   │
              │  ├─ Process    │    │ (远程操作单元)   │
              │  └─ Config     │    │                │
              └────────────────┘    └────────────────┘
```

**核心调用流（必须按此顺序）：**

```
启动:  设置 sdp.root.classpath
       └─► 注入 SdpEnvManager
              └─► loadSdp(version)          ← 必须最先
                    └─► setMode(mode)        ← 必须其次
                          └─► loadEnvResource()   ← 之后 SdpManager / HostManager 才可用
```

---

## 2. Maven 依赖与 Spring 装配

### 2.1 Maven 坐标

只需引入 starter，其余模块（`sdp-core` / `sdp-common` / `sdp-external-driver`）自动传递：

```xml
<dependency>
    <groupId>io.github.gaoshq7</groupId>
    <artifactId>sdp-spring-boot-starter</artifactId>
    <version>1.1.4-General</version> <!-- 以宿主项目实际版本为准 -->
</dependency>
```

### 2.2 自动注入的 Bean（`SdpAutoConfigure`）

| Bean | 何时使用 | 上层用法 |
|---|---|---|
| `SdpEnvManager` | 一切操作之前 | `@Autowired` 直接注入 |
| `AbstractSdpManager` | 查/操作服务、进程、配置 | `sdpEnvManager.getSdpManager()` |
| `AbstractHostManager` | 查/操作主机、分组 | `sdpEnvManager.getHostManager()` |

同时为 13 个**驱动接口**注入了 `@ConditionalOnMissingBean` 的空实现，**上层必须覆盖关键驱动**（`HostDriver` / `RpcDriver` / `ConfigDriver` / `ResourceDriver`），详见 §6 与 `references/driver-overrides.md`。

### 2.3 必须设置的全局变量

在 `SdpEnvManager` 被实例化之前设置 `sdp.root.classpath`，指向上层项目里所有 SDP 版本包的根：

```java
import cn.gsq.common.config.GalaxySpringUtil;

@Component
public class SdpBootstrap {
    @PostConstruct
    public void setup() {
        GalaxySpringUtil.setGlobalArgument("sdp.root.classpath", "io.github.sdp");
    }
}
```

> 未设置则 `SdpEnvManager` 构造器抛 `RuntimeException("全局变量'sdp.root.classpath'不能为空!")`，Spring 上下文起不来。

---

## 3. 三步初始化（必须按顺序）

```
loadSdp(version) ──► setMode(mode) ──► loadEnvResource()
```

| 步骤 | 方法 | 作用 | 跳过/错序会怎样 |
|---|---|---|---|
| 1 | `loadSdp(String version)` | 扫描该版本包下所有 `@Sdp` / `@Host` / `@Mode` / `@Serve` / `@Process` / `@Config` 注解类，注册成 Spring Bean | 直接 `setMode` 会抛 "分组模式不存在" |
| 2 | `setMode(String mode)` | 选定主机分组模式 | 跳过则 `getHostGroups()` 返空，后续主机不会被任何服务/进程视作合法目标 |
| 3 | `loadEnvResource()` | 从 `HostDriver.loadHosts()` 拉主机清单，注册主机 Bean，触发所有组件 `loadEnvResource()` 钩子 | 跳过则 `SdpManager` / `HostManager` 拿到的都是空集合 |

**典型用法**（首次激活 + 进程重启自动重放）：

```java
@Component
@RequiredArgsConstructor
public class SdpAutoActivate implements ApplicationRunner {
    private final SdpEnvManager sdpEnvManager;
    private final ClusterProfileRepo repo;          // 上层自定义的持久化层

    @Override
    public void run(ApplicationArguments args) {
        ClusterProfile p = repo.current();
        if (p == null) return;                       // 集群尚未激活
        sdpEnvManager.loadSdp(p.getVersion());       // Step 1
        sdpEnvManager.setMode(p.getMode());          // Step 2
        sdpEnvManager.loadEnvResource();             // Step 3
    }
}
```

切换版本时**整套三步重做**——`loadSdp` 会清掉所有 SDP 包下的 Bean 并重置 mode。

> 完整 API 表与 `getVersions()` / `getModes()` 等查询接口，见 **`references/sdp-env.md`**。

---

## 4. SDP 管理（服务 / 进程 / 配置）速查

通过 `sdpEnvManager.getSdpManager()` 拿到 `AbstractSdpManager`，它实现 `SdpManager` 接口。

### 4.1 高频查询 API

| 方法 | 返回 | 用途 |
|---|---|---|
| `getVersion()` / `getHome()` | `String` | 当前 SDP 版本号 / 安装根目录 |
| `getServes()` | `List<AbstractServe>` | 当前版本所有服务（按 order 排序） |
| `getServeByName(name)` | `AbstractServe` | 按名取服务 |
| `getProcessByName(name)` | `AbstractProcess` | 按名取进程（全局唯一） |
| `getConfigByName(serveName, configName)` | `AbstractConfig` | 取某服务的某配置文件 |
| `getParents(serveName)` / `getChildren(serveName)` | `List<AbstractServe>` | 上下游依赖（DAG） |
| `getHostsMapping()` | `Map<hostname, List<processName>>` | 主机→进程映射 |
| `getProcessModelsByHostname(hostname)` | `List<AbstractProcess>` | 某主机上运行的进程 |
| `isHostCanRemove(hostname)` | `boolean` | 该主机能否安全下线 |

### 4.2 服务生命周期（`AbstractServe`）

| 方法 | 用途 |
|---|---|
| `install(Blueprint.Serve)` | 按蓝图安装（带主机/分组/配置初值） |
| `start()` / `stop()` / `restart()` | 按 DAG 顺序启停服务的所有进程 |
| `uninstall()` | 卸载，反序执行 |
| `recover()` | 卸载后的环境恢复 |
| `isInstalled()` / `isAvailable()` | 状态查询 |
| `getAllConfigs()` / `getDisplayConfigs()` | 配置列表（含/不含隐藏） |
| `updateConfigDefault(cname, items)` | 修改默认分支配置 |

### 4.3 进程生命周期（`AbstractProcess`）

| 方法 | 用途 |
|---|---|
| `start()` / `stop()` / `restart()` | 自身启停（在所有承载主机上） |
| `extend(List<String> hostnames)` | 扩容到新主机 |
| `shorten(List<String> hostnames)` | 从主机缩容 |
| `canStart()` / `canStop()` / `canExtend()` / `canShorten()` | 操作前置校验 |
| `getActiveHosts()` / `getDeadHosts()` | 健康/异常主机分组 |
| `currentLog(hostname)` | 拉远端日志末尾 |

### 4.4 配置管理（`AbstractConfig`）

| 方法 | 用途 |
|---|---|
| `getBranchNames()` | 所有分支（如 `default` / `master` / `worker`） |
| `getDefaultBranchContent()` / `getBranchContent(name)` | 分支配置内容 Map |
| `getDefaultBranchDetails()` / `getBranchDetails(name)` | 含字典元数据（用于前端展示） |
| `updateConfig(branchName, items)` / `updateDefaultConfig(items)` | 修改并同步到主机 |
| `addBranchHosts(...)` / `delBranchHosts(...)` | 调整分支覆盖主机 |
| `getConfigCompare(b1, b2)` | 两版本/分支差异对比 |

> 每个方法的完整签名、参数语义、Blueprint 结构、典型代码片段，见 **`references/sdp-manager.md`**。

---

## 5. 主机管理速查

通过 `sdpEnvManager.getHostManager()` 拿到 `AbstractHostManager`（实现 `HostManager` 接口）。

### 5.1 主机管理器（`HostManager` 接口）

| 方法 | 用途 |
|---|---|
| `addHosts(HostInfo...)` | 注册新主机（含 hostname + groups） |
| `removeHosts(String... hostnames)` | 删除主机 |
| `updateHostGroups(hostname, groups)` | 改主机分组（整体覆盖） |
| `getHosts()` / `getHostByName(name)` | 查询 |
| `getHostGroups()` | 当前 mode 下所有分组定义 |

### 5.2 远程操作（`AbstractHost`）

每台主机对应一个 `AbstractHost` Bean（Bean name 即 hostname），上面可直接调：

| 方法 | 用途 |
|---|---|
| `start()` / `stop()` / `restart()` | 启停该主机上的全部进程 |
| `startProcess(name)` / `stopProcess(name)` / `restart(name)` | 单进程启停 |
| `isProcessActive(process)` | 端口探针 + ps 兜底 |
| `isHostActive()` | 主机存活（走 `HostDriver.isAlive`） |
| `actuator(name, params)` | Wormhole 脚本执行（远程命令） |
| `mountDisk(diskInfo)` / `listDisk(op)` | 磁盘操作 |
| `downloadPackage(version, pkg)` | 拉安装包到本机 |
| `isFileExist(path)` / `currentLog(...)` | 文件/日志辅助 |
| `canRemove()` | 是否能从集群移除 |

> 完整签名、`HostInfo` / `HostGroup` 数据结构、Wormhole 鉴权、`@Function` 注解扩展机制，见 **`references/host-manager.md`**。

---

## 6. 驱动覆盖（必读）

`SdpAutoConfigure` 为 13 个驱动接口都提供了空实现 Bean，**真正能跑起来的关键覆盖**：

| 驱动 | 必须覆盖？ | 一句话作用 |
|---|---|---|
| `HostDriver` | ✅ | 主机清单装载、增删回调、存活检查 |
| `RpcDriver` | ✅ | 远端脚本执行通道（否则启停全部 no-op） |
| `ConfigDriver` | ✅ | 配置下发到目标主机 |
| `ResourceDriver` | ✅ | 安装包下载、SDP/服务可用性判定 |
| `ServeDriver` | 视需要 | 服务安装/卸载完成回调（一般用来落库） |
| `BroadcastDriver` / `LogDriver` / `ProcessDriver` / `SshDriver` / `WormholeDriver` | 视需要 | 见详表 |
| `PilotDriver` | ❌ 已 `@Deprecated` | 旧 Agent 通道，不再用 |

覆盖方式：在任意 `@Configuration` 中声明一个同类型 `@Bean`，即可替换默认实现。

> 13 个驱动的字段语义、典型实现样例（含 JPA 仓库对接），见 **`references/driver-overrides.md`**。

---

## 7. 易踩坑（精选）

按踩坑频率从高到低：

1. **三步顺序不能乱**；切换版本会重置 mode，必须重做 setMode + loadEnvResource。
2. **`sdp.root.classpath` 未设置 → 启动直接挂**。需在 `SdpEnvManager` Bean 注入前完成。
3. **`HostInfo.groups` 必须是当前 mode 下合法的 group 名**，否则主机虽注册但不参与部署。debug 时先查 `getHostManager().getHostGroups()` 看合法 group 名。
4. **`getVersions().isAvailable`** 来源是 `ResourceDriver.isSdpAvailable`，默认永远 false——做版本激活 UI 时务必覆盖。
5. **`AbstractServe.install` / `start` 等带 `synchronized`**，并发触发同一服务会串行。版本切换、跨服务批量操作请自行加业务锁。
6. **`updateConfig` 不会自动重启服务**——配置同步到磁盘但进程不会感知，需显式 `restart()`。
7. **`addHosts` 是重操作**：会触发安装包下载和初始化，单机可能数分钟，Web 接口请异步处理。

> SDP 包侧的约定（`@Sdp` / `@Host` / `@Mode` 注解、`package-info.java` 命名等）属于**生产者视角**，与本 skill 无关；若用户在编写 SDP 服务包，请改用 `sdp-core` skill。

> 完整坑表与解决建议，见 `references/sdp-env.md` 与各 reference 文件末尾。

---

## 8. 何时读哪份 reference

> **按需读取，避免一次性把全部细节塞进上下文**。

| 任务场景 | 必读 reference |
|---|---|
| 启动集成 / 写初始化代码 / 切换版本 | `references/sdp-env.md` |
| 写"服务安装/启停/配置查询/修改"相关 Controller 或 Service | `references/sdp-manager.md` |
| 写"主机纳管/分组调整/远程命令执行"相关 Controller 或 Service | `references/host-manager.md` |
| 实现 `HostDriver` / `RpcDriver` / `ConfigDriver` / `ResourceDriver` 等驱动 | `references/driver-overrides.md` |
| 不确定是哪类任务 | 先读本 SKILL.md 的 §1 架构图，再决定 |

各 reference 文件的开头都有一节"对照源码位置"，便于交叉验证。

---

## 9. 姊妹 skill

| Skill | 何时改用 |
|---|---|
| `sdp-core` | 你在写 SDP 服务包本身（依赖 `sdp-core`），用 `@Serve` / `@Process` / `@Config` 注解定义大数据组件接入 |
| `sdp-starter`（本 skill） | 你在写 Web 系统（依赖 `sdp-spring-boot-starter`），通过 `SdpEnvManager` / `SdpManager` / `HostManager` 操作集群 |
