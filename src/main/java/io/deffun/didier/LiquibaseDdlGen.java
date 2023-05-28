package io.deffun.didier;

import com.google.common.base.CaseFormat;
import graphql.Scalars;
import graphql.schema.*;
import liquibase.change.ColumnConfig;
import liquibase.change.ConstraintsConfig;
import liquibase.change.core.CreateTableChange;
import liquibase.changelog.ChangeSet;
import liquibase.datatype.LiquibaseDataType;
import liquibase.datatype.core.*;
import liquibase.serializer.ChangeLogSerializer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class LiquibaseDdlGen implements DdlGen {
    private static final Set<String> SKIPPABLE_OBJECT_TYPES = Set.of(
            "__Directive",
            "__EnumValue",
            "__Field",
            "__InputValue",
            "__Schema",
            "__Type"
    );
    private static final Map<String, LiquibaseDataType> SCALARS_TYPE_MAP = Map.of(
            Scalars.GraphQLBoolean.getName(), new BooleanType(),
            Scalars.GraphQLInt.getName(), new IntType(),
            Scalars.GraphQLFloat.getName(), new FloatType(),
            Scalars.GraphQLString.getName(), new VarcharType()
    );

    private static final Function<GraphQLObjectType, String> TABLE_NAME_TRANSFORMER = objectType -> CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, objectType.getName());
    private static final Function<GraphQLFieldDefinition, String> COLUMN_NAME_TRANSFORMER = fieldDefinition -> CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, fieldDefinition.getName());

    private final String id;
    private final String author;
    private final ChangeLogSerializer changeLogSerializer;

    public LiquibaseDdlGen(String id, String author, ChangeLogSerializer changeLogSerializer) {
        this.id = id;
        this.author = author;
        this.changeLogSerializer = changeLogSerializer;
    }

    @Override
    public void generate(GraphQLSchema graphQLSchema, OutputStream outputStream) {
        ChangeSet changeSet = new ChangeSet(id, author, false, false, null, null, null, null);
        for (GraphQLNamedType namedType : graphQLSchema.getAllTypesAsList()) {
            if (namedType instanceof GraphQLObjectType objectType) {
                if (isSkippableType(graphQLSchema, objectType)) {
                    continue;
                }
                String tableName = TABLE_NAME_TRANSFORMER.apply(objectType);
                CreateTableChange createTableChange = new CreateTableChange();
                createTableChange.setTableName(tableName);
                for (GraphQLFieldDefinition fieldDefinition : objectType.getFieldDefinitions()) {
                    GraphQLType unwrapped = GraphQLTypeUtil.unwrapNonNull(fieldDefinition.getType());
                    if (unwrapped instanceof GraphQLScalarType scalarType) {
                        if (isPrimaryKey(scalarType)) {
                            createTableChange.addColumn(primaryKeyColumnConfig(fieldDefinition, tableName));
                        } else {
                            createTableChange.addColumn(scalarColumnConfig(fieldDefinition));
                        }
                    } else if (unwrapped instanceof GraphQLObjectType relatedType) {
                        String relatedTableName = TABLE_NAME_TRANSFORMER.apply(relatedType);
                        ColumnConfig columnConfig = new ColumnConfig()
                                .setName("%s_id".formatted(relatedTableName))
                                .setType(idDataType().getName())
                                .setConstraints(new ConstraintsConfig()
                                        .setForeignKeyName("fk_%s_%s".formatted(relatedTableName, tableName)));
                        createTableChange.addColumn(columnConfig);
                    } else if (unwrapped instanceof GraphQLList) {
                        // list of scalars/enums/objects
                        throw new UnsupportedOperationException("Lists are not yet supported.");
                    } else if (unwrapped instanceof GraphQLEnumType) {
                        createTableChange.addColumn(columnConfig(fieldDefinition, enumDataType()));
                    }
                }
                changeSet.addChange(createTableChange);
            }
        }
        try {
            changeLogSerializer.write(List.of(changeSet), outputStream);
        } catch (IOException e) {
            throw new IORuntimeException(e);
        }
    }

    private static boolean isSkippableType(GraphQLSchema graphQLSchema, GraphQLObjectType objectType) {
        return SKIPPABLE_OBJECT_TYPES.contains(objectType.getName())
                || objectType.equals(graphQLSchema.getQueryType())
                || objectType.equals(graphQLSchema.getMutationType())
                || objectType.equals(graphQLSchema.getSubscriptionType());
    }

    private static boolean isPrimaryKey(GraphQLScalarType scalarType) {
        return "ID".equals(scalarType.getName());
    }

    private static ColumnConfig primaryKeyColumnConfig(GraphQLFieldDefinition fieldDefinition, String tableName) {
        return new ColumnConfig()
                .setName(COLUMN_NAME_TRANSFORMER.apply(fieldDefinition))
                .setType(idDataType().getName())
                .setAutoIncrement(true)
                .setConstraints(new ConstraintsConfig()
                        .setPrimaryKey(true)
                        .setPrimaryKeyName("pk_%s".formatted(tableName))
                        .setNullable(false));
    }

    private static ColumnConfig scalarColumnConfig(GraphQLFieldDefinition fieldDefinition) {
        GraphQLScalarType scalarType = GraphQLTypeUtil.unwrapNonNullAs(fieldDefinition.getType());
        LiquibaseDataType scalarDataType = SCALARS_TYPE_MAP.get(scalarType.getName());
        if (scalarDataType == null) {
            throw new IllegalArgumentException("GraphQL scalar '%s' is not supported.".formatted(scalarType.getName()));
        }
        return columnConfig(fieldDefinition, scalarDataType);
    }

    // Create column config using field definition and liquibase datatype
    private static ColumnConfig columnConfig(GraphQLFieldDefinition fieldDefinition, LiquibaseDataType dataType) {
        ColumnConfig columnConfig = new ColumnConfig()
                .setName(COLUMN_NAME_TRANSFORMER.apply(fieldDefinition))
                .setType(dataType instanceof VarcharType ? "%s(255)".formatted(dataType.getName()) : dataType.getName());
        if (GraphQLTypeUtil.isNonNull(fieldDefinition.getType())) {
            columnConfig.setConstraints(new ConstraintsConfig()
                    .setNullable(false));
        }
        return columnConfig;
    }

    private static LiquibaseDataType idDataType() {
        return new BigIntType();
    }

    private static LiquibaseDataType enumDataType() {
        return new VarcharType();
    }
}
