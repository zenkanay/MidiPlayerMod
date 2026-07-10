package com.example.midiplayer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * MOD用のキーバインド（ホットキー）を管理するクラス。
 */
public class KeyBindings {
    // 独自のキーバインドカテゴリ（コーナー）を登録 (Yarn 1.21.11 では create を使用)
    public static final KeyBinding.Category MIDI_PLAYER_CATEGORY = 
        KeyBinding.Category.create(Identifier.of("midiplayer", "general"));

    public static KeyBinding configOpenKey;

    /**
     * キーバインドを登録し、キー押下時の監視を開始します。
     *
     * @param modInstance MODのメインインスタンス
     */
    public static void register(ClientMod modInstance) {
        // Mキーで設定画面を開くキーバインドを独自カテゴリに登録
        configOpenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.midiplayer.config", 
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M, // デフォルトキー M
                MIDI_PLAYER_CATEGORY
        ));

        // クライアントのティックイベントの最後に入力を処理
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // ゲーム画面かつGUIが開いていない通常操作時のみ反応させる
            if (client.player != null && client.currentScreen == null) {
                // 設定画面を開く監視 (連続呼び出しを防ぐため if を使用)
                if (configOpenKey.wasPressed()) {
                    client.setScreen(new ConfigScreen());
                }
            }
        });
    }
}
