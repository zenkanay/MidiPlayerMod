package com.example.midiplayer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.input.KeyInput;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MODの各種設定および自動演奏のコントロール（再生・一時停止・シーク）を行うメインGUI画面。
 * 進捗表記の統一、停止中のシーク操作、および曲時間の事前キャッシュに対応しています。
 * 設定は変更のたびに自動的にファイルに保存されます。
 */
public class ConfigScreen extends Screen {
    private String selectedMidiFile;
    private Set<Instrument> selectedInstruments = new HashSet<>();
    private int octaveOffset;
    private boolean blockClickDuringPlay;
    private boolean loopPlayback;
    
    private ButtonWidget fileSelectButton;
    private ButtonWidget instrumentButton;
    private ButtonWidget octaveTextButton;
    private ButtonWidget blockClickButton;
    private ButtonWidget loopPlaybackButton;
    private ButtonWidget autoTuneButton;

    // 再生コントロール用
    private MidiProgressSlider progressSlider;
    private ButtonWidget playPauseButton;
    private ButtonWidget stopButton;
    private long seekTargetMs = 0;

    // パフォーマンス最適化のためのMIDIデータキャッシュ
    private long totalPlayTimeMs = 0;
    private List<ParsedEvent> cachedEvents = null;

    public ConfigScreen() {
        super(Text.translatable("gui.midiplayer.config.title"));
        Config config = ClientMod.getInstance().getConfig();
        this.selectedMidiFile = config.midiFileName;
        this.selectedInstruments = new HashSet<>(config.activeInstruments);
        this.octaveOffset = config.octaveOffset;
        this.blockClickDuringPlay = config.blockClickDuringPlay;
        this.loopPlayback = config.loopPlayback;
        
        // 起動時に現在の曲のデータをキャッシュ
        cacheMidiData();
        
        // すでに再生中または一時停止中の場合は、現在の位置または一時停止位置をシークターゲットの初期値にする
        AutoPlayer player = ClientMod.getInstance().getAutoPlayer();
        if (player != null) {
            if (player.isPlaying()) {
                MidiScheduler sched = player.getScheduler();
                if (sched != null) {
                    this.seekTargetMs = sched.getCurrentTimeMs();
                }
            } else if (player.getPausedTimeMs() > 0) {
                this.seekTargetMs = player.getPausedTimeMs();
            }
        }
    }

    /**
     * 現在の設定値をConfigオブジェクトに反映し、ファイルに自動保存します。
     */
    private void saveConfig() {
        Config config = ClientMod.getInstance().getConfig();
        config.midiFileName = this.selectedMidiFile;
        config.activeInstruments = new HashSet<>(this.selectedInstruments);
        config.octaveOffset = this.octaveOffset;
        config.blockClickDuringPlay = this.blockClickDuringPlay;
        config.loopPlayback = this.loopPlayback;
        config.save();
    }

    /**
     * 現在選択されているMIDIファイルをパースし、総再生時間などの情報をキャッシュします。
     */
    private void cacheMidiData() {
        if (selectedMidiFile == null || selectedMidiFile.isEmpty()) {
            this.totalPlayTimeMs = 0;
            this.cachedEvents = null;
            return;
        }
        File midiFile = new File(ClientMod.getInstance().getMidiDir(), selectedMidiFile);
        if (midiFile.exists()) {
            try {
                this.cachedEvents = MidiParser.parse(midiFile);
                this.totalPlayTimeMs = this.cachedEvents.isEmpty() ? 0 : this.cachedEvents.get(this.cachedEvents.size() - 1).timeMs;
            } catch (Exception e) {
                e.printStackTrace();
                this.totalPlayTimeMs = 0;
                this.cachedEvents = null;
            }
        } else {
            this.totalPlayTimeMs = 0;
            this.cachedEvents = null;
        }
        if (autoTuneButton != null) {
            autoTuneButton.active = (cachedEvents != null && !cachedEvents.isEmpty());
        }
    }

