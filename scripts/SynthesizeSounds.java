/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 FartMC contributors.
 * Generated audio is dedicated under CC0-1.0; no third-party samples are used.
 */
import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class SynthesizeSounds {
    private static final int RATE = 44100;
    private record Voice(String name, double seconds, double hz, double noise, double flutter) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: java SynthesizeSounds.java <output-dir>");
        Path output = Files.createDirectories(Path.of(args[0]));
        Voice[] voices = {
            new Voice("classic", .72, 74, .18, 27),
            new Voice("whisper", 1.15, 48, .90, 12),
            new Voice("bubbles", 1.6, 115, .32, 8),
            new Voice("trumpet", 1.25, 105, .08, 34),
            new Voice("rattle", 1.45, 65, .30, 19),
            new Voice("thunder", 2.05, 38, .45, 23),
            new Voice("squeak", .52, 210, .10, 42)
        };
        for (Voice voice : voices) render(voice, output.resolve(voice.name + ".wav"));
    }

    private static void render(Voice v, Path path) throws Exception {
        Random random = new Random(0x46415254L + v.name.hashCode());
        int count = (int) (v.seconds * RATE);
        double[] samples = new double[count];
        double phase = 0, filteredNoise = 0, peak = 0;
        for (int i = 0; i < count; i++) {
            double t = i / (double) RATE;
            double progress = t / v.seconds;
            double flutter = .55 + .45 * Math.sin(2 * Math.PI * v.flutter * t);
            double frequency = v.hz * (1 - .48 * progress) * (1 + .13 * Math.sin(2 * Math.PI * 7 * t));
            if (v.name.equals("squeak")) frequency *= 1 + .8 * Math.sin(Math.PI * progress);
            if (v.name.equals("bubbles")) frequency *= 1 + .65 * Math.sin(2 * Math.PI * 8 * t);
            phase += 2 * Math.PI * frequency / RATE;
            double buzz = Math.tanh(2.5 * (Math.sin(phase) + .35 * Math.sin(2 * phase)));
            filteredNoise = .78 * filteredNoise + .22 * (random.nextDouble() * 2 - 1);
            double envelope = Math.min(1, t / .02) * Math.pow(Math.max(0, 1 - progress), .7);
            if (v.name.equals("rattle") || v.name.equals("bubbles")) {
                envelope *= Math.pow(Math.max(0, Math.sin(2 * Math.PI * v.flutter * t)), 2);
            }
            double sample = envelope * ((1 - v.noise) * buzz * flutter + v.noise * filteredNoise * 3);
            samples[i] = sample;
            peak = Math.max(peak, Math.abs(sample));
        }
        byte[] pcm = new byte[count * 2];
        double gain = .65 / Math.max(peak, .001);
        if (v.name.equals("whisper")) gain *= .55;
        for (int i = 0; i < count; i++) {
            short value = (short)Math.round(Math.clamp(samples[i] * gain, -1, 1) * 32767);
            pcm[i * 2] = (byte)value;
            pcm[i * 2 + 1] = (byte)(value >> 8);
        }
        AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
        try (AudioInputStream audio = new AudioInputStream(new ByteArrayInputStream(pcm), format, count)) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, path.toFile());
        }
    }
}
