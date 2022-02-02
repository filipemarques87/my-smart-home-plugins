package io.mysmarthome.platforms.ffmpeg;

import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class RetryExecutor {
    @SneakyThrows
    @Builder(buildMethodName = "start")
    public RetryExecutor(@NonNull Supplier<Boolean> task, int maxRetries, int interval, TimeUnit intervalUnit) {
        if (intervalUnit == null) {
            intervalUnit = TimeUnit.SECONDS;
        }

        while (--maxRetries > 0) {
            if (task.get()) {
                return;
            }
            intervalUnit.sleep(interval);
        }
        throw new IllegalStateException("Maximum retries reached");
    }
}

