/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal.streams.notif;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import java.net.URI;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.server.api.OperationsPostResult;
import org.opendaylight.restconf.server.spi.DatabindProvider;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateNotificationStream;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateNotificationStreamInput;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.stmt.NotificationEffectiveStatement;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * RESTCONF implementation of {@link CreateNotificationStream}.
 */
@Singleton
@Component(service = RpcImplementation.class)
public final class CreateNotificationStreamRpc extends RpcImplementation {
    private static final NodeIdentifier SAL_REMOTE_OUTPUT_NODEID =
        NodeIdentifier.create(CreateDataChangeEventSubscriptionOutput.QNAME);
    private static final NodeIdentifier NOTIFICATIONS =
        NodeIdentifier.create(QName.create(CreateNotificationStreamInput.QNAME, "notifications").intern());
    private static final NodeIdentifier STREAM_NAME_NODEID =
        NodeIdentifier.create(QName.create(CreateDataChangeEventSubscriptionOutput.QNAME, "stream-name").intern());

    private final DatabindProvider databindProvider;
    private final DOMNotificationService notificationService;
    private final RestconfStream.Registry streamRegistry;

    @Inject
    @Activate
    public CreateNotificationStreamRpc(@Reference final RestconfStream.Registry streamRegistry,
            @Reference final DatabindProvider databindProvider,
            @Reference final DOMNotificationService notificationService) {
        super(CreateNotificationStream.QNAME);
        this.databindProvider = requireNonNull(databindProvider);
        this.notificationService = requireNonNull(notificationService);
        this.streamRegistry = requireNonNull(streamRegistry);
    }

    @Override
    public RestconfFuture<OperationsPostResult> invoke(final URI restconfURI, final OperationInput input) {
        final var body = input.input();
        final var qnames = ((LeafSetNode<String>) body.getChildByArg(NOTIFICATIONS)).body().stream()
            .map(LeafSetEntryNode::body)
            .map(QName::create)
            .sorted()
            .collect(ImmutableSet.toImmutableSet());

        final var operPath = input.path();
        final var modelContext = operPath.databind().modelContext();
        final var description = new StringBuilder("YANG notifications matching any of {");
        var haveFirst = false;
        for (var qname : qnames) {
            final var optModule = modelContext.findModuleStatement(qname.getModule());
            if (optModule.isEmpty()) {
                return RestconfFuture.failed(new RestconfDocumentedException(qname + " refers to an unknown module",
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE));
            }
            final var module = optModule.orElseThrow();
            final var optStmt = module.findSchemaTreeNode(qname);
            if (optStmt.isEmpty()) {
                return RestconfFuture.failed(new RestconfDocumentedException(
                    qname + " refers to an unknown notification", ErrorType.APPLICATION, ErrorTag.INVALID_VALUE));
            }
            if (!(optStmt.orElseThrow() instanceof NotificationEffectiveStatement)) {
                return RestconfFuture.failed(new RestconfDocumentedException(qname + " refers to a non-notification",
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE));
            }

            if (haveFirst) {
                description.append(",\n");
            } else {
                haveFirst = true;
            }
            description.append("\n  ")
                .append(module.argument().getLocalName()).append(':').append(qname.getLocalName());
        }
        description.append("\n}");

        return streamRegistry.createStream(restconfURI,
            new NotificationSource(databindProvider, notificationService, qnames), description.toString())
            .transform(stream -> new OperationsPostResult(operPath, ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(SAL_REMOTE_OUTPUT_NODEID)
                .withChild(ImmutableNodes.leafNode(STREAM_NAME_NODEID, stream.name()))
                .build()));
    }
}
