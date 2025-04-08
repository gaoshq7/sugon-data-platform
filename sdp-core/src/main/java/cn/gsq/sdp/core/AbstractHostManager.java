package cn.gsq.sdp.core;

import cn.gsq.common.config.GalaxySpringUtil;
import cn.gsq.sdp.HostInfo;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import lombok.Getter;
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

    @Getter
    private String mode;    // 当前主机分组模式

    private final Map<String, List<HostGroup>> modes = MapUtil.newTreeMap(String::compareTo); // 全部主机分组

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
    @Override
    public void addHosts(HostInfo ... hostInfos) {
        registerHost(hostInfos);
    }

    /**
     * @Description : 删除主机
     **/
    @Override
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
    @Override
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
     * @Description : 获取集群部署模式
     **/
    protected List<String> getModes() {
        return this.modes.keySet().stream().sorted().collect(Collectors.toList());
    }

    /**
     * @Description : 获取当前主机分组
     **/
    @Override
    public List<HostGroup> getHostGroups() {
        List<HostGroup> groups;
        if (StrUtil.isBlank(this.mode)) {
            groups = Collections.emptyList();
        } else {
            groups = ListUtil.unmodifiable(this.modes.get(this.mode));
        }
        return groups;
    }

    /**
     * @Description : 根据分组名称获取主机分组
     * @note : ⚠️ 当没有调用过 setMode(String mode) 方法时啥也获取不到 !
     **/
    public HostGroup getHostGroup(String name) {
        return CollUtil.findOne(this.getHostGroups(), g -> g.name().equals(name));
    }

    /**
     * @Description : 获取所有主机
     * @note : ⚠️ 对外不可修改 !
     **/
    @Override
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
    @Override
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
                    } else {
                        log.warn("{}主机已经存在，在删除之前不可重复添加!", hostInfo.getHostname());
                    }
                }
        );
    }

    /**
     * @Description : 设置主机代理
     **/
    protected void setHostClass(Class<? extends AbstractHost> hostClass) {
        this.hostClass = hostClass;
    }

    /**
     * @Description : 重置主机分组策略
     **/
    protected void resetMode() {
        this.mode = null;
        this.modes.clear();
    }

    /**
     * @Description : 设置主机分组模式
     **/
    protected void setMode(String mode) {
        if(!this.modes.containsKey(mode)) {
            throw new RuntimeException("“" + mode + "”分组模式不存在。");
        }
        this.mode = mode;
    }

    /**
     * @Description : 添加主机分组策略
     **/
    protected void addMode(String mode, List<HostGroup> groups) {
        if(CollUtil.isEmpty(groups)) {
            throw new IllegalArgumentException(mode + "模式主机分组列表不可为空。");
        }
        this.modes.put(mode, groups);
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
