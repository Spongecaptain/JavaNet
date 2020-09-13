package oio.server;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @program: javanet
 * @description: 这是一个基于 OIO 阻塞模式下的服务端
 * @author: Spongecaptain
 * @created: 2020/09/11 21:38
 */
public class OIOServer {
    //线程池，用于处理已连接的 Socket，这里的线程池大小限制为 50
    final private ExecutorService pool = Executors.newFixedThreadPool(50);
    final private int PORT = 2333;//服务端监听的端口为 2333

    @Test
    public void testOIO() {

        try (ServerSocket server = new ServerSocket(PORT)) {
            while (true) {
                System.out.println("服务端 Server 开始执行");
                //1. 如果没有已连接的 Socket，那么其始终会阻塞于 ServerSocket.accept() 方法。
                long startTime = System.currentTimeMillis();// 获取开始时间
                Socket connectedSocket = server.accept();
                long endTime = System.currentTimeMillis();// 获取结束时间  
                System.out.println("ServerSocekt.accept() 方法阻塞的时间为：" + (endTime - startTime) + " ms");//这个阻塞时间是不固定的，取决于 OIOClient 测试方法何时运行以及网络延迟
                //2. 调用 socket.isConnected() 方法进行判断由 ServerSocket 产生的 Socket 实例是否已经处理连接状态
                assertTrue(connectedSocket.isConnected());//检查由 ServerSocket 产生的 Socket 实例是否已经处理连接状态（如果不是，那么会抛出异常）。

                //3. 将 TCP 已连接的 Socket 包装成 Callable 任务
                final SocketTask socketTask = new SocketTask(connectedSocket);
                //4. 将任务交给线程池处理
                pool.submit(socketTask);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class SocketTask implements Callable<Void> {
        private Socket socket;

        public SocketTask(Socket socket) {
            this.socket = socket;
        }

        @Override
        public Void call() throws Exception {
            //5. 获取 Socket 输入流
            final InputStream inputStream = socket.getInputStream();

            long startTime = System.currentTimeMillis();// 获取开始时间  
            //6. Socket 因为 TCP 数据包迟迟未到而阻塞 2 s
            final int read = inputStream.read();
            long endTime = System.currentTimeMillis();// 获取结束时间  
            //7. 输出因为读取 Socekt I/O 输入流而导致的阻塞时间
            System.out.println("服务端接收到的数字为："+read);
            System.out.println("服务器处的 Socket 实例的 IO 阻塞时间：" + (endTime - startTime) + "ms");
            Thread.sleep(2000);//休眠 2000 ms
            final OutputStream outputStream = socket.getOutputStream();
            outputStream.write(read);
            return null;
        }
    }
}
