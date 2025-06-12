/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@NonNullByDefault
public abstract class AbstractTestModelTest extends AbstractBaseSchemasTest {
    protected static final EffectiveModelContext TEST_MODEL =
        YangParserTestUtils.parseYangResources(AbstractTestModelTest.class,"/schemas/test-module.yang",
            "/schemas/ietf-netconf@2011-06-01.yang", "/schemas/ietf-inet-types@2010-09-24.yang");
    protected static final DatabindContext TEST_DATABIND = DatabindContext.ofModel(TEST_MODEL);
    protected static final ListenableFuture<EffectiveModelContext> TEST_MODEL_FUTURE =
        Futures.immediateFuture(TEST_MODEL);
}
