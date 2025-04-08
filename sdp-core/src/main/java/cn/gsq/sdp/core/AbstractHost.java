package cn.gsq.sdp.core;

import cn.gsq.sdp.AppEvent;
import cn.gsq.sdp.MountParams;
import cn.gsq.sdp.RpcRequest;
import cn.gsq.sdp.RpcRespond;
import cn.gsq.sdp.core.annotation.Function;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.FileNotFoundException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;

import static cn.gsq.sdp.SdpPropertiesFinal.Command.C_GRAB;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.AbstractHost
 *
 * @author : gsq
 * @date : 2025-02-28 11:02
 * @note : It's not technology, it's art !
 **/
@Slf4j
@Getter
public abstract class AbstractHost extends AbstractExecutor {

    protected final String hostname;    //  节点域名

    protected final List<HostGroup> groups;    // 主机分组（同一主机可存在多个分组）

    /**
     * @Description : ioc注册构造函数
     **/
    protected AbstractHost(String hostname, List<String> groups) {
        super.setDrivers();
        this.hostname = hostname;
        this.groups = CollUtil.newArrayList();
        if (CollUtil.isNotEmpty(groups)) {
            for (String name : groups) {
                HostGroup group = this.hostManager.getHostGroup(name);
                if (group != null) {
                    this.groups.add(group);
                }
            }
        }
    }

    /**
     * @Description : 获取节点静态域名
     * @note : An art cell !
     **/
    @Override
    public String getName() {
        return this.hostname;
    }

    @Override
    protected void initProperty() {
    }

    @Override
    protected void loadEnvResource() {
    }

    @Override
    protected void recover() {
    }

    /**
     * @Description : 获取主机所在的分组名称集合
     **/
    public List<String> getGroupNames() {
        return CollUtil.map(this.groups, HostGroup::name, true);
    }

    /**
     * @Description : 修改分组信息
     * @Note : ⚠️ 整体更新 !
     **/
    protected void updateGroups(List<String> groups) {
        this.groups.clear();
        if (CollUtil.isNotEmpty(groups)) {
            for (String name : groups) {
                HostGroup group = this.hostManager.getHostGroup(name);
                if (group != null) {
                    this.groups.add(group);
                }
            }
        }
    }

    /**
     * @Description : 主机类型声明
     **/
    @Override
    public Type getType() {
        return Type.HOST;
    }

    /**
     * @Description : 启动主机代理进程
     * @note : ⚠️ 启动galaxy agent进程（此处通过pilot引擎进程操作） !
     **/
    @Override
    public void start() {
        this.pilotDriver.startAgent(this.hostname);
    }

    /**
     * @Description : 停止主机代理进程
     **/
    @Override
    public void stop() {
        this.pilotDriver.stopAgent(this.hostname);
    }

    /**
     * @Description : 重启主机代理进程
     **/
    @Override
    public void restart() {
        this.pilotDriver.restart(this.hostname);
    }

    /**
     * @Description : 主机代理是否可用
     **/
    public boolean isHostActive() {
        return this.hostDriver.isAlive(this.hostname);
    }

    /**
     * @Description : 判断进程是否宕机
     * @note : ⚠️ 有端口查看端口, 没有端口直接rpc上去根据process mark看进程 !
     **/
    public boolean isProcessActive(AbstractProcess<? extends AbstractHost> process) {
        boolean flag = false;
        try {
            if(process.getPort() != -1) {
                if(isOpen(process.getPort())) {
                    flag = true;
                }
            } else {
                RpcRespond<String> respond = this.rpcDriver.execute(
                        RpcRequest.createRequest(
                                this.hostname,
                                process.getHome(),
                                StrUtil.format(C_GRAB, process.getMark())
                        )
                );
                if(!respond.isSuccess()) {
                    log.error("主机{}在ps脚本执行时错误: {}", this.hostname, C_GRAB);
                } else {
                    if(StrUtil.isNotBlank(respond.getContent())) {
                        flag = true;
                    }
                }
            }
        } catch (Exception e) {
            String error = StrUtil.format("{}主机在检查{}进程的{}角色时系统错误: {}",
                    this.getName(), process.getName(), process.getName(), e.getMessage());
            log.error("{}主机获取进程状态时Rpc服务异常: {}", this.getName(), e.getMessage(), e);
            throw new RuntimeException(error);
        }
        return flag;
    }

    /**
     * @Description : 判断当前主机是否可以从集群中移除
     */
    public boolean canRemove() {
        return this.sdpManager.isHostCanRemove(this.hostname);
    }

