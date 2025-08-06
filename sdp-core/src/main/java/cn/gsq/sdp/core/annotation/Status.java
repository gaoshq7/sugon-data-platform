package cn.gsq.sdp.core.annotation;

import cn.gsq.sdp.core.AppStatus;

import java.lang.annotation.*;

/**
 * Project : galaxy
 * Class : cn.gsq.sdp.core.annotation.Available
 *
 * @author : gsq
 * @date : 2024-01-23 14:28
 * @note : It's not technology, it's art !
 **/
@Inherited
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Status {

    AppStatus value();   // 状态

}
