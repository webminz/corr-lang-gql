package no.hvl.past.gqlintegration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.hvl.past.gqlintegration.queries.*;
import no.hvl.past.gqlintegration.schema.GraphQLSchemaReader;
import no.hvl.past.graph.*;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.names.Name;
import no.hvl.past.util.Pair;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class QuerySplittingTest extends GraphQLTest{


    private static final String ENDPOINT1_SCHEMA = "type Query {\n" +
            "\tr : [R1]\n" +
            "}\n" +
            "\n" +
            "type R1 {\n" +
            "\ta : [A1]\n" +
            "}\n" +
            "\n" +
            "type A1 {\n" +
            "\tx : String!\n" +
            "}";

    private static final String ENDPOINT2_SCHEMA = "type Query {\n" +
            "\tr : [R2]\n" +
            "}\n" +
            "\n" +
            "type R2 {\n" +
            "\ta : [A2]\n" +
            "\tb : [B2]\n" +
            "}\n" +
            "\n" +
            "type A2 {\n" +
            "\ty : Int!\n" +
            "}\n" +
            "\n" +
            "type B2 {\n" +
            "\tz2: String!\n" +
            "}";

    private static final String ENDPOINT3_SCHEMA = "type Query {\n" +
            "\tr : [R3]\n" +
            "}\n" +
            "\n" +
            "type R3 {\n" +
            "\tb : [B3]\n" +
            "}\n" +
            "\n" +
            "type B3 {\n" +
            "\tz3: String!\n" +
            "}";



    @Test
    public void testAllTogether() throws Exception {
        GraphQLSchemaReader converter = new GraphQLSchemaReader(getUniverseForTest());
        Sketch endpoint1Schema = converter.convert(Name.identifier("EP1"), parseSchemaAsText(ENDPOINT1_SCHEMA));
        Sketch endpoint2Schema = converter.convert(Name.identifier("EP2"), parseSchemaAsText(ENDPOINT2_SCHEMA));
        Sketch endpoint3Schema = converter.convert(Name.identifier("EP3"), parseSchemaAsText(ENDPOINT3_SCHEMA));

        GraphBuilders builders = new GraphBuilders(getUniverseForTest(), true, false);

        Sketch result = builders
                .edge(Name.identifier("Query"), Name.identifier("r").prefixWith(Name.identifier("Query")), Name.identifier("R"))
                .edge(Name.identifier("R"), Name.identifier("a").prefixWith(Name.identifier("R")), Name.identifier("A"))
                .edge(Name.identifier("R"), Name.identifier("b").prefixWith(Name.identifier("R")), Name.identifier("B"))
                .edge(Name.identifier("B"), Name.identifier("z").prefixWith(Name.identifier("B")), Name.identifier("String"))
                .graph(Name.identifier("Comm").absolute())
                .sketch(Name.identifier("Comm"))
                .getResult(Sketch.class);

        GraphMorphism p1 = builders.domain(result.carrier())
                .codomain(endpoint1Schema.carrier())
                .map(Name.identifier("Query"), Name.identifier("Query"))
                .map(Name.identifier("r").prefixWith(Name.identifier("Query")), Name.identifier("r").prefixWith(Name.identifier("Query")))
                .map(Name.identifier("R"), Name.identifier("R1"))
                .map(Name.identifier("a").prefixWith(Name.identifier("R")), Name.identifier("a").prefixWith(Name.identifier("R1")))
                .map(Name.identifier("A"), Name.identifier("A1"))
                .map(Name.identifier("String"), Name.identifier("String"))
                .morphism("Projection1")
                .getResult(GraphMorphism.class);

        GraphMorphism p2 = builders.domain(result.carrier())
                .codomain(endpoint2Schema.carrier())
                .map(Name.identifier("Query"), Name.identifier("Query"))
                .map(Name.identifier("r").prefixWith(Name.identifier("Query")), Name.identifier("r").prefixWith(Name.identifier("Query")))
                .map(Name.identifier("R"), Name.identifier("R2"))
                .map(Name.identifier("a").prefixWith(Name.identifier("R")), Name.identifier("a").prefixWith(Name.identifier("R2")))
                .map(Name.identifier("b").prefixWith(Name.identifier("R")), Name.identifier("b").prefixWith(Name.identifier("R2")))
                .map(Name.identifier("z").prefixWith(Name.identifier("B")), Name.identifier("z2").prefixWith(Name.identifier("B2")))
                .map(Name.identifier("A"), Name.identifier("A2"))
                .map(Name.identifier("B"), Name.identifier("B2"))
                .map(Name.identifier("String"), Name.identifier("String"))
                .morphism("Projection2")
                .getResult(GraphMorphism.class);

        GraphMorphism p3 = builders.domain(result.carrier())
                .codomain(endpoint3Schema.carrier())
                .map(Name.identifier("Query"), Name.identifier("Query"))
                .map(Name.identifier("r").prefixWith(Name.identifier("Query")), Name.identifier("r").prefixWith(Name.identifier("Query")))
                .map(Name.identifier("R"), Name.identifier("R3"))
                .map(Name.identifier("b").prefixWith(Name.identifier("R")), Name.identifier("b").prefixWith(Name.identifier("R3")))
                .map(Name.identifier("z").prefixWith(Name.identifier("B")), Name.identifier("z3").prefixWith(Name.identifier("B3")))
                .map(Name.identifier("B"), Name.identifier("B3"))
                .map(Name.identifier("String"), Name.identifier("String"))
                .morphism("Projection2")
                .getResult(GraphMorphism.class);

        Star federation = new StarImpl(Name.identifier("federation"),
                result,
                Arrays.asList(endpoint1Schema, endpoint2Schema, endpoint3Schema),
                Arrays.asList(p1, p2, p3),
                result.carrier().elements().map(Triple::getLabel).collect(Collectors.toSet()));


        GraphQLQueryHandler ep1Handler = new GraphQLQueryHandler() {
            @Override
            public String endpointUrl() {
                return "http://ep1";
            }

            @Override
            public Sketch getSchema() {
                return endpoint1Schema;
            }

            @Override
            public Map<Name, String> plainNames() {
                Map<Name, String> plainNames = new HashMap<>();
                plainNames.put(Name.identifier("Query"), "Query");
                plainNames.put(Name.identifier("r").prefixWith(Name.identifier("Query")), "r");
                plainNames.put(Name.identifier("String"), "String");
                plainNames.put(Name.identifier("R1"), "R1");
                plainNames.put(Name.identifier("a").prefixWith(Name.identifier("R1")), "a");
                plainNames.put(Name.identifier("A1"), "A1");
                plainNames.put(Name.identifier("x").prefixWith(Name.identifier("A1")), "x");
                return plainNames;
            }

            @Override
            public void handle(InputStream i, OutputStream o) throws IOException {
                GraphQLQuery actual = parse(i);
                GraphQLQuery expected = new GraphQLQuery(
                        new GraphQLQueryNode.Builder(Name.identifier("query"))
                                    .startChild(Name.identifier("r").prefixWith(Name.identifier("Query")))
                                        .startChild(Name.identifier("a").prefixWith(Name.identifier("R1")))
                                            .startChild(Name.identifier("x").prefixWith(Name.identifier("A1")))
                                            .endChild()
                                        .endChild()
                                    .endChild());
                assertEquals(expected.textualRepresentation(), actual.textualRepresentation());
                JsonGenerator generator = new JsonFactory().createGenerator(o);
                generator.writeStartObject();
                    generator.writeFieldName("data");
                    generator.writeStartObject(); // Q1
                        generator.writeFieldName("r");
                        generator.writeStartArray();
                            generator.writeStartObject(); // /1:R1
                                generator.writeFieldName("a");
                                generator.writeStartArray();
                                    generator.writeStartObject(); // /1/1:A1
                                        generator.writeFieldName("x");
                                        generator.writeString("A");
                                    generator.writeEndObject();
                                    generator.writeStartObject(); // /1/2:A2
                                        generator.writeFieldName("x");
                                        generator.writeString("A'");
                                    generator.writeEndObject();
                                generator.writeEndArray();
                            generator.writeEndObject();
                            generator.writeStartObject(); // /2:R1
                                generator.writeFieldName("a");
                                generator.writeStartArray();
                                    generator.writeStartObject(); // /2/2:A1
                                        generator.writeFieldName("x");
                                        generator.writeString("B");
                                    generator.writeEndObject();
                                generator.writeEndArray();
                            generator.writeEndObject();
                        generator.writeEndArray();
                    generator.writeEndObject();
                generator.writeEndObject();
                generator.flush();
                o.close();
            }
        };

        GraphQLQueryHandler ep2Handler = new GraphQLQueryHandler() {
            @Override
            public String endpointUrl() {
                return "http://ep2";
            }

            @Override
            public Sketch getSchema() {
                return endpoint2Schema;
            }

            @Override
            public Map<Name, String> plainNames() {
                Map<Name, String> plainNames = new HashMap<>();
                plainNames.put(Name.identifier("Query"), "Query");
                plainNames.put(Name.identifier("r").prefixWith(Name.identifier("Query")), "r");
                plainNames.put(Name.identifier("String"), "String");
                plainNames.put(Name.identifier("Int"), "Int");
                plainNames.put(Name.identifier("R2"), "R2");
                plainNames.put(Name.identifier("a").prefixWith(Name.identifier("R2")), "a");
                plainNames.put(Name.identifier("b").prefixWith(Name.identifier("R2")), "b");
                plainNames.put(Name.identifier("A2"), "A2");
                plainNames.put(Name.identifier("B2"), "B2");
                plainNames.put(Name.identifier("y").prefixWith(Name.identifier("A2")), "y");
                plainNames.put(Name.identifier("z2").prefixWith(Name.identifier("B2")), "z2");
                return plainNames;
            }


            @Override
            public void handle(InputStream i, OutputStream o) throws IOException {
                GraphQLQuery actual = parse(i);
                GraphQLQuery excpected = new GraphQLQuery(
                        new GraphQLQueryNode.Builder(Name.identifier("query"))
                                .startChild(Name.identifier("r").prefixWith(Name.identifier("Query")))
                                    .startChild(Name.identifier("a").prefixWith(Name.identifier("R2")))
                                        .startChild(Name.identifier("y").prefixWith(Name.identifier("A2")))
                                        .endChild()
                                    .endChild()
                                    .startChild(Name.identifier("b").prefixWith(Name.identifier("R2")))
                                        .startChild(Name.identifier("z2").prefixWith(Name.identifier("B2")))
                                        .endChild()
                                    .endChild()
                                .endChild()
                );
                assertEquals(excpected.textualRepresentation(), actual.textualRepresentation());
                JsonGenerator generator = new JsonFactory().createGenerator(o);
                generator.writeStartObject();
                    generator.writeFieldName("data");
                    generator.writeStartObject(); // Q2
                        generator.writeFieldName("r");
                        generator.writeStartArray();
                            generator.writeStartObject(); // 1:R2
                                generator.writeFieldName("a");
                                generator.writeStartArray();
                                    generator.writeStartObject(); // 1:A2
                                        generator.writeFieldName("y");
                                        generator.writeNumber(42);
                                    generator.writeEndObject();
                                generator.writeEndArray();
                                generator.writeFieldName("b");
                                generator.writeStartArray();
                                    generator.writeStartObject();
                                        generator.writeFieldName("z2");
                                        generator.writeString("C");
                                    generator.writeEndObject();
                                generator.writeEndArray();
                            generator.writeEndObject();
                        generator.writeEndArray();
                    generator.writeEndObject();
                generator.writeEndObject();
                generator.flush();
                o.close();
            }
        };

        GraphQLQueryHandler ep3Handler = new GraphQLQueryHandler() {
            @Override
            public String endpointUrl() {
                return "http://ep3";
            }

            @Override
            public Sketch getSchema() {
                return endpoint3Schema;
            }

            @Override
            public Map<Name, String> plainNames() {
                Map<Name, String> plainNames = new HashMap<>();
                plainNames.put(Name.identifier("Query"), "Query");
                plainNames.put(Name.identifier("r").prefixWith(Name.identifier("Query")), "r");
                plainNames.put(Name.identifier("String"), "String");
                plainNames.put(Name.identifier("R3"), "R3");
                plainNames.put(Name.identifier("b").prefixWith(Name.identifier("R3")), "b");
                plainNames.put(Name.identifier("B3"), "B3");
                plainNames.put(Name.identifier("z3").prefixWith(Name.identifier("B3")), "z3");
                return plainNames;
            }

            @Override
            public void handle(InputStream i, OutputStream o) throws IOException {
                GraphQLQuery actual = parse(i);
                GraphQLQuery excpected = new GraphQLQuery(
                        new GraphQLQueryNode.Builder(Name.identifier("query"))
                                .startChild(Name.identifier("r").prefixWith(Name.identifier("Query")))
                                    .startChild(Name.identifier("b").prefixWith(Name.identifier("R3")))
                                        .startChild(Name.identifier("z3").prefixWith(Name.identifier("B3")))
                                        .endChild()
                                    .endChild()
                                .endChild()
                );
                assertEquals(excpected.textualRepresentation(), actual.textualRepresentation());
                JsonGenerator generator = new JsonFactory().createGenerator(o);
                generator.writeStartObject();
                    generator.writeFieldName("data");
                    generator.writeStartObject(); // Q3
                        generator.writeFieldName("r");
                        generator.writeStartArray();
                            generator.writeStartObject(); // /1:R3
                                generator.writeFieldName("b");
                                generator.writeStartArray();
                                    generator.writeStartObject(); // /1/1:B3
                                        generator.writeFieldName("z3");
                                        generator.writeString("C");
                                    generator.writeEndObject();
                                generator.writeEndArray();
                            generator.writeEndObject();
                            generator.writeStartObject(); // /2:R3
                                generator.writeFieldName("b");
                                generator.writeStartArray();
                                    generator.writeStartObject(); // /2/1:B3
                                        generator.writeFieldName("z3");
                                        generator.writeString("D");
                                    generator.writeEndObject();
                                generator.writeEndArray();
                            generator.writeEndObject();
                        generator.writeEndArray();
                    generator.writeEndObject();
                generator.writeEndObject();
                generator.flush();
                o.close();
            }
        };


        Pair<Sketch, List<GraphMorphism>> fedResult = federation.comprehensiveSystem();

        Map<GraphMorphism, GraphQLQueryHandler> translationMap = new LinkedHashMap<>();
        translationMap.put(fedResult.getRight().get(1), ep1Handler);
        translationMap.put(fedResult.getRight().get(2), ep2Handler);
        translationMap.put(fedResult.getRight().get(3), ep3Handler);


        Map<Name, String> plainNames = new HashMap<>();
        plainNames.put(Name.identifier("Query"), "Query");
        plainNames.put(Name.identifier("String"), "String");
        plainNames.put(Name.identifier("Int"), "Int");
        plainNames.put(Name.identifier("r").prefixWith(Name.identifier("Query")), "r");
        plainNames.put(Name.identifier("R"), "R");
        plainNames.put(Name.identifier("a").prefixWith(Name.identifier("R")), "a");
        plainNames.put(Name.identifier("b").prefixWith(Name.identifier("R")), "b");
        plainNames.put(Name.identifier("A"), "A");
        plainNames.put(Name.identifier("B"), "B");
        plainNames.put(Name.identifier("z").prefixWith(Name.identifier("B")), "z");
        plainNames.put(Name.identifier("x").prefixWith(Name.identifier("A1")), "x");
        plainNames.put(Name.identifier("y").prefixWith(Name.identifier("A2")), "y");

        GraphQLQueryHandler queryDivider = new GraphQLQueryDivider(
                "http://federation",
                fedResult.getFirst(),
                plainNames,
                translationMap,
                new HashMap<>());

        GraphQLQuery query = new GraphQLQuery(
                new GraphQLQueryNode.Builder(Name.identifier("query"))
                        .startChild(Name.identifier("r").prefixWith(Name.identifier("Query")))
                            .startChild(Name.identifier("a").prefixWith(Name.identifier("R")))
                                .startChild(Name.identifier("x").prefixWith(Name.identifier("A1")))
                                .endChild()
                                .startChild(Name.identifier("y").prefixWith(Name.identifier("A2")))
                                .endChild()
                            .endChild()
                            .startChild(Name.identifier("b").prefixWith(Name.identifier("R")))
                                .startChild(Name.identifier("z").prefixWith(Name.identifier("B")))
                                .endChild()
                            .endChild()
                        .endChild()
        );


        ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
        queryDivider.resolve(query, bos1);

        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        JsonGenerator generator = new JsonFactory().createGenerator(bos2);
        generator.writeStartObject();
            generator.writeFieldName("data");
            generator.writeStartObject();
                generator.writeFieldName("r");
                generator.writeStartArray();
                    generator.writeStartObject(); // /1:R1
                        generator.writeFieldName("a");
                        generator.writeStartArray();
                            generator.writeStartObject(); // /1/1:A1
                                generator.writeFieldName("x");
                                generator.writeString("A");
                            generator.writeEndObject();
                            generator.writeStartObject(); // /1/2:A2
                                generator.writeFieldName("x");
                                generator.writeString("A'");
                            generator.writeEndObject();
                        generator.writeEndArray();
                    generator.writeEndObject();
                    generator.writeStartObject(); // /2:R1
                        generator.writeFieldName("a");
                        generator.writeStartArray();
                            generator.writeStartObject(); // /2/2:A1
                                generator.writeFieldName("x");
                                generator.writeString("B");
                            generator.writeEndObject();
                        generator.writeEndArray();
                    generator.writeEndObject();
                    generator.writeStartObject(); // 1:R2
                            generator.writeFieldName("a");
                            generator.writeStartArray();
                                generator.writeStartObject(); // 1:A2
                                    generator.writeFieldName("y");
                                    generator.writeNumber(42);
                                generator.writeEndObject();
                            generator.writeEndArray();
                            generator.writeFieldName("b");
                            generator.writeStartArray();
                                generator.writeStartObject();
                                    generator.writeFieldName("z");
                                    generator.writeString("C");
                                generator.writeEndObject();
                            generator.writeEndArray();
                    generator.writeEndObject();
                    generator.writeStartObject(); // /1:R3
                        generator.writeFieldName("b");
                        generator.writeStartArray();
                             generator.writeStartObject(); // /1/1:B3
                                generator.writeFieldName("z");
                                generator.writeString("C");
                            generator.writeEndObject();
                        generator.writeEndArray();
                    generator.writeEndObject();
                    generator.writeStartObject(); // /2:R3
                        generator.writeFieldName("b");
                            generator.writeStartArray();
                                generator.writeStartObject(); // /2/1:B3
                                    generator.writeFieldName("z");
                                    generator.writeString("D");
                                generator.writeEndObject();
                            generator.writeEndArray();
                    generator.writeEndObject();
                generator.writeEndArray();
            generator.writeEndObject();
         generator.writeEndObject();
         generator.flush();
         bos2.close();


        ObjectMapper om = new ObjectMapper();

        assertEquals(om.readTree(bos2.toByteArray()).toPrettyString(), om.readTree(bos1.toByteArray()).toPrettyString());

    }


}
