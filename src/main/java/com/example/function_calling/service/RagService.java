package com.example.function_calling.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RAG 核心服务：文档入库 + 检索增强生成
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int DEFAULT_TOP_K = 3;
    private static final int CHUNK_SIZE = 500;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatModel chatModel;

    @Value("${rag.qdrant.collection-name:knowledge-base}")
    private String collectionName;

    public RagService(EmbeddingModel embeddingModel,
                      EmbeddingStore<TextSegment> embeddingStore,
                      ChatModel chatModel) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatModel = chatModel;
    }

    /**
     * 将文本内容切分、向量化后存入 Qdrant
     */
    public int ingest(String content) {
        return ingest(content, null);
    }

    /**
     * 带元数据的文档入库
     */
    public int ingest(String content, String source) {
        if (content == null || content.isBlank()) {
            return 0;
        }

        // 1. 按段落或固定长度切分文本
        List<TextSegment> segments = splitText(content, source);

        // 2. 批量生成向量
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // 3. 批量存入向量数据库
        embeddingStore.addAll(embeddings, segments);

        log.info("成功入库 {} 个文本片段（来源: {}）", segments.size(), source != null ? source : "未知");
        return segments.size();
    }

    /**
     * RAG 查询：检索相关文档 + LLM 生成回答
     */
    public String query(String question) {
        return query(question, DEFAULT_TOP_K);
    }

    public String query(String question, int topK) {
        // 1. 将问题向量化
        Embedding queryEmbedding = embeddingModel.embed(question).content();

        // 2. 在向量数据库中检索最相关的文档
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        // 3. 收集检索到的文档内容
        List<String> retrievedDocs = new ArrayList<>();
        if (searchResult.matches() != null) {
            searchResult.matches().forEach(match -> {
                if (match.embedded() != null) {
                    retrievedDocs.add(match.embedded().text());
                }
            });
        }

        if (retrievedDocs.isEmpty()) {
            return "未在知识库中找到相关信息，请尝试其他问题或联系管理员补充资料。";
        }

        // 4. 构建 RAG Prompt
        String context = String.join("\n---\n", retrievedDocs);
        String prompt = String.format(
                "你是一个专业的知识问答助手。请根据以下检索到的参考资料回答用户的问题。\n" +
                "如果参考资料中没有相关信息，请如实说明，不要编造答案。\n\n" +
                "参考资料：\n%s\n\n" +
                "用户问题：%s\n\n" +
                "请基于参考资料回答：",
                context, question
        );

        // 5. 调用 LLM 生成回答
        return chatModel.chat(prompt);
    }

    /**
     * 文本切分：按段落切分，超过 CHUNK_SIZE 的段落再按句号切分
     */
    private List<TextSegment> splitText(String content, String source) {
        List<TextSegment> segments = new ArrayList<>();

        // 先按双换行符切分段落
        String[] paragraphs = content.split("\\n\\s*\\n");

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.length() <= CHUNK_SIZE) {
                // 短段落直接作为一个片段
                segments.add(buildSegment(trimmed, source));
            } else {
                // 长段落按句号切分
                int start = 0;
                while (start < trimmed.length()) {
                    int end = Math.min(start + CHUNK_SIZE, trimmed.length());
                    if (end < trimmed.length()) {
                        // 尝试在句号处切分
                        int lastPeriod = trimmed.lastIndexOf('。', end);
                        if (lastPeriod > start) {
                            end = lastPeriod + 1;
                        }
                    }
                    String chunk = trimmed.substring(start, end).trim();
                    if (!chunk.isEmpty()) {
                        segments.add(buildSegment(chunk, source));
                    }
                    start = end;
                }
            }
        }

        return segments;
    }

    private TextSegment buildSegment(String text, String source) {
        if (source != null && !source.isBlank()) {
            Metadata metadata = new Metadata();
            metadata.put("source", source);
            return TextSegment.from(text, metadata);
        }
        return TextSegment.from(text);
    }
}
