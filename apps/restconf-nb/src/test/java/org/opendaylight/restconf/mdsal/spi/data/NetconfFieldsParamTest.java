/*
 * Copyright © 2020 FRINX s.r.o. and others.  All rights reserved.
 * Copyright © 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.client.mdsal.spi.NetconfRestconfStrategy;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Unit test for {@link NetconfFieldsParam}.
 */
@ExtendWith(MockitoExtension.class)
class NetconfFieldsParamTest extends AbstractFieldsTranslatorTest<YangInstanceIdentifier> {
    @Override
    protected List<YangInstanceIdentifier> translateFields(final EffectiveModelContext modelContext,
            final DataSchemaContext startNode, final FieldsParam fields) throws RequestException {
        return NetconfRestconfStrategy.fieldsParamToPaths(modelContext, startNode, fields);
    }

    @Override
    protected void assertSimplePath(final List<YangInstanceIdentifier> result) {
        assertEquals(1, result.size());
        final var pathArguments = result.get(0).getPathArguments();
        assertEquals(1, pathArguments.size());
        assertEquals(LIBRARY_QNAME, pathArguments.get(0).getNodeType());
    }

    @Override
    protected void assertKeyedList(final List<YangInstanceIdentifier> result) {
        assertEquals(1, result.size());
    }

    @Override
    protected void assertDoublePath(final List<YangInstanceIdentifier> result) {
        assertEquals(2, result.size());

        final var libraryPath = assertPath(result, LIBRARY_QNAME);
        assertEquals(1, libraryPath.getPathArguments().size());

        final var playerPath = assertPath(result, PLAYER_QNAME);
        assertEquals(1, playerPath.getPathArguments().size());
    }

    @Override
    protected void assertSubPath(final List<YangInstanceIdentifier> result) {
        assertEquals(1, result.size());
        final var pathArguments = result.get(0).getPathArguments();
        assertEquals(6, pathArguments.size());
        assertEquals(LIBRARY_QNAME, pathArguments.get(0).getNodeType());
        assertEquals(ARTIST_QNAME, pathArguments.get(1).getNodeType());
        assertEquals(ARTIST_QNAME, pathArguments.get(2).getNodeType());
        assertEquals(ALBUM_QNAME, pathArguments.get(3).getNodeType());
        assertEquals(ALBUM_QNAME, pathArguments.get(4).getNodeType());
        assertEquals(NAME_QNAME, pathArguments.get(5).getNodeType());
    }

    @Override
    protected void assertChildrenPath(final List<YangInstanceIdentifier> result) {
        assertEquals(1, result.size());
        final var pathArguments = result.get(0).getPathArguments();
        assertEquals(6, pathArguments.size());
        assertEquals(LIBRARY_QNAME, pathArguments.get(0).getNodeType());
        assertEquals(ARTIST_QNAME, pathArguments.get(1).getNodeType());
        assertEquals(ARTIST_QNAME, pathArguments.get(2).getNodeType());
        assertEquals(ALBUM_QNAME, pathArguments.get(3).getNodeType());
        assertEquals(ALBUM_QNAME, pathArguments.get(4).getNodeType());
        assertEquals(NAME_QNAME, pathArguments.get(5).getNodeType());
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
        assertEquals(3, tosPath.getPathArguments().size());

        final var instanceNamePath = assertPath(result, INSTANCE_NAME_Q_NAME);
        assertEquals(5, instanceNamePath.getPathArguments().size());

        final var providerPath = assertPath(result, PROVIDER_Q_NAME);
        assertEquals(5, providerPath.getPathArguments().size());
    }

    @Override
    protected void assertMultipleChildren2(final List<YangInstanceIdentifier> result) {
        assertEquals(3, result.size());

        final var tosPath = assertPath(result, TYPE_OF_SERVICE_Q_NAME);
        assertEquals(3, tosPath.getPathArguments().size());

        final var instanceNamePath = assertPath(result, INSTANCE_NAME_Q_NAME);
        assertEquals(5, instanceNamePath.getPathArguments().size());

        final var providerPath = assertPath(result, PROVIDER_Q_NAME);
        assertEquals(5, providerPath.getPathArguments().size());
    }

    @Override
    protected void assertMultipleChildren3(final List<YangInstanceIdentifier> result) {
        assertEquals(3, result.size());

        final var instanceNamePath = assertPath(result, INSTANCE_NAME_Q_NAME);
        assertEquals(5, instanceNamePath.getPathArguments().size());

        final var tosPath = assertPath(result, TYPE_OF_SERVICE_Q_NAME);
        assertEquals(3, tosPath.getPathArguments().size());

        final var nextServicePath = assertPath(result, NEXT_SERVICE_Q_NAME);
        assertEquals(4, nextServicePath.getPathArguments().size());
    }

