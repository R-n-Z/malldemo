package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rule.rag.hybrid")
public class HybridSearchProperties {
    private int vectorTopK = 5;
    private double vectorThreshold = 0.4;
    private double keywordWeight = 1.0;
    private double vectorWeight = 0.8;

    public int getVectorTopK() { return vectorTopK; }
    public void setVectorTopK(int vectorTopK) { this.vectorTopK = vectorTopK; }

    public double getVectorThreshold() { return vectorThreshold; }
    public void setVectorThreshold(double vectorThreshold) { this.vectorThreshold = vectorThreshold; }

    public double getKeywordWeight() { return keywordWeight; }
    public void setKeywordWeight(double keywordWeight) { this.keywordWeight = keywordWeight; }

    public double getVectorWeight() { return vectorWeight; }
    public void setVectorWeight(double vectorWeight) { this.vectorWeight = vectorWeight; }
}
