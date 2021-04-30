package no.hvl.past.gqlintegration.integration;

import no.hvl.past.gqlintegration.GraphQLAdapter;
import no.hvl.past.gqlintegration.GraphQLTest;
import no.hvl.past.gqlintegration.schema.GraphQLSchemaWriter;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.names.Name;
import no.hvl.past.plugin.UnsupportedFeatureException;
import no.hvl.past.systems.Sys;
import no.hvl.past.techspace.TechSpaceException;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class TestExternalSchemas extends GraphQLTest {

    @Test
    public void testRickAndMortyAPI() throws IOException, UnsupportedFeatureException, TechSpaceException {
        GraphQLAdapter adapter = createAdapter();
        Sys rickAndMorty = adapter.parseSchema(Name.identifier("RickAndMorty"), "https://rickandmortyapi.com/graphql");
        List<Name> types = rickAndMorty.schema().carrier().nodes().collect(Collectors.toList());
        assertNotEmpty(types);
        Optional<Triple> l1 = rickAndMorty.lookup("Query", "characters");
        assertTrue(l1.isPresent());
        assertTrue(l1.get().isNode());
        Optional<Triple> l2 = rickAndMorty.lookup("Query", "characters", "filter");
        assertTrue(l2.isPresent());
        assertTrue(l2.get().isEddge());
        assertEquals(l1.get().getLabel(), l2.get().getSource());
        assertEquals(Name.identifier("FilterCharacter"), l2.get().getTarget());
    }


    @Test
    public void testKarlEriksDemo() throws IOException, UnsupportedFeatureException, TechSpaceException {
        GraphQLAdapter adapter = createAdapter();
        Sys ke = adapter.parseSchema(Name.identifier("Persons"), "http://localhost:52297/graphql");
        List<Name> types = ke.schema().carrier().nodes().collect(Collectors.toList());
        assertNotEmpty(types);


        ke.messages().forEach(m -> System.out.println(m.typeName().printRaw()));
        Optional<Triple> lookup = ke.lookup("PersonsRelationsQuery", "persons");
        assertTrue(lookup.isPresent());
        Optional<Triple> result = ke.schema().carrier().get(Name.identifier("result").prefixWith(lookup.get().getLabel()));
        assertTrue(result.isPresent());

        assertEquals(Name.identifier("PersonsType"), result.get().getTarget());



        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        adapter.writeSchema(ke, bos);

        String expected = "type Query {\n" +
                "   person(id : Int) : PersonsType\n" +
                "   persons : [PersonsType]\n" +
                "   relation(id : Int) : PersonalRelationsType\n" +
                "   relations : [PersonalRelationsType]\n" +
                "}\n" +
                "\n" +
                "type Mutation {\n" +
                "   addRelation(relation : AddPersonalRelationsInput!) : PersonalRelationsType\n" +
                "   createPerson(person : AddPersonInput!) : PersonsType\n" +
                "   deletePerson(personId : ID!) : String\n" +
                "   deleteRelation(relationId : ID!) : String\n" +
                "   updatePerson(person : AddPersonInput!, personId : ID!) : PersonsType\n" +
                "   updateRelation(relation : AddPersonalRelationsInput!, relationId : ID!) : PersonalRelationsType\n" +
                "}\n" +
                "\n" +
                "scalar Date\n" +
                "\n" +
                "scalar DateTime\n" +
                "\n" +
                "enum RelationshipType {\n" +
                "   DAUGHTER\n" +
                "   FATHER\n" +
                "   SON\n" +
                "   SISTER\n" +
                "   BROTHER\n" +
                "   MOTHER\n" +
                "   PARTNER\n" +
                "}\n" +
                "\n" +
                "type PersonalRelationsType {\n" +
                "   relativeId : ID\n" +
                "   relationType : RelationshipType\n" +
                "   reverseRelationType : RelationshipType\n" +
                "   id : ID\n" +
                "   person : PersonsType\n" +
                "   relative : PersonsType\n" +
                "   personId : ID\n" +
                "}\n" +
                "\n" +
                "type PersonsType {\n" +
                "   natIdNr : String!\n" +
                "   sex : String!\n" +
                "   personalRelations : [PersonalRelationsType]\n" +
                "   dateOfDeath : DateTime\n" +
                "   email : String!\n" +
                "   nationality : String!\n" +
                "   lastName : String!\n" +
                "   address : String!\n" +
                "   id : ID\n" +
                "   firstName : String!\n" +
                "   dateOfBirth : DateTime!\n" +
                "}\n" +
                "\n" +
                "input AddPersonInput {\n" +
                "   dateOfDeath : Date\n" +
                "   nationality : String\n" +
                "   natIdNr : String\n" +
                "   dateOfBirth : Date\n" +
                "   email : String\n" +
                "   firstName : String\n" +
                "   address : String\n" +
                "   lastName : String\n" +
                "   sex : String\n" +
                "}\n" +
                "\n" +
                "input AddPersonalRelationsInput {\n" +
                "   relativeId : Int!\n" +
                "   relationType : String\n" +
                "   reverseRelationType : String\n" +
                "   personId : Int!\n" +
                "}\n\n";

        assertEquals(expected, bos.toString("UTF-8"));


    }


    @Test
    public void testKarlEriksDemo2() throws IOException, UnsupportedFeatureException, TechSpaceException {
        GraphQLAdapter adapter = createAdapter();
        Sys ke = adapter.parseSchema(Name.identifier("Pregnancies"), "http://localhost:52298/graphql");
        List<Name> types = ke.schema().carrier().nodes().collect(Collectors.toList());
        assertNotEmpty(types);


        ke.messages().forEach(m -> System.out.println(m.typeName().printRaw()));
        Optional<Triple> lookup = ke.lookup("PregnancyQuery", "persons");
        assertTrue(lookup.isPresent());
        Optional<Triple> result = ke.schema().carrier().get(Name.identifier("result").prefixWith(lookup.get().getLabel()));
        assertTrue(result.isPresent());

        assertEquals(Name.identifier("PersonsType"), result.get().getTarget());
        ke.features(Name.identifier("PersonsType")).forEach(t -> System.out.println(t.toString()));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        adapter.writeSchema(ke, bos);


        String expected = "type Query {\n" +
                "   childbirth(id : Int) : ChildbirthsType\n" +
                "   childbirths : [ChildbirthsType]\n" +
                "   obstetrician(id : Int) : ObstetriciansType\n" +
                "   obstetricians : [ObstetriciansType]\n" +
                "   person(id : Int) : PersonsType\n" +
                "   persons : [PersonsType]\n" +
                "   pregnancies : [PregnanciesType]\n" +
                "   pregnancy(id : Int) : PregnanciesType\n" +
                "}\n" +
                "\n" +
                "type Mutation {\n" +
                "   addChildbirth(childbirth : AddBirthInput!) : ChildbirthsType\n" +
                "   addObstetrician(obstetrician : AddObstetricianInput!) : ObstetriciansType\n" +
                "   addPregnancy(pregnancy : AddPregnancyInput!) : PregnanciesType\n" +
                "   createPerson(person : AddPersonInput!) : PersonsType\n" +
                "   deleteChildbirth(birthId : ID!) : String\n" +
                "   deleteObstetrician(obstetricianId : ID!) : String\n" +
                "   deletePerson(personId : ID!) : String\n" +
                "   deletePregnancy(pregnancyId : ID!) : String\n" +
                "   updateChildbirth(childbirth : AddBirthInput!, birthId : ID!) : ChildbirthsType\n" +
                "   updateObstetrician(obstetrician : AddObstetricianInput!, obstetricianId : ID!) : ObstetriciansType\n" +
                "   updatePerson(person : AddPersonInput!, personId : ID!) : PersonsType\n" +
                "   updatePregnancy(pregnancy : AddPregnancyInput!, pregnancyId : ID!) : PregnanciesType\n" +
                "}\n" +
                "\n" +
                "type AddBirthInput {\n" +
                "}\n" +
                "\n" +
                "type AddObstetricianInput {\n" +
                "}\n" +
                "\n" +
                "type AddPersonInput {\n" +
                "}\n" +
                "\n" +
                "type AddPregnancyInput {\n" +
                "}\n" +
                "\n" +
                "type ChildbirthsType {\n" +
                "   notes : String!\n" +
                "   pregnancyId : ID\n" +
                "   id : ID\n" +
                "   mothersInfo : [PersonsType]\n" +
                "}\n" +
                "\n" +
                "type Date {\n" +
                "}\n" +
                "\n" +
                "type DateTime {\n" +
                "}\n" +
                "\n" +
                "type ObstetriciansType {\n" +
                "   id : ID\n" +
                "   obestrician : PersonsType\n" +
                "   personId : ID\n" +
                "   guidedPregnancies : [PregnanciesType]\n" +
                "}\n" +
                "\n" +
                "type PersonsType {\n" +
                "   sex : String!\n" +
                "   lastName : String!\n" +
                "   address : String!\n" +
                "   id : ID\n" +
                "   personalObstetricians : [ObstetriciansType]\n" +
                "   dateOfBirth : DateTime!\n" +
                "   natIdNr : String!\n" +
                "   firstName : String!\n" +
                "   personalPregnancies : [PregnanciesType]\n" +
                "}\n" +
                "\n" +
                "type PregnanciesType {\n" +
                "   dueDate : DateTime!\n" +
                "   obstetrician : ObstetriciansType\n" +
                "   id : ID\n" +
                "   personId : ID\n" +
                "   obstetricianId : Int\n" +
                "   mother : PersonsType\n" +
                "}";

        assertEquals(expected, bos.toString("UTF-8"));

    }
}
