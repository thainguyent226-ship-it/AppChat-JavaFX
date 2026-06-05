package src.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        try {
            // Chỉ load giao diện lên màn hình, không kết nối Socket tự động nữa
            Parent root = FXMLLoader.load(getClass().getResource("/src/client/view/LoginView.fxml"));
            primaryStage.setTitle("App Chat Client ");
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}