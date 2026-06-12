package cn.bugstack.ai.trigger.http.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

/**
 * description: 渲染内容清理器，在响应前裁剪模型误输出的前后说明字符，
 * 并正规化 PPT 元素字段为前端标准 schema。
 */
public class ChatRenderContentSanitizer {

    private static final String MX_FILE_START = "<mxfile";
    private static final String MX_FILE_END = "</mxfile>";
    private static final String MX_GRAPH_MODEL_START = "<mxGraphModel";
    private static final String MX_GRAPH_MODEL_END = "</mxGraphModel>";
    private static final String XML_DECLARATION_START = "<?xml";
    private static final String SLIDES_KEY = "\"slides\"";

    /**
     * description: 清理 draw.io XML 内容，裁剪 XML 前后的非渲染文本。
     *
     * @param content input 模型返回的 draw.io content
     * @return output 裁剪后的 XML 字符串，无法识别 XML 时返回原始文本
     */
    public static String sanitizeDrawioContent(Object content) {
        String contentText = toContentText(content);
        String trimmedContentText = StringUtils.trimToEmpty(contentText);

        String mxfileXml = extractBetween(trimmedContentText, MX_FILE_START, MX_FILE_END);
        if (StringUtils.isNotBlank(mxfileXml)) {
            return mxfileXml;
        }

        String mxGraphModelXml = extractBetween(trimmedContentText, MX_GRAPH_MODEL_START, MX_GRAPH_MODEL_END);
        if (StringUtils.isNotBlank(mxGraphModelXml)) {
            return mxGraphModelXml;
        }

        return trimmedContentText;
    }

    /**
     * description: 清理 PPT 内容，兼容对象内容和字符串包裹的 JSON 内容。
     *
     * @param content input 模型返回的 PPT content
     * @return output 可渲染 PPT JSON 对象，无法解析时返回原始内容
     */
    public static Object sanitizePptContent(Object content) {
        if (content instanceof JSONObject) {
            return content;
        }

        String contentText = toContentText(content);
        String jsonText = extractJsonObjectContainingKey(contentText, SLIDES_KEY);
        if (StringUtils.isBlank(jsonText)) {
            return content;
        }

        try {
            return JSON.parseObject(jsonText);
        } catch (Exception e) {
            return content;
        }
    }

    /**
     * description: 正规化 PPT content 中所有 slides 的 elements，
     * 将旧字段（type/value/textAlign/fontWeight/fillColor）映射为标准 schema。
     *
     * @param content input PPT content（JSONObject 或 JSONObject 兼容对象）
     * @return output 正规化后的 content 对象
     */
    public static Object normalizePptElements(Object content) {
        if (!(content instanceof JSONObject)) {
            return content;
        }

        JSONObject contentJson = (JSONObject) content;
        if (contentJson.containsKey("elements")) {
            normalizeSlideElements(contentJson);
            return contentJson;
        }

        JSONArray slidesArray = contentJson.getJSONArray("slides");
        if (null == slidesArray || slidesArray.isEmpty()) {
            return content;
        }

        for (int i = 0; i < slidesArray.size(); i++) {
            JSONObject slideJson = slidesArray.getJSONObject(i);
            if (null == slideJson) {
                continue;
            }
            normalizeSlideElements(slideJson);
        }

        return contentJson;
    }

    /**
     * description: 正规化单页 slide 的 elements 数组。
     *
     * @param slideJson input/output 单页 slide JSON 对象
     */
    private static void normalizeSlideElements(JSONObject slideJson) {
        JSONArray elementsArray = slideJson.getJSONArray("elements");
        if (null == elementsArray) {
            return;
        }

        for (int j = 0; j < elementsArray.size(); j++) {
            JSONObject elementJson = elementsArray.getJSONObject(j);
            if (null == elementJson) {
                continue;
            }
            normalizeSingleElement(elementJson);
        }
    }

