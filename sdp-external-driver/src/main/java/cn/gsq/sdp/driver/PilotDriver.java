package cn.gsq.sdp.driver;

import cn.hutool.core.thread.ThreadUtil;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.driver.PilotDriver
 *
 * @author : gsq
 * @date : 2025-03-05 10:35
 * @note : It's not technology, it's art !
 **/
@Deprecated
public interface PilotDriver {

    /**
     * @Description : 启动pilot服务
     * @Param : [hostname]
     * @Return : void
     * @Author : syu
     * @Date : 2024/3/29
     */
    @Deprecated
    void startPilot(String hostname);


    /**
     * @Description : 停止pilot服务
     * @Param : [hostname]
     * @Return : void
     * @Author : syu
     * @Date : 2024/3/29
     */
    @Deprecated
    void stopPilot(String hostname);


    /**
     * @Description : 重启pilot服务
     * @Param : [hostname]
     * @Return : void
     * @Author : syu
     * @Date : 2024/3/29
     */
    @Deprecated
    default void restart(String hostname) {
        startPilot(hostname);
        ThreadUtil.safeSleep(1000);
        stopPilot(hostname);
    }

    /**
     * @Description : 机器上的pilot服务是否正常
     * @Param : [hostname]
     * @Return : boolean
     * @Author : syu
     * @Date : 2024/3/29
     */
    @Deprecated
    boolean isActive(String hostname);

    /**
     * @Description : 启动主机
     * @Param :
     * @Return :
     * @Author : xyy
     * @Date : 2024/5/23
     * @note : ⚠️ 有错误要抛出来 !
     **/
    @Deprecated
    void startAgent(String hostname);

    /**
     * @Description : 停止主机
     * @Param :
     * @Return :
     * @Author : xyy
     * @Date : 2024/5/23
     * @note : ⚠️ 有错误要抛出来 !
     **/
    @Deprecated
    void stopAgent(String hostname);

    /**
     * @Description : 重启主机
     * @Param :
     * @Return :
     * @Author : xyy
     * @Date : 2024/5/23
     * @note : ⚠️ 有错误要抛出来 !
     **/
    @Deprecated
    void restartAgent(String hostname);

}
