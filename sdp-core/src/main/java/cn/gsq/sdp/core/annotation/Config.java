package cn.gsq.sdp.core.annotation;

import cn.gsq.sdp.core.AbstractServe;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Project : galaxy
 * Class : cn.gsq.sdp.core.annotation.Config
 *
 * @author : gsq
 * @date : 2021-04-22 14:04
 * @note : It's not technology, it's art !
 **/
@Component
@Inherited
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Config {

    Class<? extends AbstractServe> master();     // 所属服务

    String type() default "";  // 配置文件的数据类型（使用者自行约定）

    String path();      //  文件地址

    String description();   // 配置文件描述信息

    String[] branches() default {};    // 分支文件名称

    boolean show() default true;      // 是否展示给使用者

    int order();

}
