/**
 * ShinyProxy-Visualizer
 * 
 * Copyright 2021 MetaMorph
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *       
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.metamorphsoftware.shinyproxy.services;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.metadata.CommonsDbcp2DataSourcePoolMetadata;
import org.springframework.stereotype.Service;

import com.google.common.reflect.TypeToken;
import com.metamorphsoftware.shinyproxy.configurationproperties.SQLConfigurationProperties;
import com.metamorphsoftware.shinyproxy.services.SQLService.Record.DBWhere.DBWhereListBuilder;
import com.metamorphsoftware.shinyproxy.services.SQLService.Record.DBWhereClause;
import com.metamorphsoftware.shinyproxy.services.SQLService.Record.DBWhereClause.DBWhereClauseBuilder;

/**
 * @author Fraser LeFevre
 *
 */
@Service
public class SQLService {
	private static BasicDataSource pool;
	private static SQLConfigurationProperties sqlProperties;
	private static boolean initialized = false;
	
	/**
	 * @param sqlProp
	 */
	@Autowired
	public static void initilizePool(SQLConfigurationProperties sqlProp) {
		if (!SQLService.initialized) {
			SQLService.sqlProperties = sqlProp;
			
			SQLService.pool = new BasicDataSource();
			SQLService.pool.setUrl(SQLService.sqlProperties.getUrl());
			SQLService.pool.setUsername(SQLService.sqlProperties.getUsername());
			SQLService.pool.setPassword(SQLService.sqlProperties.getPassword());
			SQLService.pool.setMaxTotal(SQLService.sqlProperties.getMaxConnections());
			SQLService.pool.setMaxConnLifetimeMillis(SQLService.sqlProperties.getMaxConnectionLifetimeMilliseconds());
			SQLService.pool.setMaxIdle(SQLService.sqlProperties.getMaxIdleConnections());
			SQLService.pool.setMinIdle(SQLService.sqlProperties.getMinIdleConnections());
			
			SQLService.initialized = true;
		}
	}
	
	/**
	 * @return
	 */
	public CommonsDbcp2DataSourcePoolMetadata getStats() {
		return new CommonsDbcp2DataSourcePoolMetadata(SQLService.pool);
	}
	
	/**
	 * @return
	 * @throws SQLException
	 */
	public static Connection getConnection() throws SQLException {
		return SQLService.pool.getConnection();
	}
	
	/**
	 * @author Fraser LeFevre
	 *
	 */
	public static class OperationNotPermitted extends RuntimeException {
		private static final long serialVersionUID = 5729522636155125306L;

		/**
		 * 
		 */
		public OperationNotPermitted() {
			super();
		}

		/**
		 * @param message
		 * @param cause
		 * @param enableSuppression
		 * @param writableStackTrace
		 */
		public OperationNotPermitted(String message, Throwable cause, boolean enableSuppression,
				boolean writableStackTrace) {
			super(message, cause, enableSuppression, writableStackTrace);
		}

		/**
		 * @param message
		 * @param cause
		 */
		public OperationNotPermitted(String message, Throwable cause) {
			super(message, cause);
		}

		/**
		 * @param message
		 */
		public OperationNotPermitted(String message) {
			super(message);
		}

		/**
		 * @param cause
		 */
		public OperationNotPermitted(Throwable cause) {
			super(cause);
		}
		
	}
	
	/**
	 * @author Fraser LeFevre
	 *
	 */
	public static abstract class Record {
		
		public static class RecordNotFoundException extends Exception {

			/**
			 * 
			 */
			private static final long serialVersionUID = 2796406512668672230L;

			public RecordNotFoundException() {
				super();
			}

			public RecordNotFoundException(String message, Throwable cause, boolean enableSuppression,
					boolean writableStackTrace) {
				super(message, cause, enableSuppression, writableStackTrace);
			}

			public RecordNotFoundException(String message, Throwable cause) {
				super(message, cause);
			}

			public RecordNotFoundException(String message) {
				super(message);
			}

			public RecordNotFoundException(Throwable cause) {
				super(cause);
			}
			
		}
		
		/**
		 * @author Fraser LeFevre
		 *
		 */
		public static class DBFieldSettings {
			public int type = Types.OTHER;
			public Class<?> objectCastType = null;
			public boolean required = false;
			public boolean insert = false;
			public boolean update = false;
			
			public DBFieldSettings() {}
			private DBFieldSettings(DBFieldSettings dbfieldsettings) {
				this.type = dbfieldsettings.type;
				this.objectCastType = dbfieldsettings.objectCastType;
				this.required = dbfieldsettings.required;
				this.insert = dbfieldsettings.insert;
				this.update = dbfieldsettings.update;
			}
			
			public DBFieldSettings copy() {
				return new DBFieldSettings(this);
			}
		}
		
		/**
		 * @author Fraser LeFevre
		 *
		 */
		public static class DBField {
			public String name;
			public Object value = null;
			public Object defaultValue = null;
			public DBFieldSettings settings;
			
			public DBField() {}
			private DBField(DBField dbfield) {
				this.name = dbfield.name;
				this.value = dbfield.value;
				this.defaultValue = dbfield.defaultValue;
				this.settings = dbfield.settings.copy();
			}
			
			public DBField copy() {
				return new DBField(this);
			}
		}
		
		public enum DBWhereComparator {
			LT("<"), LTE("<="), GT(">"), GTE(">="), EQ("="), NEQ("<>"), IS("IS"), IS_NOT("IS NOT"), IN("IN");
			
			private String sqlRepresentation = null;
			DBWhereComparator(String sqlRepresentation) {
				this.sqlRepresentation = sqlRepresentation;
			}
			
			public String getSQLRepresentation() {
				return this.sqlRepresentation;
			}
		}
		
		public enum DBWhereLinker {
			OR, AND;
		}
		
		public static class DBWhere {
			public DBWhereComparator comparator = DBWhereComparator.EQ;
			public DBField field = null;
			public List<DBField> fields = null; // only used with IN Comparator
			
			public static class DBWhereBuilder {
				private DBWhereListBuilder parent;
				private Record record;
				
				private DBWhereComparator comparator = DBWhereComparator.EQ;
				private String columnName;
				private Object value;
				private List<Object> values;
//				private DBField field = null;
//				private List<DBField> fields = null; // only used with IN Comparator
				public static DBWhereBuilder Builder() {
					return new DBWhereBuilder();
				}
				
				public DBWhereBuilder withRecord(Record record) {
					this.record = record;
					return this;
				}

				public DBWhereBuilder withParentWhereList(DBWhereListBuilder dbWhereListBuilder) {
					this.parent = dbWhereListBuilder;
					return this;
				}
				
				public DBWhereBuilder withColumn(String columnName) {
					this.columnName = columnName;
					return this;
				}
				
				public DBWhereBuilder withComparator(DBWhereComparator comparator) {
					this.comparator = comparator;
					return this;
				}
				
				public DBWhereBuilder withValue(Object value) {
					this.value = value;
					return this;
				}
				
				public DBWhereBuilder withValues(List<Object> values) {
					this.values = values;
					return this.withComparator(DBWhereComparator.IN);
				}
				
