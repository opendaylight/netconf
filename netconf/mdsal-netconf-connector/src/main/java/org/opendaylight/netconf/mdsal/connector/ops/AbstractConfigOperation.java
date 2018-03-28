/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.base.Strings;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

abstract class AbstractConfigOperation extends AbstractSingletonNetconfOperation {

    protected AbstractConfigOperation(final String netconfSessionIdForReporting) {
        super(netconfSessionIdForReporting);
    }

    protected static NodeList getElementsByTagName(final XmlElement parent, final String key) throws
        DocumentedException {
        final Element domParent = parent.getDomElement();
        final NodeList elementsByTagName;

        if (Strings.isNullOrEmpty(domParent.getPrefix())) {
            elementsByTagName = domParent.getElementsByTagName(key);
        } else {
            elementsByTagName = domParent.getElementsByTagNameNS(parent.getNamespace(), key);
        }

        return elementsByTagName;
    }
}
