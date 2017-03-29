/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.util;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.Set;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.DomUtils;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.parser.DomToNormalizedNodeParserFactory;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class NormalizedNodeUtils {

    private NormalizedNodeUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static NormalizedNode<?, ?> parseRpcInput(final SchemaContext context, final RpcDefinition definition, final Element body) {
        final Document doc = XmlUtil.createDocumentCopy(body.getOwnerDocument());
        final XmlElement element = XmlElement.fromDomDocument(doc);
        if (definition.getInput() != null) {
            final ContainerSchemaNode schemaNode = definition.getInput();

            return DomToNormalizedNodeParserFactory
                    .getInstance(DomUtils.defaultValueCodecProvider(), context)
                    .getContainerNodeParser()
                    .parse(Collections.singletonList(element.getDomElement()), schemaNode);
        } else {
            return null;
        }
    }

    public static RpcDefinition findRpcDefinition(final SchemaContext context, final String moduleName, final String rpcName) {
        final Module module = context.findModuleByName(moduleName, null);
        final Set<RpcDefinition> rpcs = Preconditions.checkNotNull(module, "module not found " + moduleName).getRpcs();
        for (final RpcDefinition rpcDefinition : rpcs) {
            final SchemaPath p = rpcDefinition.getPath();
            if (rpcName.equals(p.getLastComponent().getLocalName())) {
                return rpcDefinition;
            }
        }
        throw new IllegalStateException("Rpc schema path not found in context");
    }
}
