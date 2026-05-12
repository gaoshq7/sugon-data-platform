# Reference: SdpManager / AbstractServe / AbstractProcess / AbstractConfig 详细 API

> 本文件是 `sdp-sdk` skill 的扩展参考。仅在以下场景读取：
> - 写"查/操作服务、进程、配置"相关 Controller / Service；
> - 实现服务的安装/启停/扩缩容、配置修改与回退、依赖查询等业务流；
> - 需要 `Blueprint` 数据结构、`AppStatus` 状态枚举、各方法完整签名时。

---

## 0. 对照源码

| 角色 | 位置 |
|---|---|
| `SdpManager` 接口 | `sdp-core/cn/gsq/sdp/core/SdpManager.java` |
| `AbstractSdpManager` | `sdp-core/cn/gsq/sdp/core/AbstractSdpManager.java` |
| `AbstractServe` | `sdp-core/cn/gsq/sdp/core/AbstractServe.java` |
| `AbstractProcess<T>` | `sdp-core/cn/gsq/sdp/core/AbstractProcess.java` |
| `AbstractConfig` | `sdp-core/cn/gsq/sdp/core/AbstractConfig.java` |
| `Blueprint` | `sdp-common/cn/gsq/sdp/Blueprint.java` |
| `AppStatus` | `sdp-core/cn/gsq/sdp/core/AppStatus.java` |
| `ServeHandler` / `ProcessHandler` | `sdp-core/cn/gsq/sdp/core/` |

---

## 1. SdpManager 接口

`AbstractSdpManager` 实现此接口，所有方法均为只读查询，可在 Step 3 完成后任意时刻调用。

```java
String getVersion();                                              // 当前已激活 SDP 版本
String getHome();                                                 // 当前版本安装包根目录
AbstractHostManager getHostManager();                             // 主机管理器（透传）
List<AbstractServe> getServes();                                  // 全部服务（按 order 升序）
AbstractServe getServeByName(String servename);                   // 按名取
List<AbstractServe> getParents(String servename);                 // 上游依赖（DAG）
List<AbstractServe> getChildren(String servename);                // 下游被依赖（DAG）
AbstractProcess<AbstractHost> getProcessByName(String name);      // 全局唯一进程名
AbstractConfig getConfigByName(String servename, String cname);   // 取配置文件
AbstractHost getHostByName(String hostname);                      // 透传至 HostManager
<T> T getExpectHostByName(String hostname);                       // 期望类型的主机（一般是上层自定义 Host 子类）
Map<String, List<String>> getHostsMapping();                      // hostname → [processName]
List<String> getProcessNamesByHostname(String hostname);          // 单主机上的进程名
List<AbstractProcess<AbstractHost>> getProcessModelsByHostname(String hostname);
boolean isHostCanRemove(String hostname);                         // 该主机能否安全下线
```

`AbstractSdpManager` 额外公开（lombok `@Getter`）：

```java
String version;
String home;
List<AbstractServe> serves;
```

---

## 2. `AbstractServe`：服务

### 2.1 状态查询

```java
boolean isInstalled();                       // 锁定态 或 任一进程已安装
boolean isAvailable();                       // 所有必需进程可用
boolean isExist();                           // 服务定义存在
boolean isExistConfig();                     // 含可见配置
RpcRespond<String> isServeAvailable();       // 业务级可用性
```

### 2.2 生命周期（带 `@Function` 标注，前端常作为按钮触发）

```java
@Function(id = "install")   void install(Blueprint.Serve blueprint);
@Function(id = "start")     void start();
@Function(id = "stop")      void stop();
@Function(id = "restart")   void restart();
@Function(id = "uninstall") void uninstall();
void recover();                              // 卸载后环境恢复
```

**安装流程概要**：`install` 内部按以下顺序执行（消费者只需知道**最重要的两个回调点**）：

1. 加锁 + 状态置 `INSTALLING`；
2. 在所有目标主机下载安装包；
3. 调 **`ServeDriver.receiptInstallServe(blueprint)`** 落库回调（**消费者侧在这里做服务元数据持久化**）；
4. 初始化配置 → 启动进程（DAG 顺序）；
5. 轮询所有进程的 `isAvailable()`，超时 → 抛错回滚；
6. 解锁 + 状态置 `RUNNING`。

**异常处理**：任意步骤异常都会触发 `recover()`，进入 `UNINSTALLING` 状态做反向清理。

> ⚠️ `ServeDriver.receiptInstallServe` 在第 3 步触发，但服务此时**还没安装完成**——若安装最终失败，落库内容需要由你的 ServeDriver 实现做幂等清理。

### 2.3 DAG / 依赖

```java
List<AbstractServe> getParents();            // 自己依赖谁（@Serve.depends）
List<AbstractServe> getChildren();           // 谁依赖自己
```

