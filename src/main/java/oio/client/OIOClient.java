package oio.client;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * @program: javanet
 * @description: 基于阻塞 OIO 的 Socket 客户端
 * @author: Spongecaptain
 * @created: 2020/09/11 21:59
 */
public class OIOClient {
    final private String HOST = "localhost";
    final private int PORT = 2333;//服务端监听的端口为 2333

    @Test
    public void testOIOClient() {
        try {
            //1. Java 的 Socket 实例在构造过程中就会试图建立 TCP 连接
            final Socket socket = new Socket(HOST, PORT);
            //2. 在建立连接后故意休眠 3 s,然后在发送 TCP 数据
            Thread.sleep(3000);
            final OutputStream outputStream = socket.getOutputStream();
            outputStream.write(1);
            final InputStream inputStream = socket.getInputStream();
            long startTime = System.currentTimeMillis();// 获取开始时间
            int echo = inputStream.read();
            System.out.println("echo:"+echo);
            long endTime = System.currentTimeMillis();// 获取结束时间  
            System.out.println("客户端的 Socket 实例的 IO 阻塞时间："+ (endTime - startTime) + "ms");
            socket.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
