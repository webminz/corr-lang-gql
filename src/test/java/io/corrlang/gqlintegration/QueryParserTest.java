package io.corrlang.gqlintegration;

import io.corrlang.domain.MessageType;
import io.corrlang.domain.ProcessingException;
import io.corrlang.domain.Sys;
import io.corrlang.gqlintegration.queries.GraphQLQuery;
import io.corrlang.gqlintegration.queries.GraphQueryErrorMessage;
import io.corrlang.gqlintegration.queries.ParsingErrorMessages;
import io.corrlang.gqlintegration.schema.EntryPointType;
import io.corrlang.gqlintegration.schema.FieldMult;
import no.hvl.past.graph.*;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.predicates.FloatDT;
import no.hvl.past.graph.predicates.StringDT;
import no.hvl.past.graph.trees.*;
import no.hvl.past.names.Name;

import no.hvl.past.util.IOStreamUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static junit.framework.TestCase.*;


public class QueryParserTest extends GraphQLTest {

    private GraphQLEndpoint endpoint;

    @Before
    public void setUp() throws Exception {
        Sketch sketch = new GraphBuilders(getUniverseForTest(), true, false)
                .node(Name.identifier("createPatient").prefixWith(Name.identifier("Mutation")))
                .node(Name.identifier("createPatients").prefixWith(Name.identifier("Mutation")))
                .node(Name.identifier("patients").prefixWith(Name.identifier("Query")))
                .node(Name.identifier("observations").prefixWith(Name.identifier("Query")))
                .node("String")
                .node("Patient")
                .node("PatientInput")
                .node("Observation")
                .node("Quantity")
                .node("Float")
                .node("Address")
                .edgePrefixWithOwner("Patient", "firstName", "String")
                .edgePrefixWithOwner("Patient", "lastName", "String")
                .edgePrefixWithOwner("Patient", "email", "String")
                .edgePrefixWithOwner("Patient", "id", "String")
                .edgePrefixWithOwner("Patient", "observations", "Observation")
                .edgePrefixWithOwner("Quantity", "value", "Float")
                .edgePrefixWithOwner("Quantity", "unit", "String")
                .edgePrefixWithOwner("Observation", "valueQuantity", "Quantity")
                .edgePrefixWithOwner("Observation", "code", "String")
                .edgePrefixWithOwner("Observation", "subject", "Patient")
                .edgePrefixWithOwner("PatientInput", "firstName", "String")
                .edgePrefixWithOwner("PatientInput", "lastName", "String")
                .edgePrefixWithOwner("PatientInput", "email", "String")
                .edgePrefixWithOwner("PatientInput", "address", "Address")
                .edgePrefixWithOwner("Address", "lines", "String")
                .edgePrefixWithOwner("Address", "zip", "String")
                .edgePrefixWithOwner("Address", "country", "String")
            .edgePrefixWithOwner(Name.identifier("createPatient").prefixWith(Name.identifier("Mutation")), "person", Name.identifier("PatientInput"))
                .edgePrefixWithOwner(Name.identifier("createPatient").prefixWith(Name.identifier("Mutation")), "result", Name.identifier("Patient"))
                .edgePrefixWithOwner(Name.identifier("createPatients").prefixWith(Name.identifier("Mutation")), "personList", Name.identifier("PatientInput"))
                .edgePrefixWithOwner(Name.identifier("createPatients").prefixWith(Name.identifier("Mutation")), "result", Name.identifier("Patient"))
                .edgePrefixWithOwner(Name.identifier("patients").prefixWith(Name.identifier("Query")), "id", Name.identifier("String"))
                .edgePrefixWithOwner(Name.identifier("patients").prefixWith(Name.identifier("Query")), "result", Name.identifier("Patient"))
                .edgePrefixWithOwner(Name.identifier("observations").prefixWith(Name.identifier("Query")), "result", Name.identifier("Observation"))
                .edgePrefixWithOwner(Name.identifier("observations").prefixWith(Name.identifier("Query")), "patientId", Name.identifier("String"))

                .graph("DemoGraph")
                .startDiagram(StringDT.getInstance())
                .map(Universe.ONE_NODE_THE_NODE, Name.identifier("String"))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(FloatDT.getInstance())
                .map(Universe.ONE_NODE_THE_NODE, Name.identifier("Float"))
                .endDiagram(Name.anonymousIdentifier())
                .sketch("Demo")
                .getResult(Sketch.class);

        Sys sys = new Sys.Builder("http://localhost", sketch)
                .beginMessageContainer(Name.identifier("Mutation"))
                    .beginMessage(Name.identifier("createPatient"), true)
                        .input(Name.identifier("person"))
                        .output(Name.identifier("result"))
                    .endMessage()
                    .beginMessage(Name.identifier("createPatients"), true)
                        .input(Name.identifier("personList"))
                        .output(Name.identifier("result"))
                    .endMessage()
                .endMessageContainer()
                .beginMessageContainer(Name.identifier("Query"))
                    .beginMessage(Name.identifier("patients"), false)
                        .input(Name.identifier("id"))
                        .output(Name.identifier("result"))
                    .endMessage()
                    .beginMessage(Name.identifier("observations"), false)
                        .input(Name.identifier("patientId"))
                        .output(Name.identifier("result"))
                    .endMessage()
                .endMessageContainer()
                .build();

        EntryPointType query = new EntryPointType("Query");
        EntryPointType mutation = new EntryPointType("Mutation");
        mutation.addMessage(sys.getMessageType(Name.identifier("createPatient").prefixWith(Name.identifier("Mutation"))));
        mutation.addMessage(sys.getMessageType(Name.identifier("createPatients").prefixWith(Name.identifier("Mutation"))));
        query.addMessage(sys.getMessageType(Name.identifier("patients").prefixWith(Name.identifier("Query"))));
        query.addMessage(sys.getMessageType(Name.identifier("observations").prefixWith(Name.identifier("Query"))));

        // TODO field mults should be better handled with classical diagrams
        List<FieldMult> fieldMultList = new ArrayList<>();
        fieldMultList.add(new FieldMult(Name.identifier("firstName").prefixWith(Name.identifier("PatientInput")), false, true));
        fieldMultList.add(new FieldMult(Name.identifier("lastName").prefixWith(Name.identifier("PatientInput")), false, true));
        fieldMultList.add(new FieldMult(Name.identifier("email").prefixWith(Name.identifier("PatientInput")), false, true));
        fieldMultList.add(new FieldMult(Name.identifier("firstName").prefixWith(Name.identifier("Patient")), false, true));
        fieldMultList.add(new FieldMult(Name.identifier("lastName").prefixWith(Name.identifier("Patient")), false, true));
        fieldMultList.add(new FieldMult(Name.identifier("email").prefixWith(Name.identifier("Patient")), false, true));
        fieldMultList.add(new FieldMult(Name.identifier("id").prefixWith(Name.identifier("Patient")), false, true));
        fieldMultList.add(new FieldMult(Name.identifier("observations").prefixWith(Name.identifier("Patient")), true, false));
        fieldMultList.add(new FieldMult(Name.identifier("value").prefixWith(Name.identifier("Quantity")), false, true));
        fieldMultList.add(new FieldMult(Name.identifier("unit").prefixWith(Name.identifier("Quantity")), false, true));
        fieldMultList.add(new FieldMult(Name.identifier("valueQuantity").prefixWith(Name.identifier("Observation")), false, true));
        fieldMultList.add(new FieldMult(Name.identifier("code").prefixWith(Name.identifier("Observation")), false, true));
        fieldMultList.add(new FieldMult(Name.identifier("subject").prefixWith(Name.identifier("Observation")), false, true));
        fieldMultList.add(new FieldMult(Name.identifier("result").prefixWith(Name.identifier("createPatient").prefixWith(Name.identifier("Mutation"))), false, true));
        fieldMultList.add(new FieldMult(Name.identifier("person").prefixWith(Name.identifier("createPatient").prefixWith(Name.identifier("Mutation"))), false, true));
        fieldMultList.add(new FieldMult(Name.identifier("personList").prefixWith(Name.identifier("createPatients").prefixWith(Name.identifier("Mutation"))), true, true));
        fieldMultList.add(new FieldMult(Name.identifier("result").prefixWith(Name.identifier("createPatients").prefixWith(Name.identifier("Mutation"))), true, false));
        fieldMultList.add(new FieldMult(Name.identifier("result").prefixWith(Name.identifier("observations").prefixWith(Name.identifier("Query"))), true, false));
        fieldMultList.add(new FieldMult(Name.identifier("patientId").prefixWith(Name.identifier("observations").prefixWith(Name.identifier("Query"))), false, false));
        fieldMultList.add(new FieldMult(Name.identifier("result").prefixWith(Name.identifier("patients").prefixWith(Name.identifier("Query"))), true, false));
        fieldMultList.add(new FieldMult(Name.identifier("id").prefixWith(Name.identifier("patients").prefixWith(Name.identifier("Query"))), false, false));
        fieldMultList.add(new FieldMult(Name.identifier("lines").prefixWith(Name.identifier("Address")), true, false));
        fieldMultList.add(new FieldMult(Name.identifier("zip").prefixWith(Name.identifier("Address")), false, false));
        fieldMultList.add(new FieldMult(Name.identifier("country").prefixWith(Name.identifier("Address")), false, false));
        fieldMultList.add(new FieldMult(Name.identifier("address").prefixWith(Name.identifier("PatientInput")), false, false));


        Map<Name, String> displayNames = new HashMap<>();
        displayNames.put(Name.identifier("createPatient").prefixWith(Name.identifier("Mutation")), "createPatient");
        displayNames.put(Name.identifier("createPatients").prefixWith(Name.identifier("Mutation")), "createPatients");
        displayNames.put(Name.identifier("patients").prefixWith(Name.identifier("Query")), "patients");
        displayNames.put(Name.identifier("result").prefixWith(Name.identifier("patients").prefixWith(Name.identifier("Query"))), "patients");
        displayNames.put(Name.identifier("observations").prefixWith(Name.identifier("Query")), "observations");

        // FIXME this looks stupid!!!
        Map<Name, MessageType> messageTypes = new HashMap<>();
        messageTypes.put(Name.identifier("createPatient").prefixWith(Name.identifier("Mutation")), sys.getMessageType(Name.identifier("createPatient").prefixWith(Name.identifier("Mutation"))));
        messageTypes.put(Name.identifier("createPatients").prefixWith(Name.identifier("Mutation")), sys.getMessageType(Name.identifier("createPatients").prefixWith(Name.identifier("Mutation"))));
        messageTypes.put(Name.identifier("patients").prefixWith(Name.identifier("Query")), sys.getMessageType(Name.identifier("patients").prefixWith(Name.identifier("Query"))));
        messageTypes.put(Name.identifier("observations").prefixWith(Name.identifier("Query")), sys.getMessageType(Name.identifier("observations").prefixWith(Name.identifier("Query"))));

        endpoint = new GraphQLEndpoint(
                sys.url(),
                sketch,
                displayNames,
                messageTypes,
                fieldMultList,
                query,
                mutation,
                objectMapper,
                jsonFactory);
    }

