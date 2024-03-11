package vg.skye.geologist.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.world.WorldSaveHandler;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import vg.skye.geologist.Database;
import vg.skye.geologist.DatabaseHolder;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin implements DatabaseHolder {

    @Shadow
    @Final
    private WorldSaveHandler saveHandler;

    @NotNull
    @Override
    @Unique
    public Database geologist$getDatabase() {
        return ((DatabaseHolder) this.saveHandler).geologist$getDatabase();
    }
}
