package sg.edu.nus.iss.c2csectrade.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.service.SearchService;
import sg.edu.nus.iss.c2csectrade.service.search.ProductSearchDocument;
import sg.edu.nus.iss.c2csectrade.service.search.ProductSearchIndex;
import sg.edu.nus.iss.c2csectrade.service.search.ProductSearchQuery;
import sg.edu.nus.iss.c2csectrade.service.search.ProductSearchCriteria;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real HTTP controller, production MyBatis XML, MySQL and the Compose ES version. */
@ExtendWith(OutputCaptureExtension.class)
class SearchParityIT extends MySqlIntegrationTest {
    static final GenericContainer<?> ES = new GenericContainer<>(
            "docker.elastic.co/elasticsearch/elasticsearch:8.10.4")
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
            .withExposedPorts(9200)
            .waitingFor(new HttpWaitStrategy().forPort(9200)
                    .forPath("/_cluster/health?wait_for_status=yellow&timeout=1s")
                    .forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)));
    static {
        ES.start();
    }

    @DynamicPropertySource
    static void searchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", () -> "http://" + ES.getHost() + ":" + ES.getMappedPort(9200));
        registry.add("spring.elasticsearch.connection-timeout", () -> "500ms");
        registry.add("spring.elasticsearch.socket-timeout", () -> "500ms");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ProductMapper products;
    @Autowired ProductSearchIndex index;
    @Autowired ProductSearchQuery query;
    @Autowired ElasticsearchOperations operations;
    @Autowired SearchService service;

    // The assertions read the per-search path line, which is DEBUG.
    private final Logger searchLog = (Logger) LoggerFactory.getLogger(SearchService.class);
    private Level previousLevel;

    @BeforeEach
    void fixtures() {
        previousLevel = searchLog.getLevel();
        searchLog.setLevel(Level.DEBUG);
        insert(9001, "Parity campus phone", "Budget device", 100, 9, "Electronics", 1, 0, 1, "Kent Ridge");
        insert(9002, "Parity campus phone case", "Protects a phone", 20, 8, "Electronics", 2, 0, 1, "West Coast");
        insert(9003, "Parity textbook", "A campus phone comparison", 40, 9, "Books", 1, 0, 1, "Kent Ridge");
        insert(9004, "Parity campus table", "Wooden desk", 60, 7, "Furniture", 1, 0, 1, "Kent Ridge");
        insert(9005, "Parity C++ Primer", "Compiler reference", 30, 8, "Books", 1, 0, 1, "Kent Ridge");
        insert(9006, "Parity 学习电脑", "便携笔记本", 90, 9, "Electronics", 1, 0, 1, "Kent Ridge");
        insert(9007, "Parity campus phone reserved", "Held", 10, 9, "Electronics", 1, 1, 1, "Kent Ridge");
        insert(9008, "Parity campus phone delisted", "Hidden", 10, 9, "Electronics", 1, 0, 0, "Kent Ridge");
        insert(9009, "Parity campus phone sold", "No stock", 10, 9, "Electronics", 0, 0, 1, "Kent Ridge");
        index.rebuild(products.selectAllActive());
        operations.indexOps(ProductSearchDocument.class).refresh();
    }

    @AfterEach
    void restoreLogLevel() {
        searchLog.setLevel(previousLevel);
    }

    @Test
    void theSameQuerySetSurvivesAnActualEsOutageAndRecoversWithoutRestart(CapturedOutput output) throws Exception {
        List<DemoQuery> queries = List.of(
                new DemoQuery("campus phone", null, null, null, "Parity", null, Set.of(9001L, 9002L, 9003L)),
                new DemoQuery("  CAMPUS PHONE  ", null, null, null, "Parity", null, Set.of(9001L, 9002L, 9003L)),
                new DemoQuery("amp", null, null, null, "Parity", null, Set.of(9001L, 9002L, 9003L, 9004L)),
                new DemoQuery("C++", null, null, null, "Parity", null, Set.of(9005L)),
                new DemoQuery("学习", null, null, null, "Parity", null, Set.of(9006L)),
                new DemoQuery("Books", null, null, null, "Parity", null, Set.of(9003L, 9005L)),
                new DemoQuery("campus phone", "20", "40", null, "Parity", null, Set.of(9002L, 9003L)),
                new DemoQuery("campus phone", null, null, "9", "Parity Kent", null, Set.of(9001L, 9003L)),
                new DemoQuery("campus phone", null, null, null, "Parity", "Electronics,Books", Set.of(9001L, 9002L, 9003L)),
                new DemoQuery("campus phone", null, null, null, "Parity", "Books", Set.of(9003L)),
                new DemoQuery("not-present", null, null, null, "Parity", null, Set.of()),
                new DemoQuery("  ", null, null, null, "Parity", null, Set.of(9001L, 9002L, 9003L, 9004L, 9005L, 9006L)));

        // Prove ES is actually queried and not silently bypassed by a broken fixture.
        assertTrue(query.search(new ProductSearchCriteria("phone", null, null, null, null, null)).contains(9001L));
        SearchService originalService = service;
        for (DemoQuery demo : queries) assertResults(demo);
        assertTrue(output.getOut().contains("path=elasticsearch_ranked"));
        assertTrue(output.getOut().contains("path=sql_browse"));

        ES.getDockerClient().pauseContainerCmd(ES.getContainerId()).exec();
        try {
            for (DemoQuery demo : queries) assertResults(demo);
            assertTrue(output.getOut().contains("path=sql_fallback"));
        } finally {
            ES.getDockerClient().unpauseContainerCmd(ES.getContainerId()).exec();
        }

        assertSame(originalService, service);
        // The same Spring/ES client instances must resume real ES ranking.
        int recoveredLogStart = output.getOut().length();
        for (DemoQuery demo : queries) assertResults(demo);
        String recoveredLogs = output.getOut().substring(recoveredLogStart);
        assertTrue(recoveredLogs.contains("path=elasticsearch_ranked"));
        assertFalse(recoveredLogs.contains("path=sql_fallback"));
    }

    @Test
    void staleAndMissingDocumentsCannotOverrideCurrentSqlFilters(CapturedOutput output) throws Exception {
        // Simulate database commits that the best-effort indexer missed.
        jdbc.update("UPDATE pms_product SET price=500 WHERE id=9001");
        jdbc.update("UPDATE pms_product SET reserved_stock=stock WHERE id=9002");
        jdbc.update("UPDATE pms_product SET status=0 WHERE id=9003");
        insert(9010, "Parity campus phone new", "Written during outage", 25, 9, "Electronics", 1, 0, 1, "Kent Ridge");
        // Keep multiple SQL matches so this regression still exercises ES
        // reconciliation instead of the zero/one-match ranking shortcut.
        insert(9011, "Parity campus phone newer", "Also not indexed", 30, 9, "Electronics", 1, 0, 1, "Kent Ridge");
        assertResults(new DemoQuery("campus phone", null, "50", null, "Parity", null, Set.of(9010L, 9011L)));
        assertTrue(output.getOut().contains("sql_only=2"));

        // Even total index loss/recreation must not empty the catalogue.
        var indexOps = operations.indexOps(ProductSearchDocument.class);
        assertTrue(indexOps.delete());
        assertTrue(indexOps.createWithMapping());
        assertResults(new DemoQuery("campus phone", null, "50", null, "Parity", null, Set.of(9010L, 9011L)));
    }

    private void assertResults(DemoQuery demo) throws Exception {
        var request = get("/api/products").param("keyword", demo.keyword()).param("location", demo.location());
        if (demo.min() != null) request.param("minPrice", demo.min());
        if (demo.max() != null) request.param("maxPrice", demo.max());
        if (demo.condition() != null) request.param("conditionLevel", demo.condition());
        if (demo.categories() != null) request.param("categories", demo.categories());
        String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        for (JsonNode row : json.readTree(body)) {
            ids.add(row.get("id").asLong());
            assertTrue(row.get("stock").asInt() > 0);
            if (!demo.keyword().isBlank()) assertTrue(row.hasNonNull("highlightedName"));
        }
        assertEquals(ids.size(), new HashSet<>(ids).size(), "Duplicate listings: " + demo);
        assertEquals(demo.expected(), new HashSet<>(ids), demo.toString());
    }

    private void insert(long id, String name, String description, int price, int condition,
                        String category, int stock, int reserved, int status, String location) {
        jdbc.update("""
                INSERT INTO pms_product (id,user_id,name,description,price,condition_level,
                    category,stock,reserved_stock,status,location)
                VALUES (?,101,?,?,?,?,?,?,?,?,?)
                """, id, name, description, price, condition, category, stock, reserved, status, "Parity " + location);
    }

    private record DemoQuery(String keyword, String min, String max, String condition,
                             String location, String categories, Set<Long> expected) {}
}
