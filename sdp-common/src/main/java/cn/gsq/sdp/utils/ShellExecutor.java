package cn.gsq.sdp.utils;

import cn.hutool.core.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.utils.ShellExecutor
 *
 * @author : gsq
 * @date : 2021-05-27 19:37
 * @note : It's not technology, it's art !
 **/
@Slf4j
public class ShellExecutor {

    /**
    * @param command 执行命令
    * @return 命令返回字符串
    * ⚠️ 只获取脚本正确输出数据，对脚本异常退出不作处理（阻塞式执行，不适合运行长脚本）！
    * */
    public static String executeCommand(String command)
            throws InterruptedException, IOException {
        StringBuffer rspList = new StringBuffer();
        Runtime run = Runtime.getRuntime();
        Process proc = run.exec("/bin/bash", null, null);
        // ⚠️ 此处只获取了脚本正确输出没有对脚本异常输出进行获取！
        BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(proc.getOutputStream())), true);

        out.println(command);
        out.println("exit");

        String rspLine = "";
        while ((rspLine = in.readLine()) != null) {
            rspList.append(rspLine).append("\n");
        }
        String response = rspList.length()==0 ? rspList.toString() : rspList.deleteCharAt(rspList.length() - 1).toString();

        proc.waitFor();
        in.close();
        out.close();
        proc.destroy();

        return response;
    }

    /**
    * @param command 执行命令
    * @param directory 命令执行目录
    * @param timeout 命令等待超时时间
    * @param communicatorExecutor 线程池
    * @param communicators 逐行处理输出信息函数
    * @return 脚本运行结果：0：成功；其它：失败
    * ⚠️ 不可执行阻塞命令脚本！
    * */
    public static int execute(String command, String directory, Long timeout, ExecutorService communicatorExecutor, final Communicator... communicators)
            throws CommandTimeoutException, InterruptedException, IOException {
        String[] commands = new String[]{"sh", "-c", command};
        final ProcessBuilder processBuilder = new ProcessBuilder(commands);
        if (directory != null) {
            File workDir = new File(directory);
            if (workDir.exists() && workDir.isDirectory()) {
                processBuilder.directory(workDir);
            }
        }
        processBuilder.redirectErrorStream(true);
        int status = -1;
        try {
            final Process process = processBuilder.start();
            // 传入的函数默认同步读取输入流
            if(communicators != null && communicators.length > 0){
                communicatorExecutor.submit(() -> {
                    BufferedReader reader = null;
                    try {
                        InputStream inputStream = process.getInputStream();
                        if (ObjectUtil.isNotNull(inputStream)) {
                            reader = new BufferedReader(new InputStreamReader(inputStream));
                            String line;
                            while ((line = reader.readLine()) != null) {
                                for (Communicator communicator : communicators) {
                                    communicator.onMessage(line, process);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("命令执行错误: {}", command);
                        e.printStackTrace();
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (Exception e) {
                                log.error("命令输出流关闭异常: ", command);
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
            // 是否超时
            if (timeout == null || timeout <= 0) {
                status = process.waitFor();
            } else {
                if (!process.waitFor(timeout, TimeUnit.MILLISECONDS)) {
                    throw new CommandTimeoutException(String.format("命令运行超时, 阈值: %s, command: %s", timeout, command));
                } else {
                    status = process.exitValue();
                }
            }
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (CommandTimeoutException e) {
            if (e instanceof CommandTimeoutException) {
                throw e;
            }
        }
        return status;
    }

    public static class CommandTimeoutException extends Exception {
        public CommandTimeoutException(String message) {
            super(message);
        }
    }

    public interface Communicator {
        void onMessage(String message, Process process);
    }

}
