# Reference: 驱动 Bean 覆盖指南

> 本文件是 `sdp-sdk` skill 的扩展参考。仅在以下场景读取：
> - 上层项目首次接入 SDP，需要决定要覆盖哪些驱动；
> - 实现 `HostDriver` / `RpcDriver` / `ConfigDriver` / `ResourceDriver` 等关键驱动；
> - 排查"配置改了没生效"、"启停操作 no-op"、"主机列表为空"等驱动未覆盖类问题。

---

## 0. 总览：11 个驱动接口

> 注：`SdpAutoConfigure` 总共注入 11 个 `@ConditionalOnMissingBean` 驱动（"13 个驱动"是按字段计数，部分接口内含子能力）。

| 驱动接口 | 优先级 | 用途 | 默认实现 |
|---|---|---|---|
| `HostDriver` | ✅ 必须 | 主机清单的装载/增删/存活检测 | 空集合，回调全 true |
| `RpcDriver` | ✅ 必须 | 在远端主机执行启停脚本 / 用户/keytab 操作 | `execute` 返回空响应 |
| `ConfigDriver` | ✅ 必须 | 配置下发到主机、扩缩容时同步 | 全部仅日志 |
| `ResourceDriver` | ✅ 必须 | 下载安装包、判定 SDP/服务可用性 | `isSdpAvailable` 永远 false |
| `ServeDriver` | 视需要 | 服务安装/卸载完成回调（持久化） | 全 no-op |
| `BroadcastDriver` | 视需要 | 集群事件广播 | 仅日志 |
| `LogDriver` | 视需要 | 业务级日志通道 | 转 SLF4J |
| `ProcessDriver` | 视需要 | 进程级主机列表同步钩子 | `initHosts` 返回空 |
| `SshDriver` | 视需要 | SSH 启停 Agent / 主机首次加入 SSH 准备 | 全 no-op |
| `WormholeDriver` | 视需要 | Wormhole 脚本执行的行处理器 | 仅日志 |
| `PilotDriver` | ❌ 已 `@Deprecated` | 旧的 Agent 控制通道 | 全 no-op，不用覆盖 |

---

## 1. 覆盖通用模式

在任意 `@Configuration` 类里声明同类型 `@Bean`，即可替换默认实现：

```java
@Configuration
public class SdpDriverConfig {

    @Bean
    public HostDriver hostDriver(HostRepository repo) {
        return new HostDriver() { /* ... */ };
    }
}
```

`SdpAutoConfigure` 中的默认实现都标了 `@ConditionalOnMissingBean`，**只要上层声明同类型 Bean 就会被替换**。

---

## 2. `HostDriver`（必须覆盖）

```java
public interface HostDriver {
    Set<HostInfo> loadHosts();                                    // Step 3 装载主机
    boolean removeHostsCallback(String... hostnames);             // removeHosts 时回调
    boolean updateHostGroups(String managerName, List<String> groups);
    boolean isExist(String hostname);
    boolean isAlive(String hostname);                             // 频繁调用，要快
}
```

**典型实现（对接 JPA）：**

```java
@Component
@RequiredArgsConstructor
public class JpaHostDriver implements HostDriver {

    private final HostEntityRepository repo;
    private final HeartbeatService heartbeat;

    @Override
    public Set<HostInfo> loadHosts() {
        return repo.findAll().stream()
            .map(e -> new HostInfo(e.getHostname(), e.getGroups()))
            .collect(Collectors.toSet());
    }

    @Override
    public boolean removeHostsCallback(String... hostnames) {
        repo.deleteAllByHostnameIn(List.of(hostnames));
        return true;
    }

    @Override
    public boolean updateHostGroups(String hostname, List<String> groups) {
        repo.updateGroupsByHostname(hostname, groups);
        return true;
    }

    @Override
    public boolean isExist(String hostname) {
        return repo.existsByHostname(hostname);
    }

    @Override
    public boolean isAlive(String hostname) {
        return heartbeat.isAlive(hostname);     // 带缓存，TTL 5s
    }
}
```

**坑：**
- `isAlive` 在 UI 渲染时高频调用，**实现必须快**（缓存/异步刷新）；
- `loadHosts` 返回的 `HostInfo.groups` 必须是当前 mode 下合法的 group 名；
- `removeHostsCallback` 失败（返回 false）会让 Spring 容器中 Bean 残留——务必保证幂等成功。

---

## 3. `RpcDriver`（必须覆盖）

