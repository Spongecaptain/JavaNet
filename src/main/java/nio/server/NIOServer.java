package nio.server;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * @program: javanet
 * @description: 基于 NIO 实现的 Server
 * @author: Spongecaptain
 * @created: 2020/09/11 22:14
 */
public class NIOServer {
    //这个 map 用于存储分批尽力读取的字节数据
    final static HashMap<SelectionKey, Object> hashMap = new HashMap();

    @Test
    public void testNIO() {
        Selector selector = null;
        ServerSocketChannel serverSocket = null;
        ByteBuffer byteBuffer = null;
        try {
            //1. 创建 Selector 实例(典型的工厂方法来构造，Selector 类的构造器是 protected 的，因此不能直接构造)
            selector = Selector.open();
            //2. 创建 ServerSocketChannel 实例
            serverSocket = ServerSocketChannel.open();
            //3. 初始化 ServerSocketChannel 内部的 serverSocket 实例确定绑定的本地端口
            serverSocket.bind(new InetSocketAddress("localhost", 2333));
            //4. 将 ServerSocketChannel 配置成非阻塞模式，这是必要的，
            //注意事项：ServerSocketChannel 实例默认是阻塞模式，可以通过配置修改为非阻塞模式
            serverSocket.configureBlocking(false);
            //5. 将 ServerSocketChannel 注册到 Selector 中，事件是 OP_ACCEPT，一旦有 HTTP 请求就会触发
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);
            //6. 创建 ByteBuffer 用于从 SocketChannel 中读取字节数据
            byteBuffer = ByteBuffer.allocate(100);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Main Loop，基于事件驱动模式进行循环处理
        while (true) {
            try {
                selector.select();//Selector 实例倘若没有事件，则阻塞，直到发生事件
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 8. 阻塞结束，说明 selector 中有事件触发，所以获得其迭代器进行处理
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iter = selectedKeys.iterator();
            //9. 迭代器中全体元素的循环（注意事项：并非所有元素都有对应的事件发生）
            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                //10. SelectionKey.isAcceptable() 返回 true 代表发生了 TCP 新连接事件，如果是这样，那么就将新连接对应的 SocketChannel 进行注册处理
                if (key.isAcceptable()) {
                    try {
                        register(selector, serverSocket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                //11. SelectionKey.isReadable() 返回 true 代表发生了可读事件，即接收到来自客户端的写字节数据
                if (key.isReadable()) {
                    try {
                        //read 方法定义在下面，用于读取 SocketChannel 实例内的字节数据到入口参数的 ByteBuffer 实例
                        read(byteBuffer, key);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                //12. 将此 SelectableChannel 移出迭代器（实际上是 SelectionKeys 容器）是必要的，否则会进行没有必要的事件是否准备好的询问
                iter.remove();
            }
        }
    }

    private static void read(ByteBuffer buffer, SelectionKey key)
            throws IOException {
        System.out.println("read");
        //1. 得到 SelectionKey 实例内部的 SocketChannel 实例，因为可读状态下需要利用 Channel 进行读取字节数据
        SocketChannel socketChannel = (SocketChannel) key.channel();
        //2. 构造一组键值对，key 为 SelectionKey 实例，value 为 List，用于存储多次尽力读取的字节数组
        if (!hashMap.containsKey(key))
            hashMap.put(key, new ArrayList<>());
        //3. 此标志用于判断：
        //0： UNIX 底层的缓冲字节数组被读完了或者 ByteBuffer 没有写一个字节，这里指的是前者，因为每次读取后都 clear 了；
        //-1: 意味这 EOF，即 HTTP 请求数据已经全部传输到服务端 Socket 了。
        //其他大于 0 的数字：意味着这里从底层 UNIX 缓冲字节数组读取了几个字节
        int count = 0;
        while (buffer.hasRemaining() && (count = socketChannel.read(buffer)) > 0) {
            //使 Buffer 变成可读
            buffer.flip();
            ArrayList list = (ArrayList) hashMap.get(key);

            byte[] arr = new byte[buffer.remaining()];
            buffer.get(arr);
            buffer.rewind();
            list.add(arr);
            buffer.clear();
        }
        //当 socketChannel.read(buffer) 返回 -1 时，意味着此时彼通道数据传输已经完成，因为遇到了流传输中的 EOF 标志
        // 如果没有读到字节流末尾，那么选择不关闭，因为下一次还是要继续读取
        if (count == -1) {
            print(key, socketChannel);
        }

    }

    private static void register(Selector selector, ServerSocketChannel serverSocket)
            throws IOException {
        //1. 利用 ServerSocketChannel accept 方法能够得到一个此次连接请求对应的 SocketChannel 实例
        SocketChannel client = serverSocket.accept();
        //2. 将此 SocketChannel 实例设置为非阻塞模式
        client.configureBlocking(false);
        //3. 将此 SocketChannel 注册到 Selector 中，事件为 OP_READ，即可读事件，此方法返回的 SelectionKey 并不需要保存并引用起来
        client.register(selector, SelectionKey.OP_READ);
    }
    //遍历 SelectionKey 实例对应的 ArrayList 内的 byte[] 数组，打印出每一个数组的大小
    private static void print(SelectionKey key, SocketChannel socketChannel) throws IOException {
//这些代码用于验证一次次的 Buffer 工作是否正确地转换为 byte 数组保存起来。
        ArrayList list = (ArrayList) hashMap.get(key);

        final Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            final byte[] next = (byte[]) iterator.next();
            System.out.print(next.length+ " ");
        }
        System.out.println();
        socketChannel.close();
    }
}
