/*
 * Copyright (c) 2015 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.impl;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassNotFoundExceptionMapper implements ExceptionMapper<ClassNotFoundException>{

  private static Logger LOG = LoggerFactory.getLogger(ClassNotFoundExceptionMapper.class);

  @Override
  public Response toResponse(ClassNotFoundException e) {
    LOG.error("Warning: Unable to locate AAAFilter.class;  AAA is disabled!");
    return Response.ok().build();
  }

}
