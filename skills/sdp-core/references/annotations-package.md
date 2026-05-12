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
package io.github.sdp.v531;

import cn.gsq.sdp.core.annotation.Sdp;
```

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
io.github.sdp/
├── v531/package-info.java   // @Sdp(version = "v5.3.1")
├── v540/package-info.java   // @Sdp(version = "v5.4.0")
└── v550/package-info.java   // @Sdp(version = "v5.5.0")
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

每个版本包内**有且只能有一个** `@Host` 注解的类，继承 `AbstractHost`：

```java
package io.github.sdp.v531;

@Host
public class SdpHost531Impl extends AbstractHost {

    public SdpHost531Impl(String hostname, List<String> groups) {
        super(hostname, groups);
    }

    // 按需覆盖父类钩子（可选，几乎不需要）
}
```

### 2.3 强约束

1. **构造器签名必须是 `(String hostname, List<String> groups)`**——`AbstractHostManager.hostRegister()` 通过反射调用此构造器实例化，签名错误时报 `NoSuchMethodException`。
2. **每个版本包仅一个 `@Host`**——多个会触发 `log.warn` 并由 `CollUtil.getFirst` 随机选一个。
3. **不要在 @Host 类内自定义业务字段并通过 `@Autowired` 注入**——主机是动态注册的 Bean（一台主机一个实例），Spring 注入时机晚于 hostRegister，注入时机不可靠。要拿外部服务请用 `GalaxySpringUtil.getBean(...)` 显式取。

### 2.4 自定义业务方法

`AbstractHost` 已提供 `startProcess` / `stopProcess` / `actuator` / `mountDisk` 等通用操作。子类一般只需覆盖一两个钩子（如自定义 `environment()` 增加版本特定的初始化）。可加 `@Function` 方法供 Web 调用。

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

### 3.3 完整示例

```java
package io.github.sdp.v531.mode;

@Mode("主从混合")
public enum MasterSlave implements HostGroup {

    MASTER {
        @Override public int min() { return 2; }
        @Override public int max() { return 2; }
        @Override public String description() { return "运行主控进程的节点"; }
    },

    DATA {
        @Override public int min() { return 3; }
        @Override public int max() { return -1; }
        @Override public String description() { return "运行数据存储进程的节点"; }
    },

    WEB {
        @Override public int min() { return 1; }
        @Override public int max() { return 1; }
        @Override public String description() { return "运行 Web 控制台"; }
    };

    // ⚠️ 不要在这里覆盖 mode() 方法
}
```

### 3.4 强约束

1. **必须是枚举**（`isEnum()` 为 true）——否则 `SdpEnvManager` 扫描时过滤。
2. **必须实现 `HostGroup`** 接口——同上。
3. **不要覆盖 `mode()` 方法**。框架反射逻辑：
   ```java
   String mode = clazz.getAnnotation(Mode.class).value();   // "主从混合"
   group.mode();   // 默认实现也返回 "主从混合"
   ```
   若你覆盖 `mode()` 返回 `"other"`，则该枚举的 group 不会被归到 `"主从混合"` 桶，整个 mode 失效。
4. **`name()` 不要覆盖**——默认由枚举值名自动提供（如 `MASTER`），框架按此名匹配 `HostInfo.groups`。覆盖会让"主机注册了但找不到对应 group"。

### 3.5 多模式共存

一个版本包内可定义多个 `@Mode` 类：

```
io.github.sdp.v531.mode/
├── MasterSlave.java              // @Mode("主从混合")
├── ComputeStorage.java           // @Mode("存算分离")
└── HtapMode.java                 // @Mode("HTAP")
```

Web 端通过 `sdpEnvManager.getModes()` 列出全部，`setMode("主从混合")` 选定。每次 `loadSdp` 后必须重新 `setMode`。

### 3.6 Group 名设计建议

- 大写常量风格（与 Java 枚举一致）：`MASTER` / `DATA` / `WEB`；
- 避免与其它 mode 的 group 名冲突（虽然按 mode 分桶，但 UI 展示时容易混淆）；
- `min` / `max` 要符合实际部署能力：`min = 2, max = 2` 表示固定主备；`min = 3, max = -1` 表示≥3 节点无上限；
- `max = -1` 是无上限的约定，**不要写 `Integer.MAX_VALUE`**。

---

## 4. 包路径规划建议

```
io.<your-org>.<product>/                       ← sdp.root.classpath 指向这里
├── v531/                                       ← 版本包 1
│   ├── package-info.java                       (@Sdp version="v5.3.1")
│   ├── SdpHost531Impl.java                     (@Host)
│   ├── mode/
│   │   ├── MasterSlave.java                    (@Mode)
│   │   └── ComputeStorage.java                 (@Mode)
│   ├── hdfs/
│   ├── spark/
│   └── ...
├── v540/                                       ← 版本包 2
│   ├── package-info.java                       (@Sdp version="v5.4.0")
│   ├── SdpHost540Impl.java
│   └── ...
└── shared/                                     ⚠️ 不要放在这里
```

**⚠️ 注意**：`SdpEnvManager#loadSdp` 在切换版本时会**删除所有以 `sdp.root.classpath` 为前缀的 Spring Bean**，所以：

- 不要把"业务通用 Bean"放在 `sdp.root.classpath` 下；
- 跨版本共享的工具类放在另一个根包（如 `io.<your-org>.shared`）；
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
