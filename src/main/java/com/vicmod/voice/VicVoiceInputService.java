package com.vicmod.voice;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vicmod.VicMod;
import com.vicmod.config.VicConfig;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class VicVoiceInputService {
    private static final int SAMPLE_RATE = 16000;
    private static final ExecutorService VOICE_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Vic-VoiceInput-Thread");
        t.setDaemon(true);
        return t;
    });

    private static final java.net.http.HttpClient HTTP_CLIENT = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private static final AtomicBoolean IS_RECORDING = new AtomicBoolean(false);
    private static volatile Process activeRecordProcess;
    private static volatile TargetDataLine activeTargetLine;
    private static volatile ByteArrayOutputStream recordedPcmStream;
    private static volatile long recordingStartMs;

    public record TranscriptionResult(boolean success, String text, String errorMessage) {
    }

    public static boolean isRecording() {
        return IS_RECORDING.get();
    }

    public static synchronized boolean startRecording() {
        if (IS_RECORDING.get()) {
            return false;
        }

        VicConfig cfg = VicConfig.get();
        if ("OFF".equals(cfg.getEffectiveSttMode())) {
            return false;
        }

        recordedPcmStream = new ByteArrayOutputStream();
        recordingStartMs = System.currentTimeMillis();
        IS_RECORDING.set(true);

        VOICE_EXECUTOR.submit(() -> {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("linux") && VicTtsService.isBinaryAvailable("arecord")) {
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                            "arecord", "-q", "-f", "S16_LE", "-r", String.valueOf(SAMPLE_RATE), "-c", "1", "-t", "raw", "-"
                    );
                    Process proc = pb.start();
                    activeRecordProcess = proc;
                    try (InputStream in = proc.getInputStream()) {
                        byte[] buf = new byte[2048];
                        int read;
                        while (IS_RECORDING.get() && (read = in.read(buf)) != -1) {
                            recordedPcmStream.write(buf, 0, read);
                        }
                    } finally {
                        if (proc.isAlive()) {
                            proc.destroy();
                        }
                        activeRecordProcess = null;
                    }
                    return;
                } catch (Exception e) {
                    VicMod.LOGGER.debug("arecord capture failed, falling back to Java TargetDataLine", e);
                }
            }

            // Universal Java Sound TargetDataLine capture
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            try {
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                activeTargetLine = line;
                line.open(format);
                line.start();
                byte[] buf = new byte[2048];
                while (IS_RECORDING.get()) {
                    int read = line.read(buf, 0, buf.length);
                    if (read > 0) {
                        recordedPcmStream.write(buf, 0, read);
                    }
                }
            } catch (Exception e) {
                VicMod.LOGGER.warn("Microphone capture via Java Sound failed", e);
            } finally {
                TargetDataLine line = activeTargetLine;
                activeTargetLine = null;
                if (line != null) {
                    try {
                        line.stop();
                        line.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        });

        return true;
    }

    public static synchronized void stopRecordingAndTranscribe(Consumer<TranscriptionResult> callback) {
        if (!IS_RECORDING.getAndSet(false)) {
            return;
        }

        Process proc = activeRecordProcess;
        if (proc != null && proc.isAlive()) {
            proc.destroy();
        }
        TargetDataLine line = activeTargetLine;
        if (line != null) {
            try {
                line.stop();
                line.close();
            } catch (Exception ignored) {
            }
        }

        long elapsedMs = System.currentTimeMillis() - recordingStartMs;
        ByteArrayOutputStream stream = recordedPcmStream;

        VOICE_EXECUTOR.submit(() -> {
            try {
                // Brief pause to allow reader thread to flush final bytes
                Thread.sleep(80);
                byte[] pcmBytes = stream != null ? stream.toByteArray() : new byte[0];

                if (elapsedMs < 350 || pcmBytes.length < SAMPLE_RATE / 2) {
                    callback.accept(new TranscriptionResult(false, "", "Hold [R] slightly longer while speaking."));
                    return;
                }

                byte[] wavBytes = VicTtsService.wrapPcmAsWav(pcmBytes, SAMPLE_RATE, 1, 16);
                TranscriptionResult result = transcribeWavBytes(wavBytes);
                callback.accept(result);
            } catch (Exception e) {
                callback.accept(new TranscriptionResult(false, "", "Voice transcription error: " + e.getMessage()));
            }
        });
    }

    public static void recordFixedDurationAndTranscribe(int seconds, Consumer<TranscriptionResult> callback) {
        int clampedSeconds = Math.clamp(seconds, 2, 15);
        if (!startRecording()) {
            callback.accept(new TranscriptionResult(false, "", "Already recording or Voice Input is disabled."));
            return;
        }
        VOICE_EXECUTOR.submit(() -> {
            try {
                Thread.sleep(clampedSeconds * 1000L);
            } catch (InterruptedException ignored) {
            }
            stopRecordingAndTranscribe(callback);
        });
    }

    private static TranscriptionResult transcribeWavBytes(byte[] wavBytes) {
        VicConfig cfg = VicConfig.get();
        String mode = cfg.getEffectiveSttMode();

        Path tmpWav = null;
        try {
            tmpWav = Files.createTempFile("vic_voice_in_", ".wav");
            Files.write(tmpWav, wavBytes);

            if ("LOCAL_CLI".equals(mode)) {
                return transcribeViaLocalCli(tmpWav, cfg);
            } else if ("WHISPER_API".equals(mode)) {
                return transcribeViaWhisperApi(tmpWav, wavBytes, cfg);
            } else {
                // AUTO mode:
                // 1. If a custom local STT command or local Whisper/Vosk CLI is installed, try it first
                if ((cfg.localSttCommand != null && !cfg.localSttCommand.isBlank()) || hasLocalSttSupport()) {
                    TranscriptionResult localRes = transcribeViaLocalCli(tmpWav, cfg);
                    if (localRes.success()) {
                        return localRes;
                    }
                }
                // 2. Try Whisper HTTP API (Groq / OpenAI / Local Whisper server)
                return transcribeViaWhisperApi(tmpWav, wavBytes, cfg);
            }
        } catch (Exception e) {
            String cause = describeException(e);
            VicMod.LOGGER.warn("Failed to process recorded STT audio: mode={}, cause={}", mode, cause);
            return new TranscriptionResult(false, "", "Failed to process recorded audio (" + cause + ").");
        } finally {
            if (tmpWav != null) {
                try {
                    Files.deleteIfExists(tmpWav);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static boolean hasLocalSttSupport() {
        return VicTtsService.isBinaryAvailable("whisper-cli")
                || VicTtsService.isBinaryAvailable("faster-whisper")
                || VicTtsService.isBinaryAvailable("whisper")
                || VicTtsService.isBinaryAvailable("vosk-transcriber");
    }

    private static String getLocalSttBackend(VicConfig cfg) {
        if (cfg.localSttCommand != null && !cfg.localSttCommand.isBlank()) return "custom command";
        if (VicTtsService.isBinaryAvailable("whisper-cli")) return "whisper-cli";
        if (VicTtsService.isBinaryAvailable("vosk-transcriber")) return "vosk-transcriber";
        if (VicTtsService.isBinaryAvailable("faster-whisper")) return "faster-whisper";
        if (VicTtsService.isBinaryAvailable("whisper")) return "whisper";
        if (VicTtsService.isBinaryAvailable("python3")) return "python3/faster-whisper";
        return null;
    }

    private static TranscriptionResult transcribeViaLocalCli(Path wavFile, VicConfig cfg) {
        String absPath = wavFile.toAbsolutePath().toString();
        String backend = getLocalSttBackend(cfg);
        try {
            ProcessBuilder pb = null;
            if (cfg.localSttCommand != null && !cfg.localSttCommand.isBlank()) {
                String cmd = cfg.localSttCommand.replace("{file}", absPath);
                pb = new ProcessBuilder("sh", "-c", cmd);
            } else if (VicTtsService.isBinaryAvailable("whisper-cli")) {
                pb = new ProcessBuilder("whisper-cli", "-np", "-nt", "-f", absPath);
            } else if (VicTtsService.isBinaryAvailable("vosk-transcriber")) {
                pb = new ProcessBuilder("vosk-transcriber", "-i", absPath);
            } else if (VicTtsService.isBinaryAvailable("faster-whisper")) {
                pb = new ProcessBuilder("faster-whisper", absPath, "--model", "base", "--output_format", "txt");
            } else if (VicTtsService.isBinaryAvailable("whisper")) {
                pb = new ProcessBuilder("whisper", absPath, "--model", "base", "--output_format", "txt", "--verbose", "False");
            } else if (VicTtsService.isBinaryAvailable("python3")) {
                String pyScript = "import sys\n" +
                        "wav = sys.argv[1]\n" +
                        "try:\n" +
                        "    from faster_whisper import WhisperModel\n" +
                        "    m = WhisperModel('base', device='cpu', compute_type='int8')\n" +
                        "    segs, _ = m.transcribe(wav)\n" +
                        "    print(' '.join(s.text.strip() for s in segs).strip())\n" +
                        "    sys.exit(0)\n" +
                        "except ImportError:\n" +
                        "    sys.stderr.write('faster-whisper is not installed\\n')\n" +
                        "    sys.exit(3)\n" +
                        "except Exception as e:\n" +
                        "    sys.stderr.write(type(e).__name__ + '\\n')\n" +
                        "    sys.exit(4)\n";
                pb = new ProcessBuilder("python3", "-c", pyScript, absPath);
            }

            if (pb == null) {
                VicMod.LOGGER.warn("No local STT recognizer found. Install whisper-cli, faster-whisper, whisper, or vosk-transcriber.");
                return new TranscriptionResult(false, "", "No local recognizer found. Install whisper-cli, faster-whisper, or vosk-transcriber.");
            }

            VicMod.LOGGER.debug("Starting local STT backend={}", backend);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exit = proc.waitFor();
            if (exit == 0 && !output.isEmpty()) {
                // Take the last non-empty line if tool printed status headers
                String[] lines = output.split("\\R");
                StringBuilder cleaned = new StringBuilder();
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("[") && !trimmed.startsWith("Detecting language")) {
                        if (!cleaned.isEmpty()) cleaned.append(" ");
                        cleaned.append(trimmed);
                    }
                }
                String finalTxt = cleaned.isEmpty() ? output : cleaned.toString();
                return new TranscriptionResult(true, finalTxt.trim(), "");
            }
            VicMod.LOGGER.warn("Local STT backend failed (backend={}, exitCode={}, outputPresent={})",
                    backend, exit, !output.isBlank());
            if ("python3/faster-whisper".equals(backend) && exit == 3) {
                return new TranscriptionResult(false, "", "Python is installed, but faster-whisper is missing. Install a local recognizer or use AUTO.");
            }
            if ("python3/faster-whisper".equals(backend) && exit == 4) {
                return new TranscriptionResult(false, "", "faster-whisper could not load its model. Check the local model installation.");
            }
            if (exit == 0) {
                return new TranscriptionResult(false, "", "No speech was recognized by " + backend + ". Try speaking closer to the microphone.");
            }
            return new TranscriptionResult(false, "", "Local recognizer " + backend + " failed (exit " + exit + "). Check its installation and model.");
        } catch (Exception e) {
            String cause = describeException(e);
            VicMod.LOGGER.warn("Local STT failed (backend={}, cause={})", backend, cause);
            return new TranscriptionResult(false, "", "Local STT error using " + backend + " (" + cause + ").");
        }
    }

    private static TranscriptionResult transcribeViaWhisperApi(Path wavFile, byte[] wavBytes, VicConfig cfg) {
        String endpoint = (cfg.sttEndpoint == null || cfg.sttEndpoint.isBlank())
                ? "https://api.groq.com/openai/v1/audio/transcriptions"
                : cfg.sttEndpoint.trim();
        String model = (cfg.sttModel == null || cfg.sttModel.isBlank())
                ? "whisper-large-v3-turbo"
                : cfg.sttModel.trim();
        String apiKey = cfg.getEffectiveSttApiKey();

        boolean isLocalEndpoint = endpoint.contains("localhost") || endpoint.contains("127.0.0.1");
        if (apiKey.isEmpty() && !isLocalEndpoint) {
            // Try installed local recognizers before failing if no API key is set.
            TranscriptionResult pyFallback = transcribeViaLocalCli(wavFile, cfg);
            if (pyFallback.success()) {
                return pyFallback;
            }
            return new TranscriptionResult(
                    false,
                    "",
                    "Voice Input requires an API key (Groq/OpenAI) or local Whisper! Press [V] or run '/vic apikey <KEY>'."
            );
        }

        try {
            String boundary = "----VicVoiceBoundary" + UUID.randomUUID().toString().replace("-", "");
            ByteArrayOutputStream bodyOut = new ByteArrayOutputStream();

            // 1. model field
            writeMultipartField(bodyOut, boundary, "model", model);
            // 2. response_format field
            writeMultipartField(bodyOut, boundary, "response_format", "json");
            // 3. file field
            String fileHeader = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n" +
                    "Content-Type: audio/wav\r\n\r\n";
            bodyOut.write(fileHeader.getBytes(StandardCharsets.UTF_8));
            bodyOut.write(wavBytes);
            bodyOut.write("\r\n".getBytes(StandardCharsets.UTF_8));

            String closing = "--" + boundary + "--\r\n";
            bodyOut.write(closing.getBytes(StandardCharsets.UTF_8));

            java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri( java.net.URI.create(endpoint))
                    .timeout(Duration.ofSeconds(Math.max(8, cfg.requestTimeoutSeconds)))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(bodyOut.toByteArray()));

            if (!apiKey.isEmpty()) {
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }

            java.net.http.HttpResponse<String> response = HTTP_CLIENT.send(
                    reqBuilder.build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body() != null) {
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonElement textEl = root.get("text");
                if (textEl != null && !textEl.isJsonNull()) {
                    String text = textEl.getAsString().trim();
                    if (!text.isEmpty()) {
                        return new TranscriptionResult(true, text, "");
                    }
                    return new TranscriptionResult(false, "", "No speech detected in microphone recording.");
                }
            }
            int statusCode = response.statusCode();
            String host = getEndpointHost(endpoint);
                String providerMessage = extractProviderError(response.body());
                String statusHint = getHttpStatusHint(statusCode);
                VicMod.LOGGER.warn("STT API returned HTTP {}: host={}, model={}, hint={}, providerMessage={}",
                    statusCode, host, model, statusHint, providerMessage);
                return new TranscriptionResult(false, "", "STT API HTTP " + statusCode + " from " + host + ": "
                    + statusHint + (providerMessage.isEmpty() ? "" : " (" + providerMessage + ")"));
        } catch (Exception e) {
            String cause = describeException(e);
            String host = getEndpointHost(endpoint);
            VicMod.LOGGER.warn("STT API request failed: host={}, model={}, cause={}", host, model, cause);
            return new TranscriptionResult(false, "", "STT request failed (" + cause + ") for " + host
                    + ". Check that the endpoint is reachable and the model/key are valid.");
        }
    }

    private static String getEndpointHost(String endpoint) {
        try {
            String host = java.net.URI.create(endpoint).getHost();
            return host == null || host.isBlank() ? "unknown host" : host;
        } catch (IllegalArgumentException e) {
            return "invalid endpoint";
        }
    }

    private static String getHttpStatusHint(int statusCode) {
        return switch (statusCode) {
            case 400 -> "Request rejected; check the audio format and model";
            case 401, 403 -> "Authentication failed; check the STT API key";
            case 404 -> "Endpoint or model was not found";
            case 413 -> "Audio is too large for this endpoint";
            case 429 -> "Rate limit or quota exceeded";
            case 500, 502, 503, 504 -> "STT service is temporarily unavailable";
            default -> "Check the endpoint, model, and API key in [V] Config";
        };
    }

    private static String extractProviderError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return "";
        try {
            JsonElement rootElement = JsonParser.parseString(responseBody);
            if (!rootElement.isJsonObject()) return "";
            JsonObject root = rootElement.getAsJsonObject();
            JsonElement errorElement = root.get("error");
            String message = "";
            if (errorElement != null && errorElement.isJsonObject()) {
                JsonElement nestedMessage = errorElement.getAsJsonObject().get("message");
                if (nestedMessage != null && nestedMessage.isJsonPrimitive()) {
                    message = nestedMessage.getAsString();
                }
            } else if (errorElement != null && errorElement.isJsonPrimitive()) {
                message = errorElement.getAsString();
            }
            if (message.isBlank()) {
                JsonElement detail = root.has("detail") ? root.get("detail") : root.get("message");
                if (detail != null && detail.isJsonPrimitive()) {
                    message = detail.getAsString();
                }
            }
            return sanitizeProviderMessage(message);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String sanitizeProviderMessage(String message) {
        if (message == null || message.isBlank()) return "";
        String sanitized = message
                .replaceAll("(?i)(api[_-]?key|token|password)([=:]\\s*)[^\\s&]+", "$1$2[redacted]")
                .replaceAll("(?i)bearer\\s+[^\\s,;]+", "Bearer [redacted]")
                .replaceAll("https?://[^\\s]+", "[url]")
            .replaceAll("[\\r\\n\\t]+", " ")
            .trim();
        return sanitized.substring(0, Math.min(180, sanitized.length()));
    }

    private static String describeException(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }

        String description = cause.getClass().getSimpleName();
        String message = cause.getMessage();
        if (message != null && !message.isBlank()) {
            String safeMessage = message
                    .replaceAll("(?i)(api[_-]?key|token|password)([=:]\\s*)[^\\s&]+", "$1$2[redacted]")
                    .replaceAll("(?i)bearer\\s+[^\\s,;]+", "Bearer [redacted]")
                    .replaceAll("https?://[^\\s]+", "[url]");
            description += ": " + safeMessage;
        }
        return description;
    }

    private static void writeMultipartField(ByteArrayOutputStream out, String boundary, String name, String value) throws Exception {
        String part = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" +
                value + "\r\n";
        out.write(part.getBytes(StandardCharsets.UTF_8));
    }
}
