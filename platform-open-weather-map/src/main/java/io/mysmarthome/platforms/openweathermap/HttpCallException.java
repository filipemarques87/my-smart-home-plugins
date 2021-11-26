package io.mysmarthome.platforms.openweathermap;

import lombok.Getter;

@Getter
public class HttpCallException extends RuntimeException {

    private int code;
    private String body;

    public HttpCallException() {
    }

    public HttpCallException(String message) {
        super(message);
    }

    public HttpCallException(String message, Throwable cause) {
        super(message, cause);
    }

    public HttpCallException(Throwable cause) {
        super(cause);
    }

    public HttpCallException(int code, String body) {
        this.code = code;
        this.body = body;
    }
}
