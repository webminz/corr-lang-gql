package no.hvl.past.gqlintegration;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import no.hvl.past.di.PropertyHolder;
import no.hvl.past.gqlintegration.caller.GraphQLCaller;
import no.hvl.past.gqlintegration.caller.GraphQLCallerFactory;
import no.hvl.past.gqlintegration.queries.GraphQLQueryDelegator;
import no.hvl.past.gqlintegration.queries.GraphQLQueryDivider;
import no.hvl.past.gqlintegration.queries.GraphQLQueryHandler;
import no.hvl.past.gqlintegration.schema.GraphQLSchemaWriter;
import no.hvl.past.gqlintegration.schema.GraphQLSchemaReader;
import no.hvl.past.gqlintegration.server.GraphQLWebserviceHandler;
import no.hvl.past.graph.*;
import no.hvl.past.graph.trees.QueryHandler;
import no.hvl.past.keys.Key;
import no.hvl.past.names.Name;
import no.hvl.past.plugin.UnsupportedFeatureException;
import no.hvl.past.server.WebserviceRequestHandler;
import no.hvl.past.techspace.TechSpaceAdapter;
import no.hvl.past.techspace.TechSpaceDirective;
import no.hvl.past.techspace.TechSpaceException;
import no.hvl.past.util.Pair;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

public class GraphQLAdapter implements TechSpaceAdapter<GraphQLTechSpace>, TechSpaceDirective {
    private static Logger LOGGER = Logger.getLogger(GraphQLAdapter.class);

    private final Universe universe;
    private final PropertyHolder propertyHolder;

    public GraphQLAdapter(Universe universe, PropertyHolder propertyHolder) {
        this.universe = universe;
        this.propertyHolder = propertyHolder;
    }

    public Sketch parseSchema(Name schemaName, String fromURI) throws TechSpaceException {
        try {
            return parseSchema(schemaName, fromURI, new GraphQLSchemaReader(universe));
        } catch (URISyntaxException | IOException | GraphError e) {
            throw new TechSpaceException(e, GraphQLTechSpace.INSTANCE);
        }
    }

    private Sketch parseSchema(Name schemaName, String fromURI, GraphQLSchemaReader reader) throws GraphError, URISyntaxException, IOException {
        if (fromURI.startsWith("file") || fromURI.startsWith(".")) {
            // local file
            File file = new File(new URI(fromURI));
            TypeDefinitionRegistry registry = new SchemaParser().parse(file);
            GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.newRuntimeWiring().build());
            return reader.convert(schemaName, schema);
        } else {
            // introspection query
            GraphQLCaller caller = GraphQLCallerFactory.getInstance().create();
            GraphQLSchema schema = caller.getGraphQLSchema(fromURI);
            return reader.convert(schemaName, schema);
        }
    }


    @Override
    public TechSpaceDirective directives() {
        return this;
    }


    private void writeSchema(Sketch schema, OutputStream outputStream, GraphQLSchemaWriter writer) throws IOException {
        schema.accept(writer);
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
        writer.printToBuffer(bufferedWriter);
        bufferedWriter.flush();
        bufferedWriter.close();
    }

    @Override
    public void writeSchema(Sketch formalSchemaRepresentation, OutputStream outputStream) throws TechSpaceException {
        GraphQLSchemaWriter wr = new GraphQLSchemaWriter();
        try {
            writeSchema(formalSchemaRepresentation, outputStream, wr);
        } catch (IOException e) {
            throw new TechSpaceException(e, GraphQLTechSpace.INSTANCE);
        }
    }

    @Override
    public QueryHandler queryHandler(String locationURI, String schemaLocationURI) throws TechSpaceException {
        GraphQLSchemaReader reader = new GraphQLSchemaReader(universe);
        Sketch schema;
        try {
            if (schemaLocationURI != null) {
                schema = parseSchema(Name.identifier("Schema"), schemaLocationURI, reader);
            } else {
                schema = parseSchema(Name.identifier("Schema"), locationURI, reader);
            }
            return new GraphQLQueryDelegator(schema, locationURI, reader.getNameToText());
        } catch (Exception e) {
            throw new TechSpaceException(e, GraphQLTechSpace.INSTANCE);
        }
    }

    @Override
    public WebserviceRequestHandler federationQueryHandler(
            Sketch comprehensiveSchema,
            List<GraphMorphism> embeddings,
            List<QueryHandler> localQueryHandlers) throws TechSpaceException, UnsupportedFeatureException {
        try {
            Map<GraphMorphism, GraphQLQueryHandler> handlerMap = new LinkedHashMap<>();
            ListIterator<GraphMorphism> listIterator = embeddings.listIterator();
            while (listIterator.hasNext()) {
                int i = listIterator.nextIndex();
                GraphMorphism next = listIterator.next();
                if (localQueryHandlers.get(i) instanceof GraphQLQueryHandler) {
                    GraphQLQueryHandler gqlHandler = (GraphQLQueryHandler) localQueryHandlers.get(i);
                    handlerMap.put(next, gqlHandler);
                }
            }


            Map<Name, Key> keys = new HashMap<>(); // TODO extend the Star
         //   multiModel.components().forEach(component -> component.diagrams().filter(d -> d instanceof Key).map(d -> (Key) d).forEach(k -> {
          //      keys.put(k.definedOnType(), k);
         //   }));

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            GraphQLSchemaWriter schemaWriter = new GraphQLSchemaWriter();
            writeSchema(comprehensiveSchema, bos, schemaWriter);
            LOGGER.debug("GRAPH_QL schema for " + comprehensiveSchema.getName() + " is written");

            String url = propertyHolder.getServerurl();
            String gqlPath = propertyHolder.getPropertyAndSetDefaultIfNecessary("graphql.url", "/graphql");
            GraphQLQueryHandler handler = new GraphQLQueryDivider(url + gqlPath, comprehensiveSchema, schemaWriter.getNameToText(), handlerMap, keys);
            LOGGER.debug("GRAPH_QL query split/aggregator is created");

            return new GraphQLWebserviceHandler(gqlPath, bos.toString(), handler, comprehensiveSchema, schemaWriter.getNameToText());
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            throw new TechSpaceException(e, GraphQLTechSpace.INSTANCE);
        }
    }


    @Override
    public Optional<Name> stringDataType() {
        return Optional.of(Name.identifier("String"));
    }

    @Override
    public Optional<Name> boolDataType() {
        return Optional.of(Name.identifier("Boolean"));
    }

    @Override
    public Optional<Name> integerDataType() {
        return Optional.of(Name.identifier("Int"));
    }

    @Override
    public Optional<Name> floatingPointDataType() {
        return Optional.of(Name.identifier("Float"));
    }

    @Override
    public Stream<Name> implicitTypeIdentities() {
        return Stream.of(Name.identifier("ID"), Name.identifier("Query"), Name.identifier("Mutation"));
    }
}
