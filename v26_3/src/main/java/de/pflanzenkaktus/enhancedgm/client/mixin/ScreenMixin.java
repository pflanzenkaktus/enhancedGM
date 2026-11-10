package de.pflanzenkaktus.enhancedgm.client.mixin;

import de.pflanzenkaktus.enhancedgm.client.GMAddonClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URI;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "clickUrlAction", at = @At("HEAD"), cancellable = true)
    private static void onEnhancedGMClickUrl(Minecraft client, Screen screen, URI uri, CallbackInfoReturnable<Boolean> cir) {
        if (uri != null && "enhancedgm".equalsIgnoreCase(uri.getScheme())) {
            cir.setReturnValue(true);
            cir.cancel();

            String action = uri.getHost() != null ? uri.getHost() : uri.getSchemeSpecificPart();
            if (action != null && action.startsWith("//")) {
                action = action.substring(2);
            }
            if (action != null) {
                action = action.trim().toLowerCase(java.util.Locale.ROOT);
            }

            if ("update".equals(action)) {
                GMAddonClient.triggerInGameUpdate(client);
            } else if ("settings".equals(action)) {
                GMAddonClient.openSettingsScreen(client);
            } else if ("autogear".equals(action)) {
                GMAddonClient.enableAutoGear(client);
            }
        }
    }
}
