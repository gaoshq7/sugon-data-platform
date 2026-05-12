# Reference: @Config / AbstractConfig 完整规范

> 本文件是 `sdp-core` skill 的扩展参考。仅在以下场景读取：
> - 写或修改一个 `@Config` 配置文件类；
> - 设计 CSV 字典文件（默认值 + 元数据）；
> - 配置文件包含多分支（master / worker 等）；
> - 实现 `initContents` 钩子按 Blueprint 修改配置；
> - 不确定 CSV 文件应该放在 `resources/` 的哪个路径。

---

## 0. 对照源码

- `sdp-core/cn/gsq/sdp/core/annotation/Config.java`
- `sdp-core/cn/gsq/sdp/core/AbstractConfig.java`
- `sdp-common/cn/gsq/sdp/ConfigItem.java`
- `sdp-common/cn/gsq/sdp/ConfigBranch.java`

---

## 1. `@Config` 全字段

```java
public @interface Config {
    Class<? extends AbstractServe> master();           // ✅ 所属服务
    String type();                                     // ✅ 文件类型（xml/cfg/properties/text...）
    String path();                                     // ✅ 目标文件绝对路径
    String description() default "";
    String[] branches() default {};                    // 分支名列表，留空表示单分支（default）
    boolean show() default true;                       // 是否在 UI 暴露给用户编辑
    int order();                                       // ✅ 在服务配置列表中的排序
}
```

### 1.1 `master`

必须指向同一版本包内的 `@Serve` 类。

### 1.2 `type`

自定义字符串，标识配置文件格式。**框架不解析**，仅用于：

- UI 端选合适的编辑器/语法高亮；
- `ConfigDriver.conform()` 内部按 type 选用渲染器（接入方实现时使用）。

常见取值：`xml`、`cfg`、`properties`、`yaml`、`json`、`text`。

### 1.3 `path`

部署到主机后配置文件的绝对路径（或相对 `home` 的路径，由 ConfigDriver 实现决定）。例如：

```java
path = "/spark/conf/spark-defaults.conf"
```

### 1.4 `branches`

分支名列表。**留空表示单分支**（框架自动用 `default` 分支）。多分支用于"同一配置文件在不同主机上有不同内容"的场景：

```java
@Config(
    branches = { "master", "worker" },
    ...
)
public class PrestoConfig extends AbstractConfig { }
```

每个分支独立维护自己的内容和覆盖主机集合。

### 1.5 `show`

- `true`（默认）：用户可在 UI 上看到并编辑此配置；
- `false`：仅框架内部使用，不暴露给用户。常用于"必须 SDP 自动管理"的关键配置（如 `core-site.xml` 中由 HA 拓扑决定的项）。

### 1.6 `order`

在所属服务的配置列表中的排序号。同一服务内不重复。`AbstractServe.getAllConfigs()` 按此排序。

---

## 2. CSV 字典文件

### 2.1 路径约定（含 SDP 版本号目录）

**关键**：`resources/` 顶层是 **SDP 版本号**（与 `@Sdp.version` 去 `.` 后一致，与 Java 包名末段相同），下一层才是服务名。

**单分支**：

```
resources/{sdpVersion}/{serveClassName}/{configClassName}[$].csv
```

**多分支**：

```
resources/{sdpVersion}/{serveClassName}/{configClassName}/{branchName}.csv
```

> - `sdpVersion`：例如 `v531`（来自 `@Sdp(version = "v5.3.1")`）；
> - `serveClassName`：`@Serve` 类的简单类名，**与类名严格一致**（如 `HDFS` / `PrestoSQL` / `Zookeeper`）；
> - `configClassName`：配置文件名（与 `@Config.path` 末段一致，如 `core-site.xml`）；
> - **单分支文件可带 `$`**，**多分支文件名一般不带 `$`**（见 §2.2）。

### 2.2 `$` 后缀的含义

