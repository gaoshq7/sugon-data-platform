package cn.gsq.sdp.driver;

import cn.gsq.sdp.SshInfo;
import cn.hutool.core.thread.ThreadUtil;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.driver.SshDriver
 *
 * @author : gsq
 * @date : 2025-03-03 17:12
 * @note : It's not technology, it's art !
 **/
public interface SshDriver {

    /**
     * @Description : 启动galaxy agent代理
     * @note : ⚠️ 有错误要抛出来 !
     **/
    void startAgent(String hostname);

    /**
     * @Description : 停止galaxy agent代理
     * @note : ⚠️ 有错误要抛出来 !
     **/
    @Deprecated
    void stopAgent(String hostname);

    /**
     * @Description : 重启galaxy agent代理
     * @note : An art cell !
     **/
    @Deprecated
    default void restart(String hostname) {
        stopAgent(hostname);
        ThreadUtil.safeSleep(1000);
        startAgent(hostname);
    }

    /**
     * @Description : 通过基础方式添加主机
     * @note : ⚠️ 有错误要抛出来 !
     **/
    void addHost(SshInfo info) throws Exception;

}
