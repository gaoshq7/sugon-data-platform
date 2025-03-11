package cn.gsq.sdp.core;

import cn.hutool.core.collection.CollUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.ProcessHandler
 *
 * @author : gsq
 * @date : 2025-03-03 09:54
 * @note : It's not technology, it's art !
 **/
@Slf4j
@Getter
@AllArgsConstructor
public enum ProcessHandler {

    MASTER(1, "master", "主进程") {
        @Override
        public <T extends AbstractHost> boolean isAvailable(AbstractProcess<T> process) {
            List<T> hosts = process.getHosts();
            // 未安装该进程
            if(CollUtil.isEmpty(hosts)) return false;
            return getDownHosts(process).size() == 0;
        }
    },

    SLAVE(2, "slave", "从进程") {
        @Override
        public <T extends AbstractHost> boolean isAvailable(AbstractProcess<T> process) {
            List<T> hosts = process.getHosts();
            // 未安装该进程
            if(CollUtil.isEmpty(hosts)) return false;
            return getDownHosts(process).size() < hosts.size() / 2;
        }
    },

    WATCH(3, "watch", "守护进程") {
        @Override
        public <T extends AbstractHost> boolean isAvailable(AbstractProcess<T> process) {
            // 判断同主进程
            return MASTER.isAvailable(process);
        }
    },

    ALONE(4, "alone", "独立进程") {
        @Override
        public <T extends AbstractHost> boolean isAvailable(AbstractProcess<T> process) {
            // 判断同主进程
            return MASTER.isAvailable(process);
        }
    },

    ELECTION(5, "election", "选举进程") {
        @Override
        public <T extends AbstractHost> boolean isAvailable(AbstractProcess<T> process) {
            return SLAVE.isAvailable(process);
        }
    };

    private static final HashMap<Integer, ProcessHandler> handlers = new HashMap<>();

    static {
        for(ProcessHandler processType : ProcessHandler.values()) {
            handlers.put(processType.code, processType);
        }
    }

    /**
     * @Description : 枚举方法
     **/
    public static ProcessHandler parse(Integer code) {
        if(handlers.containsKey(code)){
            return handlers.get(code);
        }
        return null;
    }

    private final Integer code;

    private final String key;

    private final String value;

    /**
     * @Description : 获取宕机的主机名列表
     **/
    protected <T extends AbstractHost> List<T> getDownHosts(AbstractProcess<T> process) {
        List<T> hosts = process.getHosts();
        return hosts.parallelStream()
                .filter(getPredicate(process).negate())
                .collect(Collectors.toList());
    }

    /**
     * @Description : 获取判断进程是否宕机算法
     **/
    private <T extends AbstractHost> Predicate<T> getPredicate(AbstractProcess<T> process) {
        // 获取判断规则
        return host -> getRule().opinion(host, process);
    }

    /**
     * @Description : 获取判断进程是否宕机接口规则
     **/
    private Rule getRule() {
        return new Rule() {
            @Override
            public <T extends AbstractHost> boolean opinion(AbstractHost host, AbstractProcess<T> process) {
                boolean available;
                try {
                    if(!host.isHostActive()) {
                        // 代理下线
                        available = false;
                    } else {
                        available = host.isProcessActive(process);
                    }
                } catch (Exception e) {
                    available = false;
                    log.error("获取{}主机的{}进程状态错误: {}", host.getName(), process.getName(), e);
                }
                return available;
            }
        };
    }

    /**
     * @Description : 进程是否安装
     **/
    public <T extends AbstractHost> boolean isInstalled(AbstractProcess<T> process){
        return CollUtil.isNotEmpty(process.getHosts());
    }

    /**
     * @Description : 进程是否可用
     **/
    public abstract <T extends AbstractHost> boolean isAvailable(AbstractProcess<T> process);

    /**
     * @Description : 算法接口
     **/
    interface Rule {

        <T extends AbstractHost> boolean opinion(AbstractHost host, AbstractProcess<T> process);

    }

}