- 文件名带 `$`（即 `xxx$.csv`）：CSV 内包含**完整配置字典**（10 列固定），每项有元数据（默认值、中英文描述、权限等级、标签…）；
- 文件名不带 `$`（即 `xxx.csv`）：**纯文本模式**——CSV 列数 < 10，按"整文件单一 ConfigItem"处理（见 §2.4 模式 B）。

> 多分支配置文件 **实际项目里通常不带 `$`**（按纯文本模式处理）。需要字典的单分支配置才带 `$`。

### 2.3 完整资源路径示例（galaxy-libraries 真实结构）

```
src/main/resources/
├── v531/                                           ← @Sdp(version="v5.3.1")
│   ├── HDFS/                                       ← @Serve class HDFS
│   │   ├── core-site.xml$.csv                      ← 单分支 + 字典（10 列）
│   │   ├── hdfs-site.xml$.csv                      ← 单分支 + 字典
│   │   ├── dfs.hosts.csv                           ← 单分支纯文本（无 $）
│   │   ├── dfs.hosts.exclude.csv
│   │   ├── hadoop-env.sh.csv                       ← 纯文本（无 $）
│   │   └── hdfs-jaas.conf.csv
│   ├── Zookeeper/
│   └── ...
├── v532/
│   ├── HDFS/...
│   └── PrestoSQL/                                  ← 含多分支配置
│       ├── hive.properties.csv                     ← 单分支纯文本
│       ├── jvm.config.csv
│       ├── node.properties.csv
│       └── config/                                 ← 多分支配置（@Config.path 派生）
│           ├── master.properties.csv               ← master 分支（无 $）
│           └── worker.properties.csv               ← worker 分支（无 $）
└── v541/
    └── ...
```

### 2.4 CSV 两种实际格式

CSV 加载器（`cn.gsq.sdp.core.utils.CSVConverter`）**按列数自动判定**模式：

#### 模式 A：字典模式（10 列固定）

适合 `xml` / `properties` / `cfg` 等键值对格式。文件**必须正好 10 列**：

```csv
key,v_dictionary,v_default,description_en,description_ch,isMust,delimiter,labels,authority,isSysConfig
dfs.replication,null,3,"Default block replication.","HDFS 默认副本数",TRUE,null,"存储",3,FALSE
dfs.namenode.handler.count,null,100,"NN handler count","NameNode RPC 线程池大小",FALSE,null,"调优",3,FALSE
hadoop.security.authorization,FALSE,TRUE,"Is service-level auth enabled?","是否启用服务级别授权",TRUE,null,"权限",2,FALSE
```

**列映射**（CSVConverter 按索引解析，**表头名仅给人看**，列顺序固定）：

| 索引 | 表头惯例名 | 映射到 `ConfigItem` 字段 | 说明 |
|---|---|---|---|
| 0 | `key` | `key` | 配置项 key |
| 1 | `v_dictionary` | `origin` | 字典值（可选值或原始默认） |
| 2 | `v_default` | `value` | 实际默认值；`TRUE`/`FALSE` 会自动小写化 |
| 3 | `description_en` | `description` | 英文描述 |
| 4 | `description_ch` | `description_ch` | 中文描述 |
| 5 | `isMust` | `isMust` (`Boolean`) | 是否必填 |
| 6 | `delimiter` | `separator` | 值为数组时的分隔符 |
| 7 | `labels` | `label` (`List<String>`) | 标签，按逗号拆成列表 |
| 8 | `authority` | (派生 3 个 bool) | 权限等级 1-4，映射 `isHidden`/`isReadOnly`/`canDelete` |
| 9 | `isSysConfig` | `isSysConfig` (`Boolean`) | 是否系统内部配置（隐藏给用户） |

**`authority` 数字含义**：

| 值 | isHidden | isReadOnly | canDelete |
|---|---|---|---|
| 1 | true | — | false |
| 2 | false | true | false |
| 3 | false | false | false |
| 4 | false | false | true |

