package cn.gsq.sdp.core.annotation;

import java.lang.annotation.*;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.annotation.Mode
 *
 * @author : gsq
 * @date : 2025-04-01 15:35
 * @note : It's not technology, it's art !
 **/
@Inherited
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Mode {

    String value();

}
