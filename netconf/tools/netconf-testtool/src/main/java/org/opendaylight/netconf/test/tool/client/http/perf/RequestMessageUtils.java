/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
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

public class RequestMessageUtils {

    private static final String PEER_KEY = "{PEERID}";
    private static final String INT_LEAF_KEY = "{INTLEAF}";

    private static final String PHYS_ADDR_PLACEHOLDER = "{PHYS_ADDR}";

    private static final String HOST_KEY = "{HOST}";
    private static final String PORT_KEY = "{PORT}";
    private static final String DEVICE_PORT_KEY = "{DEVICE_PORT}";


    private static final String DEST = "http://{HOST}:{PORT}";

    private static long macStart = 0xAABBCCDD0000L;

    private RequestMessageUtils(){
    }

    public static String prepareMessage(final int idi, final int idj, final String editContentString,
                                        final int devicePort) {
        StringBuilder messageBuilder = new StringBuilder(editContentString);
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

    public static RestPerfClient.RequestData formPayload(Parameters parameters, String editContentString,
                                                         int threadId, int requests) {
        final int devicePort = parameters.sameDevice
                ? parameters.devicePortRangeStart : parameters.devicePortRangeStart + threadId;
        final StringBuilder destBuilder = new StringBuilder(DEST);
        destBuilder.replace(
                destBuilder.indexOf(HOST_KEY),
                destBuilder.indexOf(HOST_KEY) + HOST_KEY.length(),
                parameters.ip)
                .replace(
                        destBuilder.indexOf(PORT_KEY),
                        destBuilder.indexOf(PORT_KEY) + PORT_KEY.length(),
                        parameters.port + "");
        final StringBuilder suffixBuilder = new StringBuilder(parameters.destination);
        if (suffixBuilder.indexOf(DEVICE_PORT_KEY) != -1) {
            suffixBuilder.replace(
                    suffixBuilder.indexOf(DEVICE_PORT_KEY),
                    suffixBuilder.indexOf(DEVICE_PORT_KEY) + DEVICE_PORT_KEY.length(),
                    devicePort + "");
        }
        destBuilder.append(suffixBuilder);


        return new RestPerfClient.RequestData(destBuilder.toString(), editContentString,
                threadId, devicePort, requests);
    }

    public static Request formRequest(AsyncHttpClient asyncHttpClient, String url, Parameters params, String msg) {
        AsyncHttpClient.BoundRequestBuilder requestBuilder = asyncHttpClient.preparePost(url)
                .addHeader("content-type", "application/json")
                .addHeader("Accept", "application/xml")
                .setBody(msg)
                .setRequestTimeout(Integer.MAX_VALUE);

        if (params.auth != null) {
            requestBuilder.setRealm(new Realm.RealmBuilder()
                    .setScheme(Realm.AuthScheme.BASIC)
                    .setPrincipal(params.auth.get(0))
                    .setPassword(params.auth.get(1))
                    .setUsePreemptiveAuth(true)
                    .build());
        }
        return requestBuilder.build();
    }


}