#### 模式 B：纯文本模式（< 10 列）

适合脚本、`*.sh`、`*.conf` 等"无结构"文件。CSV 一般 3 列：

```csv
content,authority,labels
"#!/bin/bash\nexport HADOOP_HOME=/usr/sdp/v5.3.1/hadoop\n...",3,"环境变量"
```

CSVConverter 行为：
- key 自动固定为 `"content"`；
- `nextLine[0]` → `value`（整个文件内容）；
- `nextLine[1]` → `authority` 数字（同模式 A）；
- `nextLine[2]` → `label` (按逗号拆分)；
- `isMust = true`，`isSysConfig = false` 默认。

#### CSV 文件编码注意

- 真实项目里 CSV **带 UTF-8 BOM**（OpenCSV 能正确处理）；
- 多列值含逗号时**必须用双引号包围**；
- 描述里有换行时也用双引号包围多行。

---

## 3. AbstractConfig 子类钩子

| 钩子 | 触发时机 | 典型用途 |
|---|---|---|
| `initContents(branches, blueprint)` | install 流程中，配置初始化阶段（Step 4） | 根据蓝图修改各分支的配置项与覆盖主机 |

### 3.1 `initContents` 签名

```java
protected List<BranchModel> initContents(
    Map<String, Map<String, String>> branches,      // 分支名 → 默认配置项
    Blueprint.Serve serve                            // 蓝图，含安装参数与主机信息
) {
    // 返回每个分支最终的 BranchModel（含覆盖主机和内容）
    return null;   // 返回 null 表示"用默认逻辑"
}
```

**默认逻辑**（返回 null 时）：

- `default` 分支不修改，覆盖主机由 `@Serve.all` 决定（all=true 时所有主机，否则仅服务相关主机）。

### 3.2 `BranchModel` 结构（`AbstractConfig` 的内部类）

`BranchModel` 是 `AbstractConfig` 的 `protected static` 内部类，**子类继承时直接 `new BranchModel(...)` 即可**：

```java
@Getter
@AllArgsConstructor
protected static class BranchModel {
    private final String name;                  // 分支名
    private final Set<String> hostnames;        // 该分支覆盖的主机名集合
    private final Map<String, String> content;  // 该分支的配置项内容
}
```

⚠️ **构造器参数顺序**：`(name, hostnames, content)` —— 由 Lombok `@AllArgsConstructor` 按字段声明顺序生成。

> 默认分支名常量：`cn.gsq.sdp.SdpPropertiesFinal.DEFAULT_CHAR`（通常等于 `"default"`）。

### 3.3 典型实现

#### 例 1：单分支，根据 ZK 信息修改 core-site.xml（galaxy-libraries 真实写法）

```java
@Override
protected List<BranchModel> initContents(
    Map<String, Map<String, String>> branches,
    Blueprint.Serve serve
) {
    Map<String, String> config = branches.get(SdpPropertiesFinal.DEFAULT_CHAR);
    config.put("fs.defaultFS", "hdfs://sugon-cluster");
    config.put("ha.zookeeper.quorum",
        getZkUrl(this.sdpManager.getServeByName("Zookeeper")));
    return CollUtil.newLinkedList(
        new BranchModel(
            SdpPropertiesFinal.DEFAULT_CHAR,
            CollUtil.newHashSet(CollUtil.map(
                hostManager.getHosts(), AbstractHost::getHostname, true)),
            config
        )
    );
}
```

#### 例 2：多分支按角色分发

```java
@Override
protected List<BranchModel> initContents(
    Map<String, Map<String, String>> branches,
    Blueprint.Serve serve
) {
    Map<String, String> masterCfg = branches.get("master");
    Map<String, String> workerCfg = branches.get("worker");

    masterCfg.put("coordinator", "true");
    workerCfg.put("coordinator", "false");

    Set<String> masterHosts = sdpManager.getProcessByName("Coordinator")
        .getHosts().stream().map(AbstractHost::getHostname)
        .collect(Collectors.toSet());
    Set<String> workerHosts = sdpManager.getProcessByName("Worker")
        .getHosts().stream().map(AbstractHost::getHostname)
        .collect(Collectors.toSet());

    return List.of(
        new BranchModel("master", masterHosts, masterCfg),    // 顺序：name, hostnames, content
        new BranchModel("worker", workerHosts, workerCfg)
    );
}
```

