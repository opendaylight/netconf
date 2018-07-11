/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.rpc;

import com.google.common.base.Optional;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.util.mapping.AbstractLastNetconfOperation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class SimulatedCommit extends AbstractLastNetconfOperation {

    public SimulatedCommit(final String netconfSessionIdForReporting) {
        super(netconfSessionIdForReporting);
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {
        return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.absent());
    }

    @Override
    protected String getOperationName() {
        return XmlNetconfConstants.COMMIT;
    }
}
