package src.client;

import java.io.*;
import java.net.Socket;
import java.sql.*;

public class ClientHandler extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String clientMessage;
            while ((clientMessage = in.readLine()) != null) {
                System.out.println("[RECEIVE]: " + clientMessage);
                String[] data = clientMessage.split(";");
                String command = data[0];

                if ("LOGIN".equals(command)) {
                    handleLogin(data[1], data[2]);
                } else if ("REGISTER".equals(command)) {
                    handleRegister(data[1], data[2]);
                }
            }
        } catch (Exception e) {
            System.out.println("[SERVER] Client ngat ket noi.");
        }
    }

    private void handleLogin(String user, String pass) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, user);
            pstmt.setString(2, pass);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                out.println("LOGIN_SUCCESS");
                System.out.println("[LOGIN] User: " + user + " thanh cong!");
            } else {
                out.println("LOGIN_FAILED");
                System.out.println("[LOGIN] User: " + user + " that bai.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void handleRegister(String user, String pass) {
        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, user);
            pstmt.setString(2, pass);
            
            int result = pstmt.executeUpdate();
            if (result > 0) {
                out.println("REGISTER_SUCCESS");
                System.out.println("[REGISTER] Da luu user " + user + " vao SQL Server!");
            }
        } catch (SQLException e) {
            out.println("REGISTER_FAILED");
            System.out.println("[REGISTER] User " + user + " da ton tai.");
        }
    }
}