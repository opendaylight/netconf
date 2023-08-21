/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import java.util.Optional;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.yanglib.writer.YangLibrarySchemaSourceUrlProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yangtools.yang.common.Revision;
import org.osgi.service.component.annotations.Component;

/**
 * Component composing schema source URL value on per yang resource basis.
 *
 * <p>The URL is expected to be requested by {@link org.opendaylight.netconf.yanglib.writer.YangLibraryWriter
 * YangLibraryWriter} when yang-library data is constructed. The resource download by URL composed is handled by
 * {@link org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfSchemaService RestconfSchemaService}
 */
@Singleton
@Component(immediate = true, service = YangLibrarySchemaSourceUrlProvider.class)
public class RestconfSchemaSourceUrlProvider implements YangLibrarySchemaSourceUrlProvider {

    @Override
    public Optional<Uri> getSchemaSourceUrl(final @NonNull String moduleSetName,
        final @NonNull String moduleName, @Nullable Revision revision) {
        final var sb = new StringBuilder("/rests/modules/").append(moduleName);
        if (revision != null) {
            sb.append("/").append(revision);
        }
        return Optional.of(new Uri(sb.toString()));
    }
}


