package cn.bugstack.ai.trigger.http.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * description: 智能体流式响应格式化器，将模型单行输出转换为前端统一识别的 NDJSON chunk 协议。
 * <p>
 * v2 协议新增 event / renderable / final 字段，前端按 event 类型决定渲染行为：
 * process_delta  → 思考过程增量
 * process_result → 阶段完成卡片
 * render_delta   → 可渲染增量（逐 slide / 逐节点）
 * render_result  → 最终可渲染结果
 * message        → 需要用户补充信息
 */
public class ChatStreamResponseFormatter {

    private static final String TYPE_DRAWIO_NODE = "drawio_node";
    private static final String TYPE_DRAWIO_EDGE = "drawio_edge";
    private static final String TYPE_DRAWIO_DONE = "drawio_done";
    private static final String TYPE_DRAWIO = "drawio";
    private static final String TYPE_PPT = "ppt";
    private static final String TYPE_PPT_SLIDE = "ppt_slide";
    private static final String TYPE_TOKEN = "token";
    private static final String TYPE_USER = "user";
    private static final String TYPE_STATUS = "status";
    private static final String TYPE_ERROR = "error";

    private static final String AUTHOR_ANALYST = "agent_analyst";
    private static final String AUTHOR_PPT_ANALYST = "agent_ppt_analyst";
    private static final String AUTHOR_DRAWER = "agent_drawer";
    private static final String AUTHOR_PPT_GENERATOR = "agent_ppt_generator";
    private static final String AUTHOR_REVIEWER = "agent_reviewer";
    private static final String AUTHOR_PPT_REVIEWER = "agent_ppt_reviewer";

    /**
     * description: 格式化单行模型输出为统一流式协议。
     *
     * @param phase  input 当前智能体执行阶段
     * @param author input 当前输出的 agent 名称
     * @param line   input 模型输出的单行文本
     * @return output 格式化后的 NDJSON 行（不含 seq）和绘图停顿标识
     */
    public static FormatResult format(String phase, String author, String line) {
        String responsePhase = StringUtils.defaultIfBlank(phase, "thinking");
        String responseAuthor = StringUtils.defaultIfBlank(author, "unknown");
        String responseLine = StringUtils.trimToEmpty(line);
        String jsonText = ChatRenderContentSanitizer.sanitizeTopLevelJsonText(responseLine);

        JSONObject jsonObject = parseJsonObject(jsonText);
        if (null != jsonObject) {
            return formatJson(responsePhase, responseAuthor, jsonObject);
        }

        return buildProcessDelta(responsePhase, responseAuthor, responseLine);
    }

    /**
     * description: 按 author + type 路由事件类型，构造统一 chunk。
     *
     * @param phase      input 当前智能体执行阶段
     * @param author     input 当前输出的 agent 名称
     * @param jsonObject input 已解析的 JSON 对象
     * @return output 格式化结果
     */
    private static FormatResult formatJson(String phase, String author, JSONObject jsonObject) {
        String type = jsonObject.getString("type");

        // === analyst 阶段：只发过程消息，绝不发 render_result ===
        if (isAnalyst(author)) {
            return formatAnalystOutput(phase, author, jsonObject, type);
        }

        // === generator / drawer 阶段：可发 render_delta（增量），不主动发 render_result ===
        if (isGenerator(author)) {
            return formatGeneratorOutput(phase, author, jsonObject, type);
        }

        // === reviewer 阶段：可发 render_result（最终结果）===
        if (isReviewer(author)) {
            return formatReviewerOutput(phase, author, jsonObject, type);
        }

        // === 未知 author：按 type 兜底 ===
        return formatUnknownOutput(phase, author, jsonObject, type);
    }

    // ======================== Analyst ========================

    private static boolean isAnalyst(String author) {
        return AUTHOR_ANALYST.equals(author) || AUTHOR_PPT_ANALYST.equals(author);
    }

    /**
     * description: analyst 输出一律为过程消息，不触发渲染。
     */
    private static FormatResult formatAnalystOutput(String phase, String author, JSONObject jsonObject, String type) {
        if (TYPE_USER.equals(type)) {
            return buildMessage(phase, author, jsonObject);
        }

        // analyst 输出的任何 JSON（即使含 slides/outline）都是分析数据，不是渲染数据
        return buildProcessResult(phase, author, jsonObject, type);
    }

    // ======================== Generator ========================

    private static boolean isGenerator(String author) {
        return AUTHOR_DRAWER.equals(author) || AUTHOR_PPT_GENERATOR.equals(author);
    }

