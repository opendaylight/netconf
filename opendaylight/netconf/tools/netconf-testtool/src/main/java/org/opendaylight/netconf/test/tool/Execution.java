package org.opendaylight.netconf.test.tool;

import com.ning.http.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Created by miroslav on 14.2.2016.
 */
public class Execution implements Callable<Void> {

    private final ArrayList<Request> payloads;
    private final AsyncHttpClient asyncHttpClient;
    private static final Logger LOG = LoggerFactory.getLogger(Execution.class);

    static final class DestToPayload {

        private final String destination;
        private final String payload;

        public DestToPayload(String destination, String payload) {
            this.destination = destination;
            this.payload = payload;
        }

        public String getDestination() {
            return destination;
        }

        public String getPayload() {
            return payload;
        }
    }

    public Execution(MainParameters params, ArrayList<DestToPayload> payloads)
    {
        this.asyncHttpClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder()
                .setConnectTimeout(Integer.MAX_VALUE)
                .setRequestTimeout(Integer.MAX_VALUE)
                .setAllowPoolingConnections(true)
                .build());

        this.payloads = new ArrayList<>();
        for (DestToPayload payload : payloads) {
            AsyncHttpClient.BoundRequestBuilder requestBuilder = asyncHttpClient.preparePut(payload.getDestination())
                    .addHeader("Content-Type", "application/xml")
                    .addHeader("Accept", "application/xml")
                    .setBody(payload.getPayload())
                    .setRequestTimeout(Integer.MAX_VALUE);

            if(params.auth != null) {
                requestBuilder.setRealm(new Realm.RealmBuilder()
                        .setScheme(Realm.AuthScheme.BASIC)
                        .setPrincipal(params.auth.get(0))
                        .setPassword(params.auth.get(1))
                        .setUsePreemptiveAuth(true)
                        .build());
            }
            this.payloads.add(requestBuilder.build());
        }
    }

    private void invoke()
    {
        LOG.info("Begin sending sync requests");
        for (Request request : payloads) {
            try {
                Response response = asyncHttpClient.executeRequest(request).get();
                if (response.getStatusCode() != 200 && response.getStatusCode() != 204) {
                    LOG.warn("Status code: {}", response.getStatusCode());
                    LOG.warn("url: {}", request.getUrl());
                    LOG.warn(response.getResponseBody());
                }
            } catch (InterruptedException | ExecutionException | IOException e) {
                LOG.warn(e.toString());
            }
        }
        LOG.info("End sending sync requests");
    }




    @Override
    public Void call() throws Exception {
        this.invoke();
        return null;
    }
}
