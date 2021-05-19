/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.RestconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.module.Deviation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.module.Submodule;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;

/**
 * Common constants defined and relating to RFC8040.
 */
public final class Rfc8040 {
    private static final YangInstanceIdentifier RESTCONF_STATE_STREAMS = YangInstanceIdentifier.create(
        NodeIdentifier.create(RestconfState.QNAME), NodeIdentifier.create(Streams.QNAME));
    private static final QName STREAM_QNAME = QName.create(Streams.QNAME, "stream").intern();

    private Rfc8040() {
        // Hidden on purpose
    }

    @Beta
    // FIXME: move this method somewhere else
    public static @NonNull YangInstanceIdentifier restconfStateStreamPath(final String streamName) {
        return restconfStateStreamPath(NodeIdentifierWithPredicates.of(Streams.QNAME, STREAM_QNAME, streamName));
    }

    @Beta
    // FIXME: move this method somewhere else
    public static @NonNull YangInstanceIdentifier restconfStateStreamPath(final NodeIdentifierWithPredicates arg) {
        return RESTCONF_STATE_STREAMS.node(arg);
    }

    /**
     * Constants for <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-11.4">RESTCONF Capability URNs</a>.
     */
    public static final class Capabilities {
        /**
         * Support for <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.2">depth</a> Query Parameter.
         */
        public static final String DEPTH = "urn:ietf:params:restconf:capability:depth:1.0";
        /**
         * Support for <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.3">fields</a> Query Parameter.
         */
        public static final String FIELDS = "urn:ietf:params:restconf:capability:fields:1.0";
        /**
         * Support for <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.4">filter</a> Query Parameter.
         */
        public static final String FILTER = "urn:ietf:params:restconf:capability:filter:1.0";
        /**
         * Support for <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.7">start-time</a>
         * and <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.7">stop-time</a> Query Parameters.
         */
        public static final String REPLAY = "urn:ietf:params:restconf:capability:replay:1.0";
        /**
         * Support for
         * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.9">with-defaults</a> Query Parameter.
         */
        public static final String WITH_DEFAULTS = "urn:ietf:params:restconf:capability:with-defaults:1.0";

        private Capabilities() {
            // Hidden on purpose
        }
    }

    /**
     * Constants for ietf-yang-library model.
     */
    // FIXME: split this out
    public static final class IetfYangLibrary {
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

        private IetfYangLibrary() {
            // Hidden on purpose
        }
    }
}
