/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

@Beta
@NonNullByDefault
public interface LibraryModulesSchemaFactory {

    LibraryModulesSchemas createLibraryModulesSchema(NetconfDeviceRpc deviceRpc, RemoteDeviceId deviceId);

    /**
     * Resolves URLs with YANG schema resources from modules-state.
     *
     * @param url URL pointing to yang library
     * @return Resolved URLs with YANG schema resources for all yang modules from yang library
     */
    LibraryModulesSchemas createLibraryModulesSchema(String url);

    /**
     * Resolves URLs with YANG schema resources from modules-state. Uses basic http authentication.
     *
     * @param url URL pointing to yang library
     * @param username User name for basic authentication
     * @param password Password for basic authentication
     * @return Resolved URLs with YANG schema resources for all yang modules from yang library
     */
    LibraryModulesSchemas createLibraryModulesSchema(String url, String username, String password);
}
