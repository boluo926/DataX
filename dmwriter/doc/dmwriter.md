# DataX DmWriter


---


## 1 快速介绍

DmWriter 插件实现了写入数据到达梦数据库（DM8）目的表的功能。在底层实现上，DmWriter 通过 JDBC 连接远程达梦数据库，并执行相应的 `insert into...` 或者 `merge into...` 的 sql 语句将数据写入达梦数据库，内部会分批次提交入库。

DmWriter 面向 ETL 开发工程师，他们使用 DmWriter 从数仓导入数据到达梦数据库。同时 DmWriter 亦可以作为数据迁移工具为 DBA 等用户提供服务。

## 2 实现原理

DmWriter 通过 DataX 框架获取 Reader 生成的协议数据，根据你配置的 `writeMode` 生成


* `insert into...`(当主键/唯一性索引冲突时会写不进去冲突的行)

##### 或者

* `merge into...`(使用 MERGE INTO 语法实现 upsert 功能，当主键冲突时更新现有行，否则插入新行) 的语句写入数据到达梦数据库。出于性能考虑，采用了 `PreparedStatement + Batch`，将数据缓冲到线程上下文 Buffer 中，当 Buffer 累计到预定阈值时，才发起写入请求。

<br />

    **注意**：
    1. 达梦数据库不支持 MySQL 的 `REPLACE INTO` 语法，使用 `MERGE INTO` 实现 upsert 功能
    2. 使用 replace/update 模式时，目标表必须有主键
    3. `MERGE INTO` 不支持批量操作，会自动设置 batchSize=1，性能较 insert 模式慢
    4. 目的表所在数据库必须具备 insert/merge 权限，是否需要其他权限，取决于你任务配置中在 preSql 和 postSql 中指定的语句


## 3 功能说明

### 3.1 配置样例

* 这里使用一份从内存产生到达梦数据库导入的数据。

```json
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
                    "name": "streamreader",
                    "parameter": {
                        "column" : [
                            {
                                "value": "DataX",
                                "type": "string"
                            },
                            {
                                "value": 19880808,
                                "type": "long"
                            },
                            {
                                "value": "1988-08-08 08:08:08",
                                "type": "date"
                            },
                            {
                                "value": true,
                                "type": "bool"
                            },
                            {
                                "value": "test",
                                "type": "bytes"
                            }
                        ],
                        "sliceRecordCount": 1000
                    }
                },
                "writer": {
                    "name": "dmwriter",
                    "parameter": {
                        "writeMode": "insert",
                        "username": "SYSDBA",
                        "password": "SYSDBA",
                        "column": [
                            "id",
                            "name"
                        ],
                        "session": [
                            "SET SCHEMA TEST"
                        ],
                        "preSql": [
                            "delete from test"
                        ],
                        "connection": [
                            {
                                "jdbcUrl": "jdbc:dm://127.0.0.1:5236/DAMENG",
                                "table": [
                                    "test"
                                ]
                            }
                        ]
                    }
                }
            }
        ]
    }
}
```

### 3.2 参数说明

* **jdbcUrl**

	* 描述：目的数据库的 JDBC 连接信息。JDBC URL 遵循达梦数据库官方规范。

               注意：1、在一个数据库上只能配置一个 jdbcUrl 值
                    2、jdbcUrl 按照达梦官方规范，格式为：`jdbc:dm://host:5236/database`
                    3、连接附加控制信息请参考达梦数据库官方文档

 	* 必选：是 <br />

	* 默认值：无 <br />

* **username**

	* 描述：目的数据库的用户名 <br />

	* 必选：是 <br />

	* 默认值：无 <br />

* **password**

	* 描述：目的数据库的密码 <br />

	* 必选：是 <br />

	* 默认值：无 <br />

* **table**

	* 描述：目的表的表名称。支持写入一个或者多个表。当配置为多张表时，必须确保所有表结构保持一致。

               注意：table 和 jdbcUrl 必须包含在 connection 配置单元中

	* 必选：是 <br />

	* 默认值：无 <br />

* **column**

	* 描述：目的表需要写入数据的字段，字段之间用英文逗号分隔。例如: `"column": ["id","name","age"]`。如果要依次写入全部列，使用 `*` 表示，例如：`"column": ["*"]`。

			**column 配置项必须指定，不能留空！**

               注意：1、我们强烈不推荐你这样配置，因为当你目的表字段个数、类型等有改动时，你的任务可能运行不正确或者失败
                    2、column 不能配置任何常量值

	* 必选：是 <br />

	* 默认值：否 <br />

* **session**

	* 描述：DataX 在获取达梦数据库连接时，执行 session 指定的 SQL 语句，修改当前 connection session 属性

	* 必选：否

	* 默认值：空

* **preSql**

	* 描述：写入数据到目的表前，会先执行这里的标准语句。如果 Sql 中有你需要操作到的表名称，请使用 `@table` 表示，这样在实际执行 Sql 语句时，会对变量按照实际表名称进行替换。比如你的任务是要写入到目的端的 100 个同构分表(表名称为：datax_00, datax_01, ... datax_98, datax_99)，并且你希望导入数据前，先对表中数据进行删除操作，那么你可以这样配置：`"preSql":["delete from 表名"]`，效果是：在执行到每个表写入数据前，会先执行对应的 delete from 对应表名称 <br />

	* 必选：否 <br />

	* 默认值：无 <br />