    @Test
    public void testParsing() throws Exception {
        String request = "{\n" +
                "\t\"query\" : \"mutation makePatient($p: PatientInput!) { createPatient(person: $p) { firstName } }\",\n" +
                "\t\"variables\" : {\n" +
                "\t\t\"p\" : {\n" +
                "        \"firstName\" : \"Hans\",\n" +
                "        \"lastName\" : \"Herrmann\",\n" +
                "        \"email\" : \"hans@example.com\"\n" +
                "  \t  }\n" +
                "\t}\n" +
                "}";




        InputStream inputStream = IOStreamUtils.stringAsInputStream(request);
        GraphMorphism graphMorphism = endpoint.parseQueryOrInstance(inputStream);

        assertTrue(graphMorphism instanceof QueryTree);
        List<QueryNode.Root> roots = ((QueryTree) graphMorphism).queryRoots().collect(Collectors.toList());
        assertEquals(1, roots.size());
        QueryNode.Root root = roots.get(0);
        assertEquals(Triple.edge(Name.identifier("createPatient").prefixWith(Name.identifier("Mutation")),
                Name.identifier("result").prefixWith(Name.identifier("createPatient").prefixWith(Name.identifier("Mutation"))),
                Name.identifier("Patient")), root.queryResultEdge());
        List<Branch> arguments = root.children().filter(x -> x instanceof QueryBranch.Selection).collect(Collectors.toList());
        List<Branch> selectionSets = root.children().filter(x -> x instanceof QueryBranch.Projection).collect(Collectors.toList());
        assertEquals(1, arguments.size());
        assertEquals(1, selectionSets.size());
        assertEquals("firstName", selectionSets.get(0).label());
        assertTrue(selectionSets.get(0).child().parent().isPresent());
        assertEquals(Triple.edge(
                Name.identifier("Patient"),
                Name.identifier("firstName").prefixWith(Name.identifier("Patient")),
                Name.identifier("String")
                ),
                ((TypedBranch)selectionSets.get(0)).typeFeature());
        assertEquals(Triple.edge(Name.identifier("Patient"), Name.identifier("firstName").prefixWith(Name.identifier("Patient")), Name.identifier("String")),
        ((TypedBranch)selectionSets.get(0)).typeFeature());

    }

