package cn.gsq.sdp.driver;

import cn.gsq.sdp.AppEvent;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.driver.BroadcastDriver
 *
 * @author : gsq
 * @date : 2025-03-03 15:57
 * @note : It's not technology, it's art !
 **/
public interface BroadcastDriver {

    /**
     * @Description : 服务/进程事件广播通知
     **/
    void appNotice(String hostname, AppEvent appEvent, String serveName, String msg);

}
