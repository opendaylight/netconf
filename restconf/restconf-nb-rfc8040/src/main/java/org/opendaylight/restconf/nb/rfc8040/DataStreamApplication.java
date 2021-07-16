/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfDataStreamService;

/**
 * Restconf Application extends {@link AbstractRestconfApplication}. Is used for sending SSE.
 */
@Singleton
public class DataStreamApplication extends AbstractRestconfApplication {
    @Inject
    public DataStreamApplication(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointService mountPointService, final RestconfDataStreamService dataStreamService) {
        super(schemaContextHandler, mountPointService, List.of(dataStreamService));
    }
}
