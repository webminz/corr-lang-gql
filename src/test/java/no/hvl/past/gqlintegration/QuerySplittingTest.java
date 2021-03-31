package no.hvl.past.gqlintegration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.hvl.past.gqlintegration.queries.*;
import no.hvl.past.gqlintegration.schema.GraphQLSchemaReader;
import no.hvl.past.graph.*;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.trees.QueryHandler;
import no.hvl.past.graph.trees.Tree;
import no.hvl.past.names.Name;
import no.hvl.past.systems.ComprSys;
import no.hvl.past.systems.Sys;
import no.hvl.past.util.Pair;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        JsonFactory jsonFactory = new JsonFactory();
        ObjectMapper om = new ObjectMapper(jsonFactory);

        GraphQLSchemaReader converter = new GraphQLSchemaReader(getUniverseForTest());
        Sketch endpoint1Schema = converter.convert(Name.identifier("EP1"), parseSchemaAsText(ENDPOINT1_SCHEMA));
        Sketch endpoint2Schema = converter.convert(Name.identifier("EP2"), parseSchemaAsText(ENDPOINT2_SCHEMA));
        Sketch endpoint3Schema = converter.convert(Name.identifier("EP3"), parseSchemaAsText(ENDPOINT3_SCHEMA));

        GraphBuilders builders = new GraphBuilders(getUniverseForTest(), true, false);


        Map<Name, String> ep1PlainNames = new HashMap<>();
        ep1PlainNames.put(Name.identifier("Query.r"), "r");
        ep1PlainNames.put(Name.identifier("result").prefixWith(Name.identifier("Query.r")), "r");
        ep1PlainNames.put(Name.identifier("String"), "String");
        ep1PlainNames.put(Name.identifier("R1"), "R1");
        ep1PlainNames.put(Name.identifier("a").prefixWith(Name.identifier("R1")), "a");
        ep1PlainNames.put(Name.identifier("A1"), "A1");
        ep1PlainNames.put(Name.identifier("x").prefixWith(Name.identifier("A1")), "x");


        Map<Name, String> ep2PlainNames = new HashMap<>();
        ep2PlainNames.put(Name.identifier("Query.r"), "r");
        ep2PlainNames.put(Name.identifier("result").prefixWith(Name.identifier("Query.r")), "r");
        ep2PlainNames.put(Name.identifier("String"), "String");
        ep2PlainNames.put(Name.identifier("Int"), "Int");
        ep2PlainNames.put(Name.identifier("R2"), "R2");
        ep2PlainNames.put(Name.identifier("a").prefixWith(Name.identifier("R2")), "a");
        ep2PlainNames.put(Name.identifier("b").prefixWith(Name.identifier("R2")), "b");
        ep2PlainNames.put(Name.identifier("A2"), "A2");
        ep2PlainNames.put(Name.identifier("B2"), "B2");
        ep2PlainNames.put(Name.identifier("y").prefixWith(Name.identifier("A2")), "y");
        ep2PlainNames.put(Name.identifier("z2").prefixWith(Name.identifier("B2")), "z2");

        Map<Name, String> ep3PlainNames = new HashMap<>();
        ep3PlainNames.put(Name.identifier("Query.r"), "r");
        ep3PlainNames.put(Name.identifier("result").prefixWith(Name.identifier("Query.r")), "r");

        ep3PlainNames.put(Name.identifier("String"), "String");
        ep3PlainNames.put(Name.identifier("R3"), "R3");
        ep3PlainNames.put(Name.identifier("b").prefixWith(Name.identifier("R3")), "b");
        ep3PlainNames.put(Name.identifier("B3"), "B3");
        ep3PlainNames.put(Name.identifier("z3").prefixWith(Name.identifier("B3")), "z3");

        Sys s1 = new Sys() {
            @Override
            public String displayName(Name name) {
                return ep1PlainNames.get(name);
            }

            @Override
            public Optional<Triple> lookup(String... path) {
                if (path[0].equals("Query")) {
                    return endpoint1Schema.carrier().get(Name.identifier("Query." + path[1]));
                }
                if (path.length == 2) {
                    return endpoint2Schema.carrier().outgoing(Name.identifier(path[0])).filter(t -> ep3PlainNames.get(t.getLabel()).equals(path[1])).findFirst();
                }
                if (path.length == 1) {
                    return endpoint1Schema.carrier().get(Name.identifier(path[0]));
                }
                return Optional.empty();
            }

            @Override
            public Sketch schema() {
                return endpoint1Schema;
            }

            @Override
            public String url() {
                return "http://ep1";
            }
        };

        Sys s2 = new Sys() {
            @Override
            public String displayName(Name name) {
                return ep2PlainNames.get(name);
            }

            @Override
            public Optional<Triple> lookup(String... path) {
                if (path[0].equals("Query")) {
                    return endpoint2Schema.carrier().get(Name.identifier("Query." + path[1]));
                }
                if (path.length == 2) {
                    return endpoint2Schema.carrier().outgoing(Name.identifier(path[0])).filter(t -> ep2PlainNames.get(t.getLabel()).equals(path[1])).findFirst();
                }
                if (path.length == 1) {
                    return endpoint2Schema.carrier().get(Name.identifier(path[0]));
                }
                return Optional.empty();
            }

            @Override
            public Sketch schema() {
                return endpoint2Schema;
            }

            @Override
            public String url() {
                return "http://ep2";
            }
        };

        Sys s3 = new Sys() {
            @Override
            public String displayName(Name name) {
                return ep3PlainNames.get(name);
            }

            @Override
            public Optional<Triple> lookup(String... path) {
                if (path[0].equals("Query")) {
                    return endpoint3Schema.carrier().get(Name.identifier("Query." + path[1]));
                }
                if (path.length == 2) {
                    return endpoint3Schema.carrier().outgoing(Name.identifier(path[0])).filter(t -> ep3PlainNames.get(t.getLabel()).equals(path[1])).findFirst();
                }
                if (path.length == 1) {
                    return endpoint3Schema.carrier().get(Name.identifier(path[0]));
                }
                return Optional.empty();
            }

            @Override
            public Sketch schema() {
                return endpoint3Schema;
            }

            @Override
            public String url() {
                return "http://ep3";
            }
        };


        Sketch result = builders
                .edge(Name.identifier("Query.r"), Name.identifier("result").prefixWith(Name.identifier("Query.r")), Name.identifier("R"))
                .edge(Name.identifier("R"), Name.identifier("a").prefixWith(Name.identifier("R")), Name.identifier("A"))
                .edge(Name.identifier("R"), Name.identifier("b").prefixWith(Name.identifier("R")), Name.identifier("B"))
                .edge(Name.identifier("B"), Name.identifier("z").prefixWith(Name.identifier("B")), Name.identifier("String"))
                .graph(Name.identifier("Comm").absolute())
                .sketch(Name.identifier("Comm"))
                .getResult(Sketch.class);

        GraphMorphism p1 = builders.domain(result.carrier())
                .codomain(endpoint1Schema.carrier())
                .map(Name.identifier("Query.r"), Name.identifier("Query.r"))
                .map(Name.identifier("result").prefixWith(Name.identifier("Query.r")), Name.identifier("result").prefixWith(Name.identifier("Query.r")))
                .map(Name.identifier("R"), Name.identifier("R1"))
                .map(Name.identifier("a").prefixWith(Name.identifier("R")), Name.identifier("a").prefixWith(Name.identifier("R1")))
                .map(Name.identifier("A"), Name.identifier("A1"))
                .map(Name.identifier("String"), Name.identifier("String"))
                .morphism("Projection1")
                .getResult(GraphMorphism.class);

        GraphMorphism p2 = builders.domain(result.carrier())
                .codomain(endpoint2Schema.carrier())
                .map(Name.identifier("Query.r"), Name.identifier("Query.r"))
                .map(Name.identifier("result").prefixWith(Name.identifier("Query.r")), Name.identifier("result").prefixWith(Name.identifier("Query.r")))
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
                .map(Name.identifier("Query.r"), Name.identifier("Query.r"))
                .map(Name.identifier("result").prefixWith(Name.identifier("Query.r")), Name.identifier("result").prefixWith(Name.identifier("Query.r")))
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


        GraphQLQueryHandler ep1Handler = new GraphQLQueryHandler(null) { // TODO fix, just to get the compile error away


            @Override
            protected String displayName(Name formalName) {
                return ep1PlainNames.get(formalName);
            }


            @Override
            public void handle(InputStream i, OutputStream o) throws IOException {
                Tree tree = deserialize(i);
                assertTrue(tree instanceof GraphQLQuery);
                GraphQLQuery actual = (GraphQLQuery) tree;
                GraphQLQuery.QueryRoot root = new GraphQLQuery.QueryRoot("r", false, Triple.edge(Name.identifier("Query.r"), Name.identifier("result").prefixWith(Name.identifier("Query.r")), Name.identifier("R1")));
                GraphQLQuery.Node a = new GraphQLQuery.Node("a", Name.identifier("A1"));
                GraphQLQuery.Node x = new GraphQLQuery.Node("x", Name.identifier("String"));
                root.addChild(a, Triple.edge(Name.identifier("R1"), Name.identifier("a").prefixWith(Name.identifier("R1")), Name.identifier("A1")), true, true);
                a.addChild(x, Triple.edge(Name.identifier("A1"), Name.identifier("x").prefixWith(Name.identifier("A1")), Name.identifier("String")), false, false);
                GraphQLQuery expected = new GraphQLQuery(Collections.singletonList(root), endpoint1Schema, Name.anonymousIdentifier());

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

        GraphQLQueryHandler ep2Handler = new GraphQLQueryHandler(null) { // TODO fix



            @Override
            protected String displayName(Name formalName) {
                return ep2PlainNames.get(formalName);
            }

            @Override
            public void handle(InputStream i, OutputStream o) throws IOException {
                Tree tree = deserialize(i);
                assertTrue(tree instanceof GraphQLQuery);
                GraphQLQuery actual = (GraphQLQuery) tree;
                GraphQLQuery.QueryRoot root = new GraphQLQuery.QueryRoot("r", false, Triple.edge(Name.identifier("Query.r"), Name.identifier("result").prefixWith(Name.identifier("Query.r")), Name.identifier("R2")));
                GraphQLQuery.Node a = new GraphQLQuery.Node("a", Name.identifier("A2"));
                GraphQLQuery.Node b = new GraphQLQuery.Node("b", Name.identifier("B2"));
                GraphQLQuery.Node y = new GraphQLQuery.Node("y", Name.identifier("Int"));
                GraphQLQuery.Node z = new GraphQLQuery.Node("z2", Name.identifier("String"));
                root.addChild(a, Triple.edge(Name.identifier("R2"), Name.identifier("a").prefixWith(Name.identifier("R2")), Name.identifier("A2")), true, true);
                root.addChild(b, Triple.edge(Name.identifier("R2"), Name.identifier("b").prefixWith(Name.identifier("R2")), Name.identifier("B2")), true, true);
                a.addChild(y, Triple.edge(Name.identifier("A2"), Name.identifier("y").prefixWith(Name.identifier("A2")), Name.identifier("Int")), false, false);
                b.addChild(z, Triple.edge(Name.identifier("B2"), Name.identifier("z2").prefixWith(Name.identifier("B2")), Name.identifier("String")), false, false);
                GraphQLQuery expected = new GraphQLQuery(Collections.singletonList(root), endpoint2Schema, Name.anonymousIdentifier());
                assertEquals(expected.textualRepresentation(), actual.textualRepresentation());
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

        GraphQLQueryHandler ep3Handler = new GraphQLQueryHandler(null) { // TODO fix


            @Override
            protected String displayName(Name formalName) {

                return ep3PlainNames.get(formalName);
            }


            @Override
            public void handle(InputStream i, OutputStream o) throws IOException {
                Tree tree = deserialize(i);
                assertTrue(tree instanceof GraphQLQuery);
                GraphQLQuery actual = (GraphQLQuery) tree;

                GraphQLQuery.QueryRoot root = new GraphQLQuery.QueryRoot("r", false, Triple.edge(Name.identifier("Query.r"), Name.identifier("result").prefixWith(Name.identifier("Query.r")), Name.identifier("R3")));
                GraphQLQuery.Node b = new GraphQLQuery.Node("b", Name.identifier("B3"));
                GraphQLQuery.Node z = new GraphQLQuery.Node("z3", Name.identifier("String"));
                root.addChild(b, Triple.edge(Name.identifier("R3"), Name.identifier("b").prefixWith(Name.identifier("R3")), Name.identifier("B3")), true, true);
                b.addChild(z, Triple.edge(Name.identifier("B3"), Name.identifier("z3").prefixWith(Name.identifier("B3")), Name.identifier("String")), false, false);
                GraphQLQuery expected = new GraphQLQuery(Collections.singletonList(root), endpoint3Schema, Name.anonymousIdentifier());

                assertEquals(expected.textualRepresentation(), actual.textualRepresentation());
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

        Pair<Sketch, List<GraphMorphism>> fedResult = federation.comprehensiveSystem();


        Map<Sys, GraphMorphism> embeddings = new LinkedHashMap<>();
        embeddings.put(s1, fedResult.getSecond().get(1));
        embeddings.put(s2, fedResult.getSecond().get(2));
        embeddings.put(s3, fedResult.getSecond().get(3));

        LinkedHashMap<Sys, QueryHandler> localHandlers = new LinkedHashMap<>();
        localHandlers.put(s1, ep1Handler);
        localHandlers.put(s2, ep2Handler);
        localHandlers.put(s3, ep3Handler);

        ComprSys cs = new ComprSys.Impl("http://federation", fedResult.getFirst(), plainNames, embeddings, result.carrier().elements().map(Triple::getLabel).collect(Collectors.toSet()), new HashSet<>());


        GraphQLQueryHandler queryDivider = GraphQLQueryDivider.create(om, jsonFactory, cs, localHandlers);

        GraphQLQuery.QueryRoot root = new GraphQLQuery.QueryRoot("r", false, Triple.edge(Name.identifier("Query.r"), Name.identifier("result").prefixWith(Name.identifier("Query.r")), Name.identifier("R")));
        GraphQLQuery.Node a = new GraphQLQuery.Node("a", Name.identifier("A"));
        GraphQLQuery.Node b = new GraphQLQuery.Node("b", Name.identifier("B"));
        GraphQLQuery.Node x = new GraphQLQuery.Node("x", Name.identifier("String"));
        GraphQLQuery.Node y = new GraphQLQuery.Node("y", Name.identifier("Int"));
        GraphQLQuery.Node z = new GraphQLQuery.Node("z", Name.identifier("String"));
        a.addChild(x, Triple.edge(Name.identifier("A"), Name.identifier("x").prefixWith(Name.identifier("A1")), Name.identifier("String")), false, false);
        a.addChild(y, Triple.edge(Name.identifier("A"), Name.identifier("y").prefixWith(Name.identifier("A2")), Name.identifier("Int")), false, false);
        b.addChild(z, Triple.edge(Name.identifier("B"), Name.identifier("z").prefixWith(Name.identifier("B")), Name.identifier("String")), false, false);
        root.addChild(a, Triple.edge(Name.identifier("R"), Name.identifier("a").prefixWith(Name.identifier("R")), Name.identifier("A")), true, true);
        root.addChild(b, Triple.edge(Name.identifier("R"), Name.identifier("b").prefixWith(Name.identifier("R")), Name.identifier("B")), true, true);

        GraphQLQuery query = new GraphQLQuery(Collections.singletonList(root), cs.schema(), Name.anonymousIdentifier());



        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        queryDivider.resolve(query, actual);

        String expected = "{\n" +
                "  \"data\" : {\n" +
                "    \"r\" : [ \n" +
                "    {\n" +
                "      \"a\" : [ {\n" +
                "        \"x\" : \"A\",\n" +
                "        \"y\" : null\n" +
                "      }, {\n" +
                "        \"x\" : \"A'\",\n" +
                "        \"y\" : null\n" +
                "      } ],\n" +
                "      \"b\" : [ ]\n" +
                "    }, \n" +
                "    {\n" +
                "      \"a\" : [ {\n" +
                "        \"x\" : \"B\",\n" +
                "        \"y\" : null\n" +
                "      } ],\n" +
                "      \"b\" : [ ]\n" +
                "    }, \n" +
                "    {\n" +
                "      \"a\" : [ {\n" +
                "        \"x\" : null,\n" +
                "        \"y\" : 42\n" +
                "      } ],\n" +
                "      \"b\" : [ {\n" +
                "        \"z\" : \"C\"\n" +
                "      } ]\n" +
                "    }, \n" +
                "    {\n" +
                "      \"a\" : [ ],\n" +
                "      \"b\" : [ {\n" +
                "        \"z\" : \"C\"\n" +
                "      } ]\n" +
                "    }, \n" +
                "    {\n" +
                "      \"a\" : [ ],\n" +
                "      \"b\" : [ {\n" +
                "        \"z\" : \"D\"\n" +
                "      } ]\n" +
                "    } \n" +
                "    ]\n" +
                "  }\n" +
                "}";




        assertEquals(om.readTree(expected).toPrettyString(), om.readTree(actual.toByteArray()).toPrettyString());

    }


}
