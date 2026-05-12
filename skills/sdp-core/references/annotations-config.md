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

### 2.1 路径约定

**单分支**：

```
resources/{serveClassName}/{configClassName的派生名}$.csv
```

**多分支**：

```
resources/{serveClassName}/{configClassName的派生名}/{branchName}.{suffix}$.csv
```

> `serveClassName` 是 `@Serve` 注解所在类的简单类名（如 `Hdfs`、`PrestoSQL`）。`configClassName的派生名` 一般是 `@Config.path` 的文件名（如 `core-site.xml`、`config.properties`），具体由框架内 CSV 加载器解析。

### 2.2 `$` 后缀的含义

- 文件名带 `$`（即 `xxx$.csv`）：包含**配置字典**（每行有 origin/label/description/permission 等元数据）；
- 文件名不带 `$`（即 `xxx.csv`）：仅含**键值对**（key / value 两列）。

带 `$` 用于"用户可编辑的配置"，让 UI 知道每项的默认值、描述和合法范围；不带 `$` 用于"框架内部固定数据"。

### 2.3 CSV 两种内容模式

#### 模式 A：key-value 模式（结构化配置）

适合 `xml` / `properties` / `cfg` 等键值对格式：

```csv
key,origin,label,description,permission
dfs.replication,3,副本数,HDFS 文件块副本数,EDITABLE
dfs.namenode.handler.count,100,RPC 处理线程,NameNode RPC 线程池大小,EDITABLE
dfs.permissions.enabled,true,启用权限检查,,READONLY
```

字段说明：

- `key`：配置项 key；
- `origin`：默认值；
- `label`：UI 显示名；
- `description`：描述；
- `permission`：权限标识（`EDITABLE` / `READONLY` 等，具体由接入方定义）。

#### 模式 B：纯文本模式（整文件作为一项）

适合脚本、日志配置等"无结构"文件：

```csv
key,origin,...
__content__,"<整个文件内容>",...
```

框架把整个文件当作单一 ConfigItem 处理。具体 key 命名约定看接入方的 ConfigDriver 实现。

### 2.4 资源路径完整示例

假设有 HDFS 服务带 `core-site.xml` 和 `hdfs-site.xml` 单分支配置，以及 PrestoSQL 服务带 `config.properties` 多分支（master/worker）：

```
src/main/resources/
├── Hdfs/                                           ← 对应 @Serve class Hdfs
│   ├── core-site.xml$.csv                          ← 单分支 + 字典
│   └── hdfs-site.xml$.csv
└── PrestoSQL/                                      ← 对应 @Serve class PrestoSQL
    └── config.properties/                          ← 多分支用目录
        ├── master.properties$.csv                  ← master 分支
        └── worker.properties$.csv                  ← worker 分支
```

> **⚠️** 实际框架的目录拼接规则细节可参考 `AbstractConfig` 中的 CSV 加载逻辑（`loadConfigItems` 调用路径），上述是常见组织方式。

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

### 3.2 `BranchModel` 结构

```java
class BranchModel {
    String branchName;                  // 分支名
    Map<String, String> content;        // 该分支的配置项
    Set<String> hosts;                  // 该分支覆盖的主机
}
```

### 3.3 典型实现

#### 例 1：根据蓝图主机修改 dfs.replication

```java
@Override
protected List<BranchModel> initContents(
    Map<String, Map<String, String>> branches,
    Blueprint.Serve serve
) {
    Map<String, String> def = branches.get("default");
    int dataNodeCount = serve.getProcesses().stream()
        .filter(p -> p.getName().equals("DataNode"))
        .mapToInt(p -> p.getHostnames().size()).sum();
    // 副本数不超过 DataNode 数
    def.put("dfs.replication", String.valueOf(Math.min(3, dataNodeCount)));
    return List.of(
        new BranchModel("default", def, /* hosts */ getAllHostnames(serve))
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

    Set<String> masterHosts = getProcessHosts(serve, "PrestoCoordinator");
    Set<String> workerHosts = getProcessHosts(serve, "PrestoWorker");

    return List.of(
        new BranchModel("master", masterCfg, masterHosts),
        new BranchModel("worker", workerCfg, workerHosts)
    );
}
```

---

## 4. 配置项元数据 `ConfigItem`

```java
class ConfigItem {
    String key;             // 配置项 key
    String value;           // 当前值
    String origin;          // 默认值（来自 CSV）
    String label;           // UI 显示名
    String description;     // 描述
    String permission;      // 权限（EDITABLE / READONLY 等）
}
```

`ConfigItem` 既用于内存中的当前配置，也用于 UI 展示和编辑。

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

## 6. 端到端示例：完整 PrestoSQL 配置定义

```java
package io.github.sdp.v531.prestosql.config;

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

        // 端口可由用户在 Blueprint 中覆盖
        String httpPort = (String) serve.getArgs().getOrDefault("http-port", "8080");
        master.put("http-server.http.port", httpPort);
        worker.put("http-server.http.port", httpPort);

        return List.of(
            new BranchModel("master", master, getProcessHosts(serve, "Coordinator")),
            new BranchModel("worker", worker, getProcessHosts(serve, "Worker"))
        );
    }
}
```

对应 CSV：

```
src/main/resources/PrestoSQL/config.properties/
├── master.properties$.csv      // 默认 master 分支配置 + 字典
└── worker.properties$.csv      // 默认 worker 分支配置 + 字典
```

---

## 7. 易踩坑（针对 @Config）

1. **`branches` 列出但 CSV 文件缺失** → 加载时 NPE 或空内容。每个 `branches` 元素必须有对应 CSV 文件。
2. **CSV 字段顺序错乱**：必须严格 `key, origin, label, description, permission`（按接入方约定）。乱序时所有项的元数据错位。
3. **CSV 包含 BOM 或换行符不一致** → 解析器可能跳过首行或合并行。统一用 UTF-8 无 BOM + Unix 换行。
4. **`initContents` 返回的 BranchModel 中 hosts 集合为空** → 该分支的配置文件不会下发到任何主机。
5. **`@Config.path` 与实际部署目录不一致** → 配置写到错误位置，进程读不到。
6. **多分支配置同一 key 给不同值，但 hosts 集合重叠** → 同一台主机上的配置文件被覆盖两次，最后一次"赢"，行为不可预期。**hosts 集合应不相交**。
7. **`show = false` 但配置项有 `permission = EDITABLE`** → 矛盾。`show=false` 时 UI 不暴露，权限标志没意义。
8. **修改 CSV 字典中的 `origin`（默认值）但不发 SDP 版本** → 已安装的环境不会自动应用新默认值（除非显式 `updateConfig`）。
9. **`@Config.order` 在同一服务内重复** → 配置列表顺序不稳定。
10. **Blueprint 的 `args` 字段为 null** → `initContents` 中 `serve.getArgs().getOrDefault(...)` NPE。务必 `Optional.ofNullable(serve.getArgs()).orElse(Map.of())`。
