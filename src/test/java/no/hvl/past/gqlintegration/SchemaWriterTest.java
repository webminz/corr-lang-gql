package no.hvl.past.gqlintegration;

import no.hvl.past.gqlintegration.predicates.FieldArgument;
import no.hvl.past.gqlintegration.predicates.QueryMesage;
import no.hvl.past.gqlintegration.schema.GraphQLSchemaWriter;
import no.hvl.past.graph.GraphBuilders;
import no.hvl.past.graph.GraphError;
import no.hvl.past.graph.Sketch;
import no.hvl.past.graph.Universe;
import no.hvl.past.graph.predicates.*;
import no.hvl.past.names.Name;
import no.hvl.past.systems.MessageArgument;
import no.hvl.past.systems.MessageType;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertEquals;

public class SchemaWriterTest extends GraphQLTest {

    // Test vanilla
    @Test
    public void testSimple() throws GraphError, IOException {

        Sketch prelim = contextCreatingBuilder()
                .node("Query.all")
                .node("A")
                .node("Text")
                .edge(Name.identifier("Query.all"), Name.identifier("result").prefixWith(Name.identifier("Query.all")), Name.identifier("A"))
                .edge(Name.identifier("A"), Name.identifier("name").prefixWith(Name.identifier("A")), Name.identifier("Text"))
                .graph(Name.anonymousIdentifier())
                .startDiagram(StringDT.getInstance())
                .map(Universe.ONE_NODE_THE_NODE, Name.identifier("Text"))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(TargetMultiplicity.getInstance(1, 1))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("A"))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("name").prefixWith(Name.identifier("A")))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("Text"))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(Ordered.getInstance())
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Query.all"))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("result").prefixWith(Name.identifier("Query.all")))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("A"))
                .endDiagram(Name.anonymousIdentifier())
                .sketch("Test")
                .getResult(Sketch.class);
        List<MessageArgument> agrs = new ArrayList<>();
        QueryMesage qm = new QueryMesage(prelim.carrier(), Name.identifier("Query.all"), "Query", "all", agrs);
        agrs.add(new MessageArgument(qm, Name.identifier("result").prefixWith(Name.identifier("Query.all")), Name.identifier("A"), 0, true));

        Sketch result = prelim.restrict(Arrays.asList(qm, agrs.get(0)));

        String expected =     "type Query {\n" +
                "   all : [A]\n" +
                "}\n\n" +
                "type A {\n" +
                "   name : String!\n" +
                "}\n" +
                "\n";

        testExpectedSchema(result, expected);
    }

    private void testExpectedSchema(Sketch result, String expected) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GraphQLSchemaWriter writer = new GraphQLSchemaWriter();
        result.accept(writer);
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(bos));
        writer.printToBuffer(bufferedWriter);
        bufferedWriter.flush();
        String actual = bos.toString("UTF-8");
        assertEquals(expected, actual);
    }

    @Test
    public void testFieldArguments() throws GraphError, IOException {
        Sketch result = contextCreatingBuilder()
                .node("Mutation")
                .edge(Name.identifier("Mutation"), Name.identifier("insert").prefixWith(Name.identifier("Mutation")), Name.identifier("Boolean"))
                .graph(Name.anonymousIdentifier())
                .startDiagram(new FieldArgument("id", Name.identifier("ID"), false, true))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Mutation"))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("insert").prefixWith(Name.identifier("Mutation")))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("Boolean"))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(new FieldArgument("firstname", Name.identifier("String"), false, false))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Mutation"))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("insert").prefixWith(Name.identifier("Mutation")))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("Boolean"))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(new FieldArgument("street", Name.identifier("String"), false, false))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Mutation"))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("insert").prefixWith(Name.identifier("Mutation")))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("Boolean"))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(TargetMultiplicity.getInstance(0,1))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Mutation"))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("insert").prefixWith(Name.identifier("Mutation")))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("Boolean"))
                .endDiagram(Name.anonymousIdentifier())
                .sketch("Test")
                .getResult(Sketch.class);

        String expected = "type Mutation {\n" +
                "   insert(id : ID!\n" +
                "      firstname : String\n" +
                "      street : String) : Boolean\n" +
                "}\n" +
                "\n";

        testExpectedSchema(result, expected);
    }

    @Test
    public void testCustomScalarsAndEnums() throws GraphError, IOException {
        Sketch result = contextCreatingBuilder()
                .node("Query")
                .node("Publication")
                .node("PublicationType")
                .node("Date")
                .edge(Name.identifier("Query"), Name.identifier("pubs").prefixWith(Name.identifier("Query")), Name.identifier("Publication"))
                .edge(Name.identifier("Publication"), Name.identifier("title").prefixWith(Name.identifier("Publication")), Name.identifier("String"))
                .edge(Name.identifier("Publication"), Name.identifier("type").prefixWith(Name.identifier("Publication")), Name.identifier("PublicationType"))
                .edge(Name.identifier("Publication"), Name.identifier("published").prefixWith(Name.identifier("Publication")), Name.identifier("Date"))
                .graph(Name.anonymousIdentifier())
                .startDiagram(DataTypePredicate.getInstance())
                .map(Universe.ONE_NODE_THE_NODE, Name.identifier("Date"))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(EnumValue.getInstance(Name.identifier("JOURNAL_ARTICLE"), Name.identifier("INPROCEEDINGS"), Name.identifier("TECH_REPORT")))
                .map(Universe.ONE_NODE_THE_NODE, Name.identifier("PublicationType"))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(TargetMultiplicity.getInstance(0,1))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Publication"))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("title").prefixWith(Name.identifier("Publication")))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("String"))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(TargetMultiplicity.getInstance(0,1))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Publication"))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("type").prefixWith(Name.identifier("Publication")))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("PublicationType"))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(TargetMultiplicity.getInstance(0,1))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Publication"))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("published").prefixWith(Name.identifier("Publication")))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("Date"))
                .endDiagram(Name.anonymousIdentifier())
                .sketch("Test")
                .getResult(Sketch.class);

        String expected = "scalar Date\n" +
                "\n" +
                "enum PublicationType {\n" +
                "   JOURNAL_ARTICLE\n" +
                "   INPROCEEDINGS\n" +
                "   TECH_REPORT\n" +
                "}\n" +
                "\n" +
                "type Publication {\n" +
                "   title : String\n" +
                "   published : Date\n" +
                "   type : PublicationType\n" +
                "}\n" +
                "\n" +
                "type Query {\n" +
                "   pubs : [Publication]\n" +
                "}\n\n";

        testExpectedSchema(result, expected);
    }

    @Test
    public void testDuplicatedNames() throws GraphError, IOException {
        Sketch sketch = contextCreatingBuilder()
                .node("Query")
                .edge(Name.identifier("Query"), Name.identifier("partners").prefixWith(Name.identifier("Query")), Name.identifier("Partner"))
                .node(Name.identifier("Address").prefixWith(Name.identifier("Sales")))
                .edge(Name.identifier("Address").prefixWith(Name.identifier("Sales")), Name.identifier("address").prefixWith(Name.identifier("Address").prefixWith(Name.identifier("Sales"))), Name.identifier("String"))
                .node(Name.identifier("Address").prefixWith(Name.identifier("Invoices")))
                .edge(Name.identifier("Address").prefixWith(Name.identifier("Invoices")), Name.identifier("address").prefixWith(Name.identifier("Address").prefixWith(Name.identifier("Invoices"))), Name.identifier("String"))
                .node(Name.identifier("Partner"))
                .edge(Name.identifier("Partner"), Name.identifier("id").prefixWith(Name.identifier("Customer")).prefixWith(Name.identifier("Sales")), Name.identifier("ID"))
                .edge(Name.identifier("Partner"), Name.identifier("id").prefixWith(Name.identifier("Customer")).prefixWith(Name.identifier("Invoices")), Name.identifier("ID"))
                .edge(Name.identifier("Partner"), Name.identifier("id").prefixWith(Name.identifier("Employee")), Name.identifier("ID"))
                .edge(Name.identifier("Partner"), Name.identifier("deliveryAddress").prefixWith(Name.identifier("Customer")), Name.identifier("Address").prefixWith(Name.identifier("Sales")))
                .edge(Name.identifier("Partner"), Name.identifier("billingAddress").prefixWith(Name.identifier("Customer")), Name.identifier("Address").prefixWith(Name.identifier("Invoices")))
                .edge(Name.identifier("Partner"), Name.identifier("salary").prefixWith(Name.identifier("Employee")), Name.identifier("Int"))
                .graph(Name.anonymousIdentifier())
                .startDiagram(TargetMultiplicity.getInstance(0, 1))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Address").prefixWith(Name.identifier("Sales")))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("address").prefixWith(Name.identifier("Address").prefixWith(Name.identifier("Sales"))))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("String"))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(TargetMultiplicity.getInstance(0, 1))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Address").prefixWith(Name.identifier("Invoices")))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("address").prefixWith(Name.identifier("Address").prefixWith(Name.identifier("Invoices"))))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("String"))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(TargetMultiplicity.getInstance(1, 1))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Partner"))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("id").prefixWith(Name.identifier("Customer")).prefixWith(Name.identifier("Sales")))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("ID"))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(TargetMultiplicity.getInstance(1, 1))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Partner"))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("id").prefixWith(Name.identifier("Customer")).prefixWith(Name.identifier("Invoices")))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("ID"))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(TargetMultiplicity.getInstance(1, 1))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Partner"))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("id").prefixWith(Name.identifier("Employee")))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("ID"))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(TargetMultiplicity.getInstance(0, 1))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Partner"))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("deliveryAddress").prefixWith(Name.identifier("Customer")))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("Address").prefixWith(Name.identifier("Sales")))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(TargetMultiplicity.getInstance(0, 1))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Partner"))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("billingAddress").prefixWith(Name.identifier("Customer")))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("Address").prefixWith(Name.identifier("Invoices")))
                .endDiagram(Name.anonymousIdentifier())
                .startDiagram(TargetMultiplicity.getInstance(0, 1))
                .map(Universe.ARROW_SRC_NAME, Name.identifier("Partner"))
                .map(Universe.ARROW_LBL_NAME, Name.identifier("salary").prefixWith(Name.identifier("Employee")))
                .map(Universe.ARROW_TRG_NAME, Name.identifier("Int"))
                .endDiagram(Name.anonymousIdentifier())
                .sketch("Test")
                .getResult(Sketch.class);
        String expected = "type Invoices_Address {\n" +
                "   address : String\n" +
                "}\n" +
                "\n" +
                "type Partner {\n" +
                "   billingAddress : Invoices_Address\n" +
                "   salary : Int\n" +
                "   deliveryAddress : Sales_Address\n" +
                "   invoices_Customer_id : ID!\n" +
                "   employee_id : ID!\n" +
                "   sales_Customer_id : ID!\n" +
                "}\n" +
                "\n" +
                "type Query {\n" +
                "   partners : [Partner]\n" +
                "}\n" +
                "\n" +
                "type Sales_Address {\n" +
                "   address : String\n" +
                "}\n\n";

        testExpectedSchema(sketch,expected);
    }

    @Test
    public void testDuplicateFieldArguments() {
        // TODO field arguments with the same type are merged together

        // TODO field arguments with different types cause an exception
    }



}
