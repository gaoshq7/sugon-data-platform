package cn.gsq.sdp.core;

import cn.gsq.common.config.GalaxySpringUtil;
import cn.gsq.sdp.*;
import cn.gsq.sdp.core.annotation.*;
import cn.gsq.sdp.core.annotation.Process;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.AbstractProcess
 *
 * @author : gsq
 * @date : 2025-02-28 11:03
 * @note : It's not technology, it's art !
 **/
@Slf4j
@Getter
public abstract class AbstractProcess<T extends AbstractHost> extends AbstractApp {

    private transient AbstractServe serve;    // 所属服务

    private final transient ProcessHandler handler; // 逻辑处理

    private final String mark;    // 进程唯一标识

    private final String start;   // 启动命令

    private final String stop;   // 停止命令

    private final boolean dynamic; // 是否可以扩容/缩容

    private final int order;    // 拍序

    private final int min;      // 最小部署数量

    private final int max;      // 最大部署数量

    private Logger logger;      // 日志处理类

    private String home;    // 命令执行根目录

    protected List<T> hosts = CollUtil.newArrayList();     // 进程所在主机集合

    protected List<HostGroup> groups = CollUtil.newArrayList(); // 不同模式下进程所在主机分组集合

    protected Set<AbstractProcess<AbstractHost>> companions = CollUtil.newHashSet();    // 伴生进程

    protected Set<AbstractProcess<AbstractHost>> excludes = CollUtil.newHashSet();    // 互斥进程

    protected AbstractProcess() {
        Process process = this.getClass().getAnnotation(Process.class);
        this.handler = process.handler();
        // 初始化主机分组信息
        for (Group group : process.groups()) {
            Class clazz = group.mode();
            try {
                HostGroup hostGroup = (HostGroup) EnumUtil.fromString(clazz, group.name());
                this.groups.add(hostGroup);
            } catch (Exception e) {
                log.error("“{}”主机分组不存在。", group.name(), e);
            }
        }
        this.mark = process.mark();
        this.start = process.start();
        this.stop = process.stop();
        this.dynamic = process.dynamic();
        this.order = process.order();
        this.min = process.min();
        this.max = process.max();
        this.description = process.description();
    }

    /**
     * @Description : 系统启动时初始化进程属性
     **/
    @Override
    public void initProperty() {
        Process process = this.getClass().getAnnotation(Process.class);
        this.serve = GalaxySpringUtil.getBean(process.master());
        this.home = this.sdpManager.getHome() + (process.home().startsWith(StrUtil.SLASH) ? process.home() : StrUtil.SLASH + process.home());
        // 添加依赖与被依赖关系
        Arrays.stream(process.depends())
                .forEach(depend -> {
                    AbstractProcess<AbstractHost> dependProcess = GalaxySpringUtil.getBean(depend);
                    this.parents.add(dependProcess);
                    dependProcess.getChildren().add((AbstractProcess<AbstractHost>) this);
                });
        // 添加进程伴生关系
        Arrays.stream(process.companions())
                .forEach(companion -> {
                    AbstractProcess<AbstractHost> companionProcess = GalaxySpringUtil.getBean(companion);
                    this.companions.add(companionProcess);
                    companionProcess.getCompanions().add((AbstractProcess<AbstractHost>) this);
                });
        // 添加进程互斥关系
        Arrays.stream(process.excludes())
                .forEach(exclude -> {
                    AbstractProcess<AbstractHost> excludeProcess = GalaxySpringUtil.getBean(exclude);
                    this.excludes.add(excludeProcess);
                    excludeProcess.getExcludes().add((AbstractProcess<AbstractHost>) this);
                });
        this.logger = new Logger();
    }

    /**
     * @Description : 添加进程覆盖的主机
     **/
    @Override
    protected void loadEnvResource() {
        // 加载进程运行的主机（ ⚠️ 没有安装则返回空值）
        List<String> hostnames = this.processDriver.initHosts(this.getName());
        deployHosts(hostnames);
    }

