/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemaProvider;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;

public abstract class AbstractBaseSchemasTest {
    protected static final BaseNetconfSchemaProvider BASE_SCHEMAS =
        new DefaultBaseNetconfSchemaProvider(new DefaultYangParserFactory());
}
