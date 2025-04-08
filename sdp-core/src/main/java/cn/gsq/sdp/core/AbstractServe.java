package cn.gsq.sdp.core;

import cn.gsq.common.config.GalaxySpringUtil;
import cn.gsq.graph.dag.Vertex;
import cn.gsq.sdp.*;
import cn.gsq.sdp.core.annotation.Serve;
import cn.gsq.sdp.core.utils.DagUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.AbstractServe
 *
 * @author : gsq
 * @date : 2025-02-28 11:07
 * @note : It's not technology, it's art !
 **/
@Slf4j
public abstract class AbstractServe extends AbstractApp {

    @Getter
    private final String version;     // 版本号

    @Getter
    private final String serveType;    // 服务分类

    @Getter
    private final List<String> serveLabels; // 服务标签

    private Map<String, String> properties;     // 服务需要展示的属性

    private List<AbstractConfig> configs;       // 配置文件集合

    @Getter
    private List<AbstractProcess<AbstractHost>> processes;    // 子进程集合

    @Getter
    private volatile boolean locked = false;    // 服务是否被锁定（处于“安装中”、“卸载中”等临时状态）

    private final boolean allMust;     // 是否所有主机都需要下载安装包

    @Getter
    private final int order;

    private final transient ServeHandler handler;               // 根据部署模式划分管理者（注解中获取）

    /* ⚠️ 以下是服务相关接口 */

    protected AbstractServe() {
        Serve serve = this.getClass().getAnnotation(Serve.class);
        this.version = serve.version();
        this.handler = serve.handler();
        this.allMust = serve.all();
        this.order = serve.order();
        this.serveType = serve.type().getName();
        this.serveLabels = CollUtil.toList(serve.labels());
        this.description = serve.description();
    }

    /**
     * @Description : 系统启动时初始化服务属性
     * @note : ⚠️ 系统启动入口 !
     **/
    @Override
    protected void initProperty() {
        Serve serve = this.getClass().getAnnotation(Serve.class);
        // 添加依赖与被依赖关系
        Arrays.stream(serve.depends())
                .forEach(depend -> {
                    AbstractServe dependServe = GalaxySpringUtil.getBean(depend);
                    this.getParents().add(dependServe);
                    dependServe.getChildren().add(this);
                });
        // 添加服务的配置文件
        this.configs = GalaxySpringUtil.getBeans(AbstractConfig.class).stream()
                .filter(config -> config.isBelong(this.getClass()))
                .collect(Collectors.toList());
        // 配置文件排序
        this.configs.sort(Comparator.comparing(AbstractConfig::getOrder));
        // 添加服务的进程
        this.processes = GalaxySpringUtil.getBeans(AbstractProcess.class).stream()
                .filter(process -> process.isBelong(this.getClass()))
                .map(process -> (AbstractProcess<AbstractHost>) process)
                .collect(Collectors.toList());
        // 进程排序
        this.processes.sort(Comparator.comparing(AbstractProcess::getOrder));
        // 加载可展示的属性列表
        this.properties = initProperties();
        // 将本服务需要的配置文件写入所在配置文件的路径集合
        for(String appendMsg : serve.appends()) {
            addAppendToTarget(appendMsg);
        }
    }

    @Override
    protected void loadEnvResource() {
        // 服务的属性都是固定的，没有与环境相关的。
    }