				public DBWhereListBuilder addToWhereList() {
					return this.parent.addWhere(this.build());
				}
				
				public DBWhere build() {
					DBWhereComparator comp = this.comparator;
					DBField fld = record.fields.get(this.columnName).copy();
					List<DBField> flds = new ArrayList<DBField>();
					
					fld.value = value;
					if (this.values != null) {
						for (Object val: this.values) {
							DBField field = fld.copy();
							field.value = val;
							flds.add(field);
						}
					}
					
					return new DBWhere() {{
						this.comparator = comp;
						this.field = (flds.isEmpty() ? fld : null);
						this.fields = (flds.isEmpty() ? null : Collections.unmodifiableList(flds));
					}};
				}
			}
			
			public static class DBWhereListBuilder {
				private DBWhereClauseBuilder parent;
				private Record record;
				
				private List<DBWhere> wheres;
				private List<DBWhereLinker> linkers;
				
				public static DBWhereListBuilder Builder() {
					return new DBWhereListBuilder();
				}
				
				public DBWhereListBuilder withRecord(Record record) {
					this.record = record;
					return this;
				}

				public DBWhereListBuilder withParentClause(DBWhereClauseBuilder dbWhereClauseBuilder) {
					this.parent = dbWhereClauseBuilder;
					return this;
				}
				
				public DBWhereListBuilder withNewList() {
					this.wheres = new ArrayList<DBWhere>();
					this.linkers = new ArrayList<DBWhereLinker>();
					return this;
				}
				
				public DBWhereBuilder addWhere() {
					return DBWhereBuilder.Builder().withParentWhereList(this).withRecord(this.record);
				}
				
				public DBWhereListBuilder addWhere(DBWhere where) {
					this.wheres.add(where);
					return this;
				}
				
				public DBWhereListBuilder linker(DBWhereLinker linker) {
					this.linkers.add(linker);
					return this;
				}
				
				public DBWhereClauseBuilder addListToClause() {
					this.parent.addWhereList(this.buildWhereList(), this.buildLinkerList());
					return this.parent;
				}

				public List<DBWhere> buildWhereList() {
					return Collections.unmodifiableList(this.wheres);
				}
				
				public List<DBWhereLinker> buildLinkerList() {
					return Collections.unmodifiableList(this.linkers);
				}
			}
		}
		
		public static class DBWhereClause {
			public List<DBWhereLinker> linkers = null;
			public List<DBWhere> wheres = null;
			public List<DBWhereClause> clauses = null;
			
			public boolean isEmpty() {
				boolean isEmpty = true;
				
				if (this.wheres != null && !this.wheres.isEmpty()) {
					for (DBWhere where: this.wheres) {
						if (where.field != null || (where.fields != null && !where.fields.isEmpty())) {
							isEmpty = false;
						}
					}
				}
				else if (this.clauses != null && !this.clauses.isEmpty()) {
					for (DBWhereClause clause: this.clauses) {
						if (!clause.isEmpty()) {
							isEmpty = false;
						}
					}
				}
				
				return isEmpty;
			}
			
			@Override
			public String toString() {
				String format = "(";
				
				if (this.wheres != null && !this.wheres.isEmpty()) {
					int i = 0;
					for (DBWhere where: this.wheres) {
						if (where.field != null) {
							format = format + String.format("%s %s %s", where.field.name, where.comparator, where.field.value);
						} else if (where.fields != null) {
							format = format + String.format("%s %s (%s)", where.field.name, where.comparator, Arrays.toString(where.fields.toArray()));
						}
						
						if (this.linkers != null && i < this.linkers.size()) {
							format = format + String.format(" %s ", this.linkers.get(i));
						}
						++i;
					}
				} else if (this.clauses != null && !this.clauses.isEmpty()) {
					int i = 0;
					for (DBWhereClause clause: this.clauses) {
						format = format + clause.toString();
						
						if (this.linkers != null && i < this.linkers.size()) {
							format = format + String.format(" %s ", this.linkers.get(i));
						}
						++i;
					}
				}
				
				format = format + ")";
				return format;
			}
			
			public static class DBWhereClauseBuilder {
				private DBWhereClauseListBuilder parent;
				private Record record;
				
				private List<DBWhereLinker> linkers = null;
				private List<DBWhere> wheres = null;
				private List<DBWhereClause> clauses = null;
				
				public static DBWhereClauseBuilder Builder() {
					return new DBWhereClauseBuilder();
				}
				
				public DBWhereClauseBuilder withRecord(Record record) {
					this.record = record;
					return this;
				}
				
				public DBWhereListBuilder  withWhereList() {
					return DBWhereListBuilder.Builder().withParentClause(this).withRecord(this.record).withNewList();
				}
				
				public DBWhereClauseListBuilder withClauseList() {
					return DBWhereClauseListBuilder.Builder().withParentClause(this).withRecord(this.record).withNewList();
				}

				public DBWhereClauseBuilder withParentClauseList(DBWhereClauseListBuilder dbWhereClauseListBuilder) {
					this.parent = dbWhereClauseListBuilder;
					return this;
				}
				
				public DBWhereClauseBuilder addWhereList(List<DBWhere> wheres, List<DBWhereLinker> linkers) {
					this.wheres = wheres;
					this.linkers = linkers;
					return this;
				}

				public DBWhereClauseBuilder addClauseList(List<DBWhereClause> clauses, List<DBWhereLinker> linkers) {
					this.clauses = clauses;
					this.linkers = linkers;
					return this;
				}
				
				public DBWhereClauseListBuilder addClauseToList() {
					return this.parent.addClause(this.build());
				}
				
				public DBWhereClause build() {
					List<DBWhereLinker> links = this.linkers;
					List<DBWhere> whrs = this.wheres;
					List<DBWhereClause> clses = this.clauses;
					
					return new DBWhereClause() {{
						this.linkers = links;
						this.wheres = whrs;
						this.clauses = clses;
					}};
				}
			}
			
			public static class DBWhereClauseListBuilder {
				private DBWhereClauseBuilder parent;
				private Record record;
				
				private List<DBWhereClause> clauses;
				private List<DBWhereLinker> linkers;
				
				public static DBWhereClauseListBuilder Builder() {
					return new DBWhereClauseListBuilder();
				}
				
				public DBWhereClauseListBuilder withRecord(Record record) {
					this.record = record;
					return this;
				}

				public DBWhereClauseListBuilder withParentClause(DBWhereClauseBuilder dbWhereClauseBuilder) {
					this.parent = dbWhereClauseBuilder;
					return this;
				}
				
				public DBWhereClauseListBuilder withNewList() {
					this.clauses = new ArrayList<DBWhereClause>();
					this.linkers = new ArrayList<DBWhereLinker>();
					return this;
				}
				
				public DBWhereClauseBuilder addClause() {
					return DBWhereClauseBuilder.Builder().withParentClauseList(this).withRecord(this.record);
				}
				
				public DBWhereClauseListBuilder addClause(DBWhereClause clause) {
					this.clauses.add(clause);
					return this;
				}
				
				public DBWhereClauseListBuilder linker(DBWhereLinker linker) {
					this.linkers.add(linker);
					return this;
				}
				