    /**
     * @Description : 进程安装入口函数
     * @note : ⚠️ 此函数只负责完成进程的初始化 !
     **/
    @Override
    protected synchronized void install(Blueprint.Serve blueprint) {
        // 获取该进程的蓝图
        Blueprint.Process blueprintProcess =
                CollUtil.findOne(blueprint.getProcesses(), bpp -> bpp.getProcessname().equals(this.getName()));
        // 在内存中添加主机
        deployHosts(blueprintProcess.getHostnames());
        // 开始安装进程
        initProcess();
        // 启动进程
        start();
        // 可用性检测
        await();
    }

    /**
     * @Description : 卸载进程
     * @note : ⚠️ 该方法不会清空进程的主机列表，需要在serve中依照逻辑调用 !
     **/
    @Override
    protected void recover() {
        // 停止所有运行的主机
        this.stop();
        // 执行重置函数
        this.reset();
    }

    /**
     * @Description : 进程是否安装
     * @note : An art cell !
     **/
    @Override
    public boolean isInstalled() {
        return this.handler.isInstalled(this);
    }

    /**
     * @Description : 进程是否活跃
     * @note : ⚠️ 该函数只判断进程是否存在，不对可用性做甄别 !
     **/
    @Override
    public boolean isAvailable() {
        return this.handler.isAvailable(this);
    }

    /**
     * @Description : 获取依赖进程
     **/
    @Override
    public List<AbstractProcess<AbstractHost>> getParents() {
        return (List<AbstractProcess<AbstractHost>>) super.getParents();
    }

    /**
     * @Description : 获取进程被依赖
     **/
    @Override
    public List<AbstractProcess<AbstractHost>> getChildren() {
        return (List<AbstractProcess<AbstractHost>>) super.getChildren();
    }

    /**
     * @Description : 根据是否可以扩容来更新功能列表
     **/
    @Override
    public List<Operation> getFunctions() {
        List<Operation> operations = super.getFunctions();
        if (this.isDynamic()) {
            return operations;
        }
        return CollUtil.filter(operations, o -> !o.getId().equals("EXPAND") && !o.getId().equals("SHRINK"));
    }

    /**
     * @Description : 启动进程
     * @note : ⚠️ 只启动进程宕机状态的主机 !
     **/
    @Override
    @Function(name = "启动", id = "start")
    public synchronized void start() {
        Predicate<T> function = host -> host.isProcessActive(this);
        // 并行启动进程宕机的主机
        this.hosts.parallelStream()
                .filter(function.negate())
                .forEach(host -> {
                    try {
                        host.startProcess(this);
                    }catch (Exception e) {
                        log.error("{}启动进程{}异常:{}", host.getHostname(), this.getName(), e.getMessage(), e);
                        throw new RuntimeException(host.getHostname()+"启动进程"+this.getName()+"异常:"+e.getMessage());
                    }
                });
    }

    /**
     * @Description : 停止进程
     * @note : ⚠️ 只停止进程启动状态的主机 !
     **/
    @Override
    @Function(name = "停止", id = "stop")
    public synchronized void stop() {
        Predicate<T> function = host -> host.isProcessActive(this);
        // 并行停止进程启动的主机
        this.hosts.parallelStream()
                .filter(function)
                .forEach(host -> {
                    try {
                        host.stopProcess(this);
                    } catch (Exception e) {
                        log.error("{}停止进程{}异常:{}", host.getHostname(), this.getName(), e.getMessage(), e);
                        throw new RuntimeException(host.getHostname()+"停止进程"+this.getName()+"异常:"+e.getMessage());
                    }
                });
    }

    /**
     * @Description : 重启进程
     **/
    @Override
    @Function(name = "重启", id = "restart")
    public synchronized void restart() {
        super.restart();
    }

