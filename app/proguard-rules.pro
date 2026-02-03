# ProGuard rules for R1 Manager

# Keep NanoHTTPD classes
-keep class org.nanohttpd.** { *; }
-dontwarn org.nanohttpd.**

# Keep app classes
-keep class com.phicomm.r1manager.** { *; }
