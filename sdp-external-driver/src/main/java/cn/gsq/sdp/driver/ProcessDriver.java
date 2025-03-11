package cn.gsq.sdp.driver;

import java.util.Collection;
import java.util.List;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.driver.ProcessDriver
 *
 * @author : gsq
 * @date : 2025-03-03 17:18
 * @note : It's not technology, it's art !
 **/
public interface ProcessDriver {

    /**
     * @Description : 系统启动时初始化进程拓扑图
     * @note : ⚠️ 返回进程运行的节点 !
     **/
    List<String> initHosts(String processname);

    /**
     * @Description : 添加新的主机
     * @note : ⚠️ 遇到错误需要抛出 !
     **/
    void addHosts(String processname, Collection<String> hostnames);

    /**
     * @Description : 删除主机
     * @note : ⚠️ 遇到错误需要抛出 !
     **/
    void delHosts(String processname, Collection<String> hostnames);

}
