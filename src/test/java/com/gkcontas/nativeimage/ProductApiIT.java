package com.gkcontas.nativeimage;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.nativeimage.api.ErrorResponse;
import com.gkcontas.nativeimage.api.ProductResponse;
import com.gkcontas.nativeimage.api.QuoteResponse;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class ProductApiIT extends IntegrationTestBase {

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE product RESTART IDENTITY");
    }

    private ResponseEntity<ProductResponse> create(String sku, String category, String price) {
        return http.postForEntity("/api/products",
                Map.of("sku", sku, "name", "Product " + sku, "category", category, "price", new BigDecimal(price)),
                ProductResponse.class);
    }

    @Test
    void createsAndReadsAProduct() {
        ResponseEntity<ProductResponse> created = create("SKU-1", "books", "19.90");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).hasToString("/api/products/" + created.getBody().id());
        ProductResponse read = http.getForObject("/api/products/" + created.getBody().id(), ProductResponse.class);
        assertThat(read.sku()).isEqualTo("SKU-1");
        assertThat(read.price()).isEqualByComparingTo("19.90");
        assertThat(read.createdAt()).isNotNull();
    }

    @Test
    void duplicateSkuIsAConflict() {
        create("SKU-1", "books", "10.00");

        ResponseEntity<ErrorResponse> again = http.postForEntity("/api/products",
                Map.of("sku", "SKU-1", "name", "x", "category", "books", "price", 5), ErrorResponse.class);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().details()).containsExactly("sku SKU-1 already exists");
    }

    @Test
    void invalidBodyListsEveryFieldInEnglish() {
        ResponseEntity<ErrorResponse> response = http.postForEntity("/api/products",
                Map.of("sku", "bad sku", "name", "", "category", "books", "price", 0), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().details()).containsExactly(
                "name: name is required",
                "price: price must be at least 0.01",
                "sku: sku must be 3 to 32 uppercase letters, digits or dashes");
    }

    @Test
    void malformedJsonIsABadRequest() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        ResponseEntity<ErrorResponse> response = http.postForEntity("/api/products",
                new org.springframework.http.HttpEntity<>("{\"sku\":", headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().details()).containsExactly("malformed JSON body");
    }

    @Test
    void missingProductIsNotFound() {
        ResponseEntity<ErrorResponse> response = http.getForEntity("/api/products/999", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().details()).containsExactly("product 999 not found");
    }

    @Test
    void listsACategoryInIdOrderUpToTheLimit() {
        create("SKU-1", "books", "1.00");
        create("SKU-2", "games", "1.00");
        create("SKU-3", "books", "1.00");
        create("SKU-4", "books", "1.00");

        ProductResponse[] page = http.getForObject("/api/products?category=books&limit=2", ProductResponse[].class);

        assertThat(page).extracting(ProductResponse::sku).containsExactly("SKU-1", "SKU-3");
    }

    @Test
    void limitOutOfRangeIsABadRequest() {
        ResponseEntity<ErrorResponse> response =
                http.getForEntity("/api/products?category=books&limit=500", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().details()).containsExactly("limit must be at most 100");
    }

    @Test
    void quoteUsesTheTaxTableResourceAndBankersRounding() {
        long id = create("SKU-1", "books", "0.25").getBody().id();

        // 0.25 * 0.18 = 0.045: a tie. HALF_EVEN keeps 0.04, HALF_UP would give 0.05.
        QuoteResponse quote = http.getForObject("/api/products/" + id + "/quote?region=sp", QuoteResponse.class);

        assertThat(quote.taxRate()).isEqualByComparingTo("0.18");
        assertThat(quote.tax()).isEqualByComparingTo("0.04");
        assertThat(quote.gross()).isEqualByComparingTo("0.29");
        assertThat(quote.rounding()).isEqualTo("HalfEvenRounding");
        assertThat(quote.tableVersion()).isEqualTo("2026-11");
    }

    @Test
    void unknownRegionIsABadRequest() {
        long id = create("SKU-1", "books", "10.00").getBody().id();

        ResponseEntity<ErrorResponse> response =
                http.getForEntity("/api/products/" + id + "/quote?region=XX", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().details()).containsExactly("unknown region XX");
    }

    @Test
    void seedGeneratesProductsInsidePostgres() {
        Map<?, ?> seeded = http.postForObject("/api/admin/seed?count=1000", null, Map.class);

        assertThat(seeded.get("inserted")).isEqualTo(1000);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT category) FROM product", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT min(price) > 0 FROM product", Boolean.class)).isTrue();
    }
}
