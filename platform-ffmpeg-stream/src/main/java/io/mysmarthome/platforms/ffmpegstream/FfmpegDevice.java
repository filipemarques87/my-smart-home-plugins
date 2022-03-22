package io.mysmarthome.platforms.ffmpegstream;

import io.mysmarthome.device.Device;
import io.mysmarthome.util.TypedValue;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class FfmpegDevice implements Device {

//    private static final String COMMAND = "" +
//            " ${executablePath} " +
//            " ${protocol_options} " +
//            " -i ${url} " +
//            " -nostdin " +
//            " -f singlejpeg " +
//            " - ";

    private final Device device;

    public Device getOriginalDevice() {
        return device;
    }

    @Override
    public String getDeviceId() {
        return device.getDeviceId();
    }

    @Override
    public TypedValue getCustomInfo(String key) {
        return device.getCustomInfo(key);
    }

    @SneakyThrows
    public String getCommand() {
        return getCustomInfo("command").asString();
    }
}
