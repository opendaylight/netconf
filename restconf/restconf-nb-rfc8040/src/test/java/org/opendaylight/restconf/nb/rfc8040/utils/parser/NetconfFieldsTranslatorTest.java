/*
 * Copyright © 2020 FRINX s.r.o. and others.  All rights reserved.
 * Copyright © 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.FieldsParam;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;

/**
 * Unit test for {@link NetconfFieldsTranslator}.
 */
@RunWith(MockitoJUnitRunner.class)
public class NetconfFieldsTranslatorTest extends AbstractFieldsTranslatorTest<YangInstanceIdentifier> {
    @Override
    protected List<YangInstanceIdentifier> translateFields(final InstanceIdentifierContext<?> context,
            final FieldsParam fields) {
        return NetconfFieldsTranslator.translate(context, fields);
    }

    @Override
    protected void assertSimplePath(final List<YangInstanceIdentifier> result) {
        assertEquals(1, result.size());
        final var pathArguments = result.get(0).getPathArguments();
        assertEquals(1, pathArguments.size());
        assertEquals(LIBRARY_Q_NAME, pathArguments.get(0).getNodeType());
    }

    @Override
    protected void assertKeyedList(List<YangInstanceIdentifier> result) {
        assertEquals(1, result.size());
    }

    @Override
    protected void assertDoublePath(final List<YangInstanceIdentifier> result) {
        assertEquals(2, result.size());

        final var libraryPath = assertPath(result, LIBRARY_Q_NAME);
        assertEquals(1, libraryPath.getPathArguments().size());

        final var playerPath = assertPath(result, PLAYER_Q_NAME);
        assertEquals(1, playerPath.getPathArguments().size());
    }

    @Override
    protected void assertSubPath(final List<YangInstanceIdentifier> result) {
        assertEquals(1, result.size());
        final var pathArguments = result.get(0).getPathArguments();
        assertEquals(3, pathArguments.size());
        assertEquals(LIBRARY_Q_NAME, pathArguments.get(0).getNodeType());
        assertEquals(ALBUM_Q_NAME, pathArguments.get(1).getNodeType());
        assertEquals(NAME_Q_NAME, pathArguments.get(2).getNodeType());
    }

    @Override
    protected void assertChildrenPath(final List<YangInstanceIdentifier> result) {
        assertEquals(1, result.size());
        final var pathArguments = result.get(0).getPathArguments();
        assertEquals(3, pathArguments.size());
        assertEquals(LIBRARY_Q_NAME, pathArguments.get(0).getNodeType());
        assertEquals(ALBUM_Q_NAME, pathArguments.get(1).getNodeType());
        assertEquals(NAME_Q_NAME, pathArguments.get(2).getNodeType());
    }

    @Override
    protected void assertNamespace(final List<YangInstanceIdentifier> result) {
        assertEquals(1, result.size());
        final var augmentedLibraryPath = assertPath(result, AUGMENTED_LIBRARY_Q_NAME);
        assertEquals(1, augmentedLibraryPath.getPathArguments().size());
    }

    @Override
    protected void assertMultipleChildren1(final List<YangInstanceIdentifier> result) {
        assertEquals(3, result.size());

        final var tosPath = assertPath(result, TYPE_OF_SERVICE_Q_NAME);
        assertEquals(2, tosPath.getPathArguments().size());

        final var instanceNamePath = assertPath(result, INSTANCE_NAME_Q_NAME);
        assertEquals(3, instanceNamePath.getPathArguments().size());

        final var providerPath = assertPath(result, PROVIDER_Q_NAME);
        assertEquals(3, providerPath.getPathArguments().size());
    }

    @Override
    protected void assertMultipleChildren2(final List<YangInstanceIdentifier> result) {
        assertEquals(3, result.size());

        final var tosPath = assertPath(result, TYPE_OF_SERVICE_Q_NAME);
        assertEquals(2, tosPath.getPathArguments().size());

        final var instanceNamePath = assertPath(result, INSTANCE_NAME_Q_NAME);
        assertEquals(3, instanceNamePath.getPathArguments().size());

        final var providerPath = assertPath(result, PROVIDER_Q_NAME);
        assertEquals(3, providerPath.getPathArguments().size());
    }

    @Override
    protected void assertMultipleChildren3(final List<YangInstanceIdentifier> result) {
        assertEquals(3, result.size());

        final var instanceNamePath = assertPath(result, INSTANCE_NAME_Q_NAME);
        assertEquals(3, instanceNamePath.getPathArguments().size());

        final var tosPath = assertPath(result, TYPE_OF_SERVICE_Q_NAME);
        assertEquals(2, tosPath.getPathArguments().size());

        final var nextServicePath = assertPath(result, NEXT_SERVICE_Q_NAME);
        assertEquals(3, nextServicePath.getPathArguments().size());
    }

    @Override
    protected void assertAugmentedChild(final List<YangInstanceIdentifier> result) {
        assertEquals(1, result.size());
        final var pathArguments = result.get(0).getPathArguments();

        assertEquals(3, pathArguments.size());
        assertEquals(PLAYER_Q_NAME, pathArguments.get(0).getNodeType());
        assertThat(pathArguments.get(1), instanceOf(AugmentationIdentifier.class));
        assertEquals(SPEED_Q_NAME, pathArguments.get(2).getNodeType());
    }

    @Override
    protected void assertListFieldUnderList(final List<YangInstanceIdentifier> result) {
        assertEquals(1, result.size());
        assertEquals(List.of(new NodeIdentifier(SERVICES_Q_NAME), new NodeIdentifier(INSTANCE_Q_NAME)),
            result.get(0).getPathArguments());
    }

    @Override
    protected void assertLeafList(final List<YangInstanceIdentifier> parsedFields) {
        assertEquals(1, parsedFields.size());
        assertEquals(List.of(new NodeIdentifier(PROTOCOLS_Q_NAME)), parsedFields.get(0).getPathArguments());
    }

    private static YangInstanceIdentifier assertPath(final List<YangInstanceIdentifier> paths, final QName lastArg) {
        return paths.stream()
            .filter(path -> lastArg.equals(path.getLastPathArgument().getNodeType()))
            .findAny()
            .orElseThrow(() -> new AssertionError("Path ending with " + lastArg + " not found"));
    }
}
