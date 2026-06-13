package cn.bugstack.ai.trigger.http.support;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * description: 模型流式输出 JSON 聚合器。
 * 按完整 JSON 对象边界拆分 token，避免 PPT/draw.io 渲染结果被 partial=false 误截断。
 */
public class ChatStreamJsonAccumulator {

    private final StringBuilder buffer = new StringBuilder();

    /**
     * description: 追加模型流式文本，并返回当前可安全处理的完整片段。
     *
     * @param text input 模型本次输出 token 文本
     * @return output 完整 JSON 片段或普通过程文本片段
     */
    public List<Segment> append(String text) {
        List<Segment> segments = new ArrayList<>();

        if (StringUtils.isBlank(text)) {
            return segments;
        }

        buffer.append(text);
        drainCompleteSegments(segments);

        return segments;
    }

    /**
     * description: 流结束时清空剩余内容。
     *
     * @return output 剩余完整 JSON 或普通文本片段
     */
    public List<Segment> flush() {
        List<Segment> segments = new ArrayList<>();
        drainCompleteSegments(segments);

        String remainingText = StringUtils.trimToEmpty(buffer.toString());
        if (StringUtils.isNotBlank(remainingText)) {
            segments.add(new Segment(SegmentType.TEXT, remainingText));
            buffer.setLength(0);
        }

        return segments;
    }

    /**
     * description: 从 buffer 中持续提取可安全处理的完整片段。
     *
     * @param segments output 片段集合
     */
    private void drainCompleteSegments(List<Segment> segments) {
        while (buffer.length() > 0) {
            int jsonStartIndex = findNextJsonStartIndex();

            if (jsonStartIndex < 0) {
                String text = buffer.toString();
                if (isLikelyIncompleteJsonPrefix(text)) {
                    return;
                }

                addTextSegment(segments, text);
                buffer.setLength(0);
                return;
            }

            if (jsonStartIndex > 0) {
                String textBeforeJson = buffer.substring(0, jsonStartIndex);
                addTextSegment(segments, textBeforeJson);
                buffer.delete(0, jsonStartIndex);
            }

            int jsonEndIndex = findCompleteJsonEndIndex(buffer);
            if (jsonEndIndex < 0) {
                return;
            }

            String jsonText = buffer.substring(0, jsonEndIndex + 1).trim();
            segments.add(new Segment(SegmentType.JSON, jsonText));
            buffer.delete(0, jsonEndIndex + 1);
            trimLeadingSeparators();
        }
    }

    /**
     * description: 查找下一个可能的顶层 JSON 对象起点。
     *
     * @return output 起点下标，未找到返回 -1
     */
    private int findNextJsonStartIndex() {
        for (int index = 0; index < buffer.length(); index++) {
            char currentChar = buffer.charAt(index);
            if ('{' == currentChar) {
                return index;
            }
        }

        return -1;
    }

    /**
     * description: 判断文本是否可能是被 token 切开的 JSON 起始片段。
     *
     * @param text input 当前剩余文本
     * @return output true 表示继续等待后续 token
     */
    private boolean isLikelyIncompleteJsonPrefix(String text) {
        String trimmedText = StringUtils.trimToEmpty(text);
        return trimmedText.startsWith("{") || trimmedText.startsWith("\"type\"") || trimmedText.startsWith("type");
    }

    /**
     * description: 添加普通过程文本片段。
     *
     * @param segments output 片段集合
     * @param text     input 普通过程文本
     */
    private void addTextSegment(List<Segment> segments, String text) {
        String trimmedText = StringUtils.trimToEmpty(text);
        if (StringUtils.isBlank(trimmedText)) {
            return;
        }

        segments.add(new Segment(SegmentType.TEXT, trimmedText));
    }

    /**
     * description: 识别从 buffer[0] 开始的完整 JSON 对象结束位置。
     *
     * @param source input 当前 buffer
     * @return output 完整 JSON 对象结束下标，未完整返回 -1
     */
    private int findCompleteJsonEndIndex(StringBuilder source) {
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;

        for (int index = 0; index < source.length(); index++) {
            char currentChar = source.charAt(index);

            if (escaped) {
                escaped = false;
                continue;
            }

            if ('\\' == currentChar) {
                escaped = true;
                continue;
            }

            if ('"' == currentChar) {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if ('{' == currentChar) {
                depth++;
            } else if ('}' == currentChar) {
                depth--;
                if (0 == depth) {
                    return index;
                }
            }
        }

        return -1;
    }

    /**
     * description: 移除两个 JSON 对象之间的换行、空格或逗号分隔符。
     */
    private void trimLeadingSeparators() {
        while (buffer.length() > 0) {
            char currentChar = buffer.charAt(0);
            if (Character.isWhitespace(currentChar) || ',' == currentChar) {
                buffer.deleteCharAt(0);
                continue;
            }

            return;
        }
    }

    /**
     * description: 流式片段类型。
     */
    public enum SegmentType {
        JSON,
        TEXT
    }

    /**
     * description: 聚合后的流式片段。
     */
    @Getter
    @AllArgsConstructor
    public static class Segment {

        private final SegmentType type;
        private final String content;

    }

}
