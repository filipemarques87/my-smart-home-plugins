package io.mysmarthome.platforms.ffmpegstream;

@FunctionalInterface
public interface FrameExtractorListener {
    void onFrame(byte[] frame);
}

