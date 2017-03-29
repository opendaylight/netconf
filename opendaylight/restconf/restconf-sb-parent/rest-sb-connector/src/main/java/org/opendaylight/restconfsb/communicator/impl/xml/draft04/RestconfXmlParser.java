/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.xml.draft04;

import com.google.common.base.Preconditions;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.restconfsb.communicator.api.parser.RestconfParser;
import org.opendaylight.restconfsb.communicator.api.stream.Stream;
import org.opendaylight.restconfsb.communicator.util.RestconfUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev150130.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module.CapabilityOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.ModuleBuilder;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;

/**
 * Parses xml responses
 */
public class RestconfXmlParser implements RestconfParser {
    private static final YangModuleInfo YANG_LIBRARY_MODULE = $YangModuleInfoImpl.getInstance();
    private static final QName YANG_LIBRARY = QName.create(YANG_LIBRARY_MODULE.getNamespace(),
            YANG_LIBRARY_MODULE.getRevision(), YANG_LIBRARY_MODULE.getName());

    private static final YangModuleInfo MONITORING_MODULE = org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev150130.$YangModuleInfoImpl.getInstance();

    private static final QName MONITORING = QName.create(MONITORING_MODULE.getNamespace(),
            MONITORING_MODULE.getRevision(), MONITORING_MODULE.getName());

    //modules container related qnames
    private static final YangInstanceIdentifier.NodeIdentifier REVISION = new YangInstanceIdentifier.NodeIdentifier(QName.create(YANG_LIBRARY, "revision"));
    private static final YangInstanceIdentifier.NodeIdentifier NAME = new YangInstanceIdentifier.NodeIdentifier(QName.create(YANG_LIBRARY, "name"));
    private static final YangInstanceIdentifier.NodeIdentifier NAMESPACE = new YangInstanceIdentifier.NodeIdentifier(QName.create(YANG_LIBRARY, "namespace"));

    //streams container related qnames
    private static final YangInstanceIdentifier.NodeIdentifier STREAM_NAME = new YangInstanceIdentifier.NodeIdentifier(QName.create(MONITORING, "name"));
    private static final YangInstanceIdentifier.NodeIdentifier STREAM_LOCATION = new YangInstanceIdentifier.NodeIdentifier(QName.create(MONITORING, "encoding"));
    private static final YangInstanceIdentifier.NodeIdentifier STREAM_REPLAY_SUPPORT = new YangInstanceIdentifier.NodeIdentifier(QName.create(MONITORING, "replay-support"));

    @Override
    public List<Module> parseModules(final InputStream stream) {
        final ContainerNode parsed = RestconfUtil.parseXmlContainer(stream, RestconfUtil.getModulesSchemaNode());
        final List<Module> moduleList =  new ArrayList<>();
        final MapNode moduleListNode = (MapNode) parsed.getValue().iterator().next();
        for (final MapEntryNode module : moduleListNode.getValue()) {
            moduleList.add(createModule(module));
        }
        return moduleList;
    }

    @Override
    public List<Stream> parseStreams(final InputStream stream) {
        final ContainerNode parsed = RestconfUtil.parseXmlContainer(stream, RestconfUtil.getStreamsSchemaNode());
        final List<Stream> streamList = new ArrayList<>();
        final MapNode streamListNode = (MapNode) parsed.getValue().iterator().next();
        for (final MapEntryNode streamNode : streamListNode.getValue()) {
            streamList.add(createStream(streamNode));
        }
        return streamList;
    }

    private Module createModule(final MapEntryNode module) {
        final String revision = (String) module.getChild(REVISION).get().getValue();
        final String name = (String) module.getChild(NAME).get().getValue();
        final String namespace = (String) module.getChild(NAMESPACE).get().getValue();
        return new ModuleBuilder()
                .setName(new YangIdentifier(name))
                .setNamespace(new Uri(namespace))
                .setRevision(new RevisionIdentifier(revision))
                .setCapabilityOrigin(CapabilityOrigin.DeviceAdvertised)
                .build();
    }

    private Stream createStream(final MapEntryNode streamNode) {
        final String name = (String) streamNode.getChild(STREAM_NAME).get().getValue();
        String location = null;
        final Boolean replaySupport = (Boolean) streamNode.getChild(STREAM_REPLAY_SUPPORT).get().getValue();

        final MapNode mapNode = (MapNode) streamNode.getChild(STREAM_LOCATION).get();
        final MapEntryNode mapEntryNode = mapNode.getValue().iterator().next();

        for (final DataContainerChild dataChild : mapEntryNode.getValue()) {
            if (dataChild.getNodeType().getLocalName().equals("events")) {
                location = dataChild.getValue().toString();
            }
        }

        Preconditions.checkNotNull(location);
        return new Stream(name, location, replaySupport);
    }

}