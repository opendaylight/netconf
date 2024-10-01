/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.DataPostBody;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.JsonDataPostBody;
import org.opendaylight.restconf.server.api.XmlDataPostBody;

/**
 * A POST request to a child of /data resource.
 */
@NonNullByDefault
final class PendingDataPost extends AbstractPendingDataPost<DataPostResult, DataPostBody> {
    private final ApiPath apiPath;

    PendingDataPost(final EndpointInvariants invariants, final MessageEncoding encoding, final ApiPath apiPath) {
        super(invariants, encoding);
        this.apiPath = requireNonNull(apiPath);
    }

    @Override
    void execute(final NettyServerRequest<DataPostResult> request, final DataPostBody body) {
        server().dataPOST(request, apiPath, body);
    }

    @Override
    DataPostBody wrapBody(final InputStream body) {
        return switch (encoding) {
            case JSON -> new JsonDataPostBody(body);
            case XML -> new XmlDataPostBody(body);
        };
    }
}
