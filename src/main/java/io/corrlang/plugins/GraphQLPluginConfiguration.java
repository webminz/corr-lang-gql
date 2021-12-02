package io.corrlang.plugins;

import io.corrlang.gqlintegration.GraphQLAdapterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphQLPluginConfiguration {

    @Bean
    public GraphQLAdapterFactory gqlAdapterFactory() {
        return new GraphQLAdapterFactory();
    }

}
