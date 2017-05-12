/*
 * Copyright (c) 2017 Frinx s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.base.Optional;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.DomUtils;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.parser.DomToNormalizedNodeParserFactory;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class YangValidationNetconfOperation extends AbstractSingletonNetconfOperation {
    private static final Logger LOG = LoggerFactory.getLogger(YangValidationNetconfOperation.class);

    private final CurrentSchemaContext schemaContext;

    protected YangValidationNetconfOperation(String netconfSessionIdForReporting, CurrentSchemaContext schemaContext) {
        super(netconfSessionIdForReporting);
        this.schemaContext = schemaContext;
    }

    protected DataTreeChangeTracker createDataTreeChecker(ModifyAction defaultAction, XmlElement element) throws DocumentedException {
        final String ns = element.getNamespace();
        final DataSchemaNode schemaNode = getSchemaNodeFromNamespace(ns, element).get();

        final DataTreeChangeTracker changeTracker = new DataTreeChangeTracker(defaultAction);
        final DomToNormalizedNodeParserFactory.BuildingStrategyProvider editOperationStrategyProvider = new EditOperationStrategyProvider(changeTracker);

        parseIntoNormalizedNode(schemaNode, element, editOperationStrategyProvider);
        return changeTracker;
    }

    protected NormalizedNode parseIntoNormalizedNode(final DataSchemaNode schemaNode, final XmlElement element,
                                                   final DomToNormalizedNodeParserFactory.BuildingStrategyProvider editOperationStrategyProvider) {


        if (schemaNode instanceof ContainerSchemaNode) {
            return DomToNormalizedNodeParserFactory
                    .getInstance(DomUtils.defaultValueCodecProvider(), schemaContext.getCurrentContext(), editOperationStrategyProvider)
                    .getContainerNodeParser()
                    .parse(Collections.singletonList(element.getDomElement()), (ContainerSchemaNode) schemaNode);
        } else if (schemaNode instanceof ListSchemaNode) {
            return DomToNormalizedNodeParserFactory
                    .getInstance(DomUtils.defaultValueCodecProvider(), schemaContext.getCurrentContext(), editOperationStrategyProvider)
                    .getMapNodeParser()
                    .parse(Collections.singletonList(element.getDomElement()), (ListSchemaNode) schemaNode);
        } else {
            //this should never happen since edit-config on any other node type should not be possible nor makes sense
            LOG.debug("DataNode from module is not ContainerSchemaNode nor ListSchemaNode, aborting..");
        }
        throw new UnsupportedOperationException("implement exception if parse fails");
    }

    protected Optional<DataSchemaNode> getSchemaNodeFromNamespace(final String namespace, final XmlElement element) throws DocumentedException {
        Optional<DataSchemaNode> dataSchemaNode = Optional.absent();
        try {
            //returns module with newest revision since findModuleByNamespace returns a set of modules and we only need the newest one
            final Module module = schemaContext.getCurrentContext().findModuleByNamespaceAndRevision(new URI(namespace), null);
            if (module == null) {
                // no module is present with this namespace
                throw new NetconfDocumentedException("Unable to find module by namespace: " + namespace,
                        DocumentedException.ErrorType.application, DocumentedException.ErrorTag.unknown_namespace, DocumentedException.ErrorSeverity.error);
            }
            final DataSchemaNode schemaNode =
                    module.getDataChildByName(QName.create(module.getQNameModule(), element.getName()));
            if (schemaNode != null) {
                dataSchemaNode = Optional.of(schemaNode);
            } else {
                throw new DocumentedException("Unable to find node with namespace: " + namespace + "in module: " + module.toString(),
                        DocumentedException.ErrorType.application,
                        DocumentedException.ErrorTag.unknown_namespace,
                        DocumentedException.ErrorSeverity.error);
            }
        } catch (final URISyntaxException e) {
            LOG.debug("Unable to create URI for namespace : {}", namespace);
        }

        return dataSchemaNode;
    }

}