    /**
     * @Description : 进程扩容
     **/
    @Function(name = "进程扩容", id = "EXPAND")
    public synchronized void extend(List<String> hostnames) {
        if (this.isDynamic()) {
            for (String hostname : hostnames) {
                T host = this.hostManager.getExpectHostByName(hostname);
                if (ObjectUtil.isEmpty(host)) {
                    this.error("主机“" + hostname + "”不存在。");
                } else if (this.hosts.contains(host)) {
                    this.error("主机“" + hostname + "”已存在“" + this.getName() + "”进程。");
                } else {
                    // 默认只负责下载对应服务的安装包
                    host.downloadPackage(this.sdpManager.getVersion(), this.serve.getPkg());
                    this.logDriver.log(RunLogLevel.INFO, hostname + "主机" + this.getServename() + "安装包下载完成。");
                    // 备份所有配置文件
                    List<Pair<AbstractConfig, String>> pairs = CollUtil.map(
                            this.serve.getAllConfigs(), config -> new Pair<>(config, config.backup()), true
                    );
                    try {
                        this.extend(host);
                        this.addHosts(hostname);
                        // ⚠️ 此处应有同步各个主机配置文件操作
                        this.serve.getAllConfigs().forEach(config -> {
                            config.getBranchNames().forEach(s -> config.addBranchHosts(s,hostname));
                        });
                        host.startProcess(this);
                        this.logDriver.log(RunLogLevel.INFO, hostname + "主机扩容" + this.getName() + "进程成功。");
                    } catch (Exception e) {
                        this.logDriver.log(RunLogLevel.ERROR, hostname + "主机扩容" + this.getName() + "进程失败。");
                        pairs.forEach(pair -> pair.getKey().restore(pair.getValue()));
                        pairs.forEach(pair -> {
                            AbstractConfig config = pair.getKey();
                            config.getBranchNames().forEach(br->config.rollbackBranchHostsAfterExtend(br,hostname));
                        });
                        throw new RuntimeException(e);
                    } finally {
                        pairs.forEach(pair -> pair.getKey().discard(pair.getValue()));
                    }
                }
            }
            this.serve.restart();
        } else {
            throw new RuntimeException("进程“" + this.getName() + "”不支持扩容操作。");
        }
    }

    /**
     * @Description : 进程缩容
     **/
    @Function(name = "进程缩容", id = "SHRINK")
    public synchronized void shorten(List<String> hostnames) {
        if (isDynamic()) {
            for (String hostname : hostnames) {
                T host = this.hostManager.getExpectHostByName(hostname);
                if (ObjectUtil.isEmpty(host)) {
                    this.error("主机“" + hostname + "”不存在。");
                } else if (!this.hosts.contains(host)) {
                    this.error("主机“" + hostname + "”不存在“" + this.getName() + "”进程。");
                } else {
                    // 备份所有配置文件
                    List<Pair<AbstractConfig, String>> pairs = CollUtil.map(
                            this.serve.getAllConfigs(), config -> new Pair<>(config, config.backup()), true
                    );

                    try {
                        this.shorten(host);
                        host.stopProcess(this);
                        this.logDriver.log(RunLogLevel.INFO, host.getHostname() + "主机" + this.getName() + "进程已停止。");
                        this.deleteHosts(hostname);
                        // 如果主机已经不包含在当前所在的服务中，则去掉所有配置文件的所有分支。
                        if (host.isIncludeServe(this.serve)) {
                            this.serve.getAllConfigs().forEach(
                                    config -> config.getBranchNames().forEach(b -> config.delBranchHosts(b, hostname))
                            );
                        }
                        this.logDriver.log(RunLogLevel.INFO, hostname + "主机缩容" + this.getName() + "进程成功。");
                    } catch (Exception e) {
                        this.logDriver.log(RunLogLevel.ERROR, hostname + "主机缩容" + this.getName() + "进程失败。");
                        pairs.forEach(pair -> pair.getKey().restore(pair.getValue()));
                        pairs.forEach(pair -> {
                            AbstractConfig config = pair.getKey();
                            config.getBranchNames().forEach(br->config.rollbackBranchHostsAfterShorten(br,hostname));
                        });
                        throw new RuntimeException(e);
                    } finally {
                        pairs.forEach(pair -> pair.getKey().discard(pair.getValue()));
                    }
                }
            }
            this.serve.restart();
        } else {
            throw new RuntimeException("进程“" + this.getName() + "”不支持缩容操作。");
        }
    }

    /**
     * @Description : 是否可以启动
     * @note : ⚠️ 只要有一台节点宕机就可以启动 !
     **/
    @Available(fid = "start")
    public boolean canStart() {
        AtomicBoolean is = new AtomicBoolean(false);
        this.getHosts().forEach(host -> {
            if(host.isHostActive() && !host.isProcessActive(this)) {
                is.set(true);
            }
        });
        return is.get();
    }

