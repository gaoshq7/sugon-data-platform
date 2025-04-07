package cn.gsq.sdp.core;

import cn.gsq.sdp.HostInfo;

import java.util.List;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.HostManager
 *
 * @author : gsq
 * @date : 2025-03-03 14:45
 * @note : It's not technology, it's art !
 **/
public interface HostManager {

    /**
     * @Description : 添加主机
     **/
    void addHosts(HostInfo... hostInfos);

    /**
     * @Description : 删除主机
     **/
    void removeHosts(String ... hostnames);

    /**
     * @Description : 修改主机分组信息
     **/
    void updateHostGroups(String hostname, List<String> groups);

    /**
     * @Description : 获取当前模式下的主机分组
     * @Note : ⚠️ 不初始化部署模式返回空数组 !
     **/
    List<HostGroup> getHostGroups();

    /**
     * @Description : 获取所有主机
     **/
    List<AbstractHost> getHosts();

    /**
     * @Description : 根据名称获取主机
     **/
    AbstractHost getHostByName(String name);

}
