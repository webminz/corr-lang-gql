package no.hvl.past.gqlintegration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import no.hvl.past.gqlintegration.caller.IntrospectionQuery;
import no.hvl.past.gqlintegration.predicates.MutationMessage;
import no.hvl.past.gqlintegration.predicates.QueryMesage;
import no.hvl.past.gqlintegration.queries.GraphQLQueryDelegator;
import no.hvl.past.gqlintegration.queries.GraphQLQueryHandler;
import no.hvl.past.gqlintegration.schema.FieldMult;
import no.hvl.past.gqlintegration.schema.GraphQLSchemaReader;
import no.hvl.past.gqlintegration.schema.StubWiring;
import no.hvl.past.graph.GraphError;
import no.hvl.past.graph.GraphMorphism;
import no.hvl.past.graph.Sketch;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.trees.QueryTree;
import no.hvl.past.graph.trees.TypedTree;
import no.hvl.past.names.Name;
import no.hvl.past.plugin.UnsupportedFeatureException;
import no.hvl.past.systems.MessageType;
import no.hvl.past.systems.Sys;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Stream;

public class GraphQLEndpoint extends Sys.Impl {

    private final Map<Name, FieldMult> fieldMults;
    private final Map<Name, String> displayNames;
    private GraphQLQueryHandler queryHandler;
    private ObjectMapper objectMapper;
    private JsonFactory jsonFactory;
    private Set<QueryMesage> queries;
    private Set<MutationMessage> mutations;
    private String queryTypeName;
    private String mutationTyupeName;


    public GraphQLEndpoint(
            String url,
            Sketch schema,
            Map<Name, String> displayNames,
            List<FieldMult> multiplicities,
            Set<QueryMesage> queries,
            Set<MutationMessage> mutations,
            ObjectMapper objectMapper,
            JsonFactory jsonFactory,
            String queryTypeName,
            String mutationTypeName) {
        super(url, schema, displayNames);
        this.displayNames = displayNames;
        this.objectMapper = objectMapper;
        this.jsonFactory = jsonFactory;
        this.fieldMults = new HashMap<>();
        for (FieldMult m : multiplicities) {
            fieldMults.put(m.getElementName(), m);
        }
        this.queries = queries;
        this.mutations = mutations;
        this.queryTypeName = queryTypeName;
        this.mutationTyupeName = mutationTypeName;
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

    public Optional<QueryMesage> getQueryMessage(String operationName) {
        return this.queries.stream().filter(qm -> qm.getOperationName().equals(operationName)).findFirst();
    }

    public Optional<MutationMessage> getMutationNMassage(String operationName) {
        return this.mutations.stream().filter(mm -> mm.getOperationName().equals(operationName)).findFirst();
    }


    public Optional<MessageType> getMessage(String name) {
        if (name.contains(".")) {
            String container = name.substring(0, name.indexOf('.'));
            String operation = name.substring(name.indexOf('.') + 1);
            for (QueryMesage queryMesage : this.queries) {
                if (queryMesage.getContainerObjectName().equals(container) && queryMesage.getOperationName().equals(operation)) {
                    return Optional.of(queryMesage);
                }
            }
            for (MutationMessage mutationMessage : this.mutations) {
                if (mutationMessage.getContainerObjectName().equals(container) && mutationMessage.getOperationName().equals(operation)) {
                    return Optional.of(mutationMessage);
                }
            }
            return Optional.empty();
        }
        Optional<QueryMesage> qm = getQueryMessage(name);
        return qm.<Optional<MessageType>>map(Optional::of).orElseGet(() -> getMutationNMassage(name).map(x -> x));
    }

    @Override
    public Stream<MessageType> messages() {
        return Stream.concat(this.queries.stream(), this.mutations.stream());
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
    public String displayName(Name name) {
        return displayNames.get(name);
    }

    @Override
    public Optional<Triple> lookup(String... path) {
        if (path.length == 0) {
            return Optional.empty();
        }
        if (path[0].equals(queryTypeName) || path[0].equals(mutationTyupeName)) {
            if (path.length == 1) {
                return Optional.empty();
            }
            Name msgType = Name.identifier(path[0] + "." + path[1]);
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
            sketch = reader.convert(name, schema);
        } else {
            // introspection query
            GraphQLSchema schema = new IntrospectionQuery().getGraphQLSchema(url);
            sketch = reader.convert(name, schema);
        }
        return new GraphQLEndpoint(
                url,
                sketch,
                reader.getNameToText(),
                reader.getMultiplicities() ,
                reader.getQueries(),
                reader.getMuations(),
                objectMapper,
                jsonFactory
        ,reader.getQueryTypeName(), reader.getMutationTypeName());

    }

    public GraphMorphism parseQueryOrInstance(InputStream inputStream) throws IOException {
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



}
