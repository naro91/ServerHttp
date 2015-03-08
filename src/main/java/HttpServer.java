import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class HttpServer {
    public static final int DEFAULT_PORT = 8080;
    public static int CURRENT_PORT = DEFAULT_PORT;
    private static final String DEFAULT_PATH = "DOCUMENT_ROOT";
    public String getDefaultPath() {
        return DEFAULT_PATH;
    }
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
            CURRENT_PORT = port;
        }
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started on port: " + serverSocket.getLocalPort() + "\n");
        } catch (IOException e) {
            System.out.println("Exception !!! port " + port + " is blocked.");
            System.exit(-1);
        }
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientSession session = new ClientSession(clientSocket);
                new Thread(session).start();
            } catch (IOException e){
                System.out.println("Exception !!! establish connection. ");
                System.out.println(e.getMessage());
                System.exit(-1);
            }
        }
    }
}