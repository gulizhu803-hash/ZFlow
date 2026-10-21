package com.zgl.zflow.office;

import com.zgl.zflow.domain.office.model.DiagramValidationReport;
import com.zgl.zflow.domain.office.model.OfficeSettings;
import com.zgl.zflow.infrastructure.office.DiagramSpecRenderer;
import com.zgl.zflow.infrastructure.office.DiagramValidator;
import com.zgl.zflow.types.exception.OfficeException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * spec 模式的核心保证：模型只给结构，渲染器产出的 XML 必须天然合法。
 * 这里用 DiagramsValidator 反向校验渲染结果，避免“渲染器自己觉得对”。
 */
class DiagramSpecRendererTest {

    private final DiagramSpecRenderer renderer = new DiagramSpecRenderer();
    private final DiagramValidator validator = new DiagramValidator(new OfficeSettings());

    private DiagramValidationReport validate(String xml) {
        return validator.validate(xml, true);
    }

    @Test
    void rendersLinearChainThatPassesValidation() {
        String spec = """
                {"title":"订单系统","nodes":[
                  {"id":"gw","label":"网关"},
                  {"id":"order","label":"订单服务"},
                  {"id":"db","label":"订单库"}],
                 "edges":[{"from":"gw","to":"order","label":"路由"},{"from":"order","to":"db","label":"读写"}]}
                """;
        String xml = renderer.render(spec);
        DiagramValidationReport report = validate(xml);
        assertTrue(report.valid(), () -> "rendered XML invalid: " + report);
        assertEquals(3, report.nodeCount());
        assertEquals(2, report.edgeCount());
        assertTrue(xml.contains("value=\"网关\""));
    }

    @Test
    void rendersGroupAsBackdropBehindNodes() {
        String spec = """
                {"nodes":[{"id":"gw","label":"网关","group":"接入层"},{"id":"db","label":"库"}],
                 "edges":[{"from":"gw","to":"db"}]}
                """;
        String xml = renderer.render(spec);
        DiagramValidationReport report = validate(xml);
        assertTrue(report.valid(), () -> "rendered XML invalid: " + report);
        // 节点 2 个 + 分组底板 1 个
        assertEquals(3, report.nodeCount());
        assertTrue(xml.contains("value=\"接入层\""));
        assertTrue(xml.contains("id=\"group-0\""));
    }

    @Test
    void toleratesMarkdownFenceAndThinkBlock() {
        String spec = """
                <think>先想一下</think>
                ```json
                {"title":"t","nodes":[{"id":"a","label":"A"}],"edges":[]}
                ```
                """;
        String xml = renderer.render(spec);
        assertTrue(xml.startsWith("<?xml"));
        assertTrue(xml.contains("id=\"a\""));
    }

    @Test
    void escapesLabelsSoXmlStaysWellFormed() {
        String spec = """
                {"title":"A & B","nodes":[{"id":"n1","label":"a<b>&\\"c\\""}],"edges":[]}
                """;
        String xml = renderer.render(spec);
        assertTrue(validate(xml).valid());
        assertTrue(xml.contains("&amp;"));
        assertTrue(xml.contains("a＜b＞"));
    }

    @Test
    void rejectsDuplicateIds() {
        String spec = """
                {"nodes":[{"id":"a","label":"A"},{"id":"a","label":"B"}],"edges":[]}
                """;
        OfficeException error = assertThrows(OfficeException.class, () -> renderer.render(spec));
        assertEquals("INVALID_DIAGRAM_SPEC", error.getErrorKey());
    }

    @Test
    void rejectsEdgesPointingToUnknownNodes() {
        String spec = """
                {"nodes":[{"id":"a","label":"A"}],"edges":[{"from":"a","to":"ghost"}]}
                """;
        OfficeException error = assertThrows(OfficeException.class, () -> renderer.render(spec));
        assertEquals("INVALID_DIAGRAM_SPEC", error.getErrorKey());
    }

    @Test
    void rejectsUnsafeIds() {
        String spec = """
                {"nodes":[{"id":"a\\"b","label":"A"}],"edges":[]}
                """;
        assertThrows(OfficeException.class, () -> renderer.render(spec));
    }

    @Test
    void survivesCycles() {
        String spec = """
                {"nodes":[{"id":"a","label":"A"},{"id":"b","label":"B"}],
                 "edges":[{"from":"a","to":"b"},{"from":"b","to":"a"}]}
                """;
        String xml = renderer.render(spec);
        assertTrue(validate(xml).valid());
    }

    @Test
    void capsNodeCount() {
        StringBuilder nodes = new StringBuilder();
        for (int i = 0; i < 61; i++) {
            if (i > 0) nodes.append(',');
            nodes.append("{\"id\":\"n").append(i).append("\",\"label\":\"N").append(i).append("\"}");
        }
        String spec = "{\"nodes\":[" + nodes + "],\"edges\":[]}";
        assertThrows(OfficeException.class, () -> renderer.render(spec));
    }
}