    /**
     * @Description : 服务安装
     * @Note : ⚠️ 安装过程任意一步出现异常，异常抛出、安装终止；第五步需将所有的进程安装都跑一遍，如遇异常则抛出第一个 !
     **/
    @Override
    public synchronized void install(Blueprint.Serve blueprint) {
        this.locked = true; // 锁定组件服务，开始安装
        try {
            // ⚠️ 安装第一步：初始化服务，执行服务初始化函数
            initServe(blueprint);
            // ⚠️ 安装第二步：下载安装包
            List<String> hostnames;
            if (this.allMust) {
                hostnames = CollUtil.map(this.hostManager.getHosts(), AbstractHost::getHostname, true);
            } else {
                hostnames = blueprint.getAllProcessHostnames();
            }
            download(hostnames);
            // ⚠️ 安装第三步：发送服务预安装广播
            super.broadcast(AppEvent.PREINSTALL, blueprint.getServename(), JSON.toJSONString(blueprint.getProcesses()));
            // ⚠️ 安装第四步：初始化服务的每一个配置文件
            for(AbstractConfig config : this.configs) {
                config.install(blueprint);
            }
            // ⚠️ 安装第五步：延迟等待完成配置文件的更新
            ThreadUtil.safeSleep(3000);
            // ⚠️ 安装第六步：构建DAG图逐步初始化服务中的进程
            Exception exception = null;
            for(Vertex<AbstractProcess<AbstractHost>> vertex : DagUtil.getDagResult(this.processes)) {
                AbstractProcess<AbstractHost> process = vertex.getTask();
                try {
                    process.install(blueprint); // 上一步进程安装即便失败后面的进程仍然继续安装保证服务安装完整性
                } catch (Exception e) {
                    if (exception == null) {
                        exception = e;
                    }
                }
            }
            // 服务安装之后,可能需要执行的一些统一操作
            afterInstall(blueprint);
            // ⚠️ 安装第七步：回调"服务安装回执"函数即视为服务已经安装（不论成功或者失败）
            this.serveDriver.receiptInstallServe(blueprint);
            // ⚠️ 安装第八步：如果安装失败则抛出异常
            if(exception != null) throw exception;
            // ⚠️ 安装第九步：可用性检测（可被第七步终止）
            observe();
            // ⚠️ 安装第十步：发送服务已安装广播（可被第七步终止）
            this.broadcast(AppEvent.INSTALLED, blueprint.getServename(), JSON.toJSONString(blueprint.getProcesses()));
            callbackServe();
            this.serveDriver.updateResourcePlan(this.getName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            this.locked = false;    // 解除服务锁定状态
        }
    }

    /**
     * @Description : 服务安装完成后的回调函数
     * @Param   :
     * @Return : void
     * @Author : syu
     * @Date : 2024/12/2
     */
    protected void callbackServe() {}

    /**
     * @Description : 服务回滚复原
     * @Param : []
     * @Return : void
     * @Author : gsq
     * @Date : 10:00 上午
     * @note : An art cell !
     **/
    @Override
    public void recover() {
        // ⚠️ 还原第一步：反向DAG图停止进程并执行各自的还原函数
        List<Vertex<AbstractProcess<AbstractHost>>> vertices =
                ListUtil.reverse(ListUtil.toList(DagUtil.getDagResult(this.processes)));
        for(Vertex<AbstractProcess<AbstractHost>> vertex : vertices) {
            AbstractProcess<AbstractHost> process = vertex.getTask();
            process.recover();
        }
        // ⚠️ 还原第二步：上一步执行无误则清空内存中的进程主机列表信息
        this.processes.forEach(AbstractProcess::clearHosts);
        // ⚠️ 还原第三步：还原配置文件
        this.configs.forEach(AbstractConfig::recover);
        // ⚠️ 还原第四步：调用服务层卸载函数
        afterRecover();
        // ⚠️ 还原第五步：发送服务已卸载广播
        super.broadcast(AppEvent.UNINSTALLED, this.getName(), null);
        //todo 修改resourceplan
    }

    /**
     * @Description : 启动服务
     **/
    @Override
    public synchronized void start() {
        this.handler.start(this);
    }

    /**
     * @Description : 停止服务
     **/
    @Override
    public synchronized void stop() {
        this.handler.stop(this);
    }

    /**
     * @Description : 获取依赖服务集合
     **/
    @Override
    public List<AbstractServe> getParents() {
        return (List<AbstractServe>) super.getParents();
    }

    /**
     * @Description : 获取被依赖的服务集合
     **/
    @Override
    public List<AbstractServe> getChildren() {
        return (List<AbstractServe>) super.getChildren();
    }

    /**
     * @Description : 服务是否安装
     **/
    @Override
    public boolean isInstalled(){
        return this.handler.isInstalled(this);
    }

    /**
     * @Description : 服务是否可用
     **/
    @Override
    public boolean isAvailable(){
        return this.handler.isAvailable(this);
    }

    /**
     * @Description : 服务类型声明
     **/
    @Override
    public Type getType() {
        return Type.SERVE;
    }

    /**
     * @Description : 卸载服务
     * @note : ⚠️ 逻辑与复原相同 !
     **/
    public synchronized void uninstall() {
        this.locked = true; // 锁定服务
        try {
            // 卸载服务
            recover();
            // 没有异常则调用卸载回执
            this.serveDriver.receiptUninstallServe(this.getName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            this.locked = false;    // 解锁服务
        }
    }

    /**
     * @Description : 判断服务是否存在配置文件
     * @Param : []
     * @Return : boolean
     * @Author : gsq
     * @Date : 6:11 下午
     * @note : An art cell !
     **/
    public boolean isExistConfig() {
        return CollUtil.isNotEmpty(this.getDisplayConfigs());
    }

    /**
     * @Description : 添加服务依赖文件监听
     * @note : ⚠️ msg为serve注解中appends信息的一个条目 !
     **/
    private void addAppendToTarget(String msg) {
        // 分隔信息(服务名:配置文件名:配置文件分支:输出路径); 例如: "HDFS:core-site.xml:defalut:/spark/conf/"
        List<String> split = StrUtil.split(msg, StrUtil.COLON);
        // 拼凑分支配置文件输出地址
        String file = StrUtil.endWith(split.get(3), StrUtil.C_SLASH) ?
                this.sdpManager.getHome() + StrUtil.C_SLASH + split.get(3) + split.get(1) :
                this.sdpManager.getHome() + StrUtil.C_SLASH + split.get(3) + StrUtil.C_SLASH + split.get(1);
        // 找到相应的配置文件
        AbstractConfig config = CollUtil.findOne(GalaxySpringUtil.getBeans(AbstractConfig.class),
                c -> c.getServeNameByClass().equals(split.get(0)) && c.getName().equals(split.get(1)));
        // 添加分支配置文件地址
        if(ObjectUtil.isNotNull(config)) {
            config.addPathsToConfig(split.get(2), file);
        } else {
            log.warn("{}服务中无法找到{}配置文件", split.get(0), split.get(1));
        }
    }

    /**
     * @Description : 初始化服务属性
     * @note : ⚠️ 安装时运行一次 !
     **/
    private Map<String, String> initProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("名称", getName());
        properties.put("版本", getVersion());
        return properties;
    }

    /**
     * @Description : 服务可用行监测
     * @Note : ⚠️ 3分钟循环监测，超时则抛出异常 !
     **/
    private void observe() {
        // 判断函数
        BooleanSupplier function = () -> {
            List<AbstractProcess<AbstractHost>> processes = this.getProcesses();
            boolean check = true;
            for(AbstractProcess<AbstractHost> process : processes){
                if(!process.isAvailable()){
                    check = false;
                    break;
                }
            }
            return check;
        };
        // 执行3分钟的循环监测
        int i = 0;
        while (i < getTimes()) {
            ThreadUtil.safeSleep(3 * 1000);
            if(function.getAsBoolean()) return;
            i++;
        }
        throw new RuntimeException("服务" + this.getName() + "可用性响应超时, 请检查日志!");
    }

    /**
     * @Description : 获取服务可展示属性
     **/
    public Map<String, String> getProperties() {
        extendProperties(this.properties);
        return MapUtil.unmodifiable(this.properties);
    }

    /**
     * @Description : 扩展服务可展示的属性
     **/
    protected void extendProperties(Map<String, String> properties) {}

    /**
     * @Description : 初始化服务
     * @note : ⚠️ 服务初始化 子类酌情覆盖 !
     **/
    protected abstract void initServe(Blueprint.Serve blueprint);

    /**
     * @Description : 下载安装包
     **/
    protected void download(List<String> hostnames) {
        for (String hostname : hostnames) {
            this.resourceDriver.download(
                    new Resource()
                            .setVersion(this.sdpManager.getVersion())
                            .setServename(this.getName())
                            .setHostname(hostname)
                            .setPath(this.sdpManager.getHome())
            );
        }
    }

    /**
     * @Description : 服务安装后可能需要执行的方法
     */
    protected void afterInstall(Blueprint.Serve blueprint) {}

    /**
     * 判断服务是否真的可用
     */
    public RpcRespond<String> isServeAvailable() {
        RpcRespond<String> respond = new RpcRespond<>(true,"检测通过","检测通过");
        if (!this.isAvailable()) {
            new RpcRespond<>(false,"服务存在异常","某些进程不可用");
        }
        return respond;
    }

    /**
     * @Description : 所有进程还原后服务的操作
     */
    protected void afterRecover() {}

    /**
     * @Description : 获取服务的webUI
     * @note : ⚠️ 由下游服务进行覆盖 !
     **/
    public List<WebUI> getWebUIs(){
        return null;
    }

    /* ⚠️ 以下是配置文件相关接口 */

    /**
     * @Description : 获取所有的配置文件
     **/
    public List<AbstractConfig> getAllConfigs() {
        return this.configs;
    }

    /**
     * @Description : 获取支持使用者修改的配置文件
     **/
    public List<AbstractConfig> getDisplayConfigs() {
        return (List<AbstractConfig>) CollUtil.filterNew(this.configs, AbstractConfig::isDisplay);
    }

    /**
     * @Description : 根据配置文件名称查找配置文件实例
     * @note : ⚠️ 没有则返回null !
     **/
    public AbstractConfig getConfigByName(String name) {
        return CollUtil.findOne(this.configs, config -> config.getName().equals(name));
    }

    /**
     * @Description : 根据配置文件名称获取默认分支的map内容
     **/
    public Map<String, String> getConfigDefaultContentToMap(String cname) {
        return getConfigByName(cname).getDefaultBranchContent();
    }

    /**
     * @Description : 根据配置文件名称和分支名称获取map内容
     **/
    public Map<String, String> getConfigBranchContentToMap(String cname, String bname) {
        return getConfigByName(cname).getBranchContent(bname);
    }

    /**
     * @Description : 根据key获取配置文件默认分支的某项内容
     **/
    public String getConfigDefaultValueByKey(String cname, String key) {
        return getConfigByName(cname).getDefaultBranchContent().get(key);
    }

    /**
     * @Description : 更新默认配置文件内容
     * @note : ⚠️ items不需要使用全部内容, 只传入需要更新的配置即可, 不可用于删除配置 !
     **/
    public void updateConfigDefault(String cname, Map<String, String> items) {
        Map<String, String> config = getConfigDefaultContentToMap(cname);
        for(Map.Entry<String, String> entry : items.entrySet()) {
            config.put(entry.getKey(), entry.getValue());
        }
        getConfigByName(cname).updateDefaultConfig(config);
    }

    /* ⚠️ 以下是进程相关接口 */

    /**
     * @Description : 根据名称获取进程
     * @note : ⚠️ 没有则返回null !
     **/
    public AbstractProcess<AbstractHost> getProcessByName(String name) {
        return CollUtil.findOne(this.processes, process -> process.getName().equals(name));
    }

    /**
     * @Description : 根据名称获取进程
     * @note : ⚠️ 子类特供 !
     **/
    protected <T extends AbstractHost> AbstractProcess<T> getProcessByNameForImpl(String name) {
        return (AbstractProcess<T>) getProcessByName(name);
    }

    /**
     * @Description : 获取进程默认角色覆盖的主机列表
     **/
    public List<AbstractHost> getProcessHosts(String name) {
        return getProcessByName(name).getHosts();
    }

    /**
     * @Description : 获取当前服务覆盖的所有主机列表
     */
    public Set<AbstractHost> getHosts() {
        Set<AbstractHost> hosts = new HashSet<>();
        this.getProcesses().forEach(process -> {
            hosts.addAll(process.getHosts());
        });
        return hosts;
    }

}