---

## 4. 配置项元数据 `ConfigItem`

`cn.gsq.sdp.ConfigItem`（Lombok `@Getter`/`@Setter`/`@Accessors(chain = true)`）实际字段：

```java
public class ConfigItem implements Serializable {
    private String key;             // 配置项主键
    private String value;           // 配置项内容（当前值）
    private String origin;          // 默认值（来自 CSV v_dictionary）
    private String separator;       // 分隔符（值是数组时不能为空）
    private Boolean isMust;         // 是否必须
    private List<String> label;     // 配置项标签（按 CSV labels 列逗号拆分）
    private String description;     // 英文描述
    private String description_ch;  // 中文描述
    private Boolean isReadOnly;     // 是否只读
    private Boolean isHidden;       // 是否隐藏
    private Boolean canDelete;      // 是否可删除
    private Boolean isInDictionary = true;  // 是否在字典里（默认 true）
    private Boolean isInUsing;      // 字典里的配置项是否在使用中
    private Boolean isSysConfig;    // 是否系统自动生成
}
```

> `isReadOnly` / `isHidden` / `canDelete` 三者由 CSV 第 8 列 `authority`（1-4）派生（见 §2.4）。
> **不存在 `permission` 字段** —— 如果旧文档提及 `permission`，请按 `isReadOnly` / `isHidden` / `canDelete` / `isSysConfig` 重新表达。

---

## 5. 分支模型详解

### 5.1 单分支（default）

最常见的情况。`@Config(branches = {})` 留空时框架自动用 `default` 分支：

```
default 分支
├── content: { dfs.replication=3, ... }
└── hosts: [host1, host2, host3, ...]
```

### 5.2 多分支

`@Config(branches = {"master", "worker"})`：

```
master 分支
├── content: { coordinator=true, http-port=8080 }
└── hosts: [coordinator-host]

worker 分支
├── content: { coordinator=false, http-port=8080 }
└── hosts: [worker-host-1, worker-host-2, worker-host-3]
```

每个分支独立通过 `ConfigDriver.conform()` 下发到自己的 hosts。

### 5.3 分支主机管理

`AbstractConfig` 提供了一系列方法（Web 端在扩缩容时调用，开发者一般不直接调）：

- `addBranchHosts(branchName, hostnames)` —— 给分支加主机
- `delBranchHosts(branchName, hostnames)` —— 删主机
- `addBranchHostsByDefault(hostnames)` —— 加到默认分支
- `rollbackBranchHostsAfterExtend(branchName, hostnames)` —— 扩容失败时回滚
- `rollbackBranchHostsAfterShorten(branchName, hostnames)` —— 缩容失败时回滚

---

## 6. 端到端示例：完整 PrestoSQL 配置定义（v5.3.2）

```java
package com.sugon.gsq.libraries.v532.prestosql.config;

@Config(
    master = PrestoSQL.class,
    type = "properties",
    path = "/presto/etc/config.properties",
    description = "Presto 核心配置",
    branches = { "master", "worker" },
    show = true,
    order = 1
)
public class ConfigProperties extends AbstractConfig {

    @Override
    protected List<BranchModel> initContents(
        Map<String, Map<String, String>> branches,
        Blueprint.Serve serve
    ) {
        Map<String, String> master = branches.get("master");
        Map<String, String> worker = branches.get("worker");

        master.put("coordinator", "true");
        master.put("node-scheduler.include-coordinator", "false");
        worker.put("coordinator", "false");

        Set<String> masterHosts = sdpManager.getProcessByName("Coordinator")
            .getHosts().stream().map(AbstractHost::getHostname)
            .collect(Collectors.toSet());
        Set<String> workerHosts = sdpManager.getProcessByName("Worker")
            .getHosts().stream().map(AbstractHost::getHostname)
            .collect(Collectors.toSet());

        return CollUtil.newLinkedList(
            new BranchModel("master", masterHosts, master),    // (name, hostnames, content)
            new BranchModel("worker", workerHosts, worker)
        );
    }
}
```

