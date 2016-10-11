/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.confignetconfconnector.operations;

import com.google.common.base.Optional;
import java.util.HashMap;
import java.util.Map;
import org.opendaylight.controller.config.facade.xml.ConfigSubsystemFacade;
import org.opendaylight.controller.config.facade.xml.Datastore;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;


public class DiscardChanges extends AbstractConfigNetconfOperation {

    public static final String DISCARD = "discard-changes";

    private static final Logger LOG = LoggerFactory.getLogger(DiscardChanges.class);

    public DiscardChanges(final ConfigSubsystemFacade configSubsystemFacade, final String netconfSessionIdForReporting) {
        super(configSubsystemFacade, netconfSessionIdForReporting);
    }

    private static void fromXml(final XmlElement xml) throws DocumentedException {
        xml.checkName(DISCARD);
        xml.checkNamespace(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0);
    }

    @Override
    protected String getOperationName() {
        return DISCARD;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement xml) throws DocumentedException {
        fromXml(xml);
        try {
            getConfigSubsystemFacade().abortConfiguration();
        } catch (final RuntimeException e) {
            LOG.warn("Abort failed: ", e);
            final Map<String, String> errorInfo = new HashMap<>();
            errorInfo
                    .put(ErrorTag.OPERATION_FAILED.name(),
                            "Abort failed.");
            throw new DocumentedException(e.getMessage(), e, ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED,
                    ErrorSeverity.ERROR, errorInfo);
        }
        LOG.trace("Changes discarded successfully from datastore {}", Datastore.candidate);


        return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.absent());
    }
}
