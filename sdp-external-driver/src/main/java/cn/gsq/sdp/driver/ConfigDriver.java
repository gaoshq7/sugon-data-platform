package cn.gsq.sdp.driver;

import cn.gsq.sdp.ConfigBranch;
import cn.gsq.sdp.ConfigItem;

import java.util.List;
import java.util.Set;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.driver.ConfigDriver
 *
 * @author : gsq
 * @date : 2025-03-03 16:02
 * @note : It's not technology, it's art !
 **/
public interface ConfigDriver {

    /**
     * @Description : 同步配置文件
     * @note : ⚠️ 有错误要抛出 !
     **/
    void conform(ConfigBranch branch, List<ConfigItem> items);

    /**
     * @Description : 程序启动加载配置文件
     * @note : ⚠️ 前三个参数确定一个配置文件的分支，type是配置文件的数据类型，有错误不可抛出 !
     **/
    List<ConfigItem> loadConfigItems(ConfigBranch branch);

    /**
     * @Description : 扩展配置文件覆盖的主机
     * @note : ⚠️ 有错误要抛出 !
     **/
    void extendBranchHosts(ConfigBranch branch, Set<String> hostnames);

    /**
     * @Description : 缩减配置文件覆盖的主机
     * @note : ⚠️ 有错误要抛出 !
     **/
    void abandonBranchHosts(ConfigBranch branch, Set<String> hostnames);

    /**
     * @Description : 销毁配置文件分支
     * @note : ⚠️ 有错误要抛出 !
     **/
    void destroy(ConfigBranch branch);

    /**
     * @Description : 根据配置文件分支获取配置项的元数据信息
     * @note : ⚠️ 不可抛出错误 !
     **/
    @Deprecated
    ConfigItem getItemMetadata(ConfigBranch branch, String key);

}
