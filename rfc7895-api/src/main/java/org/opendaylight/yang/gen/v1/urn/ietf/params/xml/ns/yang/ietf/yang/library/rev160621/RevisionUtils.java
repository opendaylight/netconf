/*
 * Copyright (c) 2018 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621;

import com.google.common.annotations.Beta;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.CommonLeafs.Revision;

/**
 * Utility methods for working with {@link Revision}s.
 *
 * @author Robert Varga
 */
@Beta
@NonNullByDefault
public final class RevisionUtils {
    private static final Revision EMPTY_REVISION = new Revision("");

    private RevisionUtils() {

    }

    /**
     * Return an empty {@link Revision}.
     *
     * @return An empty Revision.
     */
    public static Revision emptyRevision() {
        return EMPTY_REVISION;
    }

    /**
     * Create a {@link Revision} from an optional {@link org.opendaylight.yangtools.yang.common.Revision}.
     *
     * @param revision Optional {@link org.opendaylight.yangtools.yang.common.Revision}
     * @return A Revision
     * @throws NullPointerException if revision is null
     */
    public static Revision fromYangCommon(final Optional<org.opendaylight.yangtools.yang.common.Revision> revision) {
        return revision.map(rev -> new Revision(new RevisionIdentifier(rev.toString()))).orElse(EMPTY_REVISION);
    }

    /**
     * Create an optional {@link org.opendaylight.yangtools.yang.common.Revision} from a {@link Revision}.
     *
     * @param revision A Revision
     * @return Optional {@link org.opendaylight.yangtools.yang.common.Revision}
     * @throws NullPointerException if revision is null
     */
    public static Optional<org.opendaylight.yangtools.yang.common.Revision> toYangCommon(final Revision revision) {
        final @Nullable RevisionIdentifier id = revision.getRevisionIdentifier();
        return id != null ? Optional.of(org.opendaylight.yangtools.yang.common.Revision.of(id.getValue()))
                : Optional.empty();
    }
}
