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
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfDataStreamServiceImpl;

/**
 * Restconf Application extends {@link AbstractRestconfApplication}. Is used for sending SSE.
 */
@Singleton
public class DataStreamApplication extends AbstractRestconfApplication {
    @Inject
    public DataStreamApplication(final DatabindProvider databindProvider, final DOMMountPointService mountPointService,
            final RestconfDataStreamServiceImpl dataStreamService) {
        super(databindProvider, mountPointService, List.of(dataStreamService));
    }
}
