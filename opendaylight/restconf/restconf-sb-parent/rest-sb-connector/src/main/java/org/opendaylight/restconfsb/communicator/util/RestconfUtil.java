/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.util;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev150130.RestconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev131019.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev131019.Errors;
import org.opendaylight.yangtools.sal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.DomUtils;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.parser.DomToNormalizedNodeParserFactory;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class RestconfUtil {

    private static final SchemaContext SCHEMA_CTX;
    private static final ContainerSchemaNode MODULES_SCHEMA_NODE;
    private static final ContainerSchemaNode ERRORS_SCHEMA_NODE;
    private static final ContainerSchemaNode STREAMS_SCHEMA_NODE;

    static {
        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        final List<YangModuleInfo> modules = ImmutableList.of($YangModuleInfoImpl.getInstance(),
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev150130.$YangModuleInfoImpl.getInstance(),
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev150130.$YangModuleInfoImpl.getInstance());
        moduleInfoBackedContext.addModuleInfos(modules);
        final Optional<SchemaContext> schemaContextOptional = moduleInfoBackedContext.tryToCreateSchemaContext();
        Preconditions.checkState(schemaContextOptional.isPresent());
        SCHEMA_CTX = schemaContextOptional.get();
        MODULES_SCHEMA_NODE = initModulesSchemaNode();
        ERRORS_SCHEMA_NODE = initErrorsSchemaNode();
        STREAMS_SCHEMA_NODE = initStreamsSchemaNode();
    }

    private RestconfUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ContainerSchemaNode getModulesSchemaNode() {
        return MODULES_SCHEMA_NODE;
    }

    public static ContainerSchemaNode getErrorsSchemaNode() {
        return ERRORS_SCHEMA_NODE;
    }

    public static ContainerSchemaNode getStreamsSchemaNode() {
        return STREAMS_SCHEMA_NODE;

    }

    public static ContainerNode parseXmlContainer(final InputStream body, final ContainerSchemaNode schemaNode) {
        final Document doc;
        try {
            doc = XmlUtil.readXmlToDocument(body);
        } catch (IOException | SAXException e) {
            throw new IllegalStateException("Malformed reply received", e);
        }
        final XmlElement element = XmlElement.fromDomDocument(doc);
        return DomToNormalizedNodeParserFactory
                .getInstance(DomUtils.defaultValueCodecProvider(), SCHEMA_CTX)
                .getContainerNodeParser()
                .parse(Collections.singletonList(element.getDomElement()), schemaNode);
    }

    public static RpcError[] toRpcErrorArray(final Collection<RpcError> rpcErrors) {
        return rpcErrors.toArray(new RpcError[rpcErrors.size()]);
    }

    private static ContainerSchemaNode initModulesSchemaNode() {
        final String yangLibraryModuleName =
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev150130.$YangModuleInfoImpl.getInstance().getName();
        final org.opendaylight.yangtools.yang.model.api.Module module = SCHEMA_CTX.findModuleByName(yangLibraryModuleName, null);
        return (ContainerSchemaNode) module.getDataChildByName(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev150130.Modules.QNAME);
    }

    private static ContainerSchemaNode initErrorsSchemaNode() {
        final GroupingDefinition errorsGrouping = getGrouping(Errors.QNAME);
        return (ContainerSchemaNode) errorsGrouping.getDataChildByName(Errors.QNAME);
    }

    private static ContainerSchemaNode initStreamsSchemaNode() {
        final String ietfRestconfMonitoringName = org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev150130.$YangModuleInfoImpl.getInstance().getName();
        final org.opendaylight.yangtools.yang.model.api.Module module = SCHEMA_CTX.findModuleByName(ietfRestconfMonitoringName, null);
        final ContainerSchemaNode restconfState = (ContainerSchemaNode) module.getDataChildByName(RestconfState.QNAME);
        return (ContainerSchemaNode) restconfState.getDataChildByName(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev150130.restconf.state.Streams.QNAME);
    }

    private static GroupingDefinition getGrouping(final QName name) {
        final YangModuleInfo restconfModule = $YangModuleInfoImpl.getInstance();
        final org.opendaylight.yangtools.yang.model.api.Module module = SCHEMA_CTX.findModuleByName(restconfModule.getName(), null);
        for (final GroupingDefinition grouping : module.getGroupings()) {
            if (grouping.getQName().equals(name)) {
                return grouping;
            }
        }
        throw new IllegalStateException("Can't find schema node: " + name);
    }

}
