package com.phicomm.r1manager.server.model.dto;

import java.util.List;

public class FileDto {
    public static class FileItem {
        public String name;
        public String path;
        public boolean isDir;
        public long size;
        public long modified;
        public boolean canRead;
        public boolean canWrite;
    }

    public static class DirectoryInfo {
        public String path;
        public String parent;
        public boolean canRead;
        public boolean canWrite;
        public List<FileItem> files;
        public int count;
    }
}