    @Test
    public void testParsingRaw() throws Exception {
        String request = "{\n" +
                "\t\"query\" : \"query { patients(id: \\\"12345\\\") { email observations { code valueQuantity { value unit } } } }\"\n" +
                "}";

        InputStream inputStream = IOStreamUtils.stringAsInputStream(request);
        GraphMorphism graphMorphism = endpoint.parseQueryOrInstance(inputStream);
        assertTrue(graphMorphism instanceof QueryTree);
        List<QueryNode.Root> roots = ((QueryTree) graphMorphism).queryRoots().collect(Collectors.toList());
        assertEquals(1, roots.size());
        QueryNode.Root root = roots.get(0);
        assertEquals(Triple.edge(Name.identifier("patients").prefixWith(Name.identifier("Query")),
                Name.identifier("result").prefixWith(Name.identifier("patients").prefixWith(Name.identifier("Query"))),
                Name.identifier("Patient")), root.queryResultEdge());
        assertEquals(4, root.depth());
        assertEquals(3, root.children().count());
        assertEquals(Name.value("12345"), root.childNodesByKey("id").findFirst().get().elementName());
        QueryNode n = (QueryNode) root.childNodesByKey("observations").findFirst().get();
        GraphQLQuery.SelectionSet sel = (GraphQLQuery.SelectionSet) n.childNodesByKey("valueQuantity").findFirst().get().parentRelation().get();
        assertEquals(Triple.edge(
                Name.identifier("Observation"),
                Name.identifier("valueQuantity").prefixWith(Name.identifier("Observation")),
                Name.identifier("Quantity")
                )
                ,sel.typeFeature());
        assertEquals(Name.identifier("Observation"), n.nodeType());
        assertEquals(2, n.children().count());

    }

