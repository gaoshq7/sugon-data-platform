package cn.gsq.sdp;

import lombok.Getter;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.SshInfo
 *
 * @author : gsq
 * @date : 2025-03-03 17:14
 * @note : It's not technology, it's art !
 **/
@Getter
public class SshInfo {

    private String id;

    private final String hostname;

    private Integer port;  // ssh端口

    private String username;    // 用户名

    private String pwd;    // 密码

    /**
     * @Description : 构造函数
     * @Param : [hostname]
     * @Return :
     * @Author : gsq
     * @Date : 5:02 下午
     * @note : An art cell !
     **/
    protected SshInfo(String hostname) {
        this.hostname = hostname;
    }

    protected SshInfo(String id, String hostname, Integer port,String username, String pwd) {
        this.id = id;
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.pwd = pwd;
    }

}
