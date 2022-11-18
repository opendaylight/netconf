/*
 * Copyright (c) 2022 Opendaylight, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import java.util.Optional;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.ContentParam;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.CreateStreamUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.ReadDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableMapEntryNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DeviceMountPointListener implements DOMMountPointListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceMountPointListener.class);

    final DOMMountPointService mountPointService;
    final RestconfStrategy restconfStrategy;
    final EffectiveModelContext refSchemaCtx;

    @Inject
    public DeviceMountPointListener(final DOMMountPointService mountPointService,
                                    final DOMDataBroker dataBroker,
                                    final SchemaContextHandler refSchemaCtx) {
        this.mountPointService = mountPointService;
        this.restconfStrategy = new MdsalRestconfStrategy(dataBroker);
        this.refSchemaCtx = refSchemaCtx.get();
    }

    @Override
    public void onMountPointCreated(YangInstanceIdentifier path) {
        reRegisterDeviceNotification(path);
    }

    @Override
    public void onMountPointRemoved(YangInstanceIdentifier path) {

    }

    @PostConstruct
    public void init() {
        this.mountPointService.registerProvisionListener(this);
    }

    private void reRegisterDeviceNotification(YangInstanceIdentifier path) {
        String node = String.valueOf(
                ((YangInstanceIdentifier.NodeIdentifierWithPredicates)path.getLastPathArgument()).values()
                        .iterator().next());
        node = "network-topology:network-topology/topology=topology-netconf/node=" + node + "/yang-ext:mount";
        InstanceIdentifierContext instanceIdentifier = ParserIdentifier.toInstanceIdentifier(node,
                this.refSchemaCtx,
                Optional.of(this.mountPointService));
        NormalizedNode normalizedNode = ReadDataTransactionUtil.readData(ContentParam.ALL, path,
                this.restconfStrategy, null, this.refSchemaCtx);
        ImmutableMapEntryNodeBuilder.create((MapEntryNode) normalizedNode).build().getChildByArg(
                new YangInstanceIdentifier.AugmentationIdentifier(
                        Set.of(QName.create(QNameModule.create(
                                XMLNamespace.of("urn:opendaylight:netconf-node-optional"),
                                Revision.of("2019-06-14")), "notification"))));
        LOG.info("ss {}{}",instanceIdentifier.toString(), normalizedNode.toString());
        CreateStreamUtil.createDeviceNotificationListener(null,
                node,
                this.refSchemaCtx, instanceIdentifier,
                this.mountPointService, this.restconfStrategy);
    }
}