对应 CSV（注意路径含版本号 `v532`、多分支文件**不带 `$`**）：

```
src/main/resources/v532/PrestoSQL/config/
├── master.properties.csv      // master 分支数据（纯文本或 < 10 列）
└── worker.properties.csv      // worker 分支数据
```

> 子目录名 `config` 来自 `@Config.path = "/presto/etc/config.properties"` 的文件名 `config.properties` 派生（去掉扩展名）。具体的目录名取决于 SDK 内部的 CSV 加载规则，需要时参考其它已有的多分支配置（如 v541/Trino/config/）。

---

## 7. 易踩坑（针对 @Config）

1. **CSV 不是恰好 10 列** → 字典模式（带 `$` 的文件）只在列数 == 10 时生效；少一列或多一列会被当成纯文本模式，所有元数据丢失。表头第一行的列名只是给人看的，CSVConverter **按索引而非按列名**取值。
2. **resources 目录缺版本号一层**：必须是 `resources/{sdpVersion}/{serveClassName}/...`，例如 `resources/v531/HDFS/core-site.xml$.csv`。**没有版本号前缀框架找不到文件**。
3. **`@Sdp.version` 去 `.` 后必须与 resources 顶层目录名 + Java 包名末段三者一致**（`v5.3.1` ↔ `v531`）。
4. **服务子目录名必须与 `@Serve` 类名完全一致**（大小写敏感）。`HDFS` 类对应 `resources/v531/HDFS/`，不是 `Hdfs/`。
5. **多分支文件名一般不带 `$`**：单分支字典文件用 `xxx$.csv`，多分支文件用 `{branch}.csv` 即可（真实项目里 PrestoSQL/Trino 的 master/worker 都是不带 `$` 的）。
6. **`BranchModel` 构造器参数顺序**：`(name, hostnames, content)`，不是 `(name, content, hostnames)`。这是 Lombok `@AllArgsConstructor` 按字段声明顺序生成的。
7. **`branches` 列出但 CSV 文件缺失** → 加载时 NPE 或空内容。每个 `branches` 元素必须有对应 CSV 文件。
8. **`initContents` 返回的 BranchModel 中 hostnames 集合为空** → 该分支的配置文件不会下发到任何主机。
9. **`@Config.path` 与实际部署目录不一致** → 配置写到错误位置，进程读不到。
10. **多分支同一 key 给不同值但 hostnames 集合重叠** → 同一台主机的配置文件被覆盖两次，最后一次"赢"，行为不可预期。**hostnames 集合应不相交**。
11. **CSV 第 8 列 `authority` 不是 1-4 整数** → `Integer.parseInt(...)` 直接抛 `NumberFormatException`，整个 CSV 加载失败。
12. **修改 CSV 字典中的 `v_default` 但已安装的环境** → 已安装的环境不会自动应用新默认值（除非显式 `updateConfig`）。
13. **`@Config.order` 在同一服务内重复** → 配置列表顺序不稳定。
14. **Blueprint 的 `args` 字段为 null** → `initContents` 中 `serve.getArgs().getOrDefault(...)` NPE。务必 `Optional.ofNullable(serve.getArgs()).orElse(Map.of())`。
15. **以为可以用 `permission` 字段** → ConfigItem 没有这个字段。权限由 CSV 第 8 列 `authority` 数字派生为 `isReadOnly` / `isHidden` / `canDelete` 三个布尔（见 §2.4）。