				public DBWhereClauseBuilder addListToClause() {
					return parent.addClauseList(this.buildClauseList(), this.buildLinkerList());
				}

				public List<DBWhereClause> buildClauseList() {
					return Collections.unmodifiableList(this.clauses);
				}
				public List<DBWhereLinker> buildLinkerList() {
					return Collections.unmodifiableList(this.linkers);
				}
			}
		}
		
//		public static DBWhere createDBWhere(Record record, String fieldName, DBWhereComparator comparator, Object value) {
//			DBWhereComparator dbWhereComparator = comparator;
//			return new DBWhere() {{
//				this.comparator = dbWhereComparator;
//				this.field = record.fields.get(fieldName).copy();
//				this.field.value = value;
//			}};
//		}
//		
//		public static DBWhere createDBWhere(Record record, String fieldName, List<Object> values) {
//			return new DBWhere() {{
//				this.comparator = DBWhereComparator.IN;
//				this.fields = new ArrayList<DBField>();
//				
//				for (Object value: values) {
//					DBField field = record.fields.get(fieldName).copy();
//					field.value = value;
//					this.fields.add(field);
//				}
//			}};
//		}
//		
//		public static DBWhereClause createDBWhereClause(List<DBWhereLinker> linkers, List<DBWhere> wheres) {
//			if ((wheres == null) || 
//					(linkers == null && wheres.size() != 1) ||
//					(linkers.size() != (wheres.size() - 1))) {
//				throw new IllegalArgumentException("Size of linkers must be excatly one less than sizes of wheres");
//			}
//			
//			List<DBWhereLinker> dbWhereLinkers = linkers;
//			List<DBWhere> dbWheres = wheres;
//			return new DBWhereClause() {{
//				this.linkers = dbWhereLinkers;
//				this.wheres = dbWheres;
//			}};
//		}
//		
//		public static DBWhereClause createDBWhereClauseFromClauses(List<DBWhereLinker> linkers, List<DBWhereClause> clauses) {
//			if ((clauses == null) || 
//					(linkers == null && clauses.size() != 1) ||
//					(linkers.size() != (clauses.size() - 1))) {
//				throw new IllegalArgumentException("Size of linkers must be excatly one less than sizes of wheres");
//			}
//			
//			List<DBWhereLinker> dbWhereLinkers = linkers;
//			List<DBWhereClause> dbClauses = clauses;
//			return new DBWhereClause() {{
//				this.linkers = dbWhereLinkers;
//				this.clauses = dbClauses;
//			}};
//		}
		
		private String tablename;
		public String tablename() {
			return this.tablename;
		}
		
//		protected Map<String, List<String>> keys = new TreeMap<String, List<String>>();
//		
//		protected  List<String> allKeys() {
//			return this.keys.values().stream().flatMap(list -> list.stream()).collect(Collectors.toList());
//		}
//		
//		protected void setKeyValuesToNull() {
//			for (String key: this.allKeys()) {
//				this.fields.get(key).value = null;
//			}
//		}
//		
//		protected void addKey(String id, List<String> key) {
//			this.keys.put(id, key);
//		}
//		
//		protected List<String> getKey(String id) {
//			return this.keys.get(id);
//		}

		protected Map<String, DBField> fields = new TreeMap<String, DBField>();
		
		public abstract DBWhereClause getKey();

		protected void addFields(DBField... fields) {
			for (DBField field: fields) {
				this.fields.put(field.name, field);
			}
		}
		
//		protected Record next = null;
//		@SuppressWarnings("unchecked")
//		public <T extends Record> List<T> getRecordList() {
//			List<T> records = new ArrayList<T>();
//			records.add((T) this);
//			
//			Record current = next;
//			while (current != null) {
//				records.add((T) current);
//				current = current.next;
//			}
//			
//			return Collections.unmodifiableList(records);
//		}
		
		protected Record(String tablename) {
			this.tablename = tablename;
		}
		
//		/**
//		 * 
//		 */
//		public Record() {
//
//		}
//		
//		protected Record(ResultSet recordResult) throws SQLException {
//			this.fromResultSet(recordResult);
//			
//			Constructor<? extends Record> constructor;
//			try {
////				constructor = this.getClass().getDeclaredConstructor(ResultSet.class);
//				constructor = this.getClass().getConstructor();
//				Record curr = constructor.newInstance();
//				do {
//					
//				} while (curr.fromResultSet(recordResult));
//				this.next = constructor.newInstance();
//				Record current = this.next;
//				if (!this.next.fromResultSet(recordResult)) {
//					
//				}
//				
//				this.next = (recordResult.next() ? (Record) constructor.newInstance(recordResult) : null);
//			} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
//				e.printStackTrace();
//				throw new RuntimeException("Error creating new record types dynamically from SQL Results");
//			}
//		}
		
		@SuppressWarnings("unchecked")
		public <T> T getFieldValue(String fieldName) {
			return (T) this.fields.get(fieldName).value;
		}
		
		protected <T> void setFieldValue(String fieldName, T value) {
			this.fields.get(fieldName).value = value;
		}

//		/**
//		 * @return the id
//		 */
//		public String getId() {
//			DBField id = getFieldValue("id");
//			return (id.value == null ? null : id.value.toString());
//		}
//		
//		/**
//		 * @param id the id to set
//		 */
//		protected void setId(UUID id) {
//			setFieldValue("id", id);
//		}
//		
//		/**
//		 * @param id the id to set
//		 */
//		protected void setId(String id) {
//			setId(id == null ? null : UUID.fromString(id));
//		}
		
//		protected Map<String, DBField> getKeyFields(List<String> keynames) {
//			return Record.getKeyFields(this.fields, keynames);
//		}
//		
//		public static Map<String, DBField> getKeyFields(Map<String, DBField> fields, List<String> keynames) {
//			if (keynames == null || keynames.isEmpty()) return Map.<String, DBField>of();
//			return fields.entrySet().stream().filter(e -> keynames.contains(e.getKey())).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
//		}
		
		private static Collection<DBField> getKeyFields(DBWhere key) {
			if (key.field != null) return List.of(key.field);
			if (key.fields != null && !key.fields.isEmpty()) return key.fields;
			return List.<DBField>of();
		}
		
		public static Collection<DBField> getKeyFields(DBWhereClause key) {
			Collection<DBField> fields = new ArrayList<DBField>();
			if (key == null) return fields;
			
			if (key.wheres != null && !key.wheres.isEmpty()) {
				for (DBWhere where: key.wheres) {
					fields.addAll(Record.getKeyFields(where));
				}
			} else if (key.clauses != null && !key.clauses.isEmpty()) {
				for (DBWhereClause clause: key.clauses) {
					fields.addAll(Record.getKeyFields(clause));
				}
			}
			
			return fields;
		}
		
		public static String getInsertColumnFormat(Map<String, DBField> fields) {
			if (fields.isEmpty()) return "";
			
			String format = "%s,".repeat(fields.size()).replaceFirst(",$", "");
			return String.format(format, fields.keySet().toArray());
		}
		
		public static String getInsertValuesFormat(Map<String, DBField> fields) {
			if (fields.isEmpty()) return "";
			
			String format = "?,".repeat(fields.size()).replaceFirst(",$", "");
			return format;
		}
		