    @Test
    public void testParsingListArguments() throws Exception {
        String request = "{\n" +
                "\t\"query\" : \"mutation insertThree ($p1: PatientInput!, $p2: PatientInput) { createPatients(personList: [ { firstName : \\\"Ola\\\", lastName : \\\"Nordmann\\\", email : \\\"onordmann@online.no\\\" }, $p1, $p2 ]) { observations { code } } }\",\n" +
                "\t\"operationName\" : \"insertThree\",\n" +
                "\t\"variables\" : {\n" +
                "\t\t\"p1\" : {\n" +
                "        \"firstName\" : \"Hans\",\n" +
                "        \"lastName\" : \"Herrmann\",\n" +
                "        \"email\" : \"hans@example.com\"\n" +
                "\t\t}\n" +
                "\t}\n" +
                "}\n";

        System.out.println(request);

        InputStream inputStream = IOStreamUtils.stringAsInputStream(request);
        GraphMorphism graphMorphism = endpoint.parseQueryOrInstance(inputStream);
        assertTrue(graphMorphism instanceof QueryTree);
        List<QueryNode.Root> roots = ((QueryTree) graphMorphism).queryRoots().collect(Collectors.toList());
        assertEquals(1, roots.size());
        QueryNode.Root root = roots.get(0);
        assertEquals(3, root.children().count());
        assertEquals(3, root.depth());
        assertEquals(2, root.childNodesByKey("personList").count());
        assertEquals(Name.identifier("PatientInput"), root.childNodesByKey("personList").findFirst().map(t -> (TypedNode) t).get().nodeType());


    }

