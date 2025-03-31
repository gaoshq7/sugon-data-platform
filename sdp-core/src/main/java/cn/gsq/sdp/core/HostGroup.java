package cn.gsq.sdp.core;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.HostGroup
 *
 * @author : gsq
 * @date : 2025-03-31 11:56
 * @note : It's not technology, it's art !
 **/
public interface HostGroup {

    String name();   // 主机分组名称

    int min();      // 最小部署规模

    int max();      // 最大部署规模

    String description();   // 分组描述信息

}
