package io.mysmarthome.platforms.ffmpegstream;

import io.mysmarthome.device.Device;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class FfmpegProcessManager {

    private final int maxParallelStreams;
    private final Map<String, MonitoredProcess> activeStreams = new ConcurrentHashMap<>();

    public FfmpegProcessManager(int maxAllowedParallelStreams) {
        this.maxParallelStreams = maxAllowedParallelStreams;
    }

    public boolean isStreaming(Device device) {
        return activeStreams.containsKey(device.getDeviceId());
    }

    public boolean canStream() {
        return activeStreams.size() < maxParallelStreams;
    }

    public void incrementClientsNumber(Device device) {
        if (activeStreams.containsKey(device.getDeviceId())) {
            activeStreams.get(device.getDeviceId()).addUser();
        }
    }

    @SneakyThrows
    public void start(FfmpegDevice device, FrameExtractor frameExtractor) {
        if (!canStream()) {
            throw new UnsupportedOperationException("Cannot start streaming for device [" + device.getDeviceId() + "]. " +
                    "Maximum number of concurrent streams reached.");
        }
        if (!SystemUtils.IS_OS_LINUX) {
            throw new UnsupportedOperationException("Only linux OS are supported");
        }

        activeStreams.put(device.getDeviceId(), new MonitoredProcess(device, frameExtractor));

        Thread frameExtractorThread = new Thread(frameExtractor);
        frameExtractorThread.start();
    }

    public void stop(Device device) {
        MonitoredProcess process = activeStreams.get(device.getDeviceId());
        process.removeUser();
        if (process.hasUsers()) {
            return;
        }

        process.getFrameExtractor().stop();
        activeStreams.remove(device.getDeviceId());
    }

    public void stopAll() {
        activeStreams.values()
                .forEach(m -> m.getFrameExtractor().stop());
    }

    @Data
    private static class MonitoredProcess {

        private final Device device;
        private final FrameExtractor frameExtractor;
        private int users = 1;

        public MonitoredProcess(Device device, FrameExtractor frameExtractor) {
            this.device = device;
            this.frameExtractor = frameExtractor;
        }

        public void addUser() {
            users++;
        }

        public void removeUser() {
            users--;
        }

        public boolean hasUsers() {
            return users > 0;
        }
    }
}
