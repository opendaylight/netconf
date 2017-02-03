/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *
 * We used old revision {@link Rfc8040.RestconfModule#REVISION} of restconf yang
 * because the latest restconf draft has to be supported by Yang 1.1 and we are
 * not. Then, this is only partial implementation of the latest restconf draft.
 */
public final class Rfc8040 {

    private Rfc8040() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Set of application specific media types to identify each of the available
     * resource types
     */
    public static final class MediaTypes {

        private MediaTypes() {
            throw new UnsupportedOperationException("Util class");
        }

        public static final String DATA = "application/yang-data";
        public static final String PATCH = "application/yang.patch";
        public static final String PATCH_STATUS = "application/yang.patch-status";
        public static final String YIN = "application/yin";
        public static final String YANG = "application/yang";
    }

    /**
     * Constants for restconf module
     *
     */
    public static final class RestconfModule {

        private static final Logger LOG = LoggerFactory.getLogger(RestconfModule.class);

        private RestconfModule() {
            throw new UnsupportedOperationException("Util class");
        }

        public static final String REVISION = "2017-01-26";
        public static final String NAME = "ietf-restconf";
        public static final String NAMESPACE = "urn:ietf:params:xml:ns:yang:ietf-restconf";

        public static final QName IETF_RESTCONF_QNAME = QName.create(Rfc8040.RestconfModule.NAMESPACE,
                Rfc8040.RestconfModule.REVISION, Rfc8040.RestconfModule.NAME);

        public static Date DATE = null;
        public static URI URI_MODULE = null;

        static {
            try {
                DATE = new SimpleDateFormat("yyyy-MM-dd").parse(REVISION);
                URI_MODULE = new URI(NAMESPACE);
            } catch (final ParseException | URISyntaxException e) {
                LOG.error(e.getMessage());
            }
        }

        // RESTCONF
        public static final String RESTCONF_GROUPING_SCHEMA_NODE = "restconf";
        public static final String RESTCONF_CONTAINER_SCHEMA_NODE = "restconf";
        public static final String OPERATIONS_CONTAINER_SCHEMA_NODE = "operations";
        public static final String DATA_CONTAINER_SCHEMA_NODE = "data";
        public static final String LIB_VER_LEAF_SCHEMA_NODE = "yang-library-version";

        public static final QName RESTCONF_GROUPING_QNAME =
                QName.create(IETF_RESTCONF_QNAME, RESTCONF_GROUPING_SCHEMA_NODE);
        public static final QName RESTCONF_CONTAINER_QNAME =
                QName.create(IETF_RESTCONF_QNAME, RESTCONF_CONTAINER_SCHEMA_NODE);
        public static final QName LIB_VER_LEAF_QNAME = QName.create(IETF_RESTCONF_QNAME, LIB_VER_LEAF_SCHEMA_NODE);

        // ERRORS
        public static final String ERRORS_GROUPING_SCHEMA_NODE = "errors";
        public static final String ERRORS_CONTAINER_SCHEMA_NODE = "errors";
        public static final String ERROR_LIST_SCHEMA_NODE = "error";

        public static final QName ERRORS_CONTAINER_QNAME =
                QName.create(IETF_RESTCONF_QNAME, ERRORS_CONTAINER_SCHEMA_NODE);
        public static final QName ERROR_LIST_QNAME = QName.create(IETF_RESTCONF_QNAME, ERROR_LIST_SCHEMA_NODE);
        public static final QName ERROR_TYPE_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-type");
        public static final QName ERROR_TAG_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-tag");
        public static final QName ERROR_APP_TAG_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-app-tag");
        public static final QName ERROR_MESSAGE_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-message");
        public static final QName ERROR_INFO_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-info");
        public static final QName ERROR_PATH_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-path");
    }

    /**
     * Constants for ietf-yang-library model
     *
     */
    public static final class IetfYangLibrary {

        private static final Logger LOG = LoggerFactory.getLogger(IetfYangLibrary.class);

        private IetfYangLibrary() {
            throw new UnsupportedOperationException("Util class");
        }

        public static final String NAME = "ietf-yang-library";
        public static final String NAMESPACE = "urn:ietf:params:xml:ns:yang:ietf-yang-library";
        public static final String REVISION = "2016-06-21";

        public static Date DATE = null;
        public static URI URI_MODULE = null;

        static {
            try {
                DATE = new SimpleDateFormat("yyyy-MM-dd").parse(REVISION);
                URI_MODULE = new URI(NAMESPACE);
            } catch (final ParseException | URISyntaxException e) {
                LOG.error(e.getMessage());
            }
        }

        public static final QNameModule MODULE_QNAME = QNameModule.create(URI_MODULE, DATE);

        public static final String MODULE_SET_ID_LEAF = "module-set-id";
        public static final QName MODULE_SET_ID_LEAF_QNAME = QName.create(MODULE_QNAME, MODULE_SET_ID_LEAF);

        public static final String GROUPING_MODULE_LIST = "module-list";
        public static final QName GROUPING_MODULE_LIST_QNAME = QName.create(MODULE_QNAME, GROUPING_MODULE_LIST);

        public static final String MODULES_STATE_CONT = "modules-state";
        public static final QName MODUELS_STATE_CONT_QNAME = QName.create(MODULE_QNAME, MODULES_STATE_CONT);

        public static final String MODULE_LIST = "module";
        public static final QName MODULE_QNAME_LIST = QName.create(MODULE_QNAME, MODULE_LIST);

        public static final String SPECIFIC_MODULE_NAME_LEAF = "name";
        public static final QName SPECIFIC_MODULE_NAME_LEAF_QNAME =
                QName.create(MODULE_QNAME, SPECIFIC_MODULE_NAME_LEAF);

        public static final String SPECIFIC_MODULE_REVISION_LEAF = "revision";
        public static final QName SPECIFIC_MODULE_REVISION_LEAF_QNAME =
                QName.create(MODULE_QNAME, SPECIFIC_MODULE_REVISION_LEAF);

        public static final String BASE_URI_OF_SCHEMA = "/modules/";
        public static final String SPECIFIC_MODULE_SCHEMA_LEAF = "schema";
        public static final QName SPECIFIC_MODULE_SCHEMA_LEAF_QNAME =
                QName.create(MODULE_QNAME, SPECIFIC_MODULE_SCHEMA_LEAF);

        public static final String SPECIFIC_MODULE_NAMESPACE_LEAF = "namespace";
        public static final QName SPECIFIC_MODULE_NAMESPACE_LEAF_QNAME =
                QName.create(MODULE_QNAME, SPECIFIC_MODULE_NAMESPACE_LEAF);

        public static final String SPECIFIC_MODULE_FEATURE_LEAF_LIST = "feature";
        public static final QName SPECIFIC_MODULE_FEATURE_LEAF_LIST_QNAME =
                QName.create(MODULE_QNAME, SPECIFIC_MODULE_FEATURE_LEAF_LIST);

        public static final String SPECIFIC_MODULE_DEVIATION_LIST = "deviation";
        public static final QName SPECIFIC_MODULE_DEVIATION_LIST_QNAME =
                QName.create(MODULE_QNAME, SPECIFIC_MODULE_DEVIATION_LIST);

        public static final String SPECIFIC_MODULE_CONFORMANCE_LEAF = "conformance-type";
        public static final QName SPECIFIC_MODULE_CONFORMANCE_LEAF_QNAME =
                QName.create(MODULE_QNAME, SPECIFIC_MODULE_CONFORMANCE_LEAF);

        public static final String SPECIFIC_MODULE_SUBMODULE_LIST = "submodule";
        public static final QName SPECIFIC_MODULE_SUBMODULE_LIST_QNAME =
                QName.create(MODULE_QNAME, SPECIFIC_MODULE_SUBMODULE_LIST);
    }

    /**
     * Constants for ietf-restconf-monitoring module
     *
     */
    public static final class MonitoringModule {

        private static final Logger LOG = LoggerFactory.getLogger(MonitoringModule.class);

        private MonitoringModule() {
            throw new UnsupportedOperationException("Util class");
        }

        public static final String NAME = "ietf-restconf-monitoring";
        public static final String NAMESPACE = "urn:ietf:params:xml:ns:yang:ietf-restconf-monitoring";
        public static final String REVISION = "2017-01-26";
        public static final String PATH_TO_STREAM_WITHOUT_KEY =
                "ietf-restconf-monitoring:restconf-state/streams/stream=";
        public static final String PATH_TO_STREAMS = "ietf-restconf-monitoring:restconf-state/streams";

        public static Date DATE = null;
        public static URI URI_MODULE = null;

        static {
            try {
                DATE = new SimpleDateFormat("yyyy-MM-dd").parse(REVISION);
                URI_MODULE = new URI(NAMESPACE);
            } catch (final ParseException | URISyntaxException e) {
                LOG.error(e.getMessage());
            }
        }

        public static final QNameModule MODULE_QNAME = QNameModule.create(URI_MODULE, DATE);

        public static final String CONT_RESTCONF_STATE_NAME = "restconf-state";
        public static final QName CONT_RESTCONF_STATE_QNAME = QName.create(MODULE_QNAME, CONT_RESTCONF_STATE_NAME);

        public static final String CONT_CAPABILITIES_NAME = "capabilities";
        public static final QName CONT_CAPABILITES_QNAME = QName.create(MODULE_QNAME, CONT_CAPABILITIES_NAME);

        public static final String LEAF_LIST_CAPABILITY_NAME = "capability";
        public static final QName LEAF_LIST_CAPABILITY_QNAME = QName.create(MODULE_QNAME, LEAF_LIST_CAPABILITY_NAME);

        public static final String CONT_STREAMS_NAME = "streams";
        public static final QName CONT_STREAMS_QNAME = QName.create(MODULE_QNAME, CONT_STREAMS_NAME);

        public static final String LIST_STREAM_NAME = "stream";
        public static final QName LIST_STREAM_QNAME = QName.create(MODULE_QNAME, LIST_STREAM_NAME);

        public static final String LEAF_NAME_STREAM_NAME = "name";
        public static final QName LEAF_NAME_STREAM_QNAME = QName.create(MODULE_QNAME, LEAF_NAME_STREAM_NAME);

        public static final String LEAF_DESCR_STREAM_NAME = "description";
        public static final QName LEAF_DESCR_STREAM_QNAME = QName.create(MODULE_QNAME, LEAF_DESCR_STREAM_NAME);

        public static final String LEAF_REPLAY_SUPP_STREAM_NAME = "replay-support";
        public static final QName LEAF_REPLAY_SUPP_STREAM_QNAME =
                QName.create(MODULE_QNAME, LEAF_REPLAY_SUPP_STREAM_NAME);

        public static final String LEAF_START_TIME_STREAM_NAME = "replay-log-creation-time";
        public static final QName LEAF_START_TIME_STREAM_QNAME =
                QName.create(MODULE_QNAME, LEAF_START_TIME_STREAM_NAME);

        public static final String LIST_ACCESS_STREAM_NAME = "access";
        public static final QName LIST_ACCESS_STREAM_QNAME = QName.create(MODULE_QNAME, LIST_ACCESS_STREAM_NAME);

        public static final String LEAF_ENCODING_ACCESS_NAME = "encoding";
        public static final QName LEAF_ENCODING_ACCESS_QNAME = QName.create(MODULE_QNAME, LEAF_ENCODING_ACCESS_NAME);

        public static final String LEAF_LOCATION_ACCESS_NAME = "location";
        public static final QName LEAF_LOCATION_ACCESS_QNAME = QName.create(MODULE_QNAME, LEAF_LOCATION_ACCESS_NAME);

        /**
         * Constants for capabilities
         */
        public static final class QueryParams {

            private QueryParams() {
                throw new UnsupportedOperationException("Util class");
            }

            public static final String URI_BASE = "urn:ietf:params:restconf:capability:";

            public static final String DEPTH = URI_BASE + "depth:1.0";
            public static final String FIELDS = URI_BASE + "fields:1.0";
            public static final String FILTER = URI_BASE + "filter:1.0";
            public static final String REPLAY = URI_BASE + "replay:1.0";
            public static final String WITH_DEFAULTS = URI_BASE + "with-defaults:1.0";
        }
    }
}