    @Test
    public void testParsingNestedObjectsAndOverriding() throws Exception {
        String request = "{\n" +
                "\t\"query\" : \"mutation ($n1 : String = \\\"Max\\\", $n2 : String, $l1 : [String] = [\\\"Musterstraße 1\\\", \\\"12345 Berlin\\\", \\\"GERMANY\\\"]) { createPatient(person: { firstName : $n1, lastName : $n2, address : { lines : $l1 } }) { firstName } }\",\n" +
                "\t\"variables\" : {\n" +
                "\t\t\"n1\" : \"Peter\",\n" +
                "\t\t\"n2\" : \"Petermann\"\n" +
                "\t}\n" +
                "}";

        InputStream inputStream = IOStreamUtils.stringAsInputStream(request);
        GraphMorphism graphMorphism = endpoint.parseQueryOrInstance(inputStream);
        assertTrue(graphMorphism instanceof QueryTree);
        List<QueryNode.Root> roots = ((QueryTree) graphMorphism).queryRoots().collect(Collectors.toList());
        assertEquals(1, roots.size());
        QueryNode.Root root = roots.get(0);
        assertEquals(Name.identifier("createPatient").prefixWith(Name.identifier("Mutation")), root.messageType());
        assertEquals(2, root.children().count());
        assertEquals(1, root.childNodesByKey("person").count());
        assertEquals(1, root.childNodesByKey("firstName").count());

        Node person = root.childNodesByKey("person").findFirst().get();
        assertTrue(person instanceof TypedNode);
        TypedNode personArg = (TypedNode) person;
        assertEquals(3, personArg.children().count());
        assertEquals(Name.identifier("PatientInput"), personArg.nodeType());
        assertEquals(1, personArg.childNodesByKey("firstName").count());

        Branch firstNName = personArg.childrenByKey("firstName").findFirst().get();
        assertTrue(firstNName instanceof TypedBranch);
        TypedBranch fName = (TypedBranch) firstNName;
        assertEquals(Name.identifier("firstName").prefixWith(Name.identifier("PatientInput")), fName.typeFeature().getLabel());
        assertEquals(Name.value("Peter"), fName.child().elementName());


        Branch lastNName = personArg.childrenByKey("lastName").findFirst().get();
        assertTrue(lastNName instanceof TypedBranch);
        TypedBranch lName = (TypedBranch) lastNName;
        assertEquals(Name.identifier("lastName").prefixWith(Name.identifier("PatientInput")), lName.typeFeature().getLabel());
        assertEquals(Name.value("Petermann"), lName.child().elementName());


        Branch addr = personArg.childrenByKey("address").findFirst().get();
        assertTrue(addr instanceof TypedBranch);
        TypedBranch address = (TypedBranch) addr;
        assertEquals(Name.identifier("Address"), address.typeFeature().getTarget());
        assertEquals(3, address.child().children().count());
        assertTrue(addr.child().children().allMatch(b -> b.label().equals("lines")));
        List<Node> collect = addr.child().childNodes().collect(Collectors.toList());
        assertEquals(Name.value("Musterstraße 1"), collect.get(0).elementName());
        assertEquals(Name.value("12345 Berlin"), collect.get(1).elementName());
        assertEquals(Name.value("GERMANY"), collect.get(2).elementName());
    }


    @Test
    public void testShortHandQueryNotation() throws Exception {
        String request = "\n" +
                "{\n" +
                "\t\"query\" : \"observations { valueQuantity { value } }\"\n" +
                "}";
        InputStream inputStream = IOStreamUtils.stringAsInputStream(request);
        GraphMorphism graphMorphism = endpoint.parseQueryOrInstance(inputStream);
        assertTrue(graphMorphism instanceof QueryTree);
        List<QueryNode.Root> roots = ((QueryTree) graphMorphism).queryRoots().collect(Collectors.toList());
        assertEquals(1, roots.size());

        QueryNode.Root root = roots.get(0);
        assertEquals(Name.identifier("observations").prefixWith(Name.identifier("Query")), root.messageType());
        assertEquals(3, root.depth());
    }



