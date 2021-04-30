package no.hvl.past.gqlintegration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import no.hvl.past.gqlintegration.queries.GraphQLQuery;
import no.hvl.past.gqlintegration.queries.QueryCursor;
import no.hvl.past.graph.Sketch;
import no.hvl.past.graph.Universe;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.keys.AttributeBasedKey;
import no.hvl.past.keys.ConcatenatedKey;
import no.hvl.past.keys.ConstantKey;
import no.hvl.past.keys.Key;
import no.hvl.past.names.Name;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;

public class QueryTest  extends GraphQLTest {

    private static final String CUSTOMER_RESPONSE_DATA = "{\n" +
            "  \"data\": {\n" +
            "    \"customers\": [\n" +
            "      {\n" +
            "        \"id\": \"1\",\n" +
            "        \"name\": \"Ban Geyton\",\n" +
            "        \"purchases\": [\n" +
            "          {\n" +
            "            \"id\": \"1\"\n" +
            "          },\n" +
            "          {\n" +
            "            \"id\": \"2\"\n" +
            "          }\n" +
            "        ]\n" +
            "      },\n" +
            "      {\n" +
            "        \"id\": \"2\",\n" +
            "        \"name\": \"Ferrell Leethem\",\n" +
            "        \"purchases\": [\n" +
            "          {\n" +
            "            \"id\": \"3\"\n" +
            "          },\n" +
            "          {\n" +
            "            \"id\": \"4\"\n" +
            "          },\n" +
            "          {\n" +
            "            \"id\": \"5\"\n" +
            "          }\n" +
            "        ]\n" +
            "      },\n" +
            "      {\n" +
            "        \"id\": \"3\",\n" +
            "        \"name\": \"Selinda Streader\",\n" +
            "        \"purchases\": [\n" +
            "          {\n" +
            "            \"id\": \"6\"\n" +
            "          }\n" +
            "        ]\n" +
            "      }\n" +
            "    ]\n" +
            "  }\n" +
            "}";

    private static final String CLIENT_RESPONSE_DATA = "{\n" +
            "  \"data\": {\n" +
            "    \"clients\": [\n" +
            "      {\n" +
            "        \"id\": \"1\",\n" +
            "        \"name\": \"Ban Geyton\",\n" +
            "        \"invoices\": [\n" +
            "          {\n" +
            "            \"id\": \"1\"\n" +
            "          },\n" +
            "          {\n" +
            "            \"id\": \"2\"\n" +
            "          }\n" +
            "        ]\n" +
            "      },\n" +
            "      {\n" +
            "        \"id\": \"2\",\n" +
            "        \"name\": \"Ferrell Leethem\",\n" +
            "        \"invoices\": [\n" +
            "          {\n" +
            "            \"id\": \"3\"\n" +
            "          }\n" +
            "        ]\n" +
            "      }\n" +
            "    ]\n" +
            "  }\n" +
            "}";

    private static final String EMPLOYEE_RESPONSE_DATA = "{\n" +
            "  \"data\": {\n" +
            "    \"employees\": [\n" +
            "      {\n" +
            "        \"id\": \"1\",\n" +
            "        \"firstname\": \"Tabbi\",\n" +
            "        \"lastname\": \"Witt\",\n" +
            "        \"worksAt\": {\n" +
            "          \"name\": \"Sales\"\n" +
            "        }\n" +
            "      },\n" +
            "      {\n" +
            "        \"id\": \"2\",\n" +
            "        \"firstname\": \"Venus\",\n" +
            "        \"lastname\": \"Ferenczy\",\n" +
            "        \"worksAt\": {\n" +
            "          \"name\": \"Sales\"\n" +
            "        }\n" +
            "      },\n" +
            "      {\n" +
            "        \"id\": \"3\",\n" +
            "        \"firstname\": \"Gregorio\",\n" +
            "        \"lastname\": \"Phythian\",\n" +
            "        \"worksAt\": {\n" +
            "          \"name\": \"IT\"\n" +
            "        }\n" +
            "      },\n" +
            "      {\n" +
            "        \"id\": \"4\",\n" +
            "        \"firstname\": \"Beverlie\",\n" +
            "        \"lastname\": \"Pim\",\n" +
            "        \"worksAt\": {\n" +
            "          \"name\": \"IT\"\n" +
            "        }\n" +
            "      },\n" +
            "      {\n" +
            "        \"id\": \"5\",\n" +
            "        \"firstname\": \"Ferrell\",\n" +
            "        \"lastname\": \"Leethem\",\n" +
            "        \"worksAt\": {\n" +
            "          \"name\": \"HR\"\n" +
            "        }\n" +
            "      }\n" +
            "    ]\n" +
            "  }\n" +
            "}";


