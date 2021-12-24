/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import org.junit.Test;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class NC845Test {
    @Test
    public void testIdentityInstanceIdentifier() {
        final var context = YangParserTestUtils.parseYangResourceDirectory("/nc845");
        final var resultHolder = new NormalizedNodeResult();
        final var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

        final var jsonParser = JsonParserStream.create(writer,
            JSONCodecFactorySupplier.RFC7951.getShared(context),
            SchemaInferenceStack.ofDataTreePath(context, QName.create("foo", "xyzzy")).toInference());
        jsonParser.parse(new JsonReader(new StringReader("{ \"xyzzy\": \"/foo:bar[baz='foo']\" }")));

        final var result = resultHolder.getResult();
        assertThat(result, instanceOf(LeafNode.class));
        assertEquals(YangInstanceIdentifier.create(
            new NodeIdentifier(QName.create("foo", "bar")),
            NodeIdentifierWithPredicates.of(QName.create("foo", "bar"),
                QName.create("foo", "baz"), QName.create("foo", "foo"))),
            ((LeafNode<?>) result).body());

        // FIXME: so far so good, so who's mis-converting the result?
    }
}
