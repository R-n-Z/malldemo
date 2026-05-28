package org.example.controller;

import org.example.agent.tool.FaqTools;
import org.example.agent.tool.audit.ExactMatchTools;
import org.example.agent.tool.audit.HybridSearchTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * RAG 检索性能测试专用 Controller
 * 直接暴露底层检索工具，绕过 Agent 编排层，获取原始检索分数
 */
@RestController
@RequestMapping("/rag-test")
public class RagTestController {

    private static final Logger log = LoggerFactory.getLogger(RagTestController.class);

    @Autowired
    private FaqTools faqTools;

    @Autowired
    private ExactMatchTools exactMatchTools;

    @Autowired
    private HybridSearchTools hybridSearchTools;

    /** FAQ 向量检索 */
    @PostMapping("/faq/search")
    public String faqSearch(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "");
        log.info("[RAG-Test] FAQ检索: {}", query);
        return faqTools.searchFaq(query);
    }

    /** 精确关键词匹配 */
    @PostMapping("/exact/match")
    public String exactMatch(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        log.info("[RAG-Test] 精确匹配: {}", text);
        return exactMatchTools.exactMatchKeywords(text);
    }

    /** 混合检索（向量+精确） */
    @PostMapping("/hybrid/search")
    public String hybridSearch(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        log.info("[RAG-Test] 混合检索: {}", text);
        return hybridSearchTools.hybridKeywordSearch(text);
    }

    /** 健康检查 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "availableTools",
                new String[]{"faq/search", "exact/match", "hybrid/search"});
    }
}
