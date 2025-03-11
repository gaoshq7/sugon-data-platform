package cn.gsq.sdp.core.utils;

import cn.gsq.graph.dag.DagPlus;
import cn.gsq.graph.dag.Vertex;
import cn.gsq.sdp.core.AbstractApp;
import cn.hutool.core.map.MapUtil;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.utils.DagUtil
 *
 * @author : gsq
 * @date : 2025-03-03 15:24
 * @note : It's not technology, it's art !
 **/
public final class DagUtil {

    private DagUtil() {}

    /**
     * @Description : 获取DAG拓扑图
     * @note : ⚠️ app的name属性必须保证唯一性 !
     **/
    public static <T extends AbstractApp> Queue<Vertex<T>> getDagResult(List<T> apps) {
        DagPlus<T> dag = new DagPlus<>();
        // 创建GAG图顶点集合
        Map<String, Vertex<T>> vertices = MapUtil.newHashMap();
        apps.forEach(app -> vertices.put(app.getName(), new Vertex<>(app)));
        // 添加DAG图的关系
        for(T app : apps) {
            List<T> children = (List<T>) app.getChildren();
            // 将父顶点加入DAG图
            Vertex<T> pv = vertices.get(app.getName());
            dag.addVertex(pv);
            // 建立子顶点与父顶点的关系
            for(T child : children) {
                // 下游应用如果不在安装集合中则不参与DAG图的构建
                if(apps.contains(child)) {
                    Vertex<T> cv = vertices.get(child.getName());
                    dag.putEdge(pv, cv);
                }
            }
        }
        return dag.topologicalSorting(dag.getVerticesToMap(), new ConcurrentLinkedQueue<>());
    }

}
