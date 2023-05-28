package io.deffun.didier;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.deffun.didier.data.GqlSchemas;
import liquibase.serializer.core.xml.XMLChangeLogSerializer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LiquibaseDdlGenTest {
    @Test
    void oneToOneTest() {
        SchemaParser schemaParser = new SchemaParser();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        TypeDefinitionRegistry typeRegistry = schemaParser.parse(GqlSchemas.SIMPLE_SCHEMA);
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, RuntimeWiring.MOCKED_WIRING);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        LiquibaseDdlGen ddlGen = new LiquibaseDdlGen("init", "buddy", new XMLChangeLogSerializer());
        ddlGen.generate(graphQLSchema, baos);
        String result = baos.toString();
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // assert FKs using XPath or something
    }
}
