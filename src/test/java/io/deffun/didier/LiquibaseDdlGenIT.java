package io.deffun.didier;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.deffun.didier.data.GqlSchemas;
import io.deffun.didier.util.ColumnMetaData;
import io.deffun.didier.util.DbMetaDataUtil;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.serializer.core.xml.XMLChangeLogSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class LiquibaseDdlGenIT {
    @Container
    private static final MySQLContainer<?> MY_SQL_CONTAINER = new MySQLContainer<>("mysql:8.0");

    @Test
    void generateDdlTest() throws IOException, SQLException, LiquibaseException {
        SchemaParser schemaParser = new SchemaParser();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        TypeDefinitionRegistry typeRegistry = schemaParser.parse(GqlSchemas.SIMPLE_SCHEMA);
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, RuntimeWiring.MOCKED_WIRING);
        File tempFile = File.createTempFile("changelog", ".xml");
        FileOutputStream fos = new FileOutputStream(tempFile);
        LiquibaseDdlGen ddlGen = new LiquibaseDdlGen("init", "buddy", new XMLChangeLogSerializer());
        ddlGen.generate(graphQLSchema, fos);

        // let's apply changes to the db
        Connection connection = MY_SQL_CONTAINER.createConnection("");
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        Liquibase liquibase = new Liquibase(
                tempFile.getName(),
                new DirectoryResourceAccessor(tempFile.getParentFile()),
                database
        );
        liquibase.update(new Contexts(), new LabelExpression());

        DatabaseMetaData databaseMetaData = connection.getMetaData();
        Map<String, ColumnMetaData> columns = DbMetaDataUtil.collectColumns(databaseMetaData, "product");
        ColumnMetaData nameMetaData = columns.get("name");
        assertEquals("VARCHAR", nameMetaData.typeName());
        assertFalse(nameMetaData.isNullable());
        ColumnMetaData descriptionMetaData = columns.get("description");
        assertEquals("VARCHAR", descriptionMetaData.typeName());
        assertTrue(descriptionMetaData.isNullable());
    }
}
