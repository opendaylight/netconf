/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util.xml;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;

public final class XMLNetconfUtil {
    private static final XPathFactory FACTORY = XPathFactory.newInstance();
    private static final NamespaceContext NS_CONTEXT = new HardcodedNamespaceResolver("netconf",
        XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0);

    private XMLNetconfUtil() {
        // Hidden on purpose
    }

    public static XPathExpression compileXPath(final String xpath) {
        final XPath newXPath = FACTORY.newXPath();
        newXPath.setNamespaceContext(NS_CONTEXT);
        try {
            return newXPath.compile(xpath);
        } catch (final XPathExpressionException e) {
            throw new IllegalStateException("Error while compiling xpath expression " + xpath, e);
        }
    }

}
