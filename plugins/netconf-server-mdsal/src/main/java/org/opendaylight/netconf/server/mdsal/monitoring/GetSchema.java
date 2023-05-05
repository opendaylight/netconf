/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.monitoring;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.operations.AbstractSingletonNetconfOperation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class GetSchema extends AbstractSingletonNetconfOperation {
    private static final Logger LOG = LoggerFactory.getLogger(GetSchema.class);
    public static final String URN_IETF_PARAMS_XML_NS_YANG_IETF_NETCONF_MONITORING =
        "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring";

    private static final String GET_SCHEMA = "get-schema";
    private static final String IDENTIFIER = "identifier";
    private static final String VERSION = "version";

    private final NetconfMonitoringService monitoring;

    public GetSchema(final SessionIdType sessionId, final NetconfMonitoringService monitoring) {
        super(sessionId);
        this.monitoring = requireNonNull(monitoring);
    }

    @Override
    protected String getOperationName() {
        return GET_SCHEMA;
    }

    @Override
    protected String getOperationNamespace() {
        return URN_IETF_PARAMS_XML_NS_YANG_IETF_NETCONF_MONITORING;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement xml)
            throws DocumentedException {
        final var entry = new GetSchemaEntry(xml);

        final String schema;
        try {
            schema = monitoring.getSchemaForCapability(entry.identifier, Optional.ofNullable(entry.version));
        } catch (final IllegalStateException e) {
            LOG.warn("Rpc error: {}", ErrorTag.OPERATION_FAILED, e);
            throw new DocumentedException(e.getMessage(), e,
                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR,
                // FIXME: so we have an <operation-failed>e.getMessage()</operation-failed> ??? In which namespace? Why?
                Map.of(ErrorTag.OPERATION_FAILED.elementBody(), e.getMessage()));
        }

        final var getSchemaResult = XmlUtil.createTextElement(document, XmlNetconfConstants.DATA_KEY, schema,
                Optional.of(URN_IETF_PARAMS_XML_NS_YANG_IETF_NETCONF_MONITORING));
        LOG.trace("{} operation successful", GET_SCHEMA);
        return getSchemaResult;
    }

    private static final class GetSchemaEntry {
        private final @NonNull String identifier;
        private final @Nullable String version;

        GetSchemaEntry(final XmlElement getSchemaElement) throws DocumentedException {
            getSchemaElement.checkName(GET_SCHEMA);
            getSchemaElement.checkNamespace(URN_IETF_PARAMS_XML_NS_YANG_IETF_NETCONF_MONITORING);

            final XmlElement identifierElement;
            try {
                identifierElement = getSchemaElement.getOnlyChildElementWithSameNamespace(IDENTIFIER);
            } catch (final DocumentedException e) {
                LOG.trace("Can't get identifier element as only child element with same namespace due to ", e);
                throw DocumentedException.wrap(e);
            }
            identifier = identifierElement.getTextContent();
            final var versionElement = getSchemaElement.getOnlyChildElementWithSameNamespaceOptionally(VERSION);
            if (versionElement.isPresent()) {
                version = versionElement.orElseThrow().getTextContent();
            } else {
                version = null;
            }
        }
    }
}
