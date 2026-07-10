package com.example.midiplayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * MOD設定データをJSON形式でファイルに保存・読み込みするクラス。
 * 選択されている複数の演奏楽器（Set）や、オクターブ、演奏角度などを保持します。
 */
public class Config {
    public String midiFileName = "";
    
    // 演奏が有効な楽器（複数選択）
    public Set<Instrument> activeInstruments = new HashSet<>();
    public int octaveOffset = 0;
    
    // 再生中に左/右クリック入力を無効化するかどうか
    public boolean blockClickDuringPlay = true;

    // 曲をループ再生するかどうか
    public boolean loopPlayback = false;

    // 各音符ブロック音程 (0-24) に対応するプレイヤーのYawとPitch (互換性のために保持し、JSONからは除外)
    public transient float[] yawMap = new float[25];
    public transient float[] pitchMap = new float[25];

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Config() {
        // デフォルトではすべての楽器を選択状態にする (すべて選択されているのが理想)
        activeInstruments.addAll(Arrays.asList(Instrument.values()));

        // デフォルトの演奏角度（Yawは15度ずつ回転、Pitchは水平）
        for (int i = 0; i < 25; i++) {
            yawMap[i] = i * 15.0f;
            pitchMap[i] = 0.0f;
        }
    }

    private File getConfigFile() {
        File configDir = ClientMod.getInstance().getMidiDir(); // config/midiplayer/
        return new File(configDir, "config.json");
    }

    /**
     * 設定データを config.json から読み込みます。
     * ファイルが存在しない場合は、初期状態でファイルを保存します。
     */
    public void load() {
        File file = getConfigFile();
        if (!file.exists()) {
            save(); // デフォルト設定で新規作成
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Config loaded = GSON.fromJson(reader, Config.class);
            if (loaded != null) {
                this.midiFileName = loaded.midiFileName != null ? loaded.midiFileName : "";
                
                // 複数選択リストの取得。NULLまたは空なら全選択で補完
                if (loaded.activeInstruments != null && !loaded.activeInstruments.isEmpty()) {
                    this.activeInstruments = loaded.activeInstruments;
                } else {
                    this.activeInstruments = new HashSet<>(Arrays.asList(Instrument.values()));
                }
                
                this.octaveOffset = loaded.octaveOffset;
                this.blockClickDuringPlay = loaded.blockClickDuringPlay;
                this.loopPlayback = loaded.loopPlayback;

                // 読み込んだ配列が有効であるかチェックし、足りなければデフォルトで補完
                if (loaded.yawMap != null && loaded.yawMap.length == 25) {
                    this.yawMap = loaded.yawMap;
                }
                if (loaded.pitchMap != null && loaded.pitchMap.length == 25) {
                    this.pitchMap = loaded.pitchMap;
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load midiplayer config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 設定データを config.json に書き込み保存します。
     */
    public void save() {
        File file = getConfigFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            System.err.println("Failed to save midiplayer config: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
