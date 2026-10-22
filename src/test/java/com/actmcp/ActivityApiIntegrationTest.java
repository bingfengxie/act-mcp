package com.actmcp;

import com.actmcp.controller.BigActivityController;
import com.actmcp.controller.ProductActivityController;
import com.actmcp.controller.StatsController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ActivityApiIntegrationTest {

    private static final Path TEST_DB;

    static {
        try {
            TEST_DB = Files.createTempFile("act-mcp-test-", ".db");
            Files.deleteIfExists(TEST_DB);
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + TEST_DB.toAbsolutePath());
    }

    @AfterAll
    static void cleanUp() throws IOException {
        Files.deleteIfExists(TEST_DB);
        Files.deleteIfExists(Path.of(TEST_DB + "-wal"));
        Files.deleteIfExists(Path.of(TEST_DB + "-shm"));
    }

    @Autowired
    private MockMvc mockMvc;

    @LocalServerPort
    private int port;

    @Autowired
    private BigActivityController bigActivityController;

    @Autowired
    private ProductActivityController productActivityController;

    @Autowired
    private StatsController statsController;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM product_activity");
        jdbcTemplate.update("DELETE FROM big_activity");
    }

    @Test
    void supportsFullCrudForBothTablesAndCascadeDelete() throws Exception {
        MvcResult bigResult = mockMvc.perform(post("/api/big-activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "测试大活动",
                                  "actType": 1,
                                  "startTime": "2026-10-01T00:00",
                                  "endTime": "2026-10-31T23:59",
                                  "audienceScope": "ALL",
                                  "budget": 1000.50,
                                  "collectionDiscountJson": "{\\"threshold\\":100}",
                                  "operator": "alice"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("测试大活动"))
                .andExpect(jsonPath("$.actType").value(1))
                .andExpect(jsonPath("$.createdBy").value("alice"))
                .andExpect(jsonPath("$.productCount").value(0))
                .andReturn();

        long bigId = readId(bigResult);

        MvcResult productResult = mockMvc.perform(post("/api/product-activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actId": %d,
                                  "poiId": "9007199254740993",
                                  "skuId": "9001",
                                  "upc": "6901234567890",
                                  "productDiscountJson": "{\\"price\\":9.9}",
                                  "operator": "alice"
                                }
                                """.formatted(bigId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actName").value("测试大活动"))
                .andExpect(jsonPath("$.actType").value(1))
                .andExpect(jsonPath("$.poiId").value("9007199254740993"))
                .andExpect(jsonPath("$.skuId").value("9001"))
                .andReturn();

        long productId = readId(productResult);

        mockMvc.perform(put("/api/product-activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": %d,
                                  "actId": %d,
                                  "actType": 2,
                                  "poiId": "1002",
                                  "upc": "6901234567890",
                                  "productDiscountJson": "{}",
                                  "operator": "carol"
                                }
                                """.formatted(productId, bigId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actType").value(2))
                .andExpect(jsonPath("$.poiId").value("1002"))
                .andExpect(jsonPath("$.skuId").doesNotExist())
                .andExpect(jsonPath("$.updatedBy").value("carol"));

        mockMvc.perform(get("/api/big-activities")
                        .param("keyword", "测试")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].productCount").value(1));

        mockMvc.perform(get("/api/product-activities").param("actId", String.valueOf(bigId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].poiId").value("1002"))
                .andExpect(jsonPath("$.items[0].skuId").doesNotExist());

        mockMvc.perform(put("/api/big-activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": %d,
                                  "name": "测试大活动（更新）",
                                  "actType": 1,
                                  "startTime": "2026-10-01T00:00",
                                  "endTime": "2026-11-01T23:59",
                                  "audienceScope": "NEW",
                                  "budget": 2000,
                                  "collectionDiscountJson": "{}",
                                  "operator": "bob"
                                }
                                """.formatted(bigId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("测试大活动（更新）"))
                .andExpect(jsonPath("$.audienceScope").value("NEW"))
                .andExpect(jsonPath("$.createdBy").value("alice"))
                .andExpect(jsonPath("$.updatedBy").value("bob"));

        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bigActivityCount").value(1))
                .andExpect(jsonPath("$.productActivityCount").value(1))
                .andExpect(jsonPath("$.totalBudget").value(2000));

        mockMvc.perform(delete("/api/big-activities/{id}", bigId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/product-activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get("/api/big-activities/{id}", bigId))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidPayloads() throws Exception {
        mockMvc.perform(post("/api/big-activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "actType": 9,
                                  "startTime": "2026-10-01T00:00",
                                  "endTime": "2026-10-02T00:00",
                                  "audienceScope": "ALL",
                                  "budget": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.actType").exists())
                .andExpect(jsonPath("$.fieldErrors.budget").exists());

        mockMvc.perform(post("/api/big-activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "时间错误",
                                  "actType": 1,
                                  "startTime": "2026-10-05T00:00",
                                  "endTime": "2026-10-01T00:00",
                                  "audienceScope": "ALL",
                                  "budget": 100
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("结束时间必须晚于开始时间"));

        mockMvc.perform(post("/api/big-activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "JSON 错误",
                                  "actType": 1,
                                  "startTime": "2026-10-01T00:00",
                                  "endTime": "2026-10-02T00:00",
                                  "audienceScope": "ALL",
                                  "budget": 100,
                                  "collectionDiscountJson": "not-json"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不是合法的 JSON")));
    }

    @Test
    void productActivityRequiresExistingParent() throws Exception {
        mockMvc.perform(post("/api/product-activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actId": 99999,
                                  "actType": 1,
                                  "poiId": "1001"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("所属大活动不存在")));
    }

    @Test
    void controllerMethodsAcceptAtMostOneParameter() {
        List.of(bigActivityController, productActivityController, statsController).forEach((controller) ->
                Arrays.stream(controller.getClass().getDeclaredMethods())
                        .filter((method) -> !method.isSynthetic())
                        .forEach((method) -> assertThat(method.getParameterCount())
                                .as("%s#%s", controller.getClass().getSimpleName(), method.getName())
                                .isLessThanOrEqualTo(1)));
    }

    @Test
    void exposesMcpToolsAndSupportsActivityCrud() throws Exception {
        Map<String, Object> initialize = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "initialize",
                "params", Map.of(
                        "protocolVersion", "2025-06-18",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "junit", "version", "1.0")));

        ResponseEntity<String> initResponse = mcpPost(initialize, null);
        assertThat(initResponse.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode init = readMcpBody(initResponse.getBody());
        assertThat(init.at("/result/serverInfo/name").asText()).isEqualTo("act-mcp");

        String sessionId = initResponse.getHeaders().getFirst("Mcp-Session-Id");
        assertThat(sessionId).isNotBlank();
        mcpPost(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"), sessionId);

        JsonNode toolsResponse = callMcp(sessionId, Map.of(
                "jsonrpc", "2.0", "id", 2, "method", "tools/list"));
        JsonNode tools = toolsResponse.at("/result/tools");
        assertThat(tools.size()).isEqualTo(11);
        List<String> toolNames = StreamSupport.stream(tools.spliterator(), false)
                .map(node -> node.get("name").asText())
                .toList();
        assertThat(toolNames).contains(
                "list_big_activities", "create_big_activity", "update_big_activity", "delete_big_activity",
                "list_product_activities", "create_product_activity", "delete_product_activity",
                "get_activity_stats");

        JsonNode createdResponse = callMcp(sessionId, Map.of(
                "jsonrpc", "2.0", "id", 3, "method", "tools/call",
                "params", Map.of(
                        "name", "create_big_activity",
                        "arguments", Map.of(
                                "name", "MCP 测试活动",
                                "actType", 2,
                                "startTime", "2026-11-01T00:00",
                                "endTime", "2026-11-10T23:59",
                                "audienceScope", "ALL",
                                "budget", 500,
                                "collectionDiscountJson", "{\"source\":\"mcp\"}",
                                "operator", "junit-agent"))));

        assertThat(createdResponse.at("/result/isError").asBoolean()).isFalse();
        JsonNode created = objectMapper.readTree(createdResponse.at("/result/content/0/text").asText());
        long bigId = created.get("id").asLong();
        assertThat(created.get("name").asText()).isEqualTo("MCP 测试活动");
        assertThat(created.get("createdBy").asText()).isEqualTo("junit-agent");

        JsonNode productResponse = callMcp(sessionId, Map.of(
                "jsonrpc", "2.0", "id", 4, "method", "tools/call",
                "params", Map.of(
                        "name", "create_product_activity",
                        "arguments", Map.of(
                                "actId", bigId,
                                "poiId", "9007199254740993",
                                "skuId", "9001",
                                "productDiscountJson", "{\"price\":9.9}"))));
        assertThat(productResponse.at("/result/isError").asBoolean()).isFalse();
        JsonNode product = objectMapper.readTree(productResponse.at("/result/content/0/text").asText());
        assertThat(product.get("poiId").asText()).isEqualTo("9007199254740993");

        JsonNode deleteResponse = callMcp(sessionId, Map.of(
                "jsonrpc", "2.0", "id", 5, "method", "tools/call",
                "params", Map.of("name", "delete_big_activity", "arguments", Map.of("id", bigId))));
        assertThat(deleteResponse.at("/result/isError").asBoolean()).isFalse();

        JsonNode productsAfterDelete = callMcp(sessionId, Map.of(
                "jsonrpc", "2.0", "id", 6, "method", "tools/call",
                "params", Map.of(
                        "name", "list_product_activities",
                        "arguments", Map.of("actId", bigId))));
        JsonNode productPage = objectMapper.readTree(
                productsAfterDelete.at("/result/content/0/text").asText());
        assertThat(productPage.get("total").asLong()).isZero();
    }

    @Test
    void exposesSpringMvcMappingsEndpoint() {
        ResponseEntity<String> response = restClient().get()
                .uri("/actuator/mappings")
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .contains("/api/big-activities")
                .contains("/api/product-activities")
                .contains("/api/stats");
    }

    private RestClient restClient() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    private ResponseEntity<String> mcpPost(Map<String, Object> payload, String sessionId) {
        RestClient.RequestBodySpec request = restClient().post()
                .uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .header("MCP-Protocol-Version", "2025-06-18");
        if (sessionId != null) {
            request.header("Mcp-Session-Id", sessionId);
        }
        ResponseEntity<byte[]> raw = request.body(objectMapper.writeValueAsString(payload))
                .retrieve()
                .toEntity(byte[].class);
        String body = raw.getBody() == null ? "" : new String(raw.getBody(), StandardCharsets.UTF_8);
        return new ResponseEntity<>(body, raw.getHeaders(), raw.getStatusCode());
    }

    private JsonNode callMcp(String sessionId, Map<String, Object> payload) throws IOException {
        ResponseEntity<String> response = mcpPost(payload, sessionId);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return readMcpBody(response.getBody());
    }

    private JsonNode readMcpBody(String body) throws IOException {
        assertThat(body).isNotBlank();
        if (body.contains("data:")) {
            String data = Arrays.stream(body.split("\\n"))
                    .filter(line -> line.startsWith("data:"))
                    .reduce((first, second) -> second)
                    .map(line -> line.substring(5).trim())
                    .orElse(body);
            return objectMapper.readTree(data);
        }
        return objectMapper.readTree(body);
    }

    private long readId(MvcResult result) throws IOException {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(node.get("id").isNumber()).isTrue();
        return node.get("id").asLong();
    }
}
