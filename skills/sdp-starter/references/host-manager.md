# Reference: HostManager / AbstractHost 详细 API

> 本文件是 `sdp-sdk` skill 的扩展参考。仅在以下场景读取：
> - 写"主机纳管/扩缩容/分组调整/远程命令执行"相关 Controller / Service；
> - 需要 `HostInfo` / `HostGroup` 数据结构、`AbstractHost` 上各远程操作的完整签名；
> - 排查"主机注册了但不部署"、"进程启停超时"、"Wormhole 连接失败"等问题。

---

## 0. 对照源码

| 角色 | 位置 |
|---|---|
| `HostManager` 接口 | `sdp-core/cn/gsq/sdp/core/HostManager.java` |
| `AbstractHostManager` | `sdp-core/cn/gsq/sdp/core/AbstractHostManager.java` |
| `AbstractHost` | `sdp-core/cn/gsq/sdp/core/AbstractHost.java` |
| `HostGroup` | `sdp-core/cn/gsq/sdp/core/HostGroup.java` |
| `HostInfo` | `sdp-common/cn/gsq/sdp/HostInfo.java` |
| `HostDriver` | `sdp-external-driver/cn/gsq/sdp/driver/HostDriver.java` |

---

## 1. `HostManager` 接口

```java
void addHosts(HostInfo ... hostInfos);                       // 注册新主机（含初始化）
void removeHosts(String ... hostnames);                      // 删除主机
void updateHostGroups(String hostname, List<String> groups); // 修改分组（整体覆盖）
List<HostGroup> getHostGroups();                             // 当前 mode 下所有分组定义
List<AbstractHost> getHosts();
AbstractHost getHostByName(String name);
```

`AbstractHostManager` 额外公开：

```java
HostGroup getHostGroup(String name);            // 按名取分组
<T> T getExpectHostByName(String name);         // 期望类型（上层自定义 Host 子类）
String getMode();                               // 当前已选 mode
```

protected 但通过 `SdpEnvManager` 暴露：

```java
List<String> getModes();                        // 通过 sdpEnvManager.getModes()
void setMode(String mode);                      // 通过 sdpEnvManager.setMode(mode)
```

---

## 2. `HostInfo` 数据结构

```java
public class HostInfo {
    private String hostname;             // 主机名（同时是 Spring Bean 名）
    private List<String> groups;         // 分组名列表（必须是当前 mode 下合法的 group 名）
}
```

**注意：**
- `hostname` 在集群内必须唯一（重复 `addHosts` 抛 `RuntimeException`）；
- `groups` 中的每个名称必须能在 `getHostGroups()` 里找到对应 `HostGroup`，否则主机虽然注册成功，但不参与任何 group 的部署目标计算。

---

## 3. `HostGroup` 接口

由 `@Mode` 注解的枚举类实现：

```java
public interface HostGroup {
    String name();              // group 名（如 "MASTER"），枚举值名即是
    int min();                  // 最小部署节点数
    int max();                  // 最大部署节点数（-1 表示无上限）
    String description();
    String mode();              // 所属部署模式名（来自 @Mode("XXX") 注解，⚠️ 不要覆盖）
}
```

---

## 4. `addHosts` 流程（关键路径）

`AbstractHostManager.addHosts(HostInfo...)` 内部对每台主机执行 `hostEnvInit`：

1. **`hostRegister(hostInfo)`**：通过 `GalaxySpringUtil.registerBean(hostname, hostClass, hostname, groups)` 注册 `AbstractHost` Bean；
2. **`host.environment(hostname)`**：主机自初始化（默认逻辑：遍历所有 `AbstractServe`，若 `isAllMust()=true` 且已安装，则下载对应安装包到本机，并将本机加入该服务相关配置的默认分支）；
3. **遍历所有服务（按 DAG 顺序）**：若服务已安装 → 调 `serve.environment(hostname)` → 再遍历其进程 `process.environment(hostname)`；
4. **任何步骤抛异常 → 自动调用 `removeHosts(hostname)` 回滚**。

> 因此 `addHosts` 是一个"重操作"：会触发安装包下载、配置同步等远程操作，单台机器初始化可能持续数分钟。上层接口建议异步。

---

## 5. `AbstractHost` 完整 API

### 5.1 标识 / 查询

```java
String getName();                                      // hostname
List<String> getGroupNames();
List<HostGroup> getGroups();                           // 由 @Getter 提供
Type getType();                                        // 框架内部分类
AppStatus getStatusByProcessName(String processName);  // 中间态（STARTING/STOPPING/RESTARTING）
```

### 5.2 主机级启停（聚合所有进程）

```java
void start();                            // 启动该主机上所有进程
void stop();                             // 停止
void restart();                          // 重启
boolean isHostActive();                  // 走 HostDriver.isAlive
boolean canRemove();                     // 上面没有可用进程则可移除
```

### 5.3 进程级控制（带 `@Function`）

```java
@Function(id = "startProcess")   void startProcess(String processname);
@Function(id = "stopProcess")    void stopProcess(String processname);
@Function(id = "restartProcess") void restart(String processname);

void startProcess(AbstractProcess<? extends AbstractHost> process);
void stopProcess(AbstractProcess<? extends AbstractHost> process);

boolean isProcessActive(AbstractProcess<? extends AbstractHost> process);
```

**`isProcessActive` 检测策略**：

- 若 `process.getPort() != -1`：TCP 端口探针（500 ms 超时）；
- 否则：通过 `RpcDriver` 执行 `ps -ef | grep <mark>` 检测。

**启停超时**：`startProcess` / `stopProcess` 内部 `waitForSignal` 轮询 `isProcessActive`，**默认 180 秒超时，每 4 秒一次**。超时抛 `RuntimeException` 并清理中间态。

