package cn.gsq.sdp.core.annotation;

import java.lang.annotation.*;

/**
 * Project : galaxy
 * Class : cn.gsq.sdp.core.annotation.Function
 *
 * @author : gsq
 * @date : 2021-04-22 17:11
 * @note : It's not technology, it's art !
 **/
@Inherited
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Function {    // AOP切面成一个job

    String id();

    String name();

    boolean isReveal() default true;

}
