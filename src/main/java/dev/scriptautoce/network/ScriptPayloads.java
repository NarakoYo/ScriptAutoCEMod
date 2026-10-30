package dev.scriptautoce.network;

import dev.scriptautoce.ScriptAutoCe;
import dev.scriptautoce.server.ServerNetworkController;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class ScriptPayloads {
    public static final int PROTOCOL_VERSION = 2;
    private static final int MAX_STRING = 64 * 1024;
    private static final int MAX_JSON = 256 * 1024;

    private ScriptPayloads() {
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.serverboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ListSharedPayload.TYPE, ListSharedPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DownloadSharedPayload.TYPE, DownloadSharedPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UploadSharedPayload.TYPE, UploadSharedPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DeleteSharedPayload.TYPE, DeleteSharedPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RunScriptPayload.TYPE, RunScriptPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CancelJobPayload.TYPE, CancelJobPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(GetJobPayload.TYPE, GetJobPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(GetServerSettingsPayload.TYPE, GetServerSettingsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateServerSettingsPayload.TYPE, UpdateServerSettingsPayload.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(HelloAckPayload.TYPE, HelloAckPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CatalogPayload.TYPE, CatalogPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ScriptContentPayload.TYPE, ScriptContentPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ActionResultPayload.TYPE, ActionResultPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(JobEventPayload.TYPE, JobEventPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ErrorPayload.TYPE, ErrorPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerSettingsPayload.TYPE, ServerSettingsPayload.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE,
                (payload, context) -> ServerNetworkController.hello(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ListSharedPayload.TYPE,
                (payload, context) -> ServerNetworkController.listShared(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(DownloadSharedPayload.TYPE,
                (payload, context) -> ServerNetworkController.download(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(UploadSharedPayload.TYPE,
                (payload, context) -> ServerNetworkController.upload(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(DeleteSharedPayload.TYPE,
                (payload, context) -> ServerNetworkController.delete(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(RunScriptPayload.TYPE,
                (payload, context) -> ServerNetworkController.run(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(CancelJobPayload.TYPE,
                (payload, context) -> ServerNetworkController.cancel(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(GetJobPayload.TYPE,
                (payload, context) -> ServerNetworkController.getJob(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(GetServerSettingsPayload.TYPE,
                (payload, context) -> ServerNetworkController.getServerSettings(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(UpdateServerSettingsPayload.TYPE,
                (payload, context) -> ServerNetworkController.updateServerSettings(context.player(), payload));
    }

    public static Identifier payloadId(String path) {
        return Identifier.fromNamespaceAndPath(ScriptAutoCe.MOD_ID, path);
    }

    private static void writeString(RegistryFriendlyByteBuf buffer, String value) {
        buffer.writeUtf(value == null ? "" : value, MAX_STRING);
    }

    private static String readString(RegistryFriendlyByteBuf buffer) {
        return buffer.readUtf(MAX_STRING);
    }

    private static void writeJson(RegistryFriendlyByteBuf buffer, String value) {
        buffer.writeUtf(value == null ? "{}" : value, MAX_JSON);
    }

    private static String readJson(RegistryFriendlyByteBuf buffer) {
        return buffer.readUtf(MAX_JSON);
    }

    public record HelloPayload(int protocolVersion) implements CustomPacketPayload {
        public static final Type<HelloPayload> TYPE = new Type<>(payloadId("hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeVarInt(payload.protocolVersion()),
                buffer -> new HelloPayload(buffer.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record HelloAckPayload(
            int protocolVersion,
            int maxSourceBytes,
            int maxInstructions,
            boolean sharingEnabled,
            String settingsJson
    ) implements CustomPacketPayload {
        public static final Type<HelloAckPayload> TYPE = new Type<>(payloadId("hello_ack"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HelloAckPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.protocolVersion());
                    buffer.writeVarInt(payload.maxSourceBytes());
                    buffer.writeVarInt(payload.maxInstructions());
                    buffer.writeBoolean(payload.sharingEnabled());
                    writeJson(buffer, payload.settingsJson());
                },
                buffer -> new HelloAckPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readBoolean(), readJson(buffer)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ListSharedPayload(String query, int offset, int limit) implements CustomPacketPayload {
        public static final Type<ListSharedPayload> TYPE = new Type<>(payloadId("list_shared"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ListSharedPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    writeString(buffer, payload.query());
                    buffer.writeVarInt(Math.max(0, payload.offset()));
                    buffer.writeVarInt(Math.max(1, payload.limit()));
                },
                buffer -> new ListSharedPayload(readString(buffer), buffer.readVarInt(), buffer.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CatalogPayload(String entriesJson, int total) implements CustomPacketPayload {
        public static final Type<CatalogPayload> TYPE = new Type<>(payloadId("catalog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CatalogPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    writeJson(buffer, payload.entriesJson());
                    buffer.writeVarInt(Math.max(0, payload.total()));
                },
                buffer -> new CatalogPayload(readJson(buffer), buffer.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DownloadSharedPayload(String id) implements CustomPacketPayload {
        public static final Type<DownloadSharedPayload> TYPE = new Type<>(payloadId("download_shared"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DownloadSharedPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> writeString(buffer, payload.id()),
                buffer -> new DownloadSharedPayload(readString(buffer)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ScriptContentPayload(String id, String source, String metadataJson, String error)
            implements CustomPacketPayload {
        public static final Type<ScriptContentPayload> TYPE = new Type<>(payloadId("script_content"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScriptContentPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    writeString(buffer, payload.id());
                    writeString(buffer, payload.source());
                    writeJson(buffer, payload.metadataJson());
                    writeString(buffer, payload.error());
                },
                buffer -> new ScriptContentPayload(readString(buffer), readString(buffer), readJson(buffer),
                        readString(buffer)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UploadSharedPayload(String id, String source) implements CustomPacketPayload {
        public static final Type<UploadSharedPayload> TYPE = new Type<>(payloadId("upload_shared"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadSharedPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    writeString(buffer, payload.id());
                    writeString(buffer, payload.source());
                },
                buffer -> new UploadSharedPayload(readString(buffer), readString(buffer)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DeleteSharedPayload(String id) implements CustomPacketPayload {
        public static final Type<DeleteSharedPayload> TYPE = new Type<>(payloadId("delete_shared"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DeleteSharedPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> writeString(buffer, payload.id()),
                buffer -> new DeleteSharedPayload(readString(buffer)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RunScriptPayload(String jobId, String source, String argumentsJson) implements CustomPacketPayload {
        public static final Type<RunScriptPayload> TYPE = new Type<>(payloadId("run_script"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RunScriptPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    writeString(buffer, payload.jobId());
                    writeString(buffer, payload.source());
                    writeJson(buffer, payload.argumentsJson());
                },
                buffer -> new RunScriptPayload(readString(buffer), readString(buffer), readJson(buffer)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CancelJobPayload(String jobId) implements CustomPacketPayload {
        public static final Type<CancelJobPayload> TYPE = new Type<>(payloadId("cancel_job"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CancelJobPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> writeString(buffer, payload.jobId()),
                buffer -> new CancelJobPayload(readString(buffer)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GetJobPayload(String jobId) implements CustomPacketPayload {
        public static final Type<GetJobPayload> TYPE = new Type<>(payloadId("get_job"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GetJobPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> writeString(buffer, payload.jobId()),
                buffer -> new GetJobPayload(readString(buffer)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record JobEventPayload(
            String jobId,
            String eventType,
            String message,
            int progress,
            String currentCommand,
            String logsJson,
            String reasonCode,
            String detail,
            int line
    ) implements CustomPacketPayload {
        public static final Type<JobEventPayload> TYPE = new Type<>(payloadId("job_event"));
        public static final StreamCodec<RegistryFriendlyByteBuf, JobEventPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    writeString(buffer, payload.jobId());
                    writeString(buffer, payload.eventType());
                    writeString(buffer, payload.message());
                    buffer.writeVarInt(Math.max(0, payload.progress()));
                    writeString(buffer, payload.currentCommand());
                    writeJson(buffer, payload.logsJson());
                    writeString(buffer, payload.reasonCode());
                    writeString(buffer, payload.detail());
                    buffer.writeVarInt(Math.max(0, payload.line()));
                },
                buffer -> new JobEventPayload(readString(buffer), readString(buffer), readString(buffer),
                        buffer.readVarInt(), readString(buffer), readJson(buffer), readString(buffer),
                        readString(buffer), buffer.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ErrorPayload(String code, String message, int line, int column) implements CustomPacketPayload {
        public static final Type<ErrorPayload> TYPE = new Type<>(payloadId("error"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ErrorPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    writeString(buffer, payload.code());
                    writeString(buffer, payload.message());
                    buffer.writeVarInt(Math.max(0, payload.line()));
                    buffer.writeVarInt(Math.max(0, payload.column()));
                },
                buffer -> new ErrorPayload(readString(buffer), readString(buffer), buffer.readVarInt(),
                        buffer.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ActionResultPayload(boolean success, String action, String message)
            implements CustomPacketPayload {
        public static final Type<ActionResultPayload> TYPE = new Type<>(payloadId("action_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionResultPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBoolean(payload.success());
                    writeString(buffer, payload.action());
                    writeString(buffer, payload.message());
                },
                buffer -> new ActionResultPayload(buffer.readBoolean(), readString(buffer), readString(buffer)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GetServerSettingsPayload() implements CustomPacketPayload {
        public static final GetServerSettingsPayload INSTANCE = new GetServerSettingsPayload();
        public static final Type<GetServerSettingsPayload> TYPE = new Type<>(payloadId("get_server_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GetServerSettingsPayload> CODEC =
                StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UpdateServerSettingsPayload(String settingsJson) implements CustomPacketPayload {
        public static final Type<UpdateServerSettingsPayload> TYPE = new Type<>(payloadId("update_server_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateServerSettingsPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> writeJson(buffer, payload.settingsJson()),
                buffer -> new UpdateServerSettingsPayload(readJson(buffer)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ServerSettingsPayload(String settingsJson) implements CustomPacketPayload {
        public static final Type<ServerSettingsPayload> TYPE = new Type<>(payloadId("server_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ServerSettingsPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> writeJson(buffer, payload.settingsJson()),
                buffer -> new ServerSettingsPayload(readJson(buffer)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
