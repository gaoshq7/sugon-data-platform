package cn.gsq.sdp.core;

import cn.gsq.graph.dag.Vertex;
import cn.gsq.sdp.core.utils.CommonUtil;
import cn.gsq.sdp.core.utils.DagUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.ServeHandler
 *
 * @author : gsq
 * @date : 2025-03-03 09:55
 * @note : It's not technology, it's art !
 **/
@Getter
@AllArgsConstructor
public enum ServeHandler {

    MASTER_SLAVE_MODE(1, "主从模式", "传统主从架构的分布式应用系统，容灾方案通常为HA（高可用）。"),

    MASTER_ELECTION_MODE(2, "选举模式", "多个进程通过选举机制确定主进程，通常自带容灾方案。"),   // 类似于zk

    STAND_ALONE_MODE(3, "单机模式", "独立运行在一台主机上的单个进程提供的系统服务。"),        // 单机单进程运行

    FRAGMENT_ALONE_MODE(4, "独立分片模式", "由若干个可能运行在不同主机上的微型系统组合而成的一套完整系统。"),     // 服务下有若干的进程，可能存在相互依赖，协同工作

    MULTI_ROLE_MODE(5, "角色模式", "进程相同，但在集群中职责不同的分布式应用系统。");       // 进程多角色模式，例如：presto、elasticsearch

    private static final HashMap<Integer, ServeHandler> handlers = MapUtil.newHashMap();

    static {
        for(ServeHandler serveDeploy : ServeHandler.values()){
            handlers.put(serveDeploy.code, serveDeploy);
        }
    }

    public static ServeHandler parse(Integer code) {
        if(handlers.containsKey(code)){
            return handlers.get(code);
        }
        return null;
    }

    private final Integer code;    // 服务类型

    private final String type;  // 类型

    private final String description; // 描述

    /**
     * @Description : 按顺序逐个启动进程
     * @Param : [resource, serve]
     * @Return : void
     * @Author : gsq
     * @Date : 10:22 上午
     * @note : ⚠️ 根据DAG图启动相关进程 !
     **/
    void start(AbstractServe serve) {
        // 根据DAG图启动服务
        for(Vertex<AbstractProcess<AbstractHost>> vertex : DagUtil.getDagResult(serve.getProcesses())) {
            AbstractProcess<AbstractHost> process = vertex.getTask();
            process.start();
            boolean result = CommonUtil.waitForSignal(process::isAvailable, 180000, 4000);
            if(!result) {
                throw new RuntimeException(process.getName() + " 进程启动失败，请移步环境中检查日志。");
            }
        }
    }

    /**
     * @Description : 按反序逐个停止进程
     * @Param : [resource, serve]
     * @Return : void
     * @Author : gsq
     * @Date : 10:24 上午
     * @note : ⚠️ 反向DAG图停止相关进程 !
     **/
    void stop(AbstractServe serve) {
        List<Vertex<AbstractProcess<AbstractHost>>> vertices =
                ListUtil.reverse(ListUtil.toList(DagUtil.getDagResult(serve.getProcesses())));
        for(Vertex<AbstractProcess<AbstractHost>> vertex : vertices) {
            AbstractProcess<AbstractHost> process = vertex.getTask();
            process.stop();
        }
    }

    /**
     * @Description : 服务是否可用
     * @Param : [processes]
     * @Return : boolean
     * @Author : gsq
     * @Date : 10:29 上午
     * @note : An art cell !
     **/
    boolean isAvailable(AbstractServe serve) {
        List<AbstractProcess<AbstractHost>> processes = serve.getProcesses();
        if(!isInstalled(serve)) return false;
        boolean check = true;
        for(AbstractProcess<AbstractHost> process : processes){
            // 必须安装且不可用是为服务不可用
            if(!process.isAvailable()){
                check = false;
                break;
            }
        }
        return check;
    }

    /**
     * @Description : 服务是否已安装
     * @Param : [processes]
     * @Return : boolean
     * @Author : gsq
     * @Date : 10:32 上午
     * @note : An art cell !
     **/
    boolean isInstalled(AbstractServe serve) {
        if(serve.isLocked()) {
            return true;   // 服务被锁定视为临界状态，视为已安装
        }
        List<AbstractProcess<AbstractHost>> processes = serve.getProcesses();
        boolean check = false;
        // 进程中存在节点是为已经安装
        for(AbstractProcess<AbstractHost> process : processes){
            if(process.isInstalled()){
                check = true;
                break;
            }
        }
        return check;
    }

}
