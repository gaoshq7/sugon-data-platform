package cn.gsq.sdp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.HostInfo
 *
 * @author : gsq
 * @date : 2025-03-03 14:56
 * @note : It's not technology, it's art !
 **/
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HostInfo {

    private String hostname;    // 主机名

    private List<String> groups;    // 主机分组

}