### 5.4 Pilot / Agent（部分已废弃）

```java
void startPilot();                       // 启动控制端 Agent（端口 9888）
void stopPilot();
void restartPilot();
boolean isPilotActive();
boolean isDockerActive();
boolean isDockerInstalled();
```

> `PilotDriver` 已 `@Deprecated`，新接入不建议依赖。

### 5.5 文件 / 资源

```java
boolean isOpen(int port);                              // 本机端口探针
boolean isFileExist(String path);                      // 文件存在性
void downloadPackage(String version, String pkg);      // 调 ResourceDriver 拉安装包
String currentLog(/* ... */);                          // 拉日志末尾（默认 200 行）
```

### 5.6 磁盘 / 通用脚本（Wormhole）

```java
String listDisk(String op);
@Function(id = "MOUNTDISK") String mountDisk(MountParams.DiskInfo diskInfo);

String actuator(String name, Map<String, Object> params);   // 执行任意命名脚本
AuditMsg audit(String id);                                  // 取执行审计日志
```

**`actuator`**：通过 Wormhole 协议建立 TCP 连接到主机的 10086 端口（默认凭证 `admin/admin1234@sugon`），同步发送脚本执行请求并按行处理输出。`exitCode != 0` 时抛 `RuntimeException`。

### 5.7 框架钩子（一般不直接调用）

```java
void initialization(AbstractApp app, Map<String, Object> params);
void uninstall(AbstractApp app, Map<String, Object> params);
```

---

## 6. 典型上层代码片段

### 6.1 列出主机与状态

```java
@GetMapping("/api/sdp/hosts")
public List<HostView> list() {
    return sdpEnvManager.getHostManager().getHosts().stream()
        .map(h -> new HostView(h.getName(), h.getGroupNames(), h.isHostActive()))
        .toList();
}
```

### 6.2 注册新主机

```java
@PostMapping("/api/sdp/hosts")
public void addHost(@RequestBody HostAddRequest req) {
    HostInfo info = new HostInfo(req.getHostname(), req.getGroups());
    sdpEnvManager.getHostManager().addHosts(info);   // 同步阻塞，可能数分钟
}
```

### 6.3 修改分组（例如把一台 worker 升级为 master）

```java
public void promote(String hostname) {
    sdpEnvManager.getHostManager()
        .updateHostGroups(hostname, List.of("MASTER", "DATA"));
}
```

### 6.4 在主机上执行自定义脚本

```java
public void mountDataDisk(String hostname, String dev, String path) {
    AbstractHost host = sdpEnvManager.getHostManager().getHostByName(hostname);
    MountParams.DiskInfo info = new MountParams.DiskInfo();
    info.setDriver(dev);
    info.setPath(path);
    String stdout = host.mountDisk(info);
    log.info("挂载结果：{}", stdout);
}
```

### 6.5 删除主机（先校验可移除）

```java
public void offline(String hostname) {
    SdpManager sdp = sdpEnvManager.getSdpManager();
    if (!sdp.isHostCanRemove(hostname)) {
        throw new IllegalStateException("主机上仍有可用进程，不能下线：" + hostname);
    }
    sdpEnvManager.getHostManager().removeHosts(hostname);
}
```

---

## 7. `HostDriver` 接口（上层必须实现）

```java
public interface HostDriver {
    Set<HostInfo> loadHosts();                                  // 启动时从持久化层加载
    boolean removeHostsCallback(String... hostnames);           // removeHosts 时回调（持久化层删除）
    boolean updateHostGroups(String managerName, List<String> groups);  // 更新分组的回调
    boolean isExist(String hostname);                           // 存在性查询
    boolean isAlive(String hostname);                           // 存活检测（影响 isHostActive）
}
```

**实现要点**：

- `loadHosts` 在 `loadEnvResource` 阶段被调用，必须返回**当前 mode 下合法**的 `HostInfo`；
- `removeHostsCallback` 返回 `true` 才会真正从 Spring 容器删除 Bean；
- `isAlive` 频繁被调用（每次 `isHostActive`），实现需快速（建议带缓存）。

---

## 8. 易踩坑（针对主机管理）

1. **`HostInfo.groups` 包含非法 group 名**：主机会注册成功但**不出现在任何服务的部署目标里**，表现为"主机存在但服务装不上去"。debug 时先查 `getHostManager().getHostGroups()` 确认合法名。
2. **`addHosts` 阻塞时间长**：会触发安装包下载和配置同步，单机可能数分钟。Web 接口请异步处理（如返回 task id + 轮询进度）。
3. **`removeHosts` 失败回滚不彻底**：`removeHostsCallback` 返回 false 时只是不删 Bean，但 `hostEnvInit` 阶段若已下载部分资源，**这些资源不会被自动清理**。建议覆盖 `HostDriver.removeHostsCallback` 时尽量保证幂等成功。
4. **`updateHostGroups` 是整体覆盖语义**：传入 `["MASTER"]` 会把现有所有分组替换为单个 `MASTER`，不是追加。
5. **进程启停超时 180s**：长启动服务（如 HBase Master）可能超时。可通过覆盖 `AbstractProcess.isAvailable()` 或调整启动脚本提高首次响应速度。
6. **Wormhole 默认凭证**：`admin / admin1234@sugon` + 端口 10086。若上层环境改了凭证，必须用对应的 Wormhole 配置覆盖，否则所有 `actuator` 调用失败。
7. **`getHostByName` 区分大小写且必须完全匹配**：传入短名（不含域名）和 FQDN 会被视作不同主机。建议在 `addHosts` 前统一标准化。
8. **`hostClass` 构造器**：上层自定义的 `@Host` 类必须有 `(String hostname, List<String> groups)` 构造器，否则反射注册时报 `NoSuchMethodException`。
