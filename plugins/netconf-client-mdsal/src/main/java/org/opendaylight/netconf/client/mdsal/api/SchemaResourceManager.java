/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.client.mdsal.NetconfDevice.SchemaResourcesDTO;

@Beta
@NonNullByDefault
public interface SchemaResourceManager {

    // FIXME: document this, nodeId is not quite appropriate name here: it should be a @NonNull id with .toString()
    //        being interesting
    // FIXME: subDirectory should have be really String..., placing the onus of splitting the directory to callers,
    //        so we do not get separator ambiguity
    SchemaResourcesDTO getSchemaResources(String subDirectory, Object nodeId);
}
