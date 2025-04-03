package cn.gsq.sdp.core.annotation;

import cn.gsq.sdp.core.HostGroup;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
* Project : sugon-data-platform
* Class : cn.gsq.sdp.core.annotation.Group
* @author : gsq
* @date : 2025-04-02 15:38
* @note : It's not technology, it's art !
**/
@Retention(RetentionPolicy.RUNTIME)
public @interface Group {

    Class<? extends Enum<? extends HostGroup>> mode();  // 所属模式

    String name();  // 分组名称

}
