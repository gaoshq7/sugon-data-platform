package cn.gsq.sdp.core;

import cn.gsq.sdp.AppEvent;
import cn.gsq.sdp.Blueprint;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.thread.ThreadUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.AbstractApp
 *
 * @author : gsq
 * @date : 2025-02-28 11:01
 * @note : It's not technology, it's art !
 **/
@Slf4j
@Getter
public abstract class AbstractApp extends AbstractExecutor {

    protected List<AbstractApp> parents = CollUtil.newArrayList();    // 依赖的进程或服务集合

    protected List<AbstractApp> children = CollUtil.newArrayList();   // 被依赖的进程或集合

    protected String description;

    @Override
    public String getName(){
        // 各子类酌情覆盖
        return this.getClass().getSimpleName();
    }

    /**
     * @Description : 获取依赖服务或进程角色集合
     **/
    public List<? extends AbstractApp> getParents() {
        return this.parents;
    }

    /**
     * @Description : 获取被依赖服务或进程集合
     **/
    public List<? extends AbstractApp> getChildren() {
        return this.children;
    }

    /**
     * @Description : 应用事件广播通知
     * @note : ⚠️ 该函数广播到所有节点 !
     **/
    protected void broadcast(AppEvent event, String appName, String msg) {
        for (AbstractHost host : this.hostManager.getHosts()) {
            if(host.isHostActive()) {
                host.appNotice(event, appName, msg);
            }
        }
    }

    /**
     * @Description : 等待应用正常可用
     * @note : ⚠️ 3秒一次轮询检测，1分钟后抛出错误 !
     **/
    protected void await() {
        int i = 0;
        while (i < getTimes()) {
            ThreadUtil.safeSleep(3 * 1000);
            if(isAvailable()) return;
            i++;
        }
        throw new RuntimeException("应用" + this.getName() + "可用性响应超时, 请检查日志!");
    }

    protected Integer getTimes() {
        return 60;
    }

    /**
     * @Description : 应用初始化
     **/
    protected abstract void initProperty();

    /**
     * @Description : 应用安装
     **/
    public abstract void install(Blueprint.Serve serve);

    /**
     * @Description : 应用恢复初始化状态
     **/
    public abstract void recover();

    /**
     * @Description : 服务或进程是否安装
     **/
    public abstract boolean isInstalled();

    /**
     * @Description : 服务或进程是否可用
     **/
    public abstract boolean isAvailable();

}
