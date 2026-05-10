package uet.vnu.edu.scrcpy;

import com.google.gson.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class OBSController {
    private final String host;
    private final int port;
    private final String password;
    private WebSocket ws;
    private Consumer<String> logger = System.out::println;
    private final ConcurrentHashMap<String, CompletableFuture<JsonObject>> pendingRequests = new ConcurrentHashMap<>();
    private CompletableFuture<Void> authFuture;

    public OBSController(String host, int port, String password) {
        this.host = host;
        this.port = port;
        this.password = password;
    }

    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    public CompletableFuture<Void> connect() {
        authFuture = new CompletableFuture<>();
        try {
            HttpClient client = HttpClient.newHttpClient();
            client.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://" + host + ":" + port), new WebSocket.Listener() {
                        StringBuilder textBuffer = new StringBuilder();

                        @Override
                        public void onOpen(WebSocket webSocket) {
                            ws = webSocket;
                            WebSocket.Listener.super.onOpen(webSocket);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            textBuffer.append(data);
                            if (last) {
                                handleMessage(textBuffer.toString());
                                textBuffer.setLength(0);
                            }
                            return WebSocket.Listener.super.onText(webSocket, data, last);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            ws = null;
                            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            if (!authFuture.isDone()) authFuture.completeExceptionally(error);
                        }
                    });
        } catch (Exception e) {
            authFuture.completeExceptionally(e);
        }
        return authFuture;
    }

    public void disconnect() {
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "Bye");
            ws = null;
        }
    }

    private void handleMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            int op = json.get("op").getAsInt();
            JsonObject d = json.has("d") && json.get("d").isJsonObject()
                    ? json.getAsJsonObject("d") : new JsonObject();

            if (op == 0) {          // Hello — cần xác thực
                JsonObject auth = d.has("authentication") ? d.getAsJsonObject("authentication") : null;
                sendIdentify(auth);
            } else if (op == 2) {   // Identified — xác thực thành công
                authFuture.complete(null);
            } else if (op == 7) {   // RequestResponse
                String reqId = d.get("requestId").getAsString();
                CompletableFuture<JsonObject> future = pendingRequests.remove(reqId);
                if (future != null) {
                    JsonObject status = d.getAsJsonObject("requestStatus");
                    if (status.get("result").getAsBoolean()) {
                        JsonObject responseData = d.has("responseData") && d.get("responseData").isJsonObject()
                                ? d.getAsJsonObject("responseData") : new JsonObject();
                        future.complete(responseData);
                    } else {
                        String code = status.has("code") ? status.get("code").getAsString() : "UNKNOWN";
                        String comment = status.has("comment") ? status.get("comment").getAsString() : "";
                        future.completeExceptionally(new RuntimeException(code + (comment.isEmpty() ? "" : ": " + comment)));
                    }
                }
            }
        } catch (Exception e) {
            logger.accept("[OBS WS] Parse error: " + e.getMessage());
        }
    }

    private void sendIdentify(JsonObject authReq) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("op", 1);
            JsonObject d = new JsonObject();
            d.addProperty("rpcVersion", 1);

            if (authReq != null && !password.isEmpty()) {
                String challenge = authReq.get("challenge").getAsString();
                String salt = authReq.get("salt").getAsString();

                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                String passSalt = Base64.getEncoder().encodeToString(
                        sha256.digest((password + salt).getBytes(StandardCharsets.UTF_8)));
                sha256.reset();
                String authStr = Base64.getEncoder().encodeToString(
                        sha256.digest((passSalt + challenge).getBytes(StandardCharsets.UTF_8)));
                d.addProperty("authentication", authStr);
            }

            payload.add("d", d);
            if (ws != null) ws.sendText(payload.toString(), true);
        } catch (Exception e) {
            authFuture.completeExceptionally(e);
        }
    }

    private CompletableFuture<JsonObject> sendRequest(String requestType, JsonObject requestData) {
        if (ws == null) return CompletableFuture.failedFuture(
                new RuntimeException("Mat ket noi OBS WebSocket — OBS co dang mo khong?"));

        String reqId = UUID.randomUUID().toString();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pendingRequests.put(reqId, future);

        JsonObject payload = new JsonObject();
        payload.addProperty("op", 6);
        JsonObject d = new JsonObject();
        d.addProperty("requestType", requestType);
        d.addProperty("requestId", reqId);
        if (requestData != null) d.add("requestData", requestData);
        payload.add("d", d);

        ws.sendText(payload.toString(), true);
        return future;
    }

    // ── Scene / Source helpers ────────────────────────────────────────────────

    public CompletableFuture<String> getCurrentScene() {
        return sendRequest("GetCurrentProgramScene", null)
                .thenApply(res -> res.get("currentProgramSceneName").getAsString());
    }

    public CompletableFuture<Boolean> sourceExists(String scene, String source) {
        JsonObject req = new JsonObject();
        req.addProperty("sceneName", scene);
        req.addProperty("sourceName", source);
        return sendRequest("GetSceneItemId", req)
                .thenApply(res -> true)
                .exceptionally(e -> false);
    }

    /**
     * Tạo mới một Media Source trỏ vào RTSP URL.
     *
     * FIX BUG 2: Thêm looping + reconnect_delay_sec để OBS tự reconnect
     * khi stream vừa bắt đầu hoặc bị ngắt tạm thời.
     */
    public CompletableFuture<Void> addRtspSource(String scene, String source, String url) {
        JsonObject req = new JsonObject();
        req.addProperty("sceneName", scene);
        req.addProperty("inputName", source);
        req.addProperty("inputKind", "ffmpeg_source");

        JsonObject settings = new JsonObject();
        settings.addProperty("input", url);
        settings.addProperty("is_local_file", false);
        settings.addProperty("hw_decode", false);
        settings.addProperty("clear_on_media_end", false);
        settings.addProperty("restart_on_activation", true);
        // ── FIX: OBS tự thử lại mỗi 2 giây nếu stream chưa sẵn sàng ──
        settings.addProperty("looping", true);
        settings.addProperty("reconnect_delay_sec", 2);

        req.add("inputSettings", settings);
        return sendRequest("CreateInput", req).thenApply(res -> null);
    }

    /**
     * Cập nhật URL cho source đã tồn tại (dùng khi restart stream).
     */
    public CompletableFuture<Void> updateRtspSource(String source, String url) {
        JsonObject req = new JsonObject();
        req.addProperty("inputName", source);

        JsonObject settings = new JsonObject();
        settings.addProperty("input", url);
        settings.addProperty("is_local_file", false);
        settings.addProperty("clear_on_media_end", false);
        settings.addProperty("restart_on_activation", true);
        settings.addProperty("looping", true);
        settings.addProperty("reconnect_delay_sec", 2);

        req.add("inputSettings", settings);
        return sendRequest("SetInputSettings", req).thenApply(res -> null);
    }

    /**
     * Trigger OBS restart media source để nó reconnect RTSP ngay lập tức.
     * Gọi khi source đã có sẵn và cần kéo lại stream mới.
     */
    public CompletableFuture<Void> refreshMediaSource(String source) {
        JsonObject req = new JsonObject();
        req.addProperty("inputName", source);
        return sendRequest("TriggerMediaInputAction", buildMediaAction(source, "OBS_WEBSOCKET_MEDIA_INPUT_ACTION_RESTART"))
                .<Void>thenApply(res -> null)
                .exceptionally(e -> {
                    // TriggerMediaInputAction có thể không được support ở OBS cũ — bỏ qua
                    logger.accept("[OBS] refreshMediaSource fallback: " + e.getMessage());
                    return null;
                });
    }

    private JsonObject buildMediaAction(String source, String action) {
        JsonObject req = new JsonObject();
        req.addProperty("inputName", source);
        req.addProperty("mediaAction", action);
        return req;
    }

    // ── Virtual Camera ────────────────────────────────────────────────────────

    public CompletableFuture<Boolean> isVirtualCamActive() {
        return sendRequest("GetVirtualCamStatus", null)
                .thenApply(res -> res.get("outputActive").getAsBoolean());
    }

    /**
     * Bật hoặc tắt Virtual Camera.
     * Chỉ gửi lệnh toggle nếu trạng thái hiện tại khác với mong muốn.
     */
    public CompletableFuture<Void> setVirtualCamera(boolean active) {
        return isVirtualCamActive().thenCompose(isActive -> {
            if (isActive == active) return CompletableFuture.completedFuture(null);
            return sendRequest("ToggleVirtualCam", null).thenApply(res -> null);
        });
    }
}