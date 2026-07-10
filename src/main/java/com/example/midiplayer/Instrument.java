package com.example.midiplayer;

/**
 * サポートする楽器の種類（General MIDIの16カテゴリ ＋ ドラム）と、
 * それぞれのMIDIプログラム番号の範囲を定義する列挙型。
 * 多言語対応のため、表示名は言語ファイル（translationKey）を介して取得します。
 */
public enum Instrument {
    PIANO("instrument.midiplayer.piano", 0, 7),
    CHROMATIC("instrument.midiplayer.chromatic", 8, 15),
    ORGAN("instrument.midiplayer.organ", 16, 23),
    GUITAR("instrument.midiplayer.guitar", 24, 31),
    BASS("instrument.midiplayer.bass", 32, 39),
    STRINGS("instrument.midiplayer.strings", 40, 47),
    ENSEMBLE("instrument.midiplayer.ensemble", 48, 55),
    BRASS("instrument.midiplayer.brass", 56, 63),
    REED("instrument.midiplayer.reed", 64, 71),
    PIPE("instrument.midiplayer.pipe", 72, 79),
    SYNTH_LEAD("instrument.midiplayer.synth_lead", 80, 87),
    SYNTH_PAD("instrument.midiplayer.synth_pad", 88, 95),
    SYNTH_SFX("instrument.midiplayer.synth_sfx", 96, 103),
    ETHNIC("instrument.midiplayer.ethnic", 104, 111),
    PERCUSSIVE("instrument.midiplayer.percussive", 112, 119),
    SFX("instrument.midiplayer.sfx", 120, 127),
    DRUMS("instrument.midiplayer.drums", -1, -1);

    public final String translationKey;
    public final int startProg;
    public final int endProg;

    Instrument(String translationKey, int startProg, int endProg) {
        this.translationKey = translationKey;
        this.startProg = startProg;
        this.endProg = endProg;
    }

    /**
     * 指定されたMIDIプログラム（楽器）番号が、このカテゴリに該当するか判定します。
     *
     * @param program MIDIプログラム番号 (0-127)
     * @return 該当する場合は true
     */
    public boolean matches(int program) {
        // ドラムの場合はプログラム番号ではマッチしない（チャンネル判定のため）
        if (this == DRUMS) {
            return false;
        }
        return program >= startProg && program <= endProg;
    }
}
