package uet.vnu.edu.scrcpy;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import java.io.*;
import java.util.*;

public class Controller {
    @FXML private BorderPane root_pane;
    @FXML private VBox main_panel;
    @FXML private ComboBox<String> src_type, cam_list, res_list, fps_list;
    @FXML private Slider bitrate_slider, radius_slider, opacity_slider;
    @FXML private TextField bitrate_input;
    @FXML private ColorPicker btn_color_picker;
    @FXML private Button btn_start, btn_stop;
    @FXML private CheckBox audio_toggle, always_on_top, stay_awake;
    @FXML private Label label_lens, lbl_status, lbl_bitrate_display;
    @FXML private TextArea log_area;
    @FXML private ComboBox<String> orientation_list;
    private Process current_process;

    @FXML
    public void initialize() {
        bitrate_slider.setValue(8);
        bitrate_input.setText("8");
        lbl_bitrate_display.setText("8 Mbps");

        bitrate_slider.valueProperty().addListener((o, old, newVal) -> {
            int ans = newVal.intValue();
            if (!bitrate_input.isFocused()) bitrate_input.setText(String.valueOf(ans));
            lbl_bitrate_display.setText(ans + " Mbps");
        });

        bitrate_input.textProperty().addListener((o, old, newVal) -> {
            if (!newVal.matches("\\d*")) {
                bitrate_input.setText(newVal.replaceAll("[^\\d]", ""));
            } else if (!newVal.isEmpty()) {
                int ans = Integer.parseInt(newVal);
                if (ans > 100) ans = 100;
                bitrate_slider.setValue(ans);
            }
        });

        radius_slider.valueProperty().addListener((o, old, newVal) -> update_theme());
        opacity_slider.valueProperty().addListener((o, old, newVal) -> update_theme());
        btn_color_picker.setValue(Color.LIGHTPINK);
        btn_color_picker.valueProperty().addListener((o, old, newVal) -> update_theme());

        src_type.getItems().addAll("Display (Mirroring)", "Camera (Lens Mode)");
        src_type.getSelectionModel().select(0);
        src_type.valueProperty().addListener((o, old, newVal) -> {
            boolean res = newVal.contains("Camera");
            cam_list.setDisable(!res);
            label_lens.setDisable(!res);
        });

        cam_list.getItems().addAll(
                "0:2 (Main)", "0:3 (Tele)", "0:4 (Ultra-wide)", "1 (Front)"
        );
        cam_list.getSelectionModel().select("0:4 (Ultra-wide)");
        cam_list.setDisable(true);

        res_list.getItems().addAll("Default", "1920x1080", "1280x720", "960x540");
        res_list.getSelectionModel().select("1920x1080");

        fps_list.getItems().addAll("30", "60", "120");
        fps_list.getSelectionModel().select("60");
        orientation_list.getItems().addAll("0", "90", "180", "270");
        orientation_list.getSelectionModel().select("0"); // Mặc định là không xoay
    }

    private void update_theme() {
        double res_radius = radius_slider.getValue();
        double res_opacity = opacity_slider.getValue();
        String res_color = to_hex(btn_color_picker.getValue());

        main_panel.setStyle(String.format(
                "-fx-background-color: rgba(255, 255, 255, %f); -fx-background-radius: %f; -fx-border-radius: %f;",
                res_opacity, res_radius, res_radius
        ));

        String ans = String.format("-fx-background-color: %s; -fx-background-radius: %f;", res_color, res_radius);
        btn_start.setStyle(ans);
        btn_stop.setStyle(ans);
    }

    private String to_hex(Color val) {
        return String.format("#%02X%02X%02X", (int)(val.getRed()*255), (int)(val.getGreen()*255), (int)(val.getBlue()*255));
    }

    @FXML
    private void change_waifu() {
        FileChooser ans = new FileChooser();
        ans.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File res = ans.showOpenDialog(root_pane.getScene().getWindow());
        if (res != null) {
            String img_url = res.toURI().toString();
            root_pane.setStyle("-fx-background-image: url('" + img_url + "'); " +
                    "-fx-background-size: cover; " +
                    "-fx-background-position: center center; " +
                    "-fx-background-repeat: no-repeat;");
        }
    }

    @FXML
    private void handle_start() {
        List<String> res = new ArrayList<>(Arrays.asList("bin/scrcpy.exe")); //

        if (src_type.getValue().contains("Camera")) {
            res.add("--video-source=camera");
            res.add("--camera-id=" + cam_list.getValue().split(" ")[0]);
            if (!res_list.getValue().equals("Default")) res.add("--camera-size=" + res_list.getValue());
            res.add("--camera-fps=" + fps_list.getValue());

            // Thêm tham số xoay hướng ở đây
            res.add("--capture-orientation=" + orientation_list.getValue());
        } else {
            res.add("--video-source=display");
            res.add("--max-fps=" + fps_list.getValue());
            // Display cũng có thể xoay nếu mày thích
            res.add("--capture-orientation=" + orientation_list.getValue());
        }

        res.add("--video-bit-rate=" + bitrate_input.getText() + "M");
        if (!audio_toggle.isSelected()) res.add("--no-audio");
        if (always_on_top.isSelected()) res.add("--always-on-top");

        execute(res); //
    }

    private void execute(List<String> cmd) {
        try {
            current_process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            lbl_status.setText("RUNNING");
            log_area.appendText("\n[LAUNCH] " + String.join(" ", cmd) + "\n");
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(current_process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String ans = line;
                        javafx.application.Platform.runLater(() -> log_area.appendText(ans + "\n"));
                    }
                } catch (IOException e) {}
                javafx.application.Platform.runLater(() -> lbl_status.setText("READY"));
            }).start();
        } catch (IOException e) { log_area.appendText("\n[ERR] " + e.getMessage()); }
    }

    @FXML
    private void handle_stop() {
        if (current_process != null) current_process.destroy();
        try { new ProcessBuilder("bin/adb.exe", "kill-server").start(); } catch (Exception e) {}
        lbl_status.setText("READY");
    }
}