    /**
     * @Description : 是否可以停止
     * @note : ⚠️ 只要有一台主机运行就可以停止 !
     **/
    @Available(fid = "stop")
    public boolean canStop() {
        AtomicBoolean is = new AtomicBoolean(false);
        this.getHosts().forEach(host -> {
            if(host.isHostActive() && host.isProcessActive(this)) {
                is.set(true);
            }
        });
        return is.get();
    }

    /**
     * @Description : 是否允许扩容
     **/
    @Available(fid = "EXPAND")
    public boolean canExtend() {
        boolean flag = false;
        if (this.isDynamic()) {
            List<String> unusables = CollUtil.map(this.getHosts(), AbstractHost::getName, true);
            if (!this.getExcludes().isEmpty()) {
                for (AbstractProcess<AbstractHost> pro : this.getExcludes()) {
                    List<String> hosts_ = CollUtil.map(pro.getHosts(), AbstractHost::getName, true);
                    unusables.addAll(hosts_);
                }
            }
            List<AbstractHost> usables = this.hostManager.getHosts().stream()
                    .filter(host -> !unusables.contains(host.getName()))
                    .collect(Collectors.toList());
            flag = CollUtil.isNotEmpty(usables);
        }
        return flag;
    }

    /**
     * @Description : 是否允许缩容
     **/
    @Available(fid = "SHRINK")
    public boolean canShorten() {
        boolean flag = false;
        if (this.isDynamic()) {
            flag = this.getHosts().size() > getMin();
        }
        return flag;
    }

    /**
     * @Description : 进程类型声明
     **/
    @Override
    public Type getType(){
        return Type.PROCESS;
    }

    /**
     * @Description : 通过主机名部署分布式进程
     * @note : ⚠️ 初始化进程主机列表 !
     **/
    private void deployHosts(List<String> hostnames) {
        // 首先排序
        hostnames.sort(String::compareTo);
        for(String hostname : hostnames) {
            T host = this.hostManager.getExpectHostByName(hostname);
            this.hosts.add(host);
        }
    }

    /**
     * @Description : 获取所在服务名称
     **/
    public String getServename(){
        return ObjectUtil.isNull(this.serve) ? this.getClass().getAnnotation(Process.class).master().getSimpleName() : this.serve.getName();
    }

    /**
     * @Description : 获取宕机的节点
     **/
    public List<T> getDeadHosts() {
        Collection<T> hosts = CollUtil.filterNew(this.hosts, host -> !host.isProcessActive(this));
        return CollUtil.newArrayList(hosts);
    }

    /**
     * @Description : 获取正常的节点
     **/
    public List<T> getActiveHosts() {
        Collection<T> hosts = CollUtil.filterNew(this.hosts, host -> host.isProcessActive(this));
        return CollUtil.newArrayList(hosts);
    }

    /**
     * @Description : 进程占用的端口号
     * @note : ⚠️ 子类根据需求进行覆盖 !
     **/
    public Integer getPort() {
        return -1;
    }

    /**
     * @Description : 根据主机域名获取实时日志信息
     * @Note : ⚠️ 日志内容返回最新的200行 !
     **/
    public String currentLog(String hostname) {
        return this.logger.getCurrentLog(hostname);
    }

    /**
     * @Description : 获取进程的分组信息
     * @Note : ⚠️ 同一个模式下，同一个进程只能存在一个主机组 !
     **/
    public HostGroup getHostGroup() {
        HostGroup group = null;
        String mode = this.hostManager.getMode();
        if (StrUtil.isNotBlank(mode)) {
            group = CollUtil.findOne(this.groups, g -> g.mode().equals(mode));
        }
        return group;
    }

    /**
     * @Description : 添加角色主机
     * @note : ⚠️ 只在内存和数据库中进行元数据修改 !
     **/
    protected synchronized void addHosts(String ... hostnames) {
        // 调用引擎，错误抛出则终止
        this.processDriver.addHosts(this.getName(), Convert.toList(String.class, hostnames));
        Arrays.stream(hostnames).forEach(hostname -> {
            T host = this.hostManager.getExpectHostByName(hostname);
            this.hosts.add(host);
        });
        // 添加完成进行排序
        this.hosts.sort(Comparator.comparing(AbstractHost::getHostname));
    }

