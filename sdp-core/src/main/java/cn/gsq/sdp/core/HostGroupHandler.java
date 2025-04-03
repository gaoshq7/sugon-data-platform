package cn.gsq.sdp.core;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.HostGroupHandler
 *
 * @author : gsq
 * @date : 2025-03-03 09:53
 * @note : It's not technology, it's art !
 **/
@Slf4j
@Getter
@AllArgsConstructor
public enum HostGroupHandler {

    MASTER("master主机组", CollUtil.newArrayList(), 2, 2, "运行与使用终端交互的服务主进程"),

    COMMON("common主机组", CollUtil.newArrayList(), 3, -1, "运行分布式元数据服务进程"),

    WEB("web主机组", CollUtil.newArrayList(), 1, 1, "运行组件的页面终端服务进程"),

    DATA("data主机组", CollUtil.newArrayList(), 3, -1, "运行数据存储进程"),

    TASK("task主机组", CollUtil.newArrayList(), 3, -1, "运行数据计算进程"),

    HTAP("htap主机组", CollUtil.newArrayList(), 3, -1, "运行Doris服务计算存储进程"),

    OLAP("olap主机组", CollUtil.newArrayList(), 2, -1, "运行Presto服务计算进程");

    private static final HashMap<String, HostGroupHandler> groups = MapUtil.newHashMap();

    private final String name;      // 分组名称

    private final List<AbstractProcess> processes;    // 进程集合

    private final int min;          // 主机数量下限

    private final int max;          // 主机数量上线

    private final String desc;      // 分组描述

    static {
        for(HostGroupHandler groupHandler : HostGroupHandler.values()) {
            groups.put(groupHandler.name, groupHandler);
        }
    }

    public static HostGroupHandler parse(String name) {
        if(groups.containsKey(name)){
            return groups.get(name);
        }
        return null;
    }

}
