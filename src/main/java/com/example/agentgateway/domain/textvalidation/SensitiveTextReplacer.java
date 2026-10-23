package com.example.agentgateway.domain.textvalidation;

import com.example.agentgateway.domain.filteredstream.StreamReplacementCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对完整文本执行敏感词替换。匹配策略与流式内容处理器一致：
 * 同一位置存在多个候选词时，优先替换最长的敏感词。
 */
public class SensitiveTextReplacer {
    private final Map<String, String> replacements;
    private final List<String> sensitiveWords;

    public SensitiveTextReplacer(StreamReplacementCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("敏感词目录不能为空");
        }
        this.replacements = validateAndCopy(catalog.sensitiveReplacements());
        this.sensitiveWords = new ArrayList<String>(replacements.keySet());
        Collections.sort(this.sensitiveWords,
                Comparator.comparingInt(String::length).reversed());
    }

    public String replace(String text) {
        if (text == null || text.isEmpty() || sensitiveWords.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            String matchedWord = longestWordAt(text, index);
            if (matchedWord == null) {
                result.append(text.charAt(index));
                index++;
            } else {
                result.append(replacements.get(matchedWord));
                index += matchedWord.length();
            }
        }
        return result.toString();
    }

    private String longestWordAt(String text, int index) {
        for (String word : sensitiveWords) {
            if (text.startsWith(word, index)) {
                return word;
            }
        }
        return null;
    }

    private Map<String, String> validateAndCopy(Map<String, String> source) {
        if (source == null) {
            throw new IllegalArgumentException("敏感词替换映射不能为空");
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty()) {
                throw new IllegalArgumentException("敏感词不能为空字符串");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("敏感词替换值不能为空");
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }
}
