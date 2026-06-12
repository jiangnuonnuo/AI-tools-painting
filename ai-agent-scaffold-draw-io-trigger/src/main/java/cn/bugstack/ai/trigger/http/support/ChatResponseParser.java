package cn.bugstack.ai.trigger.http.support;

import cn.bugstack.ai.api.dto.ChatResponseDTO;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * description: 智能体对话响应解析器，将模型文本输出规范化为前端可识别的 ChatResponseDTO。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
public class ChatResponseParser {

    private static final String TYPE_USER = "user";
    private static final String TYPE_DRAWIO = "drawio";
    private static final String TYPE_DRAWIO_DONE = "drawio_done";
    private static final String TYPE_PPT = "ppt";

    /**
     * description: 解析智能体消息列表的最后一条输出，返回稳定的对话响应结构。
     *
     * @param messages input 智能体事件输出文本列表
     * @return output 规范化后的 ChatResponseDTO，无法解析或校验失败时返回 user 类型
     */
    public static ChatResponseDTO parse(List<String> messages) {
        String result = lastMessage(messages);
        ChatResponseDTO fallbackResponseDTO = buildUserResponse(joinMessages(messages));

        if (StringUtils.isBlank(result)) {
            return fallbackResponseDTO;
        }

        String jsonText = normalizeJsonText(result);
        ChatResponseDTO parsedResponseDTO = parseJson(jsonText);

        if (null == parsedResponseDTO) {
            return fallbackResponseDTO;
        }

        String responseType = StringUtils.defaultIfBlank(parsedResponseDTO.getType(), TYPE_USER);
        if (TYPE_DRAWIO_DONE.equals(responseType)) {
            responseType = TYPE_DRAWIO;
        }
        parsedResponseDTO.setType(responseType);

        sanitizeRenderContent(parsedResponseDTO, responseType);

        if (TYPE_DRAWIO.equals(responseType) && !isDrawioXml(parsedResponseDTO.getContent())) {
            return fallbackResponseDTO;
        }

        if (TYPE_PPT.equals(responseType) && !isPptContent(parsedResponseDTO.getContent())) {
            return fallbackResponseDTO;
        }

        if (TYPE_USER.equals(responseType) && StringUtils.isBlank(toContentText(parsedResponseDTO.getContent()))) {
            return fallbackResponseDTO;
        }

        return parsedResponseDTO;
    }

    /**
     * description: 按响应类型清理机器可渲染 content，裁剪模型误输出的前后说明字符。
     *
     * @param responseDTO  input/output 待清理的响应对象
     * @param responseType input 响应类型
     */
    private static void sanitizeRenderContent(ChatResponseDTO responseDTO, String responseType) {
        if (TYPE_DRAWIO.equals(responseType)) {
            responseDTO.setContent(ChatRenderContentSanitizer.sanitizeDrawioContent(responseDTO.getContent()));
            return;
        }

        if (TYPE_PPT.equals(responseType)) {
            Object pptContent = ChatRenderContentSanitizer.sanitizePptContent(responseDTO.getContent());
            pptContent = ChatRenderContentSanitizer.normalizePptElements(pptContent);
            responseDTO.setContent(pptContent);
        }
    }

    /**
     * description: 获取最后一条智能体输出。
     *
     * @param messages input 智能体事件输出文本列表
     * @return output 最后一条输出文本，列表为空时返回空字符串
     */
    private static String lastMessage(List<String> messages) {
        if (null == messages || messages.isEmpty()) {
            return "";
        }

        return messages.get(messages.size() - 1);
    }

    /**
     * description: 合并智能体输出作为普通聊天内容兜底返回。
     *
     * @param messages input 智能体事件输出文本列表
     * @return output 合并后的输出文本
     */
    private static String joinMessages(List<String> messages) {
        if (null == messages || messages.isEmpty()) {
            return "";
        }

        return String.join("\n", messages);
    }

