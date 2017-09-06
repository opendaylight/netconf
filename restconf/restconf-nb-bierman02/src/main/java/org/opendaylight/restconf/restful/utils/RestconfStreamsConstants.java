/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.collect.ImmutableSet;
import java.net.URI;
import java.text.ParseException;
import java.util.Date;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.restconf.utils.parser.builder.ParserBuilderConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Constants for streams.
 *
 */
public final class RestconfStreamsConstants {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfStreamsConstants.class);

    public static final String SAL_REMOTE_NAMESPACE = "urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote";

    public static final String DATASTORE_PARAM_NAME = "datastore";

    private static final URI NAMESPACE_EVENT_SUBSCRIPTION_AUGMENT = URI.create("urn:sal:restconf:event:subscription");

    public static final QNameModule SAL_REMOTE_AUGMENT;

    static {
        final Date eventSubscriptionAugRevision;
        try {
            eventSubscriptionAugRevision = SimpleDateFormatUtil.getRevisionFormat().parse("2014-07-08");
        } catch (final ParseException e) {
            final String errMsg = "It wasn't possible to convert revision date of sal-remote-augment to date";
            LOG.debug(errMsg);
            throw new RestconfDocumentedException(errMsg, ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
        }
        SAL_REMOTE_AUGMENT = QNameModule.create(NAMESPACE_EVENT_SUBSCRIPTION_AUGMENT, eventSubscriptionAugRevision)
                .intern();
    }

    public static final AugmentationIdentifier SAL_REMOTE_AUG_IDENTIFIER = new AugmentationIdentifier(
        ImmutableSet.of(QName.create(SAL_REMOTE_AUGMENT, "scope"), QName.create(SAL_REMOTE_AUGMENT, "datastore"),
            QName.create(SAL_REMOTE_AUGMENT, "notification-output-type")));

    public static final DataChangeScope DEFAULT_SCOPE = DataChangeScope.BASE;

    public static final LogicalDatastoreType DEFAULT_DS = LogicalDatastoreType.CONFIGURATION;

    public static final String SCOPE_PARAM_NAME = "scope";

    public static final char EQUAL = ParserBuilderConstants.Deserializer.EQUAL;

    public static final String DS_URI = RestconfConstants.SLASH + DATASTORE_PARAM_NAME + EQUAL;

    public static final String SCOPE_URI = RestconfConstants.SLASH + SCOPE_PARAM_NAME + EQUAL;

    public static final int NOTIFICATION_PORT = 8181;

    public static final String SCHEMA_SUBSCIBRE_URI = "ws";

    public static final CharSequence DATA_SUBSCR = "data-change-event-subscription";
    public static final CharSequence CREATE_DATA_SUBSCR = "create-" + DATA_SUBSCR;

    public static final CharSequence NOTIFICATION_STREAM = "notification-stream";
    public static final CharSequence CREATE_NOTIFICATION_STREAM = "create-" + NOTIFICATION_STREAM;

    public static final String STREAMS_PATH = "ietf-restconf-monitoring:restconf-state/streams";
    public static final String STREAM_PATH_PART = "/stream=";
    public static final String STREAM_PATH = STREAMS_PATH + STREAM_PATH_PART;
    public static final String STREAM_ACCESS_PATH_PART = "/access=";
    public static final String STREAM_LOCATION_PATH_PART = "/location";

    private RestconfStreamsConstants() {
        throw new UnsupportedOperationException("Util class.");
    }

}