		public static String getSetFormat(Map<String, DBField> fields) {
			if (fields.isEmpty()) return "";
			
			String format = "%s = ?,".repeat(fields.size()).replaceFirst(",$", "");
			return String.format(format, fields.keySet().toArray());
		}
		
//		public static String getWhereKeyFormat(Map<String, DBField> fields) {
//			if (fields.isEmpty()) return "";
//			
//			String format = "";
//			for (DBField field: fields.values()) {
//				format += "%s" + String.format(" %s ? AND ", (field.value == null ? "IS" : "="));
//			}
//			format = format.replaceFirst(" AND $", "");
//			return String.format(format, fields.keySet().toArray());
//		}
		
		public static String getWhereKeyFormat(DBWhereClause clause) {
			if ((clause.wheres == null || clause.wheres.isEmpty()) && (clause.clauses == null || clause.clauses.isEmpty())) return "";
			
			String format = "(";
			if (clause.wheres != null && !clause.wheres.isEmpty()) {
				if ((clause.linkers == null && clause.wheres.size() == 1) ||
						(clause.linkers != null && clause.linkers.size() == (clause.wheres.size() - 1))) {
					
					int i = 0;
					for (DBWhere where: clause.wheres) {
						if (where.field != null) {
							if (where.field.value != null && !EnumSet.of(DBWhereComparator.IN).contains(where.comparator)) {
								format = format + String.format("%s %s ?", where.field.name, where.comparator.sqlRepresentation);
							} else if (where.field.value != null && EnumSet.of(DBWhereComparator.IN).contains(where.comparator)) {
								format = format + String.format("%s %s (?)", where.field.name, where.comparator.sqlRepresentation);
							} else if (EnumSet.of(DBWhereComparator.IS, DBWhereComparator.IS_NOT).contains(where.comparator)) {
								format = format + String.format("%s %s ?", where.field.name, where.comparator.sqlRepresentation);
							} else if (EnumSet.of(DBWhereComparator.EQ).contains(where.comparator)) {
								format = format + String.format("%s %s ?", where.field.name, DBWhereComparator.IS.sqlRepresentation);
							} else if (EnumSet.of(DBWhereComparator.NEQ).contains(where.comparator)) {
								format = format + String.format("%s %s ?", where.field.name, DBWhereComparator.IS_NOT.sqlRepresentation);
							}
						} else if (where.fields != null && !where.fields.isEmpty() && EnumSet.of(DBWhereComparator.IN).contains(where.comparator) ) {
							format = format + String.format("%s %s (%s)", where.fields.get(0).name, where.comparator, "?, ".repeat(where.fields.size()).replaceFirst(", $", ""));
						}
						
						if (clause.linkers != null && i < clause.linkers.size()) {
							format = format + String.format(" %s ", clause.linkers.get(i));
						}
						
						++i;
					}
				}
			} else if (clause.clauses != null && !clause.clauses.isEmpty()) {
				if ((clause.linkers == null && clause.clauses.size() == 1) ||
						(clause.linkers != null && clause.linkers.size() == (clause.clauses.size() - 1))) {
					
					int i = 0;
					for (DBWhereClause cl: clause.clauses) {
						format = format + Record.getWhereKeyFormat(cl);
						
						if (clause.linkers != null && i < clause.linkers.size()) {
							format = format + String.format(" %s ", clause.linkers.get(i));
						}
						
						++i;
					}
				}
			}
			format = format + ")";
			
			return format;
		}

		
		public abstract boolean insert();
		public abstract boolean update();
		public abstract boolean delete();

		/**
		 * @return
		 */
		public static boolean insert(Record record) {
			Map<String, DBField> insertFields = record.fields.entrySet().stream().filter(e -> e.getValue().settings.insert).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
			if (insertFields.entrySet().stream().anyMatch(e -> e.getValue().settings.required && (e.getValue().value == null && e.getValue().defaultValue == null ))) return false;
							
			try (Connection conn = getConnection()) {
				PreparedStatement insertStatement = conn.prepareStatement(
						String.format("INSERT INTO %s(%s) VALUES(%s) RETURNING *", record.tablename, Record.getInsertColumnFormat(insertFields), Record.getInsertValuesFormat(insertFields)));
				
				Record.setQueryValuesFromFields(insertStatement, insertFields.values());
				
				ResultSet insertResult = insertStatement.executeQuery();
				record.fromResultSet(insertResult);
				
				insertResult.close();
				conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
				return false;
			}
			
			return true;
		}
		
		
		/**
		 * @return
		 */
		public static boolean updateByKey(Record record, DBWhereClause key) {
//			Map<String, DBField> keyFields = Record.getKeyFields(record.fields, key);
//			if (keyFields.isEmpty() || keyFields.values().stream().anyMatch(f -> f.value == null)) return false;
			
			Map<String, DBField> updateFields = record.fields.entrySet().stream().filter(e -> e.getValue().settings.update).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
			if (updateFields.entrySet().stream().anyMatch(e -> e.getValue().settings.required && (e.getValue().value == null && e.getValue().defaultValue == null))) return false;

			try (Connection conn = getConnection()) {
				PreparedStatement insertStatement = conn.prepareStatement(
						String.format("UPDATE %s SET %s %s RETURNING *", 
								record.tablename, Record.getSetFormat(updateFields), 
								(key.isEmpty() ? "" : String.format("WHERE %s", Record.getWhereKeyFormat(key)))));
				
				Record.setQueryValuesFromFields(insertStatement, updateFields.values());
				Record.setQueryValuesFromFields(insertStatement, Record.getKeyFields(key), updateFields.size());
				
				ResultSet insertResult = insertStatement.executeQuery();
				record.fromResultSet(insertResult);
				
				insertResult.close();
				conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
				return false;
			}
			
			return true;
		}
		
		/**
		 * @param key
		 * @return
		 */
		public static boolean deleteByKey(Record record, DBWhereClause key) {
//			Map<String, DBField> keyFields = record.getKeyFields(key);
//			if (keyFields.isEmpty() || keyFields.values().stream().anyMatch(f -> f.value == null)) return false;
			
			try (Connection conn = getConnection()) {
				PreparedStatement deleteRecordStatement = conn.prepareStatement(
						String.format("DELETE FROM %s WHERE %s RETURNING *", record.tablename, Record.getWhereKeyFormat(key)));
				
				Record.setQueryValuesFromFields(deleteRecordStatement, Record.getKeyFields(key));
				
				ResultSet deleteResult = deleteRecordStatement.executeQuery();
				record.fromResultSet(deleteResult);
				
				deleteResult.close();
				conn.close();
			} catch (SQLException e) {
				throw new RuntimeException(String.format("Error deleting record: $s", record.toString()), e);
			}
			
			return true;
		}
		
