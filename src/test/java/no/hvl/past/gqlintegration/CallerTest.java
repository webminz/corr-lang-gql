package no.hvl.past.gqlintegration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import no.hvl.past.gqlintegration.caller.GraphQLCaller;
import no.hvl.past.gqlintegration.caller.GraphQLCallerFactory;
import no.hvl.past.gqlintegration.queries.GraphQLQuery;
import no.hvl.past.gqlintegration.queries.GraphQLQueryDelegator;
import no.hvl.past.gqlintegration.queries.GraphQLQueryHandler;
import no.hvl.past.gqlintegration.queries.GraphQLQueryNode;
import no.hvl.past.gqlintegration.schema.GraphQLSchemaReader;
import no.hvl.past.gqlintegration.server.DynamicQueryResolver;
import no.hvl.past.gqlintegration.server.JsonDataFetcher;
import no.hvl.past.graph.GraphError;
import no.hvl.past.graph.Sketch;
import no.hvl.past.graph.trees.QueryHandler;
import no.hvl.past.graph.trees.QueryTree;
import no.hvl.past.names.Name;
import no.hvl.past.techspace.TechSpaceException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CallerTest extends GraphQLTest {

    @Test
    public void testCalling() throws IOException, TechSpaceException {
        GraphQLQuery query = new GraphQLQuery(
                new GraphQLQueryNode.Builder(Name.identifier("query"))
                        .startChild(Name.identifier("customers"))
                            .startChild(Name.identifier("id"))
                            .endChild()
                            .startChild(Name.identifier("name"))
                            .endChild()
                        .endChild());

        GraphQLAdapter adapter = createAdapter();
        Sketch schema = adapter.parseSchema(Name.identifier("Sales"), "http://localhost:4011");

        QueryHandler queryHandler = adapter.queryHandler( "http://localhost:4011",null);

        InputStream inputStream = queryHandler.resolveAsStream(query);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(inputStream).get("data");


        assertTrue(jsonNode.isObject());
        JsonNode customers = jsonNode.get("customers");
        assertTrue(customers.isArray());
        String name = customers.get(0).get("name").asText();
        assertEquals("Ban Geyton", name);
    }


    @Test
    public void testQueryParsing() throws IOException, TechSpaceException {
        GraphQLAdapter adapter = createAdapter();
        Sketch schema = adapter.parseSchema(Name.identifier("Sales"), "http://localhost:4011");
        String q = "query {\n" +
                "  customers {\n" +
                "    id  \n" +
                "    name\n" +
                "  }\n" +
                "}";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JsonGenerator generator = new JsonFactory().createGenerator(bos);
        generator.writeStartObject();
        generator.writeFieldName("query");
        generator.writeString(q);
        generator.writeEndObject();
        generator.flush();
        QueryHandler queryHandler = adapter.queryHandler( "http://localhost:4011", null);
        QueryTree actual = queryHandler.parse(new ByteArrayInputStream(bos.toByteArray()));

        GraphQLQuery expected = new GraphQLQuery(
                new GraphQLQueryNode.Builder(Name.identifier("query"))
                        .startChild(Name.identifier("customers"))
                        .startChild(Name.identifier("id"))
                        .endChild()
                        .startChild(Name.identifier("name"))
                        .endChild()
                        .endChild());

        assertEquals(expected.textualRepresentation(), actual.textualRepresentation());
    }

    // TODO testcase with a field query


//    @Test
//    public void testDynamicResolver() throws GraphError, JsonProcessingException {
//        String schema = "type Query {\n" +
//                "  customers: [Customer]\n" +
//                "}\n" +
//                "type Customer {\n" +
//                "  name: String\n" +
//                "  email: String\n" +
//                "}";
//        GraphQLSchema graphQLSchema = parseSchemaAsText(schema);
//        Sketch test = new GraphQLSchemaReader(getUniverseForTest()).convert(Name.identifier("test"), graphQLSchema);
//        GraphQLQueryHandler handler = new GraphQLQueryDelegator("http://localhost:4011");
//        DynamicQueryResolver dynResolver = new DynamicQueryResolver(handler, test);
//
//        TypeDefinitionRegistry typeReg = new SchemaParser().parse(schema);
//        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
//                .type(TypeRuntimeWiring.newTypeWiring("Query").defaultDataFetcher(dynResolver))
//                .type(TypeRuntimeWiring.newTypeWiring("Customer").defaultDataFetcher(new JsonDataFetcher()))
//                .build();
//        GraphQLSchema executableSchema = new SchemaGenerator().makeExecutableSchema(typeReg, runtimeWiring);
//        GraphQL graphQL = GraphQL.newGraphQL(executableSchema).build();
//        ExecutionResult result = graphQL.execute("query {\n" +
//                "  customers {\n" +
//                "      name\n" +
//                "  }\n" +
//                "}");
//
//        Map<String, Object> spec = result.toSpecification();
//        ObjectMapper objectMapper = new ObjectMapper();
//        System.out.println(objectMapper.writeValueAsString(spec).toString());
//        assertTrue(spec.toString().contains("Ban Geyton"));
//        assertTrue(spec.toString().contains("Ferrell Leethem"));
//        assertTrue(spec.toString().contains("Selinda Streader"));
//    }
}