    @Test
    public void testMultiRootQuery() throws Exception {
        String request = "{\n" +
                "\t\"query\" : \"query { observations { valueQuantity { value } } patients { firstName } }\"\n" +
                "} ";
        InputStream inputStream = IOStreamUtils.stringAsInputStream(request);
        GraphMorphism graphMorphism = endpoint.parseQueryOrInstance(inputStream);
        assertTrue(graphMorphism instanceof QueryTree);
        List<QueryNode.Root> roots = ((QueryTree) graphMorphism).queryRoots().collect(Collectors.toList());
        assertEquals(2, roots.size());
        QueryNode.Root root1 = roots.get(0);
        assertEquals(Name.identifier("observations").prefixWith(Name.identifier("Query")), root1.messageType());
        assertEquals(3, root1.depth());

        QueryNode.Root root2 = roots.get(1);
        assertEquals(Name.identifier("patients").prefixWith(Name.identifier("Query")), root2.messageType());
        assertEquals(2, root2.depth());
    }


    @Test
    public void testResilientParsing() throws Exception {
        String request = "{\n" +
                "\t\"query\" : \"query myFalseQ2 { patients(id: \\\"12345\\\") { email observations { code recordedAt } } }\\n mutation makePatient($p: PatientInput!) { createPatient(person: $p) { firstName } }\\n mutation insertThree ($p1: PatientInput!, $p2: PatientInput) { createPatients(personList: [ { firstName : \\\"Ola\\\", lastName : \\\"Nordmann\\\", email : \\\"onordmann@online.no\\\" }, $p1, $p2 ]) { observations { code } } }\",\n" +
                "\t\"operationName\" : \"makePatient\",\n" +
                "\t\"variables\" : {\n" +
                "\t\t\"p\" : {\n" +
                "        \"firstName\" : \"Hans\",\n" +
                "        \"lastName\" : \"Herrmann\",\n" +
                "        \"email\" : \"hans@example.com\"\n" +
                "  \t  },\n" +
                "  \t  \"p1\" : {\n" +
                "        \"firstName\" : \"Hans\",\n" +
                "        \"lastName\" : \"Herrmann\",\n" +
                "        \"email\" : \"hans@example.com\"\n" +
                "\t\t}\n" +
                "\t}\n" +
                "}";
        InputStream inputStream = IOStreamUtils.stringAsInputStream(request);
        GraphMorphism graphMorphism = endpoint.parseQueryOrInstance(inputStream);
        assertTrue(graphMorphism instanceof QueryTree);
        List<QueryNode.Root> roots = ((QueryTree) graphMorphism).queryRoots().collect(Collectors.toList());
        assertEquals(1, roots.size());
        QueryNode.Root root = roots.get(0);
        assertEquals(Triple.edge(Name.identifier("createPatient").prefixWith(Name.identifier("Mutation")),
                Name.identifier("result").prefixWith(Name.identifier("createPatient").prefixWith(Name.identifier("Mutation"))),
                Name.identifier("Patient")), root.queryResultEdge());
        List<Branch> arguments = root.children().filter(x -> x instanceof QueryBranch.Selection).collect(Collectors.toList());
        List<Branch> selectionSets = root.children().filter(x -> x instanceof QueryBranch.Projection).collect(Collectors.toList());
        assertEquals(1, arguments.size());
        assertEquals(1, selectionSets.size());
        assertEquals("firstName", selectionSets.get(0).label());
    }



    // faulty ones

    @Test
    public void testErrorUnknownRootSelection() throws IOException {
        String request = "{\n" +
                "\t\"query\" : \"query myFalseQ1 { diagnosticReports { type createdAt } }\"\n" +
                "}\n";
        GraphQueryErrorMessage expectedError = new GraphQueryErrorMessage();
        expectedError.addError(ParsingErrorMessages.unkownRootField("Query", "diagnosticReports"), 1, 18);
        try (InputStream inputStream = IOStreamUtils.stringAsInputStream(request)) {
            endpoint.parseQueryOrInstance(inputStream);
            fail();
        } catch (ProcessingException e) {
            assertEquals(expectedError, e);
        }
    }

    @Test
    public void testErrorUnknownSelection() throws IOException {
        String request = "{\n" +
                "\t\"query\" : \"query myFalseQ2 { patients(id: \\\"12345\\\") { email observations { code recordedAt } } }\"\n" +
                "}\n";
        GraphQueryErrorMessage expectedError = new GraphQueryErrorMessage();
        Stack<String> expectedStack = new Stack<>();
        expectedStack.push("patients");
        expectedStack.push("observations");
        expectedError.addError(ParsingErrorMessages.unknownField(endpoint.displayName(Name.identifier("Observation")), "recordedAt"),1,68, expectedStack);
        try (InputStream inputStream = IOStreamUtils.stringAsInputStream(request)) {
            endpoint.parseQueryOrInstance(inputStream);
            fail();
        } catch (ProcessingException e) {
            assertEquals(expectedError, e);
        }
    }

