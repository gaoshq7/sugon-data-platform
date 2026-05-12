# Reference: @Serve / AbstractServe 完整规范

> 本文件是 `sdp-core` skill 的扩展参考。仅在以下场景读取：
> - 写或修改一个 `@Serve` 服务类；
> - 选择 ServeHandler 模式；
> - 覆盖 AbstractServe 的子类钩子（initServe / afterInstall / callbackServe 等）；
> - 添加 `@Function` / `@Available` / `@Status` 方法；
> - 配置服务依赖（depends）和外部配置追加（appends）。

---

## 0. 对照源码

- `sdp-core/cn/gsq/sdp/core/annotation/Serve.java`
- `sdp-core/cn/gsq/sdp/core/annotation/Function.java`
- `sdp-core/cn/gsq/sdp/core/annotation/Available.java`
- `sdp-core/cn/gsq/sdp/core/annotation/Status.java`
- `sdp-core/cn/gsq/sdp/core/AbstractServe.java`
- `sdp-core/cn/gsq/sdp/core/ServeHandler.java`
- `sdp-core/cn/gsq/sdp/core/ClassifyHandler.java`

---

## 1. `@Serve` 全字段

```java
public @interface Serve {
    String version();                                              // ✅ 必填，服务自身版本号
    ServeHandler handler();                                        // ✅ 必填，部署模式（5 选 1）
    ClassifyHandler type() default ClassifyHandler.OTHER;          // 服务分类（用于 UI 展示分组）
    Class<? extends AbstractServe>[] depends() default {};         // 服务依赖（DAG）
    String[] appends() default {};                                 // 外部附加配置文件
    String[] labels() default {};                                  // 服务标签
    String description();                                          // ✅ 必填
    boolean all() default false;                                   // 是否所有主机都需要下载安装包
    String pkg() default "";                                       // 安装包目录名
    int order();                                                   // ✅ 必填，全局唯一排序号
}
```

### 1.1 `version`

服务自身的版本号（不是 SDP 版本号）。例如 `version = "3.3.3"` 表示 Spark 3.3.3。

### 1.2 `handler`（必填）

5 种 ServeHandler 之一，决定服务的部署/启停/可用性聚合方式。详见 §2。

### 1.3 `type`（可选）

`ClassifyHandler` 枚举值，仅作 UI 展示分类，不影响行为。完整枚举：

| 枚举 | 中文名 | 典型例子 |
|---|---|---|
| `BIGDATA` | 大数据组件 | HDFS、Spark、Hive、Doris |
| `BASICS` | 基础组件 | ZooKeeper、MySQL |
| `TOOL` | 工具服务 | Ranger、Kerby |
| `CUSTOM` | 自定义服务 | 业务方扩展 |
| `OTHER` | 其它类型 | 默认值 |

### 1.4 `depends`（重要）

声明上游服务依赖。框架按 DAG 拓扑排序：

- 安装时：先装 depends 里的服务，再装本服务；
- 启动时：parents 先启动，children 后启动；
- 停止时：反序，children 先停，parents 后停。

```java
@Serve(
    depends = { Zookeeper.class, HDFS.class },   // Hive 依赖 ZK 和 HDFS
    ...
)
public class Hive extends AbstractServe { }
```

### 1.5 `appends`（外部配置文件追加）

格式：`"服务名:配置文件名:分支名:目标路径"`，将其它服务的某配置文件拷贝到本服务的目标路径下。常用于让计算服务能访问 HDFS：

```java
@Serve(
    appends = {
        "HDFS:core-site.xml:default:/spark/conf/",
        "HDFS:hdfs-site.xml:default:/spark/conf/",
        "YARN:yarn-site.xml:default:/spark/conf/",
        "Hive:hive-site.xml:default:/spark/conf/"
    },
    ...
)
public class Spark extends AbstractServe { }
```

### 1.6 `labels`

字符串数组，UI 标签云使用。例如 `{"计算框架", "批处理"}`。

### 1.7 `all`（下载策略）

