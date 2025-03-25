package cn.gsq.sdp.core;

import cn.gsq.sdp.*;
import cn.gsq.sdp.driver.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Project : galaxy
 * Class : cn.gsq.rpc.config.RpcAutoConfigure
 *
 * @author : gsq
 * @date : 2021-04-08 10:44
 * @note : It's not technology, it's art !
 **/
@Slf4j
@Configuration
public class SdpAutoConfigure {

    /**
     * @Description : 注入SdpManager
    **/
    @Bean
    public AbstractSdpManager SdpManager() {
        return new AbstractSdpManager() {};
    }

    /**
     * @Description : 注入HostManager
     **/
    @Bean
    public AbstractHostManager HostManager() {
        return new AbstractHostManager() {};
    }

    /**
     * @Description : 注入SdpEnvManager
     **/
    @Bean
    public SdpEnvManager SdpEnvManager(@Autowired AbstractSdpManager sdpManager, @Autowired AbstractHostManager hostManager) {
        return new SdpEnvManager(sdpManager, hostManager);
    }

    @Bean
    @ConditionalOnMissingBean(BroadcastDriver.class)
    public BroadcastDriver getBroadcastDriver() {
        log.warn("未定义集群广播驱动：BroadcastDriver，将采用默认策略。");
        return (hostname, appEvent, serveName, msg) -> {
            log.info("向{}主机发送{}服务{}信息：{}", hostname, serveName, appEvent.getCnname(), msg);
        };
    }

    @Bean
    @ConditionalOnMissingBean(ConfigDriver.class)
    public ConfigDriver getConfigDriver() {
        log.warn("未定义配置文件驱动：ConfigDriver，将采用默认策略。");
        return new ConfigDriver() {
            @Override
            public void conform(ConfigBranch branch, List<ConfigItem> items) {
                log.info("同步{}配置文件的{}分支", branch.getConfigName(), branch.getBranchName());
            }

            @Override
            public List<ConfigItem> loadConfigItems(ConfigBranch branch) {
                log.info("装载{}配置文件的{}分支", branch.getConfigName(), branch.getBranchName());
                return Collections.emptyList();
            }

            @Override
            public void extendBranchHosts(ConfigBranch branch, Set<String> hostnames) {

            }

            @Override
            public void abandonBranchHosts(ConfigBranch branch, Set<String> hostnames) {

            }

            @Override
            public void destroy(ConfigBranch branch) {
                log.info("销毁{}配置文件的{}分支", branch.getConfigName(), branch.getBranchName());
            }

            @Override
            public ConfigItem getItemMetadata(ConfigBranch branch, String key) {
                return new ConfigItem();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(HostDriver.class)
    public HostDriver getHostDriver() {
        log.warn("未定义主机管理驱动：HostDriver，将采用默认策略。");
        return new HostDriver() {
            @Override
            public Set<HostInfo> loadHosts() {
                log.info("装载了一套空主机列表");
                return Collections.emptySet();
            }

            @Override
            public boolean removeHostsCallback(String... hostnames) {
                return true;
            }

            @Override
            public boolean updateHostGroups(String hostname, List<String> groups) {
                return true;
            }

            @Override
            public boolean isExist(String hostname) {
                return true;
            }

            @Override
            public boolean isAlive(String hostname) {
                return true;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(LogDriver.class)
    public LogDriver getLogDriver() {
        log.warn("未定义日志输出驱动：LogDriver，将采用默认策略。");
        return (level, msg) -> {
            log.info("输出{}日志: {}", level.getName(), msg);
        };
    }

    @Bean
    @ConditionalOnMissingBean(PilotDriver.class)
    public PilotDriver getPilotDriver() {
        log.warn("未定义Pilot驱动：PilotDriver，将采用默认策略。");
        return new PilotDriver() {
            @Override
            public void startPilot(String hostname) {

            }

            @Override
            public void stopPilot(String hostname) {

            }

            @Override
            public boolean isActive(String hostname) {
                return false;
            }

            @Override
            public void startAgent(String hostname) {

            }

            @Override
            public void stopAgent(String hostname) {

            }

            @Override
            public void restartAgent(String hostname) {

            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ProcessDriver.class)
    public ProcessDriver getProcessDriver() {
        log.warn("未定义进程管理驱动：ProcessDriver，将采用默认策略。");
        return new ProcessDriver() {
            @Override
            public List<String> initHosts(String processname) {
                log.info("{}进程装载了一个空主机列表", processname);
                return Collections.emptyList();
            }

            @Override
            public void addHosts(String processname, Collection<String> hostnames) {

            }

            @Override
            public void delHosts(String processname, Collection<String> hostnames) {

            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(RpcDriver.class)
    public RpcDriver getRpcDriver() {
        log.warn("未定义RPC通信驱动：RpcDriver，将采用默认策略。");
        return new RpcDriver() {
            @Override
            public RpcRespond<String> execute(RpcRequest rpcRequest) {
                return new RpcRespond<String>() {};
            }

            @Override
            public void addUser(RpcRequest rpcRequest, String username, String uuid) {

            }

            @Override
            public void delUser(RpcRequest rpcRequest, String username) {

            }

            @Override
            public void createKeytab(String masterHost, String username, String hostname, String uid) {

            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ServeDriver.class)
    public ServeDriver getServeDriver() {
        log.warn("未定义服务管理驱动：ServeDriver，将采用默认策略。");
        return new ServeDriver() {
            @Override
            public void receiptInstallServe(Blueprint.Serve serve) {

            }

            @Override
            public void receiptUninstallServe(String serve) {

            }

            @Override
            public void updateResourcePlan(String serve) {

            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(SshDriver.class)
    public SshDriver getSshDriver() {
        log.warn("未定义SSH服务驱动：SshDriver，将采用默认策略。");
        return new SshDriver() {
            @Override
            public void startAgent(String hostname) {

            }

            @Override
            public void stopAgent(String hostname) {

            }

            @Override
            public void addHost(SshInfo info) throws Exception {

            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ResourceDriver.class)
    public ResourceDriver getResourceDriver() {
        log.warn("未定义安装包下载服务驱动：ResourceDriver，将采用默认策略。");
        return resource -> {

        };
    }

}
