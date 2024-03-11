package vg.skye.geologist.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import vg.skye.geologist.Database;
import vg.skye.geologist.DatabaseHolder;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin implements DatabaseHolder {
    @Shadow @Final protected LevelStorage.Session session;

    @NotNull
    @Override
    @Unique
    public Database geologist$getDatabase() {
        return ((DatabaseHolder) this.session).geologist$getDatabase();
    }
}
