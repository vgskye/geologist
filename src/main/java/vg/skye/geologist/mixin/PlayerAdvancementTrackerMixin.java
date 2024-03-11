package vg.skye.geologist.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.ServerAdvancementLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vg.skye.geologist.Database;
import vg.skye.geologist.DatabaseHolder;
import vg.skye.geologist.DatabaseKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("OverwriteAuthorRequired")
@Mixin(PlayerAdvancementTracker.class)
public abstract class PlayerAdvancementTrackerMixin {
    @Shadow @Final private DataFixer dataFixer;
    @Shadow @Final private static Gson GSON;
    @Shadow @Final private static TypeToken<Map<Identifier, AdvancementProgress>> JSON_TYPE;
    @Shadow @Final private static Logger LOGGER;

    @Shadow protected abstract void initProgress(Advancement advancement, AdvancementProgress progress);

    @Shadow @Final private Set<Advancement> progressUpdates;

    @Shadow protected abstract void onStatusUpdate(Advancement advancement);

    @Shadow protected abstract void rewardEmptyAdvancements(ServerAdvancementLoader advancementLoader);

    @Shadow protected abstract void beginTrackingAllAdvancements(ServerAdvancementLoader advancementLoader);

    @Shadow private ServerPlayerEntity owner;
    @Shadow @Final private Map<Advancement, AdvancementProgress> progress;
    @Unique
    private Database database;
    @Unique
    private static final byte[] NAMESPACE = "advancements".getBytes(StandardCharsets.UTF_8);

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancement/PlayerAdvancementTracker;load(Lnet/minecraft/server/ServerAdvancementLoader;)V"))
    private void getRocks(DataFixer dataFixer, PlayerManager playerManager, ServerAdvancementLoader advancementLoader, Path filePath, ServerPlayerEntity owner, CallbackInfo ci) {
        database = ((DatabaseHolder) playerManager).geologist$getDatabase();
    }

    @Overwrite
    private void load(ServerAdvancementLoader advancementLoader) {
        byte[] value = database.readBytes(new DatabaseKey(NAMESPACE, Uuids.toByteArray(owner.getUuid())));
        if (value != null) {
            try {
                Dynamic<JsonElement> parsed = new Dynamic<>(JsonOps.INSTANCE, GSON.fromJson(new String(value, StandardCharsets.UTF_8), JsonElement.class));
                int version = parsed.get("DataVersion").asInt(1343);
                parsed = parsed.remove("DataVersion");
                parsed = DataFixTypes.ADVANCEMENTS.update(this.dataFixer, parsed, version);
                Map<Identifier, AdvancementProgress> map = GSON.getAdapter(JSON_TYPE).fromJsonTree(parsed.getValue());
                if (map == null) {
                    throw new JsonParseException("Found null for advancements");
                }

                map.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry -> {
                    Advancement advancement = advancementLoader.get(entry.getKey());
                    if (advancement == null) {
                        LOGGER.warn("Ignored advancement '{}' in progress file {} - it doesn't exist anymore?", entry.getKey(), owner.getUuidAsString());
                    } else {
                        this.initProgress(advancement, entry.getValue());
                        this.progressUpdates.add(advancement);
                        this.onStatusUpdate(advancement);
                    }
                });
            } catch (JsonParseException e) {
                LOGGER.error("Couldn't parse player advancements for {}", owner.getUuidAsString(), e);
            }
        }

        this.rewardEmptyAdvancements(advancementLoader);
        this.beginTrackingAllAdvancements(advancementLoader);
    }

    @Overwrite
    public void save() {
        Map<Identifier, AdvancementProgress> map = new LinkedHashMap<>();

        for(Map.Entry<Advancement, AdvancementProgress> entry : this.progress.entrySet()) {
            AdvancementProgress advancementProgress = entry.getValue();
            if (advancementProgress.isAnyObtained()) {
                map.put(entry.getKey().getId(), advancementProgress);
            }
        }

        JsonElement json = GSON.toJsonTree(map);
        json.getAsJsonObject().addProperty("DataVersion", SharedConstants.getGameVersion().getSaveVersion().getId());

        byte[] contents = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
        database.writeBytes(new DatabaseKey(NAMESPACE, Uuids.toByteArray(owner.getUuid())), contents);
    }
}
