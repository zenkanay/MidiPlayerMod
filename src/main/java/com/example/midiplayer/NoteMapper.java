package com.example.midiplayer;

/**
 * MIDIノート番号をMinecraftの音符ブロック（25音：0〜24）へマッピングするクラス。
 * 範囲外の音程はオクターブ単位で自動補正され、GUIから設定されたオクターブ補正値も加味します。
 */
public class NoteMapper {
    private int octaveOffset = 0; // GUIから変更可能なオクターブ補正値

    /**
     * オクターブ補正値を設定します。
     *
     * @param offset 補正するオクターブ数（正負の整数値）
     */
    public void setOctaveOffset(int offset) {
        this.octaveOffset = offset;
    }

    /**
     * 現在のオクターブ補正値を取得します。
     *
     * @return オクターブ補正値
     */
    public int getOctaveOffset() {
        return octaveOffset;
    }

    /**
     * MIDIノート番号 (0-127) を Minecraftの音符ブロックのピッチ値 (0-24) に変換します。
     * 基準音は F#3 (MIDI 54) でピッチ値 0、F#5 (MIDI 78) でピッチ値 24 となります。
     * 範囲外の場合はオクターブ単位で範囲内に収まるよう補正します。
     *
     * @param midiNote MIDIノート番号
     * @return Minecraftの音符ブロックのピッチ値 (0-24)
     */
    public int mapToNoteBlockPitch(int midiNote) {
        // ユーザー設定のオクターブ補正を適用
        int shiftedNote = midiNote + (octaveOffset * 12);
        
        // F#3 (MIDIノート 54) を 0 とした時の半音差（ピッチ値）
        int pitch = shiftedNote - 54;
        
        // 0〜24 の範囲に収まるようにオクターブ（12半音）単位で補正する
        if (pitch < 0) {
            // 0以上にするための必要オクターブ数を計算して加算
            int octavesToAdd = (-pitch + 11) / 12;
            pitch += octavesToAdd * 12;
        } else if (pitch > 24) {
            // 24以下にするための必要オクターブ数を計算して減算
            int octavesToSub = (pitch - 13) / 12;
            pitch -= octavesToSub * 12;
        }
        
        // 万が一のクランプ
        if (pitch < 0) {
            pitch = 0;
        } else if (pitch > 24) {
            pitch = 24;
        }
        
        return pitch;
    }
}