    @Test
    public void testErrorUnknownArgument() throws IOException {
        String request = "{\n" +
                "\t\"query\" : \"query myFalseQ2 { patients(id: \\\"12345\\\") { email observations(after: \\\"2021-07-09T22:00:09\\\") { code } } }\"\n" +
                "}";
        GraphQueryErrorMessage expectedError = new GraphQueryErrorMessage();
        Stack<String> expectedStack = new Stack<>();
        expectedStack.push("patients");
        expectedStack.push("observations");
        expectedError.addError(ParsingErrorMessages.unknownParam(
                endpoint.displayName(Name.identifier("Patient")),
                endpoint.displayName(Name.identifier("observations").prefixWith(Name.identifier("Patient"))),
                "after"), 1, 61);
        try (InputStream inputStream = IOStreamUtils.stringAsInputStream(request)) {
            endpoint.parseQueryOrInstance(inputStream);
            fail();
        } catch (ProcessingException e) {
            assertEquals(expectedError, e);
        }
    }


    @Test
    public void testErrorNotProvidedVariable() throws IOException {
        String request = "{\n" +
                "\t\"query\" : \"mutation makePatient($p: PatientInput!) { createPatient(person: $p) { firstName } }\",\n" +
                "\t\"variables\" : {\n" +
                "\t}\n" +
                "}";

        GraphQueryErrorMessage expectedError = new GraphQueryErrorMessage();
        expectedError.addError( ParsingErrorMessages.notProvidedVariable("p", "PatientInput"),1, 21);
        try (InputStream inputStream = IOStreamUtils.stringAsInputStream(request)) {
            endpoint.parseQueryOrInstance(inputStream);
            fail();
        } catch (ProcessingException e) {
            assertEquals(expectedError, e);
        }
    }

    @Test
    public void testUnknownVariableType() throws IOException {
        String request = "{\n" +
                "\t\"query\" : \"query ($var : Selection!) { patients  { firstName } }\",\n" +
                "\t\"variables\" : {\n" +
                "\t\t\"var\" : {\n" +
                "\t\t\t\"from\" : \"A\",\n" +
                "\t\t\t\"to\" : \"Å\"\n" +
                "\t\t}\n" +
                "\t}\n" +
                "}";
        GraphQueryErrorMessage expectedError = new GraphQueryErrorMessage();
        expectedError.addError( ParsingErrorMessages.unknownVariableType("var","Selection"),1, 14);
        try (InputStream inputStream = IOStreamUtils.stringAsInputStream(request)) {
            endpoint.parseQueryOrInstance(inputStream);
            fail();
        } catch (ProcessingException e) {
            assertEquals(expectedError, e);
        }

    }

    @Test
    public void testErrorArgumentOfWrongTypeProvided() throws IOException {
        String request = "{\n" +
                "\t\"query\" : \"mutation makePatient($p: PatientInput!) { createPatient(person: $p) { firstName } }\",\n" +
                "\t\"variables\" : {\n" +
                "\t\t\"p\" : \"Hans Herrmann\"\n" +
                "\t}\n" +
                "}";
        GraphQueryErrorMessage expectedError = new GraphQueryErrorMessage();
        expectedError.addError(ParsingErrorMessages.wrongInputForVariableType("p", "\"Hans Herrmann\"", "PatientInput"),1, 21);
        try (InputStream inputStream = IOStreamUtils.stringAsInputStream(request)) {
            endpoint.parseQueryOrInstance(inputStream);
            fail();
        } catch (ProcessingException e) {
            assertEquals(expectedError, e);
        }
    }