* **postSql**

	* 描述：写入数据到目的表后，会执行这里的标准语句。（原理同 preSql ） <br />

	* 必选：否 <br />

	* 默认值：无 <br />

* **writeMode**

	* 描述：控制写入数据到目标表采用 `insert into` 或者 `merge into` 语句 <br />

	* 必选：是 <br />

	* 所有选项：insert/replace/update <br />

	* 默认值：insert <br />

    **注意**：
    - insert 模式：标准插入，主键冲突时报错，支持批量操作，性能最优
    - replace 模式：使用 MERGE INTO，主键冲突时更新，否则插入，不支持批量操作
    - update 模式：与 replace 相同，使用 MERGE INTO
    - 使用 replace/update 模式时，目标表必须有主键

* **batchSize**

	* 描述：一次性批量提交的记录数大小，该值可以极大减少 DataX 与达梦数据库的网络交互次数，并提升整体吞吐量。但是该值设置过大可能会造成 DataX 运行进程 OOM 情况。<br />

               注意：使用 replace/update 模式时，该参数会自动设置为 1（MERGE INTO 不支持批量操作）

	* 必选：否 <br />

	* 默认值：2048 <br />

* **batchByteSize**

	* 描述：一次性批量提交的记录总字节数大小，当累计的字节达到阈值时，才会发起写入请求。<br />

	* 必选：否 <br />

	* 默认值：32M <br />

* **emptyAsNull**

	* 描述：空字符串是否作为 null 处理 <br />

	* 必选：否 <br />

	* 默认值：true <br />

### 3.3 类型转换

目前 DmWriter 支持大部分达梦数据库类型，但也存在部分个别类型没有支持的情况，请注意检查你的类型。

下面列出 DmWriter 针对达梦数据库类型转换列表：

| DataX 内部类型 | 达梦数据库数据类型 |
| -------- | ----- |
| Long | INT, TINYINT, SMALLINT, BIGINT, DECIMAL, DEC, NUMBER |
| Double | FLOAT, DOUBLE, REAL, DECIMAL, DEC, NUMBER |
| String | VARCHAR, VARCHAR2, CHAR, CHARACTER, TEXT, CLOB |
| Date | DATE, TIME, TIMESTAMP, DATETIME |
| Boolean | BIT, BOOL |
| Bytes | BLOB, VARBINARY, BINARY, IMAGE |

## 4 约束限制

1. **主键要求**：
   - 使用 replace/update 模式时，目标表必须有主键
   - 无主键的表只能使用 insert 模式

2. **批量操作限制**：
   - INSERT 模式支持批量操作，性能最优
   - REPLACE/UPDATE 模式不支持批量操作，会自动设置 batchSize=1

3. **数据库版本**：
   - 仅支持达梦数据库 DM8 及以上版本
   - 需要使用 DM8 版本的 JDBC 驱动（DmJdbcDriver18.jar）

4. **数据类型限制**：
   - 请参考类型转换列表，部分特殊类型可能不支持

5. **连接数限制**：
   - 通道数不应过大，建议不超过 16
   - 过多通道可能导致数据库连接数不足

## FAQ

***

**Q: DmWriter 执行 postSql 语句报错，那么数据导入到目标数据库了吗？**

A: DataX 导入过程存在三块逻辑，pre 操作、导入操作、post 操作，其中任意一环报错，DataX 作业报错。由于 DataX 不能保证在同一个事务完成上述几个操作，因此有可能数据已经落入到目标端。

***

**Q: 按照上述说法，那么有部分脏数据导入数据库，如果影响到线上数据库怎么办？**

A: 目前有两种解法，第一种配置 pre 语句，该 sql 可以清理当天导入数据，DataX 每次导入时候可以把上次清理干净并导入完整数据。第二种，向临时表导入数据，完成后再移动到线上表。

***

**Q: 使用 replace/update 模式时提示"需要表有主键"，但我的表确实有主键，怎么办？**

A: 请检查：
1. 主键是否正确创建
2. 用户是否有权限读取数据库元数据
3. 表名大小写是否正确（达梦数据库默认大写）
如果问题仍然存在，请使用 insert 模式

***

**Q: 为什么 replace/update 模式比 insert 模式慢这么多？**

A: 因为达梦数据库的 replace/update 模式使用 MERGE INTO 语法实现，该语法不支持批量操作，必须逐条执行。如果不需要处理主键冲突，建议使用 insert 模式以获得最佳性能。

***

**Q: 如何判断应该使用哪种 writeMode？**

A:
- **insert 模式**：适合全量同步、临时表导入、无主键冲突的场景
- **replace/update 模式**：适合增量同步、需要处理主键冲突的场景

如果数据源本身已经去重或目标表是临时表，推荐使用 insert 模式。
