package io.mysmarthome.platforms.openweathermap;

import io.mysmarthome.device.Device;
import io.mysmarthome.util.TypedValue;
import okhttp3.Headers;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class OpenWeatherMapDevice implements Device {

    private final String fullUrl;
    private final Device device;

    public OpenWeatherMapDevice(Device device, String url, String appId) {
        this.device = device;
        fullUrl = buildFullUrl(url, appId);
    }

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
        return fullUrl;
    }

    public String getCity() {
        return device.getCustomInfo("city").asString();
    }

    public String getUnits() {
        return Optional.ofNullable(device.getCustomInfo("units").asString())
                .orElse("metric");
    }

    protected String buildFullUrl(String url, String appId) {
        return String.format("%s?q=%s&units=%s&appid=%s",
                url,
                getCity(),
                getUnits(),
                appId);
    }

    public Headers getHeaders() {
        Map<?, ?> rawHeadersMap = device.getCustomInfo("headers").as(Map.class, new HashMap<>());
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<?, ?> o : rawHeadersMap.entrySet()) {
            headers.put(Objects.toString(o.getKey()), Objects.toString(o.getValue()));
        }
        return Headers.of(headers);
    }
}
