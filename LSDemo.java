package com.anylogic.lsp;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.*;
import java.net.InetSocketAddress;

public class Launcher extends WebSocketServer {

    private Process process;
    private WebSocket webSocket;
    boolean live = true;

    public Launcher(int port) {
        super( new InetSocketAddress( port ) );
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {

        webSocket = conn;
        try {
            createProcess();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createProcess() throws IOException {

        String jdtFolder = "./eclipse.jdt.ls/";
        String script = "launch.sh";
        String parameter = "linux";

        String os = System.getProperty("os.name").toLowerCase();
        System.out.println(os);
        if (os.contains("win")){
            script = "launch.bat";
            parameter = "win";
        } else if (os.contains("mac") || os.contains("darwin")){
            parameter = "mac";
        }

        ProcessBuilder pb = new ProcessBuilder(jdtFolder + script, parameter);
        System.out.println("Start process:" + pb.command());
        process = pb.start();

        final InputStream procInputStream = process.getInputStream();

        Thread procOut = new Thread() {

            public void run() {
                int r;
                char[] buff = new char[160000];
                StringBuilder sb = new StringBuilder();
                InputStream is = new BufferedInputStream(procInputStream);

                try {
                    InputStreamReader rdr = new InputStreamReader(is, "UTF-8");
                    while (live && (r = rdr.read(buff)) != -1) {

                        sb.append(buff, 0, r);

                        String[] split = sb.toString().split("Content-Length:.*?\r\n\r\n");
                        System.err.printf("\n======\n%s", sb);

                        String str = "";
                        for (int i = 0; i < split.length; i++) {
                            str = split[i];
                            if (completeJson(str)){
                                webSocket.send(str);
                                System.err.printf("\n--------%s", str);
                            }
                        }
                        sb.setLength(0);
                        if (!completeJson(str)){
                            sb.append(str);
                        }

                    }


                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            };
        };
        procOut.start();

    }

    private boolean completeJson(String str) {
        return str != null && str.startsWith("{") && str.endsWith("}") ;

    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.err.println("Closed: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Got message:" + message);
        if(message.contains("\"method\":\"mvn\"")) {
            String[] split = message.split("file://|\"}}");
            if(split.length == 2){
                String s = split[1];
                String command = "mvn package";
                try {
                    ProcessBuilder pb = new ProcessBuilder("ash", "-c", "mvn package");
                    pb.directory(new File(s));
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null)
                        System.out.println("maven build: " + line);
                    p.waitFor();

                    int exitStatus = p.exitValue();
                    if (exitStatus == 0){
                        webSocket.send("{\"jsonrpc\": \"2.0\", \"result\": \"Ok\"}");
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            webSocket.send("{\"jsonrpc\": \"2.0\", \"result\": \"Error\"}");
            return;
        }
        try {
            String fullMessage = "Content-Length: " + message.length() + "\r\n\r\n" + message;

            process.getOutputStream().write(fullMessage.getBytes());
            process.getOutputStream().flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Server started!");
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }

    public static void main(String[] args) {
        int port = 3000;
        try {
            port = Integer.parseInt( args[ 0 ] );
        } catch ( Exception ex ) {
        }
        Launcher s = new Launcher( port );
        s.start();
        System.out.println( "Language Server started on port: " + s.getPort() );

    }

}