    @Override
    protected void assertMultipleChildren4(final List<YangInstanceIdentifier> result) {
        assertEquals(4, result.size());

        final var instanceNamePath = assertPath(result, INSTANCE_NAME_Q_NAME);
        assertEquals(5, instanceNamePath.getPathArguments().size());

        final var tosPath = assertPath(result, TYPE_OF_SERVICE_Q_NAME);
        assertEquals(3, tosPath.getPathArguments().size());

        final var nextServicePath = assertPath(result, NEXT_SERVICE_Q_NAME);
        assertEquals(4, nextServicePath.getPathArguments().size());

        final var providerPath = assertPath(result, PROVIDER_Q_NAME);
        assertEquals(5, providerPath.getPathArguments().size());
    }

    @Override
    protected void assertMultipleChildren5(final List<YangInstanceIdentifier> result) {
        assertEquals(4, result.size());

        final var instanceNamePath = assertPath(result, INSTANCE_NAME_Q_NAME);
        assertEquals(5, instanceNamePath.getPathArguments().size());

        final var tosPath = assertPath(result, TYPE_OF_SERVICE_Q_NAME);
        assertEquals(3, tosPath.getPathArguments().size());

        final var nextServicePath = assertPath(result, NEXT_SERVICE_Q_NAME);
        assertEquals(4, nextServicePath.getPathArguments().size());

        final var providerPath = assertPath(result, PROVIDER_Q_NAME);
        assertEquals(5, providerPath.getPathArguments().size());
    }

    @Override
    protected void assertAugmentedChild(final List<YangInstanceIdentifier> result) {
        assertEquals(1, result.size());
        final var pathArguments = result.get(0).getPathArguments();

        assertEquals(2, pathArguments.size());
        assertEquals(PLAYER_QNAME, pathArguments.get(0).getNodeType());
        assertEquals(SPEED_Q_NAME, pathArguments.get(1).getNodeType());
    }

    @Override
    protected void assertListFieldUnderList(final List<YangInstanceIdentifier> result) {
        assertEquals(1, result.size());
        assertEquals(List.of(
            NodeIdentifier.create(SERVICES_Q_NAME),
            NodeIdentifierWithPredicates.of(SERVICES_Q_NAME),
            NodeIdentifier.create(INSTANCE_Q_NAME),
            NodeIdentifierWithPredicates.of(INSTANCE_Q_NAME)),
            result.get(0).getPathArguments());
    }

    @Override
    protected void assertLeafList(final List<YangInstanceIdentifier> parsedFields) {
        assertEquals(1, parsedFields.size());
        assertEquals(List.of(new NodeIdentifier(PROTOCOLS_Q_NAME)), parsedFields.get(0).getPathArguments());
    }

    @Override
    protected void assertDuplicateNodes1(final List<YangInstanceIdentifier> parsedFields) {
        assertEquals(4, parsedFields.size());
        assertEquals(
            Set.of(List.of(NodeIdentifier.create(BAR_Q_NAME), NodeIdentifier.create(ALPHA_Q_NAME)),
                List.of(NodeIdentifier.create(BAR_Q_NAME), NodeIdentifier.create(BETA_Q_NAME),
                    NodeIdentifier.create(GAMMA_Q_NAME)),
                List.of(NodeIdentifier.create(BAZ_Q_NAME), NodeIdentifierWithPredicates.of(BAZ_Q_NAME),
                    NodeIdentifier.create(ALPHA_Q_NAME)),
                List.of(NodeIdentifier.create(BAZ_Q_NAME), NodeIdentifierWithPredicates.of(BAZ_Q_NAME),
                    NodeIdentifier.create(BETA_Q_NAME), NodeIdentifier.create(GAMMA_Q_NAME))),
            parsedFields.stream().map(YangInstanceIdentifier::getPathArguments).collect(Collectors.toSet()));
    }

    @Override
    protected void assertDuplicateNodes2(final List<YangInstanceIdentifier> parsedFields) {
        assertEquals(4, parsedFields.size());
        assertEquals(
            Set.of(List.of(NodeIdentifier.create(BAR_Q_NAME), NodeIdentifier.create(ALPHA_Q_NAME)),
                List.of(NodeIdentifier.create(BAR_Q_NAME), NodeIdentifier.create(BETA_Q_NAME),
                    NodeIdentifier.create(DELTA_Q_NAME)),
                List.of(NodeIdentifier.create(BAZ_Q_NAME), NodeIdentifierWithPredicates.of(BAZ_Q_NAME),
                    NodeIdentifier.create(ALPHA_Q_NAME)),
                List.of(NodeIdentifier.create(BAZ_Q_NAME), NodeIdentifierWithPredicates.of(BAZ_Q_NAME),
                    NodeIdentifier.create(BETA_Q_NAME), NodeIdentifier.create(EPSILON_Q_NAME))),
            parsedFields.stream().map(YangInstanceIdentifier::getPathArguments).collect(Collectors.toSet()));
    }

    private static YangInstanceIdentifier assertPath(final List<YangInstanceIdentifier> paths, final QName lastArg) {
        return paths.stream()
            .filter(path -> lastArg.equals(path.getLastPathArgument().getNodeType()))
            .findAny()
            .orElseThrow(() -> new AssertionError("Path ending with " + lastArg + " not found"));
    }
}
