package de.pflanzenkaktus.enhancedgm.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class GMSounds {
    public static final Identifier ETCS_INFO_ID = Identifier.fromNamespaceAndPath("gm-addon", "etcs_info");
    public static final SoundEvent ETCS_INFO = SoundEvent.createVariableRangeEvent(ETCS_INFO_ID);

    public static final Identifier ETCS_TOOFAST_ID = Identifier.fromNamespaceAndPath("gm-addon", "etcs_toofast");
    public static final SoundEvent ETCS_TOOFAST = SoundEvent.createVariableRangeEvent(ETCS_TOOFAST_ID);

    public static final Identifier ETCS_WARNING_ID = Identifier.fromNamespaceAndPath("gm-addon", "etcs_warning");
    public static final SoundEvent ETCS_WARNING = SoundEvent.createVariableRangeEvent(ETCS_WARNING_ID);

    private static SoundInstance currentPlayingInstance = null;

    private GMSounds() {}

    public static void init() {
        try {
            Registry.register(BuiltInRegistries.SOUND_EVENT, ETCS_INFO_ID, ETCS_INFO);
            Registry.register(BuiltInRegistries.SOUND_EVENT, ETCS_TOOFAST_ID, ETCS_TOOFAST);
            Registry.register(BuiltInRegistries.SOUND_EVENT, ETCS_WARNING_ID, ETCS_WARNING);
        } catch (Throwable ignored) {
        }
    }

    public static void playSound(Minecraft client, SoundEvent sound, float volume, float pitch) {
        if (client == null || sound == null || volume <= 0.001f) return;
        try {
            if (currentPlayingInstance != null) {
                client.getSoundManager().stop(currentPlayingInstance);
                currentPlayingInstance = null;
            }
            SimpleSoundInstance instance = SimpleSoundInstance.forUI(sound, pitch, volume);
            currentPlayingInstance = instance;
            client.getSoundManager().play(instance);
        } catch (Throwable t) {
            if (client.player != null) {
                client.player.playSound(sound, volume, pitch);
            }
        }
    }

    public static void stopCurrentSound(Minecraft client) {
        if (client != null && currentPlayingInstance != null) {
            try {
                client.getSoundManager().stop(currentPlayingInstance);
            } catch (Throwable ignored) {}
            currentPlayingInstance = null;
        }
    }
}
