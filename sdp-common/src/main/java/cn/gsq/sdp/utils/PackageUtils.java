package cn.gsq.sdp.utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.utils.PackageUtils
 *
 * @author : gsq
 * @date : 2025-03-14 15:05
 * @note : It's not technology, it's art !
 **/
public final class PackageUtils {

    private PackageUtils() {}

    /**
     * 确保指定的包已被 ClassLoader 加载，并返回 Package
     *
     * @param packageName 需要获取的包名
     * @return Package 对象，确保不为空
     */
    public static Package getPackage(String packageName) {
        try {
            // 1. 使用 ClassLoader 找到 package 对应的路径
            String path = packageName.replace('.', '/');
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(path);
            // 2. 检查是否存在 package-info.class
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                File dir = new File(url.getFile());
                if (dir.exists() && dir.isDirectory()) {
                    File[] files = dir.listFiles((d, name) -> name.equals("package-info.class"));
                    if (files != null && files.length > 0) {
                        // 3. 通过 Class.forName 强制加载包中的某个类（触发 package 加载）
                        loadAnyClassInPackage(packageName);
                        break;
                    }
                }
            }
        } catch (IOException ignored) {

        }
        // 4. 最终获取 Package（确保不会为空）
        Package pkg = Package.getPackage(packageName);
        if (pkg == null) {
            throw new RuntimeException("无法获取 Package: " + packageName);
        }
        return pkg;
    }

    /**
     * 尝试加载 package 中的任意一个类，以确保 package 被 ClassLoader 加载
     */
    private static void loadAnyClassInPackage(String packageName) {
        try {
            String basePath = packageName.replace('.', '/');
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(basePath);

            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                File dir = new File(url.getFile());
                if (dir.exists() && dir.isDirectory()) {
                    File[] files = dir.listFiles((d, name) -> name.endsWith(".class") && !name.equals("package-info.class"));
                    if (files != null && files.length > 0) {
                        String className = packageName + "." + files[0].getName().replace(".class", "");
                        Class.forName(className);  // 强制加载类
                        return;
                    }
                }
            }
        } catch (Exception ignored) {

        }
    }

}
