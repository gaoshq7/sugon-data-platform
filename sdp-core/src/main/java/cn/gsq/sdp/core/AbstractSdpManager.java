package cn.gsq.sdp.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.AbstractSdpManager
 *
 * @author : gsq
 * @date : 2025-02-28 11:28
 * @note : It's not technology, it's art !
 **/
@Slf4j
public abstract class AbstractSdpManager extends AbstractBeansAssemble implements SdpManager {

    @Getter
    protected String version;   // SDP版本号

    @Getter
    protected String home;  // SDP环境安装包所在目录

    @Getter
    protected List<AbstractServe> serves;   // 当前sdp版本中的所有服务

    protected Map<AbstractServe, List<AbstractConfig>> configs;     // 当前sdp版本中的配置文件集合

    protected Map<AbstractServe, List<AbstractProcess<AbstractHost>>> processes;      // 当前sdp版本中的进程集合

    /**
     * @Description : 获取主机管理员实例
     **/
    @Override
    public AbstractHostManager getHostManager() {
        return this.hostManager;
    }

    protected void setVersion(String version) {
        this.version = version;
    }

    protected void setHome(String home) {
        this.home = home;
    }

    protected void setServes(List<AbstractServe> serves) {
        this.serves = serves;
    }

    protected void setConfigs(Map<AbstractServe, List<AbstractConfig>> configs) {
        this.configs = configs;
    }

    protected void setProcesses(Map<AbstractServe, List<AbstractProcess<AbstractHost>>> processes) {
        this.processes = processes;
    }

    @Override
    public String getName() {
        return "SDP系统";
    }

    @Override
    public Type getType() {
        return Type.SYSTEM;
    }

}
