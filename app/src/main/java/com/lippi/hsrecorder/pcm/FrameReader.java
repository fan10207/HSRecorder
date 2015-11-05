package com.lippi.hsrecorder.pcm;

/**
 * Created by lippi on 14-12-6.
 */
import java.util.Iterator;

public class FrameReader implements Iterable<DoubleFrame> {

    private final PcmAudioInputStream pcmAudioInputStream;
    private final int frameSize;
    private final int shiftAmount;

    public FrameReader(PcmAudioInputStream pais, int frameSize, int shiftAmount) {
        this.pcmAudioInputStream = pais;
        this.frameSize = frameSize;
        this.shiftAmount = shiftAmount;
    }

    public Iterator<DoubleFrame> iterator() {
        return new FrameIterator();
    }

    private class FrameIterator implements Iterator<DoubleFrame> {

        public boolean hasNext() {
            return false;
        }

        public DoubleFrame next() {
            return null;
        }

        public void remove() {

        }
    }

}
