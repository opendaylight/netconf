/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import java.net.URI;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.Module;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.module.Deviation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.module.Submodule;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;

/**
 * Base Draft for Restconf project.
 * <ul>
 * <li>Supported {@link MediaTypes}
 * <li>Constants for modules
 * <ul>
 * <li>{@link RestconfModule}
 * <li>{@link MonitoringModule}
 * </ul>
 * </ul>
 */
public final class Rfc8040 {

    private Rfc8040() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Set of application specific media types to identify each of the available
     * resource types.
     */
    public static final class MediaTypes {

        private MediaTypes() {
            throw new UnsupportedOperationException("Util class");
        }

        /**
         * See: <a href="https://tools.ietf.org/html/rfc6415">rfc6415</a>.
         */
        public static final String XRD = "application/xrd";

        public static final String DATA = "application/yang-data";
        public static final String YANG_PATCH = "application/yang.patch";
        public static final String YANG_PATCH_STATUS = "application/yang.patch-status";
    }

    /**
     * Constants for restconf module.
     *
     */
    public static final class RestconfModule {
        private RestconfModule() {
            throw new UnsupportedOperationException("Util class");
        }

        public static final Revision REVISION = Revision.of("2017-01-26");
        public static final String NAME = "ietf-restconf";
        public static final String NAMESPACE = "urn:ietf:params:xml:ns:yang:ietf-restconf";
        public static final URI URI_MODULE = URI.create(NAMESPACE);

        public static final QName IETF_RESTCONF_QNAME = QName.create(URI_MODULE,
                Rfc8040.RestconfModule.REVISION, Rfc8040.RestconfModule.NAME).intern();

        // RESTCONF
        public static final String RESTCONF_GROUPING_SCHEMA_NODE = "restconf";
        public static final String RESTCONF_CONTAINER_SCHEMA_NODE = "restconf";
        public static final String OPERATIONS_CONTAINER_SCHEMA_NODE = "operations";
        public static final String DATA_CONTAINER_SCHEMA_NODE = "data";
        public static final String LIB_VER_LEAF_SCHEMA_NODE = "yang-library-version";

        public static final QName RESTCONF_GROUPING_QNAME =
                QName.create(IETF_RESTCONF_QNAME, RESTCONF_GROUPING_SCHEMA_NODE).intern();
        public static final QName RESTCONF_CONTAINER_QNAME =
                QName.create(IETF_RESTCONF_QNAME, RESTCONF_CONTAINER_SCHEMA_NODE).intern();
        public static final QName LIB_VER_LEAF_QNAME = QName.create(IETF_RESTCONF_QNAME, LIB_VER_LEAF_SCHEMA_NODE)
                .intern();

        // ERRORS
        public static final String ERRORS_GROUPING_SCHEMA_NODE = "errors";
        public static final String ERRORS_CONTAINER_SCHEMA_NODE = "errors";
        public static final String ERROR_LIST_SCHEMA_NODE = "error";

        public static final QName ERRORS_GROUPING_QNAME =
                QName.create(IETF_RESTCONF_QNAME, ERRORS_GROUPING_SCHEMA_NODE).intern();
        public static final QName ERRORS_CONTAINER_QNAME =
                QName.create(IETF_RESTCONF_QNAME, ERRORS_CONTAINER_SCHEMA_NODE).intern();
        public static final QName ERROR_LIST_QNAME = QName.create(IETF_RESTCONF_QNAME, ERROR_LIST_SCHEMA_NODE).intern();
        public static final QName ERROR_TYPE_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-type").intern();
        public static final QName ERROR_TAG_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-tag").intern();
        public static final QName ERROR_APP_TAG_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-app-tag").intern();
        public static final QName ERROR_MESSAGE_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-message").intern();
        public static final QName ERROR_INFO_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-info").intern();
        public static final QName ERROR_PATH_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-path").intern();
    }

    /**
     * Constants for ietf-yang-library model.
     *
     */
    public static final class IetfYangLibrary {
        private IetfYangLibrary() {
            throw new UnsupportedOperationException("Util class");
        }

        public static final QNameModule MODULE_QNAME = $YangModuleInfoImpl.getInstance().getName().getModule();
        public static final Revision REVISION = MODULE_QNAME.getRevision().orElseThrow();

        public static final QName MODULE_SET_ID_LEAF_QNAME = QName.create(MODULE_QNAME, "module-set-id").intern();

        public static final QName MODULE_QNAME_LIST = Module.QNAME;

        public static final String SPECIFIC_MODULE_NAME_LEAF = "name";
        public static final QName SPECIFIC_MODULE_NAME_LEAF_QNAME =
                QName.create(MODULE_QNAME, SPECIFIC_MODULE_NAME_LEAF).intern();

        public static final String SPECIFIC_MODULE_REVISION_LEAF = "revision";
        public static final QName SPECIFIC_MODULE_REVISION_LEAF_QNAME =
                QName.create(MODULE_QNAME, SPECIFIC_MODULE_REVISION_LEAF).intern();

