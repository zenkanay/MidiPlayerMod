package com.example.midiplayer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * MIDIファイルの一覧を表示し、ユーザーが演奏したいファイルを選択するためのGUI画面。
 * 複雑なスクロールリストのバージョン互換性問題を避けるため、安定したページング方式を採用し、多言語対応されています。
 */
public class MidiFileSelectionScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> onSelect;
    
    private List<String> midiFiles;
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 5;
    
    private final List<ButtonWidget> fileButtons = new ArrayList<>();
    private ButtonWidget prevButton;
    private ButtonWidget nextButton;

    public MidiFileSelectionScreen(Screen parent, Consumer<String> onSelect) {
        super(Text.translatable("gui.midiplayer.select.title"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();
        loadMidiFiles();

        // リスト表示用ボタンの作成（初期ダミーを配置し、後で非表示/表示切り替え）
        int startY = 40;
        int btnWidth = 260;
        int btnHeight = 20;
        int spacing = 24;

        fileButtons.clear();
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            final int index = i;
            ButtonWidget btn = ButtonWidget.builder(Text.literal(""), button -> {
                int fileIndex = currentPage * ITEMS_PER_PAGE + index;
                if (fileIndex < midiFiles.size()) {
                    onSelect.accept(midiFiles.get(fileIndex));
                    if (this.client != null) {
                        this.client.setScreen(parent);
                    }
                }
            }).dimensions(this.width / 2 - btnWidth / 2, startY + i * spacing, btnWidth, btnHeight).build();
            
            fileButtons.add(btn);
            this.addDrawableChild(btn);
        }

        // 前のページボタン (記号のみで表現)
        prevButton = ButtonWidget.builder(Text.literal("◀"), button -> {
            if (currentPage > 0) {
                currentPage--;
                updateList();
            }
        }).dimensions(this.width / 2 - 110, startY + ITEMS_PER_PAGE * spacing + 10, 50, 20).build();
        this.addDrawableChild(prevButton);

        // 次のページボタン
        nextButton = ButtonWidget.builder(Text.literal("▶"), button -> {
            if ((currentPage + 1) * ITEMS_PER_PAGE < midiFiles.size()) {
                currentPage++;
                updateList();
            }
        }).dimensions(this.width / 2 + 60, startY + ITEMS_PER_PAGE * spacing + 10, 50, 20).build();
        this.addDrawableChild(nextButton);

        // キャンセル/戻るボタン
        ButtonWidget backButton = ButtonWidget.builder(Text.translatable("gui.midiplayer.select.back"), button -> {
            if (this.client != null) {
                this.client.setScreen(parent);
            }
        }).dimensions(this.width / 2 - 50, startY + ITEMS_PER_PAGE * spacing + 10, 100, 20).build();
        this.addDrawableChild(backButton);

        // フォルダを開くボタン (リソースパックフォルダを開くボタンと同様の挙動)
        ButtonWidget openFolderButton = ButtonWidget.builder(Text.translatable("gui.midiplayer.select.open_folder"), button -> {
            java.io.File dir = ClientMod.getInstance().getMidiDir();
            net.minecraft.util.Util.getOperatingSystem().open(dir);
        }).dimensions(this.width / 2 - 110, startY + ITEMS_PER_PAGE * spacing + 34, 220, 20).build();
        this.addDrawableChild(openFolderButton);

        updateList();
    }

    private void loadMidiFiles() {
        midiFiles = new ArrayList<>();
        File dir = ClientMod.getInstance().getMidiDir();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && (f.getName().endsWith(".mid") || f.getName().endsWith(".midi"))) {
                        midiFiles.add(f.getName());
                    }
                }
            }
        }
        midiFiles.sort(String::compareToIgnoreCase);
    }

    private void updateList() {
        int totalFiles = midiFiles.size();
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int fileIndex = currentPage * ITEMS_PER_PAGE + i;
            ButtonWidget btn = fileButtons.get(i);
            
            if (fileIndex < totalFiles) {
                btn.visible = true;
                btn.setMessage(Text.literal(midiFiles.get(fileIndex)));
            } else {
                btn.visible = false;
            }
        }

        prevButton.active = (currentPage > 0);
        nextButton.active = ((currentPage + 1) * ITEMS_PER_PAGE < totalFiles);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // クラッシュ回避の半透明背景
        context.fill(0, 0, this.width, this.height, 0xC0101010);
        
        // タイトル表示
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        // ページ番号の表示
        int totalFiles = midiFiles.size();
        int maxPages = Math.max(1, (totalFiles + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        Text pageText = Text.translatable("gui.midiplayer.select.page", (currentPage + 1), maxPages, totalFiles);
        context.drawCenteredTextWithShadow(this.textRenderer, pageText, this.width / 2, 28, 0xAAAAAA);

        if (totalFiles == 0) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("gui.midiplayer.select.no_files"), this.width / 2, 80, 0xFF5555);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
