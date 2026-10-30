package dev.scriptautoce.server;

import dev.scriptautoce.script.ScriptException;
import dev.scriptautoce.script.ScriptHost;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;

final class ServerScriptHost implements ScriptHost {
    private static final int MAX_QUERY_RESULTS = 256;
    private final ServerPlayer player;

    ServerScriptHost(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public Position position() {
        Vec3 position = player.position();
        return new Position(position.x, position.y, position.z);
    }

    @Override
    public String blockAt(String x, String y, String z) {
        Vec3 origin = player.position();
        BlockPos position = BlockPos.containing(coordinate(x, origin.x), coordinate(y, origin.y),
                coordinate(z, origin.z));
        return BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(position).getBlock()).toString();
    }

    @Override
    public List<String> players() {
        return player.level().getServer().getPlayerList().getPlayers().stream()
                .map(candidate -> candidate.getGameProfile().name())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Override
    public List<String> entities(String type, double radius) {
        double boundedRadius = Math.max(0, Math.min(64, radius));
        AABB area = AABB.ofSize(player.position(), boundedRadius * 2, boundedRadius * 2, boundedRadius * 2);
        String normalizedType = type == null ? "" : type.strip();
        return player.level().getEntitiesOfClass(Entity.class, area).stream()
                .filter(entity -> normalizedType.isBlank() || normalizedType.equals("*")
                        || normalizedType.equals("any")
                        || BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(normalizedType)
                        || BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().equals(normalizedType))
                .limit(MAX_QUERY_RESULTS)
                .map(entity -> entity.getUUID().toString())
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    @Override
    public long score(String objectiveName, String target) {
        Objective objective = player.level().getScoreboard().getObjective(objectiveName);
        if (objective == null) {
            return 0;
        }
        ReadOnlyScoreInfo info = player.level().getScoreboard()
                .getPlayerScoreInfo(ScoreHolder.forNameOnly(target), objective);
        return info == null ? 0 : info.value();
    }

    private static double coordinate(String raw, double origin) {
        if (raw == null || raw.isBlank()) {
            throw new ScriptException(1, 1, "Coordinate cannot be empty");
        }
        String value = raw.strip();
        if (value.equals("~")) {
            return origin;
        }
        if (value.startsWith("~")) {
            return origin + Double.parseDouble(value.substring(1));
        }
        if (value.startsWith("^")) {
            throw new ScriptException(1, 1, "Local coordinates are not supported in block()");
        }
        return Double.parseDouble(value);
    }
}