    /**
     * @Description : 清空内存中的进程主机信息
     **/
    protected synchronized void clearHosts() {
        // 清空角色的主机信息
        this.hosts.clear();
    }

    /**
     * @Description : 删除主机
     * @note : ⚠️ 只在内存和数据库中进行元数据修改 !
     **/
    protected synchronized void deleteHosts(String ... hostnames) {
        // 调用引擎，错误抛出则终止
        this.processDriver.delHosts(this.getName(), Convert.toList(String.class, hostnames));
        Arrays.stream(hostnames).forEach(hostname -> {
            T host = this.hostManager.getExpectHostByName(hostname);
            this.hosts.remove(host);
        });
        // 添加完成进行排序
        this.hosts.sort(Comparator.comparing(AbstractHost::getHostname));
    }

    /**
     * @Description : 进程初始化
     * @note : ⚠️ 此函数在启动进程之前执行创建目录或初始化文件系统之类的操作 !
     **/
    protected void initProcess() {

    }

    /**
     * @Description : 重置进程函数
     * @note : ⚠️ 此函数运行在进程停止之后 !
     **/
    protected void reset() {

    }

    /**
     * @Description : 服务扩容
     **/
    protected void extend(AbstractHost host) {
        if (!this.isDynamic()) {
            throw new RuntimeException(this.getName() + " 进程不允许扩容操作。");
        }
    }

    /**
     * @Description : 服务缩容
     **/
    protected void shorten(AbstractHost host) {
        if (!this.isDynamic()) {
            throw new RuntimeException(this.getName() + " 进程不允许缩容操作。");
        }
    }

    /**
     * @Description : 是否存在扩容/缩容
     */
    protected boolean isDynamic() {
        return this.dynamic;
    }

    /**
     * @Description : 获取日志文件所在的绝对路径
     * @Note : ⚠️ 子类根据实际路径覆盖该函数 !
     **/
    protected String getLogFilePath() {
        return this.getHome() + StrUtil.SLASH + "logs";
    }

    /**
     * @Description : 获取日志文件名称
     * @Note : ⚠️ 子类根据具体日志文件名称覆盖该函数 !
     **/
    protected String getLogFileName(String hostname) {
        return null;
    }

    /**
     * Project : galaxy
     * Class : cn.gsq.sdp.core.serve.abstraction.AbstractProcess.Logger
     *
     * @author : gsq
     * @date : 2024-05-31 17:47
     * @note : It's not technology, it's art !
     **/
    protected class Logger {

        private final String filePath;    // 日志文件绝对路径

        /**
         * @Description : 构造函数
         * @Note : ⚠️ $HOSTNAME 代表主机名称 !
         **/
        private Logger() {
            String fileName = getLogFileName(SdpPropertiesFinal.HOSTNAME_PROXY);
            if (StrUtil.isBlank(fileName)) {
                this.filePath = null;
            } else {
                this.filePath = getLogFilePath() + StrUtil.SLASH + fileName;
            }
        }

        /**
         * @Description : 获取日志文件内容
         * @Note : ⚠️ 当结果为null时表示代码中没有写日志的相关路径 !
         **/
        private String getCurrentLog(String hostname) {
            String result = null;
            // 替换通配符
            String realPath = StrUtil.replace(this.filePath, SdpPropertiesFinal.HOSTNAME_PROXY, hostname);
            T host = CollUtil.findOne(getHosts(), h -> h.getHostname().equals(hostname));
            try {
                if(this.filePath == null) {  // 代码中没有有效日志
                    log.warn("进程'{}'没有有效日志", getName());
                } else if (host == null) {  // 主机中不存在该进程
                    throw new RuntimeException("主机'" + hostname + "'不存在'" + getName() + "'进程");
                } else if (!host.isHostActive()) {  // 主机宕机不可用
                    throw new RuntimeException("当前主机'" + getName() + "'不可用");
                } else {    // 正常获取日志内容
                    String fileContent = host.getFileContent(realPath);
                    result = fileContent == null ? "" : fileContent;    // 控制获取到的日志不为null
                }
            } catch (FileNotFoundException e) { // 主机不存在该日志文件
                throw new RuntimeException("主机'" + hostname + "'不存在'" + realPath + "'日志文件");
            }
            return result;
        }

    }

}
