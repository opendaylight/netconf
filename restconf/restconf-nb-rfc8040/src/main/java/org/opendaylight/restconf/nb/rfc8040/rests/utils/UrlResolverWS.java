/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import java.net.URI;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;

public class UrlResolverWS extends UrlResolver {

    @Override
    public URI prepareUriByStreamName(final UriInfo uriInfo, final String streamName) {
        final String scheme = uriInfo.getAbsolutePath().getScheme();
        final UriBuilder uriBuilder = uriInfo.getBaseUriBuilder();
        switch (scheme) {
            case "https":
                // Secured HTTP goes to Secured WebSockets
                uriBuilder.scheme("wss");
                break;
            case "http":
            default:
                // Unsecured HTTP and others go to unsecured WebSockets
                uriBuilder.scheme("ws");
        }
        return uriBuilder.replacePath(RestconfConstants.BASE_URI_PATTERN + '/' + streamName).build();
    }

}
