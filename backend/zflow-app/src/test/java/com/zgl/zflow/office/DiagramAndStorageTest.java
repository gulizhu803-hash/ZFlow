package com.zgl.zflow.office;

import com.zgl.zflow.domain.office.model.*;
import com.zgl.zflow.infrastructure.office.*;
import com.zgl.zflow.types.exception.OfficeException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DiagramAndStorageTest {
    @TempDir Path root;
    OfficeSettings settings=new OfficeSettings();
    DiagramValidator validator=new DiagramValidator(settings);
    String fixture()throws Exception{return new ClassPathResource("office/order-architecture.drawio").getContentAsString(StandardCharsets.UTF_8);}
    String empty="<mxfile><diagram id=\"p\"><mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/></root></mxGraphModel></diagram></mxfile>";
    @Test void fixtureHasEditableVerticesAndManualEmptyIsAllowed()throws Exception {
        var report=validator.validate(fixture(),true);assertTrue(report.valid(),report.toString());assertTrue(report.nodeCount()>0);
        assertTrue(validator.validate(empty,false).valid());assertFalse(validator.validate(empty,true).valid());
    }
    @Test void rejectsDtdCompressedContentAndCustomWrappers()throws Exception {
        for(String xml:List.of("<!DOCTYPE mxfile [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"+empty,
                "<mxfile><diagram id=\"p\">compressed-data</diagram></mxfile>",
                empty.replace("<mxCell id=\"1\" parent=\"0\"/>","<object id=\"wrapper\"><mxCell id=\"1\" parent=\"0\"/></object>")))
            assertFalse(validator.validate(xml,false).valid());
    }
    @Test void rejectsDuplicateIdsParentsAndDanglingEdges()throws Exception {
        for(String xml:List.of(fixture().replace("id=\"user\"","id=\"1\""),
                fixture().replace("id=\"1\" parent=\"0\"","id=\"1\" parent=\"user\""),
                fixture().replace("target=\"order-service\"","target=\"absent\"")))
            assertFalse(validator.validate(xml,true).valid(),xml);
    }
    @Test void rejectsNonFiniteGeometryAndRichResources()throws Exception {
        for(String xml:List.of(fixture().replace("html=0","html=1"),fixture().replace("value=\"用户\"","value=\"&lt;script&gt;alert(1)&lt;/script&gt;\""),
                fixture().replace("html=0","html=0;image=https://example.invalid/a.png"),
                fixture().replace("x=\"80\"","x=\"NaN\""),fixture().replace("parent=\"1\"","onclick=\"evil()\" parent=\"1\"")))
            assertFalse(validator.validate(xml,true).valid(),xml);
    }
    @Test void limitsDepthSizeAndReportLength()throws Exception {
        String deep="<mxfile>"+"<diagram>".repeat(70)+"</diagram>".repeat(70)+"</mxfile>";
        assertFalse(validator.validate(deep,false).valid());
        assertEquals(413,assertThrows(OfficeException.class,()->validator.normalize("a".repeat(1048577))).getStatus());
        var report=validator.validate(fixture().replace("html=0","html=1;image=bad;link=bad"),true);
        assertTrue(report.issues().size()<=10);assertTrue(OfficeJson.json(report).getBytes(StandardCharsets.UTF_8).length<=8192);
        assertEquals("a\nb\nc",validator.normalize("\uFEFFa\r\nb\rc"));
    }
    @Test void immutableFilesSurviveOtherRevisionsAndCorruptionIsDetected()throws Exception {
        LocalArtifactStorage storage=new LocalArtifactStorage(root.toString(),1048576);
        String art=OfficeJson.id("art"),rev=OfficeJson.id("rev");
        byte[] bytes=fixture().getBytes(StandardCharsets.UTF_8);
        var file=storage.writeImmutable(1,art,rev,bytes);
        assertArrayEquals(bytes,storage.readVerified(file.objectKey(),file.contentHash(),file.sizeBytes()));
        assertThrows(OfficeException.class,()->storage.writeImmutable(1,art,rev,"other".getBytes(StandardCharsets.UTF_8)));
        var other=storage.writeImmutable(1,art,OfficeJson.id("rev"),empty.getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(bytes,storage.readVerified(file.objectKey(),file.contentHash(),file.sizeBytes()));
        Files.writeString(root.resolve(other.objectKey()),"corrupt");
        assertEquals(503,assertThrows(OfficeException.class,()->storage.readVerified(other.objectKey(),other.contentHash(),other.sizeBytes())).getStatus());
        assertThrows(OfficeException.class,()->storage.readVerified("../outside","x",1));
    }
}
