package com.actmcp.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider activityToolCallbackProvider(ActivityMcpTools activityMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(activityMcpTools)
                .build();
    }

    /**
     * Prompt 规格由 @McpPrompt 注解方法生成，交由 MCP 服务端自动注册。
     */
    @Bean
    public List<McpServerFeatures.SyncPromptSpecification> activityPromptSpecifications(
            ActivityMcpPrompts activityMcpPrompts) {
        return SyncMcpAnnotationProviders.promptSpecifications(List.of(activityMcpPrompts));
    }

    /**
     * 固定 URI 资源规格，对应 @McpResource 中不带占位符的 URI。
     */
    @Bean
    public List<McpServerFeatures.SyncResourceSpecification> activityResourceSpecifications(
            ActivityMcpResources activityMcpResources) {
        return SyncMcpAnnotationProviders.resourceSpecifications(List.of(activityMcpResources));
    }

    /**
     * URI 模板资源规格，对应 @McpResource 中带 {var} 占位符的 URI。
     */
    @Bean
    public List<McpServerFeatures.SyncResourceTemplateSpecification> activityResourceTemplateSpecifications(
            ActivityMcpResources activityMcpResources) {
        return SyncMcpAnnotationProviders.resourceTemplateSpecifications(List.of(activityMcpResources));
    }
}
