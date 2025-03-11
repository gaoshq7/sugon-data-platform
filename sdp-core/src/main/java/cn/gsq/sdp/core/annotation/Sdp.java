package cn.gsq.sdp.core.annotation;

import java.lang.annotation.*;

/**
 * Project : galaxy
 * Class : cn.gsq.sdp.core.annotation.Sdp
 *
 * @author : gsq
 * @date : 2021-04-15 10:26
 * @note : It's not technology, it's art !
 **/
@Inherited
@Documented
@Target({ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sdp {

    String version();

}
