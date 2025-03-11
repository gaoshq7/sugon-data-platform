package cn.gsq.sdp.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.Thing
 *
 * @author : gsq
 * @date : 2025-02-27 14:47
 * @note : It's not technology, it's art !
 **/
public interface Thing {

    String getName();

    Type getType();

    @Getter
    @AllArgsConstructor
    enum Type {

        SYSTEM(1, "SDP系统") {},

        CONFIG(2, "配置文件") {},

        SERVE(3, "组件服务") {},

        PROCESS(4, "服务进程") {},

        ROLE(5, "进程角色") {},

        HOST(6, "集群主机") {},

        MANAGER(6, "主机管理") {};

        private final Integer code;   // 类型编号

        private final String nane;  // 类型名称

        private static final HashMap<Integer, Type> things = new HashMap<>();

        static {
            for(Type type : Type.values()){
                things.put(type.getCode(), type);
            }
        }

        public static Type parse(Integer code){
            if(things.containsKey(code)){
                return things.get(code);
            }
            return null;
        }

    }

}
