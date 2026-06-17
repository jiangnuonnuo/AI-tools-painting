package cn.bugstack.ai.test.trigger.http;

import cn.bugstack.ai.api.dto.ChatResponseDTO;
import cn.bugstack.ai.trigger.http.support.ChatResponseParser;
import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * description: ChatResponseParser 单元测试，验证模型输出规范化和非法 drawio 内容回退。
 */
public class ChatResponseParserTest {

    /**
     * description: 验证 Markdown 代码围栏包裹的 drawio JSON 可以解析为 drawio 响应。
     */
    @Test
    public void test_parse_should_extract_drawio_json_from_markdown_code_block() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><mxfile><diagram><mxGraphModel><root><mxCell id=\"0\" /></root></mxGraphModel></diagram></mxfile>";
        String modelOutput = "```json\n{\"type\":\"drawio\",\"content\":\"" + xml.replace("\"", "\\\"") + "\"}\n```";

        ChatResponseDTO responseDTO = ChatResponseParser.parse(List.of(modelOutput));

        Assert.assertEquals("drawio", responseDTO.getType());
        Assert.assertEquals(xml, responseDTO.getContent());
    }

    /**
     * description: 验证 drawio 类型但 content 不是 XML 时回退为 user 响应。
     */
    @Test
    public void test_parse_should_fallback_to_user_when_drawio_content_is_not_xml() {
        String modelOutput = "{\"type\":\"drawio\",\"content\":\"not xml\"}";

        ChatResponseDTO responseDTO = ChatResponseParser.parse(List.of(modelOutput));

        Assert.assertEquals("user", responseDTO.getType());
        Assert.assertEquals(modelOutput, responseDTO.getContent());
    }

    /**
     * description: 验证 drawio_done 类型会归一为 drawio，并保留可渲染 XML 主内容。
     */
    @Test
    public void test_parse_should_normalize_drawio_done_to_drawio() {
        String xml = "<mxGraphModel><root><mxCell id=\"0\" /></root></mxGraphModel>";
        String modelOutput = "{\"type\":\"drawio_done\",\"content\":\"" + xml.replace("\"", "\\\"") + "\"}";

        ChatResponseDTO responseDTO = ChatResponseParser.parse(List.of(modelOutput));

        Assert.assertEquals("drawio", responseDTO.getType());
        Assert.assertEquals(xml, responseDTO.getContent());
    }

    /**
     * description: 验证 PPT 响应会保留 content 中的结构化对象，供前端直接渲染。
     */
    @Test
    public void test_parse_should_keep_ppt_content_as_structured_object() {
        String modelOutput = "{\"type\":\"ppt\",\"content\":{\"title\":\"产品方案\",\"slides\":[{\"slideIndex\":1,\"layout\":\"title_slide\",\"elements\":[]}]}}";

        ChatResponseDTO responseDTO = ChatResponseParser.parse(List.of(modelOutput));

        Object content = responseDTO.getContent();

        Assert.assertEquals("ppt", responseDTO.getType());
        Assert.assertTrue(content instanceof JSONObject);
        Assert.assertEquals("产品方案", ((JSONObject) content).getString("title"));
        Assert.assertEquals(1, ((JSONObject) content).getJSONArray("slides").size());
    }

    /**
     * description: 验证 metadata 作为辅助信息被原样保留，不参与主渲染内容判断。
     */
    @Test
    public void test_parse_should_keep_metadata_as_optional_map() {
        String xml = "<mxGraphModel><root><mxCell id=\"0\" /></root></mxGraphModel>";
        String modelOutput = "{\"type\":\"drawio\",\"content\":\"" + xml.replace("\"", "\\\"") + "\",\"metadata\":{\"backendContent\":\"已生成可渲染的 draw.io 图表。\",\"summary\":\"已生成流程图\",\"progress\":100,\"suggestions\":[\"补充分支\"]}}";

        ChatResponseDTO responseDTO = ChatResponseParser.parse(List.of(modelOutput));
        Map<String, Object> metadata = responseDTO.getMetadata();

        Assert.assertEquals("drawio", responseDTO.getType());
        Assert.assertEquals(xml, responseDTO.getContent());
        Assert.assertEquals("已生成可渲染的 draw.io 图表。", metadata.get("backendContent"));
        Assert.assertEquals("已生成流程图", metadata.get("summary"));
        Assert.assertEquals(100, metadata.get("progress"));
        Assert.assertTrue(metadata.containsKey("suggestions"));
    }

    /**
     * description: 验证 metadata 不是强制字段，模型未返回时保持为空。
     */
    @Test
    public void test_parse_should_allow_empty_metadata() {
        String modelOutput = "{\"type\":\"ppt\",\"content\":{\"title\":\"产品方案\",\"slides\":[]}}";

        ChatResponseDTO responseDTO = ChatResponseParser.parse(List.of(modelOutput));

        Assert.assertEquals("ppt", responseDTO.getType());
        Assert.assertNull(responseDTO.getMetadata());
    }

    /**
     * description: 验证顶层 JSON 前后存在模型说明字符时，解析器会截取合法 JSON 并返回可渲染内容。
     */
    @Test
    public void test_parse_should_extract_top_level_json_when_wrapped_by_extra_text() {
        String xml = "<mxGraphModel><root><mxCell id=\"0\" /></root></mxGraphModel>";
        String modelOutput = "最终结果如下：{\"type\":\"drawio\",\"content\":\"" + xml.replace("\"", "\\\"") + "\",\"metadata\":{}}请直接渲染。";

        ChatResponseDTO responseDTO = ChatResponseParser.parse(List.of(modelOutput));

        Assert.assertEquals("drawio", responseDTO.getType());
        Assert.assertEquals(xml, responseDTO.getContent());
    }

    /**
     * description: 验证 draw.io content 前后混入说明文字时，返回前会裁剪为纯 XML。
     */
    @Test
    public void test_parse_should_sanitize_drawio_content_before_return() {
        String xml = "<mxGraphModel><root><mxCell id=\"0\" /></root></mxGraphModel>";
        String dirtyContent = "下面是生成结果：" + xml + "。以上 XML 可直接渲染。";
        String modelOutput = "{\"type\":\"drawio\",\"content\":\"" + dirtyContent.replace("\"", "\\\"") + "\",\"metadata\":{}}";

        ChatResponseDTO responseDTO = ChatResponseParser.parse(List.of(modelOutput));

        Assert.assertEquals("drawio", responseDTO.getType());
        Assert.assertEquals(xml, responseDTO.getContent());
    }

    /**
     * description: 验证 PPT content 被字符串包裹且前后有说明时，返回前会裁剪并解析为结构化对象。
     */
    @Test
    public void test_parse_should_sanitize_ppt_content_string_before_return() {
        String pptJson = "{\"title\":\"产品方案\",\"slides\":[{\"slideIndex\":1,\"layout\":\"title_slide\",\"elements\":[]}]}";
        String dirtyContent = "PPT JSON 如下：" + pptJson + "。已完成。";
        String modelOutput = "{\"type\":\"ppt\",\"content\":\"" + dirtyContent.replace("\"", "\\\"") + "\",\"metadata\":{}}";

        ChatResponseDTO responseDTO = ChatResponseParser.parse(List.of(modelOutput));
        JSONObject contentJsonObject = (JSONObject) responseDTO.getContent();

        Assert.assertEquals("ppt", responseDTO.getType());
        Assert.assertEquals("产品方案", contentJsonObject.getString("title"));
        Assert.assertEquals(1, contentJsonObject.getJSONArray("slides").size());
    }

    /**
     * description: 验证 PPT 旧元素字段会正规化为前端渲染 schema。
     */
    @Test
    public void test_parse_should_normalize_legacy_ppt_element_fields() {
        String modelOutput = "{\"type\":\"ppt\",\"content\":{\"title\":\"产品方案\",\"slides\":[{\"slideIndex\":1,\"layout\":\"title_slide\",\"elements\":[{\"type\":\"text\",\"value\":\"标题\",\"textAlign\":\"center\",\"fontWeight\":\"bold\",\"fillColor\":\"FFFFFF\",\"styleType\":\"title\"}]}]}}";

        ChatResponseDTO responseDTO = ChatResponseParser.parse(List.of(modelOutput));
        JSONObject contentJsonObject = (JSONObject) responseDTO.getContent();
        JSONObject elementJsonObject = contentJsonObject.getJSONArray("slides")
                .getJSONObject(0)
                .getJSONArray("elements")
                .getJSONObject(0);

        Assert.assertEquals("ppt", responseDTO.getType());
        Assert.assertEquals("text", elementJsonObject.getString("kind"));
        Assert.assertEquals("标题", elementJsonObject.getString("content"));
        Assert.assertEquals("center", elementJsonObject.getString("align"));
        Assert.assertTrue(elementJsonObject.getBooleanValue("bold"));
        Assert.assertEquals("FFFFFF", elementJsonObject.getString("fill"));
        Assert.assertFalse(elementJsonObject.containsKey("type"));
        Assert.assertFalse(elementJsonObject.containsKey("value"));
        Assert.assertFalse(elementJsonObject.containsKey("styleType"));
    }

    /**
     * description: 验证 Prompt 响应会保留完整纯文本 Prompt，供前端复制和继续改写。
     */
    @Test
    public void test_parse_should_support_prompt_text_content() {
        String prompt = "你是一个后端工程师，请根据需求输出实现计划。";
        String modelOutput = "{\"type\":\"prompt\",\"content\":\"" + prompt + "\",\"metadata\":{\"backendContent\":\"已生成 Prompt。\"}}";

        ChatResponseDTO responseDTO = ChatResponseParser.parse(List.of(modelOutput));

        Assert.assertEquals("prompt", responseDTO.getType());
        Assert.assertEquals(prompt, responseDTO.getContent());
        Assert.assertEquals("已生成 Prompt。", responseDTO.getMetadata().get("backendContent"));
    }

    /**
     * description: 验证 Prompt JSON 被 Markdown 代码围栏包裹时仍可解析。
     */
    @Test
    public void test_parse_should_extract_prompt_json_from_markdown_code_block() {
        String modelOutput = "```json\n{\"type\":\"prompt\",\"content\":\"请重构当前提示词。\",\"metadata\":{}}\n```";

        ChatResponseDTO responseDTO = ChatResponseParser.parse(List.of(modelOutput));

        Assert.assertEquals("prompt", responseDTO.getType());
        Assert.assertEquals("请重构当前提示词。", responseDTO.getContent());
    }

    /**
     * description: 验证 Prompt 类型 content 为空时回退为 user 响应，避免前端拿到不可用 Prompt。
     */
    @Test
    public void test_parse_should_fallback_to_user_when_prompt_content_is_blank() {
        String modelOutput = "{\"type\":\"prompt\",\"content\":\"   \",\"metadata\":{}}";

        ChatResponseDTO responseDTO = ChatResponseParser.parse(List.of(modelOutput));

        Assert.assertEquals("user", responseDTO.getType());
        Assert.assertEquals(modelOutput, responseDTO.getContent());
    }

}