- `true`：**所有主机**都下载本服务的安装包（即使该主机不部署任何此服务的进程）；
- `false`（默认）：仅部署有此服务进程的主机下载安装包。

什么时候用 `all = true`：客户端工具类服务（如 `hadoop-client` 命令行），希望所有主机都能执行。

### 1.8 `pkg`

`sdp.home/{sdpVersion}/{pkg}` 是安装包目录。如 `pkg = "spark"` 表示安装包在 `/usr/sdp/v5.3.1/spark/` 下。`pkg = ""`（默认）则用服务名小写。

### 1.9 `order`（必填）

服务在 `sdpManager.getServes()` 中的排序号。**全局唯一**（同一版本包内不同服务不能重复）。一般按"启动依赖链 + 业务重要性"排，例如 ZK = 1，HDFS = 2，YARN = 3，Hive = 10。

---

## 2. ServeHandler 5 种模式详解

| 枚举 | 中文 | 适用场景 | 启停行为 |
|---|---|---|---|
| `MASTER_SLAVE_MODE` | 主从模式 | HDFS（NameNode 主备 + DataNode）、YARN（ResourceManager 主备 + NodeManager） | 按 DAG 顺序启停所有进程 |
| `MASTER_ELECTION_MODE` | 选举模式 | ZooKeeper、etcd（多副本互选主） | 同上，但可用性判定走 ELECTION |
| `STAND_ALONE_MODE` | 单机模式 | 单进程小工具（如 MySQL 单节点） | 启停单进程 |
| `FRAGMENT_ALONE_MODE` | 独立分片模式 | HBase（Master + RegionServer + ThriftServer，可能在不同主机协同） | 按 DAG 顺序启停若干互相依赖的进程 |
| `MULTI_ROLE_MODE` | 角色模式 | Presto（coordinator/worker）、Elasticsearch（master/data/client） | 同一进程类型，不同角色（用 `@Process.groups` 区分） |

ServeHandler 内部统一逻辑（见源码 `ServeHandler.java`）：

```java
void start(AbstractServe serve) {
    // 按 DAG 顺序启动所有进程，每个进程启动后 waitForSignal(isAvailable, 180s)
}

void stop(AbstractServe serve) {
    // 反向 DAG 顺序停止
}

boolean isAvailable(AbstractServe serve) {
    // 所有进程都 isAvailable() 才算可用
}

boolean isInstalled(AbstractServe serve) {
    // 任一进程已安装即视为服务已安装（含锁定态）
}
```

> 5 种模式当前对启停的实际行为相同，区别主要在**语义**上（让 UI 与监控理解服务架构特征）。未来可能各模式扩展不同的安装策略。

---

## 3. AbstractServe 子类钩子

| 钩子 | 触发时机 | 典型用途 | ⚠️ 注意 |
|---|---|---|---|
| `initServe(Blueprint.Serve blueprint)` | install 流程开始（Step 1） | 解析蓝图的自定义参数、做安装前置检查 | 此时进程和配置都还没初始化 |
| `afterInstall(Blueprint.Serve blueprint)` | 所有进程安装启动完成、`ServeDriver.receiptInstallServe` 调用之前 | 触发服务级初始化脚本（如建库、初始化元数据表） | **此时服务不一定可用**——回调点早于可用性轮询 |
| `callbackServe()` | 全部完成（进程可用 + 落库完成） | 通知其它服务、刷新缓存 | 服务此时已正式可用 |
| `afterRecover(AbstractServe serve)` | 卸载时进程/配置全部清理完成后、同步数据库前 | 清理服务级残留（如外部依赖资源） | |
| `extendProperties(Map<String, String> properties)` | `getProperties()` 被调用时 | **🔥 服务详情页核心数据源**——往 properties 塞动态计算的状态信息（主备进程哪个 active、数据节点数、关键配置项摘要、Ranger 是否开启等） | 真实项目里通常是 30-100 行代码，是用户感知服务状态的主要窗口 |
| `getWebUIs()` | UI 渲染服务卡片 | 返回服务的 Web 入口（如 NameNode UI 链接） | 返回 `List<WebUI>` |
| `isServeAvailable()` | UI 主动健康检查 | 业务级可用性（HTTP 探针、SQL 探针等），默认走进程状态聚合 | 返回 `RpcRespond<String>` |

