package io.mysmarthome.platforms.http;


import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

@Slf4j
public class HttpClientPlugin extends Plugin {

    public HttpClientPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class HttpClient extends BaseHttpClient<HttpDevice> {

        public HttpClient() {
            super(HttpDevice.class);
        }
    }
}