    private static final String PATIENT_OBSERVATION_JSON = "{\n" +
            "\t\"data\" : {\n" +
            "\t\t\"patients\" : [\n" +
            "\t\t\t{\n" +
            "\t\t\t\t\"id\" : \"1\",\n" +
            "\t\t\t\t\"observations\" : [\n" +
            "\t\t\t\t\t{\n" +
            "\t\t\t\t\t\t\"coding\" : {\n" +
            "\t\t\t\t\t\t\t\"code\" : \"89123\",\n" +
            "\t\t\t\t\t\t\t\"system\" : \"LOINC\"\n" +
            "\t\t\t\t\t\t},\n" +
            "\t\t\t\t\t\t\"valueQuantity\" : {\n" +
            "\t\t\t\t\t\t\t\"value\" : 3.4,\n" +
            "\t\t\t\t\t\t\t\"unit\" : \"kg\"\n" +
            "\t\t\t\t\t\t}\n" +
            "\t\t\t\t\t}\n" +
            "\t\t\t\t]\n" +
            "\t\t\t},\n" +
            "\t\t\t{\n" +
            "\t\t\t\t\"id\" : \"3\",\n" +
            "\t\t\t\t\"observations\" : [\n" +
            "\t\t\t\t\t{\n" +
            "\t\t\t\t\t\t\"coding\" : {\n" +
            "\t\t\t\t\t\t\t\"code\" : \"9987-1\",\n" +
            "\t\t\t\t\t\t\t\"system\" : \"LOINC\"\n" +
            "\t\t\t\t\t\t},\n" +
            "\t\t\t\t\t\t\"valueQuantity\" : {\n" +
            "\t\t\t\t\t\t\t\"value\" : 20,\n" +
            "\t\t\t\t\t\t\t\"unit\" : \"cm\"\n" +
            "\t\t\t\t\t\t}\n" +
            "\t\t\t\t\t},\n" +
            "\t\t\t\t\t{\n" +
            "\t\t\t\t\t\t\"coding\" : {\n" +
            "\t\t\t\t\t\t\t\"code\" : \"2347\",\n" +
            "\t\t\t\t\t\t\t\"system\" : \"LOINC\"\n" +
            "\t\t\t\t\t\t},\n" +
            "\t\t\t\t\t\t\"valueQuantity\" : {\n" +
            "\t\t\t\t\t\t\t\"value\" : 120,\n" +
            "\t\t\t\t\t\t\t\"unit\" : \"bps\"\n" +
            "\t\t\t\t\t\t}\n" +
            "\t\t\t\t\t}\n" +
            "\t\t\t\t]\n" +
            "\t\t\t},\n" +
            "\t\t\t{\n" +
            "\t\t\t\t\"id\" : \"5\",\n" +
            "\t\t\t\t\"overvations\" : []\n" +
            "\t\t\t}\n" +
            "\t\t]\n" +
            "\t}\n" +
            "}";


    private static final String prettifyjson(ObjectMapper om, String json) throws JsonProcessingException {
        return om.readTree(json).toPrettyString();
    }

    private static GraphQLQuery.AbstractSelection sel(String f, boolean listValued, boolean complex) {
        return new GraphQLQuery.AbstractSelection() {
            @Override
            public String field() {
                return f;
            }

            @Override
            public boolean isListValued() {
                return listValued;
            }

            @Override
            public boolean isComplex() {
                return complex;
            }
        };
    }

