/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import com.google.common.base.Preconditions;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.util.concurrent.FluentFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.controller.md.sal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMActionServiceExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceCommunicator;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class DeviceActionFactoryRemove implements DeviceActionFactory {

    @Override
    public DOMActionService createDeviceAction(MessageTransformer<NetconfMessage> messageTransformer,
            RemoteDeviceCommunicator<NetconfMessage> listener, SchemaContext schemaContext) {
        return new ActionImpl(messageTransformer, listener, schemaContext);
    }

    private static class ActionImpl implements DOMActionService {

        private MessageTransformer<NetconfMessage> messageTransformer;
        private RemoteDeviceCommunicator<NetconfMessage> listener;
        private SchemaContext schemaContext;

        ActionImpl(MessageTransformer<NetconfMessage> messageTransformer, RemoteDeviceCommunicator<
                NetconfMessage> listener, SchemaContext schemaContext) {
            this.messageTransformer = messageTransformer;
            this.listener = listener;
            this.schemaContext = schemaContext;
            Preconditions.checkNotNull(this.listener);
            Preconditions.checkNotNull(this.schemaContext);
            Preconditions.checkNotNull(this.messageTransformer);
        }

        @Override
        public FluentFuture<? extends DOMActionResult> invokeAction(SchemaPath type, DOMDataTreeIdentifier path,
                ContainerNode input) {
            NetconfMessage rpcRequest = messageTransformer.toActionRequest(type, path, input);
            Preconditions.checkNotNull(rpcRequest);
            return FluentFutures.immediateFluentFuture(new ActionResultImpl());
        }

        @Override
        public @NonNull ClassToInstanceMap<DOMActionServiceExtension> getExtensions() {
            // TODO Auto-generated method stub
            return null;
        }

        private static class ActionResultImpl implements DOMActionResult {

            @Override
            public Collection<RpcError> getErrors() {
                return new ArrayList<>();
            }

            @Override
            public Optional<ContainerNode> getOutput() {
                ContainerNode build = ImmutableContainerNodeBuilder.create().build();
                return Optional.of(build);
            }
        }
    }
}
