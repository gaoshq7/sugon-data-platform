package cn.gsq.sdp.core;

import cn.gsq.sdp.Operation;
import cn.gsq.sdp.core.annotation.Available;
import cn.gsq.sdp.core.annotation.Function;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
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
public abstract class AbstractExecutor extends AbstractBeansAssemble implements Action {

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

}
