package com.vicmod.client.gui;

import com.vicmod.ai.VicAiService;
import com.vicmod.config.VicConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class VicConfigScreen extends Screen {
    private final Screen parent;

    private String aiProvider;
    private int countdownDays;
    private boolean autoSpawnBox;
    private boolean enableWorldDecay;
    private boolean enableTrueDarkness;
    private int requestTimeoutSeconds;

    private TextFieldWidget ollamaUrlField;
    private TextFieldWidget ollamaModelField;
    private ButtonWidget scanOllamaButton;

    private TextFieldWidget apiEndpointField;
    private TextFieldWidget apiModelField;
    private TextFieldWidget apiKeyField;

    private ButtonWidget providerButton;
    private ButtonWidget daysButton;
    private ButtonWidget autoSpawnButton;
    private ButtonWidget worldDecayButton;
    private ButtonWidget trueDarknessButton;

    private final List<String> detectedOllamaModels = new ArrayList<>();
    private int detectedModelIndex = -1;
    private String statusText = "";
    private int statusColor = 0xAAAAAA;

    public VicConfigScreen(Screen parent) {
        super(Text.literal("Vic Mod Configuration"));
        this.parent = parent;

        VicConfig cfg = VicConfig.get();
        this.aiProvider = cfg.getEffectiveProvider();
        this.countdownDays = cfg.countdownDays;
        this.autoSpawnBox = cfg.autoSpawnDeliveryBoxOnFirstJoin;
        this.enableWorldDecay = cfg.enableWorldDecay;
        this.enableTrueDarkness = cfg.enableTrueDarknessDuringNight;
        this.requestTimeoutSeconds = cfg.requestTimeoutSeconds;
    }

    @Override
    protected void init() {
        VicConfig cfg = VicConfig.get();
        int centerX = this.width / 2;
        int leftColX = centerX - 155;
        int rightColX = centerX + 5;
        int y = 32;

        // 1. AI Provider Cycle Button
        this.providerButton = ButtonWidget.builder(getProviderButtonText(), btn -> {
            this.aiProvider = switch (this.aiProvider) {
                case "SCRIPTED" -> "OLLAMA";
                case "OLLAMA" -> "OPENAI";
                default -> "SCRIPTED";
            };
            btn.setMessage(getProviderButtonText());
            updateFieldVisibility();
            if ("OLLAMA".equals(this.aiProvider) && this.detectedOllamaModels.isEmpty()) {
                scanAndCycleOllamaModels(false);
            }
        }).dimensions(leftColX, y, 310, 20).build();
        this.addDrawableChild(this.providerButton);

        y += 28;

        // 2. Ollama Fields
        this.ollamaUrlField = new TextFieldWidget(this.textRenderer, leftColX, y + 10, 150, 20, Text.literal("Ollama URL"));
        this.ollamaUrlField.setMaxLength(256);
        this.ollamaUrlField.setText(cfg.ollamaUrl != null ? cfg.ollamaUrl : "http://localhost:11434");
        this.addDrawableChild(this.ollamaUrlField);

        this.ollamaModelField = new TextFieldWidget(this.textRenderer, rightColX, y + 10, 95, 20, Text.literal("Ollama Model"));
        this.ollamaModelField.setMaxLength(128);
        this.ollamaModelField.setText(cfg.ollamaModel != null ? cfg.ollamaModel : "llama3.2");
        this.addDrawableChild(this.ollamaModelField);

        this.scanOllamaButton = ButtonWidget.builder(Text.literal("Models").formatted(Formatting.AQUA), btn -> {
            scanAndCycleOllamaModels(true);
        }).dimensions(rightColX + 100, y + 10, 50, 20).build();
        this.addDrawableChild(this.scanOllamaButton);

        // 3. OpenAI / Groq Fields (same vertical slot, shown when provider == OPENAI)
        this.apiEndpointField = new TextFieldWidget(this.textRenderer, leftColX, y + 10, 150, 20, Text.literal("API Endpoint"));
        this.apiEndpointField.setMaxLength(256);
        this.apiEndpointField.setText(cfg.apiEndpoint != null ? cfg.apiEndpoint : "");
        this.addDrawableChild(this.apiEndpointField);

        this.apiModelField = new TextFieldWidget(this.textRenderer, rightColX, y + 10, 150, 20, Text.literal("API Model"));
        this.apiModelField.setMaxLength(128);
        this.apiModelField.setText(cfg.apiModel != null ? cfg.apiModel : "");
        this.addDrawableChild(this.apiModelField);

        y += 38;

        this.apiKeyField = new TextFieldWidget(this.textRenderer, leftColX, y + 10, 310, 20, Text.literal("API Key"));
        this.apiKeyField.setMaxLength(256);
        this.apiKeyField.setText(cfg.apiKey != null ? cfg.apiKey : "");
        this.addDrawableChild(this.apiKeyField);

        y += 36;

        // 4. Gameplay Toggles (2 columns)
        this.daysButton = ButtonWidget.builder(getDaysButtonText(), btn -> {
            this.countdownDays = (this.countdownDays % 15) + 1;
            btn.setMessage(getDaysButtonText());
        }).dimensions(leftColX, y, 150, 20).build();
        this.addDrawableChild(this.daysButton);

        this.autoSpawnButton = ButtonWidget.builder(getToggleText("Auto-Spawn Box", this.autoSpawnBox), btn -> {
            this.autoSpawnBox = !this.autoSpawnBox;
            btn.setMessage(getToggleText("Auto-Spawn Box", this.autoSpawnBox));
        }).dimensions(rightColX, y, 150, 20).build();
        this.addDrawableChild(this.autoSpawnButton);

        y += 24;

        this.worldDecayButton = ButtonWidget.builder(getToggleText("World Decay", this.enableWorldDecay), btn -> {
            this.enableWorldDecay = !this.enableWorldDecay;
            btn.setMessage(getToggleText("World Decay", this.enableWorldDecay));
        }).dimensions(leftColX, y, 150, 20).build();
        this.addDrawableChild(this.worldDecayButton);

        this.trueDarknessButton = ButtonWidget.builder(getToggleText("Night Darkness", this.enableTrueDarkness), btn -> {
            this.enableTrueDarkness = !this.enableTrueDarkness;
            btn.setMessage(getToggleText("Night Darkness", this.enableTrueDarkness));
        }).dimensions(rightColX, y, 150, 20).build();
        this.addDrawableChild(this.trueDarknessButton);

        // 5. Save & Cancel Buttons
        int bottomY = this.height - 28;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close").formatted(Formatting.GREEN), btn -> {
            saveChanges();
            this.close();
        }).dimensions(leftColX, bottomY, 150, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> {
            this.close();
        }).dimensions(rightColX, bottomY, 150, 20).build());

        updateFieldVisibility();
        if ("OLLAMA".equals(this.aiProvider)) {
            scanAndCycleOllamaModels(false);
        }
    }

    private void updateFieldVisibility() {
        boolean isOllama = "OLLAMA".equals(this.aiProvider);
        boolean isOpenAi = "OPENAI".equals(this.aiProvider);

        this.ollamaUrlField.setVisible(isOllama);
        this.ollamaModelField.setVisible(isOllama);
        this.scanOllamaButton.visible = isOllama;

        this.apiEndpointField.setVisible(isOpenAi);
        this.apiModelField.setVisible(isOpenAi);
        this.apiKeyField.setVisible(isOpenAi);
    }

    private void scanAndCycleOllamaModels(boolean cycleIfAlreadyLoaded) {
        if (cycleIfAlreadyLoaded && !this.detectedOllamaModels.isEmpty()) {
            this.detectedModelIndex = (this.detectedModelIndex + 1) % this.detectedOllamaModels.size();
            String chosen = this.detectedOllamaModels.get(this.detectedModelIndex);
            this.ollamaModelField.setText(chosen);
            this.statusText = "Selected Ollama model (" + (this.detectedModelIndex + 1) + "/" + this.detectedOllamaModels.size() + "): " + chosen;
            this.statusColor = 0x55FF55;
            return;
        }

        String url = this.ollamaUrlField.getText();
        this.statusText = "Scanning Ollama models at " + VicAiService.normalizeOllamaBaseUrl(url) + "...";
        this.statusColor = 0xFFFF55;

        VicAiService.fetchOllamaModels(url).thenAccept(models -> {
            MinecraftClient.getInstance().execute(() -> {
                this.detectedOllamaModels.clear();
                this.detectedOllamaModels.addAll(models);
                if (models.isEmpty()) {
                    this.statusText = "No local Ollama models detected (is 'ollama serve' running?)";
                    this.statusColor = 0xFF5555;
                } else {
                    String current = this.ollamaModelField.getText().trim();
                    int idx = models.indexOf(current);
                    if (idx == -1 || cycleIfAlreadyLoaded) {
                        this.detectedModelIndex = (idx + 1) % models.size();
                        this.ollamaModelField.setText(models.get(this.detectedModelIndex));
                    } else {
                        this.detectedModelIndex = idx;
                    }
                    this.statusText = "Found " + models.size() + " Ollama model(s)! Click 'Models' to cycle.";
                    this.statusColor = 0x55FF55;
                }
            });
        });
    }

    private Text getProviderButtonText() {
        Formatting color = switch (this.aiProvider) {
            case "OLLAMA" -> Formatting.AQUA;
            case "OPENAI" -> Formatting.GREEN;
            default -> Formatting.YELLOW;
        };
        String label = switch (this.aiProvider) {
            case "OLLAMA" -> "Ollama (Local LLM)";
            case "OPENAI" -> "Remote API (OpenAI / Groq)";
            default -> "Built-in Offline Scripted";
        };
        return Text.literal("AI Mode: ").append(Text.literal(label).formatted(color, Formatting.BOLD));
    }

    private Text getDaysButtonText() {
        return Text.literal("Trial Length: ").append(Text.literal(this.countdownDays + " Days").formatted(Formatting.YELLOW));
    }

    private Text getToggleText(String name, boolean enabled) {
        return Text.literal(name + ": ").append(
                Text.literal(enabled ? "ON" : "OFF").formatted(enabled ? Formatting.GREEN : Formatting.RED, Formatting.BOLD)
        );
    }

    private void saveChanges() {
        VicConfig cfg = VicConfig.get();
        cfg.setProvider(this.aiProvider);
        cfg.ollamaUrl = this.ollamaUrlField.getText().trim();
        cfg.ollamaModel = this.ollamaModelField.getText().trim();
        cfg.apiEndpoint = this.apiEndpointField.getText().trim();
        cfg.apiModel = this.apiModelField.getText().trim();
        cfg.apiKey = this.apiKeyField.getText().trim();
        cfg.countdownDays = this.countdownDays;
        cfg.autoSpawnDeliveryBoxOnFirstJoin = this.autoSpawnBox;
        cfg.enableWorldDecay = this.enableWorldDecay;
        cfg.enableTrueDarknessDuringNight = this.enableTrueDarkness;
        cfg.requestTimeoutSeconds = this.requestTimeoutSeconds;
        VicConfig.save();

        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(
                    Text.literal("[Vic Config] ").formatted(Formatting.YELLOW, Formatting.BOLD)
                            .append(Text.literal("Saved! Mode=" + cfg.getEffectiveProvider() +
                                    ("OLLAMA".equals(cfg.getEffectiveProvider()) ? " (" + cfg.ollamaModel + ")" : "")).formatted(Formatting.GREEN)),
                    true
            );
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int leftColX = centerX - 155;
        int rightColX = centerX + 5;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 12, 0xFFE135);

        if ("OLLAMA".equals(this.aiProvider)) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("Ollama Base URL:"), leftColX, 60, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, Text.literal("Ollama Model:"), rightColX, 60, 0xCCCCCC);
        } else if ("OPENAI".equals(this.aiProvider)) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("API Endpoint URL:"), leftColX, 60, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, Text.literal("Model Name:"), rightColX, 60, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, Text.literal("Bearer API Key:"), leftColX, 98, 0xCCCCCC);
        } else {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Using built-in stage-aware dialogue (no external AI server required)."),
                    centerX,
                    76,
                    0xAAAAAA
            );
        }

        if (this.statusText != null && !this.statusText.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusText), centerX, 112, this.statusColor);
        }
    }
}
