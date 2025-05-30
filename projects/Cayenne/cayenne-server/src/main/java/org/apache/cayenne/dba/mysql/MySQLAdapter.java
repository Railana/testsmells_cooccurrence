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

package org.apache.cayenne.dba.mysql;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.apache.cayenne.access.DataNode;
import org.apache.cayenne.access.sqlbuilder.sqltree.SQLTreeProcessor;
import org.apache.cayenne.access.translator.ParameterBinding;
import org.apache.cayenne.access.translator.ejbql.EJBQLTranslatorFactory;
import org.apache.cayenne.access.translator.ejbql.JdbcEJBQLTranslatorFactory;
import org.apache.cayenne.access.types.ByteArrayType;
import org.apache.cayenne.access.types.CharType;
import org.apache.cayenne.access.types.DateType;
import org.apache.cayenne.access.types.ExtendedType;
import org.apache.cayenne.access.types.ExtendedTypeFactory;
import org.apache.cayenne.access.types.ExtendedTypeMap;
import org.apache.cayenne.access.types.JsonType;
import org.apache.cayenne.access.types.TimeType;
import org.apache.cayenne.access.types.TimestampType;
import org.apache.cayenne.access.types.UtilDateType;
import org.apache.cayenne.access.types.ValueObjectTypeRegistry;
import org.apache.cayenne.configuration.Constants;
import org.apache.cayenne.configuration.RuntimeProperties;
import org.apache.cayenne.dba.DefaultQuotingStrategy;
import org.apache.cayenne.dba.JdbcAdapter;
import org.apache.cayenne.dba.PkGenerator;
import org.apache.cayenne.dba.QuotingStrategy;
import org.apache.cayenne.dba.TypesMapping;
import org.apache.cayenne.di.Inject;
import org.apache.cayenne.map.DbAttribute;
import org.apache.cayenne.map.DbEntity;
import org.apache.cayenne.query.Query;
import org.apache.cayenne.query.SQLAction;
import org.apache.cayenne.resource.ResourceLocator;

/**
 * DbAdapter implementation for <a href="https://www.mysql.com">MySQL RDBMS</a>.
 * <h3>
 * Foreign Key Constraint Handling</h3>
 * <p>
 * Foreign key constraints are supported by InnoDB engine and NOT supported by
 * MyISAM engine. This adapter by default assumes MyISAM, so
 * <code>supportsFkConstraints</code> will be false. Users can manually change
 * this by calling <em>setSupportsFkConstraints(true)</em> or better by using an
 * {@link org.apache.cayenne.dba.AutoAdapter}, i.e. not entering the adapter
 * name at all for the DataNode, letting Cayenne guess it in runtime. In the
 * later case Cayenne will check the <em>table_type</em> MySQL variable to
 * detect whether InnoDB is the default, and configure the adapter accordingly.
 * <h3>Sample Connection Settings</h3>
 * <ul>
 * <li>Adapter name: org.apache.cayenne.dba.mysql.MySQLAdapter</li>
 * <li>DB URL: jdbc:mysql://serverhostname/dbname</li>
 * <li>Driver Class: com.mysql.jdbc.Driver</li>
 * </ul>
 */
public class MySQLAdapter extends JdbcAdapter {

	static final String DEFAULT_STORAGE_ENGINE = "InnoDB";
	static final List<String> SYSTEM_CATALOGS = Arrays.asList("sys", "information_schema", "mysql", "performance_schema");

	protected String storageEngine;

	public MySQLAdapter(@Inject RuntimeProperties runtimeProperties,
						@Inject(Constants.SERVER_DEFAULT_TYPES_LIST) List<ExtendedType> defaultExtendedTypes,
						@Inject(Constants.SERVER_USER_TYPES_LIST) List<ExtendedType> userExtendedTypes,
						@Inject(Constants.SERVER_TYPE_FACTORIES_LIST) List<ExtendedTypeFactory> extendedTypeFactories,
						@Inject(Constants.SERVER_RESOURCE_LOCATOR) ResourceLocator resourceLocator,
						@Inject ValueObjectTypeRegistry valueObjectTypeRegistry) {
		super(runtimeProperties, defaultExtendedTypes, userExtendedTypes, extendedTypeFactories, resourceLocator, valueObjectTypeRegistry);

		// init defaults
		this.storageEngine = DEFAULT_STORAGE_ENGINE;

		setSupportsBatchUpdates(true);
		setSupportsUniqueConstraints(true);
		setSupportsGeneratedKeys(true);
	}

	@Override
	protected QuotingStrategy createQuotingStrategy() {
		return new DefaultQuotingStrategy("`", "`");
	}

