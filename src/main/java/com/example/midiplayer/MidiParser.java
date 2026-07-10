package com.example.midiplayer;

import javax.sound.midi.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MIDIファイルを読み込み、テンポ変更を考慮して
 * 絶対時間（ミリ秒）ベースのParsedEventリストに変換するパーサークラス。
 */
public class MidiParser {

    /**
     * MIDIファイルから演奏イベントのリストをパースします。
     *
     * @param file 解析対象のMIDIファイル
     * @return 時間順にソートされたParsedEventのリスト
     * @throws InvalidMidiDataException MIDIデータが不正な場合
     * @throws IOException ファイルの読み込みに失敗した場合
     */
    public static List<ParsedEvent> parse(File file) throws InvalidMidiDataException, IOException {
        Sequence sequence = MidiSystem.getSequence(file);
        double divisionType = sequence.getDivisionType();

        // PPQ (Pulses Per Quarter note) 以外のタイミング方式は本実装ではサポート外とするか、
        // 簡易的にPPQとして扱います。（通常のMIDIファイルはほぼPPQです）
        if (divisionType != Sequence.PPQ) {
            throw new InvalidMidiDataException("Unsupported division type: " + divisionType + ". Only PPQ is supported.");
        }

        int ppq = sequence.getResolution();
        List<RawMidiEvent> rawEvents = new ArrayList<>();

        // すべてのトラックからイベントを収集
        Track[] tracks = sequence.getTracks();
        for (Track track : tracks) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                rawEvents.add(new RawMidiEvent(event.getTick(), event.getMessage()));
            }
        }

        // ティック順にソート
        Collections.sort(rawEvents);

        List<ParsedEvent> parsedEvents = new ArrayList<>();
        
        // テンポ追跡のための変数 (初期値は 120 BPM = 500,000 マイクロ秒/四分音符)
        long currentMpqn = 500000; 
        long prevTick = 0;
        double currentTimeUs = 0;

        for (RawMidiEvent raw : rawEvents) {
            long currentTick = raw.tick;
            long deltaTick = currentTick - prevTick;

            if (deltaTick > 0) {
                // 1ティックあたりのマイクロ秒数 = currentMpqn / ppq
                double tickDurationUs = (double) currentMpqn / ppq;
                currentTimeUs += deltaTick * tickDurationUs;
            }
            prevTick = currentTick;

            MidiMessage message = raw.message;

            if (message instanceof MetaMessage meta) {
                // テンポ変更イベント (Type: 0x51)
                if (meta.getType() == 0x51) {
                    byte[] data = meta.getData();
                    if (data.length >= 3) {
                        currentMpqn = ((long) (data[0] & 0xFF) << 16)
                                    | ((long) (data[1] & 0xFF) << 8)
                                    | (long) (data[2] & 0xFF);
                    }
                }
            } else if (message instanceof ShortMessage shortMsg) {
                int channel = shortMsg.getChannel();
                int command = shortMsg.getCommand();
                long timeMs = Math.round(currentTimeUs / 1000.0);

                if (command == ShortMessage.NOTE_ON) {
                    int note = shortMsg.getData1();
                    int velocity = shortMsg.getData2();
                    if (velocity > 0) {
                        parsedEvents.add(ParsedEvent.createNoteOn(timeMs, channel, note, velocity));
                    } else {
                        // Velocityが0のNote OnはNote Offとみなす
                        parsedEvents.add(ParsedEvent.createNoteOff(timeMs, channel, note));
                    }
                } else if (command == ShortMessage.NOTE_OFF) {
                    int note = shortMsg.getData1();
                    parsedEvents.add(ParsedEvent.createNoteOff(timeMs, channel, note));
                } else if (command == ShortMessage.PROGRAM_CHANGE) {
                    int program = shortMsg.getData1();
                    parsedEvents.add(ParsedEvent.createProgramChange(timeMs, channel, program));
                }
            }
        }

        // 解析したイベントを時間順に再ソート
        Collections.sort(parsedEvents);
        return parsedEvents;
    }

    /**
     * ソート用のテンポラリMIDIイベントクラス。
     */
    private static class RawMidiEvent implements Comparable<RawMidiEvent> {
        final long tick;
        final MidiMessage message;

        RawMidiEvent(long tick, MidiMessage message) {
            this.tick = tick;
            this.message = message;
        }

        @Override
        public int compareTo(RawMidiEvent o) {
            return Long.compare(this.tick, o.tick);
        }
    }
}
