package cn.bugstack.ai.test.trigger.http;

import cn.bugstack.ai.trigger.http.support.ChatStreamResponseFormatter;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;

/**
 * description: ChatStreamResponseFormatter 单元测试，
 * 验证流式响应行会被格式化为前端可识别的 v2 统一事件协议。
 */
public class ChatStreamResponseFormatterTest {

    /**
     * description: 验证 analyst 阶段即使输出 slides 字段，也不会被识别为 PPT 渲染结果。
     */
    @Test
    public void test_format_should_keep_ppt_analysis_as_process_result() {
        String line = "{\"type\":\"analysis\",\"content\":{\"theme\":\"微服务\",\"slides\":[{\"title\":\"封面\"}]}}";

        ChatStreamResponseFormatter.FormatResult formatResult = ChatStreamResponseFormatter.format("analyzing", "agent_ppt_analyst", line);
        JSONObject wrapper = JSON.parseObject(formatResult.toNdjsonLine());
        JSONObject chunk = wrapper.getJSONObject("chunk");

        Assert.assertEquals("analyzing", wrapper.getString("phase"));
        Assert.assertEquals("agent_ppt_analyst", wrapper.getString("author"));
        Assert.assertEquals("process_result", wrapper.getString("event"));
        Assert.assertFalse(wrapper.getBooleanValue("renderable"));
        Assert.assertTrue(wrapper.getBooleanValue("final"));
        Assert.assertEquals("analysis", chunk.getString("type"));
    }

    /**
     * description: 验证 PPT generator 的 ppt_slide 会输出 render_delta，供前端逐页增量渲染。
     */
    @Test
    public void test_format_should_emit_ppt_slide_as_render_delta() {
        String line = "{\"type\":\"ppt_slide\",\"content\":{\"slideIndex\":1,\"layout\":\"title_slide\",\"elements\":[{\"type\":\"text\",\"value\":\"标题\",\"textAlign\":\"center\",\"fontWeight\":\"bold\"}]}}";

        ChatStreamResponseFormatter.FormatResult formatResult = ChatStreamResponseFormatter.format("generating", "agent_ppt_generator", line);
        JSONObject wrapper = JSON.parseObject(formatResult.toNdjsonLine());
        JSONObject chunk = wrapper.getJSONObject("chunk");
        JSONObject slide = chunk.getJSONObject("content");
        JSONObject element = slide.getJSONArray("elements").getJSONObject(0);

        Assert.assertEquals("generating", wrapper.getString("phase"));
        Assert.assertEquals("agent_ppt_generator", wrapper.getString("author"));
        Assert.assertEquals("render_delta", wrapper.getString("event"));
        Assert.assertTrue(wrapper.getBooleanValue("renderable"));
        Assert.assertFalse(wrapper.getBooleanValue("final"));
        Assert.assertEquals("ppt_slide", chunk.getString("type"));
        Assert.assertEquals("text", element.getString("kind"));
        Assert.assertEquals("标题", element.getString("content"));
        Assert.assertEquals("center", element.getString("align"));
        Assert.assertTrue(element.getBooleanValue("bold"));
        Assert.assertFalse(element.containsKey("type"));
        Assert.assertFalse(element.containsKey("value"));
    }

    /**
     * description: 验证 PPT generator 的完整 PPT 只作为草稿过程结果，不作为最终渲染结果。
     */
    @Test
    public void test_format_should_emit_generator_ppt_as_process_result() {
        String line = "{\"type\":\"ppt\",\"content\":{\"title\":\"草稿\",\"slides\":[]},\"metadata\":{\"backendContent\":\"草稿已生成\"}}";

        ChatStreamResponseFormatter.FormatResult formatResult = ChatStreamResponseFormatter.format("generating", "agent_ppt_generator", line);
        JSONObject wrapper = JSON.parseObject(formatResult.toNdjsonLine());
        JSONObject chunk = wrapper.getJSONObject("chunk");

        Assert.assertEquals("process_result", wrapper.getString("event"));
        Assert.assertFalse(wrapper.getBooleanValue("renderable"));
        Assert.assertTrue(wrapper.getBooleanValue("final"));
        Assert.assertEquals("ppt", chunk.getString("type"));
        Assert.assertEquals("草稿", chunk.getJSONObject("content").getString("title"));
    }

    /**
     * description: 验证 PPT reviewer 的最终 PPT 输出 render_result。
     */
    @Test
    public void test_format_should_emit_reviewer_ppt_as_render_result() {
        String line = "{\"type\":\"ppt\",\"content\":{\"title\":\"最终版\",\"slides\":[{\"slideIndex\":1,\"layout\":\"title_slide\",\"elements\":[]}]},\"metadata\":{\"backendContent\":\"已生成可渲染的 PPT 结构化数据。\",\"progress\":100}}";

        ChatStreamResponseFormatter.FormatResult formatResult = ChatStreamResponseFormatter.format("reviewing", "agent_ppt_reviewer", line);
        JSONObject wrapper = JSON.parseObject(formatResult.toNdjsonLine());
        JSONObject chunk = wrapper.getJSONObject("chunk");

        Assert.assertEquals("reviewing", wrapper.getString("phase"));
        Assert.assertEquals("agent_ppt_reviewer", wrapper.getString("author"));
        Assert.assertEquals("render_result", wrapper.getString("event"));
        Assert.assertTrue(wrapper.getBooleanValue("renderable"));
        Assert.assertTrue(wrapper.getBooleanValue("final"));
        Assert.assertEquals("ppt", chunk.getString("type"));
        Assert.assertEquals("最终版", chunk.getJSONObject("content").getString("title"));
        Assert.assertEquals(100, chunk.getJSONObject("metadata").getIntValue("progress"));
    }

