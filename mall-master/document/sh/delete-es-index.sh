#!/bin/bash

# 删除Elasticsearch旧索引脚本

echo "删除 mall_product 索引..."
curl -X DELETE "localhost:9200/mall_product"

echo ""
echo "删除完成，请重启Spring Boot应用"