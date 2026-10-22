package com.actmcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    @Autowired
    private TestRestTemplate restTemplate;

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
                        .header("X-Operator", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "测试大活动",
                                  "actType": 1,
                                  "startTime": "2026-10-01T00:00",
                                  "endTime": "2026-10-31T23:59",
                                  "audienceScope": "ALL",
                                  "budget": 1000.50,
                                  "collectionDiscountJson": "{\\"threshold\\":100}"
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
                        .header("X-Operator", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actId": %d,
                                  "poiId": "9007199254740993",
                                  "skuId": "9001",
                                  "upc": "6901234567890",
                                  "productDiscountJson": "{\\"price\\":9.9}"
                                }
                                """.formatted(bigId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actName").value("测试大活动"))
                .andExpect(jsonPath("$.actType").value(1))
                .andExpect(jsonPath("$.poiId").value("9007199254740993"))
                .andExpect(jsonPath("$.skuId").value("9001"))
                .andReturn();

        long productId = readId(productResult);

        mockMvc.perform(put("/api/product-activities/{id}", productId)
                        .header("X-Operator", "carol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actId": %d,
                                  "actType": 2,
                                  "poiId": "1002",
                                  "upc": "6901234567890",
                                  "productDiscountJson": "{}"
                                }
                                """.formatted(bigId)))
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

        mockMvc.perform(put("/api/big-activities/{id}", bigId)
                        .header("X-Operator", "bob")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "测试大活动（更新）",
                                  "actType": 1,
                                  "startTime": "2026-10-01T00:00",
                                  "endTime": "2026-11-01T23:59",
                                  "audienceScope": "NEW",
                                  "budget": 2000,
                                  "collectionDiscountJson": "{}"
                                }
                                """))
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
    void exposesSpringMvcMappingsEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/mappings", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .contains("/api/big-activities")
                .contains("/api/product-activities")
                .contains("/api/stats");
    }

    private long readId(MvcResult result) throws IOException {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(node.get("id").isNumber()).isTrue();
        return node.get("id").asLong();
    }
}