    /**
     * description: 验证 drawio_node 增量绘制事件会作为 render_delta 透传，并要求发送后短暂停顿。
     */
    @Test
    public void test_format_should_emit_drawio_node_as_render_delta_and_require_pause() {
        String line = "{\"type\":\"drawio_node\",\"id\":\"2\",\"label\":\"开始\",\"xml\":\"<mxCell id='2'/>\"}";

        ChatStreamResponseFormatter.FormatResult formatResult = ChatStreamResponseFormatter.format("drawing", "agent_drawer", line);
        JSONObject wrapper = JSON.parseObject(formatResult.toNdjsonLine());
        JSONObject chunk = wrapper.getJSONObject("chunk");

        Assert.assertTrue(formatResult.isDrawPauseRequired());
        Assert.assertEquals("drawing", wrapper.getString("phase"));
        Assert.assertEquals("agent_drawer", wrapper.getString("author"));
        Assert.assertEquals("render_delta", wrapper.getString("event"));
        Assert.assertTrue(wrapper.getBooleanValue("renderable"));
        Assert.assertFalse(wrapper.getBooleanValue("final"));
        Assert.assertEquals("drawio_node", chunk.getString("type"));
        Assert.assertEquals("2", chunk.getString("id"));
    }

    /**
     * description: 验证 drawio_done 在 drawer 阶段只作为过程结果，不作为最终渲染结果。
     */
    @Test
    public void test_format_should_emit_drawer_drawio_done_as_process_result() {
        String xml = "<mxGraphModel><root><mxCell id=\"0\" /></root></mxGraphModel>";
        String line = "{\"type\":\"drawio_done\",\"content\":\"" + xml.replace("\"", "\\\"") + "\"}";

        ChatStreamResponseFormatter.FormatResult formatResult = ChatStreamResponseFormatter.format("drawing", "agent_drawer", line);
        JSONObject wrapper = JSON.parseObject(formatResult.toNdjsonLine());
        JSONObject chunk = wrapper.getJSONObject("chunk");

        Assert.assertEquals("process_result", wrapper.getString("event"));
        Assert.assertFalse(wrapper.getBooleanValue("renderable"));
        Assert.assertEquals("drawio", chunk.getString("type"));
        Assert.assertEquals(xml, chunk.getString("content"));
    }

    /**
     * description: 验证 drawio reviewer 最终输出 render_result。
     */
    @Test
    public void test_format_should_emit_reviewer_drawio_as_render_result() {
        String xml = "<mxGraphModel><root><mxCell id=\"0\" /></root></mxGraphModel>";
        String line = "{\"type\":\"drawio\",\"content\":\"" + xml.replace("\"", "\\\"") + "\",\"metadata\":{\"backendContent\":\"已生成可渲染的 draw.io 图表。\"}}";

        ChatStreamResponseFormatter.FormatResult formatResult = ChatStreamResponseFormatter.format("reviewing", "agent_reviewer", line);
        JSONObject wrapper = JSON.parseObject(formatResult.toNdjsonLine());
        JSONObject chunk = wrapper.getJSONObject("chunk");

        Assert.assertEquals("render_result", wrapper.getString("event"));
        Assert.assertTrue(wrapper.getBooleanValue("renderable"));
        Assert.assertTrue(wrapper.getBooleanValue("final"));
        Assert.assertEquals("drawio", chunk.getString("type"));
        Assert.assertEquals(xml, chunk.getString("content"));
        Assert.assertEquals("已生成可渲染的 draw.io 图表。", chunk.getJSONObject("metadata").getString("backendContent"));
    }

    /**
     * description: 验证普通文本会转换为 process_delta token，用于前端展示生成过程。
     */
    @Test
    public void test_format_should_turn_plain_text_into_process_delta_token() {
        ChatStreamResponseFormatter.FormatResult formatResult = ChatStreamResponseFormatter.format("analyzing", "agent_ppt_analyst", "正在分析需求");
        JSONObject wrapper = JSON.parseObject(formatResult.toNdjsonLine());
        JSONObject chunk = wrapper.getJSONObject("chunk");

        Assert.assertFalse(formatResult.isDrawPauseRequired());
        Assert.assertEquals("analyzing", wrapper.getString("phase"));
        Assert.assertEquals("agent_ppt_analyst", wrapper.getString("author"));
        Assert.assertEquals("process_delta", wrapper.getString("event"));
        Assert.assertFalse(wrapper.getBooleanValue("renderable"));
        Assert.assertFalse(wrapper.getBooleanValue("final"));
        Assert.assertEquals("token", chunk.getString("type"));
        Assert.assertEquals("正在分析需求", chunk.getString("content"));
    }

}
