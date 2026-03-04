# DataX ElasticSearchWriter


---

## 1 快速介绍

数据导入elasticsearch的插件

## 2 实现原理

使用elasticsearch的rest api接口， 批量把从reader读入的数据写入elasticsearch

## 3 功能说明

### 3.1 配置样例

#### job.json

```
{
  "job": {
    "setting": {
        "speed": {
            "channel": 1
        }
    },
    "content": [
      {
        "reader": {
          ...
        },
        "writer": {
          "name": "elasticsearchwriter",
          "parameter": {
            "endpoint": "http://xxx:9999",
            "accessId": "xxxx",
            "accessKey": "xxxx",
            "index": "test-1",
            "type": "default",
            "cleanup": true,
            "settings": {"index" :{"number_of_shards": 1, "number_of_replicas": 0}},
            "discovery": false,
            "batchSize": 1000,
            "splitter": ",",
            "column": [
              {"name": "pk", "type": "id"},
              { "name": "col_ip","type": "ip" },
              { "name": "col_double","type": "double" },
              { "name": "col_long","type": "long" },
              { "name": "col_integer","type": "integer" },
              { "name": "col_keyword", "type": "keyword" },
              { "name": "col_text", "type": "text", "analyzer": "ik_max_word"},
              { "name": "col_geo_point", "type": "geo_point" },
              { "name": "col_date", "type": "date", "format": "yyyy-MM-dd HH:mm:ss"},
              { "name": "col_nested1", "type": "nested" },
              { "name": "col_nested2", "type": "nested" },
              { "name": "col_object1", "type": "object" },
              { "name": "col_object2", "type": "object" },
              { "name": "col_integer_array", "type":"integer", "array":true},
              { "name": "col_geo_shape", "type":"geo_shape", "tree": "quadtree", "precision": "10m"}
            ]
          }
        }
      }
    ]
  }
}
```

#### 3.2 参数说明

* endpoint
 * 描述：ElasticSearch的连接地址
 * 必选：是
 * 默认值：无

* accessId
 * 描述：http auth中的user
 * 必选：否
 * 默认值：空

* accessKey
 * 描述：http auth中的password
 * 必选：否
 * 默认值：空

* index
 * 描述：elasticsearch中的index名
 * 必选：是
 * 默认值：无

* type
 * 描述：elasticsearch中index的type名
 * 必选：否
 * 默认值：index名
 * 注意：ES 7.x+ 已移除 type 概念，此参数将被忽略。ES 8.x 将完全移除此功能。ES 7.x 建议使用 `_doc` 或直接忽略此参数

* esVersion
 * 描述：Elasticsearch 版本号（主版本号）
 * 必选：否
 * 默认值：自动检测
 * 取值范围：5、6、7
 * 注意：建议在 ES 7.x 环境中显式配置此参数以确保兼容性

* cleanup
 * 描述：是否删除原表（重建索引）
 * 必选：否
 * 默认值：false
 * 注意：设置为 true 会先删除索引，再重新创建。索引已存在时设为 false 可保留现有数据

* batchSize
 * 描述：每次批量数据的条数
 * 必选：否
 * 默认值：1000

* trySize
 * 描述：失败后重试的次数
 * 必选：否
 * 默认值：30

* timeout
 * 描述：客户端超时时间
 * 必选：否
 * 默认值：600000

* discovery
 * 描述：启用节点发现将(轮询)并定期更新客户机中的服务器列表。
 * 必选：否
 * 默认值：false

* compression
 * 描述：http请求，开启压缩
 * 必选：否
 * 默认值：true

* multiThread
 * 描述：http请求，是否有多线程
 * 必选：否
 * 默认值：true

* ignoreWriteError
 * 描述：忽略写入错误，不重试，继续写入
 * 必选：否
 * 默认值：false

* ignoreParseError
 * 描述：忽略解析数据格式错误，继续写入
 * 必选：否
 * 默认值：true

* alias
 * 描述：数据导入完成后写入别名
 * 必选：否
 * 默认值：无

* aliasMode
 * 描述：数据导入完成后增加别名的模式，append(增加模式), exclusive(只留这一个)
 * 必选：否
 * 默认值：append

