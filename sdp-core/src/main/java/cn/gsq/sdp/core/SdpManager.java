package cn.gsq.sdp.core;

import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.ObjectUtil;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.SdpManager
 *
 * @author : gsq
 * @date : 2025-02-28 11:28
 * @note : It's not technology, it's art !
 **/
public interface SdpManager {

    /**
     * @Description : 获取当前sdp版本号
     **/
    String getVersion();

    /**
     * @Description : 获取当前版本sdp包所在根路径
     **/
    String getHome();

    /**
     * @Description : 获取主机管理员实例
     **/
    AbstractHostManager getHostManager();

    /**
     * @Description : 获取所有服务
     **/
    List<AbstractServe> getServes();

    /**
     * @Description : 根据服务名称获取服务实例
     **/
    default AbstractServe getServeByName(String servename) {
        return getServes()
                .stream()
                .filter(serve -> serve.getName().equals(servename))
                .findFirst()
                .orElse(null);
    }

    /**
     * @Description : 根据服务名称获取依赖服务集合
     **/
    default List<AbstractServe> getParents(String servename) {
        AbstractServe serve = getServeByName(servename);
        return ObjectUtil.isNotEmpty(serve) ? serve.getParents() : null;
    }

    /**
     * @Description : 根据服务名称获取被依赖服务集合
     **/
    default List<AbstractServe> getChildren(String servename) {
        AbstractServe serve = getServeByName(servename);
        return ObjectUtil.isNotEmpty(serve) ? serve.getChildren() : null;
    }

    /**
     * @Description : 根据进程名称获取进程实例
     **/
    default AbstractProcess<AbstractHost> getProcessByName(String processname) {
        return getServes()
                .stream()
                .flatMap(serve -> serve.getProcesses().stream())
                .filter(process -> process.getName().equals(processname))
                .findFirst()
                .orElse(null);
    }

    /**
     * @Description : 根据配置文件名称获取配置文件实例
     **/
    default AbstractConfig getConfigByName(String servename, String configName) {
        AbstractConfig config = null;
        AbstractServe serve = getServes().stream()
                .filter(s -> s.getName().equals(servename)).findFirst().orElse(null);
        if(ObjectUtil.isNotNull(serve)){
            config = serve.getAllConfigs().stream()
                    .filter(c -> c.getName().equals(configName)).findFirst().orElse(null);
        }
        return config;
    }

    /**
     * @Description : 根据主机名获取主机
     **/
    default AbstractHost getHostByName(String hostname) {
        return getHostManager().getHostByName(hostname);
    }

    /**
     * @Description : 根据主机名获取期望类型的主机
     **/
    default <T> T getExpectHostByName(String hostname) {
        return getHostManager().getExpectHostByName(hostname);
    }

    /**
     * @Description : 获取以主机名为键，运行进程名称集合为值的Map
     **/
    default Map<String, List<String>> getHostsMapping() {
        return getServes()
                .stream()
                .flatMap(serve -> serve.getProcesses().stream())
                .flatMap(process -> process.getHosts().stream().map(host -> Pair.of(process.getName(), host.getName())))
                .collect(
                        Collectors.groupingBy(
                                Pair::getValue,
                                Collectors.mapping(
                                        Pair::getKey,
                                        Collectors.toList()
                                )
                        )
                );
    }

    /**
     * @Description : 根据主机名获取运行的进程名集合
     **/
    default List<String> getProcessNamesByHostname(String hostname) {
        List<String> processes = getHostsMapping().get(hostname);
        return processes == null ? Lists.newArrayList() : processes;
    }

    /**
     * @Description : 根据主机名获取运行的进程实例集合
     **/
    default List<AbstractProcess<AbstractHost>> getProcessModelsByHostname(String hostname) {
        List<String> processes = getProcessNamesByHostname(hostname);
        return processes
                .stream()
                .map(this::getProcessByName)
                .collect(Collectors.toList());
    }

    /**
     * @Description : 根据主机名判断当前主机是否能从集群中移除
     */
    default boolean isHostCanRemove(String hostname) {
        List<AbstractProcess<AbstractHost>> list = getProcessModelsByHostname(hostname)
                .stream()
                .filter(AbstractProcess::isAvailable)
                .collect(Collectors.toList());
        return list.isEmpty();
    }

}
