/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
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
import org.opendaylight.restconf.nb.rfc8040.services.wrapper.ServicesWrapper;

@Singleton
public class RestconfApplication extends AbstractRestconfApplication<ServicesWrapper> {
    @Inject
    public RestconfApplication(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointServiceHandler mountPointServiceHandler, final ServicesWrapper servicesNotifWrapper,
            final RestconfLoggingConfiguration restconfLoggingConfiguration) {
        super(schemaContextHandler, mountPointServiceHandler, servicesNotifWrapper, restconfLoggingConfiguration);
    }
}
