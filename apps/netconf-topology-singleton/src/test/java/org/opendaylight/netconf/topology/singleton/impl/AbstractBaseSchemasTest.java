/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemaProvider;
import org.opendaylight.yangtools.dagger.yang.parser.DaggerDefaultYangParserComponent;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;

public abstract class AbstractBaseSchemasTest {
    protected static final @NonNull YangParserFactory PARSER_FACTORY =
        DaggerDefaultYangParserComponent.create().parserFactory();
    protected static final @NonNull BaseNetconfSchemaProvider BASE_SCHEMAS =
        new DefaultBaseNetconfSchemaProvider(PARSER_FACTORY);
}