        public static final String BASE_URI_OF_SCHEMA = "/modules/";
        public static final QName SPECIFIC_MODULE_SCHEMA_LEAF_QNAME = QName.create(MODULE_QNAME, "schema").intern();
        public static final QName SPECIFIC_MODULE_NAMESPACE_LEAF_QNAME =
                QName.create(MODULE_QNAME, "namespace").intern();

        public static final QName SPECIFIC_MODULE_FEATURE_LEAF_LIST_QNAME =
                QName.create(MODULE_QNAME, "feature").intern();

        public static final QName SPECIFIC_MODULE_DEVIATION_LIST_QNAME = Deviation.QNAME;

        public static final QName SPECIFIC_MODULE_CONFORMANCE_LEAF_QNAME =
                QName.create(MODULE_QNAME, "conformance-type").intern();

        public static final QName SPECIFIC_MODULE_SUBMODULE_LIST_QNAME = Submodule.QNAME;
    }

    /**
     * Constants for ietf-restconf-monitoring module.
     *
     */
    public static final class MonitoringModule {

        private MonitoringModule() {
            throw new UnsupportedOperationException("Util class");
        }

        public static final String NAME = "ietf-restconf-monitoring";
        public static final String NAMESPACE = "urn:ietf:params:xml:ns:yang:ietf-restconf-monitoring";
        public static final Revision REVISION = Revision.of("2017-01-26");
        public static final String PATH_TO_STREAM_WITHOUT_KEY =
                "ietf-restconf-monitoring:restconf-state/streams/stream=";
        public static final String PATH_TO_STREAMS = "ietf-restconf-monitoring:restconf-state/streams";

        public static final URI URI_MODULE = URI.create(NAMESPACE);

        public static final QNameModule MODULE_QNAME = QNameModule.create(URI_MODULE, REVISION).intern();

        public static final String CONT_RESTCONF_STATE_NAME = "restconf-state";
        public static final QName CONT_RESTCONF_STATE_QNAME = QName.create(MODULE_QNAME, CONT_RESTCONF_STATE_NAME)
                .intern();

        public static final String CONT_CAPABILITIES_NAME = "capabilities";
        public static final QName CONT_CAPABILITES_QNAME = QName.create(MODULE_QNAME, CONT_CAPABILITIES_NAME).intern();

        public static final String LEAF_LIST_CAPABILITY_NAME = "capability";
        public static final QName LEAF_LIST_CAPABILITY_QNAME = QName.create(MODULE_QNAME, LEAF_LIST_CAPABILITY_NAME)
                .intern();

        public static final String CONT_STREAMS_NAME = "streams";
        public static final QName CONT_STREAMS_QNAME = QName.create(MODULE_QNAME, CONT_STREAMS_NAME).intern();

        public static final String LIST_STREAM_NAME = "stream";
        public static final QName LIST_STREAM_QNAME = QName.create(MODULE_QNAME, LIST_STREAM_NAME).intern();

        public static final String LEAF_NAME_STREAM_NAME = "name";
        public static final QName LEAF_NAME_STREAM_QNAME = QName.create(MODULE_QNAME, LEAF_NAME_STREAM_NAME).intern();

        public static final String LEAF_DESCR_STREAM_NAME = "description";
        public static final QName LEAF_DESCR_STREAM_QNAME = QName.create(MODULE_QNAME, LEAF_DESCR_STREAM_NAME).intern();

        public static final String LEAF_REPLAY_SUPP_STREAM_NAME = "replay-support";
        public static final QName LEAF_REPLAY_SUPP_STREAM_QNAME =
                QName.create(MODULE_QNAME, LEAF_REPLAY_SUPP_STREAM_NAME).intern();

        public static final String LEAF_START_TIME_STREAM_NAME = "replay-log-creation-time";
        public static final QName LEAF_START_TIME_STREAM_QNAME =
                QName.create(MODULE_QNAME, LEAF_START_TIME_STREAM_NAME).intern();

        public static final String LIST_ACCESS_STREAM_NAME = "access";
        public static final QName LIST_ACCESS_STREAM_QNAME = QName.create(MODULE_QNAME, LIST_ACCESS_STREAM_NAME)
                .intern();

        public static final String LEAF_ENCODING_ACCESS_NAME = "encoding";
        public static final QName LEAF_ENCODING_ACCESS_QNAME = QName.create(MODULE_QNAME, LEAF_ENCODING_ACCESS_NAME)
                .intern();

        public static final String LEAF_LOCATION_ACCESS_NAME = "location";
        public static final QName LEAF_LOCATION_ACCESS_QNAME = QName.create(MODULE_QNAME, LEAF_LOCATION_ACCESS_NAME)
                .intern();

        /**
         * Constants for capabilities.
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
