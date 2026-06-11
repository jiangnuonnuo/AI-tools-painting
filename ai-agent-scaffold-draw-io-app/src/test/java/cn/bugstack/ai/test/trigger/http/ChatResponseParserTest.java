package cn.bugstack.ai.test.trigger.http;

import cn.bugstack.ai.api.dto.ChatResponseDTO;
import cn.bugstack.ai.trigger.http.support.ChatResponseParser;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

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

}
