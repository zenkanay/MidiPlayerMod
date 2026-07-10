package com.example.midiplayer;

/**
 * 解析されたMIDIイベントを表すクラス。
 * スケジューリングが容易になるよう、絶対時間（ミリ秒）を保持します。
 */
public class ParsedEvent implements Comparable<ParsedEvent> {
    public enum Type {
        NOTE_ON,
        NOTE_OFF,
        PROGRAM_CHANGE
    }

    public final Type type;
    public final long timeMs;    // 絶対時間 (ミリ秒)
    public final int channel;   // MIDIチャンネル (0-15)
    public final int note;      // MIDIノート番号 (0-127)
    public final int velocity;  // 音の強さ (0-127, NOTE_ONのみ)
    public final int program;   // プログラム（楽器）番号 (0-127, PROGRAM_CHANGEのみ)

    private ParsedEvent(Type type, long timeMs, int channel, int note, int velocity, int program) {
        this.type = type;
        this.timeMs = timeMs;
        this.channel = channel;
        this.note = note;
        this.velocity = velocity;
        this.program = program;
    }

    public static ParsedEvent createNoteOn(long timeMs, int channel, int note, int velocity) {
        return new ParsedEvent(Type.NOTE_ON, timeMs, channel, note, velocity, 0);
    }

    public static ParsedEvent createNoteOff(long timeMs, int channel, int note) {
        return new ParsedEvent(Type.NOTE_OFF, timeMs, channel, note, 0, 0);
    }

    public static ParsedEvent createProgramChange(long timeMs, int channel, int program) {
        return new ParsedEvent(Type.PROGRAM_CHANGE, timeMs, channel, 0, 0, program);
    }

    @Override
    public int compareTo(ParsedEvent o) {
        if (this.timeMs != o.timeMs) {
            return Long.compare(this.timeMs, o.timeMs);
        }
        // 同時刻の場合は、プログラムチェンジを優先して先に処理する
        if (this.type == Type.PROGRAM_CHANGE && o.type != Type.PROGRAM_CHANGE) {
            return -1;
        }
        if (o.type == Type.PROGRAM_CHANGE && this.type != Type.PROGRAM_CHANGE) {
            return 1;
        }
        return 0;
    }

    @Override
    public String toString() {
        return "ParsedEvent{" +
                "type=" + type +
                ", timeMs=" + timeMs +
                ", channel=" + channel +
                ", note=" + note +
                ", velocity=" + velocity +
                ", program=" + program +
                '}';
    }
}