### 3.1 钩子覆盖示例

```java
@Override
protected void initServe(Blueprint.Serve blueprint) {
    String customParam = (String) blueprint.getArgs().get("namespace");
    if (StrUtil.isBlank(customParam)) {
        throw new IllegalArgumentException("缺少 namespace 参数");
    }
    // 把参数挂到 this，供后续钩子使用
    this.namespace = customParam;
}

@Override
protected void afterInstall(Blueprint.Serve blueprint) {
    // 在主进程主机上执行一次初始化脚本
    AbstractHost master = getProcessHosts("HiveMetaStore").get(0);
    master.actuator("init-metastore-schema", Map.of("dbname", namespace));
    // ⚠️ 此时服务不一定可用，不要做依赖"已可用"的操作
}

@Override
protected void callbackServe() {
    // 通过 BroadcastDriver 广播服务就绪
    GalaxySpringUtil.getBean(BroadcastDriver.class)
        .appNotice("*", AppEvent.SERVE_READY, getName(), "Hive ready");
}

@Override
public List<WebUI> getWebUIs() {
    return List.of(
        new WebUI("HiveServer2 UI", "http://" + masterHost + ":10002")
    );
}

@Override
public RpcRespond<String> isServeAvailable() {
    // 自定义可用性检测（默认走进程状态聚合）
    return /* ping HiveServer2 */ ;
}
```

### 3.2 服务安装阶段与钩子触发顺序

`install(blueprint)` 内部主要阶段（不要把"步数"当固定数字记，重点是**钩子的相对顺序**）：

```
install(blueprint):
  ┌─ 状态置 INSTALLING + 加锁
  │  ───►【钩子】initServe(blueprint)              ← 此时进程/配置都未初始化
  │
  ├─ 在所有目标主机 downloadPackage
  │  ───►【驱动】ServeDriver.receiptInstallServe(blueprint)
  │
  ├─ 各 AbstractConfig 初始化（initContents 钩子触发）
  ├─ 各 AbstractProcess 按 DAG 顺序落地 + 启动
  │  ───►【钩子】afterInstall(blueprint)          ← 进程刚启动，可用性未确认
  │
  ├─ 轮询所有进程 isAvailable()（超时 → recover）
  │  ───►【钩子】callbackServe()                  ← 服务真正可用
  └─ 解锁 + 状态置 RUNNING
```

> 阶段数与精确顺序依框架版本而异，**写代码时请回查 `AbstractServe.install` 源码**。重点是钩子语义：
> - `initServe` 早期入口，**还没装东西**；
> - `afterInstall` 进程启动后，**未必可用**；
> - `callbackServe` **服务完全可用**后；
> - `afterRecover` 卸载完成、入库前。

异常路径：任意步骤抛错 → 状态置 `UNINSTALLING` → 执行 `recover()` → 反向卸载 → 触发 `afterRecover()`。

### 3.3 `AbstractServe` 常用工具方法（子类高频调用）

| 方法 | 用途 |
|---|---|
| `getProcessByName(String name)` | 取本服务下的进程（泛型为 `AbstractProcess<AbstractHost>`） |
| `getProcessByNameForImpl(String name)` | 取**带具体 Host 类型**的进程（如 `AbstractProcess<SdpHost531Impl>`），便于调用版本专用的 Host 业务方法 |
| `getConfigByName(String cname)` | 取本服务的配置文件实例 |
| `getConfigDefaultContentToMap(String cname)` | 取默认分支配置的 `Map<String, String>`（**最常用**，extendProperties / activeXxx 等里随处可见） |
| `getConfigBranchContentToMap(String cname, String bname)` | 取指定分支的配置 Map |
| `updateConfigDefault(String cname, Map<String, String> items)` | 修改默认分支配置并同步到主机 |

