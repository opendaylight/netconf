/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors;

import javassist.ClassPool;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.mdsal.binding.dom.adapter.BindingToNormalizedNodeCodec;
import org.opendaylight.mdsal.binding.dom.codec.gen.impl.DataObjectSerializerGenerator;
import org.opendaylight.mdsal.binding.dom.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.mdsal.binding.dom.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.mdsal.binding.generator.impl.GeneratedClassLoadingStrategy;
import org.opendaylight.mdsal.binding.generator.util.JavassistUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class RestconfDocumentedExceptionMapperTest {

    private static RestconfDocumentedExceptionMapper restconfDocumentedExceptionMapper;

    @BeforeClass
    public static void setupExceptionMapper() {
        final SchemaContext schemaContext = YangParserTestUtils.parseYangResources(
                RestconfDocumentedExceptionMapperTest.class, "/restconf/impl/ietf-restconf@2017-01-26.yang");
        final SchemaContextHandler schemaContextHandler = Mockito.mock(SchemaContextHandler.class);
        Mockito.when(schemaContextHandler.get()).thenReturn(schemaContext);

        final DataObjectSerializerGenerator streamWriter = StreamWriterGenerator.create(JavassistUtils.forClassPool(
                ClassPool.getDefault()));
        final BindingNormalizedNodeCodecRegistry registry = new BindingNormalizedNodeCodecRegistry(streamWriter);
        final BindingToNormalizedNodeCodec bindingToNormalizedNodeCodec = new BindingToNormalizedNodeCodec(
                GeneratedClassLoadingStrategy.getTCCLClassLoadingStrategy(), registry, true);
        bindingToNormalizedNodeCodec.onGlobalContextUpdated(schemaContext);

        restconfDocumentedExceptionMapper = new RestconfDocumentedExceptionMapper(
                bindingToNormalizedNodeCodec, schemaContextHandler);
    }

    @Test
    public void test() {
        
    }
}