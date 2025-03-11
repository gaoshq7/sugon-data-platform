package cn.gsq.sdp.core.annotation;

import cn.gsq.sdp.core.AbstractServe;
import cn.gsq.sdp.core.ClassifyHandler;
import cn.gsq.sdp.core.ServeHandler;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Project : galaxy
 * Class : cn.gsq.sdp.core.annotation.serve
 *
 * @author : gsq
 * @date : 2021-04-22 14:09
 * @note : It's not technology, it's art !
 **/
@Component
@Inherited
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Serve {

    String version();       // 服务版本号

    ServeHandler handler();     // 服务模式（默认主从模式）

    ClassifyHandler type() default ClassifyHandler.OTHER;     // 服务类型（默认其它类型）

    Class<? extends AbstractServe>[] depends() default {};       // 服务依赖

    String[] appends() default {};      // 外部附加配置文件

    String[] labels() default {};       // 服务标签

    String description();   // 服务描述信息

    int order();      // 服务集合排列顺序（不可重复）

}
