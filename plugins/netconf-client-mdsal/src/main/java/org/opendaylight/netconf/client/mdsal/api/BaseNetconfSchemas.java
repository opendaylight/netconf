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
import org.opendaylight.netconf.client.mdsal.impl.BaseSchema;

@Beta
@NonNullByDefault
// FIXME: implements Immutable once we do not use Blueprint, or ARIES-2078 is fixed
public interface BaseNetconfSchemas {

    BaseSchema getBaseSchema();

    BaseSchema getBaseSchemaWithNotifications();
}
