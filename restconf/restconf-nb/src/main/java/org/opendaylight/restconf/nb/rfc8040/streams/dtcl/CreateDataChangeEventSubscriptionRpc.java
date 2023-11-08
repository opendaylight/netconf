/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.dtcl;

import static java.util.Objects.requireNonNull;

import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.streams.DataTreeChangeStream;
import org.opendaylight.restconf.nb.rfc8040.streams.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.server.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscription;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev231103.CreateDataChangeEventSubscriptionInput1;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

/**
 * RESTCONF implementation of {@link CreateDataChangeEventSubscription}.
 */
// FIXME: this should be a component
@NonNullByDefault
public final class CreateDataChangeEventSubscriptionRpc extends RpcImplementation {
    private static final NodeIdentifier DATASTORE_NODEID = NodeIdentifier.create(
        QName.create(CreateDataChangeEventSubscriptionInput1.QNAME, "datastore").intern());
    private static final NodeIdentifier STREAM_NAME_NODEID =
        NodeIdentifier.create(QName.create(CreateDataChangeEventSubscriptionOutput.QNAME, "stream-name").intern());
    private static final NodeIdentifier PATH_NODEID =
        NodeIdentifier.create(QName.create(CreateDataChangeEventSubscriptionInput.QNAME, "path").intern());
    private static final NodeIdentifier OUTPUT_NODEID =
        NodeIdentifier.create(CreateDataChangeEventSubscriptionOutput.QNAME);

    private final DatabindProvider databindProvider;
    private final ListenersBroker broker;

    public CreateDataChangeEventSubscriptionRpc(final DatabindProvider databindProvider, final ListenersBroker broker) {
        super(CreateDataChangeEventSubscription.QNAME);
        this.databindProvider = requireNonNull(databindProvider);
        this.broker = requireNonNull(broker);
    }

    /**
     * Create data-change-event stream with POST operation via RPC.
     *
     * @param input Input of RPC - example in JSON (data-change-event stream):
     *              <pre>
     *              {@code
     *                  {
     *                      "input": {
     *                          "path": "/toaster:toaster/toaster:toasterStatus",
     *                          "sal-remote-augment:datastore": "OPERATIONAL",
     *                      }
     *                  }
     *              }
     *              </pre>
     * @return Future output of RPC - example in JSON:
     *     <pre>
     *     {@code
     *         {
     *             "output": {
     *                 "stream-name": "toaster:toaster/toaster:toasterStatus/datastore=OPERATIONAL/scope=ONE"
     *             }
     *         }
     *     }
     *     </pre>
     */
    @Override
    public RestconfFuture<RpcOutput> invoke(final Principal principal, final String restconfUri, final RpcInput input) {
        final var databind = input.databind();
        final var body = input.input();
        final var datastoreName = leaf(body, DATASTORE_NODEID, String.class);
        final var datastore = datastoreName != null ? LogicalDatastoreType.valueOf(datastoreName)
            : LogicalDatastoreType.CONFIGURATION;

        final var path = leaf(body, PATH_NODEID, YangInstanceIdentifier.class);
        if (path == null) {
            return RestconfFuture.failed(
                new RestconfDocumentedException("missing path", ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT));
        }

        return broker.createStream("Events occuring in " + datastore + " datastore under /"
            + IdentifierCodec.serialize(path, databind.modelContext()), restconfUri,
            name -> new DataTreeChangeStream(broker, name, null, databindProvider, datastore, path))
            .transform(stream -> new RpcOutput(databind, Builders.containerBuilder()
                .withNodeIdentifier(OUTPUT_NODEID)
                .withChild(ImmutableNodes.leafNode(STREAM_NAME_NODEID, stream.name()))
                .build()));
    }
}