    /**
     * description: generator 输出增量渲染节点/边/slide，完整 PPT 作为草稿过程消息。
     */
    private static FormatResult formatGeneratorOutput(String phase, String author, JSONObject jsonObject, String type) {
        // draw.io 逐节点增量
        if (TYPE_DRAWIO_NODE.equals(type) || TYPE_DRAWIO_EDGE.equals(type)) {
            return buildRenderDelta(phase, author, jsonObject, true);
        }

        // PPT 逐 slide 增量
        if (TYPE_PPT_SLIDE.equals(type)) {
            JSONObject chunk = buildPptSlideChunk(jsonObject);
            return buildRenderDelta(phase, author, chunk, false);
        }

        // draw.io done → 草稿过程消息，不是最终渲染
        if (TYPE_DRAWIO_DONE.equals(type)) {
            JSONObject chunk = copyJsonObject(jsonObject);
            chunk.put("type", TYPE_DRAWIO);
            sanitizeChunkContent(chunk, TYPE_DRAWIO);
            return buildProcessResult(phase, author, chunk, TYPE_DRAWIO);
        }

        // PPT 完整草稿 → 过程消息（generator 的 PPT 不是最终版）
        if (TYPE_PPT.equals(type)) {
            sanitizeChunkContent(jsonObject, TYPE_PPT);
            return buildProcessResult(phase, author, jsonObject, TYPE_PPT);
        }

        // 旧格式：无 type 但有 slides → 草稿过程消息
        if (StringUtils.isBlank(type) && jsonObject.containsKey("slides")) {
            JSONObject chunk = new JSONObject();
            chunk.put("type", TYPE_PPT);
            chunk.put("content", jsonObject);
            return buildProcessResult(phase, author, chunk, TYPE_PPT);
        }

        if (TYPE_USER.equals(type)) {
            return buildMessage(phase, author, jsonObject);
        }

        return buildProcessDelta(phase, author, jsonObject.toJSONString());
    }

    // ======================== Reviewer ========================

    private static boolean isReviewer(String author) {
        return AUTHOR_REVIEWER.equals(author) || AUTHOR_PPT_REVIEWER.equals(author);
    }

    /**
     * description: reviewer 输出最终可渲染结果。
     */
    private static FormatResult formatReviewerOutput(String phase, String author, JSONObject jsonObject, String type) {
        // drawio_done → 归一为 drawio 并发 render_result
        if (TYPE_DRAWIO_DONE.equals(type)) {
            JSONObject chunk = copyJsonObject(jsonObject);
            chunk.put("type", TYPE_DRAWIO);
            sanitizeChunkContent(chunk, TYPE_DRAWIO);
            return buildRenderResult(phase, author, chunk, false);
        }

        // PPT 旧格式：type=ppt 但有 slides 在根级 → 归一到 content
        if (TYPE_PPT.equals(type) && !jsonObject.containsKey("content") && jsonObject.containsKey("slides")) {
            JSONObject chunk = new JSONObject();
            chunk.put("type", TYPE_PPT);
            chunk.put("content", copyPptContent(jsonObject));
            if (jsonObject.containsKey("metadata")) {
                chunk.put("metadata", jsonObject.get("metadata"));
            }
            sanitizeChunkContent(chunk, TYPE_PPT);
            return buildRenderResult(phase, author, chunk, false);
        }

        // drawio / ppt → 最终渲染
        if (TYPE_DRAWIO.equals(type) || TYPE_PPT.equals(type)) {
            sanitizeChunkContent(jsonObject, TYPE_PPT.equals(type) ? TYPE_PPT : TYPE_DRAWIO);
            return buildRenderResult(phase, author, jsonObject, false);
        }

        // 旧格式：无 type 但有 slides → 最终渲染
        if (StringUtils.isBlank(type) && jsonObject.containsKey("slides")) {
            JSONObject chunk = new JSONObject();
            chunk.put("type", TYPE_PPT);
            chunk.put("content", jsonObject);
            sanitizeChunkContent(chunk, TYPE_PPT);
            return buildRenderResult(phase, author, chunk, false);
        }

        if (TYPE_USER.equals(type)) {
            return buildMessage(phase, author, jsonObject);
        }

        return buildProcessResult(phase, author, jsonObject, type);
    }

    // ======================== Unknown ========================

    /**
     * description: 未知 author 兜底，按 type 判断。
     */
    private static FormatResult formatUnknownOutput(String phase, String author, JSONObject jsonObject, String type) {
        if (TYPE_DRAWIO_DONE.equals(type)) {
            JSONObject chunk = copyJsonObject(jsonObject);
            chunk.put("type", TYPE_DRAWIO);
            sanitizeChunkContent(chunk, TYPE_DRAWIO);
            return buildRenderResult(phase, author, chunk, false);
        }

        if (TYPE_DRAWIO_NODE.equals(type) || TYPE_DRAWIO_EDGE.equals(type)) {
            return buildRenderDelta(phase, author, jsonObject, true);
        }

        if (TYPE_PPT_SLIDE.equals(type)) {
            JSONObject chunk = buildPptSlideChunk(jsonObject);
            return buildRenderDelta(phase, author, chunk, false);
        }

        if (TYPE_PPT.equals(type)) {
            sanitizeChunkContent(jsonObject, TYPE_PPT);
            return buildRenderResult(phase, author, jsonObject, false);
        }

        if (TYPE_DRAWIO.equals(type)) {
            sanitizeChunkContent(jsonObject, TYPE_DRAWIO);
            return buildRenderResult(phase, author, jsonObject, false);
        }

        if (TYPE_USER.equals(type)) {
            return buildMessage(phase, author, jsonObject);
        }

        if (jsonObject.containsKey("slides")) {
            JSONObject chunk = new JSONObject();
            chunk.put("type", TYPE_PPT);
            chunk.put("content", jsonObject);
            sanitizeChunkContent(chunk, TYPE_PPT);
            return buildRenderResult(phase, author, chunk, false);
        }

        return buildProcessDelta(phase, author, jsonObject.toJSONString());
    }

