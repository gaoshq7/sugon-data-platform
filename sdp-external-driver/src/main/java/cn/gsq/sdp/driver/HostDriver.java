package cn.gsq.sdp.driver;

import cn.gsq.sdp.HostInfo;

import java.util.List;
import java.util.Set;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.driver.HostDriver
 *
 * @author : gsq
 * @date : 2025-03-03 16:13
 * @note : It's not technology, it's art !
 **/
public interface HostDriver {

    /**
     * @Description : 初始化系统主机资源列表
     **/
    Set<HostInfo> loadHosts();

    /**
     * @Description : 删除主机后的回调函数
     **/
    boolean removeHostsCallback(String ... hostnames);

    /**
     * @Description : 修改主机分组
     * @note : ⚠️ 不能抛出异常 !
     **/
    boolean updateHostGroups(String hostname, List<String> groups);

    /**
     * @Description : 判断主机是否存在于集群
     * @note : ⚠️ 不能抛出异常 !
     **/
    boolean isExist(String hostname);

    /**
     * @Description : 判断主机是否可用
     * @note : ⚠️ 不能抛出异常  !
     **/
    boolean isAlive(String hostname);

}
