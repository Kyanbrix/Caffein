package com.github.kyanbrix.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads what Discord needs to show an Ogg Opus file as a voice message: its length and a waveform.
 * Nothing is decoded. The length comes from the last page's granule position. The waveform is built from
 * the size of each Opus packet, because Opus spends more bytes on loud speech and very few on silence, so
 * the bars follow the speech closely enough for a picture.
 */
public final class OggOpus {

    // Opus timestamps always count 48 kHz samples, whatever the input rate was
    private static final double SAMPLE_RATE = 48_000;
    // Discord samples voice message waveforms at about 10 Hz, up to 256 values
    private static final int SAMPLES_PER_SECOND = 10;
    private static final int MAX_WAVEFORM_LENGTH = 256;
    private static final int PAGE_HEADER_LENGTH = 27;

    /** @param waveform unsigned amplitudes (0–255), one per tenth of a second, at most 256 of them */
    public record VoiceClip(double durationSeconds, byte[] waveform) {}

    private OggOpus() {}

    /** @throws IllegalArgumentException if this isn't an Ogg Opus file with audio in it */
    public static VoiceClip analyze(byte[] ogg) {

        List<Integer> packetSizes = new ArrayList<>();
        long lastGranule = -1;
        int preSkip = -1;
        int packetSize = 0;
        int offset = 0;

        while (offset + PAGE_HEADER_LENGTH <= ogg.length) {
            if (ogg[offset] != 'O' || ogg[offset + 1] != 'g' || ogg[offset + 2] != 'g' || ogg[offset + 3] != 'S') {
                throw new IllegalArgumentException("not an Ogg page at byte " + offset);
            }

            long granule = readLong(ogg, offset + 6);
            int segmentCount = ogg[offset + 26] & 0xFF;
            int dataOffset = offset + PAGE_HEADER_LENGTH + segmentCount;
            if (dataOffset > ogg.length) {
                throw new IllegalArgumentException("truncated Ogg page at byte " + offset);
            }

            // The first page holds only the OpusHead packet, whose bytes 10–11 are the pre-skip
            if (preSkip < 0) {
                if (dataOffset + 12 > ogg.length || ogg[dataOffset] != 'O' || ogg[dataOffset + 4] != 'H') {
                    throw new IllegalArgumentException("missing OpusHead");
                }
                preSkip = (ogg[dataOffset + 10] & 0xFF) | (ogg[dataOffset + 11] & 0xFF) << 8;
            }

            // A packet is a run of 255-byte segments ended by a shorter one, and can carry over to the next page
            int dataLength = 0;
            for (int i = 0; i < segmentCount; i++) {
                int lacing = ogg[offset + PAGE_HEADER_LENGTH + i] & 0xFF;
                dataLength += lacing;
                packetSize += lacing;
                if (lacing < 255) {
                    packetSizes.add(packetSize);
                    packetSize = 0;
                }
            }

            // -1 marks a page where no packet ends
            if (granule != -1) {
                lastGranule = granule;
            }
            offset = dataOffset + dataLength;
        }

        // The first two packets are the OpusHead and OpusTags headers, not audio
        if (packetSizes.size() <= 2 || lastGranule <= preSkip) {
            throw new IllegalArgumentException("no audio packets");
        }
        List<Integer> audioPackets = packetSizes.subList(2, packetSizes.size());

        double durationSeconds = (lastGranule - preSkip) / SAMPLE_RATE;
        return new VoiceClip(durationSeconds, waveform(audioPackets, durationSeconds));
    }

    /** Averages the packet sizes into one bar per tenth of a second, scaled so the quietest is 0 and the loudest 255. */
    private static byte[] waveform(List<Integer> packetSizes, double durationSeconds) {

        int length = (int) Math.max(1, Math.min(MAX_WAVEFORM_LENGTH,
                Math.min(packetSizes.size(), Math.round(durationSeconds * SAMPLES_PER_SECOND))));

        double[] averages = new double[length];
        double min = Double.MAX_VALUE;
        double max = 0;
        for (int bar = 0; bar < length; bar++) {
            int from = (int) ((long) bar * packetSizes.size() / length);
            int to = (int) ((long) (bar + 1) * packetSizes.size() / length);
            long sum = 0;
            for (int i = from; i < to; i++) {
                sum += packetSizes.get(i);
            }
            averages[bar] = (double) sum / Math.max(1, to - from);
            min = Math.min(min, averages[bar]);
            max = Math.max(max, averages[bar]);
        }

        byte[] waveform = new byte[length];
        for (int bar = 0; bar < length; bar++) {
            double level = max > min ? (averages[bar] - min) / (max - min) : 0.5;
            waveform[bar] = (byte) Math.round(level * 255);
        }
        return waveform;
    }

    private static long readLong(byte[] bytes, int offset) {
        long value = 0;
        for (int i = 7; i >= 0; i--) {
            value = value << 8 | (bytes[offset + i] & 0xFF);
        }
        return value;
    }
}
