# Reference: SdpEnvManager 详细 API 与初始化

> 本文件是 `sdp-sdk` skill 的扩展参考。仅在以下场景读取：
> - 写 SDP 初始化逻辑、切换 SDP 版本/部署模式；
> - 排查 `RuntimeException("SDP版本不存在...")` / `"分组模式不存在"` / `sdp.root.classpath` 相关启动错误；
> - 需要 `getVersions()` / `getModes()` 等查询接口的完整字段。

---

## 1. 对照源码

- `cn.gsq.sdp.core.SdpEnvManager`（`sdp-spring-boot-starter`）—— 主类，含三步初始化逻辑
- `cn.gsq.sdp.core.SdpAutoConfigure` —— Spring 自动装配
- `cn.gsq.sdp.core.annotation.Sdp` / `Host` / `Mode` —— 注解约定
- `cn.gsq.sdp.SdpBaseInfo` —— `getVersions()` 返回元素类型

---

## 2. `SdpEnvManager` 完整 API

| 方法 | 签名 | 说明 |
|---|---|---|
| 构造 | `SdpEnvManager(AbstractSdpManager, AbstractHostManager)` | 由 `SdpAutoConfigure` 自动调用，构造时立即扫描 `sdp.root.classpath` 下所有 `@Sdp` 注解类，得到 `sdpMetas` 列表 |
| `getVersions()` | `List<SdpBaseInfo> getVersions()` | 列出所有已扫描到的 SDP 版本；`isAvailable` 字段由 `ResourceDriver.isSdpAvailable(version)` 决定 |
| `getModes()` | `List<String> getModes()` | 当前已加载版本下所有 mode 名（来自 `@Mode` 枚举） |
| `loadSdp(version)` | `void loadSdp(String version)` | 见 §3 |
| `setMode(mode)` | `void setMode(String mode)` | 把当前 mode 写入 `HostManager` |
| `loadEnvResource()` | `void loadEnvResource()` | 见 §3 |
| `getSdpManager()` | `AbstractSdpManager getSdpManager()` | lombok `@Getter` 生成，构造即可用 |
| `getHostManager()` | `AbstractHostManager getHostManager()` | 同上 |
| `getSdpMetas()` | `List<SdpMeta> getSdpMetas()` | 内部元数据（version + classpath），一般无需调用 |

---

## 3. 三步初始化详解

### Step 1：`loadSdp(String version)`

源码做的事（按顺序）：

1. 在 `sdpMetas` 里查找 `version`，未找到 → `RuntimeException("SDP版本不存在：" + version)`；
2. `sdpManager.setDrivers()` —— 把所有驱动 Bean 装到 `AbstractBeansAssemble` 父类的字段上；
3. `sdpManager.setVersion(version)` / `setHome(SDP_BASE_PATH + "/" + version)`；
4. `hostManager.setDrivers()` / `setHostClass(...)` —— 解析该版本下的 `@Host` 注解类；
5. `hostManager.resetMode()` —— **清空已有的 mode** 与所有 mode→groups 映射；
6. 扫描该版本 classpath 下所有 `@Mode` 注解枚举类（必须 `isEnum() && implements HostGroup`），按 `mode()` 字段分桶，调用 `hostManager.addMode(mode, groups)`；
7. 刷新 `ApplicationContext`（如尚未注入）；
8. **删除所有以 `sdp.root.classpath` 开头的 Spring Bean**（清旧版本残留）；
9. `GalaxySpringUtil.dynamicLoadPackage(classpath, ...)` —— 动态注册新版本所有 Bean；
10. 遍历所有 `AbstractSdpComponent`，调用 `initProperty()`（属性自检/依赖解析）；
11. 把所有 `@Serve` Bean 按 `order` 排序后注入到 `sdpManager.setServes(...)`。

> 频繁切换版本时 Step 8/9 开销显著（涉及 Spring Bean 动态注册），建议运维侧加锁。

### Step 2：`setMode(String mode)`

直接委托给 `AbstractHostManager.setMode(mode)`：

- 若 `mode` 不存在于已加载的 `modes` 映射 → `RuntimeException("\"xxx\"分组模式不存在。")`；
- 设置成功后 `getHostGroups()` 返回该 mode 下的所有 `HostGroup` 枚举值。

> 不调用 `setMode`，`getHostGroups()` 返回空 `List`——但 `loadEnvResource()` 不会报错，主机能注册，只是后续部署蓝图找不到匹配分组，所有服务/进程都"看不到"主机。

### Step 3：`loadEnvResource()`

1. `hostManager.initHosts()` —— 调 `hostDriver.loadHosts()` 拉清单，逐个 `hostRegister()` 注册成 Spring Bean（bean name = hostname，构造器参数为 hostname + groups）；
2. 遍历所有 `AbstractSdpComponent`（含服务、进程、配置），调用各自的 `loadEnvResource()` 钩子。

> `HostDriver` 若返回空集合，主机管理器仍正常工作，只是 `getHosts()` 为空。

