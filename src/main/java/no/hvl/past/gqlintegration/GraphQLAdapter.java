package no.hvl.past.gqlintegration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.hvl.past.di.PropertyHolder;

import no.hvl.past.gqlintegration.queries.GraphQLQueryDivider;
import no.hvl.past.gqlintegration.schema.GraphQLSchemaReader;
import no.hvl.past.gqlintegration.schema.GraphQLSchemaWriter;
import no.hvl.past.graph.*;
import no.hvl.past.graph.trees.QueryHandler;
import no.hvl.past.graph.trees.QueryTree;
import no.hvl.past.graph.trees.TypedTree;
import no.hvl.past.names.Name;
import no.hvl.past.names.PrintingStrategy;
import no.hvl.past.plugin.UnsupportedFeatureException;
import no.hvl.past.server.WebserviceRequestHandler;
import no.hvl.past.systems.ComprSys;
import no.hvl.past.systems.Sys;
import no.hvl.past.techspace.TechSpaceAdapter;
import no.hvl.past.techspace.TechSpaceDirective;
import no.hvl.past.techspace.TechSpaceException;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.ConnectException;
import java.net.URISyntaxException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GraphQLAdapter implements TechSpaceAdapter<GraphQLTechSpace>, TechSpaceDirective {


    private final Universe universe;
    private final PropertyHolder propertyHolder;
    private final JsonFactory jsonFactory;
    private final ObjectMapper objectMapper;

    public GraphQLAdapter(Universe universe, PropertyHolder propertyHolder) {
        this.universe = universe;
        this.propertyHolder = propertyHolder;
        this.jsonFactory = new JsonFactory();
        this.objectMapper = new ObjectMapper(jsonFactory);
    }

    public JsonParser jsonParser(InputStream inputStream) throws IOException {
        return jsonFactory.createParser(inputStream);
    }

    public JsonGenerator jsonGenerator(OutputStream outputStream) throws IOException {
        return jsonFactory.createGenerator(outputStream);
    }

    private GraphBuilders builder() {
        return new GraphBuilders(universe, false, false);
    }

    public Sys parseSchema(Name schemaName, String fromURI) throws TechSpaceException, UnsupportedFeatureException {
        try {
            return GraphQLEndpoint.createFromUrl(fromURI, schemaName, new GraphQLSchemaReader(universe), objectMapper, jsonFactory);
        } catch (ConnectException ce) {
            throw new TechSpaceException("GraphQL endpoint at URL '" + fromURI + "' is not running!", GraphQLTechSpace.INSTANCE);
        } catch (URISyntaxException | IOException | GraphError e) {
            throw new TechSpaceException(e, GraphQLTechSpace.INSTANCE);
        }
    }

    @Override
    public void writeSchema(Sys sys, OutputStream outputStream) throws TechSpaceException, UnsupportedFeatureException {
        try {
            GraphQLSchemaWriter schemaWriter = new GraphQLSchemaWriter();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            sys.schema().accept(schemaWriter);
            schemaWriter.printToBuffer(writer);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new TechSpaceException(e, GraphQLTechSpace.INSTANCE);
        }
    }

    @Override
    public QueryHandler queryHandler(Sys system) throws TechSpaceException, UnsupportedFeatureException {
        if (system instanceof GraphQLEndpoint) {
            GraphQLEndpoint endpoint = (GraphQLEndpoint) system;
            return endpoint.getOrCreateQueryHandler(objectMapper, jsonFactory);
        }
        if (system instanceof ComprSys) {
            ComprSys comprSys = (ComprSys) system;
            LinkedHashMap<Sys, QueryHandler> handlerMap = new LinkedHashMap<>();
            for (Sys sys : comprSys.components().collect(Collectors.toList())) {
                QueryHandler queryHandler = queryHandler(sys);
                handlerMap.put(sys, queryHandler);
            }
            try {
                return GraphQLQueryDivider.create(objectMapper, jsonFactory, comprSys, handlerMap);
            } catch (IOException e) {
                throw new TechSpaceException("Cannot create GraphQL handler for '" + system.url() + "'!", e, GraphQLTechSpace.INSTANCE);
            }
        }
        throw new TechSpaceException("Cannot create GraphQL handler for '" + system.url() + "'!", GraphQLTechSpace.INSTANCE);
    }

    @Override
    public GraphMorphism readInstance(Sys system, InputStream inputStream) throws TechSpaceException, UnsupportedFeatureException {
        try {
            if (system instanceof GraphQLEndpoint) {
                GraphQLEndpoint endpoint = (GraphQLEndpoint) system;
                return endpoint.parseQueryOrInstance(inputStream);
            }
            if (system instanceof ComprSys) {
                return (TypedTree) GraphQLQueryDivider.create(objectMapper, jsonFactory,(ComprSys) system, new LinkedHashMap<>()).deserialize(inputStream);
            }
            throw new TechSpaceException("Cannot parse instance for '" + system.url() + "'", GraphQLTechSpace.INSTANCE);
        } catch (IOException e) {
            throw new TechSpaceException("Error occured parsing instance", e, GraphQLTechSpace.INSTANCE);
        }
    }

    @Override
    public void writeInstance(Sys system, GraphMorphism instance, OutputStream outputStream) throws TechSpaceException, UnsupportedFeatureException {
        try {
            if (system instanceof GraphQLEndpoint) {
                GraphQLEndpoint endpoint = (GraphQLEndpoint) system;
                if (instance instanceof QueryTree) {
                    endpoint.serializeQuery((QueryTree) instance, outputStream);
                } else {
                    endpoint.serializeJson(instance, outputStream);
                }
            }
            if (system instanceof ComprSys) {
                if (instance instanceof TypedTree) {
                    GraphQLQueryDivider.create(objectMapper, jsonFactory, (ComprSys) system, new LinkedHashMap<>()).serialize((TypedTree) instance, outputStream);
                } else {
                    throw new TechSpaceException("Cannot serialize non-tree shaped instances", GraphQLTechSpace.INSTANCE);
                }
            }
            throw new TechSpaceException("Cannot write instance for '" + system.url() + "'", GraphQLTechSpace.INSTANCE);
        } catch (IOException e) {
            throw new TechSpaceException("Error occurred while writing instance " + instance.getName().print(PrintingStrategy.IGNORE_PREFIX), e, GraphQLTechSpace.INSTANCE);
        }
    }


    @Override
    public TechSpaceDirective directives() {
        return this;
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
        return Stream.of(Name.identifier("ID"));
    }
}
