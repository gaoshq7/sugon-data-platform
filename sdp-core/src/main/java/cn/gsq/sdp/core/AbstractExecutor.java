package cn.gsq.sdp.core;

import cn.gsq.sdp.Operation;
import cn.gsq.sdp.core.annotation.Available;
import cn.gsq.sdp.core.annotation.Function;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.proxy.Enhancer;
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

    @Setter
    @Getter
    private AppStatus status =AppStatus.CHECK_AVAILABLE;// app状态

    // 缓存 functionID -> Method
    private final Map<String, Method> functionCache = new ConcurrentHashMap<>();

    protected final List<Operation> functions;   // 功能列表

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

    public void proxyFunction(String functionID, Object... params) {
        //1、根据functionID找到函数
        //2、将status的值修改为注解中指定的值
        //3、使用cglib代理执行函数
        //4、执行完毕还原status的值

        try {
            Method method = resolveFunction(functionID);
            if (method == null) {
                throw new IllegalArgumentException("找不到 functionID 对应的方法: " + functionID);
            }

            // 用 CGLIB 生成一个带拦截器的 proxy（拦截器内部会设/还原 status）
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(this.getClass());
            enhancer.setCallback(new StatusMethodInterceptor(this));
            Object proxy = enhancer.create();

            // 反射调用：注意参数匹配（此处假设 params 顺序、类型完全对应）
            method.invoke(proxy, params);
        } catch (Exception e) {
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

}
