package de.germanminer.addon.module;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Declarative setting descriptors exposed by any AddonModule so the Settings Screen
 * and Mod Menu integration automatically generate controls for every module.
 */
public sealed interface Setting<T> permits
    Setting.BooleanSetting,
    Setting.IntSliderSetting,
    Setting.DoubleSliderSetting,
    Setting.StringSetting,
    Setting.CycleSetting,
    Setting.ActionSetting {

    String id();
    String label();
    String description();

    record BooleanSetting(
        String id,
        String label,
        String description,
        Supplier<Boolean> getter,
        Consumer<Boolean> setter
    ) implements Setting<Boolean> {}

    record IntSliderSetting(
        String id,
        String label,
        String description,
        int min,
        int max,
        String unitSuffix,
        Supplier<Integer> getter,
        Consumer<Integer> setter
    ) implements Setting<Integer> {}

    record DoubleSliderSetting(
        String id,
        String label,
        String description,
        double min,
        double max,
        double step,
        String unitSuffix,
        Supplier<Double> getter,
        Consumer<Double> setter
    ) implements Setting<Double> {}

    record StringSetting(
        String id,
        String label,
        String description,
        Supplier<String> getter,
        Consumer<String> setter
    ) implements Setting<String> {}

    record CycleSetting(
        String id,
        String label,
        String description,
        List<String> options,
        Supplier<String> getter,
        Consumer<String> setter
    ) implements Setting<String> {}

    record ActionSetting(
        String id,
        String label,
        String buttonText,
        String description,
        Runnable action
    ) implements Setting<Void> {}
}
