package io.corrlang.gqlintegration;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(
        Suite.class
)
@Suite.SuiteClasses({
        QueryParserTest.class,
        QuerySplittingTest.class,
        QueryTest.class,
        SchemaReaderTest.class,
        SchemaWriterTest.class
})
public class GraphQLTestsuite {
}
