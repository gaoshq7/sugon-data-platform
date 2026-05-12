---
name: sdp-core
description: Use this skill whenever a project depends on `sdp-core` and the user is authoring an SDP service pack — defining big-data services / processes / configuration files via the `@Sdp`, `@Host`, `@Mode`, `@Serve`, `@Process`, `@Config`, `@Function`, `@Available`, `@Status`, `@Group` annotations, extending `AbstractServe` / `AbstractProcess` / `AbstractConfig` / `AbstractHost`, organizing `package-info.java`, writing CSV configuration dictionaries, selecting ServeHandler / ProcessHandler modes, or adding a new big-data component (HDFS, Spark, Kafka, Doris, etc.) into an SDP version pack. This skill is for the *producer* side. If the user is writing a web layer that calls SdpEnvManager / SdpManager / HostManager to operate the cluster, use the `sdp-starter` skill instead.
---

# sdp-core：SDP 服务包开发者指南

## 0. 这是什么 / 何时用

**SDP 服务包**是依赖 `sdp-core` 的纯 Java 项目，用注解声明式地定义一个大数据生态（如 HDP 5.3.1）：包含哪些服务、每个服务由哪些进程组成、每个进程有什么配置文件、如何在主机上启停。框架反射扫描这些注解类，把它们装配成 Spring Bean，由 Web 系统（依赖 `sdp-spring-boot-starter`）通过 SdpManager / HostManager 调用执行。

**何时调用本 skill：**

- 要在某个 SDP 版本包里**新增**一个服务（HDFS、Spark、Kafka、Doris、Presto…）；
- 要**修改**已有服务的进程启停命令、配置项、依赖关系；
- 要新增一个**部署模式**（@Mode）或调整主机分组（HostGroup）；
- 要扩展某服务的**自定义功能函数**（@Function）；
- 不确定 `@Serve.handler` / `@Process.handler` 该选哪个模式；
- 不确定 CSV 字典文件该放在 `resources/` 的哪个路径。

> 如果你在写 Web 后端调用集群（Controller / Service / 驱动实现），用 `sdp-starter` skill 而不是本 skill。

---

## 1. Maven 依赖

SDP 服务包只需依赖 `sdp-core`，**不要**引 `sdp-spring-boot-starter`：

```xml
<dependency>
    <groupId>io.github.gaoshq7</groupId>
    <artifactId>sdp-core</artifactId>
    <version>1.1.4-General</version> <!-- 以宿主项目实际版本为准 -->
</dependency>
```

`sdp-core` 自动传递 `sdp-common`（数据模型）与 `sdp-external-driver`（驱动接口）。

---

## 2. SDP 服务包整体结构（galaxy-libraries 真实结构）

一个 SDP 版本包（例如 v5.3.1）的目录树：

```
src/main/java/com/sugon/gsq/libraries/v531/   ← 版本根包，末段 v531 ↔ @Sdp(version="v5.3.1")
├── package-info.java                          ← @Sdp(version = "v5.3.1") ✅必须
├── SdpHost531Impl.java                        ← @Host() extends AbstractHost ✅必须，每版本仅 1 个
├── SCIsolateMode.java                         ← @Mode("存算分离") enum，与 @Host 类同级即可
├── MasterSlaveMode.java                       ← @Mode("主从混合")
├── hdfs/                                      ← Java 子包小写
│   ├── HDFS.java                              ← @Serve（类名遵循官方组件命名：缩写全大写）
│   ├── config/
│   │   ├── CoreSiteXml.java                   ← @Config
│   │   └── HdfsSiteXml.java
│   └── process/
│       ├── NameNode.java                      ← @Process extends AbstractProcess<SdpHost531Impl>
│       ├── DataNode.java
│       ├── JournalNode.java
│       └── Zkfc.java
├── prestosql/
│   └── PrestoSQL.java                         ← 类名 PrestoSQL，对应 resources/v531/PrestoSQL/
└── ...

src/main/resources/v531/                       ← ⚠️ 顶层是 SDP 版本号目录
├── HDFS/                                      ← 服务子目录名 = @Serve 类简单类名（严格大小写一致）
│   ├── core-site.xml$.csv                     ← 单分支 + 字典（10 列固定）
│   ├── hdfs-site.xml$.csv
│   ├── dfs.hosts.csv                          ← 不带 $ 的纯文本配置
│   └── hadoop-env.sh.csv
└── PrestoSQL/
    ├── jvm.config.csv                         ← 单分支纯文本
    └── config/                                ← 多分支用子目录
        ├── master.properties.csv              ← 多分支文件名一般 *不带* $
        └── worker.properties.csv
```

