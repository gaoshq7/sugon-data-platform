package cn.gsq.sdp.driver;

import cn.gsq.sdp.RpcRequest;
import cn.gsq.sdp.RpcRespond;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.driver.RpcDriver
 *
 * @author : gsq
 * @date : 2025-03-03 16:43
 * @note : It's not technology, it's art !
 **/
public interface RpcDriver {

    /*
     *   ⚠️ 需要调用者自己实现rpc接口！
     * */

    RpcRespond<String> execute(RpcRequest rpcRequest);

    void addUser(RpcRequest rpcRequest, String username, String uuid);

    void delUser(RpcRequest rpcRequest, String username);

    void createKeytab(String masterHost, String username, String hostname, String uid);

}
