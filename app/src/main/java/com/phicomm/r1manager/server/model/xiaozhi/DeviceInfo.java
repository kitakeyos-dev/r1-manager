package com.phicomm.r1manager.server.model.xiaozhi;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class DeviceInfo {
    public int version;
    @SerializedName("flash_size")
    public int flashSize;
    @SerializedName("psram_size")
    public int psramSize;
    @SerializedName("minimum_free_heap_size")
    public int minimumFreeHeapSize;
    @SerializedName("mac_address")
    public String macAddress;
    public String uuid;
    @SerializedName("chip_model_name")
    public String chipModelName;
    @SerializedName("chip_info")
    public ChipInfo chipInfo;
    public Application application;
    @SerializedName("partition_table")
    public List<Partition> partitionTable;
    public OTA ota;
    public Board board;

    public static class ChipInfo {
        public int model;
        public int cores;
        public int revision;
        public int features;
    }

    public static class Application {
        public String name;
        public String version;
        @SerializedName("compile_time")
        public String compileTime;
        @SerializedName("idf_version")
        public String idfVersion;
        @SerializedName("elf_sha256")
        public String elfSha256;
    }

    public static class Partition {
        public String label;
        public int type;
        public int subtype;
        public int address;
        public int size;

        public Partition(String label, int type, int subtype, int address, int size) {
            this.label = label;
            this.type = type;
            this.subtype = subtype;
            this.address = address;
            this.size = size;
        }
    }

    public static class OTA {
        public String label;

        public OTA(String label) {
            this.label = label;
        }
    }

    public static class Board {
        public String name;
        public String revision;
        public List<String> features;
        public String manufacturer;
        @SerializedName("serial_number")
        public String serialNumber;
    }

    public static DeviceInfo generate(String macAddress, String uuid) {
        Random random = new Random();
        DeviceInfo info = new DeviceInfo();
        info.version = 2;
        info.flashSize = 8388608;
        info.psramSize = 4194304;
        info.minimumFreeHeapSize = 250000;
        info.macAddress = macAddress;
        info.uuid = uuid;
        info.chipModelName = "esp32s3";

        info.chipInfo = new ChipInfo();
        info.chipInfo.model = 3;
        info.chipInfo.cores = 2;
        info.chipInfo.revision = 1;
        info.chipInfo.features = 5;

        info.application = new Application();
        info.application.name = "sensor-hub";
        info.application.version = "1.3.0";
        info.application.compileTime = "2025-02-28T12:34:56Z";
        info.application.idfVersion = "5.1-beta";
        info.application.elfSha256 = "dummy_sha256";

        info.partitionTable = new ArrayList<>();
        info.partitionTable.add(new Partition("app", 1, 2, 65536, 2097152));
        info.partitionTable.add(new Partition("nvs", 1, 1, 32768, 65536));

        info.ota = new OTA("ota_1");

        info.board = new Board();
        info.board.name = "ESP32S3-DevKitM-1";
        info.board.revision = "v1.2";
        info.board.features = Arrays.asList("WiFi", "Bluetooth");
        info.board.manufacturer = "Espressif";
        info.board.serialNumber = "ESP32S3-" + random.nextInt(9999);

        return info;
    }
}
