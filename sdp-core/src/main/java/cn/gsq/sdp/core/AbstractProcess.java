package cn.gsq.sdp.core;

import cn.gsq.common.config.GalaxySpringUtil;
import cn.gsq.sdp.Blueprint;
import cn.gsq.sdp.SdpPropertiesFinal;
import cn.gsq.sdp.core.annotation.Available;
import cn.gsq.sdp.core.annotation.Function;
import cn.gsq.sdp.core.annotation.Process;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

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

    private final int order;    // 拍序

    private final int min;      // 最小部署数量

    private final int max;      // 最大部署数量

    private Logger logger;      // 日志处理类

    private String home;    // 命令执行根目录

    protected List<T> hosts = CollUtil.newArrayList();     // 进程所在的主机集合

    protected Set<AbstractProcess<AbstractHost>> companions = CollUtil.newHashSet();    // 伴生进程

    protected Set<AbstractProcess<AbstractHost>> excludes = CollUtil.newHashSet();    // 互斥进程

    public AbstractProcess() {
        Process process = this.getClass().getAnnotation(Process.class);
        this.handler = process.handler();
        this.mark = process.mark();
        this.start = process.start();
        this.stop = process.stop();
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
        this.home = this.sdpManager.getHome() + (process.home().startsWith(StrUtil.SLASH) ? process.home() : StrUtil.SLASH + process.home());
        this.logger = new Logger();
        // 加载进程运行的主机（ ⚠️ 没有安装则返回空值）
        List<String> hostnames = this.processDriver.initHosts(this.getName());
        deployHosts(hostnames);
    }

    /**
     * @Description : 进程安装入口函数
     * @note : ⚠️ 此函数只负责完成进程的初始化 !
     **/
    @Override
    public synchronized void install(Blueprint.Serve blueprint) {
        // 获取该进程的蓝图
        Blueprint.Process blueprintProcess =
                CollUtil.findOne(blueprint.getProcesses(), bpp -> bpp.getProcessname().equals(this.getName()));
        // 在内存中添加主机
        deployHosts(blueprintProcess.getHostnames());
        // 开始安装进程
        initProcess(this);
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
    public void recover() {
        // 停止所有运行的主机
        this.stop();
        // 执行卸载函数
        recover(this);
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
     * @Description : 是否可以重启
     **/
    @Available(fid = "restart")
    public boolean canRestart() {
        return true;
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
        return this.serve.getName();
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
     **/
    public HostGroupHandler getHostGroup() {
        return this.getClass().getAnnotation(Process.class).group();
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
    public synchronized void deleteHosts(String ... hostnames) {
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
    protected abstract void initProcess(AbstractProcess<T> process);

    /**
     * @Description : 卸载进程
     * @note : ⚠️ 此函数运行在进程停止之后 !
     **/
    protected abstract void recover(AbstractProcess<T> process);

    /**
     * @Description : 是否存在扩容/缩容
     */
    public boolean isExistExtend() {
        return false;
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
