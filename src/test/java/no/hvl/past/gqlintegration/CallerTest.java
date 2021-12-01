package no.hvl.past.gqlintegration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.corrlang.domain.QueryHandler;
import io.corrlang.domain.Sys;
import no.hvl.past.gqlintegration.queries.GraphQLQuery;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.trees.QueryTree;
import no.hvl.past.names.Name;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CallerTest extends GraphQLTest {

    @Test
    public void testCalling() throws Exception {


        GraphQLAdapter adapter = createAdapter();
        Sys schema = adapter.parseSchema(Name.identifier("Sales"), "http://localhost:4011");

        GraphQLQuery.Node id = new GraphQLQuery.Node("id", Name.identifier("ID"));
        GraphQLQuery.Node name = new GraphQLQuery.Node("name", Name.identifier("String"));
        GraphQLQuery.QueryRoot customers = new GraphQLQuery.QueryRoot("customers", false, Triple.edge(Name.identifier("Query.customers"), Name.identifier("result").prefixWith(Name.identifier("Query.customers")), Name.identifier("Customer")));
        customers.addChild(id, Triple.edge(Name.identifier("Customer"), Name.identifier("id").prefixWith(Name.identifier("Customer")), Name.identifier("ID")),false, false);
        customers.addChild(name, Triple.edge(Name.identifier("Customer"), Name.identifier("name").prefixWith(Name.identifier("Customer")), Name.identifier("String")), false, false);
        GraphQLQuery query = new GraphQLQuery(Collections.singletonList(customers), schema, Name.anonymousIdentifier());


        QueryHandler queryHandler = adapter.queryHandler( schema);

        InputStream inputStream = queryHandler.resolveAsStream(query);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(inputStream).get("data");


        assertTrue(jsonNode.isObject());
        JsonNode customersResult = jsonNode.get("customers");
        assertTrue(customersResult.isArray());
        String nameResult = customersResult.get(0).get("name").asText();
        assertEquals("Ban Geyton", nameResult);
    }


    @Test
    public void testQueryParsing() throws Exception {
        GraphQLAdapter adapter = createAdapter();
        Sys schema = adapter.parseSchema(Name.identifier("Sales"), "http://localhost:4011");
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
        QueryHandler queryHandler = adapter.queryHandler( schema);
        QueryTree actual = (QueryTree) queryHandler.deserialize(new ByteArrayInputStream(bos.toByteArray()));


        GraphQLQuery.Node id = new GraphQLQuery.Node("id", Name.identifier("ID"));
        GraphQLQuery.Node name = new GraphQLQuery.Node("name", Name.identifier("String"));
        GraphQLQuery.QueryRoot customers = new GraphQLQuery.QueryRoot("customers", false, Triple.edge(Name.identifier("Query.customers"), Name.identifier("result").prefixWith(Name.identifier("Query.customers")), Name.identifier("Customer")));
        customers.addChild(id, Triple.edge(Name.identifier("Customer"), Name.identifier("id").prefixWith(Name.identifier("Customer")), Name.identifier("ID")), false, false);
        customers.addChild(name, Triple.edge(Name.identifier("Customer"), Name.identifier("name").prefixWith(Name.identifier("Customer")), Name.identifier("String")), false, false);
        GraphQLQuery expected = new GraphQLQuery(Collections.singletonList(customers), schema, Name.anonymousIdentifier());

        assertEquals(expected, actual);
        assertEquals(expected.textualRepresentation(), actual.textualRepresentation());
    }

    // TODO testcase with a field query



}
