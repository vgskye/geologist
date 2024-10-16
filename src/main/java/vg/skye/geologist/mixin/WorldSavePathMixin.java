package vg.skye.geologist.mixin;

import net.minecraft.util.WorldSavePath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(WorldSavePath.class)
public class WorldSavePathMixin {
    @ModifyConstant(method = "<clinit>", constant = @Constant(stringValue = "level.dat"))
    private static String redirWorldDat(String relativePath) {
        return "geologist-level.dat";
    }
    @ModifyConstant(method = "<clinit>", constant = @Constant(stringValue = "level.dat_old"))
    private static String redirWorldDatOld(String relativePath) {
        return "geologist-level.dat_old";
    }
}
