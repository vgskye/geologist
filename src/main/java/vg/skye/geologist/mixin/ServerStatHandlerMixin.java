package vg.skye.geologist.mixin;

import com.google.gson.JsonParseException;
import com.mojang.datafixers.DataFixer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.util.Uuids;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vg.skye.geologist.Database;
import vg.skye.geologist.DatabaseHolder;
import vg.skye.geologist.DatabaseKey;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@SuppressWarnings("OverwriteAuthorRequired")
@Mixin(ServerStatHandler.class)
public abstract class ServerStatHandlerMixin {
    @Shadow @Final private static Logger LOGGER;

    @Shadow protected abstract String asString();

    @Shadow public abstract void parse(DataFixer dataFixer, String json);

    @Unique
    private Database database;
    @Unique
    private DatabaseKey key;
    @Unique
    private static final byte[] NAMESPACE = "stats".getBytes(StandardCharsets.UTF_8);

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/io/File;isFile()Z"))
    private void setup(MinecraftServer server, File file, CallbackInfo ci) {
        database = ((DatabaseHolder) server).geologist$getDatabase();
        String fileName = file.getName();
        assert fileName.endsWith(".json");
        String uuid = fileName.substring(0, fileName.length() - 5);
        byte[] key = Uuids.toByteArray(UUID.fromString(uuid));
        this.key = new DatabaseKey(NAMESPACE, key);

        byte[] value = database.readBytes(this.key);
        if (value != null) {
            try {
                this.parse(server.getDataFixer(), new String(value, StandardCharsets.UTF_8));
            } catch (JsonParseException e) {
                LOGGER.error("Couldn't parse statistics entry for {}", file, e);
            }
        }
    }

    @Overwrite
    public void save() {
        database.writeBytes(key, this.asString().getBytes(StandardCharsets.UTF_8));
    }
}
