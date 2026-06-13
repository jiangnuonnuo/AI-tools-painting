package cn.bugstack.ai.test.trigger.http;

import cn.bugstack.ai.trigger.http.support.ChatStreamJsonAccumulator;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * description: ChatStreamJsonAccumulator 单元测试，
 * 验证模型流式 token 被正确聚合为完整 JSON 渲染事件。
 */
public class ChatStreamJsonAccumulatorTest {

    /**
     * description: 验证 PPT 最终 JSON 被拆成多个 token 时，
     * 聚合器必须等待完整 JSON 对象闭合后才输出。
     */
    @Test
    public void test_append_should_wait_until_complete_ppt_json() {
        ChatStreamJsonAccumulator accumulator = new ChatStreamJsonAccumulator();

        List<ChatStreamJsonAccumulator.Segment> firstSegments = accumulator.append("{\"type\":\"ppt\",\"content\":{\"title\":\"最终版\",");
        List<ChatStreamJsonAccumulator.Segment> secondSegments = accumulator.append("\"slides\":[{\"slideIndex\":1,\"layout\":\"title_slide\",");

        Assert.assertTrue(firstSegments.isEmpty());
        Assert.assertTrue(secondSegments.isEmpty());

        List<ChatStreamJsonAccumulator.Segment> finalSegments = accumulator.append("\"elements\":[]}]},\"metadata\":{\"backendContent\":\"已生成可渲染的 PPT 结构化数据。\"}}");

        Assert.assertEquals(1, finalSegments.size());
        Assert.assertEquals(ChatStreamJsonAccumulator.SegmentType.JSON, finalSegments.get(0).getType());

        JSONObject jsonObject = JSON.parseObject(finalSegments.get(0).getContent());
        Assert.assertEquals("ppt", jsonObject.getString("type"));
        Assert.assertEquals("最终版", jsonObject.getJSONObject("content").getString("title"));
        Assert.assertEquals(1, jsonObject.getJSONObject("content").getJSONArray("slides").size());
    }

    /**
     * description: 验证同一个流片段中包含多个 JSON 对象时，
     * 聚合器会按顺序拆出多个完整渲染片段。
     */
    @Test
    public void test_append_should_extract_multiple_json_objects() {
        ChatStreamJsonAccumulator accumulator = new ChatStreamJsonAccumulator();

        String firstSlide = "{\"type\":\"ppt_slide\",\"content\":{\"slideIndex\":1,\"layout\":\"title_slide\",\"elements\":[]}}";
        String secondSlide = "{\"type\":\"ppt_slide\",\"content\":{\"slideIndex\":2,\"layout\":\"content_slide\",\"elements\":[]}}";

        List<ChatStreamJsonAccumulator.Segment> segments = accumulator.append(firstSlide + "\n" + secondSlide);

        Assert.assertEquals(2, segments.size());
        Assert.assertEquals(ChatStreamJsonAccumulator.SegmentType.JSON, segments.get(0).getType());
        Assert.assertEquals(ChatStreamJsonAccumulator.SegmentType.JSON, segments.get(1).getType());
        Assert.assertEquals(1, JSON.parseObject(segments.get(0).getContent()).getJSONObject("content").getIntValue("slideIndex"));
        Assert.assertEquals(2, JSON.parseObject(segments.get(1).getContent()).getJSONObject("content").getIntValue("slideIndex"));
    }

    /**
     * description: 验证普通文本仍会作为过程文本输出，
     * 不影响 AI 思考过程在前端展示。
     */
    @Test
    public void test_append_should_emit_plain_text_segment() {
        ChatStreamJsonAccumulator accumulator = new ChatStreamJsonAccumulator();

        List<ChatStreamJsonAccumulator.Segment> segments = accumulator.append("正在分析 PPT 需求");

        Assert.assertEquals(1, segments.size());
        Assert.assertEquals(ChatStreamJsonAccumulator.SegmentType.TEXT, segments.get(0).getType());
        Assert.assertEquals("正在分析 PPT 需求", segments.get(0).getContent());
    }

}
