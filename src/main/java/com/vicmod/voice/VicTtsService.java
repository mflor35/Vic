package com.vicmod.voice;

import com.google.gson.JsonObject;
import com.vicmod.VicMod;
import com.vicmod.config.VicConfig;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class VicTtsService {
    private static final ExecutorService TTS_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Vic-TTS-Thread");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private static final AtomicReference<Process> ACTIVE_PROCESS = new AtomicReference<>();
    private static volatile boolean cancelRequested = false;

    public static void speak(String rawText, int stage) {
        VicConfig cfg = VicConfig.get();
        String mode = cfg.getEffectiveTtsMode();
        if ("OFF".equals(mode) || rawText == null || rawText.isBlank()) {
            return;
        }

        String cleanText = sanitizeForSpeech(rawText);
        if (cleanText.isEmpty()) {
            return;
        }

        stopSpeaking();
        cancelRequested = false;

        TTS_EXECUTOR.submit(() -> {
            try {
                switch (mode) {
                    case "SYSTEM" -> {
                        if (!trySpeakSystem(cleanText, stage)) {
                            speakBuiltInSynth(cleanText, stage, cfg.ttsVolume);
                        }
                    }
                    case "OPENAI_TTS" -> {
                        if (!trySpeakOpenAiTts(cleanText, stage, cfg)) {
                            speakBuiltInSynth(cleanText, stage, cfg.ttsVolume);
                        }
                    }
                    case "SYNTH" -> speakBuiltInSynth(cleanText, stage, cfg.ttsVolume);
                    default -> {
                        // AUTO: Try system TTS if installed, otherwise use built-in formant synth
                        if (!trySpeakSystem(cleanText, stage)) {
                            speakBuiltInSynth(cleanText, stage, cfg.ttsVolume);
                        }
                    }
                }
            } catch (Exception e) {
                VicMod.LOGGER.warn("Vic TTS failed to play speech", e);
            }
        });
    }

    public static void stopSpeaking() {
        cancelRequested = true;
        Process proc = ACTIVE_PROCESS.getAndSet(null);
        if (proc != null && proc.isAlive()) {
            proc.destroyForcibly();
        }
    }

    private static String sanitizeForSpeech(String text) {
        return text
                .replaceAll("\\[ACTION:[^\\]]*\\]", "")
                .replaceAll("[*_~`<>]", "")
                .trim();
    }

    private static boolean trySpeakSystem(String text, int stage) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if (os.contains("linux")) {
            if (isBinaryAvailable("espeak-ng")) {
                int pitch = switch (stage) {
                    case 0 -> 78;
                    case 1 -> 62;
                    case 2 -> 38;
                    default -> 14;
                };
                int speed = switch (stage) {
                    case 0 -> 175;
                    case 1 -> 160;
                    case 2 -> 140;
                    default -> 110;
                };
                return runProcess(new ProcessBuilder("espeak-ng", "-p", String.valueOf(pitch), "-s", String.valueOf(speed), text));
            }
            if (isBinaryAvailable("espeak")) {
                int pitch = switch (stage) {
                    case 0 -> 78;
                    case 1 -> 62;
                    case 2 -> 38;
                    default -> 14;
                };
                int speed = switch (stage) {
                    case 0 -> 175;
                    case 1 -> 160;
                    case 2 -> 140;
                    default -> 110;
                };
                return runProcess(new ProcessBuilder("espeak", "-p", String.valueOf(pitch), "-s", String.valueOf(speed), text));
            }
            if (isBinaryAvailable("spd-say")) {
                int pitch = switch (stage) {
                    case 0 -> 45;
                    case 1 -> 15;
                    case 2 -> -25;
                    default -> -75;
                };
                int rate = switch (stage) {
                    case 0 -> 15;
                    case 1 -> 0;
                    case 2 -> -15;
                    default -> -40;
                };
                return runProcess(new ProcessBuilder("spd-say", "-w", "-p", String.valueOf(pitch), "-r", String.valueOf(rate), text));
            }
            if (isBinaryAvailable("flite")) {
                return runProcess(new ProcessBuilder("flite", "-t", text));
            }
        } else if (os.contains("mac")) {
            if (isBinaryAvailable("say")) {
                int rate = switch (stage) {
                    case 0 -> 200;
                    case 1 -> 180;
                    case 2 -> 150;
                    default -> 115;
                };
                return runProcess(new ProcessBuilder("say", "-r", String.valueOf(rate), text));
            }
        } else if (os.contains("win")) {
            int rate = switch (stage) {
                case 0 -> 2;
                case 1 -> 0;
                case 2 -> -2;
                default -> -5;
            };
            String escaped = text.replace("'", "''").replace("\"", "");
            String psCmd = "Add-Type -AssemblyName System.Speech; " +
                    "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                    "$s.Rate = " + rate + "; " +
                    "$s.Speak('" + escaped + "');";
            return runProcess(new ProcessBuilder("powershell", "-NoProfile", "-Command", psCmd));
        }

        return false;
    }

    private static boolean trySpeakOpenAiTts(String text, int stage, VicConfig cfg) {
        String endpoint = (cfg.ttsEndpoint == null || cfg.ttsEndpoint.isBlank())
            ? "https://api.openai.com/v1/audio/speech"
            : cfg.ttsEndpoint.trim();
        String voice = (cfg.ttsVoice == null || cfg.ttsVoice.isBlank()) ? "alloy" : cfg.ttsVoice.trim();
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("model", "tts-1");
            payload.addProperty("input", text);
            payload.addProperty("voice", voice);
            payload.addProperty("response_format", "wav");
            double speed = switch (stage) {
                case 0 -> 1.12;
                case 1 -> 1.0;
                case 2 -> 0.90;
                default -> 0.78;
            };
            payload.addProperty("speed", speed);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));

            String key = cfg.apiKey == null ? "" : cfg.apiKey.trim();
            if (!key.isEmpty()) {
                reqBuilder.header("Authorization", "Bearer " + key);
            }

            HttpResponse<byte[]> response = HTTP_CLIENT.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                VicMod.LOGGER.warn("OpenAI-compatible TTS returned HTTP {} (voice={}); falling back to built-in synth",
                        response.statusCode(), voice);
                return false;
            }
            if (response.body() == null || response.body().length <= 100) {
                VicMod.LOGGER.warn("OpenAI-compatible TTS returned an empty audio payload (voice={}); falling back to built-in synth", voice);
                return false;
            }
            Path tmpAudio = Files.createTempFile("vic_tts_api_", ".wav");
            try {
                Files.write(tmpAudio, response.body());
                return playAudioFile(tmpAudio, response.body());
            } finally {
                Files.deleteIfExists(tmpAudio);
            }
        } catch (Exception e) {
            VicMod.LOGGER.warn("OpenAI-compatible TTS request failed (voice={}, cause={}); falling back to built-in synth",
                    voice, e.getClass().getSimpleName());
        }
        return false;
    }

    /**
     * Built-in stage-aware Formant/Robotic Speech Synthesizer.
     * Synthesizes a retro virtual companion voice with vowel formants, consonant bursts,
     * and corruption-stage pitch/distortion so Vic can speak with zero external dependencies.
     */
    private static void speakBuiltInSynth(String text, int stage, float volume) {
        int sampleRate = 22050;
        byte[] pcmBytes = synthesizeFormantSpeech(text, stage, sampleRate, Math.clamp(volume, 0.1f, 1.0f));
        if (pcmBytes.length == 0 || cancelRequested) return;

        byte[] wavBytes = wrapPcmAsWav(pcmBytes, sampleRate, 1, 16);
        try {
            Path tmpWav = Files.createTempFile("vic_synth_", ".wav");
            try {
                Files.write(tmpWav, wavBytes);
                if (!playAudioFile(tmpWav, wavBytes)) {
                    VicMod.LOGGER.warn("Vic built-in speech could not be played by any available audio backend");
                }
            } finally {
                Files.deleteIfExists(tmpWav);
            }
        } catch (IOException e) {
            if (!playViaJavaSound(pcmBytes, sampleRate)) {
                VicMod.LOGGER.warn("Vic built-in speech could not be played after creating its WAV file failed");
            }
        }
    }

    private static boolean playAudioFile(Path wavFile, byte[] wavBytes) {
        if (cancelRequested) return true;
        String absPath = wavFile.toAbsolutePath().toString();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if (os.contains("linux")) {
            if (isBinaryAvailable("paplay") && runProcess(new ProcessBuilder("paplay", absPath))) {
                return true;
            }
            if (isBinaryAvailable("ffplay") && runProcess(new ProcessBuilder("ffplay", "-nodisp", "-autoexit", "-loglevel", "quiet", absPath))) {
                return true;
            }
            if (isBinaryAvailable("aplay") && runProcess(new ProcessBuilder("aplay", "-q", absPath))) {
                return true;
            }
        } else if (os.contains("mac")) {
            if (isBinaryAvailable("afplay") && runProcess(new ProcessBuilder("afplay", absPath))) {
                return true;
            }
        }

        // Universal Java Sound fallback
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavBytes))) {
            AudioFormat format = ais.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(format);
                line.start();
                byte[] buf = new byte[4096];
                int read;
                while (!cancelRequested && (read = ais.read(buf, 0, buf.length)) != -1) {
                    line.write(buf, 0, read);
                }
                if (!cancelRequested) {
                    line.drain();
                }
            }
            return true;
        } catch (Exception e) {
            VicMod.LOGGER.debug("Java Sound playback fallback failed (cause={})", e.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean playViaJavaSound(byte[] rawPcm16Mono, int sampleRate) {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
            line.open(format);
            line.start();
            int offset = 0;
            int chunk = 2048;
            while (!cancelRequested && offset < rawPcm16Mono.length) {
                int len = Math.min(chunk, rawPcm16Mono.length - offset);
                line.write(rawPcm16Mono, offset, len);
                offset += len;
            }
            if (!cancelRequested) {
                line.drain();
            }
            return true;
        } catch (Exception e) {
            VicMod.LOGGER.debug("Java Sound PCM playback failed (cause={})", e.getClass().getSimpleName());
            return false;
        }
    }

    private static byte[] synthesizeFormantSpeech(String text, int stage, int sampleRate, float masterVol) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String lower = text.toLowerCase(Locale.ROOT);

        double basePitch = switch (stage) {
            case 0 -> 265.0; // Cheerful bright companion
            case 1 -> 215.0; // Slightly uncanny
            case 2 -> 150.0; // Cracking mask
            default -> 88.0; // Demonic / corrupted
        };

        int charMs = switch (stage) {
            case 0 -> 52;
            case 1 -> 58;
            case 2 -> 66;
            default -> 78;
        };

        double phase0 = 0.0;
        double phaseF1 = 0.0;
        double phaseF2 = 0.0;
        double phaseSub = 0.0;
        long rng = 0x5DEECE66DL;

        int n = lower.length();
        for (int i = 0; i < n; i++) {
            if (cancelRequested) break;
            char c = lower.charAt(i);

            if (c == ' ') {
                writeSilenceMs(out, sampleRate, charMs / 2);
                continue;
            }
            if (c == '.' || c == '!' || c == '?' || c == ',' || c == ';' || c == ':') {
                writeSilenceMs(out, sampleRate, c == ',' ? charMs * 2 : charMs * 3);
                continue;
            }
            if (!Character.isLetterOrDigit(c)) {
                continue;
            }

            // Formant frequencies (F1, F2) by phoneme group
            double f1 = 500.0;
            double f2 = 1500.0;
            double noiseMix = 0.04;
            double amp = 0.85;
            int durMs = charMs;

            switch (c) {
                case 'a' -> { f1 = 730; f2 = 1090; durMs = (int) (charMs * 1.25); }
                case 'e' -> { f1 = 530; f2 = 1840; durMs = (int) (charMs * 1.2); }
                case 'i', 'y' -> { f1 = 270; f2 = 2290; durMs = (int) (charMs * 1.15); }
                case 'o' -> { f1 = 570; f2 = 840; durMs = (int) (charMs * 1.25); }
                case 'u', 'w' -> { f1 = 300; f2 = 870; durMs = (int) (charMs * 1.2); }
                case 's', 'z', 'c' -> { f1 = 400; f2 = 2600; noiseMix = 0.55; amp = 0.45; durMs = (int) (charMs * 0.8); }
                case 'f', 'v', 'h' -> { f1 = 450; f2 = 1900; noiseMix = 0.45; amp = 0.40; durMs = (int) (charMs * 0.75); }
                case 'p', 't', 'k', 'b', 'd', 'g' -> { f1 = 380; f2 = 1400; noiseMix = 0.25; amp = 0.65; durMs = (int) (charMs * 0.65); }
                case 'm', 'n' -> { f1 = 280; f2 = 1150; noiseMix = 0.02; amp = 0.75; durMs = (int) (charMs * 0.95); }
                case 'l', 'r' -> { f1 = 420; f2 = 1380; noiseMix = 0.03; amp = 0.80; durMs = (int) (charMs * 0.9); }
                default -> { f1 = 480; f2 = 1550; durMs = (int) (charMs * 0.85); }
            }

            // Melodic contour per character index
            double contour = switch (stage) {
                case 0 -> 1.0 + 0.14 * Math.sin(i * 0.55) + (text.endsWith("!") || text.endsWith("?") ? (0.15 * i / Math.max(1, n)) : 0.0);
                case 1 -> 1.0 + 0.07 * Math.sin(i * 0.4);
                case 2 -> (i % 5 == 0) ? 0.82 : 1.0 + 0.03 * Math.sin(i * 0.9);
                default -> 0.92 + 0.12 * Math.cos(i * 1.3);
            };

            double f0 = basePitch * contour;
            int totalSamples = (sampleRate * durMs) / 1000;

            for (int s = 0; s < totalSamples; s++) {
                double progress = (double) s / Math.max(1, totalSamples);
                // Smooth attack/release envelope per phoneme
                double env = 1.0;
                if (progress < 0.12) {
                    env = progress / 0.12;
                } else if (progress > 0.82) {
                    env = (1.0 - progress) / 0.18;
                }

                // Vibrato
                double vibrato = (stage == 0) ? (1.0 + 0.015 * Math.sin(2.0 * Math.PI * 6.0 * s / sampleRate))
                        : (stage >= 2 ? (1.0 + 0.035 * Math.sin(2.0 * Math.PI * 14.0 * s / sampleRate)) : 1.0);

                phase0 += (f0 * vibrato) / sampleRate;
                phaseF1 += f1 / sampleRate;
                phaseF2 += f2 / sampleRate;
                phaseSub += (f0 * 0.5) / sampleRate;

                // Rich glottal pulse (band-limited harmonics) + formant resonators
                double glottal = 0.50 * Math.sin(2.0 * Math.PI * phase0)
                        + 0.30 * Math.sin(4.0 * Math.PI * phase0)
                        + 0.15 * Math.sin(6.0 * Math.PI * phase0);

                double formantWave = 0.55 * Math.sin(2.0 * Math.PI * phaseF1) * (0.5 + 0.5 * glottal)
                        + 0.30 * Math.sin(2.0 * Math.PI * phaseF2) * (0.5 + 0.5 * glottal)
                        + 0.35 * glottal;

                if (stage >= 2) {
                    // Add detuned sub-oscillator & ring modulation in corrupted stages
                    double sub = Math.sin(2.0 * Math.PI * phaseSub);
                    formantWave = 0.65 * formantWave + 0.35 * (formantWave * sub);
                }

                // Consonant / sibilant pseudo-random noise
                rng = (rng * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
                double white = ((double) (rng >> 24) / (double) (1 << 23)) - 1.0;

                double sample = (formantWave * (1.0 - noiseMix) + white * noiseMix) * env * amp * masterVol;

                if (stage >= 3) {
                    // Subtle quantization / bit-crush grit for Stage 3+
                    sample = Math.round(sample * 12.0) / 12.0;
                }

                int pcm16 = (int) Math.clamp(sample * 24000.0, -32767.0, 32767.0);
                out.write(pcm16 & 0xFF);
                out.write((pcm16 >> 8) & 0xFF);
            }
        }

        return out.toByteArray();
    }

    private static void writeSilenceMs(ByteArrayOutputStream out, int sampleRate, int ms) {
        int samples = (sampleRate * Math.max(0, ms)) / 1000;
        for (int i = 0; i < samples; i++) {
            out.write(0);
            out.write(0);
        }
    }

    public static byte[] wrapPcmAsWav(byte[] pcmData, int sampleRate, int channels, int bitsPerSample) {
        int byteRate = sampleRate * channels * (bitsPerSample / 8);
        int blockAlign = channels * (bitsPerSample / 8);
        ByteBuffer buffer = ByteBuffer.allocate(44 + pcmData.length).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 'R').put((byte) 'I').put((byte) 'F').put((byte) 'F');
        buffer.putInt(36 + pcmData.length);
        buffer.put((byte) 'W').put((byte) 'A').put((byte) 'V').put((byte) 'E');

        buffer.put((byte) 'f').put((byte) 'm').put((byte) 't').put((byte) ' ');
        buffer.putInt(16); // PCM chunk size
        buffer.putShort((short) 1); // AudioFormat = 1 (PCM)
        buffer.putShort((short) channels);
        buffer.putInt(sampleRate);
        buffer.putInt(byteRate);
        buffer.putShort((short) blockAlign);
        buffer.putShort((short) bitsPerSample);

        buffer.put((byte) 'd').put((byte) 'a').put((byte) 't').put((byte) 'a');
        buffer.putInt(pcmData.length);
        buffer.put(pcmData);

        return buffer.array();
    }

    public static boolean isBinaryAvailable(String binary) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return false;
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File file = new File(dir, binary);
            if (file.isFile() && file.canExecute()) {
                return true;
            }
        }
        return false;
    }

    private static boolean runProcess(ProcessBuilder pb) {
        try {
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            ACTIVE_PROCESS.set(proc);
            int code = proc.waitFor();
            ACTIVE_PROCESS.compareAndSet(proc, null);
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
