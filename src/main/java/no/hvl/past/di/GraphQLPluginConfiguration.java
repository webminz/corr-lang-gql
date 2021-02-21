package no.hvl.past.di;

import no.hvl.past.gqlintegration.GraphQLAdapterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphQLPluginConfiguration {

    @Bean
    public GraphQLAdapterFactory gqlAdapterFactory() {
        return new GraphQLAdapterFactory();
    }

}