		public static Record createGenericRecord(String tablename, Map<String, DBField> fields) {
			if (tablename == null || tablename.isBlank()) throw new IllegalArgumentException("tablename must not be null or blank");
			if (fields == null || fields.isEmpty()) throw new IllegalArgumentException("fields munst not be null or empty");
			
			Map<String, DBField> dbfields = fields;
			return new Record(tablename) {
				{
					this.fields = dbfields;
				}

				@Override
				public boolean insert() {
					throw new OperationNotPermitted("No insert method for generic Record");
				}

				@Override
				public boolean update() {
					throw new OperationNotPermitted("No update method for generic Record");
				}

				@Override
				public boolean delete() {
					throw new OperationNotPermitted("No delete method for generic Record");
				}

				@Override
				public DBWhereClause getKey() {
					throw new OperationNotPermitted("No getKey method for generic Record");
				}
			};
		}
		
		public Record findOne(DBWhereClause key) {
			return Record.findOne(this, key);
		}
		
		public List<Record> find(DBWhereClause key) {
			return Record.find(this, key);
		}
		
		/**
		 * @param tablename
		 * @param fields
		 * @param key
		 * @return
		 * 	Returns a single record found with the identifying key.
		 *  If no records or more than one record is found then null is returned.
		 * @throws SQLException 
		 */
		public static <T extends Record> T findOne(T record, DBWhereClause key) {
			List<T> records = Record.find(record, key);
			return ((records.isEmpty() || records.size() > 1) ? null : records.get(0));
		}
		
		public static <T extends Record> List<T> find(T record, DBWhereClause key) {
			if (record == null || record.fields == null || record.tablename() == null) {
				throw new IllegalArgumentException("Record and its fields and tablename properties must not be null");
			}
			List<T> records = new ArrayList<T>();
			
			try(Connection conn = getConnection()) {
				ResultSet findResult = Record.getResultsFromFields(conn, record.tablename(), Record.getKeyFields(key));

				boolean recorded;
				TypeToken<T> type = new TypeToken<T>() {};
				do {
					T rec = ((type.getType().getClass() == Record.class) ? (T) type.getRawType().getConstructor().newInstance() : (T) Record.createGenericRecord(record.tablename(), record.fields));
					recorded = rec.fromResultSet(findResult);
					
					if (recorded) records.add((T) rec);
				} while (recorded);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
				e.printStackTrace();
			} catch (SQLException e) {
				return null;
			}
			
			return Collections.unmodifiableList(records);
		}
		
		/**
		 * @param key
		 * @return
		 * @throws RecordNotFoundException 
		 */
		protected boolean getRecordByKey(DBWhereClause key) throws RecordNotFoundException {
//			Map<String, DBField> keyFields = this.getKeyFields(key);
//			if (keyFields.isEmpty()) return false;

			//				ResultSet recordResult = Record.getResultsFromFields(conn, this.tablename, keyFields.values());
			//
			//				this.fromResultSet(recordResult);
			//				
			//				recordResult.close();
			//				conn.close();
			Record record = this.findOne(key);
			if (record == null) throw new RecordNotFoundException(key.toString());
			
			return true;
		}
		
		public static ResultSet getResultsFromFields(Connection conn, String tablename, Collection<DBField> fields) throws SQLException {
			PreparedStatement getRecordStatement = conn.prepareStatement(
					String.format("SELECT * FROM %s%s", tablename, 
							(fields.isEmpty() ? "" : String.format(" WHERE %s", fields))));
			
			Record.setQueryValuesFromFields(getRecordStatement, fields);
			
			ResultSet recordResult = getRecordStatement.executeQuery();
			return recordResult;
		}
		
		public static boolean setFieldsFromResultSet(ResultSet resultSet, Record record) throws SQLException {
			if (!resultSet.next()) return false;
			
			for (DBField field: (record.fields != null ? record.fields.values() : Collections.<DBField>emptyList())) {
				switch (field.settings.type) {
					case Types.DATE:
						field.value = resultSet.getDate(field.name);
						break;
					case Types.LONGVARCHAR:
						field.value = resultSet.getString(field.name);
						break;
					case Types.BOOLEAN:
						field.value = resultSet.getBoolean(field.name);
						break;
					case Types.BIGINT:
						field.value = resultSet.getLong(field.name);
						break;
					default:
						if (field.settings.objectCastType != null) {
							field.value = resultSet.getObject(field.name, field.settings.objectCastType);
						} else {
							field.value = resultSet.getObject(field.name);
						}
						break;
				}
			}
			
			return true;
		}
		
		/**
		 * @param resultSet
		 * @return
		 * @throws SQLException
		 */
		protected boolean fromResultSet(ResultSet resultSet) throws SQLException {
			return Record.setFieldsFromResultSet(resultSet, this);
		}
		
		/**
		 * @param statement
		 * @param fields
		 * @throws SQLException
		 */
		public static void setQueryValuesFromFields(PreparedStatement statement, Collection<DBField> fields) throws SQLException {
			setQueryValuesFromFields(statement, fields, 0);
		}

		
		/**
		 * @param statement
		 * @param fields
		 * @param startIndex
		 * @throws SQLException
		 */
		public static void setQueryValuesFromFields(PreparedStatement statement, Collection<DBField> fields, int startIndex) throws SQLException {
			int index = startIndex;
			for (DBField field: fields) {
				index++;
				
				Object value = (field.value == null ? field.defaultValue : field.value);
				
				if (value == null) {
					statement.setNull(index, field.settings.type);
				} 
				
				else {
					switch (field.settings.type) {
					case Types.DATE:
						statement.setDate(index, (Date) value);
						break;
					case Types.LONGVARCHAR:
						statement.setString(index, (String) value);
						break;
					case Types.BOOLEAN:
						statement.setBoolean(index, (Boolean) value);
						break;
					case Types.BIGINT:
						statement.setLong(index, (Long) value);
						break;
					case Types.OTHER:
						statement.setObject(index, value, java.sql.Types.OTHER);
						break;
					default:
						statement.setObject(index, value);
						break;
					}
				}
			}
		}

		@Override
		public String toString() {
			Map<String, String> keyValueMap = this.fields.entrySet()
					.stream().collect(Collectors.toMap(
							e -> e.getKey(), e -> e.getValue().value.toString()));
			
			return String.format("{ tablename: %s, values: %s }",
					this.tablename(), keyValueMap.toString());
		}
	}
	
	public static abstract class AbstractRecord extends Record {

		protected AbstractRecord(String tablename) {
			super(tablename);
		}

		@Override
		public DBWhereClause getKey() {
			throw new OperationNotPermitted("No getKey method for an abstract record");
		}

		@Override
		public boolean insert() {
			throw new OperationNotPermitted("No insert method for an abstract record");
		}

		@Override
		public boolean update() {
			throw new OperationNotPermitted("No update method for an abstract record");
		}

		@Override
		public boolean delete() {
			throw new OperationNotPermitted("No delete method for an abstract record");
		}
	}
	
	public static abstract class RecordIdUUID extends Record {
		@Override
		public DBWhereClause getKey() {
			return DBWhereClauseBuilder.Builder().withRecord(this).withWhereList()
					.addWhere().withColumn("id").withComparator(DBWhereComparator.EQ).withValue(this.getFieldValue("id")).addToWhereList()
					.addListToClause()
					.build();
		}
		
		protected RecordIdUUID(String tablename) {
			super(tablename);
			this.addIDField();
		}
		
