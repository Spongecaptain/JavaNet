# Java 网络编程

Java  网络编程大致可以分为三类：

- 基于 OIO 的阻塞 Socket 与 ServerSocket 编程模型；
- 基于 NIO 的非阻塞 SocketChannel 与 ServerSocketChannel 编程模型；
- 对于 JDK NIO 进行二次封装的非阻塞编程框架：Netty；

其中，Netty 几乎可以与 Java NIO 框架等价，不过在本节我并不打算对基于 Netty 的 NIO 编程模型进行过多的展开，主要强调前面两种。

GitHub 项目地址：https://github.com/Spongecaptain/JavaNet

# 1. JDK OIO 编程模型

在 JDK 中，凡是基于 JDK1.0 推出的 I/O 模型都属于 OIO（old I/O），或者将其称为 BIO(Blocking I/O)。具体来说 java.io 包下的 InputStream/OutputStream 两个抽象类分别对输入/输出字节流进行了封装，不管数据源（数据去向）是磁盘上的文件，还是远端的 Socket。

因为 JDK  的 Socket 基于InputStream/OutputStream，好处是 Socket 允许程序员将网络连接看作是另外一个可以读/写字节的流，坏处是这是一个 OIO，会造成线程阻塞。Socekt 的另一个好处是将 TCP 中的 Socket 概念具体化了，维护 TCP 连接的说法过于抽象，具体来说就是维护两端（服务端与客户端）的 Socket 实例的状态。Socket 实例的状态在 Java 语言中看得见，摸得着，具体地多。

JDK Socket 编程有两个核心类：

- Socket 类：对应于 TCP 概念中的一个 Socket，用于维护 TCP 连接，以及发送与接收具体的字节数据；
- ServerSocket 类：代表服务器 Socket，每个服务器 Socket 监听服务器机器上的一个特定端口。当远程主机上的一个客户端尝试连接这个端口时，服务器就被唤醒，协商建立客户端和服务器之间的连接，并返回一个常规的 Socket 对象**，表示两台主机之间的 Socket。换句话说，**服务器 Socket 等待连接，而客户端 Socket 发起连接。一旦 **ServerSocket 建立了连接，服务器会使用一个常规的 Socket 对象向客户端发送数据。数据总是通过常规 socket 传输**。

**下面代码用于证明以下 2 个说法**：

1. Socekt 实例在读取数据时面临阻塞问题；
2. 一旦 ServerSocket 构造出一个 Socket 实例，那么其已经处于已连接状态（已经完成三次握手、四次挥手等工作）；

网络通信模型是实现一个简单的 echo 通信，具体如下：

- 客户端在建立连接之后，阻塞 3 s 后向服务端发送消息 int 值 2；
- 服务端在接收到消息后，阻塞 2 s 后向服务端返回 echo 通知； 

期望：

- 服务端因为客户端迟迟没有发送数据而阻塞 3 s 左右；
- 客户端也因为服务端迟迟没有恢复 echo 消息而阻塞 2 s 左右；

服务端代码：

```java
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
```

客户端代码：

```java
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
```

测试需要先运行服务端的测试方法，然后再运行客户端的测试方法。

服务端控制台输出：

```
服务端 Server 开始执行
ServerSocekt.accept() 方法阻塞的时间为：9334 ms(备注，这个阻塞时间不一定，取决于你何时启动客户端)
服务端 Server 开始执行
服务端接收到的数字为：1
服务器处的 Socket 实例的 IO 阻塞时间：3008ms
```

客户端控制台输出：

```
echo:1
客户端的 Socket 实例的 IO 阻塞时间：2001ms
```

我们利用代码证明了以上关于 JDK Socket 的两个说法。

**为什么会阻塞？**

因为 InputStream 在其内部没有字节数据的情况下企图进行数据读取就会导致线程阻塞，下面是 java.io.InputStream 类 `read()` 方法的 javadoc：

>Reads the next byte of data from the input stream. The value byte is returned as an int in the range 0 to 255. If no byte is available because the end of the stream has been reached, the value -1 is returned. This method blocks until input data is available, the end of the stream is detected, or an exception is thrown.
>
>方法返回 -1 表示数据读取完毕，方法在数据不可用（未准备好）时阻塞。
>
>注：OutputStream 类有着类似的阻塞性质。

## 2. JDK NIO 编程模型

JDK OIO 模型存在阻塞线程的情况，而且我们也知道了 JDK Socket 阻塞的原因是因为其基于 InputStream/OutputStream 进行数据的读写。JDK NIO 则采用了另一种数据读取方式，并且实现了 NoBlocking I/O。

