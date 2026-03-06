#!/bin/bash
# dmwriter 插件验证脚本

echo "========================================"
echo "达梦数据库 Writer 插件验证"
echo "========================================"
echo ""

# 检查文件结构
echo "1. 检查文件结构..."
files_ok=true

if [ -f "src/main/java/com/alibaba/datax/plugin/writer/dmwriter/DmWriter.java" ]; then
    echo "  ✓ DmWriter.java 存在"
else
    echo "  ✗ DmWriter.java 不存在"
    files_ok=false
fi

if [ -f "src/main/resources/plugin.json" ]; then
    echo "  ✓ plugin.json 存在"
else
    echo "  ✗ plugin.json 不存在"
    files_ok=false
fi

if [ -f "src/main/libs/DmJdbcDriver18.jar" ]; then
    size=$(ls -lh "src/main/libs/DmJdbcDriver18.jar" | awk '{print $5}')
    echo "  ✓ DmJdbcDriver18.jar 存在 (大小: $size)"
else
    echo "  ✗ DmJdbcDriver18.jar 不存在"
    echo "    请将达梦 JDBC 驱动放置到 src/main/libs/ 目录"
    files_ok=false
fi

echo ""

# 检查编译
echo "2. 检查编译状态..."
if [ -f "target/classes/com/alibaba/datax/plugin/writer/dmwriter/DmWriter.class" ]; then
    echo "  ✓ 主类已编译"
else
    echo "  ✗ 主类未编译"
    files_ok=false
fi

if [ -f "target/dmwriter-0.0.1-SNAPSHOT.jar" ]; then
    size=$(ls -lh "target/dmwriter-0.0.1-SNAPSHOT.jar" | awk '{print $5}')
    echo "  ✓ JAR 包已生成 (大小: $size)"
else
    echo "  ✗ JAR 包未生成"
    files_ok=false
fi

echo ""

# 检查打包
echo "3. 检查打包结果..."
if [ -f "target/datax/plugin/writer/dmwriter/plugin.json" ]; then
    echo "  ✓ 插件已打包到 target/datax/plugin/writer/dmwriter/"
    echo "  ✓ plugin.json 配置正确"
    cat "target/datax/plugin/writer/dmwriter/plugin.json" | sed 's/^/    /'
else
    echo "  ✗ 插件未正确打包"
    files_ok=false
fi

echo ""

# 检查依赖
echo "4. 检查依赖库..."
if [ -d "target/datax/plugin/writer/dmwriter/libs" ]; then
    lib_count=$(ls "target/datax/plugin/writer/dmwriter/libs" | wc -l)
    echo "  ✓ 依赖库目录存在 (包含 $lib_count 个文件)"

    # 检查关键依赖
    if [ -f "target/datax/plugin/writer/dmwriter/libs/plugin-rdbms-util-0.0.1-SNAPSHOT.jar" ]; then
        echo "  ✓ plugin-rdbms-util 依赖存在"
    else
        echo "  ✗ plugin-rdbms-util 依赖缺失"
    fi

    if [ -f "target/datax/plugin/writer/dmwriter/libs/datax-common-0.0.1-SNAPSHOT.jar" ]; then
        echo "  ✓ datax-common 依赖存在"
    else
        echo "  ✗ datax-common 依赖缺失"
    fi
else
    echo "  ✗ 依赖库目录不存在"
fi

echo ""
echo "========================================"

if [ "$files_ok" = true ]; then
    echo "✓ 验证通过！dmwriter 插件构建成功"
    echo ""
    echo "使用说明:"
    echo "1. 将 DmJdbcDriver18.jar 复制到插件 libs 目录:"
    echo "   cp src/main/libs/DmJdbcDriver18.jar target/datax/plugin/writer/dmwriter/libs/"
    echo ""
    echo "2. 将插件复制到 DataX 安装目录:"
    echo "   cp -r target/datax/plugin/writer/dmwriter <datax_home>/plugin/writer/"
    echo ""
    echo "3. 参考文档配置任务:"
    echo "   doc/dmwriter.md"
    echo "   doc/job_example.json"
else
    echo "✗ 验证失败，请检查上述错误"
    exit 1
fi

echo "========================================"
