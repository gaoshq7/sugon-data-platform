package cn.gsq.sdp.core;

import cn.gsq.sdp.core.annotation.Status;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

/**
 * Project : galaxy
 * Class : cn.gsq.sdp.core.StatusMethodInterceptor
 *
 * @author : xyy
 * @since : 2025-08-06 09:34
 **/
public class StatusMethodInterceptor implements MethodInterceptor {

    private final AbstractExecutor target;

    public StatusMethodInterceptor(AbstractExecutor target) {
        this.target = target;
    }

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        // 读取注解上的 status（方法级优先，没的话可以扩展到类级）
        Status statusAnno = method.getAnnotation(Status.class);
        AppStatus previous = target.getStatus();

        if (statusAnno != null) {
            target.setStatus(statusAnno.value());
        }

        try {
            Object result = proxy.invoke(target, args);
            // 成功后恢复
            target.setStatus(previous);
            return result;
        } catch (Throwable t) {
            // 恢复原状态再抛
            target.setStatus(previous);
            throw t;
        }
    }
}
