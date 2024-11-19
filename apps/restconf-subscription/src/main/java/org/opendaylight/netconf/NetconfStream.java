/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;


import java.net.URI;
import javax.inject.Inject;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.notifications.mdsal.RestconfSubscriptionsStreamRegistry;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component
public final class NetconfStream {
    private final DOMDataBroker dataBroker;
    private final URI restconfURI = URI.create("subscriptionStream");
    private final DOMSchemaService schemaService;
    private final DOMNotificationService notificationService;

    @Inject
    @Activate
    public NetconfStream(@Reference final DOMDataBroker dataBroker, @Reference DOMSchemaService schemaService,
        @Reference DOMNotificationService notificationService) {
        this.dataBroker = dataBroker;
        this.schemaService = schemaService;
        this.notificationService = notificationService;

        startStream();
    }


    public void startStream() {
        final var restconfSubscriptionsStreamRegistry = new RestconfSubscriptionsStreamRegistry(
            uri -> uri.resolve(restconfURI), dataBroker);

        restconfSubscriptionsStreamRegistry.createStream(null, restconfURI,
            new DefaultNotificationSource(schemaService, notificationService, null), "stream description");

    }
}