    Sketch patients;
    Sketch persons;


    @Before
    public void setUp() throws Exception {
//        GraphBuilders b1 = contextCreatingBuilder()
//                .node("String")
//                .node("Int")
//                .edge("Patient", "name", "String")
//                .edge("Patient", "consultations", "Int")
//                .edge(Name.identifier("Query.patients"), Name.identifier("result").prefixWith(Name.identifier("Query.patients")), Name.identifier("Patient"))
//                .graph(Name.identifier("patients").absolute())
//                .startDiagram(StringDT.getInstance())
//                .map(Universe.ONE_NODE_THE_NODE, Name.identifier("String"))
//                .endDiagram(Name.anonymousIdentifier())
//                .startDiagram(IntDT.getInstance())
//                .map(Universe.ONE_NODE_THE_NODE, Name.identifier("Int"))
//                .endDiagram(Name.anonymousIdentifier());
//        MessageType diagram = (MessageType) b1.diagram(g -> new MessageType.Impl(g, Name.identifier("Query.patients")));
//        b1.diag(g -> new MessageArgument(diagram, Name.identifier("result").prefixWith(Name.identifier("Query.patients")), Name.identifier("Patient"), 0, true));
//        patients = b1.sketch(Name.identifier("patients")).getResult(Sketch.class);

    }

    public void testSplitTrivial() {
    }


    @Test
    public void testLocalCursor() throws IOException {
        JsonFactory factory = new JsonFactory();
        ObjectMapper objectMapper = new ObjectMapper(factory);

        JsonNode root = objectMapper.readTree(PATIENT_OBSERVATION_JSON);

        QueryCursor.LocalCursor cursorRoot = new QueryCursor.LocalCursor(sel("patients", true, true));
        new QueryCursor.LocalCursor(sel("id", false, false), cursorRoot);
        QueryCursor.LocalCursor obsCursor = new QueryCursor.LocalCursor(sel("observations", true, true), cursorRoot);
        QueryCursor.LocalCursor codingCursor = new QueryCursor.LocalCursor(sel("coding", false, true), obsCursor);
        new QueryCursor.LocalCursor(sel("code", false, false), codingCursor);
        QueryCursor.LocalCursor quantiyCursor = new QueryCursor.LocalCursor(sel("valueQuantity", false, true), obsCursor);
        new QueryCursor.LocalCursor(sel("value", false, false), quantiyCursor);

        cursorRoot.addResult(root.get("data"));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        JsonGenerator generator = factory.createGenerator(bos);
        generator.writeStartObject();
        cursorRoot.processOne(generator);
        generator.writeEndObject();
        generator.flush();
        generator.close();

        String expected = "{\n" +
                "\t\"patients\" : [\n" +
                "\t\t{\n" +
                "\t\t\t\"id\" : \"1\",\n" +
                "\t\t\t\"observations\" : [\n" +
                "\t\t\t\t{\n" +
                "\t\t\t\t\t\"coding\" : {\n" +
                "\t\t\t\t\t\t\"code\" : \"89123\"\n" +
                "\t\t\t\t\t},\n" +
                "\t\t\t\t\t\"valueQuantity\" : {\n" +
                "\t\t\t\t\t\t\"value\" : 3.4\n" +
                "\t\t\t\t\t}\n" +
                "\t\t\t\t}\n" +
                "\t\t\t]\n" +
                "\t\t},\n" +
                "\t\t{\n" +
                "\t\t\t\"id\" : \"3\",\n" +
                "\t\t\t\"observations\" : [\n" +
                "\t\t\t\t{\n" +
                "\t\t\t\t\t\"coding\" : {\n" +
                "\t\t\t\t\t\t\"code\" : \"9987-1\"\n" +
                "\t\t\t\t\t},\n" +
                "\t\t\t\t\t\"valueQuantity\" : {\n" +
                "\t\t\t\t\t\t\"value\" : 20\n" +
                "\t\t\t\t\t}\n" +
                "\t\t\t\t},\n" +
                "\t\t\t\t{\n" +
                "\t\t\t\t\t\"coding\" : {\n" +
                "\t\t\t\t\t\t\"code\" : \"2347\"\n" +
                "\t\t\t\t\t},\n" +
                "\t\t\t\t\t\"valueQuantity\" : {\n" +
                "\t\t\t\t\t\t\"value\" : 120\n" +
                "\t\t\t\t\t}\n" +
                "\t\t\t\t}\n" +
                "\t\t\t]\n" +
                "\t\t},\n" +
                "\t\t{\n" +
                "\t\t\t\"id\" : \"5\",\n" +
                "\t\t\t\"observations\" : []\n" +
                "\t\t}\n" +
                "\t]\n" +
                "}";

        String actual = bos.toString("UTF-8");
        assertEquals(prettifyjson(objectMapper, expected), prettifyjson(objectMapper, actual));
    }