**典型组合用法**（galaxy-libraries v5.3.1 HDFS 真实代码）：

```java
public class HDFS extends AbstractServe {
    @Autowired SdpRangerIFace extraIFace;       // 注入上层 Web 系统提供的业务接口

    @Function(id = "ACTIVEAUTHORITY", name = "开启权限管理")
    public void activeAuthority() {
        AbstractProcess<AbstractHost> namenode = this.getProcessByName("NameNode");
        namenode.stop();

        // 拿到带具体类型的进程（其 hosts 是 List<SdpHost531Impl>）
        AbstractProcess<SdpHost531Impl> impl = this.getProcessByNameForImpl("NameNode");
        for (SdpHost531Impl hostImpl : impl.getHosts()) {
            if (hostImpl.hdfsOpenRanger(rangerHost)) {           // 调 Host 类的业务方法
                Map<String, String> map = new HashMap<>();
                map.put(rangerKey, rangerValue);
                this.updateConfigDefault("hdfs-site.xml", map);   // 改配置
            }
        }
        namenode.start();
        extraIFace.createPlugInRanger(...);                       // 调外部业务接口
    }
}
```

### 3.4 注入上层 Web 系统的业务接口

`@Serve` 类是 Spring Bean，可以 `@Autowired` 注入由 Web 系统（依赖 `sdp-spring-boot-starter`）定义的业务接口：

```java
@Serve(...)
public class HDFS extends AbstractServe {
    @Autowired SdpRangerIFace extraIFace;   // 由上层 Web 系统提供的 Ranger 客户端
}
```

这是 SDP 包反向依赖业务接口的常见模式——典型用途：Ranger / Kerberos / 监控告警 等需要业务层配合的功能。

---

## 4. `@Function` / `@Available` / `@Status` 辅助注解

### 4.1 `@Function` —— 自定义功能函数

```java
@Function(id = "ACTIVATE_AUTH", name = "开启权限管理", isReveal = true)
public void activateAuth() {
    // 业务逻辑：调外部接口、修改配置、重启服务等
}
```

- **`id`**：在**当前服务/进程**下必须唯一。Web 端按 `serveName + functionId` 触发。
- **`name`**：UI 显示名。
- **`isReveal`**：是否在 UI 暴露按钮（默认 true）。某些内部用 `@Function` 但不想暴露的方法可设 false（如服务自身的 `install` / `start` / `stop` 都用了 `isReveal = false`）。

### 4.2 `@Available` —— 函数可用性判定

```java
@Function(id = "ACTIVATE_AUTH", name = "开启权限管理")
public void activateAuth() { /* ... */ }

@Available(fid = "ACTIVATE_AUTH")
public boolean canActivateAuth() {
    return isAvailable() && !isAuthActive();  // 服务可用且权限未开启时才可触发
}
```

UI 按钮的"可点击"状态由对应 `@Available` 方法决定。无 `@Available` 时按钮永远可用。

### 4.3 `@Status` —— 中间态标记

框架内部使用，在 install/start/stop 等带 `@Function` 的方法上加 `@Status(AppStatus.INSTALLING)` 标记此方法执行期间服务进入 INSTALLING 中间态。**子类一般无需使用**（除非要做自定义生命周期方法）。

---

## 5. 服务依赖 DAG

`@Serve.depends` 在 `SdpManager.getParents(serveName)` 和 `getChildren(serveName)` 中反映：

```java
@Serve(depends = { Zookeeper.class, HDFS.class })
public class Hive extends AbstractServe { }
```

- `getParents("Hive")` → `[Zookeeper, HDFS]`
- `getChildren("Zookeeper")` → `[..., Hive, ...]`（所有以 ZK 为 depends 的服务）

DAG 在框架初始化时通过 `DagUtil.getDagResult(serves)` 排序，启停时按拓扑顺序遍历。

**循环依赖会让 DAG 排序失败**——框架启动时报错而非延后到运行期。

---

## 6. 端到端示例：完整 Hive 服务定义（galaxy-libraries 风格）

