package cn.gsq.sdp.core;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.ClassifyHandler
 *
 * @author : gsq
 * @date : 2025-03-03 09:52
 * @note : It's not technology, it's art !
 **/
@Slf4j
@Getter
@AllArgsConstructor
public enum ClassifyHandler {

    BIGDATA("大数据组件"),

    BASICS("基础组件"),

    TOOL("工具服务"),

    CUSTOM("自定义服务"),

    OTHER("其它类型");

    private final String name;  // 类型

}
