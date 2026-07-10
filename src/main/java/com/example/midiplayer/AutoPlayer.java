package com.example.midiplayer;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.entity.Entity;

import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MIDIイベントの自動演奏を担当するクラス。
 * 周辺の音符ブロックの音色（NoteBlockInstrument）を動的に検出し、
 * 演奏するMIDI楽器カテゴリに最も適したブロックをインテリジェントに選択して演奏します。
 */
public class AutoPlayer {
    private final NoteMapper noteMapper;
    private final InstrumentFilter instrumentFilter;
    private MidiScheduler scheduler;
    private List<ParsedEvent> currentEvents;
    private long pausedTimeMs = 0; // 一時停止時の位置（ミリ秒）を保持
    
    // ピッチ (0-24) ごとに、該当するブロック座標のリストをキャッシュ
    private final Map<Integer, List<NoteBlockInfo>> noteBlockCache = new HashMap<>();
    private BlockPos lastScanPos = null;

    public AutoPlayer(NoteMapper noteMapper, InstrumentFilter instrumentFilter) {
        this.noteMapper = noteMapper;
        this.instrumentFilter = instrumentFilter;
    }

    public boolean isPlaying() {
        return scheduler != null && scheduler.isPlaying();
    }

    public boolean isActive() {
        return scheduler != null && scheduler.isActive();
    }

    public MidiScheduler getScheduler() {
        return scheduler;
    }

    public List<ParsedEvent> getCurrentEvents() {
        return currentEvents;
    }

    /**
     * 自動演奏を開始します。
     *
     * @param events     解析済みのMIDIイベントリスト
     */
    public void startPlay(List<ParsedEvent> events) {
        startPlay(events, 0);
    }

    /**
     * 指定されたミリ秒位置から演奏を開始します。
     *
     * @param events     解析済みのMIDIイベントリスト
     * @param startTimeMs 開始するミリ秒位置
     */
    public void startPlay(List<ParsedEvent> events, long startTimeMs) {
        if (events == null || events.isEmpty()) return;
        stopPlay();

        this.currentEvents = events;
        this.pausedTimeMs = 0;
        
        // 演奏開始前に周辺ブロックを強制的に再スキャンしてキャッシュを初期化
        scanNearbyNoteBlocks();

        scheduler = new MidiScheduler(events, new MidiScheduler.EventListener() {
            @Override
            public void onEvent(ParsedEvent event) {
                onMidiEvent(event);
            }

            @Override
            public void onFinished() {
                onPlaybackFinished();
            }
        });
        scheduler.start(startTimeMs);
    }

    /**
     * 演奏を一時停止し、現在の再生位置（ミリ秒）を保持します。
     *
     * @return 一時停止した時点のミリ秒位置 (演奏中でない場合は0)
     */
    public long pausePlay() {
        if (scheduler != null && scheduler.isActive()) {
            this.pausedTimeMs = scheduler.getCurrentTimeMs();
            scheduler.stop();
            return this.pausedTimeMs;
        }
        return 0;
    }

    /**
     * 一時停止されたミリ秒位置から演奏を再開します。
     */
    public void resumePlay() {
        if (currentEvents != null && !currentEvents.isEmpty()) {
            // 再開時にも周辺ブロックをスキャンして現在のプレイヤー座標に合わせてキャッシュを再ロード
            scanNearbyNoteBlocks();
            
            scheduler = new MidiScheduler(currentEvents, new MidiScheduler.EventListener() {
                @Override
                public void onEvent(ParsedEvent event) {
                    onMidiEvent(event);
                }

                @Override
                public void onFinished() {
                    onPlaybackFinished();
                }
            });
            scheduler.start(this.pausedTimeMs);
        }
    }

    /**
     * 自動演奏を完全に停止します。
     */
    public void stopPlay() {
        if (scheduler != null) {
            scheduler.stop();
            scheduler = null;
        }
        this.pausedTimeMs = 0;
    }

