package io.mysmarthome.platforms.ffmpegstream;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class FrameExtractor implements Runnable {

    private static final int BUFFER_SIZE = 4_000;
    private static final int FRAME_SIZE = 512_000;

    private final String command;
    private final AtomicBoolean isAlive = new AtomicBoolean(false);

    private Process process;
    private FrameExtractorListener listener;

    public void setFrameExtractorListener(FrameExtractorListener listener) {
        this.listener = listener;
    }

//    private String[] buildCommand() {
//        // ffmpeg -re -f mjpeg -i http://sdip.dynip.sapo.pt:8081/stream -nostdin -f singlejpeg -
//        return new String[]{
//                "ffmpeg",
//                "-i", url, // input
//                "-nostdin", // no ask to user input
//                "-f", "singlejpeg", // convert to jpg
//                "-"}; // redirect output to stdout
//    }

    private synchronized boolean isAlive(Process p) {
        try {
            p.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return isAlive.get();
        }
    }

    @SneakyThrows
    @Override
    public void run() {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("/bin/bash", "-l", "-c", command);
        processBuilder.redirectErrorStream(true); // so we can ignore the error stream
        process = processBuilder.start();
        isAlive.set(true);
        InputStream out = process.getInputStream();

        int bufferLen = 0;
        int idx = 0;
        byte[] bufferImg = new byte[FRAME_SIZE];
        byte[] buffer = new byte[BUFFER_SIZE];
        boolean processingFrame = false;

        while (isAlive(process)) {
            int no = out.available();
            if (no > 0) {
                int n = out.read(buffer, 0, Math.min(no, buffer.length));

                // start frame
                if (!processingFrame) {
                    if (n > 2 && buffer[0] == (byte) 0xff && buffer[1] == (byte) 0xD8 && buffer[2] == (byte) 0xFF) {
                        bufferLen = n;
                        idx = n;
                        System.arraycopy(buffer, 0, bufferImg, 0, n);
                        processingFrame = true;
                    }
                    continue;
                }

                // end frame
                if (n > 1 && buffer[n - 2] == (byte) 0xff && buffer[n - 1] == (byte) 0xD9) {
                    bufferLen += n - 1;
                    System.arraycopy(buffer, 0, bufferImg, idx, n - 1);

                    if (listener != null) {
                        byte[] frame = new byte[bufferLen];
                        System.arraycopy(bufferImg, 0, frame, 0, bufferLen);
                        listener.onFrame(frame);
                    }

                    processingFrame = false;
                    continue;
                }

                // continue frame
                bufferLen += n;
                System.arraycopy(buffer, 0, bufferImg, idx, n);
                idx = bufferLen;
            }
        }
    }

    public synchronized void stop() {
        process.destroy();
        isAlive.set(false);
    }
}