# 🌸 CrystalScrcpy - Aoi Edition 🌸

**CrystalScrcpy** là một phiên bản được "độ" lại (heavily modded) từ mã nguồn mở [Scrcpy](https://github.com/Genymobile/scrcpy) của Genymobile. Dự án này cung cấp một **Dashboard JavaFX** tuyệt đẹp và can thiệp sâu vào lõi C/C++ để biến điện thoại Android của bạn thành một **Webcam PC chuyên nghiệp với độ trễ 0ms**.

Đặc biệt, phiên bản này giải quyết triệt để các giới hạn của Android Camera API và Windows Desktop Window Manager (DWM).

---

## ✨ Tính năng nổi bật (Killer Features)

### 1. 📸 Truy cập ống kính vật lý (Physical Camera Lenses)
Scrcpy gốc chỉ cho phép truy cập "Logical Camera" (thường là ống kính chính). CrystalScrcpy can thiệp vào `CameraCapture.java` để ép Android mở các **ống kính vật lý (Physical Camera)** như Góc siêu rộng (Ultrawide), Telephoto, Macro...
* **Cú pháp:** `0:2`, `0:3`, `0:4` (Trong đó `0` là Logical ID, `2/3/4` là Physical ID).
* Tự động scale đúng tỷ lệ 4:3 của cảm biến, không bị méo hình.

### 2. 🚀 Native Virtual Webcam & NDI (Zero-Latency)
Không sử dụng FFmpeg hay Java để quay màn hình cửa sổ (gây lag và tốn CPU). CrystalScrcpy bơm trực tiếp khung hình (AVFrame) đã giải mã từ lõi C vào:
* **NDI (Network Device Interface):** Chuẩn truyền phát video công nghiệp, nhận diện ngay lập tức trên OBS, Discord, Zoom, Google Meet.
* **OBS Virtual Camera / Unity Capture:** Giao tiếp trực tiếp qua Windows Named Pipe (`\\.\pipe\obs-virtualcam`).

### 3. 👻 Chế độ tàng hình (Stealth Mode - Anti Throttling)
**Vấn đề:** Khi thu nhỏ (Minimize) cửa sổ trên Windows, hệ điều hành sẽ bóp hiệu năng (Throttle) GPU/CPU, khiến luồng Webcam bị tụt FPS thê thảm.
**Giải pháp:** Nút **Stealth** (hoặc phím tắt `Alt + D`).
* Biến cửa sổ thành 1x1 pixel.
* Xóa viền (Borderless) và ghim lên trên cùng (Always on top).
* **Ẩn hoàn toàn khỏi Taskbar và Alt+Tab** (Chuyển thành `WS_EX_TOOLWINDOW`).
* Đánh lừa Windows rằng cửa sổ vẫn đang hoạt động, giữ vững **60 FPS** cho luồng Webcam mà không chiếm diện tích màn hình.

### 4. 🎨 Giao diện JavaFX Dashboard (Aoi Edition)
* Giao diện kính mờ (Glassmorphism) hiện đại.
* Tùy chỉnh hình nền Waifu, độ trong suốt, bo góc, màu sắc nút bấm.
* Theo dõi tài nguyên thiết bị theo thời gian thực: **% Pin, Nhiệt độ CPU, RAM**.
* Bảng điều khiển phần cứng: Tăng/giảm âm lượng, tắt màn hình, xoay màn hình, chụp ảnh, quay video.

---

## ⚙️ Yêu cầu hệ thống (Prerequisites)

1. **Điện thoại Android:**
   * Android 12 trở lên (Bắt buộc để dùng tính năng Camera).
   * Đã bật **USB Debugging** (Gỡ lỗi USB).
   * (Tùy chọn) Bật *Disable Permission Monitoring* hoặc *Simulate input* trong Developer Options để dùng chuột/phím.
2. **Máy tính (Windows):**
   * Cài đặt [NDI Tools](https://ndi.video/tools/) (Khuyên dùng) HOẶC [OBS Studio](https://obsproject.com/).

---

## 🚀 Hướng dẫn sử dụng

### Bước 1: Kết nối
1. Cắm cáp USB từ điện thoại vào máy tính.
2. Mở file `CrystalScrcpy.exe` (hoặc chạy qua file `.bat`).
3. Cấp quyền ADB trên màn hình điện thoại (nếu có). Dashboard sẽ hiển thị tên thiết bị, Pin, Nhiệt độ.

### Bước 2: Khởi chạy Webcam
1. Ở mục **Source**, chọn `Camera (Lens Mode)`.
2. Ở mục **Lens**, chọn ống kính bạn muốn (VD: `0` là cam chính, `0:2` hoặc `0:3` là góc rộng/tele).
3. Ở mục **Resolution**, chọn `1920x1080` hoặc `1280x720` (App sẽ tự động tính toán `--max-size` để giữ đúng tỷ lệ 4:3 của cảm biến).
4. Bấm **Launch**.

### Bước 3: Đưa lên Discord / Google Meet / OBS
* **Cách 1 (Dùng NDI - Khuyên dùng):** Mở Discord/Meet -> Cài đặt Camera -> Chọn **NDI Video** (Luồng tên là `Scrcpy-Webcam`).
* **Cách 2 (Dùng OBS):** Mở OBS -> Bấm *Start Virtual Camera* -> Mở Discord chọn *OBS-Camera*.

### Bước 4: Bật Stealth Mode (Chống Lag)
Khi đã lên hình trên Discord, để ẩn cửa sổ Scrcpy mà không bị tụt FPS:
* Bấm nút **👻 Stealth** trên Dashboard.
* HOẶC bấm tổ hợp phím **`Alt + D`** khi đang trỏ chuột ở cửa sổ Scrcpy.
* *(Bấm lại lần nữa để khôi phục cửa sổ).*

---

## 🛠 Hướng dẫn Build từ mã nguồn (Dành cho Developer)

Dự án gồm 2 phần: Dashboard (Java) và Core (C/C++).

### 1. Build Core Scrcpy (C/C++)
Yêu cầu: Cài đặt **MSYS2** (MinGW 64-bit) trên Windows.
Mở MSYS2 terminal và chạy:
```bash
# Cài đặt thư viện
pacman -S mingw-w64-x86_64-make mingw-w64-x86_64-gcc mingw-w64-x86_64-pkg-config mingw-w64-x86_64-meson mingw-w64-x86_64-SDL2 mingw-w64-x86_64-ffmpeg mingw-w64-x86_64-libusb

# Trỏ tới thư mục source
cd /c/path/to/scrcpy-master

# Cấu hình và Build
meson setup build --buildtype=release --strip -Db_lto=true -Dcompile_server=false
ninja -C build
```
File `scrcpy.exe` sẽ được tạo ra trong `build/app/`. Copy file này vào thư mục `bin/` của Java project.

### 2. Build Dashboard (JavaFX)
Yêu cầu: JDK 17+ và Maven.
Chạy file `build.bat` có sẵn trong thư mục gốc. File này sẽ tự động:
1. Dọn dẹp tiến trình cũ.
2. Chạy `mvn clean javafx:jlink` để tạo Custom JRE.
3. Dùng `jpackage` để đóng gói thành file `.exe` độc lập (Portable).
4. Thành quả nằm ở thư mục `target/Release_EXE/`.

---

## 🧠 Kiến trúc kỹ thuật (Technical Details)

* **Multi-line Camera Parsing:** Sử dụng State Machine trong Java để đọc output của `dumpsys media.camera`, bóc tách chính xác các mảng byte chứa Physical Camera ID của Camera2 API.
* **Direct Frame Injection:** Can thiệp vào `sc_screen_frame_sink_push` (C), sử dụng `sws_scale` của FFmpeg để convert YUV420P sang BGRA/BGR24 và ghi trực tiếp vào Named Pipe của Windows hoặc NDI Sender.
* **Win32 API Manipulation:** Sử dụng `GetWindowLongPtr` và `SetWindowPos` để thay đổi cờ `WS_EX_APPWINDOW` thành `WS_EX_TOOLWINDOW` khi runtime, giúp cửa sổ bốc hơi khỏi Taskbar.

---

## 📜 Giấy phép (License)
* Phần lõi Scrcpy tuân thủ giấy phép **Apache License 2.0** của Genymobile.
* Phần Dashboard và các bản Mod (Aoi Edition) được phát triển độc lập.

---
*Made with ❤️ and lots of ☕*
