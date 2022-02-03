package io.mysmarthome.platforms.ffmpeg;

import io.mysmarthome.device.Device;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class Monitor {

    private static final long SCHEDULER_PERIOD = 1L; // in seconds

    private final int maxParallelStreams;
    private final int inactivityTimeout;
    private ScheduledFuture<?> scheduledFuture;
    private final Map<String, MonitoredProcess> activeStreams = new ConcurrentHashMap<>();
    private Consumer<Device> onStopListener = d -> {
    };

    public Monitor(int maxAllowedParallelStreams, int inactivityTimeout) {
        this.maxParallelStreams = maxAllowedParallelStreams;
        this.inactivityTimeout = inactivityTimeout;
    }

    public void start() {
        scheduledFuture = Executors
                .newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        monitoringProcesses(),
                        0L,
                        SCHEDULER_PERIOD,
                        TimeUnit.SECONDS);
    }

    private Runnable monitoringProcesses() {
        log.info("Running monitoring ...");
        return () -> {

            Iterator<MonitoredProcess> i = activeStreams.values().iterator();
            while (i.hasNext()) {
                MonitoredProcess p = i.next();
                if (isAliveProcess(p.getLastRequest())) {
                    continue;
                }

                log.info("Kill stream for {}", p.getDevice().getDeviceId());
                p.getProcess().destroy();
                this.onStopListener.accept(p.getDevice());

                i.remove();
            }
        };
    }

    private boolean isAliveProcess(Instant lastUpdate) {
        Instant now = Instant.now();
        return ChronoUnit.SECONDS.between(lastUpdate, now) < inactivityTimeout;
    }

    public void keepAlive(Device device) {
        activeStreams.get(device.getDeviceId()).setLastRequest(Instant.now());
    }

    public boolean streamAlreadyRunning(Device device) {
        return activeStreams.containsKey(device.getDeviceId());
    }

    public void addStream(Device device, Process process) {
        if (activeStreams.containsKey(device.getDeviceId())) {
            // already streaming, nothing to do
            return;
        }
        if (cannotStream()) {
            throw new IllegalMonitorStateException("Maximum stream allowed in parallel reached");
        }

        activeStreams.put(device.getDeviceId(), new MonitoredProcess(device, process));
    }

    public void stopStream(Device device) {
        if (!activeStreams.containsKey(device.getDeviceId())) {
            // already streaming, nothing to do
            return;
        }

        MonitoredProcess p = activeStreams.remove(device.getDeviceId());
        p.getProcess().destroy();
        this.onStopListener.accept(device);
    }

    public void setOnStopListener(Consumer<Device> onStopListener) {
        this.onStopListener = onStopListener;
    }

    public boolean cannotStream() {
        return activeStreams.size() >= maxParallelStreams;
    }

    public void stop() {
        if (isRunning()) {
            scheduledFuture.cancel(true);
            activeStreams.values().forEach(p -> p.getProcess().destroy());
        }
    }

    private boolean isRunning() {
        return !scheduledFuture.isCancelled() && !scheduledFuture.isDone();
    }

    @Data
    private static class MonitoredProcess {

        private final Device device;

        private final Process process;

        private Instant lastRequest;

        public MonitoredProcess(Device device, Process process) {
            this.device = device;
            this.process = process;
            this.lastRequest = Instant.now();
        }
    }
}
