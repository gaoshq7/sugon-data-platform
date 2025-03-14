package cn.gsq.sdp.core;

import cn.gsq.common.config.GalaxySpringUtil;
import cn.gsq.sdp.SdpPropertiesFinal;
import cn.gsq.sdp.core.annotation.*;
import cn.gsq.sdp.core.annotation.Process;
import cn.gsq.sdp.utils.PackageUtils;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.springframework.beans.factory.config.BeanDefinition;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
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

    private final String rootClasspath; // 所有版本sdp的代码根目录

    private final List<String> versions;    // 所有sdp版本号

    private final AbstractSdpManager sdpManager;

    private final AbstractHostManager hostManager;

    protected SdpEnvManager(AbstractSdpManager sdpManager, AbstractHostManager hostManager) {
        try {
            this.rootClasspath = GalaxySpringUtil.getGlobalArgument("sdp.root.classpath").toString();
            if (StrUtil.isBlank(this.rootClasspath)) {
                throw new RuntimeException("全局变量'sdp.root.classpath'不能为空!");
            }
            List<String> classpaths = getAllClasspath(this.rootClasspath);
            this.versions = scan(classpaths);
            this.sdpManager = sdpManager;
            this.hostManager = hostManager;
        } catch (IOException e) {
            log.error("获取SDP版本集合失败 : {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * @Description : 设置SDP基础环境
     * @Note : ⚠️ 使用前必须首先调用该方法 !
     **/
    public void loadSdp(String version) {
        if (!this.versions.contains(version)) {
            throw new RuntimeException("SDP版本不存在：" + version);
        }
        String sdpClasspath = this.rootClasspath + StrUtil.DOT + StrUtil.removeAll(version, StrUtil.DOT);
        // 初始化sdpmanager
        this.sdpManager.setVersion(version);
        this.sdpManager.setHome(SdpPropertiesFinal.SDP_BASE_PATH + StrUtil.SLASH + version);
        // 初始化hostmanager
        this.hostManager.setHostClass(getHostClass(sdpClasspath));
        // 动态加载SDP Bean环境
        GalaxySpringUtil.dynamicLoadPackage(
                sdpClasspath,
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
     * @Description : 获取sdp版本根路径下所有的包名称
     **/
    private List<String> getAllClasspath(String rootPath) throws IOException {
        String classpath = StrUtil.replace(rootPath, StrUtil.DOT, StrUtil.SLASH);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(classpath);
        List<String> directories = CollUtil.newArrayList();
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            File file = new File(url.getFile());
            if (file.exists() && file.isDirectory()) {
                for (File subFile : file.listFiles()) {
                    if (subFile.isDirectory()) {
                        directories.add(rootPath + StrUtil.DOT + subFile.getName());
                    }
                }
            }
        }
        return directories;
    }

    /**
     * @Description : 扫描sdp根路径下所有可用的sdp版本号
     * @Note : ⚠️ 1.扫描规则是包路径中必须含有带@Sdp注解的包元数据信息
     *            2.元数据信息中的版本号与包路径最后一层目录存在去除"."信息后相等的关系
     **/
    private List<String> scan(List<String> classpaths) {
        Function<String, String> select = classpath -> {
            String result = "";
            try {
                Package packet = PackageUtils.getPackage(classpath);
                Sdp sdp = packet.getAnnotation(Sdp.class);
                List<String> pieces = StrUtil.split(classpath, StrUtil.DOT);
                String version = pieces.get(pieces.size() - 1);
                if (sdp != null && version.equals(StrUtil.removeAll(sdp.version(), StrUtil.DOT))) {
                    result = sdp.version();
                } else {
                    log.warn("'{}'类路径不符合SDP版本规范，已被舍弃 ...", classpath);
                }
            } catch (Exception e) {
                log.warn("'{}'类路径无法加载，已被舍弃 ...", classpath);
            }
            return result;
        };
        List<String> versions = CollUtil.map(classpaths, select, true);
        return CollUtil.filter(versions, StrUtil::isNotBlank);
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

}