    /**
     * @since 4.2
     */
	@Override
	public SQLTreeProcessor getSqlTreeProcessor() {
		return MySQLTreeProcessor.getInstance(caseInsensitiveCollations);
	}

	/**
	 * Uses special action builder to create the right action.
	 *
	 * @since 1.2
	 */
	@Override
	public SQLAction getAction(Query query, DataNode node) {
		return query.createSQLAction(new MySQLActionBuilder(node));
	}

	/**
	 * @since 3.0
	 */
	@Override
	public Collection<String> dropTableStatements(DbEntity table) {
		// note that CASCADE is a noop as of MySQL 5.0, so we have to use FK
		// checks
		// statement
		StringBuilder buf = new StringBuilder();
		QuotingStrategy context = getQuotingStrategy();
		buf.append(context.quotedFullyQualifiedName(table));

		return Arrays.asList("SET FOREIGN_KEY_CHECKS=0", "DROP TABLE IF EXISTS " + buf.toString() + " CASCADE",
				"SET FOREIGN_KEY_CHECKS=1");
	}

	/**
	 * Installs appropriate ExtendedTypes used as converters for passing values
	 * between JDBC and Java layers.
	 */
	@Override
	protected void configureExtendedTypes(ExtendedTypeMap map) {
		super.configureExtendedTypes(map);

		// must handle CLOBs as strings, otherwise there
		// are problems with NULL clobs that are treated
		// as empty strings... somehow this doesn't happen
		// for BLOBs (ConnectorJ v. 3.0.9)
		CharType charType = new CharType(false, false);
		map.registerType(charType);
		map.registerType(new ByteArrayType(false, false));
		map.registerType(new JsonType(charType, true));

		// register non-default types for the dates, see CAY-2691
		map.registerType(new DateType(true));
		map.registerType(new TimeType(true));
		map.registerType(new TimestampType(true));
		map.registerType(new UtilDateType(true));
	}

	@Override
	public DbAttribute buildAttribute(String name, String typeName, int type, int size, int precision,
			boolean allowNulls) {

		if (typeName != null) {
			typeName = typeName.toLowerCase();
		}

		// all LOB types are returned by the driver as OTHER... must remap them
		// manually
		// (at least on MySQL 3.23)
		if (type == Types.OTHER) {
			if ("longblob".equals(typeName)) {
				type = Types.BLOB;
			} else if ("mediumblob".equals(typeName)) {
				type = Types.BLOB;
			} else if ("blob".equals(typeName)) {
				type = Types.BLOB;
			} else if ("tinyblob".equals(typeName)) {
				type = Types.VARBINARY;
			} else if ("longtext".equals(typeName)) {
				type = Types.CLOB;
			} else if ("mediumtext".equals(typeName)) {
				type = Types.CLOB;
			} else if ("text".equals(typeName)) {
				type = Types.CLOB;
			} else if ("tinytext".equals(typeName)) {
				type = Types.VARCHAR;
			}
		}
		// types like "int unsigned" map to Long
		else if (typeName != null && typeName.endsWith(" unsigned")) {
			// per
			// https://dev.mysql.com/doc/refman/5.0/en/connector-j-reference-type-conversions.html
			if ("int unsigned".equals(typeName) || "integer unsigned".equals(typeName)
					|| "mediumint unsigned".equals(typeName)) {
				type = Types.BIGINT;
			}
			// BIGINT UNSIGNED maps to BigInteger according to MySQL docs, but
			// there is no
			// JDBC mapping for BigInteger
		}

		// This is a special case for the Json type and older MySQL drivers (5.x)
		if(type == Types.CHAR && "json".equals(typeName)) {
			type = Types.LONGVARCHAR;
		}

		// driver reports column size that we should "translate" to the column precision
		// see CAY-2694 for details
		if(type == Types.TIME) {
			precision = Math.max(0, size - 9);
			size = -1;
		} else if(type == Types.TIMESTAMP) {
			precision = Math.max(0, size - 20);
			size = -1;
		}

		return super.buildAttribute(name, typeName, type, size, precision, allowNulls);
	}

	@Override
	public void bindParameter(PreparedStatement statement, ParameterBinding binding)
			throws SQLException, Exception {
		binding.setJdbcType(mapNTypes(binding.getJdbcType()));
		super.bindParameter(statement, binding);
	}

	private int mapNTypes(int sqlType) {
		switch (sqlType) {
		case Types.NCHAR:
			return Types.CHAR;
		case Types.NCLOB:
			return Types.CLOB;
		case Types.NVARCHAR:
			return Types.VARCHAR;
		case Types.LONGNVARCHAR:
			return Types.LONGVARCHAR;

		default:
			return sqlType;
		}
	}

