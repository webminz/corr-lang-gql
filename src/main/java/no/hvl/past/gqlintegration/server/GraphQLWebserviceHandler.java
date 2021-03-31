package no.hvl.past.gqlintegration.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import no.hvl.past.gqlintegration.queries.GraphQLQueryHandler;
import no.hvl.past.gqlintegration.schema.GraphQLSchemaWriter;
import no.hvl.past.graph.Sketch;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.names.Name;
import no.hvl.past.server.HttpMethod;
import no.hvl.past.server.WebserviceRequestHandler;
import no.hvl.past.util.GenericIOHandler;
import no.hvl.past.util.IOStreamUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GraphQLWebserviceHandler extends WebserviceRequestHandler {

    private Logger logger = Logger.getLogger(GraphQLWebserviceHandler.class);

    private GraphQLQueryHandler handler;
    public GraphQLWebserviceHandler(
            String url,
            String schemaAsText,
            GraphQLQueryHandler handler,
            Sketch schemaAsSketch,
            Map<Name, String> nameToText) {
        super(url, HttpMethod.POST,ResponseType.JSON);
        this.handler = handler;
        TypeDefinitionRegistry typeReg = new SchemaParser().parse(schemaAsText);
        RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
//        logger.debug("Starting the GraphQL engine ");
//        LocalDateTime start = LocalDateTime.now();
//        if (schemaAsSketch.carrier().mentions(Name.identifier("Query"))) {
//            DynamicQueryResolver queryResolver = new DynamicQueryResolver(handler, schemaAsSketch,nameToText, Name.identifier("Query"));
//            builder = builder.type(TypeRuntimeWiring.newTypeWiring("Query").defaultDataFetcher(queryResolver));
//        }
//        if (schemaAsSketch.carrier().mentions(Name.identifier("Mutation"))) {
//            DynamicQueryResolver mutationResolver = new DynamicQueryResolver(handler, schemaAsSketch,nameToText, Name.identifier("Mutation"));
//            builder = builder.type(TypeRuntimeWiring.newTypeWiring("Mutation").defaultDataFetcher(mutationResolver));
//        }
//        Set<Name> collect = schemaAsSketch.carrier().nodes()
//                .filter(n -> !Name.identifier("Query").equals(n) && !Name.identifier("Mutation").equals(n))
//                .filter(n -> schemaAsSketch.diagramsOn(Triple.node(n)).noneMatch(diag -> GraphQLSchemaWriter.isBaseTypePredicate(diag.label())))
//                .collect(Collectors.toSet());
//        for (Name remainingObjectType : collect) {
//            builder = builder.type(TypeRuntimeWiring.newTypeWiring(nameToText.get(remainingObjectType)).defaultDataFetcher(new JsonDataFetcher()));
//        }

        RuntimeWiring runtimeWiring = builder.build();
        GraphQLSchema executableSchema = new SchemaGenerator().makeExecutableSchema(typeReg, runtimeWiring);
        graphQL = GraphQL.newGraphQL(executableSchema).build();
       // logger.debug("GraphQL engine started. Startup took " + Duration.between(start, LocalDateTime.now()).toMillis() + "ms");
    }

    private final GraphQL graphQL;


    @Override
    protected GenericIOHandler createHandler(
            Map<String, String> headers,
            Map<String, List<String>> queryParams,
            Map<String, String> cookies,
            Map<String, Object> sessionData) {
        return new GenericIOHandler() {
            @Override
            public void handle(InputStream i, OutputStream o) throws IOException {
                ObjectMapper om = new ObjectMapper();
                JsonNode jsonNode = om.readTree(i);
//                if (jsonNode.get("operationName") != null && !jsonNode.get("operationName").asText().equals("null")) {
//                    ExecutionInput e = new ExecutionInput.Builder()
//                            .query(jsonNode.get("query").asText())
//                            .operationName(jsonNode.get("operationName").asText())
//                            .variables(convertToMap(om, jsonNode.get("variables")))
//                            .build();
//                    ExecutionResult execute = graphQL.execute(e);
//                    Map<String, Object> spec = execute.toSpecification();
//                    om.writeValue(o, spec);
//                } else {
//                    ExecutionResult executionResult = graphQL.execute(jsonNode.get("query").asText());
//                    Map<String, Object> spec = executionResult.toSpecification();
//                    om.writeValue(o, spec);
//                }
                // introspection


            }

            private Map<String, Object> convertToMap(ObjectMapper om, JsonNode variables) {
                if (variables != null && variables.isObject()) {
                    try {
                        return om.readValue(variables.toString(), Map.class);
                    } catch (JsonProcessingException e) {
                    }
                }
                return new HashMap<>();
            }
        };
    }
}
