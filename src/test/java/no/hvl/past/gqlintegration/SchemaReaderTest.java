package no.hvl.past.gqlintegration;

import com.google.common.collect.Sets;
import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import no.hvl.past.gqlintegration.predicates.FieldArgument;
import no.hvl.past.gqlintegration.schema.GraphQLSchemaReader;
import no.hvl.past.graph.*;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.predicates.Ordered;
import no.hvl.past.graph.predicates.StringDT;
import no.hvl.past.graph.predicates.TargetMultiplicity;
import no.hvl.past.logic.Formula;
import no.hvl.past.names.Name;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static junit.framework.TestCase.*;

public class SchemaReaderTest extends GraphQLTest {

    private GraphQLSchema littleManuallyProgrammed;

    private String SALES_SCHEMA = "type Address {\n" +
            "  street: String\n" +
            "  city: String\n" +
            "  postalCode: String\n" +
            "  state: String\n" +
            "  country: String\n" +
            "}\n" +
            "\n" +
            "type Customer {\n" +
            "  id: ID!\n" +
            "  name: String\n" +
            "  email: String\n" +
            "  address: Address\n" +
            "  purchases: [Purchase]\n" +
            "}\n" +
            "\n" +
            "type Mutation {\n" +
            "  createCustomer(name: String!, email: String): Customer!\n" +
            "  updateCustomer(customer: ID!, name: String, email: String): Customer\n" +
            "  setAddress(\n" +
            "    customer: ID!\n" +
            "    street: String\n" +
            "    city: String\n" +
            "    postalCode: String\n" +
            "    state: String\n" +
            "    country: String\n" +
            "  ): Customer\n" +
            "  deleteCustomer(customer: ID!): Customer\n" +
            "  createPurchase(customer: ID!, date: String!, store: ID!): Purchase\n" +
            "  addPurchaseItem(purchase: ID!, product: ID!, quantity: Int): PurchaseItem\n" +
            "  deletePurchase(purchase: ID!): Purchase\n" +
            "  createStore(\n" +
            "    manager: ID\n" +
            "    street: String\n" +
            "    city: String\n" +
            "    postalCode: String\n" +
            "    state: String\n" +
            "    country: String\n" +
            "  ): Store\n" +
            "  deleteStore(store: ID!): Store\n" +
            "}\n" +
            "\n" +
            "type Purchase {\n" +
            "  id: ID!\n" +
            "  date: String\n" +
            "  customer: Customer!\n" +
            "  store: Store!\n" +
            "  items: [PurchaseItem]\n" +
            "}\n" +
            "\n" +
            "type PurchaseItem {\n" +
            "  productId: ID!\n" +
            "  purchaseId: ID!\n" +
            "  quantity: Int\n" +
            "}\n" +
            "\n" +
            "type Query {\n" +
            "  customer(customer: ID!): Customer\n" +
            "  customers: [Customer]\n" +
            "  purchase(purchase: ID!): Purchase\n" +
            "  purchases: [Purchase]\n" +
            "  store(store: ID!): Store\n" +
            "  stores: [Store]\n" +
            "}\n" +
            "\n" +
            "type Store {\n" +
            "  id: ID!\n" +
            "  manager: ID!\n" +
            "  address: Address\n" +
            "  purchases: [Purchase]\n" +
            "}";


    private GraphQLSchema salesSchema;