	/**
	 * Creates and returns a primary key generator. Overrides superclass
	 * implementation to return an instance of MySQLPkGenerator that does the
	 * correct table locking.
	 */
	@Override
	protected PkGenerator createPkGenerator() {
		return new MySQLPkGenerator(this);
	}

	/**
	 * @since 3.0
	 */
	@Override
	protected EJBQLTranslatorFactory createEJBQLTranslatorFactory() {
		JdbcEJBQLTranslatorFactory translatorFactory = new MySQLEJBQLTranslatorFactory();
		translatorFactory.setCaseInsensitive(caseInsensitiveCollations);
		return translatorFactory;
	}

	/**
	 * Overrides super implementation to explicitly set table engine to InnoDB
	 * if FK constraints are supported by this adapter.
	 */
	@Override
	public String createTable(DbEntity entity) {
		String ddlSQL = super.createTable(entity);

		if (storageEngine != null) {
			ddlSQL += " ENGINE=" + storageEngine;
		}

		return ddlSQL;
	}

	/**
	 * Customizes PK clause semantics to ensure that generated columns are in
	 * the beginning of the PK definition, as this seems to be a requirement for
	 * InnoDB tables.
	 *
	 * @since 1.2
	 */
	// See CAY-358 for details of the InnoDB problem
	@Override
	protected void createTableAppendPKClause(StringBuffer sqlBuffer, DbEntity entity) {

		// must move generated to the front...
		List<DbAttribute> pkList = new ArrayList<>(entity.getPrimaryKeys());
		pkList.sort(PKComparator.INSTANCE);

		Iterator<DbAttribute> pkit = pkList.iterator();
		if (pkit.hasNext()) {

            sqlBuffer.append(", PRIMARY KEY (");
            boolean firstPk = true;
            while (pkit.hasNext()) {
                if (firstPk) {
					firstPk = false;
				} else {
					sqlBuffer.append(", ");
				}

                DbAttribute at = pkit.next();
                sqlBuffer.append(quotingStrategy.quotedName(at));
            }
            sqlBuffer.append(')');
        }
	}

	/**
	 * Appends AUTO_INCREMENT clause to the column definition for generated
	 * columns.
	 */
	@Override
	public void createTableAppendColumn(StringBuffer sqlBuffer, DbAttribute column) {

		String type = getType(this, column);

		sqlBuffer.append(quotingStrategy.quotedName(column));
		sqlBuffer.append(' ').append(type);

		// append size and precision (if applicable)s
		appendLengthAndScale(sqlBuffer, column);

		sqlBuffer.append(column.isMandatory() ? " NOT NULL" : " NULL");

		if (column.isGenerated()) {
			sqlBuffer.append(" AUTO_INCREMENT");
		}
	}

	private void appendLengthAndScale(StringBuffer sqlBuffer, DbAttribute column) {
		if(column.getType() == Types.TIME || column.getType() == Types.TIMESTAMP) {
			int scale = column.getScale();
			if(scale >= 0) {
				sqlBuffer.append('(').append(scale).append(')');
			}
		} else if (typeSupportsLength(column.getType())) {
			int len = column.getMaxLength();

			int scale = TypesMapping.isDecimal(column.getType()) ? column.getScale() : -1;

			// sanity check
			if (scale > len) {
				scale = -1;
			}

			if (len > 0) {
				sqlBuffer.append('(').append(len);

				if (scale >= 0) {
					sqlBuffer.append(", ").append(scale);
				}

				sqlBuffer.append(')');
			}
		}
	}


	@Override
	public boolean typeSupportsLength(int type) {
		// As of MySQL 5.6.4 the "TIMESTAMP" and "TIME" types support length,
		// which is the number of decimal places for fractional seconds
		// https://dev.mysql.com/doc/refman/5.6/en/fractional-seconds.html
		switch (type) {
		case Types.TIMESTAMP:
		case Types.TIME:
			return true;
		default:
			return super.typeSupportsLength(type);
		}
	}

	@Override
	public List<String> getSystemCatalogs() {
		return SYSTEM_CATALOGS;
	}

	/**
	 * @since 3.0
	 */
	public String getStorageEngine() {
		return storageEngine;
	}

	/**
	 * @since 3.0
	 */
	public void setStorageEngine(String engine) {
		this.storageEngine = engine;
	}

	static final class PKComparator implements Comparator<DbAttribute> {

		static final PKComparator INSTANCE = new PKComparator();

		public int compare(DbAttribute a1, DbAttribute a2) {
			if (a1.isGenerated() != a2.isGenerated()) {
				return a1.isGenerated() ? -1 : 1;
			} else {
				return a1.getName().compareTo(a2.getName());
			}
		}
	}
}
