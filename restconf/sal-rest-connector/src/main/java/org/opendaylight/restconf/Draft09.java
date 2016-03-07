/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf;

import org.opendaylight.yangtools.yang.common.QName;

/**
 * Base Draft for Restconf project
 * <ul>
 * <li>Supported {@link MediaTypes}
 * <li>Constants for modules
 * <ul>
 * <li>{@link RestconfModule}
 * <li>{@link MonitoringModule}
 * </ul>
 * </ul>
 */
public final class Draft09 {

    private Draft09() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Set of application specific media types to identify each of the available
     * resource types
     */
    public static final class MediaTypes {
        public static final String API = "application/yang.api";
        public static final String DATASTORE = "application/yang.datastore";
        public static final String DATA = "application/yang.data";
        public static final String OPERATION = "application/yang.operation";
        public static final String PATCH = "application/yang.patch";
        public static final String PATCH_STATUS = "application/yang.patch-status";
        public static final String ERRORS = "application/yang.errors";
        public static final String YIN = "application/yin";
        public static final String YANG = "application/yang";
    }

    /**
     * Constants for restconf module - ietf-restconf@2015-10-18.yang
     *
     */
    public static final class RestconfModule {
        public static final String REVISION = "2013-10-19";

        public static final String NAME = "ietf-restconf";

        public static final String NAMESPACE = "urn:ietf:params:xml:ns:yang:ietf-restconf";

        public static final String RESTCONF_GROUPING_SCHEMA_NODE = "restconf";

        public static final String RESTCONF_CONTAINER_SCHEMA_NODE = "restconf";

        public static final String MODULES_CONTAINER_SCHEMA_NODE = "modules";

        public static final String MODULE_LIST_SCHEMA_NODE = "module";

        public static final String OPERATIONS_CONTAINER_SCHEMA_NODE = "operations";

        public static final String ERRORS_GROUPING_SCHEMA_NODE = "errors";

        public static final String ERRORS_CONTAINER_SCHEMA_NODE = "errors";

        public static final String ERROR_LIST_SCHEMA_NODE = "error";

        public static final QName IETF_RESTCONF_QNAME = QName.create(Draft09.RestconfModule.NAMESPACE, Draft09.RestconfModule.REVISION,
                Draft09.RestconfModule.NAME);

        public static final QName ERRORS_CONTAINER_QNAME = QName.create(IETF_RESTCONF_QNAME, ERRORS_CONTAINER_SCHEMA_NODE);

        public static final QName ERROR_LIST_QNAME = QName.create(IETF_RESTCONF_QNAME, ERROR_LIST_SCHEMA_NODE);

        public static final QName ERROR_TYPE_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-type");

        public static final QName ERROR_TAG_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-tag");

        public static final QName ERROR_APP_TAG_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-app-tag");

        public static final QName ERROR_MESSAGE_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-message");

        public static final QName ERROR_INFO_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-info");

        public static final QName ERROR_PATH_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-path");
    }

    /**
     * Constants for restconf module - ietf-restconf-monitoring @
     * 2015-06-19.yang
     *
     */
    public static final class MonitoringModule {
        public static final String STREAMS_CONTAINER_SCHEMA_NODE = "streams";

        public static final String STREAM_LIST_SCHEMA_NODE = "stream";
    }
}
