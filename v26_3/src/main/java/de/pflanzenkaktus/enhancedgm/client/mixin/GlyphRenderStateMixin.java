package de.pflanzenkaktus.enhancedgm.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.SamplerCache;
import de.pflanzenkaktus.enhancedgm.client.screen.GMSettingsScreen;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

@Mixin(GlyphRenderState.class)
public class GlyphRenderStateMixin {
    @Shadow
    @Final
    private TextRenderable renderable;

    @Unique
    private static boolean enhancedgm$resolved = false;
    @Unique
    private static Object enhancedgm$linearSampler = null;
    @Unique
    private static Method enhancedgm$singleTexMethod = null;
    @Unique
    private static Method enhancedgm$texViewMethod = null;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Unique
    private static void enhancedgm$initLinearSampler(TextRenderable sampleRenderable) {
        enhancedgm$resolved = true;
        try {
            SamplerCache cache = RenderSystem.getSamplerCache();
            for (Method m : SamplerCache.class.getMethods()) {
                if (m.getName().equals("getClampToEdge") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isEnum()) {
                    Class<Enum> enumClass = (Class<Enum>) m.getParameterTypes()[0];
                    Object linear = Enum.valueOf(enumClass, "LINEAR");
                    enhancedgm$linearSampler = m.invoke(cache, linear);
                    break;
                }
            }
            enhancedgm$texViewMethod = TextRenderable.class.getMethod("textureView");
            for (Method m : TextureSetup.class.getMethods()) {
                if (m.getName().equals("singleTextureWithLightmap") && m.getParameterCount() == 2) {
                    enhancedgm$singleTexMethod = m;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "textureSetup", at = @At("HEAD"), cancellable = true, require = 0)
    private void enhancedgm$useLinearSamplerForModernFont(CallbackInfoReturnable<TextureSetup> cir) {
        if (this.renderable instanceof TextRenderable.Styled styled
            && GMSettingsScreen.MODERN_FONT_DESC.equals(styled.style().getFont())) {
            if (!enhancedgm$resolved) {
                enhancedgm$initLinearSampler(this.renderable);
            }
            if (enhancedgm$linearSampler != null && enhancedgm$singleTexMethod != null && enhancedgm$texViewMethod != null) {
                try {
                    Object view = enhancedgm$texViewMethod.invoke(this.renderable);
                    TextureSetup setup = (TextureSetup) enhancedgm$singleTexMethod.invoke(null, view, enhancedgm$linearSampler);
                    if (setup != null) {
                        cir.setReturnValue(setup);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
