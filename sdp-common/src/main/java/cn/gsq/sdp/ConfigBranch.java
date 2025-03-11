package cn.gsq.sdp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.ConfigBranch
 *
 * @author : gsq
 * @date : 2023-03-31 21:51
 * @note : It's not technology, it's art !
 **/
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ConfigBranch {

    private String branchName; // 分支名称

    private String configName;    // 配置文件名称

    private String serveName;   // 服务名称

    private String type;    // 文件内容格式

    private List<String> paths; // 分支配置文件需要存放的地址

    private String version;//配置文件的版本

    /**
     * @Description : 分支配置文件的同步路径构造参数
     * @Param : [branchName, configName, serveName, type, paths]
     * @Return :
     * @Author : gsq
     * @Date : 3:12 下午
     * @note : An art cell !
    **/
    public ConfigBranch(String branchName, String configName, String serveName, String type, List<String> paths) {
        this.branchName = branchName;
        this.configName = configName;
        this.serveName = serveName;
        this.type = type;
        this.paths = paths;
    }

}
