# Reference: @Sdp / @Host / @Mode 包级约定

> 本文件是 `sdp-core` skill 的扩展参考。仅在以下场景读取：
> - 新建一个 SDP 版本包（v5.3.1、v5.4.0 等）；
> - 配置 `package-info.java`；
> - 写主机代理类（继承 `AbstractHost`）；
> - 写部署模式枚举（@Mode + 实现 HostGroup）。

---

## 0. 对照源码

- `sdp-core/cn/gsq/sdp/core/annotation/Sdp.java`
- `sdp-core/cn/gsq/sdp/core/annotation/Host.java`
- `sdp-core/cn/gsq/sdp/core/annotation/Mode.java`
- `sdp-core/cn/gsq/sdp/core/HostGroup.java`
- `sdp-core/cn/gsq/sdp/core/AbstractHost.java`

---

## 1. `@Sdp` —— 版本声明

### 1.1 定义

```java
@Target({ElementType.PACKAGE})
public @interface Sdp {
    String version();
}
```

### 1.2 用法

放在版本根包的 `package-info.java` 中：

```java
@Sdp(version = "v5.3.1")
package com.sugon.gsq.libraries.v531;

import cn.gsq.sdp.core.annotation.Sdp;
```

> 包路径可任意（如 `com.sugon.gsq.libraries.v531`、`io.github.sdp.v531`），**关键是末段必须匹配版本号**。

### 1.3 命名约定（强约束）

`version` 去掉所有 `.` 后**必须等于** `package-info.java` 所在包名的最后一段：

| `@Sdp(version)` | 包名末段 | 是否合法 |
|---|---|---|
| `v5.3.1` | `v531` | ✅ |
| `v5.4.0` | `v540` | ✅ |
| `5.3.1` | `531` | ✅（不含 v 前缀也可，只要匹配） |
| `v5.3.1` | `v5_3_1` | ❌ 末段是 `v5_3_1`，去 `.` 后是 `v531`，不匹配 |
| `v5.3.1` | `v53` | ❌ 末段不等 |

若不匹配，`SdpEnvManager` 在扫描时会**静默过滤**，`getVersions()` 不会列出此版本，`loadSdp("v5.3.1")` 抛 `"SDP版本不存在"`。

### 1.4 多版本共存

同一根目录下可有多个版本包，每个一份 `package-info.java`：

```
com.sugon.gsq.libraries/
├── v530/package-info.java   // @Sdp(version = "v5.3.0")
├── v531/package-info.java   // @Sdp(version = "v5.3.1")
├── v532/package-info.java   // @Sdp(version = "v5.3.2")
└── v541/package-info.java   // @Sdp(version = "v5.4.1")
```

Web 端通过 `sdpEnvManager.getVersions()` 列出全部，`loadSdp(version)` 切换激活。

---

## 2. `@Host` —— 主机代理类

### 2.1 定义

```java
@Target({ElementType.TYPE})
public @interface Host { /* 无字段 */ }
```

### 2.2 用法

每个版本包内**有且只能有一个** `@Host` 注解的类，继承 `AbstractHost`。**强约定写法是 `@Host()`（带空括号）**：

```java
package com.sugon.gsq.libraries.v531;

@Host()
@Slf4j
public class SdpHost531Impl extends AbstractHost {

    public SdpHost531Impl(String hostname, List<String> groups) {
        super(hostname, groups);
    }

    @Override
    public void initHost() {
        // 主机加入集群时的版本级初始化（如建立 Kerberos 信任）
        if (sdpManager.getServeByName("Kerberos").isInstalled()) {
            String ldap = CollUtil.getFirst(
                sdpManager.getProcessByName("Slapd").getHosts()).getName();
            this.installKerberos(ldap);
        }
    }

    /* —— 下面是供其它服务调用的业务方法，是真实工程里 @Host 类的主要内容 —— */

    public void uninstallHMaster() { /* 远程脚本：卸载 HBase Master */ }
    public boolean hdfsOpenRanger(String hostname) { /* 给 HDFS 启用 Ranger */ return true; }
    public void createLDAPUser(String username, int uid) { /* ... */ }
    public void installKerberos(String ldapHost) { /* ... */ }
    // ……可有几十个
}
```

### 2.3 强约束

1. **构造器签名必须是 `(String hostname, List<String> groups)`**——`AbstractHostManager.hostRegister()` 通过反射调用此构造器实例化，签名错误时报 `NoSuchMethodException`。
2. **每个版本包仅一个 `@Host`**——多个会触发 `log.warn` 并由 `CollUtil.getFirst` 随机选一个。
3. **`@Autowired` 注入服务接口是允许的且常见**——例如把上层 Web 系统提供的 Ranger / Kerberos 客户端注入到 `@Host` 实现，让远程操作有外部依赖。Spring 在主机 Bean 注册时会完成依赖注入。

### 2.4 `@Host` 类的真实工程用法

