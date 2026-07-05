package src.client.controller;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class ProfileController {
    @FXML private TextField txtDisplayName;
    @FXML private TextField txtFullName;
    @FXML private DatePicker datePickerDob;
    @FXML private TextField txtUniversity;
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;
    @FXML private Button btnSave;

    private PrintWriter out;
    private String currentUsername;

    public void initData(PrintWriter out, String username) {
        this.out = out;
        this.currentUsername = username;
    }

    // Duoc ChatController goi khi Server tra ve PROFILE_DATA, tu dien san thong tin da luu truoc do
    public void populateFields(String fullName, String dob, String university, String email, String phone, String displayName) {
        txtFullName.setText(fullName);
        txtUniversity.setText(university);
        txtEmail.setText(email);
        txtPhone.setText(phone);
        txtDisplayName.setText(displayName.isEmpty() ? currentUsername : displayName);
        if (!dob.isEmpty()) {
            try {
                datePickerDob.setValue(LocalDate.parse(dob));
            } catch (Exception e) {
                // Bo qua neu dinh dang ngay cu khong hop le
            }
        }
    }

    @FXML
    public void handleSaveProfile(ActionEvent event) {
        String displayName = txtDisplayName.getText().trim();
        String fullName = txtFullName.getText().trim();
        String dob = (datePickerDob.getValue() != null) ? datePickerDob.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE) : "";
        String university = txtUniversity.getText().trim();
        String email = txtEmail.getText().trim();
        String phone = txtPhone.getText().trim();

        if (displayName.isEmpty() || fullName.isEmpty() || dob.isEmpty() || university.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            showAlert("Thiếu thông tin", "Vui lòng điền đầy đủ tất cả các trường thông tin!", Alert.AlertType.WARNING);
            return;
        }
        if (!email.matches("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$")) {
            showAlert("Email không hợp lệ", "Vui lòng nhập đúng định dạng email (vd: ten@gmail.com)!", Alert.AlertType.WARNING);
            return;
        }
        if (!phone.matches("^0\\d{9,10}$")) {
            showAlert("Số điện thoại không hợp lệ", "Số điện thoại phải bắt đầu bằng 0 và có 10-11 chữ số!", Alert.AlertType.WARNING);
            return;
        }

        if (out != null) {
            btnSave.setDisable(true);
            btnSave.setText("Đang lưu...");
            out.println("UPDATE_PROFILE;" + currentUsername + ";" + fullName + ";" + dob + ";" + university + ";" + email + ";" + phone + ";" + displayName);
        }
    }

    // Duoc ChatController goi khi Server xac nhan luu thanh cong
    public void onSaveSuccess() {
        showAlert("Thành công", "Đã cập nhật thông tin cá nhân!", Alert.AlertType.INFORMATION);
        Stage stage = (Stage) txtFullName.getScene().getWindow();
        stage.close();
    }

    // Duoc ChatController goi khi Server bao luu that bai - giu cua so mo lai de thu tiep
    public void onSaveFailed() {
        btnSave.setDisable(false);
        btnSave.setText("Lưu thay đổi");
        showAlert("Thất bại", "Không thể cập nhật thông tin. Kiểm tra kết nối tới Server rồi thử lại.", Alert.AlertType.ERROR);
    }

    @FXML
    public void handleClose(ActionEvent event) {
        Stage stage = (Stage) txtFullName.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}