package com.zgl.zflow.office;

import com.zgl.zflow.domain.office.gateway.IArtifactRenderer;
import com.zgl.zflow.domain.office.model.OfficeJson;
import com.zgl.zflow.domain.office.model.OfficeRequest;
import com.zgl.zflow.domain.office.model.OfficeRun;
import com.zgl.zflow.infrastructure.office.render.ArtifactRenderers;
import com.zgl.zflow.types.exception.OfficeException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OfficeRequestArtifactTypesTest {

    /**
     * 以下三条常量是改动前（backend/stability @ 8657f05，仅支持 DRAWIO 的版本）
     * 在容器里真实跑出的 canonical() 输出，逐字粘贴，用来锁死 request_hash 语义。
     */
    private static final String BASELINE_ARCHITECTURE =
            "{\"sessionId\":\"sess-baseline\",\"prompt\":\"订单系统架构\",\"artifactTypes\":[\"DRAWIO\"],\"diagramType\":\"ARCHITECTURE\",\"title\":\"订单系统\",\"retryOfRunId\":null}";
    private static final String BASELINE_DEFAULTS =
            "{\"sessionId\":\"sess-baseline\",\"prompt\":\"订单系统架构\",\"artifactTypes\":[\"DRAWIO\"],\"diagramType\":\"FLOWCHART\",\"title\":\"未命名图表\",\"retryOfRunId\":null}";
    private static final String BASELINE_RETRY =
            "{\"sessionId\":\"sess-baseline\",\"prompt\":\"订单系统架构\",\"artifactTypes\":[\"DRAWIO\"],\"diagramType\":\"SEQUENCE\",\"title\":\"订单系统\",\"retryOfRunId\":\"run_1\"}";

    private static OfficeRequest legacy() {
        return new OfficeRequest("sess-baseline", "订单系统架构", List.of("DRAWIO"), "ARCHITECTURE", "订单系统", null).normalize();
    }

    // ---------- 旧 DRAWIO 请求：canonical / request_hash 逐字不变 ----------

    @Test void legacyDrawioCanonicalIsByteIdenticalToBaseline() {
        assertEquals(BASELINE_ARCHITECTURE, legacy().canonical());
        var defaults = new OfficeRequest("sess-baseline", "订单系统架构", List.of("DRAWIO"), null, null, null).normalize();
        assertEquals(BASELINE_DEFAULTS, defaults.canonical());
        var retry = new OfficeRequest("sess-baseline", "订单系统架构", List.of("DRAWIO"), "SEQUENCE", "订单系统", "run_1").normalize();
        assertEquals(BASELINE_RETRY, retry.canonical());
    }

    @Test void legacySnapshotReplaysKeepOriginalRequestHash() {
        OfficeRun historical = new OfficeRun();
        historical.setRequestSnapshot(BASELINE_ARCHITECTURE);
        // 历史任务的重试比对路径：老快照 -> OfficeRequest -> withoutRetry().canonical()
        assertEquals(BASELINE_ARCHITECTURE, OfficeJson.request(historical).withoutRetry().canonical());
        // 新代码算出的 request_hash 与老版本算出的完全一致
        assertEquals(OfficeJson.hash(BASELINE_ARCHITECTURE), OfficeJson.hash(legacy().canonical()));
    }

    @Test void drawioDefaultsAreUnchanged() {
        OfficeRequest request = legacy();
        assertEquals(List.of("DRAWIO"), request.artifactTypes());
        assertEquals("ARCHITECTURE", request.diagramType());
        assertEquals("default", request.theme());
        assertEquals(0, request.targetSlides().intValue());
        assertEquals(400, assertThrows(OfficeException.class,
                () -> new OfficeRequest("s", "x", List.of("DRAWIO"), "MINDMAP", null, null).normalize()).getStatus());
    }

    // ---------- 新类型：XLSX / DOCX 通过闸门 ----------

    @Test void xlsxAndDocxPassTheGate() {
        for (String type : List.of("XLSX", "DOCX")) {
            OfficeRequest request = new OfficeRequest("s", "生成季度报表", List.of(type), null, "季度报表", null).normalize();
            assertEquals(List.of(type), request.artifactTypes());
            assertNull(request.diagramType(), "非 DRAWIO 请求不应保留 diagramType");
        }
        OfficeRequest mixed = new OfficeRequest("s", "图加表", List.of("DRAWIO", "XLSX"), "ARCHITECTURE", null, null).normalize();
        assertEquals(List.of("DRAWIO", "XLSX"), mixed.artifactTypes());
        assertEquals("ARCHITECTURE", mixed.diagramType());
    }

    @Test void diagramTypeIsOnlyValidatedWhenDrawioIsRequested() {
        // 含 DRAWIO 且 diagramType 非法 -> 仍然 400
        assertEquals("INVALID_REQUEST", assertThrows(OfficeException.class,
                () -> new OfficeRequest("s", "x", List.of("DRAWIO", "XLSX"), "PDF", null, null).normalize()).getErrorKey());
        // 不含 DRAWIO -> 完全忽略 diagramType，不报错也不保留
        OfficeRequest request = new OfficeRequest("s", "x", List.of("XLSX"), "PDF", null, null).normalize();
        assertNull(request.diagramType());
    }

    // ---------- 脏类型：白名单拒绝，错误信息带非法值 ----------

    @Test void unsupportedTypeIsRejectedWithTheOffendingValue() {
        for (List<String> types : List.of(List.of("PDF"), List.of("DRAWIO", "pdf"))) {
            OfficeException failure = assertThrows(OfficeException.class,
                    () -> new OfficeRequest("s", "x", types, "FLOWCHART", null, null).normalize());
            assertEquals(422, failure.getStatus());
            assertEquals("UNSUPPORTED_ARTIFACT_TYPE", failure.getErrorKey());
            assertTrue(failure.getMessage().contains(types.get(types.size() - 1)), failure.getMessage());
        }
    }

    @Test void emptyArtifactTypesAreRejected() {
        for (List<String> types : java.util.Arrays.<List<String>>asList(null, List.<String>of())) {
            OfficeException failure = assertThrows(OfficeException.class,
                    () -> new OfficeRequest("s", "x", types, null, null, null).normalize());
            assertEquals(422, failure.getStatus());
            assertEquals("UNSUPPORTED_ARTIFACT_TYPE", failure.getErrorKey());
        }
    }

    // ---------- PPTX：类型已登记，渲染时明确“未实现” ----------

    @Test void pptxIsRegisteredButNotImplemented() {
        OfficeRequest request = new OfficeRequest("s", "做十页介绍", List.of("PPTX"), null, null, null, null, 10).normalize();
        assertEquals(List.of("PPTX"), request.artifactTypes());

        ArtifactRenderers renderers = new ArtifactRenderers(List.<IArtifactRenderer<?>>of());
        OfficeException failure = assertThrows(OfficeException.class, () -> renderers.rendererFor("PPTX"));
        assertEquals(503, failure.getStatus());
        assertEquals("OFFICE_ARTIFACT_UNSUPPORTED", failure.getErrorKey());
        assertEquals("该产物类型尚未实现", failure.getMessage());
        // 未注册的类型（含空值）走同一条明确错误，而不是 NPE
        assertEquals("OFFICE_ARTIFACT_UNSUPPORTED", assertThrows(OfficeException.class, () -> renderers.rendererFor(null)).getErrorKey());
        assertEquals("OFFICE_ARTIFACT_UNSUPPORTED", assertThrows(OfficeException.class, () -> renderers.rendererFor("DRAWIO")).getErrorKey());
    }

    @Test void rendererRegistryResolvesRegisteredRenderers() {
        ArtifactRenderers renderers = new ArtifactRenderers(List.<IArtifactRenderer<?>>of(new FakeRenderer("XLSX")));
        assertEquals("XLSX", renderers.rendererFor("XLSX").type());
        assertEquals("XLSX", renderers.rendererFor(" xlsx ").type());
        IArtifactRenderer<?> xlsx = renderers.rendererFor("XLSX");
        byte[] bytes = roundTrip(xlsx, "{\"rows\":1}", new OfficeRequest("s", "x", List.of("XLSX"), null, null, null));
        assertTrue(bytes.length > 0);
        assertThrows(IllegalStateException.class,
                () -> new ArtifactRenderers(List.<IArtifactRenderer<?>>of(new FakeRenderer("XLSX"), new FakeRenderer("xlsx"))));
    }

    // ---------- 可选字段：只影响校验与 canonical ----------

    @Test void optionalFieldsOnlyEnterCanonicalWhenNonDefault() {
        OfficeRequest sameAsLegacy = new OfficeRequest("sess-baseline", "订单系统架构", List.of("DRAWIO"),
                "ARCHITECTURE", "订单系统", null, "default", 0).normalize();
        assertEquals(BASELINE_ARCHITECTURE, sameAsLegacy.canonical());

        OfficeRequest themed = new OfficeRequest("sess-baseline", "订单系统架构", List.of("DRAWIO"),
                "ARCHITECTURE", "订单系统", null, "dark", 12).normalize();
        assertNotEquals(BASELINE_ARCHITECTURE, themed.canonical());
        assertTrue(themed.canonical().contains("\"theme\":\"dark\""), themed.canonical());
        assertTrue(themed.canonical().contains("\"targetSlides\":12"), themed.canonical());
        // 新字段追加在末尾，前缀仍是旧的七字段顺序
        assertTrue(themed.canonical().startsWith(BASELINE_ARCHITECTURE.substring(0, BASELINE_ARCHITECTURE.length() - 1)));

        OfficeRequest withoutRetry = themed.withoutRetry();
        assertNull(withoutRetry.retryOfRunId());
        assertEquals("dark", withoutRetry.theme());
        assertEquals(12, withoutRetry.targetSlides().intValue());
        assertEquals(themed.canonical(), withoutRetry.canonical());
    }

    @Test void optionalFieldsAreValidated() {
        assertEquals(400, assertThrows(OfficeException.class,
                () -> new OfficeRequest("s", "x", List.of("DOCX"), null, null, null, "a b", 0).normalize()).getStatus());
        assertEquals(400, assertThrows(OfficeException.class,
                () -> new OfficeRequest("s", "x", List.of("DOCX"), null, null, null, null, -1).normalize()).getStatus());
        assertEquals("default", new OfficeRequest("s", "x", List.of("DOCX"), null, null, null, "  ", 0).normalize().theme());
    }

    // ---------- HTTP 入参：新字段可传，未知字段仍然拒绝 ----------

    @Test void httpParserAcceptsTheTwoOptionalFields() throws Exception {
        var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"sessionId\":\"s\",\"prompt\":\"做十页介绍\",\"artifactTypes\":[\"PPTX\"],\"theme\":\"dark\",\"targetSlides\":8}");
        OfficeRequest request = com.zgl.zflow.api.dto.office.OfficeRequests.run(body).normalize();
        assertEquals(List.of("PPTX"), request.artifactTypes());
        assertEquals("dark", request.theme());
        assertEquals(8, request.targetSlides().intValue());
        assertTrue(request.canonical().contains("\"theme\":\"dark\""), request.canonical());
    }

    @Test void httpParserStillRejectsUnknownAndNonIntegerFields() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var unknown = mapper.readTree("{\"sessionId\":\"s\",\"prompt\":\"x\",\"artifactTypes\":[\"DRAWIO\"],\"knowledgeIds\":[]}");
        assertEquals(400, assertThrows(OfficeException.class,
                () -> com.zgl.zflow.api.dto.office.OfficeRequests.run(unknown)).getStatus());
        var fractional = mapper.readTree("{\"sessionId\":\"s\",\"prompt\":\"x\",\"artifactTypes\":[\"DRAWIO\"],\"targetSlides\":8.5}");
        assertEquals(400, assertThrows(OfficeException.class,
                () -> com.zgl.zflow.api.dto.office.OfficeRequests.run(fractional)).getStatus());
    }

    /** 泛型桥：把“按类型查到的渲染器”跑完 parseSpec -> render，证明注册表可端到端使用。 */
    private static <T> byte[] roundTrip(IArtifactRenderer<T> renderer, String json, OfficeRequest request) {
        return renderer.render(renderer.parseSpec(json), request);
    }

    /** 单测桩：证明 IArtifactRenderer 可以按类型注册并端到端跑通 parseSpec -> render。 */
    private record FakeRenderer(String type) implements IArtifactRenderer<String> {
        @Override public String contentType() { return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; }
        @Override public String fileExtension() { return "xlsx"; }
        @Override public String parseSpec(String json) { return json; }
        @Override public byte[] render(String spec, OfficeRequest request) { return spec.getBytes(StandardCharsets.UTF_8); }
    }
}
