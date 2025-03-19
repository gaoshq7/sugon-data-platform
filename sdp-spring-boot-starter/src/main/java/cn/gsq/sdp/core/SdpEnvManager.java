package cn.gsq.sdp.core;

import cn.gsq.common.config.GalaxySpringUtil;
import cn.gsq.sdp.SdpPropertiesFinal;
import cn.gsq.sdp.core.annotation.*;
import cn.gsq.sdp.core.annotation.Process;
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
import org.springframework.beans.factory.config.BeanDefinition;

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
    public List<String> getVersions() {
        List<String> versions = CollUtil.map(this.sdpMetas, SdpMeta::getVersion, true);
        return ListUtil.unmodifiable(versions);
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
        this.sdpManager.setVersion(version);
        this.sdpManager.setHome(SdpPropertiesFinal.SDP_BASE_PATH + StrUtil.SLASH + version);
        // 初始化hostmanager
        this.hostManager.setHostClass(getHostClass(meta.getClasspath()));
        // 动态加载SDP Bean环境
        GalaxySpringUtil.dynamicLoadPackage(
                meta.getClasspath(),
                BeanDefinition::getBeanClassName
        );
        // 初始化Beans
        initialize();
    }

    /**
     * @Description : 初始化SDP环境
     **/
    private void initialize() {
        // 初始化所有依赖关系
        List<AbstractBeansAssemble> beans = GalaxySpringUtil.getBeans(AbstractBeansAssemble.class);
        for (AbstractBeansAssemble bean : beans) {
            bean.setDrivers();
        }
        // 加载主机列表
        this.hostManager.initHosts();
        // 初始化配置文件
        this.sdpManager.setConfigs(initConfigs());
        // 配置文件排序
        this.sdpManager.configs.forEach((key, value) ->
                this.sdpManager.configs.put(key, value.stream()
                        .sorted(Comparator.comparing(AbstractConfig::getOrder))
                        .collect(Collectors.toList()))
        );
        // 初始化进程
        this.sdpManager.setProcesses(initProcesses());
        // 进程排序
        this.sdpManager.processes.forEach((key, value) ->
                this.sdpManager.processes.put(key, value.stream()
                        .sorted(Comparator.comparing(AbstractProcess::getOrder))
                        .collect(Collectors.toList()))
        );
        // 初始化服务列表
        this.sdpManager.serves = initServes()
                .stream()
                .sorted(Comparator.comparing(AbstractServe::getOrder))
                .collect(Collectors.toList());
    }

    /**
     * @Description : 初始化配置文件
     **/
    private Map<AbstractServe, List<AbstractConfig>> initConfigs() {
        return GalaxySpringUtil.getBeanNamesByAnno(Config.class)
                .stream()
                .map(object -> {
                    AbstractConfig config = (AbstractConfig) object;
                    config.initProperty();
                    return config;
                })
                .collect(Collectors.groupingBy(AbstractConfig::getServe));
    }

    /**
     * @Description : 初始化进程
     **/
    private Map<AbstractServe, List<AbstractProcess<AbstractHost>>> initProcesses() {
        return GalaxySpringUtil.getBeanNamesByAnno(Process.class)
                .stream()
                .map(object -> {
                    AbstractProcess<AbstractHost> process = (AbstractProcess) object;
                    process.initProperty();
                    return process;
                })
                .collect(Collectors.groupingBy(AbstractProcess::getServe));
    }

    /**
     * @Description : 初始化服务
     **/
    private List<AbstractServe> initServes() {
        return GalaxySpringUtil.getBeanNamesByAnno(Serve.class)
                .stream()
                .map(object -> {
                    AbstractServe serve = (AbstractServe) object;
                    serve.initProperty();
                    return serve;
                })
                .collect(Collectors.toList());
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

    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SdpMeta {

        private String version;

        private String classpath;

    }

}