### 2.4 配置查询/修改

```java
List<AbstractConfig> getAllConfigs();
List<AbstractConfig> getDisplayConfigs();    // 仅 @Config(show = true)
AbstractConfig getConfigByName(String name);
Map<String, String> getConfigDefaultContentToMap(String cname);
Map<String, String> getConfigBranchContentToMap(String cname, String bname);
String getConfigDefaultValueByKey(String cname, String key);
void updateConfigDefault(String cname, Map<String, String> items);
```

### 2.5 主机 / 进程

```java
AbstractProcess<AbstractHost> getProcessByName(String name);
List<AbstractHost> getProcessHosts(String name);              // 某进程当前承载主机
Set<AbstractHost> getHosts();                                 // 服务涉及的全部主机去重
Map<String, String> getProperties();                          // 展示用属性
List<WebUI> getWebUIs();                                      // 服务 UI 入口
```

---

## 3. `AbstractProcess<T extends AbstractHost>`：进程

### 3.1 状态查询

```java
boolean isInstalled();
boolean isAvailable();
List<AbstractProcess<AbstractHost>> getParents();
List<AbstractProcess<AbstractHost>> getChildren();
boolean canStart();
boolean canStop();
boolean canExtend();
boolean canShorten();
```

### 3.2 生命周期 / 操作

```java
@Function(id = "start")    void start();                          // 在所有承载主机上启动
@Function(id = "stop")     void stop();
@Function(id = "restart")  void restart();
@Function(id = "EXPAND")   void extend(List<String> hostnames);   // 扩容
@Function(id = "SHRINK")   void shorten(List<String> hostnames);  // 缩容
```

> 进程是否允许扩缩容由 SDP 包决定（`@Process.dynamic`）；消费者侧调用前用 `canExtend()` / `canShorten()` 判断即可。

### 3.3 主机视图

```java
List<T> getActiveHosts();                    // 健康
List<T> getDeadHosts();                      // 异常
Integer getPort();                           // 对外端口（-1 表示无端口，框架走 ps 兜底）
String currentLog(String hostname);          // 取该主机上最近日志
HostGroup getHostGroup();                    // 进程在当前 mode 下所属分组
List<Operation> getFunctions();              // 该进程支持的 @Function 列表（前端按钮）
String getServename();
```

### 3.4 进程模式

每个进程的部署/可用性判定模式（MASTER/SLAVE/WATCH/ALONE/ELECTION）由 SDP 包侧的 `@Process.handler` 决定，**消费者无需关心**。从消费者角度，只需用 `isAvailable()` 判断进程是否可用即可。

---

## 4. `AbstractConfig`：配置文件

### 4.1 元数据

```java
Boolean isDisplay();                         // @Config.show
List<String> getBranchNames();               // 例如 ["default"] 或 ["master","worker"]
String getServeName();
```

### 4.2 读

```java
Map<String, String> getDefaultBranchContent();
Map<String, String> getBranchContent(String branchName);
Map<String, ConfigItem> getDefaultBranchDetails();          // 含字典元数据，前端编辑用
Map<String, ConfigItem> getBranchDetails(String branchName);
Map<String, ConfigItem> getBranchInstallingConfig(String branchName);   // 安装期初值
List<ConfigItem> getBranchDictionary(String branchName);     // 配置字典（默认值/描述/校验）
Boolean IsConfigItemInDictionary(String branchName, String key);
Boolean IsConfigItemInUse(String branchName, String key);
Boolean isDictionaryExists(String branchName);
Map<String, ConfigCompare> getConfigCompare(ConfigBranch b1, ConfigBranch b2);
```

`ConfigItem` 关键字段：`key` / `value` / `origin`（默认值）/ `label` / `description` / 权限标志。

### 4.3 写

```java
void updateConfig(String branchName, Map<String, String> items);    // 修改并同步到主机
void updateDefaultConfig(Map<String, String> items);
```

> 内部流程：`branch.updateContent(items)` → `ConfigDriver.conform(getSelfMetadata(), items)` 将变更下发到主机。**框架不会自动重启服务**——上层若需"改配置后重启"，请显式触发 `AbstractServe.restart()`。

### 4.4 分支主机管理

```java
void addBranchHosts(String branchName, String... hostnames);
void addBranchHostsByDefault(String... hostnames);
void delBranchHosts(String branchName, String... hostnames);
void delBranchHostsByDefault(String... hostnames);
void rollbackBranchHostsAfterExtend(String branchName, String... hostnames);
void rollbackBranchHostsAfterShorten(String branchName, String... hostnames);
```

### 4.5 配置数据源（CSV）

CSV 文件随 SDP 实现包打入 jar：

