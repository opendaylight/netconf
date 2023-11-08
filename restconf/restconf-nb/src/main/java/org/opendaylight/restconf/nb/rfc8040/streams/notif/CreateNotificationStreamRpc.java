/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.notif;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.streams.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.streams.NotificationSource;
import org.opendaylight.restconf.server.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateNotificationStream;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateNotificationStreamInput;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.stmt.NotificationEffectiveStatement;

/**
 * RESTCONF implementation of {@link CreateNotificationStream}.
 */
// FIXME: this should be a component
@NonNullByDefault
public final class CreateNotificationStreamRpc extends RpcImplementation {
    private static final NodeIdentifier SAL_REMOTE_OUTPUT_NODEID =
        NodeIdentifier.create(CreateDataChangeEventSubscriptionOutput.QNAME);
    private static final NodeIdentifier NOTIFICATIONS =
        NodeIdentifier.create(QName.create(CreateNotificationStreamInput.QNAME, "notifications").intern());
    private static final NodeIdentifier STREAM_NAME_NODEID =
        NodeIdentifier.create(QName.create(CreateDataChangeEventSubscriptionOutput.QNAME, "stream-name").intern());

    private final DatabindProvider databindProvider;
    private final DOMNotificationService notificationService;
    private final ListenersBroker broker;

    public CreateNotificationStreamRpc(final DatabindProvider databindProvider,
            final DOMNotificationService notificationService, final ListenersBroker broker) {
        super(CreateNotificationStream.QNAME);
        this.databindProvider = requireNonNull(databindProvider);
        this.notificationService = requireNonNull(notificationService);
        this.broker = requireNonNull(broker);
    }

    @Override
    public RestconfFuture<RpcOutput> invoke(final Principal principal, final String restconfUri, final RpcInput input) {
        final var databind = input.databind();
        final var body = input.input();
        final var qnames = ((LeafSetNode<String>) body.getChildByArg(NOTIFICATIONS)).body().stream()
            .map(LeafSetEntryNode::body)
            .map(QName::create)
            .sorted()
            .collect(ImmutableSet.toImmutableSet());

        final var modelContext = databind.modelContext();
        final var description = new StringBuilder("YANG notifications matching any of {");
        var haveFirst = false;
        for (var qname : qnames) {
            final var module = modelContext.findModuleStatement(qname.getModule())
                .orElseThrow(() -> new RestconfDocumentedException(qname + " refers to an unknown module",
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE));
            final var stmt = module.findSchemaTreeNode(qname)
                .orElseThrow(() -> new RestconfDocumentedException(qname + " refers to an unknown notification",
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE));
            if (!(stmt instanceof NotificationEffectiveStatement)) {
                throw new RestconfDocumentedException(qname + " refers to a non-notification",
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE);
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

        return broker.createStream(description.toString(), restconfUri,
            new NotificationSource(databindProvider, notificationService, qnames))
            .transform(stream -> new RpcOutput(databind, Builders.containerBuilder()
                .withNodeIdentifier(SAL_REMOTE_OUTPUT_NODEID)
                .withChild(ImmutableNodes.leafNode(STREAM_NAME_NODEID, stream.name()))
                .build()));
    }
}