> **关键约定**（详见 `references/annotations-config.md` §2）：
> - `resources/` 顶层是 **SDP 版本号目录**（`v531` ↔ `@Sdp(version="v5.3.1")` 去 `.`）；
> - 服务子目录名**与 `@Serve` 类简单类名完全一致**（`HDFS` / `PrestoSQL` / `Zookeeper`）；
> - 单分支字典文件带 `$`；多分支文件一般**不带** `$`。

---

## 3. "新增一个服务"端到端速览

以新增 Doris 为例，5 步完成：

### Step 1：在版本根包下声明服务类（类名遵循官方组件命名）

```java
package com.sugon.gsq.libraries.v531.doris;

@Serve(
    version = "1.2.4.1",
    handler = ServeHandler.MULTI_ROLE_MODE,          // 多角色模式（FE/BE）
    type = ClassifyHandler.BIGDATA,
    depends = { Zookeeper.class },                   // 依赖
    description = "Apache Doris MPP 数据仓库",
    pkg = "doris",
    order = 20
)
public class Doris extends AbstractServe {
    // 钩子按需覆盖，全部可选
}
```

### Step 2：写进程（每个 BE / FE 一个类）

```java
package com.sugon.gsq.libraries.v531.doris.process;

@Process(
    master = Doris.class,
    handler = ProcessHandler.SLAVE,
    groups = { @Group(mode = SCIsolateMode.class, name = "DATA") },
    mark = "DorisBE",
    home = "/doris/be",
    start = "./bin/start_be.sh --daemon",
    stop  = "./bin/stop_be.sh",
    dynamic = true,
    order = 2,
    min = 3, max = -1
)
public class DorisBe extends AbstractProcess<SdpHost531Impl> {
    @Override
    public Integer getPort() { return 9050; }

    @Override
    protected void extend(AbstractHost h)  { /* 扩容时单机操作 */ }
    @Override
    protected void shorten(AbstractHost h) { /* 缩容时单机操作 */ }
}
```

### Step 3：写配置文件类

```java
package com.sugon.gsq.libraries.v531.doris.config;

@Config(
    master = Doris.class,
    type = "cfg",
    path = "/doris/fe/conf/fe.conf",
    description = "Doris FE 配置",
    order = 1
)
public class FeConf extends AbstractConfig {
    // initContents 钩子按需覆盖
}
```

### Step 4：放 CSV 字典数据

**路径**：`resources/v531/Doris/fe.conf$.csv`（注意顶层是版本号 `v531`，子目录是服务类名 `Doris`）。

**格式**（10 列固定，CSV 加载器按列索引解析）：

```
key,v_dictionary,v_default,description_en,description_ch,isMust,delimiter,labels,authority,isSysConfig
```

详细列含义见 `references/annotations-config.md` §2.4。

### Step 5：（可选）加自定义功能函数

```java
@Function(id = "REBALANCE", name = "数据再平衡")
public void rebalance() {
    // 通过 sdpManager / hostManager 实现业务
}

@Available(fid = "REBALANCE")
public boolean canRebalance() { return isAvailable() && /* ... */; }
```

完成。`sdp-spring-boot-starter` 端调用 `sdpEnvManager.loadSdp("v5.3.1")` 即会自动扫描并装配这个新服务。

---

## 4. 注解速查表

| 注解 | 作用对象 | 必填字段 | 关键可选字段 |
|---|---|---|---|
| `@Sdp(version)` | `package-info.java` | `version` | — |
| `@Host` | 主机代理类 | （无） | — |
| `@Mode(value)` | 部署模式枚举类 | `value` | — |
| `@Serve(...)` | 服务类 | `version`, `handler`, `description`, `order` | `type`, `depends`, `appends`, `labels`, `all`, `pkg` |
| `@Process(...)` | 进程类 | `master`, `handler`, `mark`, `home`, `start`, `stop`, `order` | `depends`, `companions`, `excludes`, `groups`, `dynamic`, `min`, `max` |
| `@Config(...)` | 配置类 | `master`, `type`, `path`, `order` | `description`, `branches`, `show` |
| `@Function(id, name)` | 方法 | `id`, `name` | `isReveal` |
| `@Available(fid)` | 方法 | `fid` | — |
| `@Status(value)` | 方法 | `value`（AppStatus 枚举） | — |
| `@Group(mode, name)` | 嵌入 `@Process.groups` | `mode`, `name` | — |

---

## 5. ServeHandler / ProcessHandler 模式速查

### ServeHandler（@Serve.handler）

