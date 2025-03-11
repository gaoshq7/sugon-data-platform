package cn.gsq.sdp.core;

import cn.gsq.common.config.GalaxySpringUtil;
import cn.gsq.sdp.HostInfo;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.AbstractHostManager
 *
 * @author : gsq
 * @date : 2025-03-03 14:42
 * @note : It's not technology, it's art !
 **/
@Slf4j
public abstract class AbstractHostManager extends AbstractBeansAssemble implements HostManager  {

    @Lazy
    @Autowired(required = false)
    private List<AbstractHost> hosts;

    private Class<? extends AbstractHost> hostClass;

    /**
     * @Description : sdp系统启动时加载主机列表
     **/
    protected void initHosts() {
        Set<HostInfo> hostInfos = this.hostDriver.loadHosts();
        if(CollUtil.isNotEmpty(hostInfos))
            hostInfos.forEach(this::registerHost);
    }

    /**
     * @Description : 添加主机
     **/
    public void addHosts(HostInfo ... hostInfos) {
        registerHost(hostInfos);
    }

    /**
     * @Description : 删除主机
     **/
    public void removeHosts(String ... hostnames) {
        boolean flag = this.hostDriver.removeHostsCallback(hostnames);
        if (flag) {
            Arrays.stream(hostnames).forEach(GalaxySpringUtil::removeBeanByName);
        }
    }

    /**
     * @Description : 修改主机分组信息
     * @Note : ⚠️ 整体覆盖 !
     **/
    public void updateHostGroups(String hostname, List<String> groups) {
        boolean flag = this.hostDriver.updateHostGroups(this.getName(), groups);
        if (flag) {
            AbstractHost host = getHostByName(hostname);
            if(host != null) {
                host.updateGroups(groups);
            } else {
                log.error("{}主机不存在!", hostname);
            }
        }
    }

    /**
     * @Description : 获取所有主机
     * @note : ⚠️ 对外不可修改 !
     **/
    public List<AbstractHost> getHosts() {
        List<AbstractHost> hosts = Lists.newArrayList();
        if(!CollUtil.isEmpty(this.hosts)) {
            hosts = GalaxySpringUtil.getBeans(AbstractHost.class)
                    .stream()
                    .sorted(Comparator.comparing(AbstractHost::getName))
                    .collect(Collectors.toList());
        }
        return Collections.unmodifiableList(hosts);
    }

    /**
     * @Description : 根据主机名获取主机
     **/
    public AbstractHost getHostByName(String name) {
        return CollUtil.findOne(hosts, host -> host.getName().equals(name));
    }

    /**
     * @Description : 根据主机名获取期望的类型的主机
     **/
    public <T> T getExpectHostByName(String name) {
        return (T) CollUtil.findOne(hosts, host -> host.getName().equals(name));
    }

    /**
     * @Description : 在bean容器中注册主机
     * @note : ⚠️ manager内部使用 自定义Host实现必须有一个hostname参数的构造器 !
     **/
    private void registerHost(HostInfo ... hostInfos) {
        // 内存中注册主机
        Arrays.stream(hostInfos).forEach(
                hostInfo -> {
                    Object host = GalaxySpringUtil.getBean(hostInfo.getHostname());
                    if(ObjectUtil.isNull(host)) {
                        GalaxySpringUtil.registerBean(
                                hostInfo.getHostname(),
                                this.hostClass,
                                hostInfo.getHostname(),
                                hostInfo.getGroups()
                        );
                        AbstractBeansAssemble bean = GalaxySpringUtil.getBean(hostInfo.getHostname());
                        bean.setDrivers();
                    } else {
                        log.warn("{}主机已经存在，在删除之前不可重复添加!", hostInfo.getHostname());
                    }
                }
        );
    }

    protected void setHostClass(Class<? extends AbstractHost> hostClass) {
        this.hostClass = hostClass;
    }

    @Override
    public String getName() {
        return "主机管理";
    }

    @Override
    public Type getType() {
        return Type.MANAGER;
    }

}
