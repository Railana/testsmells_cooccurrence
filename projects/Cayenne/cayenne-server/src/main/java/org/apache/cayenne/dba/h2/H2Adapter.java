/*****************************************************************
 *   Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 ****************************************************************/

package org.apache.cayenne.dba.h2;

import org.apache.cayenne.access.DataNode;
import org.apache.cayenne.access.sqlbuilder.sqltree.SQLTreeProcessor;
import org.apache.cayenne.access.types.ExtendedType;
import org.apache.cayenne.access.types.ExtendedTypeFactory;
import org.apache.cayenne.access.types.ExtendedTypeMap;
import org.apache.cayenne.access.types.ValueObjectTypeRegistry;
import org.apache.cayenne.configuration.Constants;
import org.apache.cayenne.configuration.RuntimeProperties;
import org.apache.cayenne.dba.JdbcAdapter;
import org.apache.cayenne.dba.PkGenerator;
import org.apache.cayenne.di.Inject;
import org.apache.cayenne.map.DbAttribute;
import org.apache.cayenne.query.Query;
import org.apache.cayenne.query.SQLAction;
import org.apache.cayenne.resource.ResourceLocator;

import java.util.List;

/**
 * DbAdapter implementation for <a href="https://www.h2database.com/">H2
 * RDBMS </a>. Sample connection settings to use with H2 are shown
 * below:
 * 
 * <pre>
 *      postgres.jdbc.username = sa
 *      postgres.jdbc.password = 
 *      postgres.jdbc.url = jdbc:h2:cayenne
 *      postgres.jdbc.driver = org.h2.Driver
 * </pre>
 * 
 * @since 3.0
 */
public class H2Adapter extends JdbcAdapter {
    public H2Adapter(@Inject RuntimeProperties runtimeProperties,
            @Inject(Constants.SERVER_DEFAULT_TYPES_LIST) List<ExtendedType> defaultExtendedTypes,
            @Inject(Constants.SERVER_USER_TYPES_LIST) List<ExtendedType> userExtendedTypes,
            @Inject(Constants.SERVER_TYPE_FACTORIES_LIST) List<ExtendedTypeFactory> extendedTypeFactories,
            @Inject(Constants.SERVER_RESOURCE_LOCATOR) ResourceLocator resourceLocator,
            @Inject ValueObjectTypeRegistry valueObjectTypeRegistry) {
        super(runtimeProperties, defaultExtendedTypes, userExtendedTypes, extendedTypeFactories, resourceLocator, valueObjectTypeRegistry);
        setSupportsGeneratedKeys(true);
    }

    @Override
    public void createTableAppendColumn(StringBuffer sqlBuffer, DbAttribute column) {
        super.createTableAppendColumn(sqlBuffer, column);

        if (column.isGenerated()) {
            sqlBuffer.append(" AUTO_INCREMENT");
        }
    }

    /**
     * @since 4.2
     */
    @Override
    public SQLTreeProcessor getSqlTreeProcessor() {
        return new H2SQLTreeProcessor();
    }

    @Override
    public SQLAction getAction(Query query, DataNode node) {
        return query.createSQLAction(new H2ActionBuilder(node));
    }

    /**
     * Installs appropriate ExtendedTypes as converters for passing values
     * between JDBC and Java layers.
     * @since 4.1.2
     */
    @Override
    protected void configureExtendedTypes(ExtendedTypeMap map) {
        super.configureExtendedTypes(map);

        // create specially configured CharType handler
        map.registerType(new H2CharType());
    }

    @Override
    protected PkGenerator createPkGenerator() {
        return new H2PkGenerator(this);
    }
}
