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
import java.util.concurrent.TimeUnit;

@Slf4j
public class FfmpegClientPlugin extends Plugin {

    public FfmpegClientPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }


    @Extension
    public static class HttpClient implements PlatformPlugin<FfmpegDevice> {

        private final Map<String, DeviceHandler> handlers = new HashMap<>();
        private String dataFolder;
        private Monitor monitor;

        @Override
        public void start(ApplicationProperties config) {
            dataFolder = config.getString("ffmpeg.dataFolder");
            int maxConcurrencyExecution = config.getInt("ffmpeg.maxParallelStreams", 1);
            int inactivityTimeout = config.getInt("ffmpeg.inactivityTimeout", 8);

            monitor = new Monitor(maxConcurrencyExecution, inactivityTimeout);
            monitor.setOnStopListener(device -> {
                // just notify that the stream is stopped
                Map<String, Object> result = new HashMap<>();
                result.put("val", "0");

                ReceivedMessage message = ReceivedMessage.builder()
                        .message(result)
                        .build();

                if (handlers.containsKey(device.getDeviceId())) {
                    handlers.get(device.getDeviceId()).broadcastMessage(message);
                }
            });
            monitor.start();
        }

        @Override
        public void shutdown() {
            monitor.stop();
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
            if (monitor.cannotStream()) {
                throw new IllegalMonitorStateException("Cannot start streaming");
            }

            Map<String, Object> request = (Map<String, Object>) payload;
            if (isToStartStreaming(request)) {
                startStreaming(device);
            } else if (isToStopStreaming(request)) {
                stopStreaming(device);

            } else {
                throw new UnsupportedOperationException();
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
        }

        private boolean isToStartStreaming(Map<String, Object> request) {
            return "1".equals(request.get("val"));
        }

        private boolean isToStopStreaming(Map<String, Object> request) {
            return "0".equals(request.get("val"));
        }

        @SneakyThrows
        public DownloadDetails onDownload(FfmpegDevice device, String path) {
            monitor.keepAlive(device);

            File original = Paths.get(dataFolder, device.getDeviceId(), path).toFile();
            try {
                return DownloadDetails.builder()
                        .file(original)
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

        @SneakyThrows
        private void stopStreaming(Device device) {
            monitor.stopStream(device);
            cleanDirectory(device);
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
            if (monitor.streamAlreadyRunning(device)) {
                return;
            }

            // FIXME quando ha dois pedidos ao mesmo tempo, o segundo deve bloqueat ate que o primeiro seja processado
            cleanDirectory(device);
            String command = device.getCommand();
            log.info("Executing command : {}", command);

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("/bin/bash", "-l", "-c", command);
            Process process = processBuilder.start();

            monitor.addStream(device, process);

            // wait until have some files in the directory
            Path folder = Paths.get(dataFolder, device.getDeviceId());
            RetryExecutor.builder()
                    .task(() -> Arrays.stream(Objects.requireNonNull(folder.toFile().listFiles()))
                            .filter(f -> f.getName().endsWith(".ts") || f.getName().endsWith(".m3u8"))// TODO talvez alterar isso para verificar mesmo se existe os dois ficheiros
                            .count() >= 2)
                    .maxRetries(10)
                    .interval(2)
                    .intervalUnit(TimeUnit.SECONDS)
                    .start();
            log.info("Stream started for device {}", device.getDeviceId());
        }
    }
}
