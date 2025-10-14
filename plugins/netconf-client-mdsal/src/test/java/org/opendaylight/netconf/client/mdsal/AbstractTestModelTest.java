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
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.spi.source.StringYangTextSource;
import org.opendaylight.yangtools.yang.model.spi.source.URLYangTextSource;
import org.opendaylight.yangtools.yang.parser.api.YangParserConfiguration;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@NonNullByDefault
public abstract class AbstractTestModelTest extends AbstractBaseSchemasTest {
    protected static final EffectiveModelContext TEST_MODEL = YangParserTestUtils.parseYangSources(
        YangParserConfiguration.DEFAULT, null, new StringYangTextSource(
            new SourceIdentifier("test-module", Revision.of("2013-07-22")),
            """
                module test-module {
                  yang-version 1;
                  namespace "test:namespace";
                  prefix "tt";

                  description "Types for testing";

                  revision "2013-07-22";

                  container c {
                    leaf a {
                      type string;
                    }

                    leaf b {
                      type string;
                    }

                    container d {
                      leaf x {
                        type boolean;
                      }
                    }
                  }

                  container e {
                    leaf z {
                      type uint8;
                    }
                  }
                }""", "test-module"),
        new URLYangTextSource(AbstractTestModelTest.class.getResource(
            "/META-INF/yang/ietf-netconf@2011-06-01.yang")),
        new URLYangTextSource(AbstractTestModelTest.class.getResource(
            "/META-INF/yang/ietf-inet-types@2013-07-15.yang"))
    );

    protected static final DatabindContext TEST_DATABIND = DatabindContext.ofModel(TEST_MODEL);
    protected static final ListenableFuture<EffectiveModelContext> TEST_MODEL_FUTURE =
        Futures.immediateFuture(TEST_MODEL);
}