    @Test
    public void testErrorNoRoot() throws IOException {
        String request = "{\n" +
                "\t\"query\" : \"mutation makePatient($p: PatientInput!) {  }\",\n" +
                "\t\"variables\" : {\n" +
                "\t\t\"p\" : {\n" +
                "        \"firstName\" : \"Hans\",\n" +
                "        \"lastName\" : \"Herrmann\",\n" +
                "        \"email\" : \"hans@example.com\"\n" +
                "  \t  }\n" +
                "\t}\n" +
                "}";
        GraphQueryErrorMessage expectedError = new GraphQueryErrorMessage();
        expectedError.addError(ParsingErrorMessages.notRootProvided("makePatient", true));
        try (InputStream inputStream = IOStreamUtils.stringAsInputStream(request)) {
            endpoint.parseQueryOrInstance(inputStream);
            fail();
        } catch (ProcessingException e) {
            assertEquals(expectedError, e);
        }
    }

    @Test
    public void testErrorUnsupportedSubscription() throws IOException {
        String request = "{\n" +
                "\t\"query\" : \"subscription { patients  { firstName } }\",\n" +
                "\t\"variables\" : { }\n" +
                "}";
        GraphQueryErrorMessage expectedError = new GraphQueryErrorMessage();
        expectedError.addError(ParsingErrorMessages.unsupportedSubscription(), 1, 0);
        try (InputStream inputStream = IOStreamUtils.stringAsInputStream(request)) {
            endpoint.parseQueryOrInstance(inputStream);
            fail();
        } catch (ProcessingException e) {
            assertEquals(expectedError, e);
        }
    }


    @Test
    public void testErrorNoOperationChosen() throws IOException {
        String request = "{\n" +
                "\t\"query\" : \"mutation makePatient($p: PatientInput!) { createPatient(person: $p) { firstName } } mutation insertThree ($p1: PatientInput!, $p2: PatientInput) { createPatients(personList: [ { firstName : \\\"Ola\\\", lastName : \\\"Nordmann\\\", email : \\\"onordmann@online.no\\\" }, $p1, $p2 ]) { observations { code } } }\",\n" +
                "\t\"variables\" : {\n" +
                "\t\t\"p\" : {\n" +
                "        \"firstName\" : \"Hans\",\n" +
                "        \"lastName\" : \"Herrmann\",\n" +
                "        \"email\" : \"hans@example.com\"\n" +
                "  \t  },\n" +
                "  \t  \"p1\" : {\n" +
                "        \"firstName\" : \"Hans\",\n" +
                "        \"lastName\" : \"Herrmann\",\n" +
                "        \"email\" : \"hans@example.com\"\n" +
                "\t\t}\n" +
                "\t}\n" +
                "}";


        GraphQueryErrorMessage expectedError = new GraphQueryErrorMessage();
        expectedError.addError(ParsingErrorMessages.noOperationChosen());
        try (InputStream inputStream = IOStreamUtils.stringAsInputStream(request)) {
            endpoint.parseQueryOrInstance(inputStream);
            fail();
        } catch (ProcessingException e) {
            assertEquals(expectedError, e);
        }
    }

    @Test
    public void testErrorNonExistingOperationChosen() throws IOException {
        String request = "{\n" +
                "\t\"query\" : \"mutation makePatient($p: PatientInput!) { createPatient(person: $p) { firstName } } mutation insertThree ($p1: PatientInput!, $p2: PatientInput) { createPatients(personList: [ { firstName : \\\"Ola\\\", lastName : \\\"Nordmann\\\", email : \\\"onordmann@online.no\\\" }, $p1, $p2 ]) { observations { code } } }\",\n" +
                "\t\"operationName\" : \"removePatient\",\n" +
                "\t\"variables\" : {\n" +
                "\t\t\"p\" : {\n" +
                "        \"firstName\" : \"Hans\",\n" +
                "        \"lastName\" : \"Herrmann\",\n" +
                "        \"email\" : \"hans@example.com\"\n" +
                "  \t  },\n" +
                "  \t  \"p1\" : {\n" +
                "        \"firstName\" : \"Hans\",\n" +
                "        \"lastName\" : \"Herrmann\",\n" +
                "        \"email\" : \"hans@example.com\"\n" +
                "\t\t}\n" +
                "\t}\n" +
                "}";
        GraphQueryErrorMessage expectedError = new GraphQueryErrorMessage();
        expectedError.addError(ParsingErrorMessages.unknownOperationChosen("removePatient"));
        try (InputStream inputStream = IOStreamUtils.stringAsInputStream(request)) {
            endpoint.parseQueryOrInstance(inputStream);
            fail();
        } catch (ProcessingException e) {
            assertEquals(expectedError, e);
        }
    }



}
