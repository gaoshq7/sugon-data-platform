package cn.gsq.sdp.core;

import cn.hutool.core.thread.ThreadUtil;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.Action
 *
 * @author : gsq
 * @date : 2025-02-27 14:52
 * @note : It's not technology, it's art !
 **/
public interface Action extends Thing {

    void start();

    void stop();

    default void restart() {
        this.stop();
        ThreadUtil.safeSleep(5000);
        this.start();
    }

}
