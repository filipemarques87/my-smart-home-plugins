package io.mysmarthome.platforms.ffmpeg;

import io.mysmarthome.device.Device;
import io.mysmarthome.util.TypedValue;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Locale;

@RequiredArgsConstructor
public class FfmpegDevice implements Device {

    private static final String COMMAND = "" +
            " /snap/bin/ffmpeg -fflags nobuffer " +
            " ${protocol_options} " +
            " -i  ${url} " +
            " -vsync 0 " +
            " -copyts " +
            " -vcodec copy " +
            " -movflags frag_keyframe+empty_moov " +
            " -an " +
            " -hls_flags delete_segments+append_list " +
            " -f segment " +
            " -segment_list_flags live " +
            " -segment_time 2 " +
            " -segment_list_size 5 " +
            " -segment_format mpegts " +
            " -segment_list ${device_id}/index.m3u8 " +
            " -segment_list_type m3u8 " +
            //" -segment_list_entry_prefix '' " +
            "  ${device_id}/%3d.ts ";

    private static final String RTSP_OPTIONS = "-rtsp_transport tcp";

    private final Device device;

    private final String baseDir;

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

    public String getUrl() {
        return getCustomInfo("url").asString();
    }

    @SneakyThrows
    public String getCommand() {
        String url = getUrl();
        String protocol = new URI(url).getScheme();
        return getCustomInfo("command").asString(COMMAND)
                .replace("${url}", url)
                .replace("${protocol_options}", getProtocolOptions(protocol))
                .replaceAll("\\$\\{device_id}", Paths.get(baseDir, getDeviceId()).toString());
    }


    private String getProtocolOptions(String protocol) {
        if (protocol == null) {
            return "";
        }

        switch (protocol.toLowerCase(Locale.ROOT)) {
            case "rtsp":
                return RTSP_OPTIONS;
            default:
                return "";
        }
    }

}
