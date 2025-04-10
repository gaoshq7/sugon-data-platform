package cn.gsq.sdp.core.annotation;

import cn.gsq.sdp.core.*;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Project : galaxy
 * Class : cn.gsq.sdp.core.annotation.Process
 *
 * @author : gsq
 * @date : 2021-04-22 14:07
 * @note : It's not technology, it's art !
 **/
@Component
@Inherited
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Process {

    Class<? extends AbstractServe> master();     // 所属服务

    Class<? extends AbstractProcess>[] depends() default {};  // 前置进程

    Class<? extends AbstractProcess>[] companions() default {}; // 伴生进程

    Class<? extends AbstractProcess>[] excludes() default {};  // 互斥进程

    ProcessHandler handler();  // 进程类型

    Group[] groups() default {};   // 主机分组

    String mark();       // 进程获取唯一标示（ps -ef | grep {} | grep -v grep | awk '{print $2}'）

    String home();      // 命令执行根目录（以/usr/sdp/${version}为根目录）

    String start();     // 启动命令

    String stop();      // 停止命令

    boolean dynamic() default false; // 是否允许扩容

    String description() default "";   // 进程描述信息

    int order();  // 列表排序使用

    int min() default 1;  // 最小数量

    int max() default -1; // 最大数量

}
