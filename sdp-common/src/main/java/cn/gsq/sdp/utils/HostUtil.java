package cn.gsq.sdp.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.utils.HostUtil
 *
 * @author : gsq
 * @date : 2025-03-03 16:48
 * @note : It's not technology, it's art !
 **/
public final class HostUtil {

    private HostUtil() {}

    public static String getHostname(){
        String hostname = null;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return hostname;
    }

    public static String getHostAddress(){
        String hostname = null;
        try {
            hostname = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return hostname;
    }

    public static String getRole(){
        String role = System.getenv("ROLE");
        return role==null ? "agent" : role;
    }

}