    /**
     * @Description : 启动进程
     **/
    @Function(name = "启动进程", id = "startProcess", isReveal = false)
    public void startProcess(String processname) {
        AbstractProcess<AbstractHost> process = this.sdpManager.getProcessByName(processname);
        RpcRespond<String> respond = this.rpcDriver.execute(
                RpcRequest.createRequest(this.hostname, process.getHome(), process.getStart())
        );
        if(!respond.isSuccess()) {
            log.error("主机{}中{}进程启动脚本执行错误: {}", this.hostname, process.getName(), respond.getContent());
            throw new RuntimeException("主机" + this.hostname + "中" + process.getName() + "进程启动脚本执行错误:" + respond.getContent());
        }
    }

    /**
     * @Description : 停止进程
     **/
    @Function(name = "停止进程", id = "stopProcess", isReveal = false)
    public void stopProcess(String processname) {
        AbstractProcess<AbstractHost> process = this.sdpManager.getProcessByName(processname);
        RpcRespond<String> respond = this.rpcDriver.execute(
                RpcRequest.createRequest(this.hostname, process.getHome(), process.getStop())
        );
        if(!respond.isSuccess()) {
            log.error("主机{}中{}进程停止脚本执行错误: {}", this.hostname, process.getName(), respond.getContent());
            throw new RuntimeException("主机" + this.hostname + "中" + process.getName() + "进程停止脚本执行错误:" + respond.getContent());
        }
    }

    /**
     * @Description : 重启进程
     **/
    @Function(name = "重启进程", id = "restartProcess", isReveal = false)
    public void restart(String processname) {
        this.stopProcess(processname);
        ThreadUtil.safeSleep(3000);
        this.startProcess(processname);
    }

    /**
     * @Description : 启动进程
     **/
    public void startProcess(AbstractProcess<? extends AbstractHost> process) {
        RpcRespond<String> respond;
        try {
            respond = this.rpcDriver.execute(RpcRequest.createRequest(this.hostname, process.getHome(), process.getStart()));
            if(!respond.isSuccess()) {
                log.error("{}主机中{}进程启动脚本执行错误: {}", this.hostname, process.getName(), respond.getContent());
                String error = StrUtil.format("{}主机中{}进程启动脚本执行错误: {}", this.hostname, process.getName(), respond.getContent());
                throw new RuntimeException(error);
            }
        } catch (Exception e) {
            String error = StrUtil.format("{}主机启动{}进程时Rpc服务异常: {}", this.getName(), process.getName(), e.getMessage());
            log.error("主机{}启动进程{}时系统错误", this.getName(), process.getName(), e);
            throw new RuntimeException(error);
        }
    }

    /**
     * @Description : 停止进程
     **/
    public void stopProcess(AbstractProcess<? extends AbstractHost> process) {
        RpcRespond<String> respond;
        try {
            respond = this.rpcDriver.execute(RpcRequest.createRequest(this.hostname, process.getHome(), process.getStop()));
            if(!respond.isSuccess()) {
                log.error("{}主机中{}进程停止脚本执行错误: {}", this.hostname, process.getName(), respond.getContent());
                String error = StrUtil.format("{}主机中{}进程停止脚本执行错误: {}", this.hostname, process.getName(), respond.getContent());
                throw new RuntimeException(error);
            }
        } catch (Exception e) {
            String error = StrUtil.format("{}主机停止{}进程时Rpc服务异常: {}", this.getName(), process.getName(), e.getMessage());
            log.error("主机{}停止进程{}时系统错误", this.getName(), process.getName(), e);
            throw new RuntimeException(error);
        }
    }

    /**
     * @Description : 启动pilot
     */
    public void startPilot() {
        try {
            RpcRespond<String> execute = this.rpcDriver.execute(
                    RpcRequest.createRequest(
                            this.hostname,
                            this.sdpManager.getHome(),
                            "1pctl start"
                    )
            );
        }catch (Exception e){
            String errorMsg = StrUtil.format("{}主机pilot服务启动异常: {}", this.hostname, e.getMessage());
            log.error("{}主机pilot服务启动异常: {}", this.getName(), e.getMessage());
            throw new RuntimeException(errorMsg);
        }
    }

    /**
     * @Description : 停止pilot
     */
    public void stopPilot() {
        try {
            RpcRespond<String> execute = this.rpcDriver.execute(
                    RpcRequest.createRequest(
                            this.hostname,
                            this.sdpManager.getHome(),
                            "1pctl stop"
                    )
            );
        }catch (Exception e){
            String errorMsg = StrUtil.format("{}主机pilot服务停止异常: {}", hostname, e.getMessage());
            log.error("{}主机pilot服务停止异常: {}", this.getName(), e.getMessage());
            throw new RuntimeException(errorMsg);
        }
    }

    public void restartPilot() {
        try {
            RpcRespond<String> execute = this.rpcDriver.execute(
                    RpcRequest.createRequest(
                            this.hostname,
                            this.sdpManager.getHome(),
                            "1pctl restart"
                    )
            );
        } catch (Exception e){
            String errorMsg = StrUtil.format("{}主机pilot服务重启异常: {}", hostname, e.getMessage());
            log.error("{}主机pilot服务重启异常: {}", this.getName(), e.getMessage());
            throw new RuntimeException(errorMsg);
        }
    }

