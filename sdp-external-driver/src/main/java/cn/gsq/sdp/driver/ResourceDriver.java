package cn.gsq.sdp.driver;

import cn.gsq.sdp.Resource;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.driver.ResourceDriver
 *
 * @author : gsq
 * @date : 2025-03-24 09:09
 * @note : It's not technology, it's art !
 **/
public interface ResourceDriver {

    /**
     * @Description : 下载安装包
     **/
    void download(Resource resource);

    /**
     * @Description : sdp版本是否可用
     **/
    boolean isSdpAvailable(String version);

    /**
     * @Description : sdp版本是否可用
     **/
    boolean isServeAvailable(String version, String servename);

}
