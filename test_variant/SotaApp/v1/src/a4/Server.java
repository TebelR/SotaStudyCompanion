package a4;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import jp.vstone.RobotLib.*;
import jp.vstone.camera.CameraCapture;
import java.io.*;
import java.net.InetSocketAddress;
// import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class Server {
    private static final int PORT = 8080;
    private static HttpServer server;
    private static CameraCapture capture;
    private static CSotaMotion motion;
    private static AtomicReference<byte[]> lastFrame = new AtomicReference<>(null);
    private static volatile boolean streaming = true;
    private static final int FRAME_RATE = 15; // Target frame rate for streaming
    
    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");// using headless mode to avoid library issues on Sota
        CRobotMem mem = new CRobotMem();
        motion = new CSotaMotion(mem);
        
        if(!mem.Connect()) {
            System.err.println("Failed to connect to Sota");
            return;
        }
        
        motion.InitRobot_Sota();

        CRobotUtil.Log("Server", "Opening camera...");
        capture = new CameraCapture(0, 2);// using BGR 3 byte format to send raw video as a whole - avoid compression overhead on the Sota
        capture.openDevice("/dev/video0");
        CRobotUtil.Log("Server", "Camera opened successfully at 320x240");
        
        // Optimized capture thread - remove sleeps, capture as fast as camera allows
        Thread frameThread = new Thread(() -> {
            CRobotUtil.Log("Server", "Frame capture started");
            while(streaming) {
                try {
                    capture.snap();
                    byte[] data = capture.getImageRawData();
                    if(data != null && data.length > 0) {
                        lastFrame.set(data);
                    }
                } catch (Exception e) {
                    CRobotUtil.Err("Server", "Frame error: " + e.getMessage());
                    try { Thread.sleep(50); } catch(InterruptedException ie) {}
                }
            }
        });
        frameThread.setDaemon(true);
        frameThread.setPriority(Thread.MAX_PRIORITY);
        frameThread.start();
        

        server = HttpServer.create(new InetSocketAddress(PORT), 256); // Larger backlog
        server.createContext("/stream", new StreamHandler());
        // server.createContext("/frame", new FrameHandler());
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.start();
        
        String ip = getLocalIpAddress();
        System.out.println("\n==========================================");
        System.out.println("Sota Stream Server Ready!");
        System.out.println("Resolution: " + capture.getWidth() + "x" + capture.getHeight());
        System.out.println("Connect to: http://" + ip + ":" + PORT + "/stream");
        System.out.println("==========================================\n");
    }
    
    static class StreamHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.sendResponseHeaders(200, 0);
        
        OutputStream os = exchange.getResponseBody();
        DataOutputStream dos = new DataOutputStream(os);
        
        while(true) {
            try {
                byte[] frameData = capture.getImageRawData();
                if(frameData != null && frameData.length > 0) {
                    // Send magic number (0xDEADBEEF) to mark frame start
                    dos.writeInt(0xDEADBEEF);
                    // Send frame size
                    dos.writeInt(frameData.length);
                    // Send frame data
                    dos.write(frameData);
                    dos.flush();
                }
                Thread.sleep(1000 / FRAME_RATE);
            } catch (Exception e) {
                break;
            }
        }
    }
}
    
    // static class FrameHandler implements HttpHandler {
    //     @Override
    //     public void handle(HttpExchange exchange) throws IOException {
    //         byte[] frame = lastFrame.get();
    //         if(frame != null) {
    //             exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
    //             exchange.sendResponseHeaders(200, frame.length);
    //             OutputStream os = exchange.getResponseBody();
    //             os.write(frame);
    //             os.close();
    //         } else {
    //             String response = "No frame available";
    //             exchange.sendResponseHeaders(503, response.length());
    //             OutputStream os = exchange.getResponseBody();
    //             os.write(response.getBytes());
    //             os.close();
    //         }
    //     }
    // }
    
    private static String getLocalIpAddress() {
        try {
            java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch(Exception e) {
            return "127.0.0.1";
        }
    }
}