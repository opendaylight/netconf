package org.opendaylight.restconf.it.subscription;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.awaitility.core.ConditionTimeoutException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.it.server.TestEventStreamListener;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class NotificationSubscriptionHttp2Test extends AbstractNotificationSubscriptionTest {
    private static final String TERMINATED_NOTIFICATION = """
        {
          "ietf-restconf:notification" : {
            "ietf-subscribed-notifications:subscription-terminated" : {
              "id" : 2147483648,
              "reason" : "ietf-subscribed-notifications:no-such-subscription"
            }
          }
        }""";

    protected HTTPClient http2Client;
    private static TestEventStreamListener eventListener;

    @BeforeEach
    public void beforeEach() throws Exception {
        super.beforeEach();
        http2Client = startStreamClient();

        // Establish subscription
        final var response = invokeRequestKeepClient(http2Client, HttpMethod.POST, ESTABLISH_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            """
                {
                  "input": {
                    "stream": "NETCONF",
                    "encoding": "encode-json"
                  }
                }""", MediaTypes.APPLICATION_YANG_DATA_JSON);
        assertEquals(HttpResponseStatus.OK, response.status());

        // Extract subscription ID from response
        final var jsonContent = new JSONObject(response.content().toString(StandardCharsets.UTF_8),
            JSON_PARSER_CONFIGURATION);
        final var subscriptionId = jsonContent.getJSONObject("ietf-subscribed-notifications:output").getLong("id");

        // Start listening on notifications
        eventListener = startSubscriptionStream(String.valueOf(subscriptionId));
    }

    @AfterEach
    @Override
    void afterEach() throws Exception {
        if (http2Client != null) {
            http2Client.shutdown().get(2, TimeUnit.SECONDS);
        }
        super.afterEach();
    }

    @Test
    void testListenDeleteNotification() throws Exception {
        // Delete the subscription
        final var response = invokeRequestKeepClient(http2Client, HttpMethod.POST,
            "/restconf/operations/ietf-subscribed-notifications:delete-subscription",
            MediaTypes.APPLICATION_YANG_DATA_JSON,
            """
                {
                  "input": {
                    "id": 2147483648
                  }
                }""", MediaTypes.APPLICATION_YANG_DATA_JSON);

        assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
        JSONAssert.assertEquals(TERMINATED_NOTIFICATION, eventListener.readNext(), JSONCompareMode.LENIENT);

        // Assert exception when try to listen to subscription after it should be terminated
        assertThrows(ConditionTimeoutException.class, () -> startSubscriptionStream("2147483648"));
        // Verify notification listening ended
        await().atMost(Duration.ofSeconds(5)).until(eventListener::ended);
        assertTrue(eventListener.ended());
    }
}
