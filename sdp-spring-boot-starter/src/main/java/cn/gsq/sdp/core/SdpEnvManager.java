package cn.gsq.sdp.core;

import cn.gsq.common.config.GalaxySpringUtil;
import cn.gsq.sdp.SdpBaseInfo;
import cn.gsq.sdp.SdpPropertiesFinal;
import cn.gsq.sdp.core.annotation.*;
import cn.gsq.sdp.driver.ResourceDriver;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.SdpEnvManager
 *
 * @author : gsq
 * @date : 2025-03-07 15:09
 * @note : It's not technology, it's art !
 **/
@Slf4j
@Getter
public class SdpEnvManager {

    private final List<SdpMeta> sdpMetas;    // 所有sdp版本号

    private final AbstractSdpManager sdpManager;

    private final AbstractHostManager hostManager;

    @Autowired
    private ApplicationContext applicationContext;

    protected SdpEnvManager(AbstractSdpManager sdpManager, AbstractHostManager hostManager) {
        try {
            String rootClasspath = GalaxySpringUtil.getGlobalArgument("sdp.root.classpath").toString();
            if (StrUtil.isBlank(rootClasspath)) {
                throw new RuntimeException("全局变量'sdp.root.classpath'不能为空!");
            }
            log.debug("SDP版本扫描根目录：{}", rootClasspath);
            this.sdpMetas = getSdpVersions(rootClasspath);
            this.sdpMetas.forEach(meta -> log.debug("SDP版本{}扫描成功，版本实例所在根目录：{}", meta.getVersion(), meta.getClasspath()));
            this.sdpManager = sdpManager;
            this.hostManager = hostManager;
        } catch (IOException e) {
            log.error("获取SDP版本集合失败 : {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * @Description : 获取版本号列表
     **/
    public List<SdpBaseInfo> getVersions() {
        ResourceDriver driver = GalaxySpringUtil.getBean(ResourceDriver.class);
        List<SdpBaseInfo> versions = CollUtil.map(
                this.sdpMetas,
                meta -> new SdpBaseInfo(meta.getVersion(), driver.isSdpAvailable(meta.getVersion())),
                true
        );
        return ListUtil.unmodifiable(versions);
    }

    /**
     * @Description : 获取主机分组模式列表
     **/
    public List<String> getModes() {
        return ListUtil.unmodifiable(this.hostManager.getModes());
    }

    /**
     * @Description : 设置SDP基础环境
     * @Note : ⚠️ 使用前必须首先调用该方法 !
     **/
    public void loadSdp(String version) {
        SdpMeta meta = CollUtil.findOne(this.sdpMetas, m -> m.getVersion().equals(version));
        if (ObjectUtil.isNull(meta)) {
            throw new RuntimeException("SDP版本不存在：" + version);
        }
        // 初始化sdpmanager
        this.sdpManager.setDrivers();
        this.sdpManager.setVersion(version);
        this.sdpManager.setHome(SdpPropertiesFinal.SDP_BASE_PATH + StrUtil.SLASH + version);
        // 初始化hostmanager主机代理类
        this.hostManager.setDrivers();
        this.hostManager.setHostClass(getHostClass(meta.getClasspath()));
        this.hostManager.resetMode();
        List<HostGroup> groups = getAllGroups(meta.getClasspath());
        Map<String, List<HostGroup>> gmap = groups.stream().collect(Collectors.groupingBy(HostGroup::mode));
        gmap.forEach(this.hostManager::addMode);
        // 初始化SDP服务组件
        ApplicationContext context = GalaxySpringUtil.getContext();
        if(ObjectUtil.isEmpty(context)) {
            GalaxySpringUtil.updateApplicationContext(this.applicationContext);
        }
        // 删除已存在的libraries下所有bean
        String path = (String) GalaxySpringUtil.getGlobalArgument("sdp.root.classpath");
        String[] beanNames = GalaxySpringUtil.getContext().getBeanDefinitionNames();
        for (String name : beanNames) {
            if(name.startsWith(path))
                GalaxySpringUtil.removeBeanByName(name);
        }
        // 加载SDP所有组件
        GalaxySpringUtil.dynamicLoadPackage(
                meta.getClasspath(),
                BeanDefinition::getBeanClassName
        );
        // 初始化所有sdp组件的固有属性
        List<AbstractSdpComponent> components = GalaxySpringUtil.getBeans(AbstractSdpComponent.class);
        for (AbstractSdpComponent component : components) {
            component.initProperty();
        }
        // 装配 SdpManager
        this.sdpManager.setServes(
                GalaxySpringUtil.getBeanNamesByAnno(Serve.class)
                        .stream()
                        .map(object -> (AbstractServe) object)
                        .sorted(Comparator.comparing(AbstractServe::getOrder))
                        .collect(Collectors.toList())
        );
    }

    /**
     * @Description : 设置主机分组模式
     **/
    public void setMode(String mode) {
        this.hostManager.setMode(mode);
    }

    /**
     * @Description : 加载集群环境资源
     * @Note : ⚠️ 使用前必须先调用“setMode”方法 !
     **/
    public void loadEnvResource() {
        // 加载所有主机实例
        this.hostManager.initHosts();
        List<AbstractSdpComponent> components = GalaxySpringUtil.getBeans(AbstractSdpComponent.class);
        for (AbstractSdpComponent component : components) {
            component.loadEnvResource();
        }
    }

    /**
     * @Description : 扫描sdp代码根目录下有效的sdp版本号
     * @Note : ⚠️ 判断规则：@Sdp注解版本号去掉“.”与@Sdp注解所在最后一层目录相同 !
     **/
    private List<SdpMeta> getSdpVersions(String rootPath) throws IOException {
        Reflections reflections = new Reflections(rootPath);
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(Sdp.class);
        log.debug("获取到{}个SDP环境元数据信息：{}", classes.size(), ArrayUtil.toString(classes));
        return CollUtil.filter(
                classes,
                c -> {
                        List<String> slices = StrUtil.split(c.getPackage().getName(), StrUtil.DOT);
                        String version = slices.get(slices.size() - 1);
                        return StrUtil.removeAll(c.getAnnotation(Sdp.class).version(), StrUtil.DOT)
                                .equals(version);
                     }
                ).stream()
                 .map(c -> new SdpMeta(c.getAnnotation(Sdp.class).version(), c.getPackage().getName()))
                 .collect(Collectors.toList());
    }

    /**
     * @Description : 获取主机类
     **/
    private Class<? extends AbstractHost> getHostClass(String classpath) {
        Reflections reflections = new Reflections(classpath);
        Set<Class<?>> classes = CollUtil.filter(reflections.getTypesAnnotatedWith(Host.class),
                AbstractHost.class::isAssignableFrom);
        if(classes.size() == 1) {
            log.debug("成功加载主机代理类 : {}", CollUtil.getFirst(classes).getName());
        } else if (classes.size() > 1) {
            log.warn("SDP环境存在多个主机代理 : {}", Convert.toStr(CollUtil.map(classes, Class::getName, true)));
        } else {
            log.error("加载主机代理失败 : {}", "SDP环境中不存在主机代理类 ... ");
        }
        return (Class<? extends AbstractHost>) CollUtil.getFirst(classes);
    }

    /**
     * @Description : 获取所有模式下的主机分组
     **/
    private List<HostGroup> getAllGroups(String classpath) {
        List<HostGroup> groups = CollUtil.newArrayList();
        Reflections reflections = new Reflections(classpath);
        Set<Class<?>> classes = CollUtil.filter(
                reflections.getTypesAnnotatedWith(Mode.class),
                c -> c.isEnum() && HostGroup.class.isAssignableFrom(c)
        );
        for (Class<?> aClass : classes) {
            Object[] group = aClass.getEnumConstants();
            for (Object o : group) {
                groups.add((HostGroup) o);
            }
        }
        return groups;
    }

    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SdpMeta {

        private String version;

        private String classpath;

    }

}