首先，为了实现非阻塞式读取，我们不能再使用 InputStream/OutputStream 作为 Socket 来完成通信，NIO 使用了另一个接口完成输入输出流的抽象：Channel。Channel 我们可以理解为对应于 BIO 中的 Socket，也可以理解为 Scoket.inputStream/SocketOutputStream。如果认为是流，那么我们做一个比较：

- 传统 Socket：我们调用 Socket 的 `getInputStream()` 以及 `getOutputStream()` 进行数据的读和写。
- Channel（这里指的是 SocketChannel类）：我们不再需要得到输入输出流进行读和写，而是通过 Channel 的 `read(ByteBuffer dst)` 以及 `write(ByteBuffer src)` 方法进行读和写。我们可以认为 Channel 同时起到了 InputStream/OutputStream 的作用。

另一方面，Channel 不像 OutputStream/InputStream 能够处理字节数组 `byte[]`，而是仅仅能够处理 ByteBuffer 实例。ByteBuffer 继承于 Buffer 抽象类，后者本质是一块缓存区，内部使用字节数组存储数据，并维护几个特殊变量，实现数据的反复利用。ByteBuffer 起到了 BIO 中 byte[] 的作用，但额外具有更强的功能。

于此同时，异步编程通常需要利用阻塞队列与轮询线程，这里起到阻塞队列的角色就是 Selector。在我们的线程中不断轮询 Selector 实例，查询是否有新事件产生，如果没有，那么当前线程阻塞，否则当前线程停止阻塞。一旦停止阻塞，我们便消费 Selector 实例内部的可读可写事件。

最后，在理解网络模型上，我们可以将 ServerSocketChannel 理解为 SocketChannel，将 SocketChannel 理解为 Socket。从异步事件驱动模型上来理解，ServerSocektChannel 对 TCP 新连接事件感兴趣，而 SocektChannel 对可读/可写事件感兴趣。

以上，就是对 JDK NIO 编程中的 Channel（ServerSocketChannel、SocketChannel）、ByteBuffer、Selector 的最简单理解，下面我们来实现一个基于 JDK NIO 的线程模型。

> 与 JDK Socket 中的类进行类比，很容易理解 JDK NIO 中的相关类能提供什么功能，起到什么角色。

重点理解：

- JDK NIO 的异步事件编程模型；
- JDK NIO 的尽力读取；

首先，我们的客户端可以选择还是基于 OIO 来实现，仅仅实现基于 NIO 的服务端。

```java
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
```

客户端：

```java
public class NIOClient {
    @Test
    public void test(){
        try {
            final Socket socket = new Socket("localhost",2333);
            final OutputStream outputStream = socket.getOutputStream();

            final byte[] bytes1 = new byte[256];
            final byte[] bytes2 = new byte[1024];

            outputStream.write(bytes1);
            Thread.sleep(5000);
            outputStream.write(bytes2);

            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```

代码测试先运行服务端，在运行客户端，测试中服务端的控制台打印出如下结果：

> 100 100 56 100 100 100 100 100 100 100 100 100 100 24 

首先，基于事件轮询的异步通信已经写在 NIOServer 类的代码中。

其次，JDK NIO ByteBufer 的尽力读指的是：

这里客户端程序间隔 5 s 进行了两次不同字节大小的输出，这是为了模拟网络 I/O 中出现阻塞时，一个 HTTP 请求可能会间隔很久才能够接收完毕。SocketChannel 的非阻塞读写时尽力而为，其既会受制于网络环境的影响，又会受制于 ByteBuffer 能够接收多少字节数的影响。

- 第一次：可以认为 256 字节数据没有达到底层 UNIX 缓冲数组的阈值，但是由于很久没能够补上，所以还是将字节数据交给了 Java 程序。但是读取时，由于 ByteBuffer 仅仅设置成 100 byte 大小，所以此时 SocketChannel 受制于 ByteBuffer 的大小，其尽力将 ByteBuffer 填满。每次填满 100 字节，`socketChannel.read(buffer)` 就返回。所以对于 256 字节数据分了 3 词尽力读取：100、100、56。
- 第二次：由于第二次间隔于第一次 5s 后才向服务器程序发送，这好比同一个 HTTP 请求因为网络延迟分批到达服务端。但是 SocketChannel 是非阻塞的，所以服务端线程并不会等待阻塞 5 s 等待这些数据的到来。这里 SocketChannel 的非阻塞尽力读体现在，其只会读取已经接收到的字节数据，读取完了就返回了，不会受到网络的影响而阻塞。
