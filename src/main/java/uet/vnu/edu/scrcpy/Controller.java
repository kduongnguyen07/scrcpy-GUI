package uet.vnu.edu.scrcpy;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Controller {

    @FXML private BorderPane root_pane;
    @FXML private VBox main_panel;
    @FXML private ComboBox<String> src_type, cam_list, res_list, fps_list, orientation_list;
    @FXML private Slider bitrate_slider, radius_slider, opacity_slider;
    @FXML private TextField bitrate_input;
    @FXML private ColorPicker btn_color_picker;
    @FXML private Button btn_start, btn_stop, btn_toggle_screen;
    @FXML private CheckBox audio_toggle, always_on_top, stay_awake, record_toggle;
    @FXML private Label label_lens, lbl_status, lbl_bitrate_display, lbl_device_model;
    @FXML private TextArea log_area;
    @FXML private Label lbl_battery, lbl_cpu_temp, lbl_ram_usage;
    @FXML private CheckBox vcam_toggle;
    @FXML private ComboBox<String> vcam_mode;
    private Process vcam_process; // ffmpeg process riêng
    private static final Pattern LOGICAL_ID_PATTERN =
            Pattern.compile("Camera\\s+Id:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHYSICAL_BLOCK_PATTERN =
            Pattern.compile("(?:physicalIds|Physical\\s+Camera\\s+Ids)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");
    private static final int CAMERA_ID_MAX = 5;

    private Process current_process;
    private boolean is_screen_off = false;
    private volatile boolean device_was_connected = false;
    private static final String RECORD_DIR = System.getProperty("user.home") + "/Videos/CrystalScrcpy";
    private static final String SCREENSHOT_DIR = System.getProperty("user.home") + "/Pictures/CrystalScrcpy";
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    // OBS WebSocket
    private OBSController obs_ctrl;
    private Process mediamtx_process;
    private static final String OBS_HOST     = "127.0.0.1";
    private static final int    OBS_PORT     = 4455;
    private static final String OBS_PASSWORD = "crystal123"; // đổi theo password mày đặt
    private static final String RTSP_URL     = "rtsp://127.0.0.1:8554/phone";
    private static final String OBS_SOURCE   = "CrystalScrcpy-Phone";

    @FXML
    public void initialize() {
        update_device_info();
        bitrate_slider.setValue(8);
        bitrate_input.setText("8");
        lbl_bitrate_display.setText("8 Mbps");
        vcam_mode.getItems().addAll("OBS-Camera", "Unity Video Capture");
        vcam_mode.getSelectionModel().select(0);
        vcam_mode.disableProperty().bind(vcam_toggle.selectedProperty().not());
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

        cam_list.setDisable(true);
        res_list.getItems().addAll("Default", "1920x1080", "1280x720", "960x540");
        res_list.getSelectionModel().select("1920x1080");

        fps_list.getItems().addAll("30", "60", "120");
        fps_list.getSelectionModel().select("60");

        orientation_list.getItems().addAll("0", "90", "180", "270");
        orientation_list.getSelectionModel().select("0");
        start_resource_monitor();
        start_reconnect_watchdog();
        detect_cameras();
        detect_resolutions();
    }
    private void start_resource_monitor() {
        Thread res_thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String ans_bat = monitor_battery();
                    String ans_temp = monitor_temperature();
                    String ans_mem = monitor_ram();

                    javafx.application.Platform.runLater(() -> {
                        lbl_battery.setText(ans_bat.equals("N/A") ? "N/A" : ans_bat + "%");
                        lbl_cpu_temp.setText(ans_temp.equals("N/A") ? "N/A" : ans_temp + " °C");
                        lbl_ram_usage.setText(ans_mem.equals("N/A") ? "N/A" : ans_mem + " MB");
                    });

                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {
                }
            }
        });
        res_thread.setDaemon(true);
        res_thread.start();
    }

    private String monitor_battery() {
        try {
            Process res_p = new ProcessBuilder("bin/adb.exe", "shell", "dumpsys", "battery")
                    .redirectErrorStream(true).start();
            try (BufferedReader res_r = new BufferedReader(new InputStreamReader(res_p.getInputStream()))) {
                String res_l;
                while ((res_l = res_r.readLine()) != null) {
                    String ans = res_l.trim();
                    if (ans.startsWith("level:")) {
                        int ans_val = Integer.parseInt(ans.substring(ans.indexOf(':') + 1).trim());
                        if (ans_val >= 0 && ans_val <= 100) return String.valueOf(ans_val);
                    }
                }
            }
            res_p.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        return "N/A";
    }

    private String monitor_temperature() {
        // Ưu tiên 1: thermal sysfs — chuẩn kernel, hoạt động trên mọi Android
        // Thử lần lượt zone0 → zone1 → zone2 vì mỗi OEM map CPU ở zone khác nhau
        for (int res_zone = 0; res_zone <= 9; res_zone++) {
            try {
                Process res_p = new ProcessBuilder(
                        "bin/adb.exe", "shell", "cat",
                        "/sys/class/thermal/thermal_zone" + res_zone + "/temp")
                        .redirectErrorStream(true).start();
                try (BufferedReader res_r = new BufferedReader(new InputStreamReader(res_p.getInputStream()))) {
                    String ans = res_r.readLine();
                    if (ans != null && ans.trim().matches("\\d+")) {
                        int res_raw = Integer.parseInt(ans.trim());
                        // Loại zone trả về 0 hoặc giá trị ảo < 1000 milli-celsius
                        if (res_raw <= 0) continue;
                        // Chuẩn: > 1000 là milli-celsius, <= 1000 là tenths-of-degree (Qualcomm cũ)
                        double ans_c = res_raw > 1000 ? res_raw / 1000.0 : res_raw / 10.0;
                        // Chỉ lấy giá trị hợp lý (15°C – 120°C)
                        if (ans_c >= 15.0 && ans_c <= 120.0) {
                            return String.format("%.1f", ans_c);
                        }
                    }
                }
                res_p.waitFor(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }

        // Fallback: dumpsys battery temperature (tenths of degree Celsius, mọi Android đều có)
        try {
            Process res_p = new ProcessBuilder("bin/adb.exe", "shell", "dumpsys", "battery")
                    .redirectErrorStream(true).start();
            try (BufferedReader res_r = new BufferedReader(new InputStreamReader(res_p.getInputStream()))) {
                String res_l;
                while ((res_l = res_r.readLine()) != null) {
                    String ans = res_l.trim();
                    if (ans.startsWith("temperature:")) {
                        int ans_raw = Integer.parseInt(ans.substring(ans.indexOf(':') + 1).trim());
                        double ans_c = ans_raw / 10.0;
                        if (ans_c >= 15.0 && ans_c <= 120.0) return String.format("%.1f", ans_c);
                    }
                }
            }
            res_p.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }

        return "N/A";
    }

    private String monitor_ram() {
        // /proc/meminfo là chuẩn POSIX kernel — có trên 100% thiết bị Android
        try {
            Process res_p = new ProcessBuilder("bin/adb.exe", "shell", "cat", "/proc/meminfo")
                    .redirectErrorStream(true).start();
            long ans_total = -1;
            long ans_avail = -1;
            try (BufferedReader res_r = new BufferedReader(new InputStreamReader(res_p.getInputStream()))) {
                String res_l;
                while ((res_l = res_r.readLine()) != null) {
                    String ans = res_l.trim();
                    if (ans.startsWith("MemTotal:") && ans_total < 0) {
                        ans_total = parse_meminfo_kb(ans);
                    } else if (ans.startsWith("MemAvailable:") && ans_avail < 0) {
                        ans_avail = parse_meminfo_kb(ans);
                    }
                    if (ans_total > 0 && ans_avail >= 0) break;
                }
            }
            res_p.waitFor(3, TimeUnit.SECONDS);
            if (ans_total > 0 && ans_avail >= 0) {
                long ans_used = (ans_total - ans_avail) / 1024;
                long ans_total_mb = ans_total / 1024;
                return ans_used + "/" + ans_total_mb;
            }
        } catch (Exception ignored) {
        }
        return "N/A";
    }

    private long parse_meminfo_kb(String res_line) {
        try {
            return Long.parseLong(res_line.split("\\s+")[1]);
        } catch (Exception ignored) {
            return -1;
        }
    }
    private void detect_cameras() {
        new Thread(() -> {
            release_camera_processes();

            Set<String> res = new LinkedHashSet<>();

            try {
                Process proc = new ProcessBuilder("bin/adb.exe", "shell", "dumpsys", "media.camera")
                        .redirectErrorStream(true)
                        .start();

                List<String> lines = new ArrayList<>();
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }

                proc.waitFor(5, TimeUnit.SECONDS);

                String currentLogical = null;

                for (int i = 0; i < lines.size(); i++) {
                    String ans = lines.get(i).trim();

                    Matcher logicalMatcher = LOGICAL_ID_PATTERN.matcher(ans);
                    if (logicalMatcher.find()) {
                        int id = Integer.parseInt(logicalMatcher.group(1));
                        if (id <= CAMERA_ID_MAX) {
                            currentLogical = String.valueOf(id);
                            res.add(currentLogical);
                        } else {
                            currentLogical = null;
                        }
                        continue;
                    }

                    if (currentLogical != null && PHYSICAL_BLOCK_PATTERN.matcher(ans).find()) {
                        List<String> physicalIds = extract_ids_from_block(lines, i);
                        boolean hasStandalonePhysical = physicalIds.stream()
                                .anyMatch(pid -> !res.contains(pid));

                        for (String pid : physicalIds) {
                            if (pid.equals(currentLogical)) continue;
                            if (hasStandalonePhysical && res.contains(pid)) {
                                res.add(pid);
                            } else {
                                res.add(currentLogical + ":" + pid);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        log_area.appendText("[WARN] detect_cameras failed: " + e.getMessage() + "\n"));
            }

            // ... (Phần parse dumpsys ở trên giữ nguyên)

            List<String> ansList = build_sorted_list(res);

            javafx.application.Platform.runLater(() -> {
                // FIX BUG: Lưu lại giá trị user đang chọn trước khi clear list
                String currentSelection = cam_list.getValue();

                cam_list.getItems().clear();
                if (ansList.isEmpty()) {
                    cam_list.getItems().addAll("0", "1", "0:2", "0:3", "0:4");
                } else {
                    cam_list.getItems().addAll(ansList);
                }

                // Khôi phục lại lựa chọn cũ nếu nó tồn tại trong list mới
                if (currentSelection != null && cam_list.getItems().contains(currentSelection)) {
                    cam_list.setValue(currentSelection);
                } else {
                    cam_list.getSelectionModel().select(0);
                }
            });
        }).start();
    }

    private List<String> extract_ids_from_block(List<String> lines, int startIndex) {
        List<String> res = new ArrayList<>();
        String blockLine = lines.get(startIndex);

        int openBracket = blockLine.indexOf('[');
        int closeBracket = blockLine.indexOf(']');

        if (openBracket != -1 && closeBracket != -1 && closeBracket > openBracket) {
            String inner = blockLine.substring(openBracket + 1, closeBracket);
            Matcher m = DIGIT_PATTERN.matcher(inner);
            while (m.find()) {
                int id = Integer.parseInt(m.group(1));
                if (id <= CAMERA_ID_MAX) res.add(String.valueOf(id));
            }
            return res;
        }

        for (int j = startIndex + 1; j < Math.min(startIndex + 10, lines.size()); j++) {
            String ans = lines.get(j).trim();
            if (ans.isEmpty() || LOGICAL_ID_PATTERN.matcher(ans).find()) break;
            Matcher m = DIGIT_PATTERN.matcher(ans);
            while (m.find()) {
                int id = Integer.parseInt(m.group(1));
                if (id <= CAMERA_ID_MAX) res.add(String.valueOf(id));
            }
            if (!res.isEmpty()) break;
        }

        return res;
    }

    private List<String> build_sorted_list(Set<String> res) {
        List<String> standalone = new ArrayList<>();
        List<String> combo = new ArrayList<>();

        for (String id : res) {
            if (id.contains(":")) {
                combo.add(id);
            } else {
                standalone.add(id);
            }
        }

        Collections.sort(standalone);
        Collections.sort(combo);

        List<String> ans = new ArrayList<>();
        ans.addAll(standalone);
        ans.addAll(combo);
        return ans;
    }

    private void release_camera_processes() {
        try {
            new ProcessBuilder("bin/adb.exe", "shell", "pkill", "-f", "android.hardware.camera")
                    .start().waitFor(2, TimeUnit.SECONDS);
            new ProcessBuilder("bin/adb.exe", "shell", "pkill", "-f", "camera")
                    .start().waitFor(2, TimeUnit.SECONDS);
            Thread.sleep(800);
        } catch (Exception ignored) {
        }
    }

    private void update_device_info() {
        new Thread(() -> {
            try {
                Process res_process = new ProcessBuilder("bin/adb.exe", "shell", "getprop", "ro.product.model").start();
                BufferedReader res_reader = new BufferedReader(new InputStreamReader(res_process.getInputStream()));
                String res = res_reader.readLine();

                if (res != null && !res.isEmpty()) {
                    String ans = res.trim();
                    javafx.application.Platform.runLater(() -> lbl_device_model.setText(ans));
                } else {
                    javafx.application.Platform.runLater(() -> lbl_device_model.setText("No Device Found"));
                }
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> lbl_device_model.setText("Offline"));
            }
        }).start();
    }

    private void update_theme() {
        double res_radius = radius_slider.getValue();
        double res_opacity = opacity_slider.getValue();
        String res_color = to_hex(btn_color_picker.getValue());

        main_panel.setStyle(String.format(
                "-fx-background-color: rgba(255, 255, 255, %f); -fx-background-radius: %f; -fx-border-radius: %f;",
                res_opacity, res_radius, res_radius));

        String ans = String.format("-fx-background-color: %s; -fx-background-radius: %f;", res_color, res_radius);
        btn_start.setStyle(ans);
        btn_stop.setStyle(ans);
        btn_toggle_screen.setStyle(ans);
    }

    private String to_hex(Color val) {
        return String.format("#%02X%02X%02X",
                (int) (val.getRed() * 255),
                (int) (val.getGreen() * 255),
                (int) (val.getBlue() * 255));
    }

    @FXML
    private void change_waifu() {
        FileChooser ans = new FileChooser();
        ans.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File res = ans.showOpenDialog(root_pane.getScene().getWindow());
        if (res != null) {
            String ans_url = res.toURI().toString();
            root_pane.setStyle("-fx-background-image: url('" + ans_url + "'); -fx-background-size: cover; -fx-background-position: center center; -fx-background-repeat: no-repeat;");
        }
    }

    private void send_adb_cmd(String... cmd) {
        new Thread(() -> {
            try {
                List<String> res = new ArrayList<>();
                res.add("bin/adb.exe");
                res.add("shell");
                res.addAll(Arrays.asList(cmd));
                new ProcessBuilder(res).start();
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void send_shortcut(int ans_key, boolean ans_shift) {
        new Thread(() -> {
            try {
                // FIX: Dùng $p.Id (Process ID) thay vì MainWindowHandle.
                // Bỏ điều kiện MainWindowHandle -ne 0 để nó vẫn tìm được cửa sổ kể cả khi đang tàng hình 1x1 pixel.
                String res = "$p = Get-Process scrcpy -ErrorAction SilentlyContinue | Select-Object -First 1; if ($p) { $wshell = New-Object -ComObject wscript.shell; $wshell.AppActivate($p.Id); }";
                Process res_p = new ProcessBuilder("powershell.exe", "-Command", res).start();
                res_p.waitFor();

                // Tăng thời gian đợi lên 300ms để đảm bảo Windows kịp đưa Scrcpy lên trên cùng
                Thread.sleep(300);

                Robot res_robot = new Robot();
                res_robot.keyPress(KeyEvent.VK_ALT);
                if (ans_shift) res_robot.keyPress(KeyEvent.VK_SHIFT);
                res_robot.keyPress(ans_key);
                res_robot.keyRelease(ans_key);
                if (ans_shift) res_robot.keyRelease(KeyEvent.VK_SHIFT);
                res_robot.keyRelease(KeyEvent.VK_ALT);
            } catch (Exception ignored) {
            }
        }).start();
    }

    @FXML private void toggle_screen() {
        is_screen_off = !is_screen_off;
        send_shortcut(KeyEvent.VK_O, !is_screen_off);
    }
    @FXML private void act_stealth() {
        // Gửi phím Alt + D tới cửa sổ Scrcpy
        send_shortcut(KeyEvent.VK_D, false);
    }

    @FXML private void act_rotate() { send_shortcut(KeyEvent.VK_R, false); }
    @FXML private void act_home() { send_adb_cmd("input", "keyevent", "3"); }
    @FXML private void act_back() { send_adb_cmd("input", "keyevent", "4"); }
    @FXML private void act_switch() { send_adb_cmd("input", "keyevent", "187"); }
    @FXML private void act_vol_up() { send_adb_cmd("input", "keyevent", "24"); }
    @FXML private void act_vol_down() { send_adb_cmd("input", "keyevent", "25"); }
    @FXML private void act_power() { send_adb_cmd("input", "keyevent", "26"); }
    @FXML private void act_expand_notif() { send_adb_cmd("cmd", "statusbar", "expand-notifications"); }
    @FXML private void act_collapse() { send_adb_cmd("cmd", "statusbar", "collapse"); }

    @FXML
    private void handle_start() {
        handle_stop();
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        List<String> res = new ArrayList<>(Arrays.asList("bin/scrcpy.exe"));

        if (src_type.getValue().contains("Camera")) {
            res.add("--video-source=camera");
            res.add("--camera-id=" + cam_list.getValue());
            res.add("--camera-fps=" + fps_list.getValue());
            res.add("--capture-orientation=" + orientation_list.getValue());
        } else {
            res.add("--video-source=display");
            res.add("--max-fps=" + fps_list.getValue());
            res.add("--capture-orientation=" + orientation_list.getValue());
        }

        String selectedRes = res_list.getValue();
        if (selectedRes != null && !selectedRes.equals("Default")) {
            try {
                String[] parts = selectedRes.split("x");
                int maxDimension = Math.max(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                res.add("--max-size=" + maxDimension);
            } catch (Exception ignored) {}
        }

        res.add("--video-bit-rate=" + bitrate_input.getText() + "M");
        if (!audio_toggle.isSelected()) res.add("--no-audio");
        if (always_on_top.isSelected()) res.add("--always-on-top");
        if (stay_awake.isSelected()) res.add("--stay-awake");

        // C đã tự động đẩy vào pipe nếu OBS đang bật, nên cứ execute bình thường!
        execute(res);
    }
    private void launch_with_virtual_cam(List<String> scrcpy_args) {
        scrcpy_args.add("--always-on-top");

        try {
            // ── Bước 1: Khởi động scrcpy bình thường ──────────────────────
            ProcessBuilder pb_scrcpy = new ProcessBuilder(scrcpy_args);
            pb_scrcpy.redirectErrorStream(true);
            current_process = pb_scrcpy.start();
            lbl_status.setText("VCAM");

            new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(current_process.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String l = line;
                        javafx.application.Platform.runLater(() -> log_area.appendText(l + "\n"));
                    }
                } catch (IOException ignored) {}
            }).start();

            javafx.application.Platform.runLater(() ->
                    log_area.appendText("[VCAM] scrcpy started, đợi window...\n"));

            Thread.sleep(3000);

            // ── Bước 2: Xác định tham số ──────────────────────────────────
            String device_title = lbl_device_model.getText().trim();
            String fps          = fps_list.getValue();
            boolean use_unity   = vcam_mode.getValue().contains("Unity");

            // Named pipe do driver tạo sẵn — không cần tạo thủ công
            String pipe_path = use_unity
                    ? "\\\\.\\pipe\\unitycapture"
                    : "\\\\.\\pipe\\obs-virtualcam";

            javafx.application.Platform.runLater(() ->
                    log_area.appendText("[VCAM] Grabbing: \"" + device_title + "\" → " + pipe_path + "\n"));

            // ── Bước 3: ffmpeg gdigrab → rawvideo → stdout ─────────────────
            List<String> ffmpeg_cmd = new ArrayList<>(Arrays.asList(
                    "bin/ffmpeg.exe",
                    "-loglevel", "warning",
                    "-f", "gdigrab",
                    "-framerate", fps,
                    "-i", "title=" + device_title,
                    "-vf", "scale=1280:720,format=bgr24",
                    "-f", "rawvideo",
                    "-"
            ));

            ProcessBuilder pb_ffmpeg = new ProcessBuilder(ffmpeg_cmd);
            pb_ffmpeg.redirectErrorStream(false);
            vcam_process = pb_ffmpeg.start();

            // Log ffmpeg stderr
            new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(vcam_process.getErrorStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String l = line;
                        javafx.application.Platform.runLater(() ->
                                log_area.appendText("[FFMPEG] " + l + "\n"));
                    }
                } catch (IOException ignored) {}
                javafx.application.Platform.runLater(() -> lbl_status.setText("READY"));
            }).start();

            // ── Bước 4: Pipe rawvideo → named pipe của driver ──────────────
            InputStream ffmpeg_out = vcam_process.getInputStream();
            final String final_pipe = pipe_path;
            new Thread(() -> {
                try (FileOutputStream pipe_out = new FileOutputStream(final_pipe)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = ffmpeg_out.read(buf)) != -1) {
                        pipe_out.write(buf, 0, n);
                    }
                } catch (IOException e) {
                    javafx.application.Platform.runLater(() ->
                            log_area.appendText("[VCAM PIPE ERR] " + e.getMessage()
                                    + "\n→ Driver chưa cài? Xem hướng dẫn trong README\n"));
                }
            }).start();

            String driver_name = use_unity ? "Unity Video Capture" : "OBS Virtual Camera";
            javafx.application.Platform.runLater(() ->
                    log_area.appendText("[VCAM] ✓ Đang stream vào \"" + driver_name + "\"\n"
                            + "[VCAM] Mở Discord/Meet → chọn camera \"" + driver_name + "\" là xong!\n"));

        } catch (Exception e) {
            javafx.application.Platform.runLater(() ->
                    log_area.appendText("[VCAM ERR] " + e.getMessage() + "\n"));
        }
    }
    private void execute(List<String> cmd) {
        try {
            current_process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            lbl_status.setText("RUNNING");
            log_area.appendText("\n[LAUNCH] " + String.join(" ", cmd) + "\n");
            is_screen_off = false;

            new Thread(() -> {
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(current_process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String ans = line;
                        javafx.application.Platform.runLater(() -> log_area.appendText(ans + "\n"));
                    }
                } catch (IOException ignored) {
                }
                javafx.application.Platform.runLater(() -> lbl_status.setText("READY"));
                if (record_toggle.isSelected()) {
                    open_record_folder();
                }
            }).start();
        } catch (IOException e) {
            log_area.appendText("\n[ERR] " + e.getMessage());
        }
    }


    @FXML
    private void handle_screenshot() {
        new Thread(() -> {
            try {
                Files.createDirectories(Paths.get(SCREENSHOT_DIR));
                String ans_ts = LocalDateTime.now().format(TIMESTAMP_FMT);
                String ans_path = SCREENSHOT_DIR + "/shot_" + ans_ts + ".png";

                Process res_p = new ProcessBuilder(
                        "bin/adb.exe", "exec-out", "screencap", "-p")
                        .start();

                try (InputStream res_in = res_p.getInputStream();
                     FileOutputStream res_out = new FileOutputStream(ans_path)) {
                    byte[] res_buf = new byte[8192];
                    int res_n;
                    while ((res_n = res_in.read(res_buf)) != -1) {
                        res_out.write(res_buf, 0, res_n);
                    }
                }
                res_p.waitFor(10, TimeUnit.SECONDS);

                File res_file = new File(ans_path);
                if (res_file.exists() && res_file.length() > 1024) {
                    javafx.application.Platform.runLater(() ->
                            log_area.appendText("[SHOT] Saved: " + ans_path + "\n"));
                    Desktop.getDesktop().open(new File(SCREENSHOT_DIR));
                } else {
                    res_file.delete();
                    javafx.application.Platform.runLater(() ->
                            log_area.appendText("[SHOT] Failed — no device or empty output\n"));
                }
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        log_area.appendText("[SHOT] Error: " + e.getMessage() + "\n"));
            }
        }).start();
    }

    private void open_record_folder() {
        try {
            File res_dir = new File(RECORD_DIR);
            if (res_dir.exists() && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(res_dir);
            }
        } catch (Exception ignored) {
        }
    }

    private void start_reconnect_watchdog() {
        Thread res_thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5000);

                    Process res_p = new ProcessBuilder("bin/adb.exe", "get-state")
                            .redirectErrorStream(true).start();
                    String res_state = null;
                    try (BufferedReader res_r =
                                 new BufferedReader(new InputStreamReader(res_p.getInputStream()))) {
                        res_state = res_r.readLine();
                    }
                    res_p.waitFor(3, TimeUnit.SECONDS);

                    boolean ans_online = "device".equals(res_state != null ? res_state.trim() : "");

                    if (ans_online && !device_was_connected) {
                        device_was_connected = true;
                        javafx.application.Platform.runLater(() -> {
                            log_area.appendText("[ADB] Device reconnected — refreshing...\n");
                            lbl_device_model.setText("Detecting...");
                            lbl_battery.setText("--%");
                            lbl_cpu_temp.setText("-- °C");
                            lbl_ram_usage.setText("-- MB");
                        });
                        update_device_info();
                        detect_cameras();
                        detect_resolutions();
                    } else if (!ans_online && device_was_connected) {
                        device_was_connected = false;
                        javafx.application.Platform.runLater(() -> {
                            log_area.appendText("[ADB] Device disconnected\n");
                            lbl_device_model.setText("Offline");
                            lbl_battery.setText("N/A");
                            lbl_cpu_temp.setText("N/A");
                            lbl_ram_usage.setText("N/A");
                            lbl_status.setText("READY");
                        });
                        if (current_process != null) current_process.destroy();
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {
                }
            }
        });
        res_thread.setDaemon(true);
        res_thread.start();
    }

    private void detect_resolutions() {
        new Thread(() -> {
            List<String> res = new ArrayList<>();
            res.add("Default");

            try {
                Process res_p1 = new ProcessBuilder("bin/adb.exe", "shell", "wm", "size")
                        .redirectErrorStream(true).start();
                String ans_native = null;
                try (BufferedReader res_r =
                             new BufferedReader(new InputStreamReader(res_p1.getInputStream()))) {
                    String res_l;
                    while ((res_l = res_r.readLine()) != null) {
                        if (res_l.contains("Physical size:") || res_l.contains("Override size:")) {
                            Matcher m = Pattern.compile("(\\d{3,4})x(\\d{3,4})").matcher(res_l);                            if (m.find()) {
                                int ans_w = Integer.parseInt(m.group(1));
                                int ans_h = Integer.parseInt(m.group(2));
                                if (ans_w < ans_h) { int tmp = ans_w; ans_w = ans_h; ans_h = tmp; }
                                ans_native = ans_w + "x" + ans_h;
                            }
                        }
                    }
                }
                res_p1.waitFor(3, TimeUnit.SECONDS);

                Set<String> ans_modes = new LinkedHashSet<>();
                Process res_p2 = new ProcessBuilder("bin/adb.exe", "shell", "dumpsys", "display")
                        .redirectErrorStream(true).start();
                try (BufferedReader res_r =
                             new BufferedReader(new InputStreamReader(res_p2.getInputStream()))) {
                    String res_l;
                    while ((res_l = res_r.readLine()) != null) {
                        Matcher m = Pattern.compile("(\\d{3,4})x(\\d{3,4})").matcher(res_l);                        while (m.find()) {
                            int ans_w = Integer.parseInt(m.group(1));
                            int ans_h = Integer.parseInt(m.group(2));
                            if (ans_w < 480 || ans_h < 480 || ans_w > 4096 || ans_h > 4096) continue;
                            if (ans_w < ans_h) { int tmp = ans_w; ans_w = ans_h; ans_h = tmp; }
                            ans_modes.add(ans_w + "x" + ans_h);
                        }
                    }
                }
                res_p2.waitFor(5, TimeUnit.SECONDS);

                if (ans_modes.isEmpty() && ans_native != null) {
                    String[] ans_parts = ans_native.split("x");
                    int ans_w = Integer.parseInt(ans_parts[0]);
                    int ans_h = Integer.parseInt(ans_parts[1]);
                    ans_modes.add(ans_w + "x" + ans_h);
                    int[][] ans_scales = {{2, 3}, {1, 2}, {1, 3}, {1, 4}};
                    for (int[] ans_s : ans_scales) {
                        int ans_sw = (ans_w * ans_s[0] / ans_s[1]) & ~1;
                        int ans_sh = (ans_h * ans_s[0] / ans_s[1]) & ~1;
                        if (ans_sw >= 480) ans_modes.add(ans_sw + "x" + ans_sh);
                    }
                }

                List<String> ans_sorted = new ArrayList<>(ans_modes);
                ans_sorted.sort((a, b) -> {
                    String[] pa = a.split("x"), pb = b.split("x");
                    int area_a = Integer.parseInt(pa[0]) * Integer.parseInt(pa[1]);
                    int area_b = Integer.parseInt(pb[0]) * Integer.parseInt(pb[1]);
                    return Integer.compare(area_b, area_a);
                });
                res.addAll(ans_sorted);

            } catch (Exception ignored) {
                res.addAll(Arrays.asList("1920x1080", "1280x720", "960x540"));
            }

            List<String> ans_final = res;
            javafx.application.Platform.runLater(() -> {
                String ans_cur = res_list.getValue();
                res_list.getItems().setAll(ans_final);
                if (ans_final.contains(ans_cur)) res_list.setValue(ans_cur);
                else res_list.getSelectionModel().select(0);
            });
        }).start();
    }

    @FXML
    private void handle_stop() {
        if (current_process != null) current_process.destroy();
        if (vcam_process != null) vcam_process.destroy(); // thêm dòng này
        new Thread(() -> {
            try {
                new ProcessBuilder("bin/adb.exe", "shell", "pkill", "-f", "scrcpy")
                        .start().waitFor(1, TimeUnit.SECONDS);
                new ProcessBuilder("bin/adb.exe", "shell", "pkill", "-f", "camera")
                        .start().waitFor(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }).start();
        lbl_status.setText("READY");
    }
}