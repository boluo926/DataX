## 版本支持

本插件支持 Elasticsearch 5.x、6.x、7.x 版本：

- **Elasticsearch 5.x**：完全支持，已测试
- **Elasticsearch 6.x**：完全支持，兼容性良好
- **Elasticsearch 7.x**：支持，建议配置 `esVersion: 7` 参数

### ES 7.x 配置示例

```json
{
  "writer": {
    "name": "elasticsearchwriter",
    "parameter": {
      "esVersion": 7,
      "endpoint": "http://localhost:9200",
      "accessId": "elastic",
      "accessKey": "password",
      "index": "my_index",
      "type": "_doc",
      "cleanup": false,
      "batchSize": 1000,
      "column": [
        {"name": "id", "type": "id"},
        {"name": "title", "type": "text"},
        {"name": "status", "type": "keyword"},
        {"name": "create_time", "type": "date"}
      ]
    }
  }
}
```

### 字段类型选择指南（ES 7.x）

| 数据类型 | 推荐使用 | 说明 | 示例 |
|----------|----------|------|------|
| 唯一标识 | `id` | 文档主键 | `{"name": "id", "type": "id"}` |
| 精确匹配 | `keyword` | 用于过滤、聚合、排序 | ID、状态码、分类 |
| 全文搜索 | `text` | 需要分词搜索 | 标题、内容、描述 |
| 日期时间 | `date` | 日期格式 | 创建时间、更新时间 |
| 整数 | `long` | 长整数 | 年份、数量 |
| 浮点数 | `double` | 带小数 | 金额、坐标 |

### 重要提示

1. **`string` 类型已弃用**：ES 7.x 不支持 `string` 类型，请使用 `keyword` 或 `text`
   - `keyword`：精确匹配（如 ID、状态码）
   - `text`：全文搜索（如标题、内容）

2. **`type` 参数**：ES 7.x 已移除 mapping type 概念，建议使用 `_doc` 或直接忽略此参数

3. **索引已存在**：如果目标索引已存在，插件会自动跳过 mapping 创建，避免冲突

4. **版本检测**：建议显式配置 `esVersion: 7` 参数，避免自动检测失败

### 常见问题

**Q: 出现 `No handler for type [string]` 错误？**
A: 将配置中的 `type: "string"` 改为 `type: "keyword"` 或 `type: "text"`

**Q: 出现 `Mapper for [xxx] conflicts with existing mapper` 错误？**
A: 目标索引已存在且字段类型不匹配，删除索引或使用新索引名，或设置 `cleanup: true`

**Q: 如何同时支持精确匹配和全文搜索？**
A: 使用 `fields` 参数配置多字段：
```json
{"name": "title", "type": "text", "fields": {"keyword": {"type": "keyword"}}}
```




