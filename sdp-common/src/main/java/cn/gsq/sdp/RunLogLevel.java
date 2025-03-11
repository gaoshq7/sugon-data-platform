package cn.gsq.sdp;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.RunLogLevel
 *
 * @author : gsq
 * @date : 2025-03-07 10:52
 * @note : It's not technology, it's art !
 **/
@Getter
@AllArgsConstructor
public enum RunLogLevel {

    INFO("信息"),

    WARN("警告"),

    ERROR("错误");

    private final String name;

}
