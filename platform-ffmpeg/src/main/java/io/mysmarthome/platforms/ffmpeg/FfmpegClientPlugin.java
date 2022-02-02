package io.mysmarthome.platforms.ffmpeg;

import io.mysmarthome.configuration.ApplicationProperties;
import io.mysmarthome.device.Device;
import io.mysmarthome.platform.DownloadDetails;
import io.mysmarthome.platform.PlatformPlugin;
import io.mysmarthome.platform.message.DeviceHandler;
import io.mysmarthome.platform.message.OnReceive;
import io.mysmarthome.platform.message.ReceivedMessage;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// TODO adicionar o monitor
@Slf4j
public class FfmpegClientPlugin extends Plugin {

    public FfmpegClientPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }


    @Extension
    public static class HttpClient implements PlatformPlugin<FfmpegDevice> {

        private final Map<String, DeviceHandler> handlers = new HashMap<>();
        private String dataFolder;
        private int maxConcurrencyExecution;
        private int inactivityTimeout;

        private final Map<String, Object> locks = new ConcurrentHashMap<>();
        private final Map<String, Process> processes = new HashMap<>();

        @Override
        public void start(ApplicationProperties config) {
            dataFolder = config.getString("ffmpeg.dataFolder");
            maxConcurrencyExecution = config.getInt("ffmpeg.maxConcurrencyExecution", 1);
            inactivityTimeout = config.getInt("ffmpeg.inactivityTimeout", 3);
        }

        @SneakyThrows
        @Override
        public void onRegisterDevice(FfmpegDevice device, final OnReceive callback) {
            log.info("Register device '{}' for {} plugin", device.getDeviceId(), getName());
            handlers.putIfAbsent(device.getDeviceId(), DeviceHandler.builder()
                    .device(device.getOriginalDevice())
                    .callback(callback)
                    .build());

            Files.createDirectories(Paths.get(dataFolder, device.getDeviceId()));
        }

        @Override
        public FfmpegDevice getPlatformSpecificDevice(Device device) {
            return new FfmpegDevice(device, dataFolder);
        }

        @SneakyThrows
        @Override
        public CompletableFuture<Optional<ReceivedMessage>> onSend(FfmpegDevice device, Object payload) {
            waitForLock(device);

            lockDevice(device);
            try {
                Map<String, Object> request = (Map<String, Object>) payload;


                if ("1".equals(request.get("val"))) {
                    cleanDirectory(device);
                    startStreaming(device);
                } else if ("0".equals(request.get("val"))) {
                    processes.get(device.getDeviceId()).destroy();
                    cleanDirectory(device);
                }

                return CompletableFuture.supplyAsync(() -> {


                    ReceivedMessage message = ReceivedMessage.builder()
                            .message(request)
                            .build();

                    if (handlers.containsKey(device.getDeviceId())) {
                        handlers.get(device.getDeviceId()).broadcastMessage(message);
                    }

                    return Optional.ofNullable(message);
                });

            } finally {
                releaseLock(device);
            }


        }

        @SneakyThrows
        public DownloadDetails onDownload(FfmpegDevice device, String path) {
            File original = Paths.get(dataFolder, device.getDeviceId(), path).toFile();
            File copied = Paths.get(dataFolder, device.getDeviceId(), UUID.nameUUIDFromBytes(path.getBytes()).toString()).toFile();
            FileUtils.copyFile(original, copied);

            try {
                return DownloadDetails.builder()
                        .file(copied)
                        .fileSstream(new FileInputStream(original.getAbsoluteFile()))
                        .build();
            } catch (FileNotFoundException e) {
                log.error("File '{}' does not exists", original.getAbsoluteFile(), e);
                return DownloadDetails.builder()
                        .file(original)
                        .build();
            }
        }

        @Override
        public String getName() {
            return "ffmpeg";
        }

        private void lockDevice(Device device) {
            locks.putIfAbsent(device.getDeviceId(), new Object());
        }

        private  void releaseLock(Device device) {
            locks.remove(device.getDeviceId());
//            if (hasLock(device)) {
//                locks.get(device.getDeviceId()).notify();
//            }
        }

        private synchronized boolean hasLock(Device device) {
            return locks.containsKey(device.getDeviceId());
        }

        @SneakyThrows
        private synchronized void waitForLock(Device device) {
//            if (hasLock(device)) {
//                locks.get(device.getDeviceId()).wait();
//            }
        }


        @SneakyThrows
        private void cleanDirectory(Device device) {
            log.info("Starting cleaning folder for device {}", device.getDeviceId());
            Path folder = Paths.get(dataFolder, device.getDeviceId());
            FileUtils.cleanDirectory(folder.toFile());
            RetryExecutor.builder()
                    .task(() -> Objects.requireNonNull(folder.toFile().listFiles()).length == 0)
                    .maxRetries(5)
                    .interval(1)
                    .intervalUnit(TimeUnit.SECONDS)
                    .start();
            log.info("Folder cleaned for device {}", device.getDeviceId());
        }

        @SneakyThrows
        private void startStreaming(FfmpegDevice device) {
//            log.info("Starting stream for device {}", device.getDeviceId());
            String command = device.getCommand();
//            log.info("Execute ffmpeg command: {}", command);

            ProcessBuilder processBuilder = new ProcessBuilder();
//            processBuilder.command("/snap/bin/ffmpeg", command);
            processBuilder.command("/bin/bash", "-l", "-c", command);
            processBuilder.inheritIO();
            Process process = processBuilder.start();

            processes.put(device.getDeviceId(),process);


            Path folder = Paths.get(dataFolder, device.getDeviceId());
            RetryExecutor.builder()
                    .task(() -> Arrays.stream(Objects.requireNonNull(folder.toFile().listFiles()))
                            .filter(f -> f.getName().endsWith(".ts") || f.getName().endsWith(".m3u8"))// TODO talvez alterar isso para verificar mesmo se existe os dois ficheiros
                            .count() >= 2)
                    .maxRetries(10)
                    .interval(2)
                    .intervalUnit(TimeUnit.SECONDS)
                    .start();
//            log.info("Stream started for device {}", device.getDeviceId());
        }
    }
}



