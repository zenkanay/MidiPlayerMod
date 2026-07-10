package com.example.midiplayer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.File;
import java.util.List;

/**
 * クライアントサイドMODのエントリポイントクラス。
 * MODの初期化、キーバインドの登録、自動演奏インスタンスの管理を行います。
 */
public class ClientMod implements ClientModInitializer {
    private static ClientMod instance;
    
    private final NoteMapper noteMapper = new NoteMapper();
    private final InstrumentFilter instrumentFilter = new InstrumentFilter();
    private AutoPlayer autoPlayer;
    private Config config = new Config();
    
    private File midiDir;

    @Override
    public void onInitializeClient() {
        instance = this;
        
        autoPlayer = new AutoPlayer(noteMapper, instrumentFilter);
        
        // 設定フォルダ (config/midiplayer/) の作成
        MinecraftClient client = MinecraftClient.getInstance();
        File gameDir = client.runDirectory;
        midiDir = new File(gameDir, "config/midiplayer");
        if (!midiDir.exists()) {
            midiDir.mkdirs();
        }

        // 設定の読み込み (現在は仮実装)
        config.load();
        
        // 設定値を各モジュールに反映
        noteMapper.setOctaveOffset(config.octaveOffset);
        instrumentFilter.setActiveInstruments(config.activeInstruments);

        // キーバインドの登録 (Mキー)
        KeyBindings.register(this);


        
        System.out.println("MIDI Note Block Player Initialized!");
    }

    /**
     * MODのシングルトンインスタンスを取得します。
     */
    public static ClientMod getInstance() {
        return instance;
    }

    public Config getConfig() {
        return config;
    }

    public AutoPlayer getAutoPlayer() {
        return autoPlayer;
    }

    public NoteMapper getNoteMapper() {
        return noteMapper;
    }

    public InstrumentFilter getInstrumentFilter() {
        return instrumentFilter;
    }

    public File getMidiDir() {
        return midiDir;
    }

    /**
     * 再生と停止を切り替えます。
     * ホットキー入力などから呼び出されます。
     */
    public void togglePlayback() {
        if (autoPlayer.isPlaying()) {
            autoPlayer.stopPlay();
        } else {
            // MIDIファイルが指定されているか確認
            if (config.midiFileName == null || config.midiFileName.isEmpty()) {
                sendSystemMessage(Text.translatable("msg.midiplayer.no_file"));
                return;
            }

            File midiFile = new File(midiDir, config.midiFileName);
            if (!midiFile.exists()) {
                sendSystemMessage(Text.translatable("msg.midiplayer.file_not_found", config.midiFileName));
                return;
            }

            // 設定の最新状態を同期
            noteMapper.setOctaveOffset(config.octaveOffset);
            instrumentFilter.setActiveInstruments(config.activeInstruments);

            // MIDIファイルを解析して演奏を開始
            try {
                List<ParsedEvent> events = MidiParser.parse(midiFile);
                autoPlayer.startPlay(events);
            } catch (Exception e) {
                sendSystemMessage(Text.translatable("msg.midiplayer.parse_failed", e.getMessage()));
                e.printStackTrace();
            }
        }
    }

    private void sendSystemMessage(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(text, false);
        }
    }
}
