#!/bin/bash

# Elasticsearch IK分词器安装脚本
# 使用前请修改ES_VERSION为你的Elasticsearch版本

ES_VERSION="7.10.2"
ES_HOME="/usr/share/elasticsearch"

echo "开始安装Elasticsearch IK分词器..."

# 检查是否以root用户运行
if [ "$EUID" -ne 0 ]; then
    echo "请使用root用户运行此脚本"
    exit 1
fi

# 停止Elasticsearch服务
echo "停止Elasticsearch服务..."
systemctl stop elasticsearch || pkill -f elasticsearch

# 进入Elasticsearch安装目录
cd $ES_HOME

# 备份plugins目录
if [ -d "plugins/ik" ]; then
    echo "已存在ik分词器，先删除..."
    rm -rf plugins/ik
fi

# 下载并安装ik分词器
echo "下载ik分词器..."
IK_URL="https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v${ES_VERSION}/elasticsearch-analysis-ik-${ES_VERSION}.zip"

# 检查URL是否有效
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" $IK_URL)
if [ "$HTTP_CODE" != "200" ]; then
    echo "版本 ${ES_VERSION} 不存在，尝试其他版本..."
    # 尝试7.x版本
    IK_URL="https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v7.x/elasticsearch-analysis-ik-7.x.zip"
fi

echo "安装IK分词器: $IK_URL"
elasticsearch-plugin install --batch $IK_URL

# 清理zip文件
rm -f *.zip

# 修复权限
echo "修复权限..."
chown -R elasticsearch:elasticsearch plugins/

# 启动Elasticsearch
echo "启动Elasticsearch服务..."
systemctl start elasticsearch

# 等待启动
sleep 10

# 验证安装
echo "验证ik分词器安装..."
curl -s "localhost:9200/_analyze?pretty" -H 'Content-Type: application/json' -d '{
  "analyzer": "ik_max_word",
  "text": "中华人民共和国"
}'

echo ""
echo "安装完成！"
echo "如果上面没有显示分词结果，请检查Elasticsearch日志: journalctl -u elasticsearch -f"