    /**
     * プレイヤーの周囲（半径5ブロック以内）にある音符ブロックをスキャンし、
     * ピッチ（0〜24）ごとに座標と音色のキャッシュ情報を再構築します。
     */
    public void scanNearbyNoteBlocks() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        // メインスレッド上でのスキャン処理
        Runnable scanTask = () -> {
            ClientPlayerEntity player = client.player;
            if (player == null || client.world == null) return;

            noteBlockCache.clear();
            
            BlockPos playerPos = player.getBlockPos();
            this.lastScanPos = playerPos; // 最後にスキャンした座標を記録
            int radius = 5;

            // 半径5ブロック以内の直方体領域を検索
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        var state = client.world.getBlockState(pos);
                        if (state.isOf(Blocks.NOTE_BLOCK)) {
                            // 物理的に触れる（遮蔽物やモブが邪魔していない）ブロックのみスキャン対象にする
                            if (isBlockInteractable(player, pos)) {
                                int pitch = state.get(net.minecraft.block.NoteBlock.NOTE);
                                net.minecraft.block.enums.NoteBlockInstrument inst = state.get(net.minecraft.block.NoteBlock.INSTRUMENT);
                                noteBlockCache.computeIfAbsent(pitch, k -> new ArrayList<>()).add(new NoteBlockInfo(pos, inst));
                            }
                        }
                    }
                }
            }
            
            int totalCached = noteBlockCache.values().stream().mapToInt(List::size).sum();
            System.out.println("Scanned and cached " + totalCached + " note blocks nearby.");
        };

        if (client.isOnThread()) {
            scanTask.run();
        } else {
            try {
                client.submit(scanTask).join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private BlockPos findBestBlock(int pitch, Instrument midiInst, ClientPlayerEntity player) {
        if (lastScanPos == null || player.getBlockPos().getManhattanDistance(lastScanPos) > 2) {
            scanNearbyNoteBlocks();
        }

        List<NoteBlockInfo> list = noteBlockCache.get(pitch);
        if (list == null || list.isEmpty()) {
            return null;
        }

        net.minecraft.block.enums.NoteBlockInstrument[] preferred = getPreferredInstruments(midiInst);
        
        // 最大インタラクションリーチ（4.5マス）の平方値
        double maxReachSq = 4.5 * 4.5;

        // 1. 優先順位リスト順に、周囲に存在するブロックを走査し、手が届く範囲（4.5m以内）で最も近いものを返します。
        for (net.minecraft.block.enums.NoteBlockInstrument prefInst : preferred) {
            BlockPos closest = null;
            double minDistanceSq = Double.MAX_VALUE;
            
            for (NoteBlockInfo info : list) {
                if (info.instrument == prefInst) {
                    double distSq = getDistanceSq(info.pos, player);
                    if (distSq <= maxReachSq && distSq < minDistanceSq) {
                        minDistanceSq = distSq;
                        closest = info.pos;
                    }
                }
            }
            if (closest != null) {
                return closest;
            }
        }

        // 2. 優先リスト外の場合の厳格なカテゴリフォールバック制御 (ハープでの代用)
        if (midiInst != Instrument.DRUMS && midiInst != Instrument.BASS && midiInst != Instrument.PERCUSSIVE && midiInst != Instrument.SFX) {
            BlockPos closestHarp = null;
            double minHarpDistSq = Double.MAX_VALUE;
            for (NoteBlockInfo info : list) {
                if (info.instrument == net.minecraft.block.enums.NoteBlockInstrument.HARP) {
                    double distSq = getDistanceSq(info.pos, player);
                    if (distSq <= maxReachSq && distSq < minHarpDistSq) {
                        minHarpDistSq = distSq;
                        closestHarp = info.pos;
                    }
                }
            }
            if (closestHarp != null) {
                return closestHarp;
            }
        }

        return null;
    }

    private double getDistanceSq(BlockPos pos, ClientPlayerEntity player) {
        Vec3d eyePos = player.getEyePos();
        double minX = pos.getX();
        double maxX = pos.getX() + 1.0;
        double minY = pos.getY();
        double maxY = pos.getY() + 1.0;
        double minZ = pos.getZ();
        double maxZ = pos.getZ() + 1.0;

        double closestX = Math.max(minX, Math.min(eyePos.x, maxX));
        double closestY = Math.max(minY, Math.min(eyePos.y, maxY));
        double closestZ = Math.max(minZ, Math.min(eyePos.z, maxZ));

        double dx = eyePos.x - closestX;
        double dy = eyePos.y - closestY;
        double dz = eyePos.z - closestZ;

        return dx * dx + dy * dy + dz * dz;
    }

    private static net.minecraft.block.enums.NoteBlockInstrument[] getPreferredInstruments(Instrument midiInst) {
        switch (midiInst) {
            case PIANO:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.HARP,
                    net.minecraft.block.enums.NoteBlockInstrument.PLING
                };
            case CHROMATIC:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.XYLOPHONE,
                    net.minecraft.block.enums.NoteBlockInstrument.IRON_XYLOPHONE,
                    net.minecraft.block.enums.NoteBlockInstrument.CHIME,
                    net.minecraft.block.enums.NoteBlockInstrument.BELL,
                    net.minecraft.block.enums.NoteBlockInstrument.HARP
                };
            case ORGAN:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.PLING,
                    net.minecraft.block.enums.NoteBlockInstrument.BIT,
                    net.minecraft.block.enums.NoteBlockInstrument.HARP
                };
            case GUITAR:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.GUITAR,
                    net.minecraft.block.enums.NoteBlockInstrument.BANJO,
                    net.minecraft.block.enums.NoteBlockInstrument.HARP
                };
            case BASS:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.BASS,
                    net.minecraft.block.enums.NoteBlockInstrument.DIDGERIDOO,
                    net.minecraft.block.enums.NoteBlockInstrument.HARP
                };
            case STRINGS:
            case ENSEMBLE:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.HARP,
                    net.minecraft.block.enums.NoteBlockInstrument.PLING
                };
            case BRASS:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.FLUTE,
                    net.minecraft.block.enums.NoteBlockInstrument.DIDGERIDOO,
                    net.minecraft.block.enums.NoteBlockInstrument.HARP
                };
            case REED:
            case PIPE:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.FLUTE,
                    net.minecraft.block.enums.NoteBlockInstrument.HARP
                };
            case SYNTH_LEAD:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.BIT,
                    net.minecraft.block.enums.NoteBlockInstrument.PLING,
                    net.minecraft.block.enums.NoteBlockInstrument.HARP
                };
            case SYNTH_PAD:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.PLING,
                    net.minecraft.block.enums.NoteBlockInstrument.BIT,
                    net.minecraft.block.enums.NoteBlockInstrument.HARP
                };
            case SYNTH_SFX:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.BIT,
                    net.minecraft.block.enums.NoteBlockInstrument.HARP
                };
            case ETHNIC:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.BANJO,
                    net.minecraft.block.enums.NoteBlockInstrument.DIDGERIDOO,
                    net.minecraft.block.enums.NoteBlockInstrument.HARP
                };
            case PERCUSSIVE:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.COW_BELL,
                    net.minecraft.block.enums.NoteBlockInstrument.BASEDRUM,
                    net.minecraft.block.enums.NoteBlockInstrument.SNARE,
                    net.minecraft.block.enums.NoteBlockInstrument.HAT,
                    net.minecraft.block.enums.NoteBlockInstrument.HARP
                };
            case SFX:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.HAT,
                    net.minecraft.block.enums.NoteBlockInstrument.SNARE,
                    net.minecraft.block.enums.NoteBlockInstrument.HARP
                };
            case DRUMS:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.BASEDRUM,
                    net.minecraft.block.enums.NoteBlockInstrument.SNARE,
                    net.minecraft.block.enums.NoteBlockInstrument.HAT,
                    net.minecraft.block.enums.NoteBlockInstrument.COW_BELL,
                    net.minecraft.block.enums.NoteBlockInstrument.HARP
                };
            default:
                return new net.minecraft.block.enums.NoteBlockInstrument[]{
                    net.minecraft.block.enums.NoteBlockInstrument.HARP
                };
        }
    }

    private float[] calculateAngle(BlockPos targetPos, ClientPlayerEntity player) {
        Vec3d eyePos = player.getEyePos();
        double dx = targetPos.getX() + 0.5 - eyePos.x;
        double dy = targetPos.getY() + 0.5 - eyePos.y;
        double dz = targetPos.getZ() + 0.5 - eyePos.z;

        double distanceXZ = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distanceXZ));

        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.wrapDegrees(pitch);

        return new float[]{yaw, pitch};
    }

    private void onMidiEvent(ParsedEvent event) {
        if (!instrumentFilter.processAndFilter(event)) {
            return;
        }

        if (event.type == ParsedEvent.Type.NOTE_ON) {
            int pitch = noteMapper.mapToNoteBlockPitch(event.note);

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return;

            ClientPlayerEntity player = client.player;
            if (player == null || client.world == null) return;

            Instrument midiInst = instrumentFilter.getEventInstrument(event);
            BlockPos targetPos = findBestBlock(pitch, midiInst, player);
            if (targetPos == null) {
                return;
            }

            float[] angles = calculateAngle(targetPos, player);
            float precalculatedYaw = angles[0];
            float precalculatedPitch = angles[1];

            client.execute(() -> {
                if (scheduler == null || !scheduler.isPlaying()) {
                    return;
                }

                ClientPlayerEntity localPlayer = client.player;
                if (localPlayer == null || client.interactionManager == null || client.world == null) {
                    return;
                }

                var inventory = localPlayer.getInventory();
                int currentSlot = inventory.getSelectedSlot();

                if (isInstantBreak(localPlayer, targetPos, currentSlot)) {
                    sendSystemMessage(Text.translatable("msg.midiplayer.instant_break_danger"));
                    stopPlay();
                    return;
                }

                localPlayer.setYaw(precalculatedYaw);
                localPlayer.setPitch(precalculatedPitch);

                client.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(
                    net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                    targetPos,
                    Direction.UP
                ));
                localPlayer.swingHand(Hand.MAIN_HAND);
            });
        }
    }

    public float getYawForNote(int note) {
        return note * 15.0f;
    }

    public float getPitchForNote(int note) {
        Config config = ClientMod.getInstance().getConfig();
        if (note >= 0 && note < config.pitchMap.length) {
            return config.pitchMap[note];
        }
        return 0.0f;
    }

    private void sendSystemMessage(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(text, false);
        }
    }

    private void onPlaybackFinished() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        client.execute(() -> {
            Config config = ClientMod.getInstance().getConfig();
            sendSystemMessage(Text.translatable("msg.midiplayer.finished"));
            
            if (config.loopPlayback && currentEvents != null && !currentEvents.isEmpty()) {
                startPlay(currentEvents, 0);
            } else {
                scheduler = null;
                this.pausedTimeMs = 0;
            }
        });
    }

    private boolean isInstantBreak(ClientPlayerEntity player, BlockPos pos, int slotId) {
        if (player.isCreative()) {
            return true;
        }

        var inventory = player.getInventory();
        var stack = inventory.getStack(slotId);
        if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.item.AxeItem) {
            return true;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return true;
        }

        int originalSlot = inventory.getSelectedSlot();
        
        try {
            inventory.setSelectedSlot(slotId);
            var state = client.world.getBlockState(pos);
            float delta = state.calcBlockBreakingDelta(player, client.world, pos);
            return delta >= 1.0f;
        } finally {
            inventory.setSelectedSlot(originalSlot);
        }
    }

    private String getInstrumentName(net.minecraft.block.enums.NoteBlockInstrument inst) {
        switch (inst) {
            case HARP: return "ハープ (ピアノ)";
            case BASS: return "ベース";
            case BASEDRUM: return "バスドラム";
            case SNARE: return "スネアドラム";
            case HAT: return "クリック (ハイハット)";
            case BELL: return "ベル";
            case FLUTE: return "フルート";
            case CHIME: return "チャイム";
            case GUITAR: return "ギター";
            case XYLOPHONE: return "木琴 (シロフォン)";
            case IRON_XYLOPHONE: return "鉄琴";
            case COW_BELL: return "カウベル";
            case DIDGERIDOO: return "ディジュリドゥ";
            case BIT: return "ビット (電子音)";
            case BANJO: return "バンジョー";
            case PLING: return "プリンク";
            default: return inst.name();
        }
    }

    public void autoTune(List<ParsedEvent> events) {
        if (events == null || events.isEmpty()) {
            sendSystemMessage(Text.literal("§c自動調律を実行するには、事前に曲ファイルを選択してください。"));
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) {
            return;
        }

        client.execute(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null || client.world == null || client.interactionManager == null) return;

            // 1. 周辺ブロックをスキャンしてリストアップ (物理的に触れる状態のブロックのみ対象とする)
            List<TuneTarget> targets = new ArrayList<>();
            BlockPos playerPos = player.getBlockPos();
            int radius = 5;

            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        var state = client.world.getBlockState(pos);
                        if (state.isOf(Blocks.NOTE_BLOCK)) {
                            if (isBlockInteractable(player, pos)) {
                                int currentPitch = state.get(net.minecraft.block.NoteBlock.NOTE);
                                net.minecraft.block.enums.NoteBlockInstrument inst = state.get(net.minecraft.block.NoteBlock.INSTRUMENT);
                                targets.add(new TuneTarget(pos, inst, currentPitch));
                            }
                        }
                    }
                }
            }

            if (targets.isEmpty()) {
                sendSystemMessage(Text.literal("§c周囲に触れる状態の調律対象ブロックが見つかりません（最大距離 4.5m）。"));
                return;
            }

            // 2. 曲で必要な「音色カテゴリ（midiInst）とピッチ」の出現頻度をカウント
            Map<RequiredNote, Integer> reqFrequency = new HashMap<>();
            
            instrumentFilter.reset(); // 解析用に一時リセット
            for (ParsedEvent event : events) {
                if (event.type == ParsedEvent.Type.NOTE_ON) {
                    if (instrumentFilter.processAndFilter(event)) {
                        Instrument midiInst = instrumentFilter.getEventInstrument(event);
                        if (midiInst == Instrument.DRUMS) continue; // ドラムはピッチ調律対象外

                        int pitch = noteMapper.mapToNoteBlockPitch(event.note);
                        RequiredNote rn = new RequiredNote(pitch, midiInst);
                        reqFrequency.put(rn, reqFrequency.getOrDefault(rn, 0) + 1);
                    }
                }
            }
            instrumentFilter.reset(); // プログラム状態復元

            // 頻度の高い順にソートされた RequiredNote リストを作成
            List<RequiredNote> sortedReqs = new ArrayList<>(reqFrequency.keySet());
            sortedReqs.sort((r1, r2) -> Integer.compare(reqFrequency.get(r2), reqFrequency.get(r1)));

            // 3. 物理ブロックを割り当て用のラッパーリストで管理
            class AllocTarget {
                final TuneTarget block;
                boolean allocated = false;

                AllocTarget(TuneTarget block) {
                    this.block = block;
                }
            }

            List<AllocTarget> allocPool = new ArrayList<>();
            for (TuneTarget t : targets) {
                allocPool.add(new AllocTarget(t));
            }

            // 各 RequiredNote に対する割り当てを実行 (二フェーズ・マッチング)
            List<RequiredNote> unallocatedReqs = new ArrayList<>();

            // フェーズ1: 現在のピッチが必要なピッチと完全に一致する適合ブロックを最優先でロックして生かす
            for (RequiredNote rn : sortedReqs) {
                net.minecraft.block.enums.NoteBlockInstrument[] preferred = getPreferredInstruments(rn.instrument);
                boolean matched = false;

                for (net.minecraft.block.enums.NoteBlockInstrument prefInst : preferred) {
                    for (AllocTarget at : allocPool) {
                        if (!at.allocated && at.block.instrument == prefInst && at.block.currentPitch == rn.pitch) {
                            at.block.targetPitch = rn.pitch;
                            at.allocated = true;
                            rn.assigned = true;
                            matched = true;
                            break;
                        }
                    }
                    if (matched) break;
                }
            }

            // フェーズ2: 一致するブロックがなかった残りの RequiredNote について、残った未割り当て適合ブロックに調律を割り当て
            for (RequiredNote rn : sortedReqs) {
                if (rn.assigned) continue;

                net.minecraft.block.enums.NoteBlockInstrument[] preferred = getPreferredInstruments(rn.instrument);
                boolean matched = false;

                for (net.minecraft.block.enums.NoteBlockInstrument prefInst : preferred) {
                    for (AllocTarget at : allocPool) {
                        if (!at.allocated && at.block.instrument == prefInst) {
                            at.block.targetPitch = rn.pitch;
                            at.allocated = true;
                            rn.assigned = true;
                            matched = true;
                            break;
                        }
                    }
                    if (matched) break;
                }

                if (!matched) {
                    // 適合する代用ブロックも含めて周辺ブロックが不足している場合
                    unallocatedReqs.add(rn);
                }
            }

            // 4. 余剰ブロック（曲で必要とされなかったブロック）の補完
            // 各音色ごとに、0〜24 のうち「まだその音色として割り当てられていないピッチ」を一意に割り当ててオクターブ鍵盤化
            Map<net.minecraft.block.enums.NoteBlockInstrument, java.util.Set<Integer>> assignedPitchesByInst = new HashMap<>();
            for (AllocTarget at : allocPool) {
                if (at.allocated && at.block.targetPitch != -1) {
                    assignedPitchesByInst.computeIfAbsent(at.block.instrument, k -> new java.util.HashSet<>()).add(at.block.targetPitch);
                }
            }

            int extraCounter = 0;
            for (AllocTarget at : allocPool) {
                if (!at.allocated) {
                    net.minecraft.block.enums.NoteBlockInstrument inst = at.block.instrument;
                    java.util.Set<Integer> usedPitches = assignedPitchesByInst.computeIfAbsent(inst, k -> new java.util.HashSet<>());
                    
                    int candidate = 0;
                    while (candidate < 25 && usedPitches.contains(candidate)) {
                        candidate++;
                    }
                    if (candidate < 25) {
                        at.block.targetPitch = candidate;
                        usedPitches.add(candidate);
                    } else {
                        // 0〜24がすべて埋まっている場合は、曲の頻出ピッチまたは循環ピッチを重複割り当てして埋める
                        int wrapAroundPitch = 0;
                        java.util.List<Integer> sortedPitchesForInst = new ArrayList<>();
                        for (RequiredNote rn : sortedReqs) {
                            for (net.minecraft.block.enums.NoteBlockInstrument prefInst : getPreferredInstruments(rn.instrument)) {
                                if (prefInst == inst) {
                                    sortedPitchesForInst.add(rn.pitch);
                                    break;
                                }
                            }
                        }
                        if (!sortedPitchesForInst.isEmpty()) {
                            int extraIdx = extraCounter % sortedPitchesForInst.size();
                            wrapAroundPitch = sortedPitchesForInst.get(extraIdx);
                        } else {
                            wrapAroundPitch = extraCounter % 25;
                        }
                        at.block.targetPitch = wrapAroundPitch;
                        extraCounter++;
                    }
                    at.allocated = true;
                }
            }

            // 5. 調律の高速実行
            int tunedCount = 0;
            int totalClicks = 0;

            var inventory = player.getInventory();
            int originalSlot = inventory.getSelectedSlot();
            int emptySlot = -1;
            for (int i = 0; i < 9; i++) {
                if (inventory.getStack(i).isEmpty()) {
                    emptySlot = i;
                    break;
                }
            }
            if (emptySlot != -1) {
                inventory.setSelectedSlot(emptySlot);
                client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(emptySlot));
            }

            try {
                for (AllocTarget at : allocPool) {
                    TuneTarget t = at.block;
                    if (t.targetPitch == -1) continue;

                    if (t.targetPitch != t.currentPitch) {
                        int clicks = (t.targetPitch - t.currentPitch + 25) % 25;
                        for (int c = 0; c < clicks; c++) {
                            client.interactionManager.interactBlock(
                                player,
                                Hand.MAIN_HAND,
                                new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(t.pos), Direction.UP, t.pos, false)
                            );
                        }
                        player.swingHand(Hand.MAIN_HAND);
                        tunedCount++;
                        totalClicks += clicks;
                    }
                }
            } finally {
                if (emptySlot != -1) {
                    inventory.setSelectedSlot(originalSlot);
                    client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
                }
            }

            // 調律結果と不足警告の出力
            if (tunedCount > 0) {
                sendSystemMessage(Text.literal("§a自動調律完了: " + tunedCount + "個のブロックを調整しました (合計右クリック: " + totalClicks + "回)"));
            } else {
                sendSystemMessage(Text.literal("§aすでにすべてのブロックが正しい音程で調律されています。"));
            }

            if (!unallocatedReqs.isEmpty()) {
                sendSystemMessage(Text.literal("§c音符ブロックが不足しているため、一部の必要な音色・音階を調律できませんでした (不足数: " + unallocatedReqs.size() + "音)"));
            }

            // キャッシュをその場で直接即時同期
            noteBlockCache.clear();
            for (AllocTarget at : allocPool) {
                TuneTarget t = at.block;
                if (t.targetPitch != -1) {
                    int finalPitch = t.targetPitch;
                    noteBlockCache.computeIfAbsent(finalPitch, k -> new ArrayList<>())
                                  .add(new NoteBlockInfo(t.pos, t.instrument));
                }
            }
        });
    }

    /**
     * プレイヤーから音符ブロックへの視線が障害物やモブ等で完全に遮られておらず、
     * 物理的にインタラクト（右クリック・左クリック）が可能な状態であるかを判定します。
     */
    private boolean isBlockInteractable(ClientPlayerEntity player, BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return false;

        Vec3d start = player.getEyePos();
        
        // 1. AABB上の最も近い点（Closest Point）を算出して直線距離を測定
        double minX = pos.getX();
        double maxX = pos.getX() + 1.0;
        double minY = pos.getY();
        double maxY = pos.getY() + 1.0;
        double minZ = pos.getZ();
        double maxZ = pos.getZ() + 1.0;

        double closestX = Math.max(minX, Math.min(start.x, maxX));
        double closestY = Math.max(minY, Math.min(start.y, maxY));
        double closestZ = Math.max(minZ, Math.min(start.z, maxZ));
        
        Vec3d closestPoint = new Vec3d(closestX, closestY, closestZ);
        double dist = start.distanceTo(closestPoint);
        
        // 最大インタラクトリーチ（4.5マス）を超えている場合は物理的に届かないため除外
        if (dist > 4.5) {
            return false;
        }

        // 2. ブロックによる完全遮蔽のチェック (最も近い点、および予備チェックとしてブロックの中心への視線)
        Vec3d endCenter = Vec3d.ofCenter(pos);
        boolean closestVisible = checkRaycast(client, start, closestPoint, pos, player);
        boolean centerVisible = checkRaycast(client, start, endCenter, pos, player);
        
        if (!closestVisible && !centerVisible) {
            return false;
        }

        // 3. 視線上のエンティティ（モブ、他プレイヤー、防具立てなど）による遮蔽チェック
        Vec3d validEnd = closestVisible ? closestPoint : endCenter;
        Box box = new Box(start, validEnd);
        List<Entity> entities = client.world.getOtherEntities(
            player, 
            box, 
            entity -> entity.canHit()
        );

        for (Entity entity : entities) {
            Box entityBox = entity.getBoundingBox();
            java.util.Optional<Vec3d> rayHit = entityBox.raycast(start, validEnd);
            if (rayHit.isPresent()) {
                // 手前にモブ等が重なっているため、クリックが吸い取られてブロックに触れない
                return false;
            }
        }

        return true;
    }

    private boolean checkRaycast(MinecraftClient client, Vec3d start, Vec3d end, BlockPos targetPos, ClientPlayerEntity player) {
        net.minecraft.world.RaycastContext context = new net.minecraft.world.RaycastContext(
            start,
            end,
            net.minecraft.world.RaycastContext.ShapeType.COLLIDER, // 衝突判定を使用
            net.minecraft.world.RaycastContext.FluidHandling.NONE,
            player
        );
        var hitResult = client.world.raycast(context);
        if (hitResult.getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
            return true;
        }
        BlockPos hitPos = hitResult.getBlockPos();
        if (hitPos.equals(targetPos)) {
            return true;
        }
        var state = client.world.getBlockState(hitPos);
        // 遮蔽したのが音符ブロックや空気以外で、かつ不透過固体ブロックの場合はインタラクト不可と判定
        return state.isOf(Blocks.NOTE_BLOCK) || state.isAir() || !state.isOpaque();
    }

    public long getPausedTimeMs() {
        return this.pausedTimeMs;
    }

    public void setPausedTimeMs(long ms) {
        this.pausedTimeMs = ms;
    }

    private static class TuneTarget {
        final BlockPos pos;
        final net.minecraft.block.enums.NoteBlockInstrument instrument;
        final int currentPitch;
        int targetPitch = -1;

        TuneTarget(BlockPos pos, net.minecraft.block.enums.NoteBlockInstrument instrument, int currentPitch) {
            this.pos = pos;
            this.instrument = instrument;
            this.currentPitch = currentPitch;
        }
    }

    private static class RequiredNote {
        final int pitch;
        final Instrument instrument;
        boolean assigned = false;

        RequiredNote(int pitch, Instrument instrument) {
            this.pitch = pitch;
            this.instrument = instrument;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RequiredNote that)) return false;
            return pitch == that.pitch && instrument == that.instrument;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(pitch, instrument);
        }
    }

    private static class NoteBlockInfo {
        final BlockPos pos;
        final net.minecraft.block.enums.NoteBlockInstrument instrument;

        NoteBlockInfo(BlockPos pos, net.minecraft.block.enums.NoteBlockInstrument instrument) {
            this.pos = pos;
            this.instrument = instrument;
        }
    }
}