```java
public interface RpcDriver {
    RpcRespond<String> execute(RpcRequest rpcRequest);                  // 同步执行远程脚本
    void addUser(RpcRequest rpcRequest, String username, String uuid);  // 加 Linux 用户
    void delUser(RpcRequest rpcRequest, String username);
    void createKeytab(String masterHost, String username, String hostname, String uid);  // Kerberos
}
```

`RpcRequest` 字段：`hostname`、`home`（工作目录）、`command`（脚本命令）等。

**典型实现（基于 Galaxy RPC 框架）：**

```java
@Bean
public RpcDriver rpcDriver(GalaxyClient client) {
    return new RpcDriver() {
        @Override
        public RpcRespond<String> execute(RpcRequest req) {
            return client.send("script-executor", req, RpcRespond.class);
        }
        // 其余方法略
    };
}
```

**坑：**
- `execute` 必须**同步等待执行完成并返回 `exitCode` + stdout**，框架靠返回值判断成败；
- 不同主机的执行通道（SSH / RPC / Wormhole）可以混用，但必须保证 `home` 切换正确；
- 大输出（如 ps）建议截断到 1MB 以内，避免 OOM。

---

## 4. `ConfigDriver`（必须覆盖）

```java
public interface ConfigDriver {
    void conform(ConfigBranch branch, List<ConfigItem> items);          // 配置变更下发
    List<ConfigItem> loadConfigItems(ConfigBranch branch);              // 装载已保存配置
    void extendBranchHosts(ConfigBranch branch, Set<String> hostnames); // 扩容时加主机
    void abandonBranchHosts(ConfigBranch branch, Set<String> hostnames);// 缩容时减主机
    void destroy(ConfigBranch branch);                                  // 销毁分支
    ConfigItem getItemMetadata(ConfigBranch branch, String key);        // 取字典元数据
}
```

`ConfigBranch` 字段：`serveName` / `configName` / `branchName` / `type`（xml/cfg/...）/ `paths`（目标路径列表）/ `hosts`（覆盖主机集合）。

**典型职责：**

- `conform`：把 `items` 渲染成实际配置文件内容，**写入 `branch.hosts` 中每台主机的 `branch.paths` 路径**。可用 `RpcDriver` 或 SCP 完成。
- `loadConfigItems`：进程重启时从持久化层取出当前生效的配置（用于回填 UI / 校验一致性）。
- `extendBranchHosts` / `abandonBranchHosts`：进程扩缩容时，框架会调用这两个方法同步配置覆盖范围。

**坑：**
- `conform` 是关键路径，任何下发失败必须**抛异常**（不要吞），否则上层认为配置已生效；
- 渲染配置要根据 `branch.getType()` 区分 xml / properties / cfg / text 等格式；
- 多机并发下发建议用线程池。

---

## 5. `ResourceDriver`（必须覆盖）

```java
public interface ResourceDriver {
    void download(Resource resource);                       // 下载安装包到主机
    boolean isSdpAvailable(String version);                 // SDP 整体可用性
    boolean isServeAvailable(String version, String servename);
}
```

`Resource` 字段：`hostname`、`version`、`pkg`（包名），驱动需把对应安装包从镜像源拉到指定主机。

**`isSdpAvailable` 重要性：**
- `getVersions()` 返回的 `SdpBaseInfo.isAvailable` 字段直接取自此方法；
- 默认永远 false → 上层 UI 永远显示"版本不可用"。**接入方必须覆盖**。

判定逻辑示例：

```java
@Override
public boolean isSdpAvailable(String version) {
    // 例如：镜像仓库里所有必需安装包都存在
    return packageRegistry.allPresent(version, REQUIRED_PACKAGES);
}
```

---

## 6. `ServeDriver`（视需要，建议覆盖以落库）

```java
public interface ServeDriver {
    void receiptInstallServe(Blueprint.Serve serve);   // 服务安装回调（在 callbackServe 之前）
    void receiptUninstallServe(String serve);          // 服务卸载回调
    void updateResourcePlan(String serve);             // 资源规划变更
}
```

`receiptInstallServe` 是常用的落库点：把"哪个服务被装到哪些主机、用什么配置"持久化下来，便于后续重启时由 `loadHosts` / `loadConfigItems` 重建状态。

**实现幂等：** `install` 失败回滚时此回调已经被调用过——上层落库必须支持"安装失败后清理"。

---

## 7. 其余驱动一句话

