package io.mysmarthome.platforms.http;

import io.mysmarthome.device.Device;
import io.mysmarthome.util.TypedValue;
import lombok.AllArgsConstructor;
import okhttp3.Headers;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@AllArgsConstructor
public class HttpDevice implements Device {

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

    public String getUrl() {
        return Optional.ofNullable(getCustomInfo("url").asString())
                .orElseThrow(HttpCallException::new);
    }

    public String getMethod() {
        return Optional.ofNullable(getCustomInfo("method").asString())
                .orElse("get");
    }

    public Headers getHeaders() {
        Map<?, ?> rawHeadersMap = device.getCustomInfo("headers").as(Map.class, new HashMap<>());
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<?, ?> o : rawHeadersMap.entrySet()) {
            headers.put(Objects.toString(o.getKey()), Objects.toString(o.getValue()));
        }
        System.out.println(">>>>>>>>>>>>>>>>>>>" + headers);
        return Headers.of(headers);
    }

    public String getPayload() {
        return getCustomInfo("payload").asString();
    }
}