* settings
 * 描述：创建index时候的settings, 与elasticsearch官方相同
 * 必选：否
 * 默认值：无

* splitter
 * 描述：如果插入数据是array，就使用指定分隔符
 * 必选：否
 * 默认值：-,-

* column
 * 描述：elasticsearch所支持的字段类型，样例中包含了全部
 * 必选：是

* dynamic
 * 描述: 不使用datax的mappings，使用es自己的自动mappings
 * 必选: 否
 * 默认值: false

### 3.3 字段类型说明（ES 7.x）

#### 基本类型

| 类型 | 说明 | 适用场景 | 示例 |
|------|------|----------|------|
| `id` | 文档唯一标识 | 主键字段 | `{"name": "id", "type": "id"}` |
| `keyword` | 精确值（不分词） | 精确匹配、聚合、排序 | ID、状态码、标签 |
| `text` | 全文文本（分词） | 全文搜索 | 标题、内容、描述 |
| `long` | 64位整数 | 数值计算 | 数量、年份 |
| `integer` | 32位整数 | 小数值 | 分数、级别 |
| `double` | 浮点数 | 带小数数值 | 金额、评分 |
| `date` | 日期时间 | 时间字段 | 创建时间、更新时间 |
| `boolean` | 布尔值 | 是/否 | 是否启用、已删除 |

#### 复杂类型

| 类型 | 说明 | 示例 |
|------|------|------|
| `object` | JSON 对象 | 嵌套对象 |
| `nested` | 嵌套文档 | 对象数组 |
| `ip` | IP 地址 | IPv4/IPv6 |
| `geo_point` | 地理坐标点 | 经纬度 |

#### 类型选择建议

**需要精确匹配 → 使用 `keyword`**
```json
{"name": "user_id", "type": "keyword"}
{"name": "status", "type": "keyword"}
{"name": "category", "type": "keyword"}
```

**需要全文搜索 → 使用 `text`**
```json
{"name": "title", "type": "text"}
{"name": "content", "type": "text", "analyzer": "ik_max_word"}
```

**同时需要精确匹配和全文搜索 → 使用 `fields`**
```json
{
  "name": "title",
  "type": "text",
  "fields": {
    "keyword": {"type": "keyword"}
  }
}
```

### 3.4 ES 7.x 迁移指南

#### 从 ES 5.x/6.x 迁移到 ES 7.x

1. **添加版本配置**
```json
{"esVersion": 7}
```

2. **替换 `string` 类型**
```json
// 旧配置（ES 5.x/6.x）
{"name": "status", "type": "string"}

// 新配置（ES 7.x）
{"name": "status", "type": "keyword"}
```

3. **处理 `type` 参数**
```json
// ES 7.x 建议
{"type": "_doc"}

// 或者直接忽略，使用默认值
```

### 3.5 常见问题

#### Q1: No handler for type [string]

**原因**：ES 7.x 已移除 `string` 类型

**解决**：将 `type: "string"` 改为 `type: "keyword"` 或 `type: "text"`

#### Q2: Mapper for [xxx] conflicts with existing mapper

**原因**：索引已存在，且字段类型与配置不一致

**解决方案**：
1. 删除现有索引：设置 `"cleanup": true`
2. 使用新的索引名
3. 修改字段类型以匹配现有索引

#### Q3: 索引已存在，如何更新 mapping？

**说明**：Elasticsearch 不允许修改已有字段的 mapping

**解决方案**：
1. 对于新增字段，会自动添加到现有 mapping
2. 对于已有字段，需要重建索引或使用 reindex API

#### Q4: 如何提高写入性能？

**优化建议**：
1. 增加 `batchSize`（默认 1000，可设为 2000-5000）
2. 增加 `channel` 数量（并发数）
3. 关闭 `discovery`（节点发现）
4. 根据网络情况调整 `timeout`

```json
{
  "setting": {
    "speed": {
      "channel": 3
    }
  },
  "writer": {
    "parameter": {
      "batchSize": 3000,
      "discovery": false,
      "timeout": 300000
    }
  }
}
```