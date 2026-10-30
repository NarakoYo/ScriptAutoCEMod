package dev.scriptautoce.client.script;

import dev.scriptautoce.script.ScriptException;
import dev.scriptautoce.script.ScriptHost;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;

final class ClientScriptHost implements ScriptHost {
    private final Minecraft client;

    ClientScriptHost(Minecraft client) {
        this.client = client;
    }

    @Override
    public Position position() {
        LocalPlayer player = requirePlayer();
        Vec3 position = player.position();
        return new Position(position.x, position.y, position.z);
    }

    @Override
    public String blockAt(String x, String y, String z) {
        LocalPlayer player = requirePlayer();
        Vec3 origin = player.position();
        BlockPos position = BlockPos.containing(coordinate(x, origin.x), coordinate(y, origin.y),
                coordinate(z, origin.z));
        return BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(position).getBlock()).toString();
    }

    @Override
    public List<String> players() {
        return requirePlayer().level().players().stream()
                .map(player -> player.getGameProfile().name())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Override
    public List<String> entities(String type, double radius) {
        LocalPlayer player = requirePlayer();
        double boundedRadius = Math.max(0, Math.min(64, radius));
        AABB area = AABB.ofSize(player.position(), boundedRadius * 2, boundedRadius * 2, boundedRadius * 2);
        String normalizedType = type == null ? "" : type.strip();
        return player.level().getEntitiesOfClass(Entity.class, area).stream()
                .filter(entity -> normalizedType.isBlank() || normalizedType.equals("*")
                        || normalizedType.equals("any")
                        || BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(normalizedType)
                        || BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().equals(normalizedType))
                .limit(256)
                .map(entity -> entity.getUUID().toString())
                .sorted()
                .toList();
    }

    @Override
    public long score(String objectiveName, String target) {
        LocalPlayer player = requirePlayer();
        Objective objective = player.level().getScoreboard().getObjective(objectiveName);
        if (objective == null) {
            return 0;
        }
        ReadOnlyScoreInfo info = player.level().getScoreboard()
                .getPlayerScoreInfo(ScoreHolder.forNameOnly(target), objective);
        return info == null ? 0 : info.value();
    }

    private LocalPlayer requirePlayer() {
        if (client.player == null) {
            throw new ScriptException(1, 1, "No client player is available");
        }
        return client.player;
    }

    private static double coordinate(String raw, double origin) {
        String value = raw.strip();
        if (value.equals("~")) {
            return origin;
        }
        if (value.startsWith("~")) {
            return origin + Double.parseDouble(value.substring(1));
        }
        return Double.parseDouble(value);
    }
}