    // ======================== Chunk builders ========================

    /**
     * description: 构建 ppt_slide chunk，从原始 JSON 提取 slide content。
     */
    private static JSONObject buildPptSlideChunk(JSONObject jsonObject) {
        JSONObject chunk = new JSONObject();
        chunk.put("type", TYPE_PPT_SLIDE);
        Object slideContent = jsonObject.get("content");
        if (null != slideContent) {
            chunk.put("content", ChatRenderContentSanitizer.normalizePptElements(slideContent));
        }
        return chunk;
    }

    /**
     * description: 按 chunk 类型清理可渲染 content，避免前端收到带说明文字的主数据。
     */
    private static void sanitizeChunkContent(JSONObject chunk, String type) {
        if (TYPE_DRAWIO.equals(type)) {
            chunk.put("content", ChatRenderContentSanitizer.sanitizeDrawioContent(chunk.get("content")));
            return;
        }

        if (TYPE_PPT.equals(type)) {
            chunk.put("content", ChatRenderContentSanitizer.sanitizePptContent(chunk.get("content")));
            chunk.put("content", ChatRenderContentSanitizer.normalizePptElements(chunk.get("content")));
        }
    }

    // ======================== Wrapper builders ========================

    /**
     * description: 构造 render_result 事件（最终可渲染结果）。
     */
    private static FormatResult buildRenderResult(String phase, String author, JSONObject chunk, boolean drawPauseRequired) {
        return buildWrapper(phase, author, "render_result", true, true, chunk, drawPauseRequired);
    }

    /**
     * description: 构造 render_delta 事件（增量可渲染）。
     */
    private static FormatResult buildRenderDelta(String phase, String author, JSONObject chunk, boolean drawPauseRequired) {
        return buildWrapper(phase, author, "render_delta", true, false, chunk, drawPauseRequired);
    }

    /**
     * description: 构造 process_result 事件（阶段完成）。
     */
    private static FormatResult buildProcessResult(String phase, String author, JSONObject chunk, String type) {
        if (StringUtils.isBlank(chunk.getString("type"))) {
            chunk.put("type", StringUtils.defaultIfBlank(type, TYPE_STATUS));
        }
        return buildWrapper(phase, author, "process_result", false, true, chunk, false);
    }

    /**
     * description: 构造 process_delta 事件（过程 token 增量）。
     */
    private static FormatResult buildProcessDelta(String phase, String author, String content) {
        JSONObject chunk = new JSONObject();
        chunk.put("type", TYPE_TOKEN);
        chunk.put("content", content);
        return buildWrapper(phase, author, "process_delta", false, false, chunk, false);
    }

    /**
     * description: 构造 message 事件（需要用户补充信息）。
     */
    private static FormatResult buildMessage(String phase, String author, JSONObject jsonObject) {
        return buildWrapper(phase, author, "message", false, true, jsonObject, false);
    }

    /**
     * description: 构造统一 wrapper。
     */
    private static FormatResult buildWrapper(String phase, String author, String event,
                                              boolean renderable, boolean isFinal,
                                              JSONObject chunk, boolean drawPauseRequired) {
        JSONObject wrapper = new JSONObject();
        wrapper.put("phase", phase);
        wrapper.put("author", author);
        wrapper.put("event", event);
        wrapper.put("renderable", renderable);
        wrapper.put("final", isFinal);
        wrapper.put("chunk", chunk);
        return new FormatResult(wrapper.toJSONString(), drawPauseRequired);
    }

    // ======================== Helpers ========================

    private static JSONObject parseJsonObject(String line) {
        try {
            return JSON.parseObject(line);
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject copyJsonObject(JSONObject jsonObject) {
        return JSON.parseObject(jsonObject.toJSONString());
    }

    /**
     * description: 从旧 PPT 根级 JSON 中复制前端渲染需要的主内容。
     */
    private static JSONObject copyPptContent(JSONObject jsonObject) {
        JSONObject contentJsonObject = copyJsonObject(jsonObject);
        contentJsonObject.remove("type");
        contentJsonObject.remove("metadata");
        return contentJsonObject;
    }

    /**
     * description: 流式格式化结果，包含可发送的 NDJSON 行和绘图节奏控制标识。
     */
    @Getter
    @AllArgsConstructor
    public static class FormatResult {

        private final String jsonLine;
        private final boolean drawPauseRequired;

        /**
         * description: 输出带换行符的 NDJSON 行（不含 seq，由 Controller 注入）。
         */
        public String toNdjsonLine() {
            return jsonLine + "\n";
        }

    }

}
