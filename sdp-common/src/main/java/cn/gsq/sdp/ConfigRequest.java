package cn.gsq.sdp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Setter
@Getter
@AllArgsConstructor
public class ConfigRequest {

    private List<String> hostnames; // 需要同步的主机名

    private String serveName; // 服务名

    private String configName; // 配置文件名称

    private String branch;  // 分支名称;

    private Map<String, String> content;  // 配置文件内容

}
