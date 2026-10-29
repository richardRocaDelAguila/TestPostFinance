package ch.example.orderservice;

import ch.example.orderservice.api.dto.CreateOrderRequest;
import ch.example.orderservice.domain.OrderStatus;
import ch.example.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test of the full flow: REST create -> persisted CREATED -> JMS event ->
 * consumer -> PROCESSED. Uses the embedded Artemis broker and H2.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderFlowIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    OrderRepository orderRepository;

    @Test
    void createdOrderIsEventuallyProcessed() {
        CreateOrderRequest request = new CreateOrderRequest("12345", new BigDecimal("99.99"));

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/orders", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        UUID id = UUID.fromString((String) response.getBody().get("id"));
        assertThat(response.getBody().get("status")).isEqualTo(OrderStatus.CREATED.name());

        // The consumer processes asynchronously; wait until it reaches PROCESSED.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(orderRepository.findById(id))
                        .get()
                        .extracting(o -> o.getStatus())
                        .isEqualTo(OrderStatus.PROCESSED));
    }
}

