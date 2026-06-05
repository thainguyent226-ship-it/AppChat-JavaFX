import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class ServerApp {
    // Tạo bộ nhớ ảo tạm thời để lưu tài khoản đăng ký ở Tuần 3 (Key: username, Value: password)
    private static HashMap<String, String> localDatabase = new HashMap<>();

    public static void main(String[] args) {
        int port = 5000;
        
        
        localDatabase.put("thai123", "123");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[SERVER] Da bat! Dang cho ket noi o cong " + port + "...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[SERVER] ==> Thay Client ket noi!");

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                String clientMessage = in.readLine();
                System.out.println("[SERVER] Nhan goi tin tho: " + clientMessage);

                if (clientMessage != null) {
                    
                    if (clientMessage.startsWith("LOGIN;")) {
                        String[] data = clientMessage.split(";");
                        String username = data[1];
                        String password = data[2];

                        
                        if (localDatabase.containsKey(username) && localDatabase.get(username).equals(password)) {
                            out.println("LOGIN_SUCCESS"); 
                            System.out.println("[SERVER] ==> Đăng nhập ĐÚNG. Da phan hoi LOGIN_SUCCESS cho: " + username);
                        } else {
                            out.println("LOGIN_FAILED"); 
                            System.out.println("[SERVER] ==> Đăng nhập SAI. Da phan hoi LOGIN_FAILED");
                        }
                    } 
                    
                    else if (clientMessage.startsWith("REGISTER;")) {
                        String[] data = clientMessage.split(";");
                        String username = data[1];
                        String password = data[2];

                        System.out.println("[SERVER] Nhan yeu cau dang ky cho user: " + username);

                        // Kiểm tra nếu tài khoản đã tồn tại trong bộ nhớ ảo
                        if (localDatabase.containsKey(username) || "admin".equals(username)) {
                            out.println("REGISTER_FAILED");
                            System.out.println("[SERVER] ==> Dang ky THAT BAI! Tai khoan đã ton tai: " + username);
                        } else {
                            // Lưu tài khoản mới vào bộ nhớ ảo ngay lập tức
                            localDatabase.put(username, password);
                            out.println("REGISTER_SUCCESS");
                            System.out.println("[SERVER] ==> Dang ky THANH CONG! Da luu user: " + username);
                        }
                    }
                }
                
                // Đóng kết nối sau khi xử lý xong gói tin để giải phóng luồng
                socket.close(); 
            }
        } catch (Exception e) {
            System.out.println("[SERVER] Loi: " + e.getMessage());
        }
    }
}