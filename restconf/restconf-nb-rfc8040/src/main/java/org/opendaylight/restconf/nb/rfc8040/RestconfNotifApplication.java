/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.logging.RestconfLoggingConfiguration;
import org.opendaylight.restconf.nb.rfc8040.services.wrapper.ServicesNotifWrapper;

/**
 * Restconf Application extends {@link AbstractRestconfApplication}. Is used for sending SSE.
 */
@Singleton
public class RestconfNotifApplication extends AbstractRestconfApplication<ServicesNotifWrapper> {
    @Inject
    public RestconfNotifApplication(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointServiceHandler mountPointServiceHandler,
            final ServicesNotifWrapper servicesNotifWrapper,
            final RestconfLoggingConfiguration restconfLoggingConfiguration) {
        super(schemaContextHandler, mountPointServiceHandler, servicesNotifWrapper, restconfLoggingConfiguration);
    }
}
