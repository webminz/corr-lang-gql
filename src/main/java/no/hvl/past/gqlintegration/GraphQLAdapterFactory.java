package no.hvl.past.gqlintegration;

import no.hvl.past.di.PropertyHolder;
import no.hvl.past.graph.Universe;
import no.hvl.past.MetaRegistry;
import io.corrlang.plugins.techspace.TechSpaceAdapterFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

public class GraphQLAdapterFactory implements TechSpaceAdapterFactory<GraphQLTechSpace> {

    @Autowired
    private Universe universe;

    @Autowired
    private MetaRegistry registry;

    @Autowired
    private PropertyHolder propertyHolder;

    @Override
    public void doSetUp() {

    }

    @Override
    public GraphQLAdapter createAdapter() {
        return new GraphQLAdapter(universe, propertyHolder);
    }

    @Override
    public void prepareShutdown() {

    }

    @PostConstruct
    public void initialize() {
        registry.register(GraphQLTechSpace.KEY, GraphQLTechSpace.INSTANCE);
        registry.register(GraphQLTechSpace.KEY, this);
    }
}
