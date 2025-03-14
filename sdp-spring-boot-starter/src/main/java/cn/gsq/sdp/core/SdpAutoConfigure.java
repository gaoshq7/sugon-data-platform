package cn.gsq.sdp.core;

import cn.gsq.sdp.*;
import cn.gsq.sdp.driver.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
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
        return (hostname, appEvent, serveName, msg) -> {

        };
    }

    @Bean
    @ConditionalOnMissingBean(ConfigDriver.class)
    public ConfigDriver getConfigDriver() {
        return new ConfigDriver() {
            @Override
            public void conform(ConfigBranch branch, List<ConfigItem> items) {

            }

            @Override
            public List<ConfigItem> loadConfigItems(ConfigBranch branch) {
                return List.of();
            }

            @Override
            public void extendBranchHosts(ConfigBranch branch, Set<String> hostnames) {

            }

            @Override
            public void abandonBranchHosts(ConfigBranch branch, Set<String> hostnames) {

            }

            @Override
            public void destroy(ConfigBranch branch) {

            }

            @Override
            public ConfigItem getItemMetadata(ConfigBranch branch, String key) {
                return null;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(HostDriver.class)
    public HostDriver getHostDriver() {
        return new HostDriver() {
            @Override
            public Set<HostInfo> loadHosts() {
                return Set.of();
            }

            @Override
            public boolean removeHostsCallback(String... hostnames) {
                return false;
            }

            @Override
            public boolean updateHostGroups(String hostname, List<String> groups) {
                return false;
            }

            @Override
            public boolean isExist(String hostname) {
                return false;
            }

            @Override
            public boolean isAlive(String hostname) {
                return false;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(LogDriver.class)
    public LogDriver getLogDriver() {
        return (level, msg) -> {

        };
    }

    @Bean
    @ConditionalOnMissingBean(PilotDriver.class)
    public PilotDriver getPilotDriver() {
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
        return new ProcessDriver() {
            @Override
            public List<String> initHosts(String processname) {
                return List.of();
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
        return new RpcDriver() {
            @Override
            public RpcRespond<String> execute(RpcRequest rpcRequest) {
                return null;
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

}
