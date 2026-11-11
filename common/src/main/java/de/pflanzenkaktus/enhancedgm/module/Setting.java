package de.pflanzenkaktus.enhancedgm.module;

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
    default String subcategory() { return ""; }

    record BooleanSetting(
        String id,
        String label,
        String description,
        Supplier<Boolean> getter,
        Consumer<Boolean> setter,
        String subcategory
    ) implements Setting<Boolean> {
        public BooleanSetting(String id, String label, String description, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            this(id, label, description, getter, setter, "");
        }
        public BooleanSetting withSubcategory(String subcat) {
            return new BooleanSetting(id, label, description, getter, setter, subcat);
        }
    }

    record IntSliderSetting(
        String id,
        String label,
        String description,
        int min,
        int max,
        String unitSuffix,
        Supplier<Integer> getter,
        Consumer<Integer> setter,
        String subcategory
    ) implements Setting<Integer> {
        public IntSliderSetting(String id, String label, String description, int min, int max, String unitSuffix, Supplier<Integer> getter, Consumer<Integer> setter) {
            this(id, label, description, min, max, unitSuffix, getter, setter, "");
        }
        public IntSliderSetting withSubcategory(String subcat) {
            return new IntSliderSetting(id, label, description, min, max, unitSuffix, getter, setter, subcat);
        }
    }

    record DoubleSliderSetting(
        String id,
        String label,
        String description,
        double min,
        double max,
        double step,
        String unitSuffix,
        Supplier<Double> getter,
        Consumer<Double> setter,
        String subcategory
    ) implements Setting<Double> {
        public DoubleSliderSetting(String id, String label, String description, double min, double max, double step, String unitSuffix, Supplier<Double> getter, Consumer<Double> setter) {
            this(id, label, description, min, max, step, unitSuffix, getter, setter, "");
        }
        public DoubleSliderSetting withSubcategory(String subcat) {
            return new DoubleSliderSetting(id, label, description, min, max, step, unitSuffix, getter, setter, subcat);
        }
    }

    record StringSetting(
        String id,
        String label,
        String description,
        Supplier<String> getter,
        Consumer<String> setter,
        String subcategory
    ) implements Setting<String> {
        public StringSetting(String id, String label, String description, Supplier<String> getter, Consumer<String> setter) {
            this(id, label, description, getter, setter, "");
        }
        public StringSetting withSubcategory(String subcat) {
            return new StringSetting(id, label, description, getter, setter, subcat);
        }
    }

    record CycleSetting(
        String id,
        String label,
        String description,
        List<String> options,
        Supplier<String> getter,
        Consumer<String> setter,
        String subcategory
    ) implements Setting<String> {
        public CycleSetting(String id, String label, String description, List<String> options, Supplier<String> getter, Consumer<String> setter) {
            this(id, label, description, options, getter, setter, "");
        }
        public CycleSetting withSubcategory(String subcat) {
            return new CycleSetting(id, label, description, options, getter, setter, subcat);
        }
    }

    record ActionSetting(
        String id,
        String label,
        String buttonText,
        String description,
        Runnable action,
        String subcategory
    ) implements Setting<Void> {
        public ActionSetting(String id, String label, String buttonText, String description, Runnable action) {
            this(id, label, buttonText, description, action, "");
        }
        public ActionSetting withSubcategory(String subcat) {
            return new ActionSetting(id, label, buttonText, description, action, subcat);
        }
    }
}
