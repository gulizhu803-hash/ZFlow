package com.zgl.zflow.infrastructure.office;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zgl.zflow.types.exception.OfficeException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 把模型输出的“结构化描述”渲染成 draw.io XML。
 *
 * 这样做的好处：模型只负责语义（谁连谁），坐标、样式、合法性由代码保证，
 * 输出 token 从数千降到数百，也几乎不会产出非法 XML。
 *
 * 输入：
 * {"title":"订单系统","nodes":[{"id":"gw","label":"网关","group":"接入层"}],
 *  "edges":[{"from":"gw","to":"order","label":"路由"}]}
 */
public class DiagramSpecRenderer {

    private static final int NODE_W = 168;
    private static final int NODE_H = 56;
    private static final int GAP_X = 96;
    private static final int GAP_Y = 32;
    private static final int MARGIN = 48;
    private static final int MAX_NODES = 60;
    private static final int MAX_EDGES = 120;
    private static final int MAX_LAYERS = 12;

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public String render(String raw) {
        JSONObject spec = parse(raw);
        JSONArray nodeArray = spec.getJSONArray("nodes");
        if (nodeArray == null || nodeArray.isEmpty()) throw invalid("节点为空");
        if (nodeArray.size() > MAX_NODES) throw invalid("节点超过 " + MAX_NODES + " 个");

        Map<String, Node> nodes = new LinkedHashMap<>();
        for (int i = 0; i < nodeArray.size(); i++) {
            JSONObject item = nodeArray.getJSONObject(i);
            String id = text(item, "id");
            String label = text(item, "label");
            if (!SAFE_ID.matcher(id).matches()) throw invalid("节点 id 不合法：" + id);
            if (nodes.containsKey(id)) throw invalid("节点 id 重复：" + id);
            nodes.put(id, new Node(id, label, item.getString("group")));
        }

        List<Edge> edges = new ArrayList<>();
        JSONArray edgeArray = spec.getJSONArray("edges");
        if (edgeArray != null) {
            Set<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < edgeArray.size(); i++) {
                JSONObject item = edgeArray.getJSONObject(i);
                String from = text(item, "from");
                String to = text(item, "to");
                if (!nodes.containsKey(from) || !nodes.containsKey(to)) throw invalid("连线指向不存在的节点");
                String key = from + "->" + to;
                if (!seen.add(key)) continue;
                edges.add(new Edge(from, to, item.getString("label")));
            }
            if (edges.size() > MAX_EDGES) throw invalid("连线超过 " + MAX_EDGES + " 条");
        }

        assignLayers(nodes, edges);
        layout(nodes);

