package jamdm.lol.client.mixin;

import java.util.concurrent.CompletableFuture;

import jamdm.lol.client.DiscSoundHook;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {
    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void jamdm$customStream(Identifier path, boolean looping,
                                    CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (DiscSoundHook.matches(path)) {
            CompletableFuture<AudioStream> stream = DiscSoundHook.take();
            if (stream != null) cir.setReturnValue(stream);
        }
    }
}