    /**
     * description: 正规化单个 PPT 元素，映射旧字段到标准 schema。
     *
     * @param elementJson input/output 单个元素 JSON 对象
     */
    private static void normalizeSingleElement(JSONObject elementJson) {
        // type → kind
        if (!elementJson.containsKey("kind") && elementJson.containsKey("type")) {
            elementJson.put("kind", elementJson.remove("type"));
        }

        // value / text / label / body / title → content（按优先级）
        if (!elementJson.containsKey("content")) {
            Object candidate = findFirstStringValue(elementJson, "value", "text", "label", "body", "title", "message");
            if (null != candidate) {
                elementJson.put("content", candidate);
            }
        }

        elementJson.remove("value");
        elementJson.remove("text");
        elementJson.remove("label");
        elementJson.remove("body");
        elementJson.remove("title");
        elementJson.remove("message");

        // textAlign → align
        if (elementJson.containsKey("textAlign") && !elementJson.containsKey("align")) {
            elementJson.put("align", elementJson.remove("textAlign"));
        }

        // fontWeight → bold
        if (elementJson.containsKey("fontWeight") && !elementJson.containsKey("bold")) {
            Object fw = elementJson.remove("fontWeight");
            elementJson.put("bold", "bold".equals(fw) || Boolean.TRUE.equals(fw));
        }

        // fillColor → fill
        if (elementJson.containsKey("fillColor") && !elementJson.containsKey("fill")) {
            elementJson.put("fill", elementJson.remove("fillColor"));
        }

        // 移除前端不消费的字段
        elementJson.remove("type");
        elementJson.remove("styleType");
        elementJson.remove("typeId");
        elementJson.remove("lineHeight");
    }

    /**
     * description: 按优先级顺序从 JSON 对象中查找第一个非空字符串值。
     */
    private static Object findFirstStringValue(JSONObject json, String... keys) {
        for (String key : keys) {
            Object value = json.get(key);
            if (value instanceof String && StringUtils.isNotBlank((String) value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * description: 从模型输出文本中截取第一个完整 JSON 对象，兼容前后多余说明文本。
     *
     * @param text input 模型原始输出文本
     * @return output 截取到的 JSON 对象文本，未找到时返回原文本
     */
    public static String sanitizeTopLevelJsonText(String text) {
        String trimmedText = StringUtils.trimToEmpty(text);
        String jsonText = extractFirstBalancedJsonObject(trimmedText);
        if (StringUtils.isBlank(jsonText)) {
            return trimmedText;
        }

        return jsonText;
    }

    /**
     * description: 根据起始和结束标记裁剪片段。
     *
     * @param text        input 原始文本
     * @param startMarker input 起始标记
     * @param endMarker   input 结束标记
     * @return output 裁剪后的片段，未找到完整片段时返回空字符串
     */
    private static String extractBetween(String text, String startMarker, String endMarker) {
        int startIndex = text.indexOf(startMarker);
        if (startIndex < 0) {
            return "";
        }

        int xmlDeclarationIndex = text.lastIndexOf(XML_DECLARATION_START, startIndex);
        if (xmlDeclarationIndex >= 0) {
            startIndex = xmlDeclarationIndex;
        }

        int endIndex = text.lastIndexOf(endMarker);
        if (endIndex < startIndex) {
            return "";
        }

        return text.substring(startIndex, endIndex + endMarker.length()).trim();
    }

    /**
     * description: 截取包含指定字段的 JSON 对象。
     *
     * @param text input 原始文本
     * @param key  input 必须包含的字段名
     * @return output JSON 对象文本，未找到时返回空字符串
     */
    private static String extractJsonObjectContainingKey(String text, String key) {
        String trimmedText = StringUtils.trimToEmpty(text);
        int keyIndex = trimmedText.indexOf(key);
        if (keyIndex < 0) {
            return "";
        }

        int startIndex = trimmedText.lastIndexOf('{', keyIndex);
        if (startIndex < 0) {
            return "";
        }

        String candidateText = trimmedText.substring(startIndex);
        return extractFirstBalancedJsonObject(candidateText);
    }

    /**
     * description: 从文本中截取第一个括号平衡的 JSON 对象，忽略字符串内部的大括号。
     *
     * @param text input 原始文本
     * @return output JSON 对象文本，未找到完整对象时返回空字符串
     */
    private static String extractFirstBalancedJsonObject(String text) {
        int startIndex = text.indexOf('{');
        if (startIndex < 0) {
            return "";
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;

        for (int index = startIndex; index < text.length(); index++) {
            char currentChar = text.charAt(index);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (currentChar == '\\') {
                escaped = true;
                continue;
            }

            if (currentChar == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (currentChar == '{') {
                depth++;
            } else if (currentChar == '}') {
                depth--;
                if (0 == depth) {
                    return text.substring(startIndex, index + 1).trim();
                }
            }
        }

        return "";
    }

    /**
     * description: 将内容转为文本。
     *
     * @param content input 任意 content 对象
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

}
