@echo off
setlocal enabledelayedexpansion

echo [1/4] Giet sach tien trinh dang ket ngam de khong bi khoa file...
taskkill /F /IM CrystalScrcpy.exe /T >nul 2>&1
taskkill /F /IM java.exe /T >nul 2>&1
taskkill /F /IM javaw.exe /T >nul 2>&1

echo [2/4] Xoa thu muc Release_EXE cu...
if exist "target\Release_EXE" rmdir /s /q "target\Release_EXE"

echo [3/4] Build Jlink de tao Custom JRE...
call mvn clean javafx:jlink

if %ERRORLEVEL% NEQ 0 (
    echo [ERR] Build jlink loi. Kiem tra lai pom.xml!
    pause
    exit /b
)

echo [4/4] Dong goi thanh file .exe bang jpackage...
jpackage --type app-image ^
  --name "CrystalScrcpy" ^
  --module uet.vnu.edu.scrcpy/uet.vnu.edu.scrcpy.Main ^
  --runtime-image target/CrystalScrcpy_Portable ^
  --dest target/Release_EXE ^
  --icon icon.ico

if %ERRORLEVEL% NEQ 0 (
    echo [ERR] Dong goi jpackage loi! Kiem tra lai xem file icon.ico co o dung cho chua.
    pause
    exit /b
)

echo.
echo ========================================================
echo THANH CONG MANH ME!
echo App cua may dang nam o: target\Release_EXE\CrystalScrcpy
echo ========================================================
pause