    @Override
    protected void init() {
        super.init();

        int leftX = this.width / 2 - 155;
        int rightX = this.width / 2 + 5;
        int btnWidth = 150;
        int startY = 40;
        int spacing = 24;

        // --- セクション1: MODの設定項目 (左右2列のバニラスタイル) ---
        
        // 1. MIDIファイル選択
        String fileLabel = selectedMidiFile.isEmpty() ? Text.translatable("gui.midiplayer.config.unselected").getString() : selectedMidiFile;
        fileSelectButton = ButtonWidget.builder(Text.translatable("gui.midiplayer.config.midi_file", fileLabel), btn -> {
            if (this.client != null) {
                this.client.setScreen(new MidiFileSelectionScreen(this, name -> {
                    this.selectedMidiFile = name;
                    btn.setMessage(Text.translatable("gui.midiplayer.config.midi_file", name));
                    
                    // 曲変更時に演奏中または一時停止中であれば、古い再生スレッドを完全に停止して破棄する
                    AutoPlayer p = ClientMod.getInstance().getAutoPlayer();
                    if (p != null && p.isActive()) {
                        p.stopPlay();
                    }
                    
                    // 新しいファイルが選ばれたらシークとキャッシュを更新・リセット
                    this.seekTargetMs = 0;
                    cacheMidiData();
                    if (progressSlider != null) {
                        progressSlider.setValueDirectly(0.0);
                    }

                    saveConfig(); // 変更時に自動保存
                }));
            }
        }).dimensions(leftX, startY, btnWidth, 20).build();
        this.addDrawableChild(fileSelectButton);

        // 2. 楽器複数選択画面を開くボタン
        instrumentButton = ButtonWidget.builder(Text.translatable("gui.midiplayer.config.instruments.select"), btn -> {
            if (this.client != null) {
                this.client.setScreen(new InstrumentSelectionScreen(this, selectedInstruments, set -> {
                    this.selectedInstruments.clear();
                    this.selectedInstruments.addAll(set);
                    updateInstrumentButtonMessage();
                    
                    // 演奏中の場合はリアルタイムにフィルタに適用
                    ClientMod.getInstance().getInstrumentFilter().setActiveInstruments(selectedInstruments);

                    saveConfig(); // 変更時に自動保存
                }));
            }
        }).dimensions(rightX, startY, btnWidth, 20).build();
        this.addDrawableChild(instrumentButton);
        updateInstrumentButtonMessage();

        // 3. オクターブ補正調整 (左側: -ボタン / テキスト表示 / +ボタン の結合レイアウト)
        int octaveY = startY + spacing;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("-"), btn -> {
            octaveOffset--;
            updateOctaveButtonMessage();
            ClientMod.getInstance().getNoteMapper().setOctaveOffset(octaveOffset);
            saveConfig();
        }).dimensions(leftX, octaveY, 30, 20).build());

        octaveTextButton = ButtonWidget.builder(Text.empty(), btn -> {})
                .dimensions(leftX + 32, octaveY, 86, 20)
                .build();
        octaveTextButton.active = false;
        this.addDrawableChild(octaveTextButton);
        updateOctaveButtonMessage();

        this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), btn -> {
            octaveOffset++;
            updateOctaveButtonMessage();
            ClientMod.getInstance().getNoteMapper().setOctaveOffset(octaveOffset);
            saveConfig();
        }).dimensions(leftX + 120, octaveY, 30, 20).build());

        // 4. ループ再生トグル (右側)
        loopPlaybackButton = ButtonWidget.builder(Text.empty(), btn -> {
            this.loopPlayback = !this.loopPlayback;
            updateLoopPlaybackButtonMessage();
            saveConfig();
        }).dimensions(rightX, octaveY, btnWidth, 20).build();
        this.addDrawableChild(loopPlaybackButton);
        updateLoopPlaybackButtonMessage();

        // 5. 再生中の左・右クリックブロックのオンオフ切り替えトグル (左側)
        int toggleY = startY + spacing * 2;
        blockClickButton = ButtonWidget.builder(Text.empty(), btn -> {
            this.blockClickDuringPlay = !this.blockClickDuringPlay;
            updateBlockClickButtonMessage();
            saveConfig();
        }).dimensions(leftX, toggleY, btnWidth, 20).build();
        this.addDrawableChild(blockClickButton);
        updateBlockClickButtonMessage();

        // 6. 周辺音符ブロックの自動調律ボタン (右側)
        autoTuneButton = ButtonWidget.builder(Text.translatable("gui.midiplayer.config.auto_tune"), btn -> {
            AutoPlayer p = ClientMod.getInstance().getAutoPlayer();
            if (p != null) {
                p.autoTune(cachedEvents);
                this.close(); // 調律完了後にGUIを閉じる
            }
        }).dimensions(rightX, toggleY, btnWidth, 20).build();
        autoTuneButton.active = (cachedEvents != null && !cachedEvents.isEmpty());
        this.addDrawableChild(autoTuneButton);


        // --- セクション2: 再生コントロール (中央セクション) ---
        int controlStartY = startY + spacing * 3 + 12;

        // 進捗スライダー (中央配置、幅310)
        double initialProgress = 0.0;
        if (totalPlayTimeMs > 0) {
            initialProgress = (double) seekTargetMs / totalPlayTimeMs;
        }
        progressSlider = new MidiProgressSlider(leftX, controlStartY, 310, 20, initialProgress);
        this.addDrawableChild(progressSlider);

        // 再生 / 一時停止 & 停止ボタン
        int playBtnY = controlStartY + spacing + 4;
        playPauseButton = ButtonWidget.builder(Text.translatable("gui.midiplayer.config.play"), btn -> {
            AutoPlayer p = ClientMod.getInstance().getAutoPlayer();
            if (p != null) {
                if (p.isPlaying()) {
                    p.pausePlay();
                } else if (p.isActive() && p.getScheduler().isPaused()) {
                    // 一時停止中の場合、スライダーが動かされていたらその位置から新規に再生を開始する
                    long currentSchedTime = p.getScheduler().getCurrentTimeMs();
                    if (Math.abs(seekTargetMs - currentSchedTime) > 100) {
                        saveConfig();
                        playMidiFromOffset(p, seekTargetMs);
                    } else {
                        p.resumePlay();
                    }
                } else {
                    saveConfig();
                    playMidiFromOffset(p, seekTargetMs);
                }
            }
        }).dimensions(leftX, playBtnY, btnWidth, 20).build();
        this.addDrawableChild(playPauseButton);

        stopButton = ButtonWidget.builder(Text.translatable("gui.midiplayer.config.stop"), btn -> {
            AutoPlayer p = ClientMod.getInstance().getAutoPlayer();
            if (p != null) {
                p.stopPlay();
                seekTargetMs = 0;
                if (progressSlider != null) {
                    progressSlider.setValueDirectly(0.0);
                }
            }
        }).dimensions(rightX, playBtnY, btnWidth, 20).build();
        this.addDrawableChild(stopButton);


        // --- セクション3: 完了ボタン (画面最下部) ---
        int bottomY = this.height - 30;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), btn -> {
            this.close();
        }).dimensions(this.width / 2 - 100, bottomY, 200, 20).build());
    }

    private void playMidiFromOffset(AutoPlayer player, long offsetMs) {
        if (selectedMidiFile == null || selectedMidiFile.isEmpty()) {
            return;
        }
        File midiFile = new File(ClientMod.getInstance().getMidiDir(), selectedMidiFile);
        if (!midiFile.exists()) {
            return;
        }

        try {
            // オクターブと楽器設定を適用
            ClientMod.getInstance().getNoteMapper().setOctaveOffset(octaveOffset);
            ClientMod.getInstance().getInstrumentFilter().setActiveInstruments(selectedInstruments);

            // キャッシュされているイベントがあれば再利用し、なければロード
            List<ParsedEvent> events = cachedEvents != null ? cachedEvents : MidiParser.parse(midiFile);
            player.startPlay(events, offsetMs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void triggerSeek(long offsetMs) {
        AutoPlayer player = ClientMod.getInstance().getAutoPlayer();
        if (player != null && player.isPlaying()) {
            // シーク時にも最新の設定を反映して保存
            saveConfig();

            // 再生中の場合は、即座に指定ミリ秒から再生をやり直す
            playMidiFromOffset(player, offsetMs);
        }
    }

    private void updateBlockClickButtonMessage() {
        if (blockClickButton != null) {
            if (this.blockClickDuringPlay) {
                blockClickButton.setMessage(Text.translatable("gui.midiplayer.config.block_click.on"));
            } else {
                blockClickButton.setMessage(Text.translatable("gui.midiplayer.config.block_click.off"));
            }
        }
    }

    private void updateLoopPlaybackButtonMessage() {
        if (loopPlaybackButton != null) {
            if (this.loopPlayback) {
                loopPlaybackButton.setMessage(Text.translatable("gui.midiplayer.config.loop.on"));
            } else {
                loopPlaybackButton.setMessage(Text.translatable("gui.midiplayer.config.loop.off"));
            }
        }
    }

    private void updateOctaveButtonMessage() {
        if (octaveTextButton != null) {
            String sign = octaveOffset > 0 ? "+" : "";
            octaveTextButton.setMessage(Text.translatable("gui.midiplayer.config.octave", sign, octaveOffset));
        }
    }

    private void updateInstrumentButtonMessage() {
        if (instrumentButton != null) {
            int size = selectedInstruments.size();
            int total = Instrument.values().length;
            if (size == total) {
                instrumentButton.setMessage(Text.translatable("gui.midiplayer.config.instruments.all"));
            } else if (size == 0) {
                instrumentButton.setMessage(Text.translatable("gui.midiplayer.config.instruments.none"));
            } else {
                instrumentButton.setMessage(Text.translatable("gui.midiplayer.config.instruments.some", size, total));
            }
        }
    }

    @Override
    public void removed() {
        super.removed();
        // 自動保存が有効なため、ロールバック（以前の値へ戻す）処理は一切行いません
    }

    @Override
    public void tick() {
        super.tick();
        AutoPlayer player = ClientMod.getInstance().getAutoPlayer();
        if (player != null) {
            // 再生ボタンのテキスト状態を同期
            if (playPauseButton != null) {
                if (player.isPlaying()) {
                    playPauseButton.setMessage(Text.translatable("gui.midiplayer.config.pause"));
                } else if (player.isActive() && player.getScheduler().isPaused()) {
                    playPauseButton.setMessage(Text.translatable("gui.midiplayer.config.resume"));
                } else {
                    playPauseButton.setMessage(Text.translatable("gui.midiplayer.config.play"));
                }
            }

            // スライダー進捗の同期（演奏が行われている時のみ自動同期）
            if (player.isPlaying()) {
                MidiScheduler sched = player.getScheduler();
                if (sched != null && progressSlider != null) {
                    long current = sched.getCurrentTimeMs();
                    long total = sched.getTotalTimeMs();
                    if (total > 0) {
                        double pct = (double) current / total;
                        progressSlider.setValueDirectly(pct);
                        this.seekTargetMs = current;
                    }
                }
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // クラッシュ回避の独自半透明背景
        context.fill(0, 0, this.width, this.height, 0xC0101010);
        
        // 画面タイトル (太字)
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);

        // 設定セクションの見出し (薄い灰色)
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7--- 設定 ---"), this.width / 2, 28, 0xAAAAAA);

        // 再生コントロールの見出し (薄い灰色)
        int controlLabelY = 40 + 24 * 3 + 2;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7--- 再生コントロール ---"), this.width / 2, controlLabelY, 0xAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (KeyBindings.configOpenKey != null && KeyBindings.configOpenKey.matchesKey(keyInput)) {
            this.close();
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * MinecraftのSliderWidgetを継承したカスタムシークバー。
     */
    private class MidiProgressSlider extends SliderWidget {
        public MidiProgressSlider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, Text.empty(), value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            AutoPlayer p = ClientMod.getInstance().getAutoPlayer();
            
            // 再生中かつスケジューラがアクティブな場合は、リアルタイムの進捗を表示
            if (p != null && p.isPlaying()) {
                MidiScheduler sched = p.getScheduler();
                if (sched != null) {
                    long current = sched.getCurrentTimeMs();
                    long total = sched.getTotalTimeMs();
                    this.setMessage(Text.translatable("gui.midiplayer.config.progress", formatTime(current), formatTime(total)));
                    return;
                }
            }
            
            // 一時停止または停止中の場合は、キャッシュされた総時間と現在のシーク設定位置を表示
            this.setMessage(Text.translatable("gui.midiplayer.config.progress", formatTime(seekTargetMs), formatTime(totalPlayTimeMs)));
        }

        @Override
        protected void applyValue() {
            // スライダーの位置から目標のミリ秒を逆算
            long targetMs = Math.round(this.value * totalPlayTimeMs);
            ConfigScreen.this.seekTargetMs = targetMs;
            
            AutoPlayer p = ClientMod.getInstance().getAutoPlayer();
            if (p != null) {
                // 一時停止中の場合、AutoPlayer 内の一時停止位置（pausedTimeMs）もリアルタイムに同期する
                if (p.getPausedTimeMs() > 0) {
                    p.setPausedTimeMs(targetMs);
                }
            }
            
            // 再生中の場合は即座に再生位置をシーク
            ConfigScreen.this.triggerSeek(targetMs);
            
            // 止めているときでもスライダーのメッセージ（テキスト）を即時更新
            this.updateMessage();
        }

        /**
         * スライダーのパーセント値を直接設定し、文字表示を更新します。
         */
        public void setValueDirectly(double val) {
            this.value = MathHelper.clamp(val, 0.0, 1.0);
            this.updateMessage();
        }
    }

    private static String formatTime(long ms) {
        long sec = ms / 1000;
        long min = sec / 60;
        sec = sec % 60;
        return String.format("%d:%02d", min, sec);
    }
}
