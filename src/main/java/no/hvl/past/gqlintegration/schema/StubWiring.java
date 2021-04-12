package no.hvl.past.gqlintegration.schema;

import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.schema.*;
import graphql.schema.idl.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class StubWiring {

    private static final List<String> BUILTIN_SCALARS = Arrays.asList("ID", "String", "Int", "Float", "Boolean");


    public static RuntimeWiring createWiring(TypeDefinitionRegistry typeDefinitionRegistry) {
        RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();

        for (ScalarTypeDefinition scalarType : typeDefinitionRegistry.scalars().values()) {
            if (!BUILTIN_SCALARS.contains(scalarType.getName())) {
                builder.scalar(GraphQLScalarType.newScalar().name(scalarType.getName()).coercing(new Coercing() {
                    @Override
                    public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                        return null;
                    }

                    @Override
                    public Object parseValue(Object input) throws CoercingParseValueException {
                        return null;
                    }

                    @Override
                    public Object parseLiteral(Object input) throws CoercingParseLiteralException {
                        return null;
                    }
                }).build());
            }
        }

        return builder.build();
    }


}
