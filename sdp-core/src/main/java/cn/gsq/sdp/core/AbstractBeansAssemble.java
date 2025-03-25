package cn.gsq.sdp.core;

import cn.gsq.common.config.GalaxySpringUtil;
import cn.gsq.sdp.driver.*;
import lombok.Getter;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.AbstractBeansAssemble
 *
 * @author : gsq
 * @date : 2025-03-06 16:34
 * @note : It's not technology, it's art !
 **/
@Getter
public abstract class AbstractBeansAssemble implements Thing {

    /* 管理器 */

    protected transient AbstractSdpManager sdpManager;  // sdp环境管理器

    protected transient AbstractHostManager hostManager;    // 主机管理接口

    /* 驱动集合 */

    protected transient SshDriver sshDriver;    // 主机基础驱动器

    protected transient RpcDriver rpcDriver;    // rpc驱动器

    protected transient BroadcastDriver broadcastDriver; // 广播驱动器

    protected transient PilotDriver pilotDriver;    // agent启停驱动器

    protected transient LogDriver logDriver;    // 运行日志驱动器

    protected transient HostDriver hostDriver;  // 主机驱动器

    protected transient ServeDriver serveDriver;    // 服务持久化驱动器

    protected transient ConfigDriver configDriver;  // 配置文件驱动器

    protected transient ProcessDriver processDriver;    // 进程持久化驱动器

    protected transient ResourceDriver resourceDriver;  // 文件资源驱动器

    protected void setSdpManager(AbstractSdpManager sdpManager) {
        this.sdpManager = sdpManager;
    }

    protected void setHostManager(AbstractHostManager hostManager) {
        this.hostManager = hostManager;
    }

    protected void setSshDriver(SshDriver sshDriver) {
        this.sshDriver = sshDriver;
    }

    protected void setRpcDriver(RpcDriver rpcDriver) {
        this.rpcDriver = rpcDriver;
    }

    protected void setBroadcastDriver(BroadcastDriver broadcastDriver) {
        this.broadcastDriver = broadcastDriver;
    }

    protected void setPilotDriver(PilotDriver pilotDriver) {
        this.pilotDriver = pilotDriver;
    }

    protected void setLogDriver(LogDriver logDriver) {
        this.logDriver = logDriver;
    }

    protected void setHostDriver(HostDriver hostDriver) {
        this.hostDriver = hostDriver;
    }

    protected void setServeDriver(ServeDriver serveDriver) {
        this.serveDriver = serveDriver;
    }

    protected void setConfigDriver(ConfigDriver configDriver) {
        this.configDriver = configDriver;
    }

    protected void setProcessDriver(ProcessDriver processDriver) {
        this.processDriver = processDriver;
    }

    protected void setResourceDriver(ResourceDriver resourceDriver) {
        this.resourceDriver = resourceDriver;
    }

    protected void setDrivers() {
        this.setSdpManager(GalaxySpringUtil.getBean(AbstractSdpManager.class));
        this.setHostManager(GalaxySpringUtil.getBean(AbstractHostManager.class));
        this.setBroadcastDriver(GalaxySpringUtil.getBean(BroadcastDriver.class));
        this.setConfigDriver(GalaxySpringUtil.getBean(ConfigDriver.class));
        this.setHostDriver(GalaxySpringUtil.getBean(HostDriver.class));
        this.setLogDriver(GalaxySpringUtil.getBean(LogDriver.class));
        this.setPilotDriver(GalaxySpringUtil.getBean(PilotDriver.class));
        this.setRpcDriver(GalaxySpringUtil.getBean(RpcDriver.class));
        this.setSshDriver(GalaxySpringUtil.getBean(SshDriver.class));
        this.setServeDriver(GalaxySpringUtil.getBean(ServeDriver.class));
        this.setProcessDriver(GalaxySpringUtil.getBean(ProcessDriver.class));
        this.setResourceDriver(GalaxySpringUtil.getBean(ResourceDriver.class));
    }

}