| 枚举 | 适用场景 |
|---|---|
| `MASTER_SLAVE_MODE` | 传统 HA 主从（HDFS NameNode 主备） |
| `MASTER_ELECTION_MODE` | 选举主进程（Zookeeper） |
| `STAND_ALONE_MODE` | 单机单进程（小工具型服务） |
| `FRAGMENT_ALONE_MODE` | 多进程协同（HBase Master + RegionServer + ThriftServer） |
| `MULTI_ROLE_MODE` | 同一进程多角色（Presto coordinator/worker、Elasticsearch master/data/client） |

### ProcessHandler（@Process.handler）

| 枚举 | 可用性判定 |
|---|---|
| `MASTER` | 所有承载主机都健康才算可用 |
| `SLAVE` | 宕机数 < 总数 / 2 才算可用（多数派） |
| `WATCH` | 同 MASTER（守护进程） |
| `ALONE` | 同 MASTER（独立单实例） |
| `ELECTION` | 同 SLAVE（选举进程） |

> 详细模式判定算法、为何 WATCH 等同 MASTER 等内部逻辑，见 `references/annotations-serve.md` 与 `references/annotations-process.md`。

---

## 6. 关键强约束（必读）

按踩坑频率从高到低：

1. **三处命名必须一致**：`@Sdp(version)` 去 `.` ↔ Java 包名末段 ↔ resources 顶层目录名。
   例：`@Sdp(version = "v5.3.1")` → 包名末段 `v531` → `resources/v531/`。任一不匹配，该版本被静默丢弃。

2. **resources 路径必须有版本号一层**：`resources/{sdpVersion}/{serveClassName}/...`，**不是** `resources/{serveClassName}/...`。

3. **服务子目录名严格等于 `@Serve` 类的简单类名**（大小写敏感）。`@Serve class HDFS` 对应 `resources/v531/HDFS/`，**不是 `Hdfs/`**。类名遵循官方组件命名（缩写全大写）：`HDFS` / `PrestoSQL` / `Zookeeper`。

4. **CSV 字典文件必须正好 10 列**：`key, v_dictionary, v_default, description_en, description_ch, isMust, delimiter, labels, authority, isSysConfig`。列数 ≠ 10 自动转纯文本模式，所有元数据丢失。表头第一行的列名仅给人看，CSV 加载器**按索引而非按列名**取值。

5. **单分支文件带 `$`，多分支文件一般不带 `$`**：`xxx$.csv` 触发字典模式（10 列）；`{branch}.csv` 多分支用纯文本格式。

6. **一个版本包只能有一个 `@Host` 注解类**，且**必须有 `(String hostname, List<String> groups)` 构造器**。

7. **`@Mode` 标注的类必须是枚举且实现 `HostGroup`**；**不要覆盖 `mode()` 方法**——框架反射读取 `@Mode("XXX")` 的 value 作为 group 归属的 mode 桶。

8. **`@Serve` / `@Process` / `@Config` 的 `order` 字段在同层级内不可重复**。

9. **`@Process.dynamic = true` 时必须覆盖 `extend(AbstractHost)` 和 `shorten(AbstractHost)`**——否则扩缩容看似成功但实际未生效。

10. **`@Config.master` 与 `@Process.master`** 指向的服务类必须存在于同一版本包内。

11. **`BranchModel` 构造器参数顺序是 `(name, hostnames, content)`**（不是 `(name, content, hostnames)`）。

12. **`@Function.id` 在同一服务/进程下必须唯一**——重复会导致 `@Available` 找不到目标函数。

---

## 7. 何时读哪份 reference

| 任务 | 读这份 |
|---|---|
| 新建 SDP 版本包 / 配置 `package-info.java` / 写 `@Host` / `@Mode` 枚举 | `references/annotations-package.md` |
| 写或改 `@Serve` 类 / 选 ServeHandler / 覆盖 install 钩子 / 加 `@Function` | `references/annotations-serve.md` |
| 写或改 `@Process` 类 / 选 ProcessHandler / 实现扩缩容 | `references/annotations-process.md` |
| 写或改 `@Config` 类 / 设计 CSV 字典 / 处理分支 | `references/annotations-config.md` |
| 不确定属于哪类，先看本 SKILL.md §3 端到端速览 |

各 reference 文件开头都有"对照源码"小节，便于交叉验证。

---

## 8. 关键源码位置

- 注解定义：`sdp-core/cn/gsq/sdp/core/annotation/{Sdp,Host,Mode,Serve,Process,Config,Function,Available,Status,Group}.java`
- 父类：`sdp-core/cn/gsq/sdp/core/{AbstractServe,AbstractProcess,AbstractConfig,AbstractHost}.java`
- 模式枚举：`sdp-core/cn/gsq/sdp/core/{ServeHandler,ProcessHandler}.java`
- 分组接口：`sdp-core/cn/gsq/sdp/core/HostGroup.java`
- README：项目根的 `README.md` §42-372 含全字段示例
