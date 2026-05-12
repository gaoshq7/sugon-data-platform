# Reference: @Process / AbstractProcess 完整规范

> 本文件是 `sdp-core` skill 的扩展参考。仅在以下场景读取：
> - 写或修改一个 `@Process` 进程类；
> - 选择 ProcessHandler 模式；
> - 实现进程扩缩容（extend/shorten）；
> - 配置进程在不同部署模式下的分组（@Group）；
> - 处理进程的可用性判定、日志路径、端口探测。

---

## 0. 对照源码

- `sdp-core/cn/gsq/sdp/core/annotation/Process.java`
- `sdp-core/cn/gsq/sdp/core/annotation/Group.java`
- `sdp-core/cn/gsq/sdp/core/AbstractProcess.java`
- `sdp-core/cn/gsq/sdp/core/ProcessHandler.java`

---

## 1. `@Process` 全字段

```java
public @interface Process {
    Class<? extends AbstractServe> master();                       // ✅ 所属服务
    Class<? extends AbstractProcess>[] depends() default {};       // 前置进程
    Class<? extends AbstractProcess>[] companions() default {};    // 伴生进程
    Class<? extends AbstractProcess>[] excludes() default {};      // 互斥进程
    ProcessHandler handler();                                      // ✅ 进程模式（5 选 1）
    Group[] groups() default {};                                   // 各模式下的主机分组
    String mark();                                                 // ✅ 进程唯一标识（ps grep 用）
    String home();                                                 // ✅ 启停命令工作目录
    String start();                                                // ✅ 启动命令
    String stop();                                                 // ✅ 停止命令
    boolean dynamic() default false;                               // 是否可扩缩容
    String description() default "";
    int order();                                                   // ✅ 排序号
    int min() default 1;                                           // 最小部署节点数
    int max() default -1;                                          // 最大部署节点数（-1 无上限）
}
```

### 1.1 `master`

必须指向同一版本包内的 `@Serve` 类。

### 1.2 `depends` / `companions` / `excludes`

- **`depends`**：前置进程，本进程启动前必须先启动。例如 `NameNode` 启动前要先启动 `JournalNode`。
- **`companions`**：伴生进程，必须与本进程在同一台主机上。例如 `NameNode` 的 `ZKFailoverController`。
- **`excludes`**：互斥进程，**不能与本进程同主机**。例如 `NameNode` 与 `DataNode` 不能同主机。

```java
@Process(
    depends = JournalNode.class,
    companions = ZkfcProcess.class,
    excludes = DataNode.class,
    ...
)
```

### 1.3 `handler`

ProcessHandler 5 种之一，详见 §2。决定**可用性判定逻辑**。

### 1.4 `groups`（多模式分组）

声明本进程在不同 `@Mode` 下应该部署到哪个 `HostGroup`：

```java
@Process(
    groups = {
        @Group(mode = MasterSlave.class, name = "MASTER"),         // "主从混合" 模式 → MASTER 分组
        @Group(mode = ComputeStorage.class, name = "COMPUTE")      // "存算分离" 模式 → COMPUTE 分组
    },
    ...
)
```

> `@Group.mode` 是 `@Mode` 注解所在的枚举**类**，`@Group.name` 是该枚举里某个值的名字（即 HostGroup 实例）。

留空 (`groups = {}`) 时不限制分组，依赖 Blueprint 显式指定主机。

### 1.5 `mark`（必填）

进程在 `ps -ef | grep <mark>` 中的唯一标识字符串。框架在端口探针失败时用此命令兜底判定进程存活：

```bash
ps -ef | grep <mark> | grep -v grep | awk '{print $2}'
```

要确保 `mark` **足够独特**——不要用太通用的字符串（如 `"java"`）。一般用 Main 类名（如 `"NameNode"`、`"DorisFE"`）。

### 1.6 `home`

启停命令的工作目录，相对于 `sdp.home/{sdpVersion}/`。例如 `home = "/hadoop"` 表示工作目录为 `/usr/sdp/v5.3.1/hadoop/`。

### 1.7 `start` / `stop`

shell 命令字符串。在 `home` 目录下用 `RpcDriver.execute` 执行：

```java
@Process(
    home = "/hadoop",
    start = "./bin/hdfs --daemon start namenode",
    stop  = "./bin/hdfs --daemon stop namenode",
    ...
)
```

> 使用 `--daemon` 让进程后台运行，否则 RPC 调用会一直挂着等进程退出。

### 1.8 `dynamic`

`true` 时允许通过 `extend(hosts)` / `shorten(hosts)` 扩缩容。⚠️ **必须覆盖 `extend(AbstractHost)` 和 `shorten(AbstractHost)` 方法**，否则扩缩容操作 no-op。

### 1.9 `min` / `max`

进程要求的部署节点数范围。Web 端在调用 `extend / shorten` 时校验：

- `min = 1, max = -1`：至少 1 个，上不封顶；
- `min = 2, max = 2`：固定 2 个副本（HDFS NameNode HA）；
- `min = 3, max = -1`：至少 3 个（ZooKeeper 多数派）。

