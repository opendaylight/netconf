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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module;
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
    private static final YangInstanceIdentifier RESTCONF_STATE_STREAMS = YangInstanceIdentifier.of(
        NodeIdentifier.create(RestconfState.QNAME), NodeIdentifier.create(Streams.QNAME),
        NodeIdentifier.create(Stream.QNAME));
    private static final QName NAME_QNAME = QName.create(Stream.QNAME, "name").intern();

    private Rfc8040() {
        // Hidden on purpose
    }

    @Beta
    // FIXME: move this method somewhere else
    public static @NonNull YangInstanceIdentifier restconfStateStreamPath(final String streamName) {
        return restconfStateStreamPath(NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, streamName));
    }

    @Beta
    // FIXME: move this method somewhere else
    public static @NonNull YangInstanceIdentifier restconfStateStreamPath(final NodeIdentifierWithPredicates arg) {
        return RESTCONF_STATE_STREAMS.node(arg);
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

        private IetfYangLibrary() {
            // Hidden on purpose
        }
    }
}
