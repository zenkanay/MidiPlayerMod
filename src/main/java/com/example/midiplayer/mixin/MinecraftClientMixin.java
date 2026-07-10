package com.example.midiplayer.mixin;

import com.example.midiplayer.ClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    // 最後にチャットメッセージを送信したシステム時間（ミリ秒）
    private long lastBlockMessageTime = 0;

    private void sendBlockMessage() {
        long now = System.currentTimeMillis();
        // チャットがメッセージで埋め尽くされるのを防ぐため、1秒のクールダウンを設ける
        if (now - lastBlockMessageTime > 1000) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.translatable("msg.midiplayer.click_blocked"), false);
            }
            lastBlockMessageTime = now;
        }
    }

    @Inject(method = "doAttack()Z", at = @At("HEAD"), cancellable = true)
    private void onDoAttack(CallbackInfoReturnable<Boolean> info) {
        ClientMod mod = ClientMod.getInstance();
        if (mod != null && mod.getAutoPlayer().isPlaying() && mod.getConfig().blockClickDuringPlay) {
            sendBlockMessage();
            info.setReturnValue(false);
            info.cancel();
        }
    }

    @Inject(method = "doItemUse()V", at = @At("HEAD"), cancellable = true)
    private void onDoItemUse(CallbackInfo info) {
        ClientMod mod = ClientMod.getInstance();
        if (mod != null && mod.getAutoPlayer().isPlaying() && mod.getConfig().blockClickDuringPlay) {
            sendBlockMessage();
            info.cancel();
        }
    }

    @Inject(method = "handleBlockBreaking(Z)V", at = @At("HEAD"), cancellable = true)
    private void onHandleBlockBreaking(boolean breaking, CallbackInfo info) {
        ClientMod mod = ClientMod.getInstance();
        if (mod != null && mod.getAutoPlayer().isPlaying() && mod.getConfig().blockClickDuringPlay) {
            if (breaking) {
                sendBlockMessage();
            }
            info.cancel();
        }
    }
}