`@Host` 类在真实项目里**是版本级业务方法的集合**，远不止是构造器壳子：

| 用途 | 例子 |
|---|---|
| 覆盖 `initHost()` 钩子 | 主机首次加入时做 Kerberos 互信、LDAP 用户创建 |
| 覆盖 `environment(hostname)` 钩子 | 比 `initHost` 更早触发，做包下载前置 |
| 加业务方法供服务类调用 | `uninstallHMaster()` / `installKerberos(ldap)` / `hdfsOpenRanger(rangerHost)` |
| 加 `@Function` 方法 | UI 暴露的主机级运维按钮 |

**调用方式**（在 `AbstractServe` 子类中）：

```java
public class HDFS extends AbstractServe {
    @Override
    protected void initServe(Blueprint.Serve serve) {
        for (AbstractHost host : sdpManager.getHostManager().getHosts()) {
            // 取到带具体类型的 host，调用版本专用业务方法
            SdpHost531Impl impl = this.sdpManager.getExpectHostByName(host.getName());
            impl.createLDAPUser("hdfs", 9001);
        }
    }
}
```

### 2.5 `AbstractHost` 主要可覆盖钩子

| 钩子 | 触发时机 | 典型用途 |
|---|---|---|
| `environment(String hostname)` | 主机环境初始化最早阶段（`hostEnvInit` 中调用） | 通常无需覆盖，默认实现会下载必要安装包 |
| `initHost()` | `environment` 末尾调用 | 主机首次加入集群时的版本特定初始化 |
| `loadEnvResource()` | `loadEnvResource` 阶段（所有组件遍历钩子） | 加载主机本地资源 |
| `recover()` | 主机被移除时 | 清理本机残留 |
| `updateGroups(List<String>)` | 分组变更后 | 同步分组到本地状态 |

> 钩子签名可参见 `cn.gsq.sdp.core.AbstractHost`。`initHost` 是最常见的覆盖点。

---

## 3. `@Mode` —— 部署模式枚举

### 3.1 定义

```java
@Target({ElementType.TYPE})
public @interface Mode {
    String value();   // 模式显示名（中文友好）
}
```

### 3.2 `HostGroup` 接口

`@Mode` 标注的枚举类必须实现 `HostGroup`：

```java
public interface HostGroup {
    String name();          // 由枚举值名自动提供（如 "MASTER"）
    int min();              // 最小部署节点数
    int max();              // 最大节点数（-1 表示无上限）
    String description();
    String mode();          // 由 @Mode("XXX") 注解反射读取，⚠️ 不要覆盖
}
```

### 3.3 完整示例（galaxy-libraries 真实风格，Lombok 紧凑写法）

工程偏好用 Lombok `@AllArgsConstructor` + 实例字段，比匿名内部类紧凑得多：

```java
package com.sugon.gsq.libraries.v531;

@Mode("存算分离")
@AllArgsConstructor
public enum SCIsolateMode implements HostGroup {

    MASTER(2, 2, "运行与使用终端交互的服务主进程"),
    COMMON(3, -1, "运行分布式元数据服务进程"),
    WEB(1, 1, "运行组件的页面终端服务进程"),
    DATA(3, -1, "运行数据存储进程"),
    TASK(3, -1, "运行数据计算进程"),
    HTAP(3, -1, "运行 Doris 服务计算存储进程"),
    OLAP(2, -1, "运行 Presto 服务计算进程");

    private final int min;
    private final int max;
    private final String description;

    @Override public int min()              { return this.min; }
    @Override public int max()              { return this.max; }
    @Override public String description()   { return this.description; }

    // ⚠️ 不要覆盖 mode()，默认实现会反射读取 @Mode("存算分离") 的值
}
```

> 匿名内部类风格（每个枚举值各自覆盖 `min/max/description`）也合法，但工程上**强烈推荐 Lombok 风格**——一目了然且不易写漏字段。

### 3.4 放置位置

`@Mode` 类**可以直接放在版本根包**，与 `@Host` 类同级（如 galaxy-libraries 的 `v531/SCIsolateMode.java`）。**不强制放在 `mode/` 子包**——子包只是个人组织偏好。

### 3.5 强约束

1. **必须是枚举**（`isEnum()` 为 true）——否则 `SdpEnvManager` 扫描时过滤。
2. **必须实现 `HostGroup`** 接口——同上。
3. **不要覆盖 `mode()` 方法**。框架反射逻辑：
   ```java
   String mode = clazz.getAnnotation(Mode.class).value();   // "主从混合"
   group.mode();   // 默认实现也返回 "主从混合"
   ```
   若你覆盖 `mode()` 返回 `"other"`，则该枚举的 group 不会被归到 `"主从混合"` 桶，整个 mode 失效。
4. **`name()` 不要覆盖**——默认由枚举值名自动提供（如 `MASTER`），框架按此名匹配 `HostInfo.groups`。覆盖会让"主机注册了但找不到对应 group"。

