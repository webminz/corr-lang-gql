package no.hvl.past.gqlintegration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import no.hvl.past.TestBase;
import no.hvl.past.di.DependencyInjectionContainer;
import no.hvl.past.di.PropertyHolder;
import no.hvl.past.graph.GraphBuilders;
import no.hvl.past.graph.Universe;
import no.hvl.past.graph.UniverseImpl;

import javax.swing.*;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static org.junit.Assume.assumeTrue;

public abstract class GraphQLTest extends TestBase {

    private DependencyInjectionContainer container;

    protected JsonFactory jsonFactory = JsonFactory.builder().build();

    protected ObjectMapper objectMapper = new ObjectMapper(jsonFactory);

    protected DependencyInjectionContainer getDICOntainer() throws Exception {
        if (container == null) {
            Properties p = new Properties();
            p.put(PropertyHolder.BASE_DIR, System.getProperty("user.dir") + "/test/resources/execdir");
            container = DependencyInjectionContainer.create("GQLApp", p);
        }
        return container;
    }

    public GraphQLAdapter createAdapter() throws Exception {
        return new GraphQLAdapter(getDICOntainer().getUniverse(), getDICOntainer().getPropertyHolder());
    }

    Universe getUniverseForTest() {
        return new UniverseImpl(UniverseImpl.EMPTY);
    }

    GraphQLSchema parseSchemaAsText(String schema) {
        return new SchemaGenerator().makeExecutableSchema(new SchemaParser().parse(schema), RuntimeWiring.newRuntimeWiring().build());
    }

    GraphBuilders contextCreatingBuilder() {
        return new GraphBuilders(getUniverseForTest(), true, false);
    }

    void makeSureServerIsRunning(String url) {
        requiresSpecificInstalledComponents();
        Object[] options = {"Yes", "No"};
        int running = JOptionPane.showOptionDialog(
                null,
                "Is GraphQL endpoint at '" + url + "' running?",
                "Server running?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]
        );
        if (running == 1) {
            logInfo("Server at '" + url + "' not running, current test '" + getTestName() + "' will not be executed");
        }
        assumeTrue(running == 0);
    }

}
