package cn.gsq.sdp;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.ConfigLabel
 *
 * @author : gsq
 * @date : 2023-04-11 10:31
 * @note : It's not technology, it's art !
 **/
@Getter
@AllArgsConstructor
public enum ConfigLabel {

    STORAGE("storage", "存储"),

    HOSTNAME("hostname", "域名"),

    TUNING("tuning", "调优"),

    AUTHENTICATION("authentication", "身份验证"),

    PORT("port", "端口"),

    USER("user", "用户"),

    ADDRESS("address", "地址"),

    AUTHORITY("authority", "权限"),

    DATABASE("database", "数据库");

    private String id;

    private String name;

}
