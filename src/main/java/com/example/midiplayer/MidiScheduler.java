package com.example.midiplayer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MIDIイベントをミリ秒精度でタイミングよくトリガーするスケジューラクラス。
 * 任意の位置（ミリ秒）からのシーク再生をサポートします。
 */
public class MidiScheduler {

    /**
     * イベントトリガーおよび演奏完了時のコールバックインターフェース。
     */
    public interface EventListener {
        void onEvent(ParsedEvent event);
        void onFinished();
    }

    private final List<ParsedEvent> events;
    private final EventListener listener;
    private Thread playThread;
    
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    
    private long startTimeMs = 0;
    private long accumulatedPauseTimeMs = 0;
    private long pauseStartMs = 0;
    
    private long startOffsetMs = 0; // シーク時のオフセット

    /**
     * MidiSchedulerのコンストラクタ。
     *
     * @param events   パース済みのMIDIイベントリスト
     * @param listener イベント発生時に通知を受けるリスナー
     */
    public MidiScheduler(List<ParsedEvent> events, EventListener listener) {
        this.events = events;
        this.listener = listener;
    }

    /**
     * 最初から演奏を開始します。
     */
    public synchronized void start() {
        start(0);
    }

    /**
     * 指定されたミリ秒オフセット位置から演奏を開始します。
     *
     * @param startOffsetMs 開始位置（ミリ秒）
     */
    public synchronized void start(long startOffsetMs) {
        if (isPlaying.get()) {
            return;
        }
        this.startOffsetMs = startOffsetMs;
        isPlaying.set(true);
        isPaused.set(false);
        accumulatedPauseTimeMs = 0;
        
        playThread = new Thread(this::playLoop, "MidiPlayer-SchedulerThread");
        playThread.setDaemon(true);
        playThread.start();
    }

    /**
     * 演奏を停止します。
     */
    public synchronized void stop() {
        isPlaying.set(false);
        isPaused.set(false);
        if (playThread != null) {
            playThread.interrupt();
            playThread = null;
        }
    }

    /**
     * 演奏を一時停止します。
     */
    public synchronized void pause() {
        if (isPlaying.get() && !isPaused.get()) {
            isPaused.set(true);
            pauseStartMs = System.currentTimeMillis();
        }
    }

    /**
     * 一時停止から再開します。
     */
    public synchronized void resume() {
        if (isPlaying.get() && isPaused.get()) {
            accumulatedPauseTimeMs += (System.currentTimeMillis() - pauseStartMs);
            isPaused.set(false);
            notifyAll(); // スレッド待機を解除
        }
    }

    /**
     * 再生中（一時停止中でない）かどうかを取得します。
     */
    public boolean isPlaying() {
        return isPlaying.get() && !isPaused.get();
    }

    /**
     * 再生スレッドまたは一時停止中も含めて、アクティブであるかを取得します。
     */
    public boolean isActive() {
        return isPlaying.get();
    }

    /**
     * 現在の一時停止状態を取得します。
     */
    public boolean isPaused() {
        return isPaused.get();
    }

    /**
     * 現在の再生位置（ミリ秒）を取得します。
     */
    public long getCurrentTimeMs() {
        if (!isPlaying.get()) {
            return startOffsetMs; // 再生していない時は設定されている開始オフセットを返す
        }
        if (isPaused.get()) {
            return pauseStartMs - startTimeMs - accumulatedPauseTimeMs;
        }
        return System.currentTimeMillis() - startTimeMs - accumulatedPauseTimeMs;
    }

    /**
     * 曲の総再生時間（ミリ秒）を取得します。
     */
    public long getTotalTimeMs() {
        if (events == null || events.isEmpty()) {
            return 0;
        }
        return events.get(events.size() - 1).timeMs;
    }

    /**
     * 再生スレッドのメインループ。
     */
    private void playLoop() {
        // 現在時刻からオフセットを引いて開始時間を擬似調整
        startTimeMs = System.currentTimeMillis() - startOffsetMs;
        
        // オフセット位置に相当するイベントのインデックスを探す
        int eventIndex = 0;
        while (eventIndex < events.size() && events.get(eventIndex).timeMs < startOffsetMs) {
            eventIndex++;
        }

        while (isPlaying.get() && eventIndex < events.size()) {
            // 一時停止時の待機処理
            if (isPaused.get()) {
                synchronized (this) {
                    while (isPaused.get() && isPlaying.get()) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            return; // スレッド中断による終了
                        }
                    }
                }
            }

            if (!isPlaying.get() || eventIndex >= events.size()) {
                break;
            }

            ParsedEvent event = events.get(eventIndex);
            long now = System.currentTimeMillis();
            long elapsed = now - startTimeMs - accumulatedPauseTimeMs;
            long delay = event.timeMs - elapsed;

            if (delay > 0) {
                try {
                    if (delay > 10) {
                        // 残り時間が10msより多い場合は大雑把にスリープしてCPU負荷を下げる
                        // スリープから目覚めた後に微調整するため、少し短めにスリープする (delay - 5ms)
                        Thread.sleep(Math.min(delay - 5, 10));
                    } else {
                        // 残り時間が10ms以下になったら、極限のミリ秒精度を出すために高精度スピン待機を実行
                        while (System.currentTimeMillis() - startTimeMs - accumulatedPauseTimeMs < event.timeMs) {
                            if (!isPlaying.get() || isPaused.get() || Thread.currentThread().isInterrupted()) {
                                break;
                            }
                            Thread.onSpinWait(); // CPUにウェイトループであることを伝える最適化ヒント
                        }
                    }
                } catch (InterruptedException e) {
                    return; // スレッド中断による終了
                }
            }

            // 同時刻（誤差1ms以内）のイベントをまとめてディスパッチし、和音のズレを完全に解消する
            while (eventIndex < events.size() && isPlaying.get() && !isPaused.get()) {
                ParsedEvent nextEvent = events.get(eventIndex);
                long currentElapsed = System.currentTimeMillis() - startTimeMs - accumulatedPauseTimeMs;
                
                if (currentElapsed >= nextEvent.timeMs) {
                    try {
                        listener.onEvent(nextEvent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    eventIndex++;
                } else {
                    break;
                }
            }
        }
        
        // 再生が終了した場合は初期位置に戻す
        boolean finishedNaturally = false;
        if (eventIndex >= events.size()) {
            startOffsetMs = 0;
            finishedNaturally = true;
        }
        isPlaying.set(false);
        isPaused.set(false);

        // 自然に再生が完了した場合はリスナーに完了イベントを通知
        if (finishedNaturally) {
            try {
                listener.onFinished();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