    public static <S> Map<String, S> oneEntryMap(String key, S value) {
        Map<String, S> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    public static <S> Map<String, S> twoEntryMap(String leftKey, S leftEntry, String rightKey, S rightEntry) {
        Map<String, S> result = new LinkedHashMap<>();
        result.put(leftKey, leftEntry);
        result.put(rightKey, rightEntry);
        return result;
    }

    public static <S> Map<String, S> threeEntryMap(
            String leftKey,
            S leftEntry,
            String middleKey,
            S middleEntry,
            String rightKey, S rightEntry) {
        Map<String, S> result = new LinkedHashMap<>();
        result.put(leftKey, leftEntry);
        result.put(middleKey, middleEntry);
        result.put(rightKey, rightEntry);
        return result;
    }

    @Test
    public void testConcatCursor() throws IOException {
        JsonFactory factory = new JsonFactory();
        ObjectMapper objectMapper = new ObjectMapper(factory);

        JsonNode asRoot = objectMapper.readTree("{\n" +
                "\t\"as\" : [\n" +
                "\t\t{\n" +
                "\t\t\t\"x\": \"1\"\n" +
                "\t\t},\n" +
                "\t\t{\n" +
                "\t\t\t\"x\": \"2\",\n" +
                "\t\t\t\"y1\": \"a\"\n" +
                "\t\t},\n" +
                "\t\t{\n" +
                "\t\t\t\"y1\": \"b\"\n" +
                "\t\t}\n" +
                "\t]\n" +
                "}");
        JsonNode bsRoot = objectMapper.readTree("\n" +
                "{\n" +
                "\t\"bs\" : [\n" +
                "\t\t{\n" +
                "\t\t\t\"y2\": \"c\",\n" +
                "\t\t\t\"z\": \"i\"\n" +
                "\t\t},\n" +
                "\t\t{\n" +
                "\t\t\t\"z\": \"ii\"\n" +
                "\t\t}\n" +
                "\t]\n" +
                "}");

        String expected = objectMapper.readTree("{\n" +
                "  \"abs\" : [ {\n" +
                "    \"x\" : \"1\",\n" +
                "    \"y\" : null,\n" +
                "    \"z\" : null\n" +
                "  }, {\n" +
                "    \"x\" : \"2\",\n" +
                "    \"y\" : \"a\",\n" +
                "    \"z\" : null\n" +
                "  }, {\n" +
                "    \"x\" : null,\n" +
                "    \"y\" : \"b\",\n" +
                "    \"z\" : null\n" +
                "  }, {\n" +
                "    \"x\" : null,\n" +
                "    \"y\" : \"c\",\n" +
                "    \"z\" : \"i\"\n" +
                "  }, {\n" +
                "    \"x\" : null,\n" +
                "    \"y\" : null,\n" +
                "    \"z\" : \"ii\"\n" +
                "  } ]\n" +
                "}").toPrettyString();

        QueryCursor.LocalCursor aCursorRoot = new QueryCursor.LocalCursor(sel("as", true, true));
        QueryCursor.LocalCursor axCursor = new QueryCursor.LocalCursor(sel("x", false, false), aCursorRoot);
        QueryCursor.LocalCursor ayCursor = new QueryCursor.LocalCursor(sel("y1", false, false), aCursorRoot);

        QueryCursor.LocalCursor bCursorRoot = new QueryCursor.LocalCursor(sel("bs", true, true));
        QueryCursor.LocalCursor byCursor = new QueryCursor.LocalCursor(sel("y2", false, false), bCursorRoot);
        QueryCursor.LocalCursor bzCursor = new QueryCursor.LocalCursor(sel("z", false, false), bCursorRoot);


        String leftKey = "http://a";
        String rightKey = "http://b";
        QueryCursor.ConcatCursor abCursorRoot = new QueryCursor.ConcatCursor(sel("abs", true, true), twoEntryMap(leftKey,aCursorRoot, rightKey, bCursorRoot));
        new QueryCursor.ConcatCursor(sel("x", false, false), abCursorRoot, oneEntryMap(leftKey,axCursor));
        new QueryCursor.ConcatCursor(sel("y", false, false), abCursorRoot, twoEntryMap(leftKey,ayCursor,rightKey,byCursor));
        new QueryCursor.ConcatCursor(sel("z", false, false), abCursorRoot, oneEntryMap(rightKey,bzCursor));

        abCursorRoot.addResults(twoEntryMap(leftKey, asRoot, rightKey, bsRoot));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        JsonGenerator generator = factory.createGenerator(bos);
        generator.writeStartObject();
        abCursorRoot.processOne(generator);
        generator.writeEndObject();
        generator.flush();
        generator.close();

        assertEquals(expected, prettifyjson(objectMapper, bos.toString("UTF-8")));
    }

    @Test
    public void testCustomerClientEmployee() throws IOException {
        JsonFactory factory = new JsonFactory();
        ObjectMapper objectMapper = new ObjectMapper(factory);

        JsonNode customerResponse = objectMapper.readTree(CUSTOMER_RESPONSE_DATA);
        JsonNode clientResponse = objectMapper.readTree(CLIENT_RESPONSE_DATA);
        JsonNode employeeResponse = objectMapper.readTree(EMPLOYEE_RESPONSE_DATA);


        QueryCursor.LocalCursor customersRoot = new QueryCursor.LocalCursor(sel("customers", true, true));
        QueryCursor.LocalCursor customerID = new QueryCursor.LocalCursor(sel("id", false, false), customersRoot);
        QueryCursor.LocalCursor customerName = new QueryCursor.LocalCursor(sel("name", false, false), customersRoot);
        QueryCursor.LocalCursor purchases = new QueryCursor.LocalCursor(sel("purchases", true, true), customersRoot);
        QueryCursor.LocalCursor purchasesId = new QueryCursor.LocalCursor(sel("id", false, false), purchases);


        QueryCursor.LocalCursor invoicesRoot = new QueryCursor.LocalCursor(sel("clients", true, true));
        QueryCursor.LocalCursor clientId = new QueryCursor.LocalCursor(sel("id", false, false), invoicesRoot);
        QueryCursor.LocalCursor clientName = new QueryCursor.LocalCursor(sel("name", false, false), invoicesRoot);
        QueryCursor.LocalCursor invoices = new QueryCursor.LocalCursor(sel("invoices", true, true), invoicesRoot);
        QueryCursor.LocalCursor invoiceId = new QueryCursor.LocalCursor(sel("id", false, false), invoices);


        QueryCursor.LocalCursor employeesRoot = new QueryCursor.LocalCursor(sel("employees", true, true));
        QueryCursor.LocalCursor employeeId = new QueryCursor.LocalCursor(sel("id", false, false), employeesRoot);
        QueryCursor.LocalCursor employeeFn = new QueryCursor.LocalCursor(sel("firstname", false, false), employeesRoot);
        QueryCursor.LocalCursor employeeLn = new QueryCursor.LocalCursor(sel("lastname", false, false), employeesRoot);
        QueryCursor.LocalCursor employeeWAt = new QueryCursor.LocalCursor(sel("worksAt", true, true), employeesRoot);
        QueryCursor.LocalCursor departName = new QueryCursor.LocalCursor(sel("name", false, false), employeeWAt);



        String leftKey = "http://purchases";
        String middleKey = "http://invoices";
        String rightKey = "http://employee";

        Multimap<String, Key> keyMap = ArrayListMultimap.create();
        keyMap.put(leftKey, new AttributeBasedKey(Universe.EMPTY, Triple.edge(Name.identifier("Customer"), Name.identifier("name"), Name.identifier("String")), Name.identifier("Partner")));
        keyMap.put(leftKey, new AttributeBasedKey(Universe.EMPTY, Triple.edge(Name.identifier("Customer"), Name.identifier("id"), Name.identifier("ID")), Name.identifier("Partner")));
        keyMap.put(middleKey, new AttributeBasedKey(Universe.EMPTY, Triple.edge(Name.identifier("Client"), Name.identifier("id"), Name.identifier("ID")), Name.identifier("Partner")));
        keyMap.put(rightKey, new ConcatenatedKey(Universe.EMPTY, Name.identifier("Partner"),
                        Arrays.asList(
                                new AttributeBasedKey(Universe.EMPTY, Triple.edge(Name.identifier("Employee"), Name.identifier("firstname"), Name.identifier("String")), Name.identifier("Partner")),
                                new ConstantKey(Universe.EMPTY, Name.value(" "), Name.identifier("Partner")),
                                new AttributeBasedKey(Universe.EMPTY, Triple.edge(Name.identifier("Customer"), Name.identifier("lastname"), Name.identifier("String")), Name.identifier("Partner")))));

        QueryCursor.ConcatMergeCursor partnersRoot = new QueryCursor.ConcatMergeCursor(sel("partners", true, true),
                threeEntryMap(leftKey, customersRoot, middleKey
                        , invoicesRoot, rightKey,
                        employeesRoot), keyMap);

        new QueryCursor.ConcatCursor(sel("id", true, false),partnersRoot, threeEntryMap(leftKey, customerID, middleKey, clientId, rightKey, employeeId));
        new QueryCursor.ConcatCursor(sel("name", false, false),partnersRoot, twoEntryMap(leftKey, customerName, middleKey, clientName));
        QueryCursor.ConcatCursor partnerPurchase = new QueryCursor.ConcatCursor(sel("purchases", true, true),partnersRoot, oneEntryMap(leftKey, purchases));
        new QueryCursor.ConcatCursor(sel("id", false, false),partnerPurchase, oneEntryMap(leftKey, purchasesId));
        QueryCursor.ConcatCursor partnerInvoice = new QueryCursor.ConcatCursor(sel("invoices", true, true),partnersRoot, oneEntryMap(middleKey, invoices));
        new QueryCursor.ConcatCursor(sel("id", false, false),partnerInvoice, oneEntryMap(middleKey, invoiceId));
        QueryCursor.ConcatCursor partnerWorksAt = new QueryCursor.ConcatCursor(sel("worksAt", true, true),partnersRoot,oneEntryMap(rightKey,employeeWAt));
        new QueryCursor.ConcatCursor(sel("name", false, false),partnerWorksAt, oneEntryMap(rightKey, departName));

        partnersRoot.addResults(threeEntryMap(leftKey, customerResponse.get("data"), middleKey, clientResponse.get("data"), rightKey, employeeResponse.get("data")));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        JsonGenerator generator = factory.createGenerator(bos);
        generator.writeStartObject();
        partnersRoot.processOne(generator);
        generator.writeEndObject();
        generator.flush();
        generator.close();

        String exptected = "{\n" +
                "  \"partners\" : [ {\n" +
                "    \"id\" : [ \"1\", \"1\" ],\n" +
                "    \"name\" : \"Ban Geyton\",\n" +
                "    \"purchases\" : [ {\n" +
                "      \"id\" : \"1\"\n" +
                "    }, {\n" +
                "      \"id\" : \"2\"\n" +
                "    } ],\n" +
                "    \"invoices\" : [ {\n" +
                "      \"id\" : \"1\"\n" +
                "    }, {\n" +
                "      \"id\" : \"2\"\n" +
                "    } ],\n" +
                "    \"worksAt\" : [ ]\n" +
                "  }, {\n" +
                "    \"id\" : [ \"2\", \"2\", \"5\" ],\n" +
                "    \"name\" : \"Ferrell Leethem\",\n" +
                "    \"purchases\" : [ {\n" +
                "      \"id\" : \"3\"\n" +
                "    }, {\n" +
                "      \"id\" : \"4\"\n" +
                "    }, {\n" +
                "      \"id\" : \"5\"\n" +
                "    } ],\n" +
                "    \"invoices\" : [ {\n" +
                "      \"id\" : \"3\"\n" +
                "    } ],\n" +
                "    \"worksAt\" : [ {\n" +
                "      \"name\" : \"HR\"\n" +
                "    } ]\n" +
                "  }, {\n" +
                "    \"id\" : [ \"3\" ],\n" +
                "    \"name\" : \"Selinda Streader\",\n" +
                "    \"purchases\" : [ {\n" +
                "      \"id\" : \"6\"\n" +
                "    } ],\n" +
                "    \"invoices\" : [ ],\n" +
                "    \"worksAt\" : [ ]\n" +
                "  }, {\n" +
                "    \"id\" : [ \"1\" ],\n" +
                "    \"name\" : null,\n" +
                "    \"purchases\" : [ ],\n" +
                "    \"invoices\" : [ ],\n" +
                "    \"worksAt\" : [ {\n" +
                "      \"name\" : \"Sales\"\n" +
                "    } ]\n" +
                "  }, {\n" +
                "    \"id\" : [ \"2\" ],\n" +
                "    \"name\" : null,\n" +
                "    \"purchases\" : [ ],\n" +
                "    \"invoices\" : [ ],\n" +
                "    \"worksAt\" : [ {\n" +
                "      \"name\" : \"Sales\"\n" +
                "    } ]\n" +
                "  }, {\n" +
                "    \"id\" : [ \"3\" ],\n" +
                "    \"name\" : null,\n" +
                "    \"purchases\" : [ ],\n" +
                "    \"invoices\" : [ ],\n" +
                "    \"worksAt\" : [ {\n" +
                "      \"name\" : \"IT\"\n" +
                "    } ]\n" +
                "  }, {\n" +
                "    \"id\" : [ \"4\" ],\n" +
                "    \"name\" : null,\n" +
                "    \"purchases\" : [ ],\n" +
                "    \"invoices\" : [ ],\n" +
                "    \"worksAt\" : [ {\n" +
                "      \"name\" : \"IT\"\n" +
                "    } ]\n" +
                "  } ]\n" +
                "}";

        assertEquals(prettifyjson(objectMapper,exptected), prettifyjson(objectMapper, bos.toString("UTF-8")));
    }

    @Test
    public void testFromSplittingTest() throws IOException {
        JsonFactory factory = new JsonFactory();
        ObjectMapper objectMapper = new ObjectMapper(factory);
        JsonNode first = objectMapper.readTree("{\n" +
                "  \"r\" : [ {\n" +
                "    \"a\" : [ {\n" +
                "      \"x\" : \"A\"\n" +
                "    }, {\n" +
                "      \"x\" : \"A'\"\n" +
                "    } ]\n" +
                "  }, {\n" +
                "    \"a\" : [ {\n" +
                "      \"x\" : \"B\"\n" +
                "    } ]\n" +
                "  } ]\n" +
                "}");

        JsonNode second = objectMapper.readTree("{\n" +
                "  \"r\" : [ {\n" +
                "    \"a\" : [ {\n" +
                "      \"y\" : 42\n" +
                "    } ],\n" +
                "    \"b\" : [ {\n" +
                "      \"z2\" : \"C\"\n" +
                "    } ]\n" +
                "  } ]\n" +
                "}\n");

        JsonNode third = objectMapper.readTree("{\n" +
                "  \"r\" : [ {\n" +
                "    \"b\" : [ {\n" +
                "      \"z3\" : \"C\"\n" +
                "    } ]\n" +
                "  }, {\n" +
                "    \"b\" : [ {\n" +
                "      \"z3\" : \"D\"\n" +
                "    } ]\n" +
                "  } ]\n" +
                "}");

        QueryCursor.LocalCursor firstRoot = new QueryCursor.LocalCursor(sel("r",true, true));
        QueryCursor.LocalCursor firstAs = new QueryCursor.LocalCursor(sel("a",true, true),firstRoot);
        QueryCursor.LocalCursor firstAxs = new QueryCursor.LocalCursor(sel("x",false, false),firstAs);

        QueryCursor.LocalCursor secondRoot = new QueryCursor.LocalCursor(sel("r",true, true));
        QueryCursor.LocalCursor secondAs = new QueryCursor.LocalCursor(sel("a",true, true),secondRoot);
        QueryCursor.LocalCursor secondBs = new QueryCursor.LocalCursor(sel("b",true, true),secondRoot);
        QueryCursor.LocalCursor secondAys = new QueryCursor.LocalCursor(sel("y",false, false),secondAs);
        QueryCursor.LocalCursor secondBzs = new QueryCursor.LocalCursor(sel("z2",false, false),secondBs);

        QueryCursor.ConcatCursor concatCursorRoot = new QueryCursor.ConcatCursor(sel("r", true, true), twoEntryMap("f", firstRoot, "s", secondRoot));
        QueryCursor.ConcatCursor concatAs = new QueryCursor.ConcatCursor(sel("a", true, true),concatCursorRoot, twoEntryMap("f", firstAs, "s", secondAs));
        QueryCursor.ConcatCursor concatAxs = new QueryCursor.ConcatCursor(sel("x", false, false),concatAs, oneEntryMap("f", firstAxs));
        QueryCursor.ConcatCursor concatAys = new QueryCursor.ConcatCursor(sel("y", false, false),concatAs, oneEntryMap("s", secondAys));
        QueryCursor.ConcatCursor concatBs = new QueryCursor.ConcatCursor(sel("b", true, true),concatCursorRoot, oneEntryMap("s", secondBs));
        QueryCursor.ConcatCursor concatBzs = new QueryCursor.ConcatCursor(sel("z", false, false),concatBs, oneEntryMap("s", secondBzs));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JsonGenerator generator = factory.createGenerator(bos);
        generator.writeStartObject();

        concatCursorRoot.addResults(twoEntryMap("f",first,"s", second));
        concatCursorRoot.processOne(generator);

        generator.writeEndObject();
        generator.flush();
        generator.close();

        String expected = "{\n" +
                "  \"r\" : [ {\n" +
                "    \"a\" : [ {\n" +
                "      \"x\" : \"A\",\n" +
                "      \"y\" : null\n" +
                "    },{\n" +
                "      \"x\" : \"A'\",\n" +
                "      \"y\" : null\n" +
                "    } ],\n" +
                "    \"b\" : [ ]\n" +
                "  }, {\n" +
                "    \"a\" : [ {\n" +
                "      \"x\" : \"B\",\n" +
                "      \"y\" : null\n" +
                "    } ],\n" +
                "    \"b\" : [ ]\n" +
                "  }, {\n" +
                "    \"a\" : [ {\n" +
                "      \"x\" : null,\n" +
                "      \"y\" : 42\n" +
                "    } ],\n" +
                "    \"b\" : [ {\n" +
                "      \"z\" : \"C\"\n" +
                "    } ]\n" +
                "  } ]\n" +
                "}";

        assertEquals(prettifyjson(objectMapper, expected), prettifyjson(objectMapper, bos.toString("UTF-8")));

    }




}
