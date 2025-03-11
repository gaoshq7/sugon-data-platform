package cn.gsq.sdp;

public final class SdpPropertiesFinal {

    /**
     * Sdp包根目录
     */
    public static final String SDP_BASE_PATH = "/usr/sdp";

    /**
     * 用户home目录
     */
    public static final String USER_HOME = "/root";

    /**
     * 主机名通配符
     */
    public static final String HOSTNAME_CHARACTER = "_LOCAL";

    /**
     * 主机ip通配符
     */
    public static final String HOST_IP = "_LOCAL^IP";

    /**
     * 换行符
     */
    public static final String NEW_LINE = "_N^";

    /**
     * 制表符
     */
    public static final String TABS = "_T^";

    /**
     * presto node id
     */
    public static final String NODE_UID = "_NODE_UID";

    /**
     * 认证文件目录
     */
    public static final String KEYTAB_HOME = "/etc/security/keytab";

    /**
     * 通用默认字符串
     */
    public static final String DEFAULT_CHAR = "default";

    /**
     * 配置文件默认KEY
     */
    public static final String CONTENT_KEY = "content";

    /**
     * 主机名通配符
     */
    public static final String HOSTNAME_PROXY = "$HOSTNAME";

    public static final String NODE_HOST = "['hostname','hostname','hostname']";

    public static final String NODE_IP_PORT = "['hostname:port','hostname:port','hostname:port']";

    /**
     * 命令集合
     */
    public static final class Command {

        /**
         * 删除命令
         */
        public static final String C_DELETE = "rm -rf {}";

        /**
         * 创建目录
         */
        public static final String C_MKDIR = "mkdir -p {}";

        /**
         * 修改目录拥有者和属组
         */
        public static final String C_CHOWN_HDFS = "chown -R {}:hadoop {}";

        /**
         * 修改目录权限
         */
        public static final String C_CHMOD_775 = "chmod -R 775 {}";

        /**
         * 获取进程
         */
        public static final String C_GRAB = "ps -ef | grep {} | grep -v grep | awk '{print $2}'";

    }

}