    /**
     * description: 清理模型常见包裹格式，提取可解析 JSON 文本。
     *
     * @param text input 模型原始输出文本
     * @return output 去除 Markdown 代码围栏后的 JSON 文本
     */
    private static String normalizeJsonText(String text) {
        String trimmedText = StringUtils.trimToEmpty(text);

        if (trimmedText.startsWith("```")) {
            trimmedText = stripMarkdownCodeFence(trimmedText);
        }

        return ChatRenderContentSanitizer.sanitizeTopLevelJsonText(trimmedText);
    }

    /**
     * description: 去除 Markdown 代码围栏，兼容 ```json 和普通 ``` 包裹。
     *
     * @param text input Markdown 代码块文本
     * @return output 代码块内部文本
     */
    private static String stripMarkdownCodeFence(String text) {
        int firstLineEndIndex = text.indexOf('\n');
        int lastFenceIndex = text.lastIndexOf("```");

        if (firstLineEndIndex < 0 || lastFenceIndex <= firstLineEndIndex) {
            return text;
        }

        String contentText = text.substring(firstLineEndIndex + 1, lastFenceIndex);
        return StringUtils.trimToEmpty(contentText);
    }

    /**
     * description: 将 JSON 文本解析为 ChatResponseDTO。
     *
     * @param jsonText input JSON 文本
     * @return output 解析后的 ChatResponseDTO，解析失败时返回 null
     */
    private static ChatResponseDTO parseJson(String jsonText) {
        try {
            JSONObject jsonObject = JSON.parseObject(jsonText);
            if (null == jsonObject) {
                return null;
            }

            ChatResponseDTO responseDTO = new ChatResponseDTO();
            responseDTO.setType(jsonObject.getString("type"));
            responseDTO.setContent(jsonObject.get("content"));
            responseDTO.setMetadata(parseMetadata(jsonObject));
            return responseDTO;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * description: 解析可选 metadata 辅助信息。
     *
     * @param jsonObject input 模型输出 JSON 对象
     * @return output metadata 字段对应的 Map，对象不存在时返回 null
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMetadata(JSONObject jsonObject) {
        JSONObject metadataJsonObject = jsonObject.getJSONObject("metadata");
        if (null == metadataJsonObject) {
            return null;
        }

        return JSON.parseObject(metadataJsonObject.toJSONString(), Map.class);
    }

    /**
     * description: 校验 drawio 类型的 content 是否具备 draw.io XML 基本结构。
     *
     * @param content input drawio 响应内容
     * @return output true 表示内容可作为 draw.io XML 交给前端渲染
     */
    private static boolean isDrawioXml(Object content) {
        String contentText = toContentText(content);
        if (StringUtils.isBlank(contentText)) {
            return false;
        }

        String trimmedContent = contentText.trim();
        return trimmedContent.contains("<mxfile") || trimmedContent.contains("<mxGraphModel");
    }

    /**
     * description: 校验 PPT 类型的 content 是否为前端可渲染的结构化对象。
     *
     * @param content input PPT 响应主内容
     * @return output true 表示内容包含 slides 结构，可交给前端 PPT 渲染组件
     */
    private static boolean isPptContent(Object content) {
        if (!(content instanceof JSONObject)) {
            return false;
        }

        JSONObject contentJsonObject = (JSONObject) content;
        return contentJsonObject.containsKey("slides");
    }

    /**
     * description: 将 content 转为文本，用于 user 文本校验和 draw.io XML 校验。
     *
     * @param content input 响应主内容
     * @return output 文本内容，空值返回空字符串
     */
    private static String toContentText(Object content) {
        if (null == content) {
            return "";
        }

        if (content instanceof String) {
            return (String) content;
        }

        return JSON.toJSONString(content);
    }

    /**
     * description: 构造普通聊天响应。
     *
     * @param content input 聊天展示内容
     * @return output user 类型 ChatResponseDTO
     */
    private static ChatResponseDTO buildUserResponse(String content) {
        ChatResponseDTO responseDTO = new ChatResponseDTO();
        responseDTO.setType(TYPE_USER);
        responseDTO.setContent(content);
        return responseDTO;
    }

}
