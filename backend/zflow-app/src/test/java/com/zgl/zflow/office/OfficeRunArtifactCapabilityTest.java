package com.zgl.zflow.office;

import com.zgl.zflow.domain.office.gateway.IArtifactRenderer;
import com.zgl.zflow.domain.office.model.OfficeRequest;
import com.zgl.zflow.domain.office.model.OfficeRun;
import com.zgl.zflow.domain.office.repository.IOfficeRepository;
import com.zgl.zflow.domain.office.service.OfficeRunService;
import com.zgl.zflow.domain.office.service.OfficeTransactions;
import com.zgl.zflow.types.exception.OfficeException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OfficeRunArtifactCapabilityTest {

    /** 与 T1 固化的基线一致：DRAWIO 走能力闸门后 request_hash 必须逐字不变。 */
    private static final String DRAWIO_BASELINE =
            "{\"sessionId\":\"sess-baseline\",\"prompt\":\"订单系统架构\",\"artifactTypes\":[\"DRAWIO\"],\"diagramType\":\"ARCHITECTURE\",\"title\":\"订单系统\",\"retryOfRunId\":null}";

    private final IOfficeRepository repository = mock(IOfficeRepository.class);
    private final OfficeTransactions transactions = mock(OfficeTransactions.class);

    private OfficeRunService service(List<IArtifactRenderer<?>> renderers) {
        return new OfficeRunService(repository, transactions, renderers);
    }

    private void acceptAnything() {
        OfficeRun run = new OfficeRun();
        run.setRunId("run-test");
        when(transactions.accept(anyLong(), anyString(), any())).thenReturn(new OfficeTransactions.Accepted(run, false));
    }

    // ---------- 未实现的类型：入口即 503，且不碰数据库 ----------

    @Test void pptxIsRejectedAtTheEntryBeforeAnyDatabaseWork() {
        OfficeException failure = assertThrows(OfficeException.class, () -> service(List.of())
                .create(1, "key-pptx-0001", new OfficeRequest("s", "做十页介绍", List.of("PPTX"), null, "介绍", null)));
        assertEquals(503, failure.getStatus());
        assertEquals("OFFICE_ARTIFACT_UNSUPPORTED", failure.getErrorKey());
        assertEquals("该产物类型尚未实现：PPTX", failure.getMessage());
        verifyNoInteractions(repository, transactions);
    }

    @Test void mixedDrawioAndPptxIsRejectedAsAWhole() {
        OfficeException failure = assertThrows(OfficeException.class, () -> service(List.of())
                .create(1, "key-mixed-0001", new OfficeRequest("s", "图加幻灯片", List.of("DRAWIO", "PPTX"), "ARCHITECTURE", null, null)));
        assertEquals(503, failure.getStatus());
        assertEquals("OFFICE_ARTIFACT_UNSUPPORTED", failure.getErrorKey());
        assertTrue(failure.getMessage().contains("PPTX"), failure.getMessage());
        verifyNoInteractions(repository, transactions);
    }

    @Test void rendererForAnotherTypeDoesNotClearTheGate() {
        assertThrows(OfficeException.class, () -> service(List.<IArtifactRenderer<?>>of(new FakeRenderer("XLSX")))
                .create(1, "key-pptx-0002", new OfficeRequest("s", "做十页介绍", List.of("PPTX"), null, null, null)));
        verifyNoInteractions(repository, transactions);
    }

    // ---------- DRAWIO 回归：不被闸门拦，request_hash 不变 ----------

    @Test void drawioOnlyStillPassesWithAnEmptyRendererList() {
        acceptAnything();
        var accepted = service(List.of()).create(1, "key-drawio-001",
                new OfficeRequest("sess-baseline", "订单系统架构", List.of("DRAWIO"), "ARCHITECTURE", "订单系统", null));
        assertEquals("run-test", accepted.run().getRunId());
        assertFalse(accepted.replayed());
        verify(transactions).accept(eq(1L), eq("key-drawio-001"), any(OfficeRequest.class));
    }

    @Test void drawioRequestHashIsUnchangedByTheGate() {
        OfficeRequest drawio = new OfficeRequest("sess-baseline", "订单系统架构", List.of("DRAWIO"),
                "ARCHITECTURE", "订单系统", null).normalize();
        assertEquals(DRAWIO_BASELINE, drawio.canonical());
    }

    // ---------- 已注册的实现：按 type() 放行 ----------

    @Test void registeredRendererClearsTheGate() {
        acceptAnything();
        var accepted = service(List.<IArtifactRenderer<?>>of(new FakeRenderer("XLSX")))
                .create(1, "key-xlsx-0001", new OfficeRequest("s", "做季度报表", List.of("XLSX"), null, "报表", null));
        assertEquals("run-test", accepted.run().getRunId());
        verify(transactions).accept(eq(1L), eq("key-xlsx-0001"), any(OfficeRequest.class));
    }

    /** 单测桩：只用来证明闸门是按 type() 查表，而不是写死白名单。 */
    private record FakeRenderer(String type) implements IArtifactRenderer<String> {
        @Override public String contentType() { return "application/octet-stream"; }
        @Override public String fileExtension() { return "bin"; }
        @Override public String parseSpec(String json) { return json; }
        @Override public byte[] render(String spec, OfficeRequest request) { return spec.getBytes(StandardCharsets.UTF_8); }
    }
}
