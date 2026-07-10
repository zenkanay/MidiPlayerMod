package com.example.midiplayer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 選択された複数の楽器以外の演奏イベントを無視するためのフィルタクラス。
 * MIDIの各チャンネルが現在どのプログラム番号に設定されているかを監視・追跡します。
 */
public class InstrumentFilter {
    private final int[] channelPrograms = new int[16];
    private final Set<Instrument> activeInstruments = new HashSet<>();

    public InstrumentFilter() {
        // 初期化: MIDIのデフォルトプログラムは0 (Acoustic Grand Piano)
        reset();
    }

    /**
     * フィルタリングの対象とするアクティブな楽器セットを設定します。
     *
     * @param instruments 選択された楽器のセット
     */
    public void setActiveInstruments(Set<Instrument> instruments) {
        this.activeInstruments.clear();
        if (instruments != null) {
            this.activeInstruments.addAll(instruments);
        }
    }

    /**
     * アクティブな楽器セットを取得します。
     *
     * @return 現在アクティブな楽器のセット
     */
    public Set<Instrument> getActiveInstruments() {
        return activeInstruments;
    }

    /**
     * 従来の互換用メソッド。
     */
    public void setActiveInstrument(Instrument instrument) {
        this.activeInstruments.clear();
        if (instrument != null) {
            this.activeInstruments.add(instrument);
        }
    }

    /**
     * 指定されたイベントが現在どのMIDI楽器（Instrumentカテゴリ）に該当するかを特定します。
     */
    public Instrument getEventInstrument(ParsedEvent event) {
        if (event.channel == 9) {
            return Instrument.DRUMS;
        }
        int program = channelPrograms[event.channel];
        for (Instrument inst : Instrument.values()) {
            if (inst.matches(program)) {
                return inst;
            }
        }
        return Instrument.PIANO; // デフォルトフォールバック
    }

    /**
     * MIDIイベントに基づいてチャンネルプログラムを更新し、
     * ノートイベントを通過させるかどうか（演奏すべきか）を判定します。
     *
     * @param event 判定対象 of ParsedEvent
     * @return ノートオン/オフが選択された楽器と一致して演奏すべき場合は true
     */
    public boolean processAndFilter(ParsedEvent event) {
        if (event.type == ParsedEvent.Type.PROGRAM_CHANGE) {
            if (event.channel >= 0 && event.channel < 16) {
                channelPrograms[event.channel] = event.program;
            }
            return false; // プログラムチェンジイベント自体は演奏音を出さない
        }

        if (event.type == ParsedEvent.Type.NOTE_ON || event.type == ParsedEvent.Type.NOTE_OFF) {
            // ドラム（パーカッション）専用チャンネル10 (0-indexedでは 9)
            if (event.channel == 9) {
                return activeInstruments.contains(Instrument.DRUMS);
            }

            int program = channelPrograms[event.channel];
            
            // 現在選択されている楽器群の中に、このプログラム番号に適合するものがあるかチェック
            for (Instrument inst : activeInstruments) {
                if (inst.matches(program)) {
                    return true;
                }
            }
            return false;
        }

        return false;
    }

    /**
     * チャンネルプログラムを初期状態 (0: Piano) にリセットします。
     */
    public void reset() {
        Arrays.fill(channelPrograms, 0);
    }
}
