package cn.gsq.sdp.driver;

import cn.gsq.sdp.*;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.driver.JobDriver
 *
 * @author : gsq
 * @date : 2025-03-03 16:35
 * @note : It's not technology, it's art !
 **/
public interface LogDriver {

    /**
     * @Description : 发送日志
     **/
    void log(RunLogLevel level, String msg);

}