---

## 2. ProcessHandler 5 种模式详解

| 枚举 | 中文 | 可用性判定算法 | 适用场景 |
|---|---|---|---|
| `MASTER` | 主进程 | **所有承载主机都健康**才算可用 | NameNode（HA 主备都必须健康）|
| `SLAVE` | 从进程 | **宕机数 < 总数 / 2** 才算可用（多数派） | DataNode |
| `WATCH` | 守护进程 | 同 MASTER | ZkFailoverController |
| `ALONE` | 独立进程 | 同 MASTER | HiveServer2 单实例 |
| `ELECTION` | 选举进程 | 同 SLAVE（多数派可用） | ZooKeeper Server |

### 2.1 源码逻辑

`ProcessHandler` 枚举每个值都覆盖了 `isAvailable(AbstractProcess<T> process)`：

```java
MASTER: getDownHosts(process).size() == 0
SLAVE:  getDownHosts(process).size() < hosts.size() / 2
WATCH:  MASTER.isAvailable(process)         // 委托给 MASTER
ALONE:  MASTER.isAvailable(process)
ELECTION: SLAVE.isAvailable(process)        // 委托给 SLAVE
```

`getDownHosts` 通过 `host.isProcessActive(process)` 判定每台主机上进程是否存活：

1. 优先用端口探针（`isProcessActive` 内部走 `NetUtil.isOpen(host, port, 500ms)`）；
2. `getPort() == -1` 时退化为 ps grep `mark`。

### 2.2 选型指南

| 进程特征 | 推荐 handler |
|---|---|
| 主备 / 全副本必须健康 | `MASTER` |
| 多副本，挂少数可继续工作 | `SLAVE` |
| 与某主进程一一伴生 | `WATCH` |
| 单实例 | `ALONE` |
| 多副本选举出主 | `ELECTION` |

---

## 3. AbstractProcess 子类钩子

| 钩子 | 触发时机 | 典型用途 | 必须覆盖？ |
|---|---|---|---|
| `initProcess()` | Blueprint 中本进程所覆盖主机已添加到 `this.hosts` 之后 | **🔥 进程级安装动作**——真实项目里在这里执行**格式化、依次启动主备、等待可用**等关键步骤（不仅仅是字段初始化）。NameNode 的 initProcess 会做 HA 格式化 + 主备启动 + 探活等。 | 可选；扩缩容进程一般要写 |
| `getPort()` | 端口探针时 | 返回进程对外端口；返回 `-1` 表示走 ps grep mark | 可选（默认 -1） |
| `reset()` | 卸载时所有主机上进程已停止后 | 清理进程级残留资源 | 可选 |
| `extend(AbstractHost)` | 扩容时对每台新主机调一次 | 单机扩容动作（如复制配置、加入集群） | ⚠️ `dynamic=true` 时**必须**覆盖 |
| `shorten(AbstractHost)` | 缩容时对每台目标主机调一次 | 单机缩容动作（如从集群剔除、清理数据） | ⚠️ `dynamic=true` 时**必须**覆盖 |
| `getLogFilePath()` | UI 拉日志时 | 返回该进程日志目录 | 可选（默认 `home + "/logs"`） |
| `getLogFileName(String hostname)` | UI 拉日志时 | 返回日志文件名 | 可选 |

### 3.1 完整示例（galaxy-libraries v5.3.1 NameNode 真实代码）

```java
package com.sugon.gsq.libraries.v531.hdfs.process;

@Slf4j
@Process(
    master = HDFS.class,
    handler = ProcessHandler.MASTER,
    groups = {
        @Group(mode = SCIsolateMode.class, name = "MASTER")
    },
    mark = "NameNode",
    home = "/hadoop",
    start = "./bin/hdfs --daemon start namenode",
    stop  = "./bin/hdfs --daemon stop namenode",
    description = "HDFS主进程（高可用）",
    depends = JournalNode.class,        // 必须先启动 JournalNode
    companions = Zkfc.class,            // ZKFailoverController 必须与 NameNode 同主机
    order = 2,
    min = 2, max = 2
)
public class NameNode extends AbstractProcess<SdpHost531Impl> {

    @Override
    protected void initProcess() {
        // initProcess 不只是"做点初始化"——这里直接执行主备 NameNode 的格式化与启动
        String dirs = getNameNodeDirs();
        SdpHost531Impl nn1 = this.getHosts().get(0);
        SdpHost531Impl nn2 = this.getHosts().get(1);

        nn1.installActiveNameNode(dirs);   // 格式化主 NameNode
        nn1.startProcess(this);             // 启动主
        explore(nn1);                       // 等待主可用

        nn2.installStandbyNameNode(dirs);  // 引导备 NameNode
        nn2.startProcess(this);
        explore(nn2);
    }

    @Override
    public Integer getPort() {
        return 9871;     // HTTPS UI 端口，用于端口探针
    }

    @Override
    protected void reset() {
        // 卸载时清理
        String dirs = getNameNodeDirs();
        for (SdpHost531Impl host : this.getHosts()) {
            host.uninstallNameNode(dirs);
        }
    }
}
```