### `BroadcastDriver`
```java
void appNotice(String hostname, AppEvent appEvent, String serveName, String msg);
```
发集群事件（例如 WebSocket 推 UI）。默认仅日志，**没业务消息推送需求可不覆盖**。

### `LogDriver`
```java
void log(RunLogLevel level, String msg);
```
框架业务日志的输出通道。默认走 SLF4J，**通常不需要覆盖**。

### `ProcessDriver`
```java
List<String> initHosts(String processname);
void addHosts(String processname, Collection<String> hostnames);
void delHosts(String processname, Collection<String> hostnames);
```
进程级主机映射的同步钩子。如果上层已经在 `ServeDriver.receiptInstallServe` 里落库了完整 blueprint，这里可不实现。

### `SshDriver`
```java
void startAgent(String hostname);
void stopAgent(String hostname);
void addHost(SshInfo info) throws Exception;       // 首次加入主机的 SSH 互信准备
```
若上层已有自家的 SSH 互信流程，此驱动可保留默认空实现。

### `WormholeDriver`
```java
void id(String id);
void handle(String line);
```
`AbstractHost.actuator` 的行级回调（执行 id 标记 + stdout 逐行处理）。一般默认就够，**有特殊审计需求时再覆盖**。

### `PilotDriver`
全 `@Deprecated`，**不要使用**。

---

## 8. 最小可运行驱动配置（参考模板）

```java
@Configuration
public class MinimalSdpDriverConfig {

    @Bean
    public HostDriver hostDriver(HostRepository repo) {
        return new JpaHostDriver(repo, /* heartbeat */);
    }

    @Bean
    public RpcDriver rpcDriver(GalaxyClient client) {
        return req -> client.send("script-executor", req, RpcRespond.class);
        // addUser/delUser/createKeytab 按需补
    }

    @Bean
    public ConfigDriver configDriver(ConfigSyncService sync, ConfigStore store) {
        return new ConfigDriver() {
            @Override public void conform(ConfigBranch b, List<ConfigItem> items) { sync.push(b, items); }
            @Override public List<ConfigItem> loadConfigItems(ConfigBranch b)      { return store.load(b); }
            @Override public void extendBranchHosts(ConfigBranch b, Set<String> h) { sync.push(b, store.load(b), h); }
            @Override public void abandonBranchHosts(ConfigBranch b, Set<String> h){ sync.clean(b, h); }
            @Override public void destroy(ConfigBranch b)                          { store.drop(b); }
            @Override public ConfigItem getItemMetadata(ConfigBranch b, String k)  { return store.meta(b, k); }
        };
    }

    @Bean
    public ResourceDriver resourceDriver(PackageRegistry reg) {
        return new ResourceDriver() {
            @Override public void download(Resource r)                              { reg.fetch(r); }
            @Override public boolean isSdpAvailable(String v)                       { return reg.sdpReady(v); }
            @Override public boolean isServeAvailable(String v, String s)           { return reg.serveReady(v, s); }
        };
    }

    @Bean
    public ServeDriver serveDriver(ServeStateRepo repo) {
        return new ServeDriver() {
            @Override public void receiptInstallServe(Blueprint.Serve s) { repo.markInstalled(s); }
            @Override public void receiptUninstallServe(String s)        { repo.markUninstalled(s); }
            @Override public void updateResourcePlan(String s)           { /* no-op or update plan */ }
        };
    }
}
```

---

## 9. 易踩坑（针对驱动覆盖）

1. **覆盖顺序无关，但同一接口只能有一个非默认 Bean**——多个 `@Bean` 同类型会让 Spring 启动失败。
2. **不要在驱动实现里反向调用 `SdpEnvManager` / `SdpManager`**——会形成循环依赖（驱动是被它们调用的下游）。若必须，请用 `@Lazy` + `ObjectProvider`。
3. **`conform` / `download` / `execute` 抛异常会向上传播到 `AbstractServe.install`**，进而触发 `recover()` 回滚——驱动实现要么稳健完成、要么明确抛错，**不要吞异常返回成功**。
4. **`isAlive` 高频调用必须快**：是 UI 主机列表渲染时每个主机都调一次，建议加 5~10s 本地缓存。
5. **`loadHosts` 失败会让 `loadEnvResource` 整体挂掉**：建议在驱动实现里 try-catch 单条数据异常，避免一台主机数据脏导致整个集群初始化失败。
6. **Kerberos 相关方法**（`createKeytab` / `addUser`）：若上层无 Kerberos 启用，保留空实现即可，不会影响常规功能。