### 3.6 多模式共存

一个版本包内可定义多个 `@Mode` 类：

```
com.sugon.gsq.libraries.v531/
├── SCIsolateMode.java            // @Mode("存算分离")
├── MasterSlaveMode.java          // @Mode("主从混合")
└── HtapMode.java                 // @Mode("HTAP")
```

Web 端通过 `sdpEnvManager.getModes()` 列出全部，`setMode("主从混合")` 选定。每次 `loadSdp` 后必须重新 `setMode`。

### 3.7 Group 名设计建议

- 大写常量风格（与 Java 枚举一致）：`MASTER` / `DATA` / `WEB`；
- 避免与其它 mode 的 group 名冲突（虽然按 mode 分桶，但 UI 展示时容易混淆）；
- `min` / `max` 要符合实际部署能力：`min = 2, max = 2` 表示固定主备；`min = 3, max = -1` 表示≥3 节点无上限；
- `max = -1` 是无上限的约定，**不要写 `Integer.MAX_VALUE`**。

---

## 4. 包路径规划建议（galaxy-libraries 真实结构）

```
com.sugon.gsq.libraries/                       ← sdp.root.classpath 指向这里
├── v530/                                       ← 版本包
│   ├── package-info.java                       (@Sdp version="v5.3.0")
│   ├── SdpHost530Impl.java                     (@Host)
│   ├── MasterSlaveMode.java                    (@Mode) —— 与 @Host 同级
│   ├── SCIsolateMode.java                      (@Mode)
│   ├── hdfs/                                   ← 服务包用小写
│   │   ├── HDFS.java                           (@Serve) —— 服务类名遵循官方组件命名
│   │   ├── config/
│   │   │   ├── CoreSiteXml.java                (@Config)
│   │   │   └── HdfsSiteXml.java
│   │   └── process/
│   │       ├── NameNode.java                   (@Process)
│   │       ├── DataNode.java
│   │       ├── JournalNode.java
│   │       └── Zkfc.java
│   ├── prestosql/
│   │   └── PrestoSQL.java                      ← 类名 PrestoSQL，与 resources/PrestoSQL/ 对应
│   └── ...
├── v531/
├── v532/
├── v541/
├── utils/                                      ← 跨版本共享的工具类
│   └── HostUtil.java
└── exception/                                  ← 跨版本共享的异常类
    └── ScriptRunningException.java
```

**命名约定（与 galaxy-libraries 一致）：**

- **Java 子包用小写**：`v531/hdfs/`、`v531/prestosql/`；
- **类名遵循官方组件命名**：`HDFS`（缩写全大写）/ `PrestoSQL` / `Zookeeper`，而非 `Hdfs` / `Prestosql`；
- **resources 子目录名必须与 `@Serve` 类的简单类名完全一致**：`@Serve class HDFS` → `resources/v531/HDFS/`；
- **跨版本共享的工具/异常类放在另一个根包**（`utils/` / `exception/`），与 `sdp.root.classpath` 指向的版本根包平级 —— 避免 `loadSdp` 切换版本时被 `removeBeanByName` 误删。

**⚠️ 注意**：`SdpEnvManager#loadSdp` 在切换版本时会**删除所有以 `sdp.root.classpath` 为前缀的 Spring Bean**，所以：

- 不要把"业务通用 Bean"放在 `sdp.root.classpath` 下；
- 跨版本共享的工具类放在另一个根包（如 `com.sugon.gsq.libraries.utils`）；
- 版本包之间不要互相 import（虽然语法允许，但运行期切换会让其它版本的 Bean 不存在）。

---

## 5. 易踩坑（针对包级约定）

1. **`@Sdp.version` 与包名末段不匹配** → 版本静默丢失。debug 时打 `SdpEnvManager` 的 DEBUG 日志，看 "获取到 N 个 SDP 环境元数据信息" 是否包含你的版本。
2. **包名含下划线 `_`** → 不影响扫描，但建议用纯字母（`v531` 而非 `v5_3_1`）。
3. **`@Host` 类的构造器没用 `super(hostname, groups)`** → 实例字段 hostname/groups 为 null，所有远程操作失败。
4. **`@Mode` 枚举值用小写或带空格** → 枚举值名作为 group 名要符合 Java 约定，**不能含特殊字符或空格**，且通常用大写常量风格。
5. **多个 `@Mode` 枚举共用同一个 `@Mode("XXX")` value** → 同一 mode 桶会包含两个枚举的所有 group，可能导致 group 名冲突；建议一个 mode 一个枚举类。
6. **HostGroup 的 `min` > `max`**（且 max != -1）→ 框架不校验，但 Web 端做"主机数检查"时会永远不通过。
7. **业务 Bean 错放在 `sdp.root.classpath` 下** → `loadSdp` 切换版本时被静默删除，行为难复现。
