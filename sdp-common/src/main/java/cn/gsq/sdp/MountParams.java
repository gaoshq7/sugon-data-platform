package cn.gsq.sdp;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

@Setter
@Getter
@Accessors(chain = true)
public class MountParams {

    private String hostname;    // 需要挂载磁盘的主机

    private List<DiskInfo> diskInfos;  // 挂载磁盘的信息

    @Setter
    @Getter
    @Accessors(chain = true)
    public static class DiskInfo {
        private String driver;  // 磁盘

        private String path;    // 需要挂载到的目录
    }
}
