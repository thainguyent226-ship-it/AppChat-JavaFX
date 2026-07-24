package src.client;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class ServerApp {
    private static final int PORT = 5000;
    private static final String LOG_FILE = "server_activity.log";

    private static final ConcurrentHashMap<String, PrintWriter> onlineUsers = new ConcurrentHashMap<>();

   
    private static final String SMTP_HOST = "live.smtp.mailtrap.io";
    private static final int SMTP_PORT = 587;
    private static final String SMTP_EMAIL = "api"; 
    private static final String SMTP_APP_PASSWORD = "b8e7c63c7a35d67df2bd00bc296496eb";
    private static final String SMTP_FROM_EMAIL = "hello@demomailtrap.co"; 
    private static final ConcurrentHashMap<String, OtpEntry> pendingOtp = new ConcurrentHashMap<>();

    private static class OtpEntry {
        String code;
        long expiresAt;
        OtpEntry(String code, long expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[SERVER SQL] Da bat! Dang cho ket noi o cong " + PORT + "...");

            new Thread(ServerApp::runAdminConsole).start();

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[SERVER SQL] ==> Co một thiet bi Client ket noi vao!");
                
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (Exception e) {
            System.out.println("[SERVER SQL] Loi khoi dong cong: " + e.getMessage());
        }
    }

    private static synchronized void writeLog(String action, String username, String detail) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        String line = "[" + time + "] " + action + " - user: " + username + (detail == null || detail.isEmpty() ? "" : " - " + detail);
        try (FileWriter fw = new FileWriter(LOG_FILE, true)) {
            fw.write(line + System.lineSeparator());
        } catch (IOException e) {
            System.out.println("[LOG ERROR] Khong the ghi log: " + e.getMessage());
        }
    }

    private static void runAdminConsole() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("[ADMIN] Go lenh quan tri: stats | block <user> | unblock <user> | broadcast <noi dung>");
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(" ", 2);
            String cmd = parts[0].toLowerCase();

            switch (cmd) {
                case "stats": {
                    int totalUsers = countTotalUsers();
                    Set<String> online = onlineUsers.keySet();
                    System.out.println("[STATS] Tong so nguoi dung: " + totalUsers);
                    System.out.println("[STATS] Dang online (" + online.size() + "): " + String.join(", ", online));
                    break;
                }
                case "block": {
                    if (parts.length < 2) { System.out.println("[ADMIN] Cu phap: block <username>"); break; }
                    String targetUser = parts[1].trim();
                    if (setBlocked(targetUser, true)) {
                        System.out.println("[ADMIN] Da khoa tai khoan: " + targetUser);
                        writeLog("BLOCK_USER", targetUser, "Bi khoa boi admin");
                        PrintWriter targetOut = onlineUsers.get(targetUser);
                        if (targetOut != null) {
                            targetOut.println("ACCOUNT_BLOCKED");
                        }
                    } else {
                        System.out.println("[ADMIN] Khong tim thay user hoac loi DB: " + targetUser);
                    }
                    break;
                }
                case "unblock": {
                    if (parts.length < 2) { System.out.println("[ADMIN] Cu phap: unblock <username>"); break; }
                    String targetUser = parts[1].trim();
                    if (setBlocked(targetUser, false)) {
                        System.out.println("[ADMIN] Da mo khoa tai khoan: " + targetUser);
                        writeLog("UNBLOCK_USER", targetUser, "Duoc mo khoa boi admin");
                    } else {
                        System.out.println("[ADMIN] Khong tim thay user hoac loi DB: " + targetUser);
                    }
                    break;
                }
                case "broadcast": {
                    if (parts.length < 2) { System.out.println("[ADMIN] Cu phap: broadcast <noi dung>"); break; }
                    String msg = parts[1];
                    for (PrintWriter clientOut : onlineUsers.values()) {
                        clientOut.println("SYSTEM_MSG;" + msg);
                    }
                    System.out.println("[ADMIN] Da broadcast toi " + onlineUsers.size() + " client dang online.");
                    writeLog("BROADCAST", "admin", msg);
                    break;
                }
                default:
                    System.out.println("[ADMIN] Lenh khong hop le. Dung: stats | block <user> | unblock <user> | broadcast <noi dung>");
            }
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private String loggedInUser; // username sau khi LOGIN thanh cong

        public ClientHandler(Socket socket) { 
            this.socket = socket; 
        }

        @Override
        public void run() {
            try (
                EncryptedReader in = new EncryptedReader(socket.getInputStream());
                EncryptedWriter out = new EncryptedWriter(socket.getOutputStream())
            ) {
                String clientMessage;
                while ((clientMessage = in.readLine()) != null) {
                    System.out.println("[RECEIVE]: " + clientMessage);
                    String[] data = clientMessage.split(";");
                    String command = data[0];

                    switch (command) {
                        case "LOGIN":
                            if (isBlocked(data[1])) {
                                out.println("LOGIN_FAILED;BLOCKED");
                                System.out.println("[LOGIN] User: " + data[1] + " bi tu choi vi tai khoan da bi khoa.");
                            } else if (checkLogin(data[1], data[2])) {
                                loggedInUser = data[1];
                                onlineUsers.put(loggedInUser, out);
                                out.println("LOGIN_SUCCESS");
                                System.out.println("[LOGIN] User: " + data[1] + " thanh cong! (Dang online: " + onlineUsers.size() + ")");
                                writeLog("LOGIN", loggedInUser, "");
                                notifyFriendsStatus(loggedInUser, true);
                            } else {
                                out.println("LOGIN_FAILED");
                                System.out.println("[LOGIN] User: " + data[1] + " that bai.");
                            }
                            break;

                        case "REGISTER": {
                            if (data.length < 5) {
                                out.println("REGISTER_FAILED;MISSING_OTP");
                                break;
                            }
                            String regUser = data[1];
                            String regPass = data[2];
                            String regEmail = data[3];
                            String regOtp = data[4];

                            OtpEntry entry = pendingOtp.get(regEmail);
                            if (entry == null || System.currentTimeMillis() > entry.expiresAt) {
                                out.println("REGISTER_FAILED;OTP_EXPIRED");
                            } else if (!entry.code.equals(regOtp)) {
                                out.println("REGISTER_FAILED;OTP_WRONG");
                            } else {
                                String result = registerUser(regUser, regPass, regEmail);
                                if ("OK".equals(result)) {
                                    pendingOtp.remove(regEmail); // da dung thi xoa, khong cho dung lai
                                    out.println("REGISTER_SUCCESS");
                                    System.out.println("[REGISTER] Da luu user " + regUser + " (email: " + regEmail + ") vao SQL Server!");
                                    writeLog("REGISTER", regUser, "email: " + regEmail);
                                } else if ("EXISTS".equals(result)) {
                                    out.println("REGISTER_FAILED;EXISTS");
                                } else {
                                    out.println("REGISTER_FAILED;DBERROR");
                                }
                            }
                            break;
                        }

                        case "SEND_OTP": {
                            String targetEmail = data[1];
                            String otpCode = generateOtpCode();
                            pendingOtp.put(targetEmail, new OtpEntry(otpCode, System.currentTimeMillis() + 5 * 60 * 1000)); // het han sau 5 phut
                            boolean emailSent = sendOtpEmail(targetEmail, otpCode);
                            if (emailSent) {
                                out.println("SEND_OTP_SUCCESS;" + otpCode);
                                System.out.println("[OTP] Da gui email qua Mailtrap cho: " + targetEmail + " -> Ma: " + otpCode);
                            } else {
                                out.println("SEND_OTP_FAILED;EMAIL_ERROR");
                                System.out.println("[OTP] Gui email that bai cho: " + targetEmail);
                            }
                            break;
                        }

                        case "UPDATE_PROFILE":
                            if (data.length >= 8) {
                                if (updateProfile(data[1], data[2], data[3], data[4], data[5], data[6], data[7])) {
                                    out.println("UPDATE_PROFILE_SUCCESS");
                                } else {
                                    out.println("UPDATE_PROFILE_FAILED");
                                }
                            }
                            break;

                        case "GET_PROFILE": {
                            String[] profile = getProfile(data[1]);
                            out.println("PROFILE_DATA;" + String.join(";", profile));
                            break;
                        }

                        case "GET_DISPLAY_NAMES": {
                            String csv = data.length > 1 ? data[1] : "";
                            StringBuilder sb = new StringBuilder();
                            if (!csv.isEmpty()) {
                                boolean first = true;
                                for (String u : csv.split(",")) {
                                    if (u.isEmpty()) continue;
                                    if (!first) sb.append(",");
                                    sb.append(u).append("::").append(getDisplayName(u));
                                    first = false;
                                }
                            }
                            out.println("DISPLAY_NAMES;" + sb);
                            break;
                        }

                        case "VERIFY_EMAIL_OTP": {
                            String verifyEmail = data[1];
                            String codeInput = data[2];
                            OtpEntry verifyEntry = pendingOtp.get(verifyEmail);
                            if (verifyEntry != null && System.currentTimeMillis() <= verifyEntry.expiresAt && verifyEntry.code.equals(codeInput)) {
                                pendingOtp.remove(verifyEmail);
                                out.println("VERIFY_OTP_SUCCESS");
                                System.out.println("[OTP] Email: " + verifyEmail + " xac thuc thanh cong!");
                            } else {
                                out.println("VERIFY_OTP_FAILED");
                            }
                            break;
                        }

                        case "CREATE_GROUP":
                            String creator = data[1];
                            String groupName = data[2];
                            String createResult = createGroup(groupName, creator);
                            if ("OK".equals(createResult)) {
                                out.println("CREATE_GROUP_SUCCESS;" + groupName);
                                System.out.println("[GROUP] Phong chat '" + groupName + "' da duoc tao boi " + creator);
                                writeLog("CREATE_GROUP", creator, "group: " + groupName);
                            } else if ("EXISTS".equals(createResult)) {
                                out.println("CREATE_GROUP_FAILED;EXISTS");
                            } else {
                                out.println("CREATE_GROUP_FAILED;DBERROR");
                            }
                            break;

                        case "JOIN_GROUP":
                            String userJoin = data[1];
                            String targetGroup = data[2];
                            if (joinGroup(targetGroup, userJoin)) {
                                out.println("JOIN_GROUP_SUCCESS;" + targetGroup);
                                System.out.println("[GROUP] User " + userJoin + " da tham gia vao nhom " + targetGroup);
                            } else {
                                out.println("JOIN_GROUP_FAILED");
                            }
                            break;

                        case "LEAVE_GROUP":
                            String userLeave = data[1];
                            String groupToLeave = data[2];
                            if (leaveGroup(groupToLeave, userLeave)) {
                                out.println("LEAVE_GROUP_SUCCESS;" + groupToLeave);
                                System.out.println("[GROUP] User " + userLeave + " da roi khoi nhom " + groupToLeave);
                            } else {
                                out.println("LEAVE_GROUP_FAILED");
                            }
                            break;

                        case "CHAT": {
                            String target = data[1];
                            String content = data[2];
                            String sender = loggedInUser;
                            int msgId = saveMessageAndGetId(sender, target, null, content, "TEXT");
                            PrintWriter targetOut = onlineUsers.get(target);
                            String status = "SENT";
                            if (targetOut != null) {
                                targetOut.println("MESSAGE;" + sender + ";" + content);
                                status = "DELIVERED";
                                if (msgId > 0) updateMessageStatus(msgId, "DELIVERED");
                            }
                            out.println("MSG_STATUS;" + target + ";" + status);
                            System.out.println("[CHAT] " + sender + " -> " + target + ": " + content);
                            break;
                        }

                        case "GROUP_CHAT": {
                            String chatGroupName = data[1];
                            String content = data[2];
                            String sender = loggedInUser;
                            saveMessage(sender, null, chatGroupName, content);
                            for (String member : getGroupMembers(chatGroupName)) {
                                if (member.equals(sender)) continue; // khong gui lai cho chinh nguoi gui
                                PrintWriter memberOut = onlineUsers.get(member);
                                if (memberOut != null) {
                                    memberOut.println("GROUP_MESSAGE;" + chatGroupName + ";" + sender + ";" + content);
                                }
                            }
                            System.out.println("[GROUP_CHAT] " + sender + " -> [" + chatGroupName + "]: " + content);
                            break;
                        }

                        case "FETCH_HISTORY": {
                            String chatContext = data[1]; 
                            String historyData = fetchChatHistory(loggedInUser, chatContext);
                            String myLastStatus = "NONE";
                            if (!isGroup(chatContext)) {
                                int updatedCount = markSeen(chatContext, loggedInUser);
                                if (updatedCount > 0) {
                                    PrintWriter senderOut = onlineUsers.get(chatContext);
                                    if (senderOut != null) {
                                        senderOut.println("MSG_STATUS;" + loggedInUser + ";SEEN");
                                    }
                                }
                                myLastStatus = getLastOwnMessageStatus(loggedInUser, chatContext);
                            }
                            out.println("HISTORY_DATA;" + chatContext + ";" + historyData + ";" + myLastStatus);
                            System.out.println("[HISTORY] Da tai lich su chat cho: " + chatContext);
                            break;
                        }

                        case "GET_MY_CHATS": {
                            List<String> myGroupsList = getGroupsOfUser(loggedInUser);
                            List<String> myContacts = getDmContactsOfUser(loggedInUser);
                            List<String> myFriends = getAcceptedFriends(loggedInUser);
                            for (String f : myFriends) {
                                if (!myContacts.contains(f)) myContacts.add(f);
                            }
                            out.println("MY_CHATS;" + String.join(",", myGroupsList) + ";" + String.join(",", myContacts));
                            List<String> onlineFriends = new ArrayList<>();
                            for (String f : myFriends) {
                                if (onlineUsers.containsKey(f)) onlineFriends.add(f);
                            }
                            out.println("ONLINE_FRIENDS;" + String.join(",", onlineFriends));
                            System.out.println("[DEBUG] " + loggedInUser + " co ban be (ACCEPTED): " + myFriends);
                            System.out.println("[DEBUG] " + loggedInUser + " dang online tong cong: " + onlineUsers.keySet());
                            break;
                        }

                        case "ADD_FRIEND": {
                            String toUser = data[1];
                            String fromUser = loggedInUser;
                            if (toUser.equals(fromUser)) {
                                out.println("ADD_FRIEND_FAILED;SELF");
                                break;
                            }
                            String result = sendFriendRequest(fromUser, toUser);
                            if ("OK".equals(result)) {
                                out.println("ADD_FRIEND_SUCCESS;" + toUser);
                                PrintWriter targetOut = onlineUsers.get(toUser);
                                if (targetOut != null) {
                                    targetOut.println("FRIEND_REQUEST;" + fromUser);
                                }
                            } else {
                                out.println("ADD_FRIEND_FAILED;" + result);
                            }
                            break;
                        }

                        case "ACCEPT_FRIEND": {
                            String fromUser = data[1]; // nguoi da gui loi moi truoc do
                            if (acceptFriendRequest(fromUser, loggedInUser)) {
                                out.println("ACCEPT_FRIEND_SUCCESS;" + fromUser);
                                PrintWriter targetOut = onlineUsers.get(fromUser);
                                if (targetOut != null) {
                                    targetOut.println("FRIEND_ACCEPTED;" + loggedInUser);
                                }
                                writeLog("ACCEPT_FRIEND", loggedInUser, "voi: " + fromUser);
                            } else {
                                out.println("ACCEPT_FRIEND_FAILED");
                            }
                            break;
                        }

                        case "REJECT_FRIEND": {
                            String fromUser = data[1];
                            rejectFriendRequest(fromUser, loggedInUser);
                            out.println("REJECT_FRIEND_SUCCESS;" + fromUser);
                            break;
                        }

                        case "GET_FRIEND_REQUESTS": {
                            List<String> pending = getPendingFriendRequests(loggedInUser);
                            out.println("FRIEND_REQUESTS;" + String.join(",", pending));
                            break;
                        }

                        case "FILE": {
                            String target = data[1];
                            String fileName = data[2];
                            String base64Data = data[3];
                            if (base64Data.length() > 1_398_101) {
                                out.println("FILE_FAILED;TOO_LARGE");
                                break;
                            }
                            String sender = loggedInUser;
                            boolean toGroup = isGroup(target);
                            saveMessage(sender, toGroup ? null : target, toGroup ? target : null, fileName + "‖" + base64Data, "FILE");
                            if (toGroup) {
                                for (String member : getGroupMembers(target)) {
                                    if (member.equals(sender)) continue;
                                    PrintWriter memberOut = onlineUsers.get(member);
                                    if (memberOut != null) {
                                        memberOut.println("FILE_MSG;" + target + ";" + sender + ";" + fileName + ";" + base64Data);
                                    }
                                }
                            } else {
                                PrintWriter targetOut = onlineUsers.get(target);
                                if (targetOut != null) {
                                    targetOut.println("FILE_MSG_DM;" + sender + ";" + fileName + ";" + base64Data);
                                }
                            }
                            System.out.println("[FILE] " + sender + " -> " + target + ": " + fileName + " (" + base64Data.length() + " ky tu Base64)");
                            break;
                        }

                        case "STICKER": {
                            String target = data[1];
                            String stickerCode = data[2];
                            String sender = loggedInUser;
                            boolean toGroup = isGroup(target);
                            saveMessage(sender, toGroup ? null : target, toGroup ? target : null, stickerCode, "STICKER");
                            if (toGroup) {
                                for (String member : getGroupMembers(target)) {
                                    if (member.equals(sender)) continue;
                                    PrintWriter memberOut = onlineUsers.get(member);
                                    if (memberOut != null) {
                                        memberOut.println("STICKER_MSG;" + target + ";" + sender + ";" + stickerCode);
                                    }
                                }
                            } else {
                                PrintWriter targetOut = onlineUsers.get(target);
                                if (targetOut != null) {
                                    targetOut.println("STICKER_MSG_DM;" + sender + ";" + stickerCode);
                                }
                            }
                            break;
                        }

                        case "INVITE_TO_GROUP": {
                            String groupToInvite = data[1];
                            String userToInvite = data[2];
                            if (joinGroup(groupToInvite, userToInvite)) {
                                out.println("INVITE_SUCCESS;" + groupToInvite + ";" + userToInvite);
                                PrintWriter invitedOut = onlineUsers.get(userToInvite);
                                if (invitedOut != null) {
                                    invitedOut.println("ADDED_TO_GROUP;" + groupToInvite);
                                }
                                writeLog("INVITE_TO_GROUP", loggedInUser, "them " + userToInvite + " vao nhom " + groupToInvite);
                            } else {
                                out.println("INVITE_FAILED;" + groupToInvite);
                            }
                            break;
                        }

                        default:
                            System.out.println("[⚠️ SERVER] Lenh khong hop le: " + command);
                            break;
                    }
                }
            } catch (Exception e) {
                System.out.println("[SERVER SQL] Client ngat ket noi.");
            } finally {
                if (loggedInUser != null) {
                    onlineUsers.remove(loggedInUser);
                    System.out.println("[SERVER SQL] User " + loggedInUser + " da offline. (Con lai online: " + onlineUsers.size() + ")");
                    writeLog("LOGOUT", loggedInUser, "");
                    notifyFriendsStatus(loggedInUser, false);
                }
            }
        }
    }

    private static boolean checkLogin(String user, String pass) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setString(1, user);
            pstmt.setString(2, pass);
            try (ResultSet rs = pstmt.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    private static int countTotalUsers() {
        String sql = "SELECT COUNT(*) AS total FROM users";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (conn == null) return 0;
            if (rs.next()) return rs.getInt("total");
        } catch (SQLException e) { /* tra ve 0 neu loi */ }
        return 0;
    }

    private static boolean isBlocked(String user) {
        String sql = "SELECT is_blocked FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setString(1, user);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getBoolean("is_blocked");
            }
        } catch (SQLException e) { /* mac dinh khong khoa neu loi */ }
        return false;
    }

    private static boolean setBlocked(String user, boolean blocked) {
        String sql = "UPDATE users SET is_blocked = ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setBoolean(1, blocked);
            pstmt.setString(2, user);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    private static String sendFriendRequest(String fromUser, String toUser) {
        String checkSql = "SELECT status FROM friends WHERE (user1 = ? AND user2 = ?) OR (user1 = ? AND user2 = ?)";
        String insertSql = "INSERT INTO friends (user1, user2, status) VALUES (?, ?, 'PENDING')";
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) return "ERROR";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, fromUser);
                checkStmt.setString(2, toUser);
                checkStmt.setString(3, toUser);
                checkStmt.setString(4, fromUser);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) return "EXISTS"; // da la ban hoac da gui loi moi truoc do
                }
            }
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, fromUser);
                insertStmt.setString(2, toUser);
                return insertStmt.executeUpdate() > 0 ? "OK" : "ERROR";
            }
        } catch (SQLException e) { return "ERROR"; }
    }

    private static boolean acceptFriendRequest(String fromUser, String toUser) {
        String sql = "UPDATE friends SET status = 'ACCEPTED' WHERE user1 = ? AND user2 = ? AND status = 'PENDING'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setString(1, fromUser);
            pstmt.setString(2, toUser);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    private static void rejectFriendRequest(String fromUser, String toUser) {
        String sql = "DELETE FROM friends WHERE user1 = ? AND user2 = ? AND status = 'PENDING'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return;
            pstmt.setString(1, fromUser);
            pstmt.setString(2, toUser);
            pstmt.executeUpdate();
        } catch (SQLException e) { /* bo qua neu loi */ }
    }

    private static List<String> getPendingFriendRequests(String user) {
        List<String> result = new ArrayList<>();
        String sql = "SELECT user1 FROM friends WHERE user2 = ? AND status = 'PENDING'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return result;
            pstmt.setString(1, user);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) result.add(rs.getString("user1"));
            }
        } catch (SQLException e) { /* tra ve rong neu loi */ }
        return result;
    }

    private static List<String> getAcceptedFriends(String user) {
        List<String> result = new ArrayList<>();
        String sql = "SELECT CASE WHEN user1 = ? THEN user2 ELSE user1 END AS friend_name " +
                     "FROM friends WHERE (user1 = ? OR user2 = ?) AND status = 'ACCEPTED'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return result;
            pstmt.setString(1, user);
            pstmt.setString(2, user);
            pstmt.setString(3, user);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) result.add(rs.getString("friend_name"));
            }
        } catch (SQLException e) { /* tra ve rong neu loi */ }
        return result;
    }

    private static void notifyFriendsStatus(String user, boolean online) {
        List<String> friendsList = getAcceptedFriends(user);
        String msg = (online ? "FRIEND_ONLINE;" : "FRIEND_OFFLINE;") + user;
        System.out.println("[DEBUG] notifyFriendsStatus(" + user + ", online=" + online + ") -> ban be: " + friendsList);
        for (String friend : friendsList) {
            PrintWriter friendOut = onlineUsers.get(friend);
            if (friendOut != null) {
                friendOut.println(msg);
            }
        }
    }

    private static String registerUser(String user, String pass, String email) {
        String sql = "INSERT INTO users (username, password, email) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return "DBERROR";
            pstmt.setString(1, user);
            pstmt.setString(2, pass);
            pstmt.setString(3, email);
            return pstmt.executeUpdate() > 0 ? "OK" : "DBERROR";
        } catch (SQLException e) {
            if (e.getErrorCode() == 2627 || e.getErrorCode() == 2601) {
                return "EXISTS";
            }
            System.out.println("[DATABASE ERROR] " + e.getMessage());
            return "DBERROR";
        }
    }

    private static String generateOtpCode() {
        Random rnd = new Random();
        int code = 100000 + rnd.nextInt(900000);
        return String.valueOf(code);
    }

    private static boolean sendOtpEmail(String toEmail, String otpCode) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true"); // Mailtrap Sending bat buoc TLS tren port 587
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", String.valueOf(SMTP_PORT));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        props.put("mail.smtp.localhost", "localhost");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_EMAIL, SMTP_APP_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SMTP_FROM_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject("Ma xac thuc App Chat cua ban");
            message.setText("Ma OTP xac thuc email cua ban la: " + otpCode +
                    "\nMa co hieu luc trong 5 phut. Vui long khong chia se ma nay cho bat ky ai.");
            Transport.send(message);
            return true;
        } catch (Exception e) {
            System.out.println("[EMAIL ERROR] Khong the gui email OTP: " + e.toString());
            Throwable cause = e.getCause();
            if (cause != null) {
                System.out.println("[EMAIL ERROR] Nguyen nhan goc: " + cause.toString());
            }
            e.printStackTrace();
            return false;
        }
    }

    private static boolean updateProfile(String user, String fullName, String dob, String university, String email, String phone, String displayName) {
        String sql = "UPDATE users SET full_name = ?, dob = ?, university = ?, email = ?, phone = ?, display_name = ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setString(1, fullName);
            pstmt.setString(2, dob);
            pstmt.setString(3, university);
            pstmt.setString(4, email);
            pstmt.setString(5, phone);
            pstmt.setString(6, displayName);
            pstmt.setString(7, user);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    private static String[] getProfile(String user) {
        String sql = "SELECT full_name, dob, university, email, phone, display_name FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return new String[]{"", "", "", "", "", ""};
            pstmt.setString(1, user);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new String[]{
                        nullToEmpty(rs.getString("full_name")),
                        nullToEmpty(rs.getString("dob")),
                        nullToEmpty(rs.getString("university")),
                        nullToEmpty(rs.getString("email")),
                        nullToEmpty(rs.getString("phone")),
                        nullToEmpty(rs.getString("display_name"))
                    };
                }
            }
        } catch (SQLException e) { /* tra ve rong neu loi */ }
        return new String[]{"", "", "", "", "", ""};
    }

    private static String getDisplayName(String username) {
        String sql = "SELECT display_name FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return username;
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String dn = rs.getString("display_name");
                    return (dn == null || dn.trim().isEmpty()) ? username : dn;
                }
            }
        } catch (SQLException e) { /* mac dinh dung username neu loi */ }
        return username;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String createGroup(String groupName, String creator) {
        String sql = "INSERT INTO chat_groups (group_name, creator) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return "DBERROR";
            pstmt.setString(1, groupName);
            pstmt.setString(2, creator);
            return pstmt.executeUpdate() > 0 ? "OK" : "DBERROR";
        } catch (SQLException e) {
            if (e.getErrorCode() == 2627 || e.getErrorCode() == 2601) {
                return "EXISTS";
            }
            System.out.println("[DATABASE ERROR] Tao nhom that bai: " + e.getMessage());
            return "DBERROR";
        }
    }

    private static boolean joinGroup(String groupName, String user) {
        String sql = "INSERT INTO group_members (group_name, username) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setString(1, groupName);
            pstmt.setString(2, user);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    private static boolean leaveGroup(String groupName, String user) {
        String sql = "DELETE FROM group_members WHERE group_name = ? AND username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setString(1, groupName);
            pstmt.setString(2, user);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    private static List<String> getGroupMembers(String groupName) {
        List<String> members = new ArrayList<>();
        String sql = "SELECT username FROM group_members WHERE group_name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return members;
            pstmt.setString(1, groupName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) members.add(rs.getString("username"));
            }
        } catch (SQLException e) { /* tra ve danh sach rong neu loi */ }
        return members;
    }

    private static boolean isGroup(String name) {
        String sql = "SELECT 1 FROM chat_groups WHERE group_name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    private static List<String> getGroupsOfUser(String user) {
        List<String> result = new ArrayList<>();
        String sql = "SELECT group_name FROM group_members WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return result;
            pstmt.setString(1, user);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) result.add(rs.getString("group_name"));
            }
        } catch (SQLException e) { /* tra ve danh sach rong neu loi */ }
        return result;
    }

    private static List<String> getDmContactsOfUser(String user) {
        List<String> result = new ArrayList<>();
        String sql = "SELECT DISTINCT CASE WHEN sender = ? THEN receiver ELSE sender END AS other_user " +
                     "FROM messages WHERE group_name IS NULL AND (sender = ? OR receiver = ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return result;
            pstmt.setString(1, user);
            pstmt.setString(2, user);
            pstmt.setString(3, user);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String other = rs.getString("other_user");
                    if (other != null) result.add(other);
                }
            }
        } catch (SQLException e) { /* tra ve danh sach rong neu loi */ }
        return result;
    }

    private static boolean saveMessage(String sender, String receiver, String groupName, String content) {
        return saveMessage(sender, receiver, groupName, content, "TEXT");
    }

    private static boolean saveMessage(String sender, String receiver, String groupName, String content, String msgType) {
        String sql = "INSERT INTO messages (sender, receiver, group_name, content, msg_type) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setString(1, sender);
            if (receiver == null) pstmt.setNull(2, Types.NVARCHAR); else pstmt.setString(2, receiver);
            if (groupName == null) pstmt.setNull(3, Types.NVARCHAR); else pstmt.setString(3, groupName);
            pstmt.setString(4, content);
            pstmt.setString(5, msgType);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    private static int saveMessageAndGetId(String sender, String receiver, String groupName, String content, String msgType) {
        String sql = "INSERT INTO messages (sender, receiver, group_name, content, msg_type) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            if (conn == null) return -1;
            pstmt.setString(1, sender);
            if (receiver == null) pstmt.setNull(2, Types.NVARCHAR); else pstmt.setString(2, receiver);
            if (groupName == null) pstmt.setNull(3, Types.NVARCHAR); else pstmt.setString(3, groupName);
            pstmt.setString(4, content);
            pstmt.setString(5, msgType);
            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        } catch (SQLException e) { /* tra ve -1 neu loi */ }
        return -1;
    }

    private static boolean updateMessageStatus(int id, String status) {
        String sql = "UPDATE messages SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setString(1, status);
            pstmt.setInt(2, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    private static int markSeen(String otherUser, String me) {
        String sql = "UPDATE messages SET status = 'SEEN' WHERE sender = ? AND receiver = ? AND status <> 'SEEN'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return 0;
            pstmt.setString(1, otherUser);
            pstmt.setString(2, me);
            return pstmt.executeUpdate();
        } catch (SQLException e) { return 0; }
    }

    private static String getLastOwnMessageStatus(String me, String otherUser) {
        String sql = "SELECT TOP 1 status FROM messages WHERE sender = ? AND receiver = ? ORDER BY id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return "NONE";
            pstmt.setString(1, me);
            pstmt.setString(2, otherUser);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getString("status");
            }
        } catch (SQLException e) { /* tra ve NONE neu loi */ }
        return "NONE";
    }

    private static String fetchChatHistory(String currentUser, String context) {
        StringBuilder sb = new StringBuilder();
        boolean group = isGroup(context);
        String sql = group
                ? "SELECT sender, content, msg_type FROM messages WHERE group_name = ? ORDER BY id ASC"
                : "SELECT sender, content, msg_type FROM messages WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) ORDER BY id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return "";
            if (group) {
                pstmt.setString(1, context);
            } else {
                pstmt.setString(1, currentUser);
                pstmt.setString(2, context);
                pstmt.setString(3, context);
                pstmt.setString(4, currentUser);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append("|");
                    String msgType = rs.getString("msg_type");
                    String sender = rs.getString("sender");
                    String content = rs.getString("content");
                    if ("FILE".equals(msgType)) {
                        String fileName = content.contains("‖") ? content.substring(0, content.indexOf("‖")) : content;
                        sb.append(sender).append(": [File] ").append(fileName);
                    } else if ("STICKER".equals(msgType)) {
                        sb.append(sender).append(": [Sticker] ").append(content);
                    } else {
                        sb.append(sender).append(": ").append(content);
                    }
                    first = false;
                }
            }
        } catch (SQLException e) { /* tra ve chuoi rong neu loi */ }
        return sb.toString();
    }
}