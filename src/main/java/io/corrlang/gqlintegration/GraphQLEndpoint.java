package io.corrlang.gqlintegration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.corrlang.domain.MessageType;
import io.corrlang.domain.ProcessingException;
import io.corrlang.domain.Sys;
import io.corrlang.gqlintegration.schema.EntryPointType;
import io.corrlang.gqlintegration.schema.FieldMult;
import io.corrlang.gqlintegration.schema.GraphQLSchemaReader;
import io.corrlang.gqlintegration.schema.StubWiring;
import io.corrlang.gqlintegration.caller.IntrospectionQuery;
import io.corrlang.gqlintegration.queries.GraphQLQueryDelegator;
import io.corrlang.gqlintegration.queries.GraphQLQueryHandler;
import no.hvl.past.graph.GraphError;
import no.hvl.past.graph.GraphMorphism;
import no.hvl.past.graph.Sketch;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.trees.QueryTree;
import no.hvl.past.graph.trees.TypedTree;
import no.hvl.past.names.Name;
import no.hvl.past.UnsupportedFeatureException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Stream;

public class GraphQLEndpoint extends Sys.Impl {

    // TODO This is redundant, since it should be covered by
    private final Map<Name, FieldMult> fieldMults;
    // TODO TechSpace Handler refactoring: probably to be moved out now
    private GraphQLQueryHandler queryHandler;

    private final ObjectMapper objectMapper;
    private final JsonFactory jsonFactory;

    // TODO should be subsumed by containers now, however it is proably good to know the name of the query type
    private EntryPointType queries;
    private EntryPointType mutations;


    public GraphQLEndpoint(
            String url,
            Sketch schema,
            Map<Name, String> displayNames,
            Map<Name, MessageType> messages, // should be discoverable
            List<FieldMult> multiplicities,
            EntryPointType queries,
            EntryPointType mutations,
            ObjectMapper objectMapper,
            JsonFactory jsonFactory) {
        super(url, schema, displayNames, messages);
        this.queries = queries;
        this.mutations = mutations;
        this.objectMapper = objectMapper;
        this.jsonFactory = jsonFactory;
        this.fieldMults = new HashMap<>();
        for (FieldMult m : multiplicities) {
            fieldMults.put(m.getElementName(), m);
        }
    }

    @Override
    public boolean isMessageContainer(Name name) {
        return queries.getName().equals(name.printRaw()) || mutations.getName().equals(name.printRaw());
    }

    public Optional<Triple> lookupField(Name owner, String fieldNameAsString) {
        return schema().carrier().outgoing(owner).filter(t -> displayName(t.getLabel()).equals(fieldNameAsString)).findFirst();
    }

    @Override
    public boolean hasTargetMultiplicity(Triple edge, int lowerBound, int upperBound) {
        if (upperBound == 1 && lowerBound == 1) {
            return this.fieldMults.get(edge.getLabel()).isMandatory() && !this.fieldMults.get(edge.getLabel()).isListValued();
        }
        if (lowerBound == 1) {
            return this.fieldMults.get(edge.getLabel()).isMandatory();
        }
        if (upperBound == 1) {
            return !this.fieldMults.get(edge.getLabel()).isListValued();
        }
        if (upperBound < 0 || upperBound > 1) {
            return this.fieldMults.get(edge.getLabel()).isListValued();
        }
        return super.hasTargetMultiplicity(edge,lowerBound, upperBound);
    }

    public Optional<MessageType> getQueryMessage(String operationName) {
        return this.queries.getFields().stream().filter(qm -> this.displayName(qm.typeName()).equals(operationName)).findFirst();
    }

    public Optional<MessageType> getMutationNMassage(String operationName) {
        return this.mutations.getFields().stream().filter(mm -> this.displayName(mm.typeName()).equals(operationName)).findFirst();
    }


