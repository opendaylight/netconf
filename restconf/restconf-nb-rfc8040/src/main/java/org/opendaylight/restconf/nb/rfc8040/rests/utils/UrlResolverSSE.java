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

public class UrlResolverSSE extends UrlResolver {

    @Override
    public URI prepareUriByStreamName(final UriInfo uriInfo, final String streamName) {
        final UriBuilder uriBuilder = uriInfo.getBaseUriBuilder();
        return uriBuilder.replacePath(RestconfConstants.BASE_URI_PATTERN + '/'
                + RestconfConstants.NOTIF + '/' + streamName).build();
    }

}
