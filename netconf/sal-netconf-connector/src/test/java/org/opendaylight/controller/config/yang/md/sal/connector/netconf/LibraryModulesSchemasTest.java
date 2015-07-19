package org.opendaylight.controller.config.yang.md.sal.connector.netconf;

import java.net.URL;
import java.util.Map;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;

public class LibraryModulesSchemasTest {



    @Test
    public void testCreateFromXML() throws Exception {
        final LibraryModulesSchemas libraryModulesSchemas = LibraryModulesSchemas.create(getClass().getResource("/yang-library.xml").toString());
        Assert.assertThat(libraryModulesSchemas.getAvailableModels().size(), CoreMatchers.is(4));
        final Map<SourceIdentifier, URL> resolvedModulesSchema = libraryModulesSchemas.getAvailableModels();

        Assert.assertTrue(resolvedModulesSchema.containsKey(new SourceIdentifier("odl-netconf-cfg", "2014-04-08")));
        Assert.assertThat(resolvedModulesSchema.get(
                new SourceIdentifier("odl-netconf-cfg", "2014-04-08")),
                CoreMatchers.is(new URL("http://localhost:8181/yanglib/schemas/odl-netconf-cfg/2014-04-08")));
    }

    @Test
    public void testCreateInvalid() throws Exception {
        final LibraryModulesSchemas libraryModulesSchemas = LibraryModulesSchemas.create(getClass().getResource("/yang-library-fail.xml").toString());
        Assert.assertThat(libraryModulesSchemas.getAvailableModels().size(), CoreMatchers.is(1));
    }

//    @Test
//    public void testCreateInvalidAll() throws Exception {
//        final LibraryModulesSchemas libraryModulesSchemas = LibraryModulesSchemas.create(getClass().getResource("/yang-library-fail-completely.xml").toString());
//        Assert.assertThat(libraryModulesSchemas.getAvailableModels().size(), CoreMatchers.is(0));
//    }

    @Test
    public void testCreateJson() throws Exception {
        final LibraryModulesSchemas libraryModulesSchemas = LibraryModulesSchemas.create(getClass().getResource("/yang-library.json").toString());
        Assert.assertThat(libraryModulesSchemas.getAvailableModels().size(), CoreMatchers.is(4));
        final Map<SourceIdentifier, URL> resolvedModulesSchema = libraryModulesSchemas.getAvailableModels();

        Assert.assertTrue(resolvedModulesSchema.containsKey(new SourceIdentifier("odl-netconf-cfg", "2014-04-08")));
        Assert.assertThat(resolvedModulesSchema.get(
                new SourceIdentifier("odl-netconf-cfg", "2014-04-08")),
                CoreMatchers.is(new URL("http://localhost:8181/yanglib/schemas/odl-netconf-cfg/2014-04-08")));
    }
}