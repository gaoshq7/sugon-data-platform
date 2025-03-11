## 🔧 使用方法

- 在Spring boot主类中添加@SdpScan注解，value是SDP代码扫描根路径。
- 在系统环境变量中添加要使用的SDP版本号（例如：SDP=v5.3.1）。
- 启动代码：

    ```shell
        SdpEnvBuilder builder = new SdpEnvBuilder(主类.class);
        builder.loadSdpEnvironment().run(args);
    ```
## 🔔️ 特别提醒

- 启动类必须添加@SdpScan注解。
- 环境变量中必须有SDP版本号。
- 启动代码必须按要求填写。
- SDP包路径拼接规则是@SdpScan的value值 + 去掉.的版本号。
- <font color="red">**@SpringBootApplication注解中必须加scanBasePackages属性！！！**</font>