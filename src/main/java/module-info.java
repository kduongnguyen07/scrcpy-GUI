module uet.vnu.edu.scrcpy {
    requires javafx.controls;
    requires javafx.fxml;


    opens uet.vnu.edu.scrcpy to javafx.fxml;
    exports uet.vnu.edu.scrcpy;
}