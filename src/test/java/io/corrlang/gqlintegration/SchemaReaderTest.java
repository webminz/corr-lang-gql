package io.corrlang.gqlintegration;

import com.google.common.collect.Sets;
import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.corrlang.domain.MessageArgument;
import io.corrlang.domain.MessageType;
import io.corrlang.domain.Sys;
import io.corrlang.gqlintegration.predicates.InputType;
import io.corrlang.gqlintegration.schema.GraphQLSchemaReader;
import no.hvl.past.graph.*;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.predicates.Ordered;
import no.hvl.past.graph.predicates.StringDT;
import no.hvl.past.graph.predicates.TargetMultiplicity;
import no.hvl.past.names.Name;
import no.hvl.past.UnsupportedFeatureException;

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
    public void testSmall() throws GraphError, UnsupportedFeatureException {
        GraphQLSchemaReader converter = new GraphQLSchemaReader(new UniverseImpl(UniverseImpl.EMPTY)); ;
        Sys convert = converter.convert("http:localhost", Name.identifier("test"), littleManuallyProgrammed,objectMapper,jsonFactory);


        Set<Name> expectedNodes = new HashSet<>();
        expectedNodes.add(Name.identifier("A"));
        expectedNodes.add(Name.identifier("b").prefixWith(Name.identifier("Query")));
        expectedNodes.add(Name.identifier("B"));
        expectedNodes.add(Name.identifier("Int"));
        expectedNodes.add(Name.identifier("String"));
        expectedNodes.add(Name.identifier("Boolean"));
        expectedNodes.add(Name.identifier("Query"));

        assertEquals(expectedNodes, convert.schema().carrier().nodes().collect(Collectors.toSet()));

        Set<Triple> expectedEdges = new HashSet<>();
        expectedEdges.add(Triple.edge(Name.identifier("A"), Name.identifier("name").prefixWith(Name.identifier("A")), Name.identifier("String")));
        expectedEdges.add(Triple.edge(Name.identifier("b").prefixWith(Name.identifier("Query")), Name.identifier("result").prefixWith(Name.identifier("b").prefixWith(Name.identifier("Query"))), Name.identifier("B")));
        expectedEdges.add(Triple.edge(Name.identifier("B"), Name.identifier("age").prefixWith(Name.identifier("B")), Name.identifier("Int")));
        expectedEdges.add(Triple.edge(Name.identifier("B"), Name.identifier("a").prefixWith(Name.identifier("B")), Name.identifier("A")));
        assertEquals(expectedEdges, convert.schema().carrier().edges().collect(Collectors.toSet()));
    }

    @Test
    public void testReadBigger() throws GraphError, IOException, UnsupportedFeatureException {
        GraphQLSchemaReader converter = new GraphQLSchemaReader(new UniverseImpl(UniverseImpl.EMPTY));
        Sys result = converter.convert("http://localhost",Name.identifier("sales"), salesSchema,objectMapper,jsonFactory);

        assertsAboutSalesSchema(result);

    }

    static void assertsAboutSalesSchema(Sys result) {
        Set<Name> nodes = result.schema().carrier().nodes().collect(Collectors.toSet());
        Set<Name> expectedNodes = Sets.newHashSet(
                Name.identifier("Mutation"),
                Name.identifier("Query"),
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
                Name.identifier("createCustomer").prefixWith(Name.identifier("Mutation")),
                Name.identifier("updateCustomer").prefixWith(Name.identifier("Mutation")),
                Name.identifier("setAddress").prefixWith(Name.identifier("Mutation")),
                Name.identifier("deleteCustomer").prefixWith(Name.identifier("Mutation")),
                Name.identifier("createPurchase").prefixWith(Name.identifier("Mutation")),
                Name.identifier("addPurchaseItem").prefixWith(Name.identifier("Mutation")),
                Name.identifier("deletePurchase").prefixWith(Name.identifier("Mutation")),
                Name.identifier("createStore").prefixWith(Name.identifier("Mutation")),
                Name.identifier("deleteStore").prefixWith(Name.identifier("Mutation")),
                Name.identifier("customer").prefixWith(Name.identifier("Query")),
                Name.identifier("customers").prefixWith(Name.identifier("Query")),
                Name.identifier("purchase").prefixWith(Name.identifier("Query")),
                Name.identifier("purchases").prefixWith(Name.identifier("Query")),
                Name.identifier("store").prefixWith(Name.identifier("Query")),
                Name.identifier("stores").prefixWith(Name.identifier("Query"))
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
        assertEquals(expectedFieldsOfStore, result.schema().carrier().outgoing(Name.identifier("Store")).filter(Triple::isEddge).collect(Collectors.toSet()));


        assertTrue(result.schema().diagramsOn(Triple.node(Name.identifier("String"))).anyMatch(d -> StringDT.class.isAssignableFrom(d.label().getClass())));

        assertEquals(1, result.schema().diagramsOn(Triple.edge(
                Name.identifier("Store"),
                Name.identifier("manager").prefixWith(Name.identifier("Store")),
                Name.identifier("ID")
        )).count());
        assertTrue(result.schema().diagramsOn(Triple.edge(
                Name.identifier("Store"),
                Name.identifier("manager").prefixWith(Name.identifier("Store")),
                Name.identifier("ID")
        )).allMatch(d -> {
            return TargetMultiplicity.class.isAssignableFrom(d.label().getClass()) &&
                    ((TargetMultiplicity)d.label()).getUpperBound() == 1 &&
                    ((TargetMultiplicity)d.label()).getLowerBound() == 1 ;
        }));


        assertEquals(1, result.schema().diagramsOn(Triple.edge(
                Name.identifier("Store"),
                Name.identifier("address").prefixWith(Name.identifier("Store")),
                Name.identifier("Address")
        )).count());
        assertTrue(result.schema().diagramsOn(Triple.edge(
                Name.identifier("Store"),
                Name.identifier("address").prefixWith(Name.identifier("Store")),
                Name.identifier("Address")
        )).allMatch(d -> {
            return TargetMultiplicity.class.isAssignableFrom(d.label().getClass()) &&
                    ((TargetMultiplicity)d.label()).getUpperBound() == 1 &&
                    ((TargetMultiplicity)d.label()).getLowerBound() == 0 ;
        }));

        assertEquals(1, result.schema().diagramsOn(Triple.edge(
                Name.identifier("Store"),
                Name.identifier("purchases").prefixWith(Name.identifier("Store")),
                Name.identifier("Purchase")
        )).count());
        assertTrue(result.schema().diagramsOn(Triple.edge(
                Name.identifier("Store"),
                Name.identifier("purchases").prefixWith(Name.identifier("Store")),
                Name.identifier("Purchase")
        )).allMatch(d -> {
            return Ordered.class.isAssignableFrom(d.label().getClass());
        }));


        Set<Triple> argumentsOfSetAddress = result.schema().carrier().outgoing(Name.identifier("setAddress").prefixWith(Name.identifier("Mutation"))).filter(Triple::isEddge).collect(Collectors.toSet());
        assertFalse(argumentsOfSetAddress.isEmpty());
        assertEquals(7, argumentsOfSetAddress.size());

        MessageType msgTypeForSetAddress = result.getMessageType(Name.identifier("setAddress").prefixWith(Name.identifier("Mutation")));
        assertNotNull(msgTypeForSetAddress);
        MessageArgument thirdArgsOfSetaddress = msgTypeForSetAddress.inputs().get(2);
        assertNotNull(thirdArgsOfSetaddress);
        assertEquals(Name.identifier("String"),thirdArgsOfSetaddress.returnType());
        assertEquals(Name.identifier("city").prefixWith(Name.identifier("setAddress").prefixWith(Name.identifier("Mutation"))), thirdArgsOfSetaddress.asEdge().getLabel());
        assertTrue(thirdArgsOfSetaddress.isInput());
        assertEquals(Name.identifier("Mutation"), msgTypeForSetAddress.getGroup().get().getTypeName());
    }


    @Test
    public void testInputTypes() throws UnsupportedFeatureException, GraphError {
        String content = "type Query {\n" +
                "\tcreateCustomer(data: CustomerData): Customer\n" +
                "}\n" +
                "\n" +
                "type Customer {\n" +
                "\tid: ID!\n" +
                "\tname: String!\n" +
                "\temail: String\n" +
                "\tage: Int\n" +
                "}\n" +
                "\n" +
                "input CustomerData {\n" +
                "\tname: String!\n" +
                "\temail: String\n" +
                "\tage: Int\n" +
                "}\n";

        TypeDefinitionRegistry parse = new SchemaParser().parse(content);
        GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(parse, RuntimeWiring.newRuntimeWiring().build());

        GraphQLSchemaReader converter = new GraphQLSchemaReader(new UniverseImpl(UniverseImpl.EMPTY));
        Sketch result = converter.convert("http://localhost",Name.anonymousIdentifier(), graphQLSchema,objectMapper,jsonFactory).schema();
        assertEquals(3, result.carrier().outgoing(Name.identifier("CustomerData")).filter(Triple::isEddge).count());

        assertTrue(result.diagramsOn(Triple.node(Name.identifier("CustomerData"))).anyMatch(d -> d.label() instanceof InputType));
    }


}
