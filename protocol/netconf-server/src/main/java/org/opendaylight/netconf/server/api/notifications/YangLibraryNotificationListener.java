/*
 * Copyright (c) 2020 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.notifications;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibraryUpdate;

/**
 * Listener for "yang-library-change" notification defined in https://tools.ietf.org/html/rfc8525.
 * This listener uses generated classes from yang model defined in RFC7895.
 */
public interface YangLibraryNotificationListener {
    /**
     * Callback used to notify about a change in the set of modules and submodules supported by the server.
     *
     * @deprecated ietf-yang-library:yang-library-change was deprecated in the RFC8525.
     *             Use {@link YangLibraryNotificationListener#onYangLibraryUpdate(YangLibraryUpdate)}.
     */
    @Deprecated(forRemoval = true)
    void onYangLibraryChange(YangLibraryChange yangLibraryChange);

    /**
     * Callback generated when any YANG library information on the server has changed.
     */
    void onYangLibraryUpdate(YangLibraryUpdate yangLibraryUpdate);
}