```java
package com.sugon.gsq.libraries.v531.hive;

@Serve(
    version = "3.1.3",
    handler = ServeHandler.FRAGMENT_ALONE_MODE,    // Metastore + Server2 + Client 协同
    type = ClassifyHandler.BIGDATA,
    depends = { Zookeeper.class, HDFS.class, YARN.class },
    appends = {
        "HDFS:core-site.xml:default:/hive/conf/",
        "HDFS:hdfs-site.xml:default:/hive/conf/",
        "YARN:yarn-site.xml:default:/hive/conf/"
    },
    labels = { "数仓", "SQL" },
    description = "Apache Hive 数据仓库",
    all = false,
    pkg = "hive",
    order = 10
)
@Slf4j
public class Hive extends AbstractServe {

    @Autowired SdpRangerIFace extraIFace;       // 上层 Web 系统提供的业务接口

    @Override
    protected void initServe(Blueprint.Serve blueprint) {
        // 在所有主机创建 hive 用户
        for (AbstractHost host : sdpManager.getHostManager().getHosts()) {
            SdpHost531Impl impl = sdpManager.getExpectHostByName(host.getName());
            impl.createLDAPUser("hive", 9002);
        }
    }

    @Override
    protected void afterInstall(Blueprint.Serve blueprint) {
        // 初始化 Metastore schema —— 此时服务未必可用，但进程已启动可执行脚本
        SdpHost531Impl master = getProcessByNameForImpl("HiveMetaStore").getHosts().get(0);
        master.actuator("schematool", Map.of("type", "init", "dbType", "mysql"));
    }

    @Override
    protected void extendProperties(Map<String, String> properties) {
        Map<String, String> hiveSite = getConfigDefaultContentToMap("hive-site.xml");
        properties.put("Metastore URI", hiveSite.get("hive.metastore.uris"));
        properties.put("Server2 端口", hiveSite.get("hive.server2.thrift.port"));
        properties.put("仓库目录", hiveSite.get("hive.metastore.warehouse.dir"));
        properties.put("HiveServer2 节点数",
            String.valueOf(getProcessByName("HiveServer2").getHosts().size()));
    }

    @Override
    public List<WebUI> getWebUIs() {
        List<WebUI> uis = new ArrayList<>();
        for (AbstractHost h : getProcessByName("HiveServer2").getHosts()) {
            WebUI ui = new WebUI();
            ui.setUrl("http://" + h.getName() + ":10002");
            ui.setName("HiveServer2 UI");
            uis.add(ui);
        }
        return uis;
    }

    @Function(id = "REFRESH_METADATA", name = "刷新元数据")
    public void refreshMetadata() { /* ... */ }

    @Available(fid = "REFRESH_METADATA")
    public boolean canRefresh() { return isAvailable(); }
}
```

---

## 7. 易踩坑（针对 @Serve）

1. **`order` 重复** → 框架按 `Comparator.comparing(getOrder)` 排序，重复值结果不确定。版本内严格唯一。
2. **`depends` 形成环** → 启动时 DAG 排序报错，整个 SDP 版本不可加载。
3. **`appends` 路径不存在的服务/配置** → 框架忽略，但运行期会有警告日志，且依赖配置同步链路断裂（如 Spark 找不到 core-site.xml）。
4. **`afterInstall` 中假设服务已可用** → 这是踩坑高发点。afterInstall 触发时进程刚启动，可用性轮询还没开始。如要"服务可用后操作"，请用 `callbackServe()`。
5. **`@Function.id` 重复** → AOP 切面按 id 路由，重复时只有一个生效，另一个静默被忽略。
6. **覆盖 `getName()`** → 框架按类的简单类名作为服务名，覆盖可能导致 SdpManager 按名查找失败。**不要覆盖**。
7. **服务类同时被 `@Component` 标注** → `@Serve` 内部已经 `@Component` 元注解，重复标注可能让 Spring 注册两次 Bean。
8. **修改 `version` 字段但保留旧服务名** → Web 端如果按 "服务名" 落库，升级版本时要做迁移；按 "服务名 + 版本号" 落库则无碍。
