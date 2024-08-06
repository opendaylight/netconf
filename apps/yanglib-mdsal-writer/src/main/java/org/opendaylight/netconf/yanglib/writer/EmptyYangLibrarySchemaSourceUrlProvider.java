/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.yanglib.writer;

import com.google.common.base.MoreObjects;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yangtools.yang.common.Revision;

@NonNullByDefault
final class EmptyYangLibrarySchemaSourceUrlProvider implements YangLibrarySchemaSourceUrlProvider {
    static final YangLibrarySchemaSourceUrlProvider INSTANCE = new EmptyYangLibrarySchemaSourceUrlProvider();

    private EmptyYangLibrarySchemaSourceUrlProvider() {
        // Hidden on purpose
    }

    @Override
    public Set<Uri> getSchemaSourceUrl(final String moduleSetName, final String moduleName,
            final @Nullable Revision revision) {
        return Set.of();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).toString();
    }
}
