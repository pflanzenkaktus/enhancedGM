package de.pflanzenkaktus.enhancedgm.client.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Sound definitions for enhancedGM.
 * Acoustic alerts for speed cameras are sourced from the European Union Agency for Railways (ERA)
 * specification ERA_ERTMS_015560 (ETCS Driver Machine Interface).
 */
public final class GMSounds {
    public static final Identifier ETCS_INFO_ID = Identifier.fromNamespaceAndPath("gm-addon", "etcs.info");
    public static final SoundEvent ETCS_INFO = SoundEvent.createVariableRangeEvent(ETCS_INFO_ID);

    public static final Identifier ETCS_TOOFAST_ID = Identifier.fromNamespaceAndPath("gm-addon", "etcs.toofast");
    public static final SoundEvent ETCS_TOOFAST = SoundEvent.createVariableRangeEvent(ETCS_TOOFAST_ID);

    public static final Identifier ETCS_WARNING_ID = Identifier.fromNamespaceAndPath("gm-addon", "etcs.warning");
    public static final SoundEvent ETCS_WARNING = SoundEvent.createVariableRangeEvent(ETCS_WARNING_ID);

    private GMSounds() {}

    public static void init() {
        try {
            Registry.register(BuiltInRegistries.SOUND_EVENT, ETCS_INFO_ID, ETCS_INFO);
            Registry.register(BuiltInRegistries.SOUND_EVENT, ETCS_TOOFAST_ID, ETCS_TOOFAST);
            Registry.register(BuiltInRegistries.SOUND_EVENT, ETCS_WARNING_ID, ETCS_WARNING);
        } catch (Throwable ignored) {
            // BuiltInRegistries may be frozen in client-only bootstrap;
            // SoundEvent instances resolve directly via sounds.json and SoundManager.
        }
    }
}
