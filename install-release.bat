@echo off
REM Tat bat am thong bao loi cua Windows de giu CMD
setlocal EnableExtensions enabledelayedexpansion

title Bo cai dat APK RELEASE - FIX TU DONG DONG
mode con lines=30 cols=90

echo ========================================================
echo    Cong cu cai dat APK RELEASE - PHIEN BAN CHONG SAP CMD
echo ========================================================
echo.

REM --- Cau hinh thong tin mac dinh ---
set "R1_IP=192.168.1.20"
set "APK_PATH=app\build\outputs\apk\release\app-release.apk"
set "APK_REMOTE_NAME=r1manager_release.apk"


REM --- Build APK ---
echo [INFO] Dang build file APK...
call gradlew assembleRelease
if errorlevel 1 (
    echo [ERROR] Build file APK that bai.
    goto :HALT
)

REM Kiem tra file APK tai may tinh
if not exist "%APK_PATH%" (
    echo [ERROR] Khong tim thay file build tai: %APK_PATH%
    goto :HALT
)

REM Ket noi thiet bi
echo [INFO] Dang ket noi toi %R1_IP%...
adb disconnect >nul 2>&1
adb connect %R1_IP%:5555
timeout /t 2 /nobreak >nul

REM Mo khoa quyen cai dat
echo [INFO] Dang mo khoa quyen cai dat nguon la...
adb -s %R1_IP%:5555 shell settings put secure install_non_market_apps 1

REM Dung app cu truoc khi cai
echo [INFO] Dang dung ung dung cu...
adb -s %R1_IP%:5555 shell am force-stop com.phicomm.r1manager

REM Tai file len loa (Push)
echo [INFO] Dang tai file vao bo nho tam...
adb -s %R1_IP%:5555 push "%APK_PATH%" "/data/local/tmp/%APK_REMOTE_NAME%"
if errorlevel 1 (
    echo [ERROR] Tai file len that bai.
    goto :HALT
)

REM Thuc thi lenh cai dat
echo [INFO] Dang thuc thi lenh cai dat... (Vui long doi)
echo ---------------------------------------------------
adb -s %R1_IP%:5555 shell /system/bin/pm install -r -t "/data/local/tmp/%APK_REMOTE_NAME%"
echo ---------------------------------------------------

REM Khoi chay ung dung sau khi cai
echo [INFO] Dang khoi chay ung dung...
adb -s %R1_IP%:5555 shell am start -n com.phicomm.r1manager/.MainActivity

REM Kiem tra trang thai sau khi chay lenh adb
echo [INFO] Lenh adb da thuc thi xong. 
echo Dang don dep file tam...
adb -s %R1_IP%:5555 shell rm "/data/local/tmp/%APK_REMOTE_NAME%" >nul 2>&1

echo.
echo === KET THUC QUY TRINH ===
goto :HALT

:HALT
exit
