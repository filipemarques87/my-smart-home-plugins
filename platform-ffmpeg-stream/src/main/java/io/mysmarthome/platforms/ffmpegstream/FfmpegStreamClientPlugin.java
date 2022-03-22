package io.mysmarthome.platforms.ffmpegstream;

import io.mysmarthome.configuration.ApplicationProperties;
import io.mysmarthome.device.Device;
import io.mysmarthome.platform.PlatformPlugin;
import io.mysmarthome.platform.message.DeviceHandler;
import io.mysmarthome.platform.message.OnReceive;
import io.mysmarthome.platform.message.ReceivedMessage;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
public class FfmpegStreamClientPlugin extends Plugin {

    public FfmpegStreamClientPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class HttpClient implements PlatformPlugin<FfmpegDevice> {

        private final Map<String, DeviceHandler> handlers = new HashMap<>();
        private String executablePath;
        private FfmpegProcessManager ffmpegProcessManager;

        @Override
        public void start(ApplicationProperties config) {
            executablePath = config.getString("ffmpeg.executablePath");
            int maxConcurrencyExecution = config.getInt("ffmpeg.maxParallelStreams", 1);

            ffmpegProcessManager = new FfmpegProcessManager(maxConcurrencyExecution);
        }

        @Override
        public void shutdown() {
            ffmpegProcessManager.stopAll();
        }

        @SneakyThrows
        @Override
        public void onRegisterDevice(FfmpegDevice device, final OnReceive callback) {
            log.info("Register device '{}' for {} plugin", device.getDeviceId(), getName());
            handlers.putIfAbsent(device.getDeviceId(), DeviceHandler.builder()
                    .device(device.getOriginalDevice())
                    .callback(callback)
                    .build());
        }

        @Override
        public FfmpegDevice getPlatformSpecificDevice(Device device) {
            return new FfmpegDevice(device);
        }

        @Override
        public String getName() {
            return "ffmpeg-stream";
        }

        @Override
        public void onStartStream(FfmpegDevice device, Consumer<Object> processPayload) {
            if (ffmpegProcessManager.isStreaming(device)) {
                ffmpegProcessManager.incrementClientsNumber(device);
                return;
            }

            String command = prepareCommand(device);
            FrameExtractor frameExtractor = new FrameExtractor(command);
            frameExtractor.setFrameExtractorListener(processPayload::accept);
            ffmpegProcessManager.start(device, frameExtractor);

            if (handlers.containsKey(device.getDeviceId())) {
                ReceivedMessage message = ReceivedMessage.createSimpleMessage("1");
                handlers.get(device.getDeviceId()).broadcastMessage(message);
            }
        }

        @Override
        public void onStopStream(FfmpegDevice device) {
            ffmpegProcessManager.stop(device);
            if (ffmpegProcessManager.isStreaming(device)) {
                return;
            }

            if (handlers.containsKey(device.getDeviceId())) {
                ReceivedMessage message = ReceivedMessage.createSimpleMessage("0");
                handlers.get(device.getDeviceId()).broadcastMessage(message);
            }
        }

        private String prepareCommand(FfmpegDevice device) {
            return executablePath + " " + device.getCommand();
        }
    }
}