    @Before
    public void setUp() {
        GraphQLObjectType a = GraphQLObjectType.newObject()
                .name("A")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("name")
                        .type(Scalars.GraphQLString).build()
                ).build();
        GraphQLObjectType b = GraphQLObjectType.newObject()
                .name("B")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("age").type(Scalars.GraphQLInt).build())
                .field(GraphQLFieldDefinition.newFieldDefinition().name("a").type(a).build())
                .build();
        GraphQLObjectType query = GraphQLObjectType.newObject()
                .name("Query")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("b").type(b).build())
                .build();


        this.littleManuallyProgrammed = GraphQLSchema.newSchema()
                .query(query)
                .additionalType(b)
                .additionalType(a)
                .build();

        TypeDefinitionRegistry parse = new SchemaParser().parse(SALES_SCHEMA);
        this.salesSchema = new SchemaGenerator().makeExecutableSchema(parse, RuntimeWiring.newRuntimeWiring().build());
    }

    @Test
    public void testSmall() throws GraphError {
        GraphQLSchemaReader converter = new GraphQLSchemaReader(new UniverseImpl(UniverseImpl.EMPTY)); ;
        Sketch convert = converter.convert(Name.identifier("test"), littleManuallyProgrammed);


        Set<Name> expectedNodes = new HashSet<>();
        expectedNodes.add(Name.identifier("A"));
        expectedNodes.add(Name.identifier("B"));
        expectedNodes.add(Name.identifier("Int"));
        expectedNodes.add(Name.identifier("String"));
        expectedNodes.add(Name.identifier("Boolean"));

        assertEquals(expectedNodes, convert.carrier().nodes().collect(Collectors.toSet()));

        Set<Triple> expectedEdges = new HashSet<>();
        expectedEdges.add(Triple.edge(Name.identifier("A"), Name.identifier("name").prefixWith(Name.identifier("A")), Name.identifier("String")));
        expectedEdges.add(Triple.edge(Name.identifier("B"), Name.identifier("age").prefixWith(Name.identifier("B")), Name.identifier("Int")));
        expectedEdges.add(Triple.edge(Name.identifier("B"), Name.identifier("a").prefixWith(Name.identifier("B")), Name.identifier("A")));
        assertEquals(expectedEdges, convert.carrier().edges().collect(Collectors.toSet()));
    }

    @Test
    public void testReadBigger() throws GraphError, IOException {
        GraphQLSchemaReader converter = new GraphQLSchemaReader(new UniverseImpl(UniverseImpl.EMPTY));
        Sketch result = converter.convert(Name.identifier("sales"), salesSchema);

        assertsAboutSalesSchema(result);

    }

    private void assertsAboutSalesSchema(Sketch result) {
        Set<Name> nodes = result.carrier().nodes().collect(Collectors.toSet());
        Set<Name> expectedNodes = Sets.newHashSet(
                Name.identifier("String"),
                Name.identifier("Int"),
                Name.identifier("ID"),
                Name.identifier("Boolean"),
                Name.identifier("Address"),
                Name.identifier("Store"),
                Name.identifier("Purchase"),
                Name.identifier("PurchaseItem"),
                Name.identifier("PurchaseItem"),
                Name.identifier("Customer"),
                Name.identifier("Mutation"),
                Name.identifier("Query")
        );
        assertEquals(expectedNodes, nodes);


        Set<Triple> expectedFieldsOfStore = Sets.newHashSet(
                Triple.edge(
                        Name.identifier("Store"),
                        Name.identifier("id").prefixWith(Name.identifier("Store")),
                        Name.identifier("ID")
                ),
                Triple.edge(
                        Name.identifier("Store"),
                        Name.identifier("manager").prefixWith(Name.identifier("Store")),
                        Name.identifier("ID")
                ),
                Triple.edge(
                        Name.identifier("Store"),
                        Name.identifier("address").prefixWith(Name.identifier("Store")),
                        Name.identifier("Address")
                ),
                Triple.edge(
                        Name.identifier("Store"),
                        Name.identifier("purchases").prefixWith(Name.identifier("Store")),
                        Name.identifier("Purchase")
                )
        );
        assertEquals(expectedFieldsOfStore, result.carrier().outgoing(Name.identifier("Store")).filter(Triple::isEddge).collect(Collectors.toSet()));


        assertTrue(result.diagramsOn(Triple.node(Name.identifier("String"))).anyMatch(d -> StringDT.class.isAssignableFrom(d.label().getClass())));

        assertEquals(1, result.diagramsOn(Triple.edge(
                Name.identifier("Store"),
                Name.identifier("manager").prefixWith(Name.identifier("Store")),
                Name.identifier("ID")
        )).count());
        assertTrue(result.diagramsOn(Triple.edge(
                Name.identifier("Store"),
                Name.identifier("manager").prefixWith(Name.identifier("Store")),
                Name.identifier("ID")
        )).allMatch(d -> {
            return TargetMultiplicity.class.isAssignableFrom(d.label().getClass()) &&
                    ((TargetMultiplicity)d.label()).getUpperBound() == 1 &&
                    ((TargetMultiplicity)d.label()).getLowerBound() == 1 ;
        }));


        assertEquals(1, result.diagramsOn(Triple.edge(
                Name.identifier("Store"),
                Name.identifier("address").prefixWith(Name.identifier("Store")),
                Name.identifier("Address")
        )).count());
        assertTrue(result.diagramsOn(Triple.edge(
                Name.identifier("Store"),
                Name.identifier("address").prefixWith(Name.identifier("Store")),
                Name.identifier("Address")
        )).allMatch(d -> {
            return TargetMultiplicity.class.isAssignableFrom(d.label().getClass()) &&
                    ((TargetMultiplicity)d.label()).getUpperBound() == 1 &&
                    ((TargetMultiplicity)d.label()).getLowerBound() == 0 ;
        }));

        assertEquals(1, result.diagramsOn(Triple.edge(
                Name.identifier("Store"),
                Name.identifier("purchases").prefixWith(Name.identifier("Store")),
                Name.identifier("Purchase")
        )).count());
        assertTrue(result.diagramsOn(Triple.edge(
                Name.identifier("Store"),
                Name.identifier("purchases").prefixWith(Name.identifier("Store")),
                Name.identifier("Purchase")
        )).allMatch(d -> {
            return Ordered.class.isAssignableFrom(d.label().getClass());
        }));

        Set<Formula<Graph>> argumentsOfSetAddress = result.diagramsOn(Triple.edge(
                Name.identifier("Mutation"),
                Name.identifier("setAddress").prefixWith(Name.identifier("Mutation")),
                Name.identifier("Customer")
        )).filter(d -> FieldArgument.class.isAssignableFrom(d.label().getClass())).map(Diagram::label).collect(Collectors.toSet());

        assertFalse(argumentsOfSetAddress.isEmpty());
        assertEquals(6, argumentsOfSetAddress.size());

        assertTrue(argumentsOfSetAddress.contains(
                new FieldArgument("customer", Name.identifier("ID"), false, true)
        ));
        assertTrue(argumentsOfSetAddress.contains(
                new FieldArgument("country", Name.identifier("String"), false, false)
        ));
    }

    @Test
    public void testFromIntrospectionQuery() throws Exception {
        makeSureServerIsRunning("http://localhost:4011");
        GraphQLAdapter adaptor = createAdapter();
        Sketch sketch = adaptor.parseSchema(Name.identifier("SALES"), "http://localhost:4011");

        assertsAboutSalesSchema(sketch);
    }



}
