package nio.client;

import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * @program: javanet
 * @description: 基于 OIO 实现的 Client
 * @author: Spongecaptain
 * @created: 2020/09/11 22:14
 */
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
