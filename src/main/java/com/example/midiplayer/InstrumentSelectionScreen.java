package com.example.midiplayer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 演奏に使用する楽器を複数選択（チェックボックス形式）するためのGUI画面。
 * 「すべて選択」「すべて解除」による一括設定もサポートし、多言語対応されています。
 */
public class InstrumentSelectionScreen extends Screen {
    private final Screen parent;
    private final Set<Instrument> tempSelected;
    private final Consumer<Set<Instrument>> onConfirm;

    private final ButtonWidget[] instrumentButtons = new ButtonWidget[Instrument.values().length];

    public InstrumentSelectionScreen(Screen parent, Set<Instrument> currentlySelected, Consumer<Set<Instrument>> onConfirm) {
        super(Text.translatable("gui.midiplayer.instrument.title"));
        this.parent = parent;
        this.tempSelected = new HashSet<>(currentlySelected);
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        super.init();

        int startY = 50;
        int spacingY = 24;
        
        // 3列配置のX座標
        int colWidth = 115;
        int totalWidth = colWidth * 3 + 10 * 2; // 365
        int startX = this.width / 2 - totalWidth / 2;

        // 1. 上部の一括選択ボタン
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.midiplayer.instrument.select_all"), btn -> {
            tempSelected.addAll(Arrays.asList(Instrument.values()));
            updateButtonLabels();
        }).dimensions(this.width / 2 - 110, 24, 105, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.midiplayer.instrument.deselect_all"), btn -> {
            tempSelected.clear();
            updateButtonLabels();
        }).dimensions(this.width / 2 + 5, 24, 105, 20).build());

        // 2. 17個の楽器ボタンをグリッド状に配置 (3列 x 6行)
        Instrument[] instruments = Instrument.values();
        for (int i = 0; i < instruments.length; i++) {
            final Instrument inst = instruments[i];
            int col = i % 3;
            int row = i / 3;

            int x = startX + col * (colWidth + 10);
            int y = startY + row * spacingY;

            ButtonWidget btn = ButtonWidget.builder(Text.empty(), button -> {
                if (tempSelected.contains(inst)) {
                    tempSelected.remove(inst);
                } else {
                    tempSelected.add(inst);
                }
                updateButtonMessage(button, inst);
            }).dimensions(x, y, colWidth, 20).build();

            instrumentButtons[i] = btn;
            updateButtonMessage(btn, inst);
            this.addDrawableChild(btn);
        }

        // 3. 下部の決定・キャンセルボタン
        int bottomY = startY + 6 * spacingY + 12;
        
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.midiplayer.instrument.confirm"), btn -> {
            onConfirm.accept(tempSelected);
            if (this.client != null) {
                this.client.setScreen(parent);
            }
        }).dimensions(this.width / 2 - 110, bottomY, 105, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.midiplayer.instrument.cancel"), btn -> {
            if (this.client != null) {
                this.client.setScreen(parent);
            }
        }).dimensions(this.width / 2 + 5, bottomY, 105, 20).build());
    }

    private void updateButtonLabels() {
        Instrument[] instruments = Instrument.values();
        for (int i = 0; i < instruments.length; i++) {
            if (instrumentButtons[i] != null) {
                updateButtonMessage(instrumentButtons[i], instruments[i]);
            }
        }
    }

    private void updateButtonMessage(ButtonWidget btn, Instrument inst) {
        Text instName = Text.translatable(inst.translationKey);
        if (tempSelected.contains(inst)) {
            btn.setMessage(Text.literal("§a[✔] ").append(instName));
        } else {
            btn.setMessage(Text.literal("§c[ ] ").append(instName));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // クラッシュ回避の半透明背景
        context.fill(0, 0, this.width, this.height, 0xC0101010);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
