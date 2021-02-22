/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.client.http.perf;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Realm;
import com.ning.http.client.Request;
import org.opendaylight.netconf.test.tool.TestToolUtils;

final class RequestMessageUtils {
    private static final String PEER_KEY = "{PEERID}";
    private static final String INT_LEAF_KEY = "{INTLEAF}";
    private static final String PHYS_ADDR_PLACEHOLDER = "{PHYS_ADDR}";
    private static final String HOST_KEY = "{HOST}";
    private static final String PORT_KEY = "{PORT}";
    private static final String DEVICE_PORT_KEY = "{DEVICE_PORT}";
    private static final String DEST = "http://{HOST}:{PORT}";

    private static long macStart = 0xAABBCCDD0000L;

    private RequestMessageUtils() {
        // Hidden on purpose
    }

    static String prepareMessage(final int idi, final int idj, final String editContentString, final int devicePort) {
        final StringBuilder messageBuilder = new StringBuilder(editContentString);
        if (editContentString.contains(PEER_KEY)) {
            messageBuilder.replace(
                    messageBuilder.indexOf(PEER_KEY),
                    messageBuilder.indexOf(PEER_KEY) + PEER_KEY.length(),
                    Integer.toString(idi))
                    .replace(
                            messageBuilder.indexOf(INT_LEAF_KEY),
                            messageBuilder.indexOf(INT_LEAF_KEY) + INT_LEAF_KEY.length(),
                            Integer.toString(idj));
        }

        if (messageBuilder.indexOf(DEVICE_PORT_KEY) != -1) {
            messageBuilder.replace(
                    messageBuilder.indexOf(DEVICE_PORT_KEY),
                    messageBuilder.indexOf(DEVICE_PORT_KEY) + DEVICE_PORT_KEY.length(),
                    Integer.toString(devicePort));
        }

        int idx = messageBuilder.indexOf(PHYS_ADDR_PLACEHOLDER);

        while (idx != -1) {
            messageBuilder.replace(idx, idx + PHYS_ADDR_PLACEHOLDER.length(), TestToolUtils.getMac(macStart++));
            idx = messageBuilder.indexOf(PHYS_ADDR_PLACEHOLDER);
        }

        return messageBuilder.toString();
    }

    static RestPerfClient.RequestData formPayload(final Parameters parameters, final String editContentString,
            final int threadId, final int requests) {
        final int devicePort = parameters.isSameDevice()
                ? parameters.getDevicePortRangeStart() : parameters.getDevicePortRangeStart() + threadId;
        final StringBuilder destBuilder = new StringBuilder(DEST);
        destBuilder.replace(
                destBuilder.indexOf(HOST_KEY),
                destBuilder.indexOf(HOST_KEY) + HOST_KEY.length(),
                parameters.getIp())
                .replace(
                        destBuilder.indexOf(PORT_KEY),
                        destBuilder.indexOf(PORT_KEY) + PORT_KEY.length(),
                        Integer.toString(parameters.getPort()));
        final StringBuilder suffixBuilder = new StringBuilder(parameters.getDestination());
        if (suffixBuilder.indexOf(DEVICE_PORT_KEY) != -1) {
            suffixBuilder.replace(
                    suffixBuilder.indexOf(DEVICE_PORT_KEY),
                    suffixBuilder.indexOf(DEVICE_PORT_KEY) + DEVICE_PORT_KEY.length(),
                    Integer.toString(devicePort));
        }
        destBuilder.append(suffixBuilder);


        return new RestPerfClient.RequestData(destBuilder.toString(), editContentString,
                threadId, devicePort, requests);
    }

    static Request formRequest(final AsyncHttpClient asyncHttpClient, final String url,
            final Parameters params, final String msg) {
        final AsyncHttpClient.BoundRequestBuilder requestBuilder = asyncHttpClient.preparePost(url)
                .addHeader("content-type", "application/json")
                .addHeader("Accept", "application/xml")
                .setBody(msg)
                .setRequestTimeout(Integer.MAX_VALUE);

        if (params.getAuth() != null) {
            requestBuilder.setRealm(new Realm.RealmBuilder()
                    .setScheme(Realm.AuthScheme.BASIC)
                    .setPrincipal(params.getAuth().get(0))
                    .setPassword(params.getAuth().get(1))
                    .setUsePreemptiveAuth(true)
                    .build());
        }
        return requestBuilder.build();
    }
}
