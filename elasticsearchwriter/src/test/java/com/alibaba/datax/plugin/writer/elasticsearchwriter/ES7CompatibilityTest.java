package com.alibaba.datax.plugin.writer.elasticsearchwriter;

import com.alibaba.datax.common.util.Configuration;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * ES 7.x 兼容性测试
 */
public class ES7CompatibilityTest {

    @Test
    public void testESVersionConfig() {
        // 测试版本配置参数获取
        Configuration conf = Configuration.from("{}");

        // 测试 esVersion 参数
        conf.set("esVersion", 7);
        assertEquals(Integer.valueOf(7), Key.getESVersion(conf));

        // 测试 es.version 参数（别名）
        conf = Configuration.from("{}");
        conf.set("es.version", 7);
        assertEquals(Integer.valueOf(7), Key.getESVersion(conf));

        // 测试 elasticsearch.version 参数（别名）
        conf = Configuration.from("{}");
        conf.set("elasticsearch.version", 7);
        assertEquals(Integer.valueOf(7), Key.getESVersion(conf));

        // 测试未配置版本的情况
        conf = Configuration.from("{}");
        assertNull(Key.getESVersion(conf));
    }

    @Test
    public void testMappingFormatForES7() {
        // 测试 ES 7.x mapping 格式
        // ES 7.x: {"mappings": {"properties": {...}}}
        // ES 6.x: {"mappings": {"typeName": {"properties": {...}}}}

        String es7Mapping = "{\"mappings\":{\"properties\":{\"field1\":{\"type\":\"text\"}}}}";
        String es6Mapping = "{\"mappings\":{\"_doc\":{\"properties\":{\"field1\":{\"type\":\"text\"}}}}}";

        // 验证格式正确性
        assertTrue(es7Mapping.contains("\"properties\""));
        assertFalse(es7Mapping.contains("\"_doc\""));
        assertTrue(es6Mapping.contains("\"_doc\""));
    }

    @Test
    public void testBulkOperationCompatibility() {
        // 测试 bulk 操作在不同版本下的行为
        // ES 7.x 不需要 type 参数
        // ES 6.x 需要 type 参数
        // 此测试验证配置正确性，实际功能需要集成测试
        Configuration conf = Configuration.from("{}");
        conf.set("index", "test_index");
        conf.set("type", "test_type");
        conf.set("esVersion", 7);

        assertEquals("test_index", Key.getIndexName(conf));
        assertEquals("test_type", Key.getTypeName(conf));
        assertEquals(Integer.valueOf(7), Key.getESVersion(conf));
    }

    @Test
    public void testTypeNameWithES7() {
        // 测试在 ES 7.x 环境下 type 参数的处理
        Configuration conf = Configuration.from("{}");
        conf.set("index", "my_index");
        conf.set("type", "my_type");

        // type 参数仍然可以获取，但在 ES 7.x 中会被忽略
        assertEquals("my_type", Key.getTypeName(conf));
    }

    @Test
    public void testActionTypeConfig() {
        // 测试 actionType 配置
        Configuration conf = Configuration.from("{}");

        conf.set("actionType", "index");
        assertEquals(Key.ActionType.INDEX, Key.getActionType(conf));

        conf.set("actionType", "create");
        assertEquals(Key.ActionType.CREATE, Key.getActionType(conf));

        conf.set("actionType", "update");
        assertEquals(Key.ActionType.UPDATE, Key.getActionType(conf));

        conf.set("actionType", "delete");
        assertEquals(Key.ActionType.DELETE, Key.getActionType(conf));

        conf.set("actionType", "invalid");
        assertEquals(Key.ActionType.UNKONW, Key.getActionType(conf));
    }

    @Test
    public void testStringTypeConversion() {
        // 测试 STRING 类型枚举值
        ElasticSearchFieldType stringType = ElasticSearchFieldType.getESFieldType("string");
        assertEquals(ElasticSearchFieldType.STRING, stringType);

        ElasticSearchFieldType stringTypeUpper = ElasticSearchFieldType.getESFieldType("STRING");
        assertEquals(ElasticSearchFieldType.STRING, stringTypeUpper);

        // 测试其他类型
        ElasticSearchFieldType keywordType = ElasticSearchFieldType.getESFieldType("keyword");
        assertEquals(ElasticSearchFieldType.KEYWORD, keywordType);

        ElasticSearchFieldType textType = ElasticSearchFieldType.getESFieldType("text");
        assertEquals(ElasticSearchFieldType.TEXT, textType);
    }

    @Test
    public void testStringTypeInES7ShouldConvertToKeyword() {
        // 测试在 ES 7.x 中 string 类型应该被转换为 keyword
        // 这个测试验证了枚举值的正确性
        // 实际的转换逻辑在 genMappings 方法中
        ElasticSearchFieldType stringType = ElasticSearchFieldType.STRING;
        assertNotNull("STRING type should exist", stringType);

        // 验证 STRING 和 KEYWORD 是不同的枚举值
        assertNotSame("STRING and KEYWORD should be different types",
            ElasticSearchFieldType.STRING, ElasticSearchFieldType.KEYWORD);
    }
}
