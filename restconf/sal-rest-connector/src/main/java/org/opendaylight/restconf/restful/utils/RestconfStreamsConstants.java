/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.collect.Sets;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.restconf.utils.parser.builder.ParserBuilderConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Constants for streams
 *
 */
public final class RestconfStreamsConstants {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfStreamsConstants.class);

    public static final String SAL_REMOTE_NAMESPACE = "urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote";

    public static final String DATASTORE_PARAM_NAME = "datastore";

    private static final URI NAMESPACE_EVENT_SUBSCRIPTION_AUGMENT = URI.create("urn:sal:restconf:event:subscription");

    public static final QNameModule SAL_REMOTE_AUGMENT;

    public static final YangInstanceIdentifier.AugmentationIdentifier SAL_REMOTE_AUG_IDENTIFIER;

    public static final DataChangeScope DEFAULT_SCOPE = DataChangeScope.BASE;

    public static final LogicalDatastoreType DEFAULT_DS = LogicalDatastoreType.CONFIGURATION;

    public static final String SCOPE_PARAM_NAME = "scope";

    public static final String DS_URI = RestconfConstants.SLASH + DATASTORE_PARAM_NAME
            + ParserBuilderConstants.Deserializer.EQUAL;

    public static final String SCOPE_URI = RestconfConstants.SLASH + SCOPE_PARAM_NAME
            + ParserBuilderConstants.Deserializer.EQUAL;

    static {
        Date eventSubscriptionAugRevision;
        try {
            eventSubscriptionAugRevision = new SimpleDateFormat("yyyy-MM-dd").parse("2014-07-08");
        } catch (final ParseException e) {
            final String errMsg = "It wasn't possible to convert revision date of sal-remote-augment to date";
            LOG.debug(errMsg);
            throw new RestconfDocumentedException(errMsg, ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
        }
        SAL_REMOTE_AUGMENT = QNameModule.create(NAMESPACE_EVENT_SUBSCRIPTION_AUGMENT, eventSubscriptionAugRevision);
        SAL_REMOTE_AUG_IDENTIFIER = new YangInstanceIdentifier.AugmentationIdentifier(Sets
                .newHashSet(QName.create(SAL_REMOTE_AUGMENT, "scope"), QName.create(SAL_REMOTE_AUGMENT, "datastore")));
    }

    private RestconfStreamsConstants() {
        throw new UnsupportedOperationException("Util class.");
    }

}