    public Optional<MessageType> getMessage(String name) {
        if (name.contains(".")) {
            String container = name.substring(0, name.indexOf('.'));
            String operation = name.substring(name.indexOf('.') + 1);
            if (container.equals(this.queries.getName())) {
                return getQueryMessage(operation);
            }
            if (container.equals(this.mutations.getName())) {
                return getMutationNMassage(operation);
            }
            return Optional.empty();
        }
        return messages().filter(m -> displayName(m.typeName()).equals(name)).findFirst();
    }

    @Override
    public Stream<MessageType> messages() {
        return Stream.concat(this.queries.getFields().stream(), this.mutations.getFields().stream());
    }

    @Override
    public boolean isCollectionValued(Triple edge) {
        return this.fieldMults.get(edge.getLabel()).isListValued();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GraphQLEndpoint that = (GraphQLEndpoint) o;
        return url().equals(that.url());
    }

    @Override
    public int hashCode() {
        return Objects.hash(url());
    }

    @Override
    public Optional<Triple> lookup(String... path) {
        if (path.length == 0) {
            return Optional.empty();
        }
        if (path[0].equals(this.queries.getName()) || path[0].equals(this.mutations.getName())) {
            if (path.length == 1) {
                return Optional.empty();
            }
            // FIXME urgent: Have to write as test for this!!!
            Name msgType = Name.identifier(path[1]).prefixWith(Name.identifier(path[0]));
            if (path.length > 2) {
                Name last = Name.identifier(path[path.length - 1]);
                for (int i = path.length - 2; i > 1; i++) {
                    last = last.prefixWith(Name.identifier(path[i]));
                }
                return schema().carrier().get(last.prefixWith(msgType));
            }
            return schema().carrier().get(msgType);
        }
        return super.lookup(path);
    }


    public GraphQLQueryHandler getOrCreateQueryHandler(ObjectMapper objectMapper, JsonFactory jsonFactory) {
        if (queryHandler == null) {
            queryHandler = new GraphQLQueryDelegator(this);
        }
        return queryHandler;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public JsonFactory getJsonFactory() {
        return jsonFactory;
    }

    public static GraphQLEndpoint createFromUrl(
            String url,
            Name name,
            GraphQLSchemaReader reader,
            ObjectMapper objectMapper,
            JsonFactory jsonFactory) throws URISyntaxException, GraphError, IOException, UnsupportedFeatureException {
        Sketch sketch;
        if (url.startsWith("file") || url.startsWith(".")) {
            // local file
            File file = new File(new URI(url));
            TypeDefinitionRegistry registry = new SchemaParser().parse(file);
            GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, StubWiring.createWiring(registry));
            return reader.convert(url, name, schema, objectMapper, jsonFactory);
        } else {
            // introspection query
            GraphQLSchema schema = new IntrospectionQuery().getGraphQLSchema(url);
            return reader.convert(url, name, schema, objectMapper, jsonFactory);
        }
    }

    public GraphMorphism parseQueryOrInstance(InputStream inputStream) throws IOException, ProcessingException {
        return getOrCreateQueryHandler(objectMapper,jsonFactory).deserialize(inputStream);
    }

    public void serializeQuery(QueryTree instance,  OutputStream outputStream) throws IOException {
        this.getOrCreateQueryHandler(objectMapper,jsonFactory).serialize(instance, outputStream);
    }

    public void serializeJson(GraphMorphism instance, OutputStream outputStream) throws IOException {
        if (instance instanceof TypedTree) {
            this.getOrCreateQueryHandler(objectMapper,jsonFactory).serialize((TypedTree) instance, outputStream);
        }
        throw new IOException("Cannot serialize non-tree shaped instances!");
    }


    public void setQueryHandler(GraphQLQueryHandler handler) {
        this.queryHandler = handler;
    }

    public String mutationTypeName() {
        return mutations.getName();
    }

    public String queryTypeName() {
        return queries.getName();
    }
}