    /**
     * @Description : pilot运行状态
     */
    public boolean isPilotActive() {
        return isOpen(9888);
    }

    /**
     * @Description : docker状态查询
     */
    public boolean isDockerActive() {
        return isOpen(2375);
    }

    /**
     * @Description : 查询docker是否安装
     */
    public boolean isDockerInstalled() {
        boolean result = true;
        try {
            RpcRespond<String> execute = this.rpcDriver.execute(
                    RpcRequest.createRequest(
                            this.hostname,
                            this.sdpManager.getHome(),
                            "systemctl status docker"
                    )
            );
            if (execute.getContent().contains("could not be found")) {
                result = false;
            }
        }catch (Exception e) {
            String errorMsg = StrUtil.format("{}主机docker服务未安装: {}", hostname, e.getMessage());
            log.error("{}主机docker服务未安装", this.getName(), e);
            throw new RuntimeException(errorMsg);
        }
        return result;
    }

    /**
     * @Description : 端口探针
     **/
    public boolean isOpen(int port) {
        boolean isOpen = false;
        try {
            isOpen = NetUtil.isOpen(new InetSocketAddress(this.hostname, port), 500);
        } catch (Exception e) {
            log.error("通往主机{}的{}端口探针错误：{}", hostname, port, e.getMessage(), e);
        }
        return isOpen;
    }

    /**
     * @Description : 查询节点中是否存在该文件
     **/
    public boolean isFileExist(String path) {
        RpcRespond<String> respond;
        try {
            respond = this.rpcDriver.execute(
                    RpcRequest.createRequest(
                            this.hostname,
                            "/usr/bin",
                            "./exist.sh " + path
                    )
            );
            return respond.isSuccess();
        } catch (RuntimeException e) {
            String errorMsg = StrUtil.format("{}主机执行查询{}是否存在脚本时Rpc服务异常: {}", this.hostname, path, e.getMessage());
            log.error("主机{}查看{}脚本时系统错误", this.getName(), path, e);
            throw new RuntimeException(errorMsg);
        }
    }

    /**
     * @Description : 展示磁盘信息
     **/
    public String listDisk(String op){
        try {
            RpcRespond<String> respond = this.rpcDriver.execute(
                    RpcRequest.createRequest(
                            this.hostname,
                            "./scripts",
                            "./disklist.py " + op
                    )
            );
            if (!respond.isSuccess()) {
                log.error("{}上磁盘获取失败", this.hostname);
            }
            return respond.getContent();
        } catch (RuntimeException e) {
            log.error("{}上磁盘获取异常:{}", this.hostname, e.getMessage());
            return null;
        }
    }

    /**
     * @Description : 磁盘挂载功能
     **/
    @Function(id = "MOUNTDISK", name = "挂载磁盘")
    public String mountDisk(MountParams.DiskInfo diskInfo) {
        String driver = diskInfo.getDriver();
        String path = diskInfo.getPath();
        RpcRespond<String> respond;
        if (!Objects.equals(driver, "") && !Objects.equals(path, "")) {
            respond = this.rpcDriver.execute(
                    RpcRequest.createRequest(
                            this.hostname,
                            "./scripts",
                            "./diskmount.sh " + driver + " " + path
                    )
            );
            if (!respond.isSuccess()) {
                throw new RuntimeException(hostname + "上磁盘" + driver + "挂载到目录" + path + "失败:" + respond.getContent());
            }
        } else {
            throw new RuntimeException("磁盘挂载参数错误!");
        }
        return respond.getContent();
    }

    /**
     * @Description : 服务安装后，发送通知消息
     */
    protected void appNotice(AppEvent event, String serveName, String msg) {
        this.broadcastDriver.appNotice(this.hostname, event, serveName, msg);
    }

    /**
     * @Description : 获取该主机下的实时文件内容
     * @Note : ⚠️ 内容返回最后200行，文件不存在的时候抛出异常 !
     **/
    protected String getFileContent(String path) throws FileNotFoundException {
        RpcRespond<String> respond;
        if(path.contains("-root-")) {
            String b=path.replace("-root-","--");

            respond = this.rpcDriver.execute(
                    RpcRequest.createRequest(
                            this.hostname,
                            "/",
                            "[[ -f "+path+" ]] && tail -200 "+path+" || ([[ -f "+b+ " ]] && tail -200 "+b+")")
            );
        } else {
            respond = this.rpcDriver.execute(
                    RpcRequest.createRequest(
                            hostname,
                            "/",
                            "tail -200 "+path)
            );
        }
        if (!respond.isSuccess()) {
            log.error("{}上获取日志失败",hostname);
        }
        return respond.getContent();
    }

}