		protected void addIDField() {
			this.fields.put("id", new DBField() {{
				name = "id";
				settings = new DBFieldSettings() {{
					type = Types.OTHER;
					objectCastType = UUID.class;
					required = true;
				}};
			}});
		}

		public UUID getId() {
			return this.<UUID>getFieldValue("id");
		}
		
		protected void setId(String id) {
			this.setId(id == null ? null : UUID.fromString(id));
		}
		
		protected void setId(UUID id) {
			this.setFieldValue("id", id);
		}
		
		public static <T extends RecordIdUUID> T fromId(UUID id) {
			T record = RecordIdUUIDBuilder.<T>Builder().withId(id).build();
			return RecordIdUUID.<T>findOne(record, record.getKey());
		}
		
		public static class RecordIdUUIDBuilder<T extends RecordIdUUID> {
			private UUID id;
			
			public static <T extends RecordIdUUID> RecordIdUUIDBuilder<T> Builder() {
				return new RecordIdUUIDBuilder<T>();
			}
			
			public RecordIdUUIDBuilder<T> withId(String id) {
				return this.withId(id == null ? null : UUID.fromString(id));
			}
			
			public RecordIdUUIDBuilder<T> withId(UUID id) {
				this.id = id;
				return this;
			}
			
			public T build() {
				TypeToken<T> type = new TypeToken<T>() {};
				try {
					T record = (T) type.getRawType().getConstructor().newInstance();
					record.setId(this.id);
					return record;
				} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
						| InvocationTargetException | NoSuchMethodException | SecurityException e) {
					e.printStackTrace();
				}
				return null;
			}
		}
	}
	
	/**
	 * @author Fraser LeFevre
	 *
	 */
	public static class File extends RecordIdUUID {
		public final static String TABLENAME = "file";
		
//		@Override
//		public DBWhereClause getKey() {
//			return DBWhereClauseBuilder.Builder().withRecord(this).withWhereList()
//					.addWhere().withColumn("id").withComparator(DBWhereComparator.EQ).withValue(this.getFieldValue("id")).addToWhereList()
//					.addListToClause()
//					.build();
////			return Record.createDBWhereClause(null, 
////					List.of(
////							Record.createDBWhere(
////									this, "id", 
////									DBWhereComparator.EQ, 
////									this.getFieldValue("id"))));
//		}

		@Override
		public boolean insert() {
			return Record.insert(this);
		}

		@Override
		public boolean update() {
			return Record.updateByKey(this, this.getKey());
		}

		@Override
		public boolean delete() {
			return Record.deleteByKey(this, this.getKey());
		}
		
		{
//			this.addIDField();
			this.addFields(
				new DBField() {{
					name = "filename";
					settings = new DBFieldSettings() {{
						type = Types.LONGVARCHAR;
						required = true;
						insert = true;
					}};
				}},
				new DBField() {{
					name = "title";
					settings = new DBFieldSettings() {{
						type = Types.LONGVARCHAR;
						required = true;
						insert = true;
						update = true;
					}};
				}},
				new DBField() {{
					name = "description";
					settings = new DBFieldSettings() {{
						type = Types.LONGVARCHAR;
						insert = true;
						update = true;
					}};
				}},
				new DBField() {{
					name = "date_added";
					settings = new DBFieldSettings() {{
						type = Types.DATE;
						required = true;
					}};
				}});
//			this.addKey("id", List.of("id"));
		}
		
		/**
		 * 
		 */
		public File() {
			super(File.TABLENAME);
		}
		
//		public File(ResultSet fileResult) throws SQLException {
//			super(fileResult);
//		}
		
		/**
		 * @param id
		 * @throws RecordNotFoundException 
		 */
		public File(String id) throws RecordNotFoundException {
			this();
			this.setId(id);
			this.getRecordByKey(this.getKey());
		}
		
		/**
		 * @param userId
		 * @param filename
		 * @param title
		 * @param description
		 */
		public File(String userId, String filename, String title, String description) {
			this();
			this.fields.get("user_id").value = UUID.fromString(userId);
			this.fields.get("filename").value = filename;
			this.fields.get("title").value = title;
			this.fields.get("description").value = description;
		}
		
		/**
		 * @return
		 */
		public String getUserId() {
			return this.fields.get("user_id").value.toString();
		}
		
		/**
		 * @return
		 */
		public String getFilename() {
			return (String) this.fields.get("filename").value;
		}
		
		/**
		 * @return
		 */
		public String getTitle() {
			return (String) this.fields.get("title").value;
		}
		
		/**
		 * @return
		 */
		public String getDescription() {
			return (String) this.fields.get("description").value;
		}
		
		/**
		 * @return
		 */
		public Date getDateAdded() {
			return (Date) this.fields.get("date_added").value;
		}
		
		/**
		 * @return
		 */
		public Boolean hasAnonymousAccess() {
			return (Boolean) this.fields.get("anonymous_access").value;
		}
		
		/**
		 * @param title
		 */
		public void setTitle(String title) {
			this.fields.get("title").value = title;
		}
		
		/**
		 * @param description
		 */
		public void setDescription(String description) {
			this.fields.get("description").value = description;
		}
		
		/**
		 * @return
		 */
		public void setAnonymousAccess(Boolean anonymousAccess) {
			this.fields.get("anonymous_access").value = anonymousAccess;
		}
	}
	
	public static class FilePermission extends AbstractRecord {
		public final static String TABLENAME = "file_permission";
		
		{
			this.addFields(
				new DBField() {{
					name = "id";
					settings = new DBFieldSettings() {{
						type = Types.BIGINT;
						required = true;
					}};
				}},
				new DBField() {{
					name = "title";
					settings = new DBFieldSettings() {{
						type = Types.LONGVARCHAR;
						required = true;
					}};
				}});
		}

		protected FilePermission() {
			super(FilePermission.TABLENAME);
		}

		@Override
		public DBWhereClause getKey() {
			return DBWhereClauseBuilder.Builder().withRecord(this).withWhereList()
					.addWhere().withColumn("id").withValue(this.<Long>getFieldValue("id")).addToWhereList()
					.addListToClause().build();
		}
		
		public Long getId() {
			return this.<Long>getFieldValue("id");
		}
		
		public static FilePermission fromID(Long id) {
			FilePermission fp = new FilePermission();
			return FilePermission.<FilePermission>findOne(fp, 
					DBWhereClauseBuilder.Builder().withRecord(new FilePermission()).withWhereList()
						.addWhere().withColumn("id").withValue(id).addToWhereList()
						.addListToClause().build());
		}
		
		public static FilePermission fromTitle(String title) {
			FilePermission fp = new FilePermission();
			return FilePermission.<FilePermission>findOne(fp, 
					DBWhereClauseBuilder.Builder().withRecord(new FilePermission()).withWhereList()
						.addWhere().withColumn("title").withValue(title).addToWhereList()
						.addListToClause().build());
		}
	}

	public static class UserFilePermission extends Record {
		public final static String TABLENAME = "user_file_permission";

		@Override
		public DBWhereClause getKey() {
			return DBWhereClauseBuilder.Builder().withRecord(this).withWhereList()
					.addWhere().withColumn("file_id").withComparator(DBWhereComparator.EQ).withValue(this.getFieldValue("file_id")).addToWhereList()
					.linker(DBWhereLinker.AND)
					.addWhere().withColumn("user_id").withComparator(DBWhereComparator.EQ).withValue(this.getFieldValue("user_id")).addToWhereList()
					.addListToClause()
					.build();
//			return Record.createDBWhereClause(
//					List.of(DBWhereLinker.AND), 
//					List.of(
//							Record.createDBWhere(
//									this, "file_id", 
//									DBWhereComparator.EQ, 
//									this.getFieldValue("file_id")),
//							Record.createDBWhere(
//									this, "user_id", 
//									DBWhereComparator.EQ, 
//									this.getFieldValue("user_id"))));
		}

		@Override
		public boolean insert() {
			return Record.insert(this);
		}

		@Override
		public boolean update() {
			return Record.updateByKey(this, this.getKey());
		}

		@Override
		public boolean delete() {
			return Record.deleteByKey(this, this.getKey());
		}
		
		{
			this.addFields(
					new DBField() {{
						name = "file_id";
						settings = new DBFieldSettings() {{
							objectCastType = UUID.class;
							type = Types.OTHER;
							required = true;
							insert = true;
						}};
					}},
					new DBField() {{
						name = "user_id";
						settings = new DBFieldSettings() {{
							objectCastType = UUID.class;
							type = Types.OTHER;
							insert = true;
						}};
					}},
					new DBField() {{
						name = "file_permission_id";
						settings = new DBFieldSettings() {{
							type = Types.BIGINT;
							required = true;
							insert = true;
							update = true;
						}};
					}});
//			this.addKey("primary_key", List.of("file_id", "user_id"));
//			
//			// Multi-Record Keys
//			this.addKey("file_id", List.of("file_id"));
//			this.addKey("user_id", List.of("user_id"));
		}
		
		public UserFilePermission() {
			super(UserFilePermission.TABLENAME);
		}
		
		public UserFilePermission(String fileId, String userId, Long file_permission_id) {
			this();
			this.setFileId(fileId);
			this.setUserId(userId);
			this.setFilePermissionId(file_permission_id);
		}

		public String getFileId() {
			return this.fields.get("file_id").value.toString();
		}

		private void setFileId(String fileId) {
			this.fields.get("file_id").value = UUID.fromString(fileId);
		}
		
		public String getUserId() {
			return this.fields.get("user_id").value.toString();
		}

		private void setUserId(String userId) {
			this.fields.get("user_id").value = UUID.fromString(userId);
		}
		
		public Long getFilePermissionId() {
			return (Long) this.fields.get("file_permission_id").value;
		}
		
		public void setFilePermissionId(Long file_permission_id) {
			this.fields.get("file_permission_id").value = file_permission_id;
		}
	}
	
	/**
	 * @author Fraser LeFevre
	 *
	 */
	public static class User extends RecordIdUUID {
		public final static String TABLENAME = "app_user";
		
//		@Override
//		public DBWhereClause getKey() {
//			return DBWhereClauseBuilder.Builder().withRecord(this).withWhereList()
//					.addWhere().withColumn("id").withComparator(DBWhereComparator.EQ).withValue(this.getFieldValue("id")).addToWhereList()
//					.addListToClause()
//					.build();
////			return Record.createDBWhereClause(null, 
////					List.of(
////							Record.createDBWhere(
////									this, "id", 
////									DBWhereComparator.EQ, 
////									this.getFieldValue("id"))));
//		}
		
		public DBWhereClause getUsernameKey() {
			return DBWhereClauseBuilder.Builder().withRecord(this).withWhereList()
					.addWhere().withColumn("username").withComparator(DBWhereComparator.EQ).withValue(this.getFieldValue("username")).addToWhereList()
					.addListToClause()
					.build();
//			return Record.createDBWhereClause(null, 
//					List.of(
//							Record.createDBWhere(
//									this, "username", 
//									DBWhereComparator.EQ, 
//									this.getFieldValue("username"))));
		}
		
		@Override
		public boolean insert() {
			return Record.insert(this);
		}

		@Override
		public boolean update() {
			return Record.updateByKey(this, this.getKey());
		}

		@Override
		public boolean delete() {
			return Record.deleteByKey(this, this.getKey());
		}


		{
//			this.addIDField();
			this.addFields(
					new DBField() {{
						name = "username";
						settings = new DBFieldSettings() {{
							type = Types.LONGVARCHAR;
							required = true;
							insert = true;
						}};
					}},
					new DBField() {{
						name = "f_name";
						settings = new DBFieldSettings() {{
							type = Types.LONGVARCHAR;
							insert = true;
						}};
					}},
					new DBField() {{
						name = "l_name";
						settings = new DBFieldSettings() {{
							type = Types.LONGVARCHAR;
							insert = true;
						}};
					}});
//			this.addKey("id", List.of("id"));
//			this.addKey("username", List.of("username"));
		}
		
		/**
		 * 
		 */
		public User() { 
			super(User.TABLENAME);
		}
		
		/**
		 * @param id
		 * @throws RecordNotFoundException 
		 */
		public User(String id) throws RecordNotFoundException {
			this();
			if (id == null) return;
			
			this.setId(id);
			this.getRecordByKey(this.getKey());
		}
		
		/**
		 * @param username
		 * @param fName
		 * @param lName
		 */
		public User(String username, String fName, String lName) {
			this();
			this.fields.get("username").value = username;
			this.fields.get("f_name").value = fName;
			this.fields.get("l_name").value = lName;
		}
		
//		/**
//		 * @param userResult
//		 * @throws SQLException
//		 */
//		public User(ResultSet userResult) throws SQLException {
//			super(userResult);
//		}
		
		/**
		 * @return
		 * @throws RecordNotFoundException 
		 */
		public boolean getFromUsername() throws RecordNotFoundException {
			return this.getRecordByKey(this.getUsernameKey());
		}

		/**
		 * @param username
		 */
		public void setUsername(String username) {
			this.fields.get("username").value = username;
		}

		/**
		 * @return
		 */
		public String getUsername() {
			return (String) this.fields.get("username").value;
		}

		/**
		 * @return
		 */
		public String getfName() {
			return (String) this.fields.get("f_name").value;
		}

		/**
		 * @return
		 */
		public String getlName() {
			return (String) this.fields.get("l_name").value;
		}

		@Override
		public boolean equals(Object obj) {
			try {
				User usr = (User) obj;
				if (this.getId().equals(usr.getId())) return true;
				else return false;
			} catch (ClassCastException e) {
				return false;
			}
		}
	}
	
