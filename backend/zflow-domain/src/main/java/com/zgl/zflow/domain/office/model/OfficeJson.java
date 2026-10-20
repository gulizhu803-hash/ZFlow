package com.zgl.zflow.domain.office.model;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.zgl.zflow.types.exception.OfficeException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

public final class OfficeJson {
    private OfficeJson() {}
    public static String json(Object value) { return JSON.toJSONString(value, JSONWriter.Feature.WriteMapNullValue); }
    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
    public static String id(String prefix) { return prefix + "_" + UUID.randomUUID().toString().replace("-", ""); }
    public static String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static void key(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,64}")) throw OfficeException.invalid("Idempotency-Key 须为 8–64 位 ASCII 字母、数字、连字符或下划线");
    }
    public static void page(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100 || (long) (page - 1) * pageSize > Integer.MAX_VALUE)
            throw OfficeException.invalid("分页参数不合法");
    }
    public static String time(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC).toString(); }
    public static OfficeRequest request(OfficeRun run) { return JSON.parseObject(run.getRequestSnapshot(), OfficeRequest.class); }
}
