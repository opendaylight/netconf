/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.core.UriInfo;
import org.apache.maven.project.MavenProject;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.URIType;
import org.opendaylight.netconf.sal.rest.doc.impl.BaseYangSwaggerGeneratorDraft02;
import org.opendaylight.netconf.sal.rest.doc.swagger.Resource;
import org.opendaylight.netconf.sal.rest.doc.swagger.ResourceList;
import org.opendaylight.netconf.sal.rest.doc.swagger.SwaggerObject;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang2sources.spi.BasicCodeGenerator;
import org.opendaylight.yangtools.yang2sources.spi.MavenProjectAware;
import org.opendaylight.yangtools.yang2sources.spi.ModuleResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class gathers all yang defined {@link Module}s and generates Swagger compliant documentation.
 */
public class StaticDocGenerator extends BaseYangSwaggerGeneratorDraft02
        implements BasicCodeGenerator, MavenProjectAware {
    private static final Logger LOG = LoggerFactory.getLogger(StaticDocGenerator.class);

    private static final String DEFAULT_OUTPUT_BASE_DIR_PATH = "target" + File.separator + "generated-resources"
        + File.separator + "swagger-api-documentation";

    public StaticDocGenerator() {
        super(Optional.empty());
    }

    @Override
    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    public Collection<File> generateSources(final EffectiveModelContext context, final File outputBaseDir,
            final Set<Module> currentModules, final ModuleResourceResolver moduleResourcePathResolver)
                    throws IOException {
        final List<File> result = new ArrayList<>();

        // Create Base Directory
        final File outputDir;
        if (outputBaseDir == null) {
            outputDir = new File(DEFAULT_OUTPUT_BASE_DIR_PATH);
        } else {
            outputDir = outputBaseDir;
        }

        if (!outputDir.mkdirs()) {
            throw new IOException("Could not create directory " + outputDir);
        }

        // Create Resources directory
        final File resourcesDir = new File(outputDir, "resources");
        if (!resourcesDir.mkdirs()) {
            throw new IOException("Could not create directory " + resourcesDir);
        }

        // Create JS file
        final File resourcesJsFile = new File(outputDir, "resources.js");
        if (!resourcesJsFile.createNewFile()) {
            LOG.info("File {} already exists.", resourcesJsFile);
        }

        try (BufferedWriter bufferedWriter =
                new BufferedWriter(new FileWriter(resourcesJsFile, StandardCharsets.UTF_8))) {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

            // Write resource listing to JS file
            final ResourceList resourceList = super.getResourceListing(null, context, "", URIType.DRAFT02,
                    ApiDocServiceImpl.OAversion.V2_0);
            String resourceListJson = mapper.writeValueAsString(resourceList);
            resourceListJson = resourceListJson.replace("\'", "\\\'").replace("\\n", "\\\\n");
            bufferedWriter.write("function getSpec() {\n\treturn \'" + resourceListJson + "\';\n}\n\n");

            // Write resources/APIs to JS file and to disk
            bufferedWriter.write("function jsonFor(resource) {\n\tswitch(resource) {\n");
            for (final Resource resource : resourceList.getApis()) {
                final int revisionIndex = resource.getPath().indexOf('(');
                final String name = resource.getPath().substring(0, revisionIndex);
                final String revision =
                        resource.getPath().substring(revisionIndex + 1, resource.getPath().length() - 1);
                final SwaggerObject swaggerObject = super.getApiDeclaration(name, revision, null, context, "",
                    URIType.DRAFT02, ApiDocServiceImpl.OAversion.V2_0);
                String json = mapper.writeValueAsString(swaggerObject);
                // Manually insert models because org.json.JSONObject cannot be serialized by ObjectMapper
                json = json.replace(
                        "\"models\":{}", "\"models\":"
                                + swaggerObject.getDefinitions().toString().replace("\\\"", "\""));
                // Escape single quotes and new lines
                json = json.replace("\'", "\\\'").replace("\\n", "\\\\n");
                bufferedWriter.write("\t\tcase \"" + name + "(" + revision + ")\": return \'" + json + "\';\n");

                final File resourceFile = new File(resourcesDir, name + "(" + revision + ").json");

                try (BufferedWriter resourceFileWriter =
                        new BufferedWriter(new FileWriter(resourceFile, StandardCharsets.UTF_8))) {
                    resourceFileWriter.write(json);
                }

                result.add(resourceFile);
            }
            bufferedWriter.write("\t}\n\treturn \"\";\n}");
        }

        result.add(resourcesJsFile);
        return result;
    }

    @Override
    public String generatePath(final UriInfo uriInfo, final String name, final String revision) {
        if (uriInfo == null) {
            return name + "(" + revision + ")";
        }
        return super.generatePath(uriInfo, name, revision);
    }

    @Override
    public void setAdditionalConfig(final Map<String, String> additionalConfig) {
    }

    @Override
    public void setResourceBaseDir(final File resourceBaseDir) {
    }

    @Override
    public void setMavenProject(final MavenProject mavenProject) {
    }
}
