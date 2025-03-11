package cn.gsq.sdp.utils;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.io.resource.NoResourceException;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.utils.FileUtil
 *
 * @author : gsq
 * @date : 2020-10-22 17:07
 * @note : It's not technology, it's art !
 **/
public class FileUtil {

    private FileUtil(){}

    /**
     * @Description : 在classpath中读取文件
     * @Param : [fileName]
     * @Return : java.lang.String
     * @Author : gsq
     * @Date : 10:56 上午
     * @note : An art cell !
    **/
    public static String readClassPathFileToString(String classpath) {
        ClassPathResource resource = new ClassPathResource(classpath);
        return IoUtil.readUtf8(resource.getStream());
    }

    /**
     * @Description : 获取文件名
     * @Param : [filepath]
     * @Return : java.lang.String
     * @Author : gsq
     * @Date : 12:02 下午
     * @note : ⚠️ 不验证文件是否存在 !
     **/
    public static String getFileName(String filepath) {
        return cn.hutool.core.io.FileUtil.getName(filepath);
    }

    /**
     * @Description : 获取文件路径，去掉文件名
     * @Param : [filepath]
     * @Return : java.lang.String
     * @Author : gsq
     * @Date : 12:02 下午
     * @note : ⚠️ 不验证文件是否存在 !
     **/
    public static String getFilepath(String filepath) {
        int lastSlashIndex = filepath.lastIndexOf('/');
        if (lastSlashIndex != -1) {
            return filepath.substring(0, lastSlashIndex);
        }
        return null;
    }

    /**
     * @Description : 获取文件后缀名
     * @Param : [filename]
     * @Return : java.lang.String
     * @Author : gsq
     * @Date : 12:02 下午
     * @note : ⚠️ 不验证文件是否存在 !
    **/
    public static String getSuffix(String filename) {
        return cn.hutool.core.io.FileUtil.getSuffix(filename);
    }

    /**
     * @Description : 获取文件名称（去掉后缀名）
     * @Param : [filename]
     * @Return : java.lang.String
     * @Author : gsq
     * @Date : 12:02 下午
     * @note : ⚠️ 不验证文件是否存在 !
    **/
    public static String getPrefix(String filename) {
        return cn.hutool.core.io.FileUtil.getPrefix(filename);
    }

    /**
     * @Description : 判断文件是否存在
     * @Param : [path]
     * @Return : java.lang.Boolean
     * @Author : gsq
     * @Date : 3:57 下午
     * @note : ⚠️ 文件系统路径中 !
    **/
    public static Boolean isFileExist(String path) {
        return cn.hutool.core.io.FileUtil.exist(path);
    }

    /**
     * @Description : 判断jar包中资源是否存在
     * @Param : []
     * @Return : java.lang.Boolean
     * @Author : gsq
     * @Date : 10:27 上午
     * @note : ⚠️ 判断classpath资源是否存在 !
    **/
    public static Boolean isResourceExist(String classpath) {
        boolean flag = false;
        try {
            ClassPathResource resource = new ClassPathResource(classpath);
            flag = true;
        } catch (NoResourceException ignored) {

        }
        return flag;
    }

}