//	/**
//	 * @author Fraser LeFevre
//	 *
//	 */
//	public class SharedFile extends Record {
//		public final static String TABLENAME = "shared_files";
//
//		@Override
//		protected String tablename() {
//			return SharedFile.TABLENAME;
//		}
//
//		@Override
//		public boolean insert() {
//			return Record.insert(this);
//		}
//
//		@Override
//		public boolean update() {
//			return Record.updateByKey(this, this.getKey("id"));
//		}
//
//		@Override
//		public boolean delete() {
//			return Record.deleteByKey(this, this.getKey("id"));
//		}
//		
//		{
//			this.addIDField();
//			this.addFields(
//					new DBField() {{
//						name = "user_id";
//						settings = new DBFieldSettings() {{
//							type = Types.OTHER;
//							objectCastType = UUID.class;
//							required = true;
//							insert = true;
//						}};
//					}},
//					new DBField() {{
//						name = "file_id";
//						settings = new DBFieldSettings() {{
//							type = Types.OTHER;
//							objectCastType = UUID.class;
//							required = true;
//							insert = true;
//						}};
//					}});
//			this.addKey("id", List.of("id):"));
//			this.addKey("user_file", List.of("user_id", "file_id"));
//		}
//		
//		/**
//		 * 
//		 */
//		private SharedFile() { }
//		
//		/**
//		 * @param id
//		 */
//		public SharedFile(String id) {
//			this.setId(id);
//			this.getRecordByKey(this.getKey("id"));
//		}
//		
//		/**
//		 * @param userId
//		 * @param fileId
//		 */
//		public SharedFile(String userId, String fileId) {
//			this.setUserId(userId);
//			this.setFileId(fileId);
//		}
//		
//		public SharedFile(ResultSet sharedFileResult) throws SQLException {
//			super(sharedFileResult);
//		}
//
//		/**
//		 * @return
//		 */
//		public boolean get() {
//			return this.getRecordByKey(this.getKey("user_file"));
//		}
//		
//		/**
//		 * @param fileId
//		 */
//		protected void setFileId(String fileId) {
//			this.fields.get("file_id").value = UUID.fromString(fileId);
//		}
//		
//		/**
//		 * @return
//		 */
//		public String getFileId() {
//			return this.fields.get("file_id").value.toString();
//		}
//
//		/**
//		 * @param userId
//		 */
//		protected void setUserId(String userId) {
//			this.fields.get("user_id").value = UUID.fromString(userId);
//		}
//
//		/**
//		 * @return
//		 */
//		public String getUserId() {
//			return this.fields.get("user_id").value.toString();
//		}
//	}
	