> 注意：`initProcess` 里直接调 `host.startProcess(this)` 完成主备启动，**这与 skill 早期描述的"只是初始化字段"不同**。`AbstractProcess.install()` 在 `initProcess` 之后还会做 DAG 启停，但对于带顺序约束的进程（如 HA NameNode 主备依次格式化），在 `initProcess` 里完成更直接。

### 3.2 扩缩容进程示例

```java
@Process(
    master = HDFS.class,
    handler = ProcessHandler.SLAVE,
    excludes = NameNode.class,            // DataNode 不能与 NameNode 同主机
    groups = { @Group(mode = SCIsolateMode.class, name = "DATA") },
    mark = "DataNode",
    home = "/hadoop",
    start = "./bin/hdfs --daemon start datanode",
    stop  = "./bin/hdfs --daemon stop datanode",
    dynamic = true,      // ⚠️ 可扩缩容
    order = 4,
    min = 3, max = -1
)
public class DataNode extends AbstractProcess<SdpHost531Impl> {

    @Override
    public Integer getPort() { return 9864; }

    @Override
    protected void extend(AbstractHost host) {
        SdpHost531Impl impl = (SdpHost531Impl) host;
        impl.installDataNode();             // 调 Host 业务方法
    }

    @Override
    protected void shorten(AbstractHost host) {
        SdpHost531Impl impl = (SdpHost531Impl) host;
        impl.uninstallDataNode();
    }
}
```

### 3.3 `companions` / `excludes` 真实场景

| 关系 | 真实例子 |
|---|---|
| `depends` | `NameNode` 依赖 `JournalNode` —— JN 必须先启动给 HA 提供编辑日志 |
| `companions` | `Zkfc`（ZKFailoverController）与 `NameNode` 同主机 —— Zkfc 在 NN 同机监控选主 |
| `excludes` | `DataNode` 与 `NameNode` 互斥 —— 通常生产环境 NN 节点不跑 DN 以免资源争抢 |

---

## 4. 启停与可用性

### 4.1 启停超时

`AbstractHost.startProcess()` 内部使用 `CommonUtil.waitForSignal(isProcessActive, 180000, 4000)`：

- 最长等待 **180 秒**；
- 每 **4 秒** 检测一次。

超时则抛 `RuntimeException`，框架自动回滚（清理中间态）。

如果你的进程启动慢（如 HBase Master 初始化 ZK 元数据需 1 分钟以上），有两种应对：

1. 启动脚本中 fork 出守护进程后立即返回（用 `--daemon` 或 `nohup`），让进程后台慢慢初始化；
2. 覆盖 `AbstractProcess.isAvailable()` 实现自定义"启动完成"判定，例如检测特定 znode 出现。

### 4.2 端口探针 vs ps grep

| 方式 | 触发条件 | 速度 | 准确性 |
|---|---|---|---|
| 端口探针 | `getPort() != -1` | 快（500ms 超时） | 高 |
| ps grep mark | `getPort() == -1` | 慢（要 RPC 远程执行） | 中等（mark 字符串要够独特） |

**强烈建议覆盖 `getPort()`**：进程有对外监听端口的就返回，没有的（如纯客户端工具）保持默认 -1。

---

## 5. 进程依赖 DAG

`@Process.depends` 让进程间形成 DAG：

- `getParents()` / `getChildren()` 返回上下游；
- ServeHandler 启动服务时按 DAG 拓扑顺序启动，停止时反序。

服务级 DAG（`@Serve.depends`）与进程级 DAG（`@Process.depends`）独立运作——服务间先启动有 depends 关系的，每个服务内部再按进程 DAG 启动。

---

## 6. 易踩坑（针对 @Process）

1. **`dynamic = true` 但不覆盖 `extend / shorten`** → 扩缩容 no-op，UI 看似成功但实际未生效。
2. **`mark` 太通用**（如 `"java"`） → ps grep 命中其它进程，误判进程存活。
3. **`start` / `stop` 命令不返回**（前台运行） → RPC 调用阻塞超时。**必须 fork 守护进程**。
4. **`start` 中包含相对路径但 `home` 配置错** → 找不到二进制。务必确保 `home/bin/xxx.sh` 实际存在。
5. **`getPort()` 返回的端口与进程实际监听端口不一致** → 端口探针永远 false，框架认为进程没启动。
6. **`groups` 配置遗漏某个 mode** → 切换到该 mode 时本进程不会有任何主机部署目标（除非 Blueprint 显式指定）。
7. **`depends` 形成环** → DAG 排序失败，服务无法启动。
8. **`companions` 与 `excludes` 同时声明同一进程** → 配置矛盾，框架不会自动检测，行为不确定。
9. **`min` 大于实际可用主机数** → 安装时无法满足约束，install 失败。
10. **`order` 在同一服务内重复** → 进程列表排序不稳定，DAG 启动顺序可能与预期不符。
