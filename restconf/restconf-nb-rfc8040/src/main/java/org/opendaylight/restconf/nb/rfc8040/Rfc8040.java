/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import java.net.URI;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.module.Deviation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.module.Submodule;
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
        // Hidden on purpose
    }

    /**
     * Set of application specific media types to identify each of the available resource types.
     */
    public static final class MediaTypes {
        private MediaTypes() {
            // Hidden on purpose
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
        public static final QName RESTCONF_GROUPING_QNAME = QName.create(IETF_RESTCONF_QNAME, "restconf").intern();
        public static final QName RESTCONF_CONTAINER_QNAME = QName.create(IETF_RESTCONF_QNAME, "restconf").intern();
        public static final QName LIB_VER_LEAF_QNAME = QName.create(IETF_RESTCONF_QNAME, "yang-library-version")
                .intern();

        // ERRORS
        public static final QName ERRORS_GROUPING_QNAME = QName.create(IETF_RESTCONF_QNAME, "errors").intern();
        public static final QName ERRORS_CONTAINER_QNAME = QName.create(IETF_RESTCONF_QNAME, "errors").intern();
        public static final QName ERROR_LIST_QNAME = QName.create(IETF_RESTCONF_QNAME, "error").intern();
        public static final QName ERROR_TYPE_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-type").intern();
        public static final QName ERROR_TAG_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-tag").intern();
        public static final QName ERROR_APP_TAG_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-app-tag").intern();
        public static final QName ERROR_MESSAGE_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-message").intern();
        public static final QName ERROR_INFO_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-info").intern();
        public static final QName ERROR_PATH_QNAME = QName.create(IETF_RESTCONF_QNAME, "error-path").intern();
    }

    /**
     * Constants for ietf-yang-library model.
     */
    public static final class IetfYangLibrary {
        private IetfYangLibrary() {
            // Hidden on purpose
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
     */
    public static final class MonitoringModule {
        private MonitoringModule() {
            // Hidden on purpose
        }

        public static final String NAME = "ietf-restconf-monitoring";
        public static final String NAMESPACE = "urn:ietf:params:xml:ns:yang:ietf-restconf-monitoring";
        public static final Revision REVISION = Revision.of("2017-01-26");
        public static final String PATH_TO_STREAM_WITHOUT_KEY =
                "ietf-restconf-monitoring:restconf-state/streams/stream=";

        public static final URI URI_MODULE = URI.create(NAMESPACE);

        public static final QNameModule MODULE_QNAME = QNameModule.create(URI_MODULE, REVISION).intern();

        public static final QName CONT_RESTCONF_STATE_QNAME = QName.create(MODULE_QNAME, "restconf-state").intern();

        public static final QName CONT_CAPABILITES_QNAME = QName.create(MODULE_QNAME, "capabilities").intern();

        public static final QName LEAF_LIST_CAPABILITY_QNAME = QName.create(MODULE_QNAME, "capability").intern();

        public static final QName CONT_STREAMS_QNAME = QName.create(MODULE_QNAME, "streams").intern();

        public static final QName LIST_STREAM_QNAME = QName.create(MODULE_QNAME, "stream").intern();

        public static final QName LEAF_NAME_STREAM_QNAME = QName.create(MODULE_QNAME, "name").intern();

        public static final QName LEAF_DESCR_STREAM_QNAME = QName.create(MODULE_QNAME, "description").intern();

        public static final QName LEAF_REPLAY_SUPP_STREAM_QNAME = QName.create(MODULE_QNAME, "replay-support").intern();

        public static final QName LEAF_START_TIME_STREAM_QNAME = QName.create(MODULE_QNAME, "replay-log-creation-time")
            .intern();

        public static final QName LIST_ACCESS_STREAM_QNAME = QName.create(MODULE_QNAME, "access").intern();

        public static final QName LEAF_ENCODING_ACCESS_QNAME = QName.create(MODULE_QNAME, "encoding").intern();

        public static final QName LEAF_LOCATION_ACCESS_QNAME = QName.create(MODULE_QNAME, "location").intern();

        /**
         * Constants for capabilities.
         */
        public static final class QueryParams {
            private QueryParams() {
                // Hidden on purpose
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
