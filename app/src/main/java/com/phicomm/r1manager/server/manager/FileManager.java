package com.phicomm.r1manager.server.manager;

import android.content.Context;
import android.os.Environment;
import com.phicomm.r1manager.util.AppLog;

import com.phicomm.r1manager.server.model.dto.FileDto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * FileManager - File management operations
 */
public class FileManager {

    private static final String TAG = "FileManager";
    private Context context;

    public FileManager(Context context) {
        this.context = context;
    }

    /**
     * List directory contents
     */
    public FileDto.DirectoryInfo listDirectory(String path) throws Exception {
        File dir = new File(path);

        if (!dir.exists()) {
            throw new Exception("Directory not found: " + path);
        }

        if (!dir.isDirectory()) {
            throw new Exception("Not a directory: " + path);
        }

        FileDto.DirectoryInfo result = new FileDto.DirectoryInfo();
        result.path = dir.getAbsolutePath();
        result.parent = dir.getParent();
        result.canRead = dir.canRead();
        result.canWrite = dir.canWrite();
        result.files = new ArrayList<FileDto.FileItem>();

        File[] fileList = dir.listFiles();

        if (fileList != null) {
            // Sorting removed for performance. Client-side sorting implemented.
            // Arrays.sort(fileList, ...);

            for (File file : fileList) {
                FileDto.FileItem fileObj = new FileDto.FileItem();
                fileObj.name = file.getName();
                fileObj.path = file.getAbsolutePath();
                fileObj.isDir = file.isDirectory();
                fileObj.size = file.isDirectory() ? 0 : file.length();
                fileObj.modified = file.lastModified();
                fileObj.canRead = file.canRead();
                fileObj.canWrite = file.canWrite();
                result.files.add(fileObj);
            }
        }

        result.count = result.files.size();
        return result;
    }

    /**
     * Get file info
     */
    public FileDto.FileItem getFileInfo(String path) throws Exception {
        File file = new File(path);

        if (!file.exists()) {
            throw new Exception("File not found: " + path);
        }

        FileDto.FileItem info = new FileDto.FileItem();
        info.name = file.getName();
        info.path = file.getAbsolutePath();
        info.isDir = file.isDirectory();
        info.size = file.length();
        info.modified = file.lastModified();
        info.canRead = file.canRead();
        info.canWrite = file.canWrite();

        return info;
    }

    /**
     * Read file as InputStream
     */
    public FileInputStream readFile(String path) throws Exception {
        File file = new File(path);

        if (!file.exists()) {
            throw new Exception("File not found: " + path);
        }

        if (file.isDirectory()) {
            throw new Exception("Cannot read directory as file: " + path);
        }

        return new FileInputStream(file);
    }

    /**
     * Write file from InputStream
     */
    public void writeFile(String path, InputStream input) throws Exception {
        File file = new File(path);

        // Create parent directories if needed
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        FileOutputStream output = new FileOutputStream(file);
        byte[] buffer = new byte[8192];
        int bytesRead;

        try {
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        } finally {
            output.close();
        }
        AppLog.i(TAG, "File written: " + path + " (" + file.length() + " bytes)");
    }

    public String saveUploadedFile(String tempFilePath, String destPath, String filename) throws Exception {
        if (tempFilePath == null)
            throw new Exception("No file uploaded");
        if (filename == null || filename.isEmpty())
            filename = "uploaded_file";

        String cleanDestPath = (destPath == null || destPath.isEmpty()) ? getDefaultPath() : destPath;
        String fullPath = cleanDestPath + "/" + filename;

        FileInputStream fis = new FileInputStream(new File(tempFilePath));
        try {
            writeFile(fullPath, fis);
        } finally {
            fis.close();
        }
        return fullPath;
    }

    /**
     * Create directory
     */
    public boolean createDirectory(String path) throws Exception {
        File dir = new File(path);

        if (dir.exists()) {
            throw new Exception("Already exists: " + path);
        }

        boolean created = dir.mkdirs();
        AppLog.i(TAG, "Directory created: " + path + " = " + created);
        return created;
    }

    /**
     * Delete file or directory
     */
    public boolean delete(String path) throws Exception {
        File file = new File(path);

        if (!file.exists()) {
            throw new Exception("Not found: " + path);
        }

        boolean deleted;
        if (file.isDirectory()) {
            deleted = deleteRecursive(file);
        } else {
            deleted = file.delete();
        }

        AppLog.i(TAG, "Deleted: " + path + " = " + deleted);
        return deleted;
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    /**
     * Rename file or directory
     */
    public boolean rename(String oldPath, String newName) throws Exception {
        File file = new File(oldPath);

        if (!file.exists()) {
            throw new Exception("Not found: " + oldPath);
        }

        File newFile = new File(file.getParent(), newName);

        if (newFile.exists()) {
            throw new Exception("Already exists: " + newFile.getAbsolutePath());
        }

        boolean renamed = file.renameTo(newFile);
        AppLog.i(TAG, "Renamed: " + oldPath + " -> " + newName + " = " + renamed);
        return renamed;
    }

    /**
     * Get default storage path
     */
    public String getDefaultPath() {
        File sdcard = Environment.getExternalStorageDirectory();
        return sdcard.getAbsolutePath();
    }

    /**
     * Format file size for display
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
