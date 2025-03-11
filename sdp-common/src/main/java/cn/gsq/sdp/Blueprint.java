package cn.gsq.sdp;

import cn.hutool.json.JSONUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.Blueprint
 *
 * @author : gsq
 * @date : 2021-05-13 15:01
 * @note : It's not technology, it's art !
 **/
@Getter
@Setter
@Accessors(chain = true)
public class Blueprint {

    private List<Serve> serves; // 预安装服务集合

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class Serve {

        private String servename;   // 服务名称

        private List<Config> configs;   // 配置文件集合

        private List<Process> processes;    // 进程集合

        /**
         * @Description : 根据名称获取配置文件
         * @Param : [name]
         * @Return : cn.gsq.sdp.core.serve.entity.blueprint.Blueprint.Config
         * @Author : gsq
         * @Date : 4:58 下午
         * @note : An art cell !
        **/
        public Config getConfigByName(String name) {
            return configs.stream()
                    .filter(config -> config.getConfigname().equals(name))
                    .findFirst().orElse(null);
        }

        /**
         * @Description : 根据名称获取进程
         * @Param : [name]
         * @Return : cn.gsq.sdp.core.serve.entity.blueprint.Blueprint.Process
         * @Author : gsq
         * @Date : 4:56 下午
         * @note : An art cell !
        **/
        public Process getProcessByName(String name) {
            return processes.stream()
                    .filter(process -> process.getProcessname().equals(name))
                    .findFirst().orElse(null);
        }

        /**
         * @Description : 获取服务涉及到的所有主机名
         * @Param : []
         * @Return : java.util.List<java.lang.String>
         * @Author : gsq
         * @Date : 4:39 下午
         * @note : An art cell !
        **/
        public List<String> getAllProcessHostnames() {
            return processes.stream()
                    .flatMap(process -> process.getHostnames().stream())
                    .distinct().collect(Collectors.toList());
        }

    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class Config {

        private String configname;  // 配置文件名称

        private Map<String, Map<String, String>> branches;  // 配置文件分支内容

        /**
         * @Description : 获取默认分支配置内容
         * @Param : []
         * @Return : java.util.Map<java.lang.String,java.lang.String>
         * @Author : gsq
         * @Date : 2:42 下午
         * @note : An art cell !
        **/
        public Map<String, String> getDefaultContent() {
            return this.branches.getOrDefault(SdpPropertiesFinal.DEFAULT_CHAR, null);
        }

        /**
         * @Description : 获取分支配置文件内容
         * @Param : [name]
         * @Return : java.util.Map<java.lang.String,java.lang.String>
         * @Author : gsq
         * @Date : 2:04 下午
         * @note : An art cell !
        **/
        public Map<String, String> getBranchContent(String name) {
            return this.branches.getOrDefault(name, null);
        }

    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class Process {

        private String processname; // 进程名称

        private List<String> hostnames;    // 进程运行的主机

    }

    /**
     * @Description : 覆盖toString函数
     * @Param : []
     * @Return : java.lang.String
     * @Author : gsq
     * @Date : 2:05 下午
     * @note : An art cell !
    **/
    @Override
    public String toString(){
        return JSONUtil.toJsonPrettyStr(this);
    }

}