---

## 4. SDP 服务包侧的约定（仅作了解）

`@Sdp` / `@Host` / `@Mode` 注解是 **SDP 服务包开发者** 编写版本包时使用的——作为 Web 集成者，你只需知道：

- 一个 SDP 版本包有自己的 `version`（如 `v5.3.1`）、一个主机代理类（继承 `AbstractHost`）、若干部署模式（如 `主从混合` / `存算分离`）、若干服务/进程/配置定义；
- 上述细节对你透明：`loadSdp(version)` 自动扫描装配，`getModes()` / `getHostGroups()` 返回该版本支持的模式/分组。

若你需要修改 SDP 包内的注解定义（增加服务、改进程启停命令等），请改用 `sdp-core` skill。

---

## 5. 驱动 Bean 概览

> 详细字段语义与覆盖示例见 `driver-overrides.md`。这里仅列出快表，帮助决定 §6 章节里"覆盖哪些"。

| 接口 | 默认实现行为 | 上层必须覆盖 |
|---|---|---|
| `HostDriver` | `loadHosts` 返回空 | ✅ |
| `ResourceDriver` | `isSdpAvailable` 永远 false，`download` no-op | ✅ |
| `RpcDriver` | `execute` 返回空响应 | ✅ |
| `ConfigDriver` | 全部仅打日志 | ✅ |
| `ServeDriver` | 全 no-op | 视需要（落库回调） |
| `BroadcastDriver` | 仅日志 | 视需要 |
| `LogDriver` | 仅日志 | 视需要 |
| `ProcessDriver` | `initHosts` 返回空 | 视需要 |
| `SshDriver` | 全 no-op | 视需要 |
| `WormholeDriver` | 仅日志 | 视需要（默认 OK） |
| `PilotDriver` | 全 no-op，已 `@Deprecated` | ❌ 不要用 |

---

## 6. 易踩坑（针对环境初始化）

1. **`sdp.root.classpath` 注入时机**：必须早于 `SdpEnvManager` Bean 构造。常见做法：
   - `ApplicationContextInitializer` 中设置（最稳）；
   - 或在另一个 `@Configuration` 类里用 `@PostConstruct` 并加 `@DependsOn` 让其先于 SDP 自动配置生效。
2. **多个 SDP 版本共存**：可同时加载多版本元数据，但运行期只能激活一个。
3. **`getVersions()` 与 `isAvailable`**：上层 UI 常用此字段做"版本可激活"开关，默认 `ResourceDriver.isSdpAvailable` 永远 false。务必覆盖 `ResourceDriver`。
4. **重复 `loadSdp` 的副作用**：会 `removeBeanByName` 删除所有 `sdp.root.classpath` 前缀的 Bean——若上层另有业务 Bean 也放在此包路径下，**会被一并删除**。请把上层业务 Bean 放在另一个包。
5. **`loadEnvResource()` 抛错的传染**：`HostDriver.loadHosts()` 抛异常会让整个 Step 3 挂掉，需要 `try/catch` 包裹或确保驱动实现稳健。

---

## 7. 完整端到端样例

```java
// 1. 启动前注入 classpath
public class SdpInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        GalaxySpringUtil.setGlobalArgument("sdp.root.classpath", "io.github.sdp");
    }
}
// META-INF/spring.factories 注册：
// org.springframework.context.ApplicationContextInitializer=com.acme.SdpInitializer

// 2. Spring 启动后激活集群
@Component
@RequiredArgsConstructor
public class SdpAutoActivate implements ApplicationRunner {
    private final SdpEnvManager sdpEnvManager;
    private final ClusterProfileRepo repo;

    @Override
    public void run(ApplicationArguments args) {
        ClusterProfile p = repo.current();
        if (p == null) {
            log.info("集群未激活，等待管理员配置");
            return;
        }
        sdpEnvManager.loadSdp(p.getVersion());
        sdpEnvManager.setMode(p.getMode());
        sdpEnvManager.loadEnvResource();
        log.info("SDP 已激活：version={}, mode={}", p.getVersion(), p.getMode());
    }
}

// 3. 暴露切换接口
@RestController
@RequestMapping("/api/sdp/env")
@RequiredArgsConstructor
public class SdpEnvController {
    private final SdpEnvManager sdpEnvManager;
    private final ClusterProfileRepo repo;
    private static final Object SWITCH_LOCK = new Object();

    @GetMapping("/versions") public List<SdpBaseInfo> versions() { return sdpEnvManager.getVersions(); }
    @GetMapping("/modes")    public List<String> modes()         { return sdpEnvManager.getModes(); }

    @PostMapping("/activate")
    public void activate(@RequestParam String version, @RequestParam String mode) {
        synchronized (SWITCH_LOCK) {           // 防并发切换
            sdpEnvManager.loadSdp(version);
            sdpEnvManager.setMode(mode);
            sdpEnvManager.loadEnvResource();
            repo.save(new ClusterProfile(version, mode));
        }
    }
}
```