        String title = spec.getString("title");
        return xml(title == null || title.isBlank() ? "图表" : title, nodes.values(), edges);
    }

    private JSONObject parse(String raw) {
        if (raw == null) throw invalid("模型未返回内容");
        String text = raw.startsWith("\uFEFF") ? raw.substring(1) : raw;
        text = text.replaceAll("(?s)<think>.*?</think>", "").strip();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) text = text.substring(firstNewline + 1, lastFence).strip();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) throw invalid("没有找到 JSON 对象");
        try {
            return JSON.parseObject(text.substring(start, end + 1));
        } catch (RuntimeException e) {
            throw invalid("JSON 解析失败");
        }
    }

    /** 按“从入口开始的层号”分层；有环时按遍历顺序兜底。 */
    private void assignLayers(Map<String, Node> nodes, List<Edge> edges) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String id : nodes.keySet()) {
            indegree.put(id, 0);
            out.put(id, new ArrayList<>());
        }
        for (Edge edge : edges) {
            indegree.merge(edge.to, 1, Integer::sum);
            out.get(edge.from).add(edge.to);
        }

        List<String> queue = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                nodes.get(entry.getKey()).layer = 0;
                queue.add(entry.getKey());
            }
        }
        if (queue.isEmpty()) {
            String first = nodes.keySet().iterator().next();
            nodes.get(first).layer = 0;
            queue.add(first);
        }

        for (int head = 0; head < queue.size(); head++) {
            String current = queue.get(head);
            Node node = nodes.get(current);
            for (String next : out.get(current)) {
                Node target = nodes.get(next);
                int candidate = Math.min(node.layer + 1, MAX_LAYERS - 1);
                if (target.layer < candidate) {
                    target.layer = candidate;
                    queue.add(next);
                }
            }
        }
        for (Node node : nodes.values()) {
            if (node.layer == 0 && !queue.contains(node.id)) node.layer = 1;
        }
    }

    private void layout(Map<String, Node> nodes) {
        Map<Integer, Integer> perLayer = new LinkedHashMap<>();
        for (Node node : nodes.values()) {
            int index = perLayer.merge(node.layer, 1, Integer::sum) - 1;
            node.x = MARGIN + node.layer * (NODE_W + GAP_X);
            node.y = MARGIN + index * (NODE_H + GAP_Y);
        }
    }

    private String xml(String title, Iterable<Node> nodes, List<Edge> edges) {
        List<Node> ordered = new ArrayList<>();
        nodes.forEach(ordered::add);

        Map<String, int[]> groups = new LinkedHashMap<>();
        for (Node node : ordered) {
            if (node.group == null || node.group.isBlank()) continue;
            int[] box = groups.computeIfAbsent(node.group, k -> new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE});
            box[0] = Math.min(box[0], node.x);
            box[1] = Math.min(box[1], node.y);
            box[2] = Math.max(box[2], node.x + NODE_W);
            box[3] = Math.max(box[3], node.y + NODE_H);
        }

        int maxX = MARGIN, maxY = MARGIN;
        for (Node node : ordered) {
            maxX = Math.max(maxX, node.x + NODE_W);
            maxY = Math.max(maxY, node.y + NODE_H);
        }
        for (int[] box : groups.values()) {
            maxX = Math.max(maxX, box[2]);
            maxY = Math.max(maxY, box[3]);
        }

        StringBuilder sb = new StringBuilder(2048);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<mxfile host=\"app.diagrams.net\" type=\"device\">\n");
        sb.append("  <diagram id=\"page-1\" name=\"").append(escape(title)).append("\">\n");
        sb.append("    <mxGraphModel dx=\"1200\" dy=\"800\" grid=\"1\" gridSize=\"10\" guides=\"1\" tooltips=\"1\" connect=\"1\" arrows=\"1\" fold=\"1\" page=\"1\" pageScale=\"1\" pageWidth=\"")
                .append(maxX + MARGIN).append("\" pageHeight=\"").append(maxY + MARGIN).append("\" math=\"0\" shadow=\"0\">\n");
        sb.append("      <root>\n");
        sb.append("        <mxCell id=\"0\"/>\n");
        sb.append("        <mxCell id=\"1\" parent=\"0\"/>\n");

        int bandIndex = 0;
        for (Map.Entry<String, int[]> entry : groups.entrySet()) {
            int[] box = entry.getValue();
            sb.append("        <mxCell id=\"group-").append(bandIndex++).append("\" value=\"").append(escape(entry.getKey()))
                    .append("\" style=\"rounded=0;whiteSpace=wrap;html=0;fillColor=#f7f7f5;strokeColor=#d5d5d0;dashed=1;verticalAlign=top;align=left;spacingLeft=8;spacingTop=6;fontSize=11;fontColor=#666666;\" vertex=\"1\" parent=\"1\">\n");
            sb.append("          <mxGeometry x=\"").append(box[0] - 20).append("\" y=\"").append(box[1] - 28)
                    .append("\" width=\"").append(box[2] - box[0] + 40).append("\" height=\"").append(box[3] - box[1] + 48)
                    .append("\" as=\"geometry\"/>\n");
            sb.append("        </mxCell>\n");
        }

        for (Node node : ordered) {
            sb.append("        <mxCell id=\"").append(node.id).append("\" value=\"").append(escape(node.label))
                    .append("\" style=\"rounded=1;whiteSpace=wrap;html=0;fillColor=#dae8fc;strokeColor=#6c8ebf;fontSize=13;\" vertex=\"1\" parent=\"1\">\n");
            sb.append("          <mxGeometry x=\"").append(node.x).append("\" y=\"").append(node.y)
                    .append("\" width=\"").append(NODE_W).append("\" height=\"").append(NODE_H).append("\" as=\"geometry\"/>\n");
            sb.append("        </mxCell>\n");
        }

        int edgeIndex = 0;
        for (Edge edge : edges) {
            String label = edge.label == null ? "" : edge.label;
            sb.append("        <mxCell id=\"edge-").append(edgeIndex++).append("\" value=\"").append(escape(label))
                    .append("\" style=\"edgeStyle=orthogonalEdgeStyle;rounded=1;html=0;endArrow=block;endFill=1;strokeWidth=1.4;fontSize=11;\" edge=\"1\" parent=\"1\" source=\"")
                    .append(edge.from).append("\" target=\"").append(edge.to).append("\">\n");
            sb.append("          <mxGeometry relative=\"1\" as=\"geometry\"/>\n");
            sb.append("        </mxCell>\n");
        }

        sb.append("      </root>\n");
        sb.append("    </mxGraphModel>\n");
        sb.append("  </diagram>\n");
        sb.append("</mxfile>\n");
        return sb.toString();
    }

    private String text(JSONObject item, String key) {
        String value = item.getString(key);
        if (value == null || value.isBlank()) throw invalid("缺少字段：" + key);
        return value.strip();
    }

    private String escape(String value) {
        // 校验器看的是解析后的属性值：标签里出现 < > 会被判为富文本，
        // 所以这里换成全角符号，而不是转义成实体（实体解析后仍然是 < >）。
        String safe = value.replace("<", "＜").replace(">", "＞")
                .replaceAll("(?i)javascript:", "")
                .replaceAll("(?i)data:", "");
        return safe.replace("&", "&amp;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private OfficeException invalid(String message) {
        return new OfficeException(422, "INVALID_DIAGRAM_SPEC", "模型返回的结构不合法：" + message);
    }

    private static final class Node {
        final String id;
        final String label;
        final String group;
        int layer;
        int x;
        int y;

        Node(String id, String label, String group) {
            this.id = id;
            this.label = label;
            this.group = group;
        }
    }

    private static final class Edge {
        final String from;
        final String to;
        final String label;

        Edge(String from, String to, String label) {
            this.from = from;
            this.to = to;
            this.label = label;
        }
    }
}
