/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;


import static org.opendaylight.restconf.server.spi.AbstractRestconfStreamRegistry.streamEntry;

import java.net.URI;
import javax.inject.Inject;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.mdsal.spi.NotificationSource;
import org.opendaylight.restconf.notifications.mdsal.RestconfSubscriptionsStreamRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component
public final class NetconfStream {
    private final DOMDataBroker dataBroker;
    private final URI restconfURI = URI.create("subscriptionStream");


    @Inject
    @Activate
    public NetconfStream(@Reference final DOMDataBroker dataBroker) {
        this.dataBroker = dataBroker;

        startStream();

    }

    private void startStream() {
        final var restconfSubscriptionsStreamRegistry = new RestconfSubscriptionsStreamRegistry(
            uri -> uri.resolve(restconfURI), dataBroker);

        restconfSubscriptionsStreamRegistry.addStream(streamEntry("name", "description",
            "base", NotificationSource.ENCODINGS.keySet()));
    }
}
