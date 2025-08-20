package cn.gsq.sdp.core;

import cn.gsq.sdp.Operation;
import cn.gsq.sdp.core.annotation.Available;
import cn.gsq.sdp.core.annotation.Function;
import cn.gsq.sdp.core.annotation.Status;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.AbstractExecutor
 *
 * @author : gsq
 * @date : 2025-02-27 17:28
 * @note : It's not technology, it's art !
 **/
@Slf4j
public abstract class AbstractExecutor extends AbstractSdpComponent implements Action {

    @Getter
    private AppStatus status = AppStatus.CHECK_AVAILABLE;// app状态

    // 缓存 functionID -> Method
    private final Map<String, Method> functionCache = new ConcurrentHashMap<>();

    protected final List<Operation> functions;   // 功能列表

    @Setter
    protected Runnable task;//一个函数接口

    protected AbstractExecutor() {
        this.functions = Arrays.stream(this.getClass().getMethods())
                .filter(method -> method.isAnnotationPresent(Function.class) && method.getAnnotation(Function.class).isReveal())
                .map(method -> new Operation(method.getAnnotation(Function.class).id()
                        , method.getAnnotation(Function.class).name()))
                .collect(Collectors.toList());
    }

    /**
     * @Description : 获取功能清单（不包含非展示属性功能）
     **/
    public List<Operation> getFunctions() {
        java.util.function.Function<Operation, Operation> decide = operation -> {
            boolean isEnable = true;    // 函数默认可执行
            List<Method> methods = CollUtil.toList(this.getClass().getMethods());
            Method method = CollUtil.findOne(
                    methods,
                    x -> x.getAnnotation(Available.class) != null &&
                            x.getAnnotation(Available.class).fid().equals(operation.getId())
            );
            if(ObjectUtil.isNotNull(method)) {
                // 与function id匹配的函数必须存在；存在的函数参数列表必须为空；存在的函数返回值必须是boolean型变量
                boolean isClass = method.getReturnType().isAssignableFrom(Boolean.class);
                boolean isBase = method.getReturnType().getName().equals("boolean");
                if(ObjectUtil.isNotEmpty(method) && ReflectUtil.isEmptyParam(method) && (isBase || isClass)) {
                    ReflectUtil.setAccessible(method);
                    isEnable = ReflectUtil.invoke(this, method);
                }
            }
            return operation.setEnable(isEnable);
        };
        return CollUtil.map(this.functions, decide, true);
    }

    /**
     * @Description : 反射执行function函数
     * @Param : executor：执行对象，functionID：函数id,logInfo:日志对象，params：function函数参数列表
     * @Return :
     * @Author : xyy
     * @Date : 2024/6/13
     * @note :
     **/
    @Deprecated
    public void doFunction(String functionID, Object... params) {
        for(Method method : this.getClass().getMethods()) {
            Function function = AnnotationUtils.findAnnotation(method, Function.class);
            if(ObjectUtil.isNotNull(function) && function.id().equals(functionID)) {
                try {
                    if(ObjectUtil.isEmpty(params)){
                        method.invoke(this);
                    }else {
                        method.invoke(this, params);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                break;
            }
        }
    }

    /**
     * 代理执行函数方法
     * 根据函数ID查找对应方法，设置状态并执行该方法
     *
     * @param functionID 函数唯一标识符，用于查找对应的方法
     * @param params 可变参数列表，传递给目标方法的参数
     */
    public void proxyFunction(String functionID, Object... params) {
        try {

            //1、根据functionID找到函数
            Method method = resolveFunction(functionID);
            if (method == null) {
                throw new IllegalArgumentException("找不到 functionID 对应的方法: " + functionID);
            }

            //2、将status的值修改为注解中指定的值
            Status statusAnno = method.getAnnotation(Status.class);
            status = statusAnno != null ? statusAnno.value() : AppStatus.CHECK_AVAILABLE;
            if(ObjectUtil.isNotNull(task))
                task.run();// 发送通知

            //3、执行函数
            method.invoke(this, params);

            //4、执行完毕还原status的值
            status=AppStatus.CHECK_AVAILABLE;
            if(ObjectUtil.isNotNull(task))
                task.run();// 发送通知
        } catch (Exception e) {
            //4、执行完毕还原status的值
            status=AppStatus.CHECK_AVAILABLE;
            if(ObjectUtil.isNotNull(task))
                task.run();// 发送通知
            // 你可以在这里统一日志/错误处理
            throw new RuntimeException("通过 proxyFunction 执行失败: " + e.getMessage(), e);
        }
    }

    private Method resolveFunction(String functionID) {
        if (functionCache.containsKey(functionID)) {
            return functionCache.get(functionID);
        }

        // 扫描当前类及父类的方法，找带 @Function 且 id 匹配的
        Class<?> cls = this.getClass();
        while (cls != null && cls != Object.class) {
            for (Method m : cls.getDeclaredMethods()) {
                Function funcAnno = m.getAnnotation(Function.class);
                if (funcAnno != null && functionID.equals(funcAnno.id())) {
                    m.setAccessible(true);
                    functionCache.put(functionID, m);
                    return m;
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    public String getExecutorStatus() {
        if(status.equals(AppStatus.CHECK_AVAILABLE)){
            //根据具体的服务 进程 主机 判断状态 结果为 正常/故障
            boolean isAvailable = false;
            if(this instanceof AbstractApp)
                isAvailable = ((AbstractApp)this).isAvailable();
            if(this instanceof AbstractHost)
                isAvailable = ((AbstractHost)this).isHostActive();
            if(isAvailable) {
                return AppStatus.RUNNING.getName();
            } else {
                return AppStatus.FAULT.getName();
            }
        } else {
            return status.getName();
        }
    }


}