//	/**
//	 * @author Fraser LeFevre
//	 *
//	 */
//	public class UserFileAccess extends SharedFile {
//		public final static String VIEWNAME = "file_access";
//
//		@Override
//		protected String tablename() {
//			return UserFileAccess.VIEWNAME;
//		}
//
//		@Override
//		public boolean delete() {
//			throw new OperationNotPermitted("UserFileAccess is a view. DELETE is invalid");
//		}
//
//		@Override
//		public boolean update() {
//			throw new OperationNotPermitted("UserFileAccess is a view. UPDATE is invalid");
//		}
//
//		@Override
//		public boolean insert() {
//			throw new OperationNotPermitted("UserFileAccess is a view. INSERT is invalid");
//		}
//
//		/**
//		 * @return
//		 */
//		@Override
//		public String getUserId() {
//			UUID userId = (UUID) this.fields.get("user_id").value;
//			return (userId == null ? null : userId.toString());
//		}
//		
//		protected User owner;
//		protected User user;
//		protected File file;
//		
//		{
//			this.addFields(
//					new DBField() {{
//						name = "owner_id";
//						settings = new DBFieldSettings() {{
//							type = Types.OTHER;
//							objectCastType = UUID.class;
//							required = true;
//							insert = true;
//						}};
//					}});
//			
//			// remove fields from SharedFile parent class
//			this.fields.remove("id");
//			this.keys.remove("id");
//		}
//		
//		/**
//		 * @param ufaResult
//		 * @throws SQLException
//		 */
//		public UserFileAccess(ResultSet ufaResult) throws SQLException {
//			super(ufaResult);
////			this.fromResultSet(ufaResult);
//			this.owner = new User(this.getOwnerId());
//			this.user = new User(this.getUserId());
//			this.file = new File(this.getFileId());
//			
////			this.next = (ufaResult.next() ? new UserFileAccess(ufaResult) : null);
//		}
//
//		/**
//		 *
//		 */
//		@Override
//		public String getId() {
//			throw new OperationNotPermitted("UserFileAccess view has no id");
//		}
//
//		/**
//		 * @return
//		 */
//		public String getOwnerId() {
//			return this.fields.get("owner_id").value.toString();
//		}
//
//		/**
//		 * @return the owner
//		 */
//		public User getOwner() {
//			return owner;
//		}
//
//		/**
//		 * @return the user
//		 */
//		public User getUser() {
//			return user;
//		}
//
//		/**
//		 * @return the file
//		 */
//		public File getFile() {
//			return file;
//		}
//	}
//	
//	/**
//	 * @author Fraser LeFevre
//	 *
//	 */
//	public class FileUserAccess {
//		protected File file = null;
//		protected List<User> users = null;
//		
//		/**
//		 * @param fileId
//		 */
//		public FileUserAccess(String fileId) {
//			try (Connection conn = getConnection()) {
//				PreparedStatement fuaStatement = conn.prepareStatement(
//						"SELECT * FROM file_access WHERE file_id = ? AND user_id IS NOT NULL");
//				fuaStatement.setObject(1, UUID.fromString(fileId), Types.OTHER);
//				
//				List<FileUserAccess> fuaList = new ArrayList<FileUserAccess>();
//				ResultSet fuaResult = fuaStatement.executeQuery();
//				
//				while(fuaResult.next()) {
//					fuaList.add(new FileUserAccess(fuaResult));
//				}
//				
//				FileUserAccess fua = new FileUserAccess(fuaList);
//				this.file = fua.file;
//				this.users = fua.users;
//			} catch (SQLException e) {
//				e.printStackTrace();
//			}
//		}
//		
//		/**
//		 * @param fuaResult
//		 * @throws SQLException
//		 */
//		private FileUserAccess(ResultSet fuaResult) throws SQLException {
//			if (fuaResult == null) throw new IllegalArgumentException("ResultSet must not be null");
//			
//			UserFileAccess ufa = new UserFileAccess(fuaResult);
//			this.file = ufa.file;
//			this.users = List.of(ufa.user);
//		}
//		
//		/**
//		 * @param fuas
//		 */
//		private FileUserAccess(List<FileUserAccess> fuas) {
//			if (fuas == null || fuas.isEmpty()) return;
//			this.file = fuas.get(0).file;
//			this.users = fuas.get(0).users;
//			this.merge(fuas.subList(1, fuas.size()));
//		}
//		
//		/**
//		 * @param fuas
//		 */
//		private void merge(List<FileUserAccess> fuas) {
//			fuas = (fuas == null ? List.of() : fuas);
//			if (fuas.stream().anyMatch(f -> !f.file.getId().equals(this.file.getId()))) throw new IllegalArgumentException("Unable to merge FileUserAccess objects with different file ids");
//
//			this.users = Stream.of(List.of(this), fuas)
//					.flatMap(list -> list.stream())
//					.map(fua -> fua.users).flatMap(users -> users.stream())
//					.distinct().collect(Collectors.toList());
//		}
//
//		/**
//		 * @return the file
//		 */
//		public File getFile() {
//			return file;
//		}
//
//		/**
//		 * @return the userIds
//		 */
//		public String[] getUserIds() {
//			return this.users.stream().map(user -> user.getId()).toArray(String[]::new);
//		}
//	}
}
