package cn.gsq.sdp.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.AbstractSdpManager
 *
 * @author : gsq
 * @date : 2025-02-28 11:28
 * @note : It's not technology, it's art !
 **/
@Slf4j
@Getter
public abstract class AbstractSdpManager extends AbstractBeansAssemble implements SdpManager {

    protected String version;   // SDP版本号

    protected String home;  // SDP环境安装包所在目录

    protected List<AbstractServe> serves;   // 当前sdp版本中的所有服务

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

    @Override
    public String getName() {
        return "SDP系统";
    }

    @Override
    public Type getType() {
        return Type.SYSTEM;
    }

}
