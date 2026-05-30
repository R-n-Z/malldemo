package org.example.controller;

import org.example.agent.tool.FaqTools;
import org.example.agent.tool.audit.ExactMatchTools;
import org.example.agent.tool.audit.HybridSearchTools;
import org.example.service.ReturnRuleAgentService;
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

    @Autowired
    private ReturnRuleAgentService returnRuleAgentService;

    /** FAQ 向量检索 */
    @PostMapping("/faq/search")
    public String faqSearch(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "");
        long t0 = System.currentTimeMillis();
        String result = faqTools.searchFaq(query);
        log.info("[RAG-Latency] FAQ检索: {}ms query={}", System.currentTimeMillis() - t0, query);
        return result;
    }

    /** 精确关键词匹配 */
    @PostMapping("/exact/match")
    public String exactMatch(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        long t0 = System.currentTimeMillis();
        String result = exactMatchTools.exactMatchKeywords(text);
        log.info("[RAG-Latency] 精确匹配: {}ms text={}", System.currentTimeMillis() - t0,
                text.substring(0, Math.min(40, text.length())));
        return result;
    }

    /** 混合检索（向量+精确），支持运行时权重覆盖 */
    @PostMapping("/hybrid/search")
    public String hybridSearch(@RequestBody Map<String, String> body,
            @RequestParam(defaultValue = "0") double vw,
            @RequestParam(defaultValue = "0") double kw,
            @RequestParam(defaultValue = "0") double vt,
            @RequestParam(defaultValue = "0") int vtk) {
        String text = body.getOrDefault("text", "");
        long t0 = System.currentTimeMillis();
        String result;
        if (vw > 0) {
            result = hybridSearchTools.hybridKeywordSearch(text, vw, kw, vt, vtk);
            log.info("[RAG-Latency] 混合检索(权重覆盖): {}ms vw={} kw={} text={}",
                    System.currentTimeMillis() - t0, vw, kw, text);
        } else {
            result = hybridSearchTools.hybridKeywordSearch(text);
            log.info("[RAG-Latency] 混合检索: {}ms text={}",
                    System.currentTimeMillis() - t0, text);
        }
        return result;
    }

    /** 语义分析（直接暴露 analyzeReturnText，用于测试缓存效果） */
    @PostMapping("/analyze/text")
    public String analyzeText(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        long t0 = System.currentTimeMillis();
        String result = returnRuleAgentService.analyzeReturnText(text);
        log.info("[RAG-Latency] 语义分析: {}ms text={}", System.currentTimeMillis() - t0, text);
        return result;
    }

    /** 健康检查 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "availableTools",
                new String[]{"faq/search", "exact/match", "hybrid/search"});
    }
}
