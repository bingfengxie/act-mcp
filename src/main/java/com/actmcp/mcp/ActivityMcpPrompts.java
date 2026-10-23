package com.actmcp.mcp;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP Prompt 层：面向业务场景的标准化提示词模板。
 * <p>
 * Prompt 只负责组装上下文与输出规范，具体数据由调用方（Agent）通过工具获取。
 */
@Component
public class ActivityMcpPrompts {

    private static final String DEFAULT_REVIEW_PERIOD = "最近 7 天";

    /**
     * 场景一：大活动健康度巡检。
     */
    @McpPrompt(name = "activity_health_review",
            title = "大活动健康度巡检",
            description = "生成大活动健康度巡检报告，覆盖投放规模、预算消耗、商品活动覆盖与风险提示。")
    public GetPromptResult activityHealthReview(
            @McpArg(name = "act_id", required = false,
                    description = "大活动 ID。不传时巡检全部进行中的大活动。") String actId,
            @McpArg(name = "review_period", required = false,
                    description = "巡检周期描述，默认：最近 7 天。") String reviewPeriod) {

        String scope = (actId == null || actId.isBlank())
                ? "全部进行中的大活动"
                : "大活动 ID = " + actId.trim();
        String period = (reviewPeriod == null || reviewPeriod.isBlank())
                ? DEFAULT_REVIEW_PERIOD
                : reviewPeriod.trim();

        String content = """
                # 角色
                你是公司活动运营中心的数据分析助手，负责输出规范、可追溯的活动巡检结论。

                # 任务
                针对【%s】，完成【%s】的活动健康度巡检，并输出一份可直接进入周会材料的结构化报告。

                # 数据获取要求
                1. 先用 list_big_activities 获取活动清单，必要时用 get_big_activity 补齐活动详情。
                2. 用 list_product_activities 获取活动下的商品活动，统计覆盖的 POI、SKU 数量。
                3. 用 get_activity_stats 获取全局口径数据，用于横向对比。
                4. 所有结论必须能回溯到工具返回的字段，禁止编造数据。

                # 分析维度
                - 投放规模：活动数量、商品活动数量、覆盖 POI/SKU 数。
                - 预算执行：预算总额与活动周期是否匹配，是否存在预算闲置或超支风险。
                - 配置质量：活动类型、活动人群、起止时间、优惠 JSON 是否存在缺失或异常。
                - 风险提示：即将到期、名称重复、商品活动为空、时间冲突等。

                # 输出格式
                ## 一、巡检概述
                ## 二、核心指标
                以表格呈现：指标名称 | 数值 | 环比/基准 | 结论
                ## 三、问题清单
                以表格呈现：级别(P0/P1/P2) | 问题描述 | 影响范围 | 建议动作 | 责任方
                ## 四、结论与建议
                不超过 5 条，按优先级排序。

                # 约束
                - 使用正式书面语，不使用口语化表达。
                - 缺失数据需明确标注“数据缺失”，不得推测。
                - 报告末尾注明数据快照时间与数据来源（工具名称）。
                """.formatted(scope, period);

        return new GetPromptResult(
                "大活动健康度巡检提示词",
                List.of(new PromptMessage(Role.USER, new TextContent(content)))
        );
    }

    /**
     * 场景二：活动上线文案生成。
     */
    @McpPrompt(name = "activity_launch_copy",
            title = "活动上线文案生成",
            description = "根据活动配置生成渠道化的上线文案，包含标题、正文、行动号召与合规校验项。")
    public GetPromptResult activityLaunchCopy(
            @McpArg(name = "act_id", required = true,
                    description = "大活动 ID。") String actId,
            @McpArg(name = "channel", required = false,
                    description = "投放渠道，例如：APP 首页、小程序、短信、企业微信。默认：APP 首页。") String channel,
            @McpArg(name = "tone", required = false,
                    description = "文案语气，例如：专业、活泼、紧迫。默认：专业。") String tone) {

        String resolvedChannel = (channel == null || channel.isBlank()) ? "APP 首页" : channel.trim();
        String resolvedTone = (tone == null || tone.isBlank()) ? "专业" : tone.trim();
        String resolvedActId = (actId == null || actId.isBlank()) ? "未提供" : actId.trim();

        String content = """
                # 角色
                你是公司品牌与活动运营团队的文案策划，输出符合品牌规范、可直接投放的营销文案。

                # 任务
                基于大活动 ID = %s 的配置，产出面向【%s】渠道、语气风格为【%s】的活动上线文案。

                # 数据获取要求
                1. 使用 get_big_activity 获取活动名称、活动类型、活动人群、起止时间与优惠内容。
                2. 使用 list_product_activities 获取参与商品范围，提炼可感知的利益点。
                3. 文案中的时间、优惠力度、适用人群必须与活动配置完全一致。

                # 输出要求
                ## 一、标题（3 条）
                每条不超过 20 字，突出利益点，避免绝对化用语。
                ## 二、正文
                150 字以内，结构为：场景痛点 → 活动利益 → 参与方式。
                ## 三、行动号召
                1 条，动词开头，明确下一步操作。
                ## 四、合规校验
                逐项确认：价格表述、活动期限、适用范围、免责声明是否齐全。

                # 约束
                - 不得使用“最”“第一”“绝对”等违反广告法的表述。
                - 不得虚构活动配置中不存在的权益。
                - 面向 %s 渠道时，需适配该渠道的篇幅与表达习惯。
                """.formatted(resolvedActId, resolvedChannel, resolvedTone, resolvedChannel);

        return new GetPromptResult(
                "活动上线文案生成提示词",
                List.of(new PromptMessage(Role.USER, new TextContent(content)))
        );
    }
}
