/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.collect.ImmutableSet;
import java.net.URI;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.restconf.common.util.DataChangeScope;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;

/**
 * Constants for streams.
 */
public final class RestconfStreamsConstants {

    public static final QNameModule SAL_REMOTE_AUGMENT = QNameModule.create(
            URI.create("urn:sal:restconf:event:subscription"),
            Revision.of("2014-07-08"));
    public static final QNameModule SUBSCRIBE_TO_NOTIFICATION = QNameModule.create(
            URI.create("subscribe:to:notification"),
            Revision.of("2016-10-28"));

    public static final QName SAL_REMOTE_NAMESPACE = QName.create(
            "urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote",
            "2014-01-14",
            "sal-remote");

    public static final String STREAM_PATH_PARAM_NAME = "path";
    public static final String DATASTORE_PARAM_NAME = "datastore";
    public static final String SCOPE_PARAM_NAME = "scope";
    public static final String OUTPUT_TYPE_PARAM_NAME = "notification-output-type";
    public static final String OUTPUT_CONTAINER_NAME = "output";
    public static final String OUTPUT_STREAM_NAME = "stream-name";


    public static final AugmentationIdentifier SAL_REMOTE_AUG_IDENTIFIER = new AugmentationIdentifier(ImmutableSet.of(
            QName.create(SAL_REMOTE_AUGMENT, SCOPE_PARAM_NAME),
            QName.create(SAL_REMOTE_AUGMENT, DATASTORE_PARAM_NAME),
            QName.create(SAL_REMOTE_AUGMENT, OUTPUT_TYPE_PARAM_NAME)));

    public static final QName LOCATION_QNAME = QName.create(SUBSCRIBE_TO_NOTIFICATION, "location");
    public static final QName NOTIFI_QNAME = QName.create(SUBSCRIBE_TO_NOTIFICATION, "notifi");

    public static final DataChangeScope DEFAULT_SCOPE = DataChangeScope.BASE;
    public static final LogicalDatastoreType DEFAULT_DS = LogicalDatastoreType.CONFIGURATION;

    public static final char EQUAL = ParserBuilderConstants.Deserializer.EQUAL;
    public static final String DS_URI = RestconfConstants.SLASH + DATASTORE_PARAM_NAME + EQUAL;
    public static final String SCOPE_URI = RestconfConstants.SLASH + SCOPE_PARAM_NAME + EQUAL;
    public static final String SCHEMA_SUBSCRIBE_URI = "ws";
    public static final String SCHEMA_SUBSCRIBE_SECURED_URI = "wss";
    public static final String SCHEMA_UPGRADE_URI = "http";
    public static final String SCHEMA_UPGRADE_SECURED_URI = "https";

    public static final String DATA_SUBSCRIPTION = "data-change-event-subscription";
    public static final String CREATE_DATA_SUBSCRIPTION = "create-" + DATA_SUBSCRIPTION;
    public static final String NOTIFICATION_STREAM = "notification-stream";
    public static final String CREATE_NOTIFICATION_STREAM = "create-" + NOTIFICATION_STREAM;

    public static final String STREAMS_PATH = "ietf-restconf-monitoring:restconf-state/streams";
    public static final String STREAM_PATH_PART = "/stream=";
    public static final String STREAM_PATH = STREAMS_PATH + STREAM_PATH_PART;
    public static final String STREAM_ACCESS_PATH_PART = "/access=";
    public static final String STREAM_LOCATION_PATH_PART = "/location";

    public static final String DATA_CHANGE_EVENT_STREAM_PATTERN = '/' + DATA_SUBSCRIPTION + "/*";
    public static final String YANG_NOTIFICATION_STREAM_PATTERN = '/' + NOTIFICATION_STREAM + "/*";

    private RestconfStreamsConstants() {
        throw new UnsupportedOperationException("Util class.");
    }
}