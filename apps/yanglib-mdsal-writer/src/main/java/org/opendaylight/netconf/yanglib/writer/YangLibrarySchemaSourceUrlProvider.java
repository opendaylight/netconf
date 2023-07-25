/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.yanglib.writer;

import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yangtools.yang.model.api.ModuleLike;

/**
 * The service providing URLs to yang schema sources.
 */
// FIXME: current interface is a part of integration with YangLibrarySupport and expected
//  to be removed once the similar interface is implemented there.
//  Addresses https://jira.opendaylight.org/browse/MDSAL-833
public interface YangLibrarySchemaSourceUrlProvider {

    /**
     * Provides yang schema source URL where it can be downloaded from.
     *
     * @param moduleSetName the module set name the requested resource belongs to
     * @param module referenced module or submodule object
     *
     * @return optional of URL to requested resource
     */
    Optional<Uri> getSchemaSourceUrl(@NonNull String moduleSetName, @NonNull ModuleLike module);
}

