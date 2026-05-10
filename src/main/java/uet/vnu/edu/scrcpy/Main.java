package uet.vnu.edu.scrcpy;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        Parent res = FXMLLoader.load(getClass().getResource("dashboard.fxml"));
        stage.setTitle("CrystalScrcpy - Aoi Edition");

        // Nap icon waifu len thanh tieu de
        try {
            Image ans = new Image(getClass().getResourceAsStream("/uet/vnu/edu/scrcpy/icon.jpg"));
            stage.getIcons().add(ans);
        } catch (Exception e) {
            System.out.println("Loi nap icon: " + e.getMessage());
        }

        stage.setScene(new Scene(res, 1100, 800));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}