```
resources/{serviceName}/{configFileName}$.csv             # 单分支
resources/{serviceName}/{configFilePrefix}/{branch}.{suffix}$.csv   # 多分支
```

- `$` 表示该 CSV 包含**字典**（默认值 + 元数据），不带 `$` 则只有键值对；
- 格式分两种：`key-value 模式`（标准结构化）和 `纯文本模式`（整文件存为一项）；
- `configType` 由 `@Config.type` 指定（xml / cfg / properties / text 等），仅作展示/格式化。

---

## 5. `Blueprint` 数据结构（用于 `install`）

```java
class Blueprint {
    String version;                     // 目标 SDP 版本
    String mode;                        // 目标部署模式
    List<Serve> serves;                 // 服务清单（含进程/配置）

    static class Serve {
        String name;                    // 服务名（对应 @Serve 注解类）
        Map<String, Object> args;       // 自定义安装参数
        List<Process> processes;        // 进程→主机映射
        List<Config> configs;           // 配置覆盖
    }

    static class Process {
        String name;                    // 进程名
        List<String> hostnames;         // 部署目标主机
    }

    static class Config {
        String name;                    // 配置文件名
        String branch;                  // 分支
        Map<String, String> items;      // 安装期覆盖项（覆盖默认值）
    }
}
```

---

## 6. `AppStatus` 状态枚举

| 值 | 含义 |
|---|---|
| `INSTALLING` | 安装中 |
| `RUNNING` | 正常运行 |
| `FAULT` | 至少一个必需进程不可用 |
| `STARTING` / `STOPPING` / `RESTARTING` | 启停中间态 |
| `UNINSTALLING` | 卸载中 |
| `OPERATING` | 通用操作中 |
| `CHECK_AVAILABLE` | 可用性检测中 |

`AbstractHost.getStatusByProcessName(processName)` 返回主机视角的中间态（适合 UI 进度条）；服务/进程的整体状态走 `isInstalled` / `isAvailable` 组合判定。

---

## 7. 典型上层代码片段

### 7.1 列出服务与状态

```java
@GetMapping("/api/sdp/serves")
public List<ServeView> list() {
    return sdpEnvManager.getSdpManager().getServes().stream()
        .map(s -> new ServeView(s.getName(), s.isInstalled(), s.isAvailable()))
        .toList();
}
```

### 7.2 安装一个服务

```java
@PostMapping("/api/sdp/serves/{name}/install")
public void install(@PathVariable String name, @RequestBody Blueprint.Serve blueprint) {
    AbstractServe serve = sdpEnvManager.getSdpManager().getServeByName(name);
    if (serve == null) throw new NotFoundException("服务不存在: " + name);
    blueprint.setName(name);
    serve.install(blueprint);
}
```

### 7.3 修改配置并重启服务

```java
public void updateAndRestart(String serveName, String configName, Map<String, String> items) {
    AbstractServe serve = sdpEnvManager.getSdpManager().getServeByName(serveName);
    AbstractConfig config = serve.getConfigByName(configName);
    config.updateDefaultConfig(items);   // 同步配置到主机
    serve.restart();                      // 触发服务重启加载新配置
}
```

### 7.4 进程扩容

```java
public void expand(String processName, List<String> newHosts) {
    AbstractProcess<AbstractHost> p = sdpEnvManager.getSdpManager().getProcessByName(processName);
    if (!p.canExtend()) throw new IllegalStateException("当前不可扩容");
    p.extend(newHosts);
}
```

---

## 8. 易踩坑（针对 SDP 管理）

1. **`install` / `start` / `stop` / `restart` / `uninstall` 都是 `synchronized`**——同一服务并发触发会串行，上层需考虑请求超时。
2. **`install` 抛错会触发 `recover()`**：会跑反向卸载逻辑，配置/进程残留会被尝试清理，但 **`ServeDriver.receiptInstallServe` 已经回调过的副作用（如落库）需要驱动实现侧自行处理幂等**。
3. **`Blueprint.Process.hostnames` 必须是当前 mode 下合法分组的主机**，否则该进程实际不会被部署到任何主机，安装看似成功但服务不可用。
4. **`updateConfig` 不会自动重启服务**——配置同步到磁盘但进程不会感知，需显式 `restart()`。
5. **`getConfigByName(serveName, cname)` 区分大小写**：`cname` 是 `@Config` 注解所在类的简单类名（默认）或 `@Config.name` 显式指定。
6. **`AbstractProcess.extend / shorten` 仅当 `@Process(dynamic = true)` 时可用**，否则 `canExtend() / canShorten()` 返回 false。
7. **`getHosts()` 返回 `Set<AbstractHost>`** 是服务**当前实际承载**的主机去重集合，与 Blueprint 中声明的主机可能不一致（如有进程已扩缩容）。
