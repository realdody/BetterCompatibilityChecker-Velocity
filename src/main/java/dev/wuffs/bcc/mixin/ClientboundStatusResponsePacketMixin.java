package dev.wuffs.bcc.mixin;

import dev.wuffs.bcc.contract.ServerDataExtension;
import dev.wuffs.bcc.data.BetterStatus;
import dev.wuffs.bcc.data.BetterStatusServerHolder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.google.gson.JsonElement;
import net.minecraft.Util;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.GsonHelper;
import com.mojang.serialization.Codec;

@Mixin(value = ClientboundStatusResponsePacket.class)
public class ClientboundStatusResponsePacketMixin {
    @Shadow
    @Final
    private ServerStatus status;

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeJsonWithCodec(Lcom/mojang/serialization/Codec;Ljava/lang/Object;)V"))
    private void onWrite(FriendlyByteBuf instance, Codec<ServerStatus> codec, Object status) {
        BetterStatus betterStatus = BetterStatusServerHolder.INSTANCE.getStatus();
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, (ServerStatus) status).result().orElseThrow(() -> new IllegalStateException("Failed to encode status"));
        if (betterStatus != null) {
            JsonElement betterStatusJson = BetterStatus.CODEC.encodeStart(JsonOps.INSTANCE, betterStatus).result().orElseThrow(() -> new IllegalStateException("Failed to encode better status"));
            json.getAsJsonObject().add("bcc", betterStatusJson);
        }
        instance.writeUtf(GsonHelper.toStableString(json));
    }

    @Redirect(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;readJsonWithCodec(Lcom/mojang/serialization/Codec;)Ljava/lang/Object;"))
    private Object onRead(FriendlyByteBuf instance, Codec<ServerStatus> codec) {
        JsonElement json = GsonHelper.parse(instance.readUtf());
        ServerStatus status = codec.parse(JsonOps.INSTANCE, json).result().orElseThrow(() -> new IllegalStateException("Failed to parse status"));

        if (json.isJsonObject() && json.getAsJsonObject().has("bcc")) {
            BetterStatus betterStatus = BetterStatus.CODEC.parse(JsonOps.INSTANCE, json.getAsJsonObject().get("bcc")).result().orElseThrow(() -> new IllegalStateException("Failed to parse better status"));
            ((ServerDataExtension) (Object) status).setBetterData(betterStatus);
        }

        return status;
    }
}
