module uet.vnu.edu.scrcpy {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;

    // Cấp quyền cho 2 thằng này để chạy OBS WebSocket
    requires java.net.http;
    requires com.google.gson;

    // Phải mở cửa (opens) cho thằng gson nó dùng Reflection để đọc biến
    opens uet.vnu.edu.scrcpy to javafx.fxml, com.google.gson;
    exports uet.vnu.edu.scrcpy;
}