package com.zgl.zflow.infrastructure.office;

import com.zgl.zflow.domain.office.gateway.IDiagramValidator;
import com.zgl.zflow.domain.office.model.*;
import com.zgl.zflow.types.exception.OfficeException;
import org.w3c.dom.*;
import org.xml.sax.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DiagramValidator implements IDiagramValidator {
    private final OfficeSettings settings;
    public DiagramValidator(OfficeSettings settings) { this.settings=settings; }
    @Override public String normalize(String xml) {
        if(xml==null) throw OfficeException.invalid("xml 必填");
        String result=(xml.startsWith("\uFEFF")?xml.substring(1):xml).replace("\r\n","\n").replace("\r","\n");
        if(result.getBytes(StandardCharsets.UTF_8).length>settings.getMaxXmlBytes())
            throw new OfficeException(413,"ARTIFACT_CONTENT_TOO_LARGE","图表正文超过大小限制");
        return result;
    }
    @Override public DiagramValidationReport validate(String xml,boolean generated) {
        xml=normalize(xml); Check check=new Check();
        try {
            DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            f.setFeature("http://xml.org/sax/features/external-general-entities",false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
            f.setXIncludeAware(false); f.setExpandEntityReferences(false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            f.setAttribute("jdk.xml.maxElementDepth","64");
            var builder=f.newDocumentBuilder();
            builder.setEntityResolver((publicId,systemId)->{throw new SAXException("External entity forbidden");});
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void error(SAXParseException e)throws SAXException {throw e;}
                @Override public void fatalError(SAXParseException e)throws SAXException {throw e;}
            });
            Document doc=builder.parse(new InputSource(new StringReader(xml)));
            if(doc.getXmlEncoding()!=null&&!Set.of("UTF-8","UTF8").contains(doc.getXmlEncoding().toUpperCase(Locale.ROOT)))
                check.issue("INVALID_XML_ENCODING",null,null,"图表声明必须使用 UTF-8");
            for(Node n=doc.getFirstChild();n!=null;n=n.getNextSibling())
                if(n.getNodeType()==Node.PROCESSING_INSTRUCTION_NODE)check.issue("UNSUPPORTED_RESOURCE",null,null,"不支持处理指令");
            Element root=doc.getDocumentElement();
            if(!"mxfile".equals(root.getTagName())) check.issue("INVALID_CONTAINER",null,null,"必须使用未压缩 mxfile");
            inspect(root,0,null,null,check);
            List<Element> pages=children(root,"diagram"); check.pages=pages.size();
            if(pages.isEmpty()||pages.size()>settings.getMaxDiagramPages()) check.issue("INVALID_PAGE_COUNT",null,null,"页数须在支持范围内");
            Set<String> pageIds=new HashSet<>();
            for(Element page:pages) {
                String pageId=page.getAttribute("id");
                if(pageId.isBlank()||!pageIds.add(pageId)) check.issue("INVALID_PAGE_ID",pageId,null,"页面 ID 缺失或重复");
                List<Element> graphs=children(page,"mxGraphModel");
                if(graphs.size()!=1 || children(page,null).size()!=1) {check.issue("UNSUPPORTED_COMPRESSED_DIAGRAM",pageId,null,"每页须包含一个未压缩 mxGraphModel");continue;}
                List<Element> roots=children(graphs.get(0),"root");
                if(roots.size()!=1) {check.issue("INVALID_ROOT",pageId,null,"图表缺少唯一 root");continue;}
                List<Element> cells=children(roots.get(0),"mxCell");
                if(cells.size()!=children(roots.get(0),null).size()) check.issue("UNSUPPORTED_CELL_WRAPPER",pageId,null,"首版只支持直接 mxCell 节点");
                Map<String,Element> ids=new LinkedHashMap<>();
                for(Element cell:cells) {
                    String id=cell.getAttribute("id");
                    if(id.isBlank()||id.length()>128) check.issue("INVALID_CELL_ID",pageId,id,"节点 ID 缺失或过长");
                    if(ids.putIfAbsent(id,cell)!=null) check.issue("DUPLICATE_CELL_ID",pageId,id,"同页节点 ID 重复");
                    boolean vertex="1".equals(cell.getAttribute("vertex")),edge="1".equals(cell.getAttribute("edge"));
                    if(vertex) check.nodes++; if(edge) check.edges++;
                    if(vertex&&edge) check.issue("INVALID_CELL",pageId,id,"节点不能同时为顶点和连线");
                    if((vertex||edge)&&children(cell,"mxGeometry").size()!=1)
                        check.issue("INVALID_GEOMETRY",pageId,id,"节点或连线需要一个 mxGeometry");
                }
                if(!ids.containsKey("0")||!ids.containsKey("1")) check.issue("MISSING_BASE_CELLS",pageId,null,"缺少基础 0/1 节点");
                for(Element cell:cells) {
                    String id=cell.getAttribute("id"),parent=cell.getAttribute("parent");
                    if(!"0".equals(id)&&parent.isBlank()) check.issue("MISSING_PARENT",pageId,id,"节点缺少 parent");
                    Set<String> chain=new HashSet<>(); chain.add(id);
                    while(!parent.isBlank()) {
                        if(!chain.add(parent)) {check.issue("PARENT_CYCLE",pageId,id,"parent 链存在环");break;}
                        if(chain.size()>64) {check.issue("PARENT_DEPTH_EXCEEDED",pageId,id,"parent 链深度超过 64");break;}
                        Element p=ids.get(parent);
                        if(p==null) {check.issue("MISSING_PARENT",pageId,id,"parent 指向不存在的节点");break;}
                        parent=p.getAttribute("parent");
                    }
                    if("1".equals(cell.getAttribute("edge"))) for(String end:List.of("source","target")) {
                        Element v=ids.get(cell.getAttribute(end));
                        if(v==null||!"1".equals(v.getAttribute("vertex"))) check.issue("DANGLING_EDGE",pageId,id,"连线两端必须连接同页顶点");
                    }
                }
            }
            if(check.nodes>settings.getMaxDiagramNodes()||check.edges>settings.getMaxDiagramEdges())
                check.issue("DIAGRAM_TOO_COMPLEX",null,null,"节点或连线总数超过限制");
            if(generated&&check.nodes==0) check.issue("EMPTY_DIAGRAM",null,null,"生成图表至少需要一个顶点");
        } catch(Exception e) { check.issue("INVALID_XML",null,null,"XML 无法安全解析或超出最大深度"); }
        return check.report();
    }
    private void inspect(Element e,int depth,String pageId,String cellId,Check check) {
        if(depth>64) {check.issue("XML_DEPTH_EXCEEDED",pageId,cellId,"XML 深度超过 64");return;}
        String tag=e.getTagName();
        if("diagram".equals(tag)) pageId=e.getAttribute("id");
        if("mxCell".equals(tag)) cellId=e.getAttribute("id");
        Set<String> allowed=Set.of("mxfile","diagram","mxGraphModel","root","mxCell","mxGeometry","mxPoint","Array","mxRectangle");
        if(!allowed.contains(tag)) check.issue("UNSUPPORTED_RESOURCE",pageId,cellId,"包含不支持的节点包装或资源");
        Map<String,Set<String>> children=Map.of("mxfile",Set.of("diagram"),"diagram",Set.of("mxGraphModel"),
                "mxGraphModel",Set.of("root"),"root",Set.of("mxCell"),"mxCell",Set.of("mxGeometry"),
                "mxGeometry",Set.of("mxPoint","Array","mxRectangle"),"Array",Set.of("mxPoint"));
        for(Element c:children(e,null)) if(!children.getOrDefault(tag,Set.of()).contains(c.getTagName()))
            check.issue("INVALID_STRUCTURE",pageId,cellId,"图表节点层次不受支持");
        NamedNodeMap attrs=e.getAttributes();
        for(int i=0;i<attrs.getLength();i++) {
            Node a=attrs.item(i); String name=a.getNodeName().toLowerCase(Locale.ROOT),value=a.getNodeValue(),lower=value.toLowerCase(Locale.ROOT);
            if(name.startsWith("on")||Set.of("href","src","link","image").contains(name)||
                    lower.contains("javascript:")||lower.contains("data:")||value.contains("<")||value.contains(">"))
                check.issue("UNSUPPORTED_RESOURCE",pageId,cellId,"仅支持纯文本标签，不支持脚本、链接、图片或富文本");
            if("style".equals(name)) for(String declaration:value.split(";")) {
                String[] pair=declaration.split("=",2); String key=pair[0].strip().toLowerCase(Locale.ROOT);
                String val=pair.length==2?pair[1].strip().toLowerCase(Locale.ROOT):"";
                if(("html".equals(key)&&!"0".equals(val)) || key.contains("image") || key.contains("link") ||
                        val.contains("http:")||val.contains("https:")||val.contains("url(")||val.contains("stencil(")||
                        ("shape".equals(key)&&Set.of("image","svg").contains(val)))
                    check.issue("UNSUPPORTED_RESOURCE",pageId,cellId,"样式包含富文本或外部资源，请使用 html=0");
            }
            if(Set.of("mxGeometry","mxPoint","mxRectangle").contains(tag)&&Set.of("x","y","width","height").contains(name)) {
                try {
                    double n=Double.parseDouble(value);
                    if(!Double.isFinite(n)||((name.equals("width")||name.equals("height"))&&n<0)) throw new NumberFormatException();
                } catch(NumberFormatException invalid) {check.issue("INVALID_GEOMETRY",pageId,cellId,"几何数值必须有限且宽高非负");}
            }
        }
        for(Node c=e.getFirstChild();c!=null;c=c.getNextSibling()) {
            if(c instanceof Element child) inspect(child,depth+1,pageId,cellId,check);
            else if((c.getNodeType()==Node.TEXT_NODE||c.getNodeType()==Node.CDATA_SECTION_NODE)&&!c.getTextContent().isBlank())
                check.issue("UNSUPPORTED_COMPRESSED_DIAGRAM",pageId,cellId,"不支持压缩图表或元素文本内容");
            else if(c.getNodeType()==Node.PROCESSING_INSTRUCTION_NODE)
                check.issue("UNSUPPORTED_RESOURCE",pageId,cellId,"不支持处理指令");
        }
    }
    private static List<Element> children(Element e,String name) {
        List<Element> result=new ArrayList<>();
        for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element child&&(name==null||name.equals(child.getTagName())))result.add(child);
        return result;
    }
    private static class Check {
        int pages,nodes,edges,count;
        List<DiagramValidationReport.Issue> issues=new ArrayList<>();
        void issue(String code,String page,String cell,String message) {
            count++; if(issues.size()<10)issues.add(new DiagramValidationReport.Issue(code,"ERROR",shortId(page),shortId(cell),message));
        }
        String shortId(String id){return id==null?null:id.substring(0,Math.min(64,id.length()));}
        DiagramValidationReport report(){return new DiagramValidationReport(1,VERSION,count==0,pages,nodes,edges,List.copyOf(issues),count>10);}
    }
}
