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

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.metadata.CommonsDbcp2DataSourcePoolMetadata;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableMap;
import com.metamorphsoftware.shinyproxy.configurationproperties.SQLConfigurationProperties;

/**
 * @author Fraser LeFevre
 *
 */
@Service
public class SQLService {
	protected BasicDataSource pool;
	private static SQLConfigurationProperties sqlProperties;
	private static boolean initialized = false;
	
	/**
	 * @param sqlProp
	 */
	@Autowired
	public void initilizePool(SQLConfigurationProperties sqlProp) {
		if (!SQLService.initialized) {
			SQLService.sqlProperties = sqlProp;
			
			this.pool = new BasicDataSource();
			pool.setUrl(SQLService.sqlProperties.getUrl());
			pool.setUsername(SQLService.sqlProperties.getUsername());
			pool.setPassword(SQLService.sqlProperties.getPassword());
			pool.setMaxTotal(SQLService.sqlProperties.getMaxConnections());
			pool.setMaxConnLifetimeMillis(SQLService.sqlProperties.getMaxConnectionLifetimeMilliseconds());
			pool.setMaxIdle(SQLService.sqlProperties.getMaxIdleConnections());
			pool.setMinIdle(SQLService.sqlProperties.getMinIdleConnections());
			
			SQLService.initialized = true;
		}
	}
	
	/**
	 * @return
	 */
	public CommonsDbcp2DataSourcePoolMetadata getStats() {
		return new CommonsDbcp2DataSourcePoolMetadata(pool);
	}
	
	/**
	 * @return
	 * @throws SQLException
	 */
	public Connection getConnection() throws SQLException {
		return pool.getConnection();
	}
	
	/**
	 * @author Fraser LeFevre
	 *
	 */
	public class OperationNotPermitted extends RuntimeException {
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
	public abstract class Record {
		/**
		 * @author Fraser LeFevre
		 *
		 */
		public class DBFieldSettings {
			public int type = Types.OTHER;
			public Class<?> objectCastType = null;
			public boolean required = false;
			public boolean insert = false;
			public boolean update = false;
		}
		
		/**
		 * @author Fraser LeFevre
		 *
		 */
		public class DBField {
			public String name;
			public Object value = null;
			public DBFieldSettings settings;
		}

		protected UUID id = null;
		protected ImmutableMap<String, DBField> fields = null;
		
		/**
		 * @param fields
		 */
		protected void setFields(Map<String, DBField> fields) {
			if (fields == null) this.fields = ImmutableMap.of();
			else this.fields = ImmutableMap.copyOf(fields);
		}
		
		/**
		 * 
		 */
		private Record() {

		}

		/**
		 * @return the id
		 */
		public String getId() {
			return (id == null ? null : id.toString());
		}
		
		/**
		 * @param id the id to set
		 */
		protected void setId(UUID id) {
			this.id = id;
		}
		
		/**
		 * @param id the id to set
		 */
		protected void setId(String id) {
			this.id = (id == null ? null : UUID.fromString(id));
		}
		
		/**
		 * @return
		 */
		protected abstract String tablename();
		
		/**
		 * @return
		 */
		public boolean deleteById() {
			if (id == null) return false;
			
			try (Connection conn = getConnection()) {
				PreparedStatement deleteStatement = conn.prepareStatement(
						String.format("DELETE FROM %s WHERE id = ? RETURNING *", this.tablename()));
				deleteStatement.setObject(1, this.id);
				ResultSet deleteResult = deleteStatement.executeQuery();
				
				if (deleteResult.next()) {
					this.fromResultSet(deleteResult);
					this.id = null;
				}
				
			} catch (SQLException e) {
				e.printStackTrace();
				return false;
			}
			
			return true;
		}
		
		/**
		 * @param keyNames
		 * @return
		 */
		protected boolean deleteByKey(List<String> keyNames) {
			if (keyNames == null || keyNames.isEmpty()) return false;

			try (Connection conn = getConnection()) {
				String keyCheckArray[] = new String[keyNames.size()];
				Arrays.fill(keyCheckArray, "%s = ?");
				String keyCheck = String.format(String.join(" AND ", keyCheckArray), keyNames.toArray());
				
				PreparedStatement deleteRecordStatement = conn.prepareStatement(
						String.format("DELETE FROM %s WHERE %s RETURNING *", this.tablename(), keyCheck));
				
				Map<String, DBField> fields = this.fields.entrySet().stream().filter(e -> keyNames.contains(e.getKey())).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
				this.setQueryValuesFromFields(deleteRecordStatement, fields);
				
				ResultSet deleteResult = deleteRecordStatement.executeQuery();
				if (deleteResult.next()) { 
					this.fromResultSet(deleteResult);
					this.id = null;
				}
				
				deleteResult.close();
				conn.close();
			} catch (SQLException e) {
				throw new RuntimeException(String.format("Error retrieving user details: %s", this.id), e);
			}
			
			return true;
		}

		/**
		 * @return
		 */
		protected boolean insert() {
			if (this.id != null) return false;
			
			Map<String, DBField> fields = this.fields.entrySet().stream().filter(e -> e.getValue().settings.insert).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
			if (fields.entrySet().stream().anyMatch(e -> e.getValue().settings.required && e.getValue().value == null)) return false;
			
			fields = fields.entrySet().stream().filter(e -> e.getValue() != null).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
				
			try (Connection conn = getConnection()) {
				String columns = String.join(", ", fields.entrySet().stream().map(e -> e.getValue().name).toArray(String[]::new));
				String valueFormat = "?,".repeat(fields.size()).replaceFirst(",$", "");
				PreparedStatement insertStatement = conn.prepareStatement(
						String.format("INSERT INTO %s(%s) VALUES(%s) RETURNING *", this.tablename(), columns, valueFormat));
				
				this.setQueryValuesFromFields(insertStatement, fields);
				
				ResultSet insertResult = insertStatement.executeQuery();
				if (insertResult.next()) {
					this.fromResultSet(insertResult);
				}
				
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
		protected boolean update() {
			throw new UnsupportedOperationException("Not yet Implimented");
		}
		
		/**
		 * @return
		 */
		public boolean save() {
			if (this.id == null) 
				return this.insert();
			
			return this.update();
		}

		/**
		 * @return
		 */
		protected boolean getById() {
			if (this.id == null) return false;

			try (Connection conn = getConnection()) {
				PreparedStatement getRecordStatement = conn.prepareStatement(
						String.format("SELECT * FROM %s WHERE id = ?", this.tablename()));
				getRecordStatement.setObject(1, this.id, Types.OTHER);
				ResultSet recordResult = getRecordStatement.executeQuery();
				
				if (recordResult.next()) { 
					this.fromResultSet(recordResult);
				}
				
				recordResult.close();
				conn.close();
			} catch (SQLException e) {
				throw new RuntimeException(String.format("Error retrieving user details: %s", this.id), e);
			}
			
			return true;
		}
		
		/**
		 * @param keyNames
		 * @return
		 */
		protected boolean getByKey(List<String> keyNames) {
			if (keyNames == null || keyNames.isEmpty()) return false;

			try (Connection conn = getConnection()) {
				String keyCheckArray[] = new String[keyNames.size()];
				Arrays.fill(keyCheckArray, "%s = ?");
				String keyCheck = String.format(String.join(" AND ", keyCheckArray), keyNames.toArray());
				
				PreparedStatement getRecordStatement = conn.prepareStatement(
						String.format("SELECT * FROM %s WHERE %s", this.tablename(), keyCheck));
				
				Map<String, DBField> fields = this.fields.entrySet().stream().filter(e -> keyNames.contains(e.getKey())).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
				this.setQueryValuesFromFields(getRecordStatement, fields);
				
				ResultSet recordResult = getRecordStatement.executeQuery();
				if (recordResult.next()) { 
					this.fromResultSet(recordResult);
				}
				
				recordResult.close();
				conn.close();
			} catch (SQLException e) {
				throw new RuntimeException(String.format("Error retrieving user details: %s", this.id), e);
			}
			
			return true;
		}
		
		/**
		 * @param resultSet
		 * @return
		 * @throws SQLException
		 */
		protected boolean fromResultSet(ResultSet resultSet) throws SQLException {
			try {
				this.setId(resultSet.getObject("id", UUID.class));
			} catch (SQLException e) {
				// if no id field exists, record is just from a view/table with another key method
				this.setId((UUID) null);
			}
			for (DBField field: (this.fields != null ? this.fields.values() : Collections.<DBField>emptyList())) {
				switch (field.settings.type) {
					case Types.DATE:
						field.value = resultSet.getDate(field.name);
						break;
					case Types.LONGVARCHAR:
						field.value = resultSet.getString(field.name);
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
		 * @param statement
		 * @param fields
		 * @throws SQLException
		 */
		protected void setQueryValuesFromFields(PreparedStatement statement, Map<String, DBField> fields) throws SQLException {
			int index = 1;
			for (DBField field: fields.values()) {
				switch (field.settings.type) {
				case Types.DATE:
					statement.setDate(index, (Date) field.value);
					break;
				case Types.LONGVARCHAR:
					statement.setString(index, (String) field.value);
					break;
				case Types.OTHER:
					statement.setObject(index, field.value, java.sql.Types.OTHER);
					break;
				default:
					statement.setObject(index, field.value);
					break;
				}
				index++;
			}
		}
	}
	
	/**
	 * @author Fraser LeFevre
	 *
	 */
	public class File extends Record {
		public final static String TABLENAME = "files";

		@Override
		protected String tablename() {
			return File.TABLENAME;
		}
		
		{
			this.setFields(List.of(
				new DBField() {{
					name = "user_id";
					settings = new DBFieldSettings() {{
						type = Types.OTHER;
						objectCastType = UUID.class;
						required = true;
						insert = true;
					}};
				}},
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
				}}).stream().collect(Collectors.toMap(f -> f.name, f -> f)));
		}
		
		/**
		 * 
		 */
		public File() { }
		
		/**
		 * @param id
		 */
		public File(String id) {
			this.setId(id);
			this.getById();
		}
		
		/**
		 * @param userId
		 * @param filename
		 * @param title
		 * @param description
		 */
		public File(String userId, String filename, String title, String description) {
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
		 * @param title
		 */
		public void setTitle(String title) {
			this.fields.get("title").value = title;
		}
		
		/**
		 * @return
		 */
		public String getTitle() {
			return (String) this.fields.get("title").value;
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
		public String getDescription() {
			return (String) this.fields.get("description").value;
		}
		
		/**
		 * @return
		 */
		public Date getDateAdded() {
			return (Date) this.fields.get("date_added").value;
		}
	}
	
	/**
	 * @author Fraser LeFevre
	 *
	 */
	public class User extends Record {
		public final static String TABLENAME = "users";

		@Override
		protected String tablename() {
			return User.TABLENAME;
		}
		
		{
			this.setFields(List.of(
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
					}}).stream().collect(Collectors.toMap(f -> f.name, f -> f)));
		}
		
		/**
		 * 
		 */
		public User() { }
		
		/**
		 * @param id
		 */
		public User(String id) {
			this.setId(id);
			this.getById();
		}
		
		/**
		 * @param username
		 * @param fName
		 * @param lName
		 */
		public User(String username, String fName, String lName) {
			this.fields.get("username").value = username;
			this.fields.get("f_name").value = fName;
			this.fields.get("l_name").value = lName;
		}
		
		/**
		 * @param userResult
		 * @throws SQLException
		 */
		public User(ResultSet userResult) throws SQLException {
			this.fromResultSet(userResult);
		}
		
		/**
		 * @return
		 */
		public boolean getFromUsername() {
			return this.getByKey(List.of("username"));
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
				if (this.id.equals(usr.id)) return true;
				else return false;
			} catch (ClassCastException e) {
				return false;
			}
		}
	}
	
	/**
	 * @author Fraser LeFevre
	 *
	 */
	public class SharedFile extends Record {
		public final static String TABLENAME = "shared_files";

		@Override
		protected String tablename() {
			return SharedFile.TABLENAME;
		}
		
		{
			this.setFields(List.of(
					new DBField() {{
						name = "user_id";
						settings = new DBFieldSettings() {{
							type = Types.OTHER;
							objectCastType = UUID.class;
							required = true;
							insert = true;
						}};
					}},
					new DBField() {{
						name = "file_id";
						settings = new DBFieldSettings() {{
							type = Types.OTHER;
							objectCastType = UUID.class;
							required = true;
							insert = true;
						}};
					}}).stream().collect(Collectors.toMap(f -> f.name, f -> f)));
		}
		
		/**
		 * 
		 */
		private SharedFile() { }
		
		/**
		 * @param id
		 */
		public SharedFile(String id) {
			this.setId(id);
			this.getById();
		}
		
		/**
		 * @param userId
		 * @param fileId
		 */
		public SharedFile(String userId, String fileId) {
			this.setUserId(userId);
			this.setFileId(fileId);
		}

		/**
		 * @return
		 */
		public boolean getByKey() {
			return this.getByKey(List.of("user_id", "file_id"));
		}
		
		/**
		 * @param fileId
		 */
		protected void setFileId(String fileId) {
			this.fields.get("file_id").value = UUID.fromString(fileId);
		}
		
		/**
		 * @return
		 */
		public String getFileId() {
			return this.fields.get("file_id").value.toString();
		}

		/**
		 * @param userId
		 */
		protected void setUserId(String userId) {
			this.fields.get("user_id").value = UUID.fromString(userId);
		}

		/**
		 * @return
		 */
		public String getUserId() {
			return this.fields.get("user_id").value.toString();
		}
	}
	
	/**
	 * @author Fraser LeFevre
	 *
	 */
	public class UserFileAccess extends SharedFile {
		public final static String VIEWNAME = "file_access";

		@Override
		protected String tablename() {
			return UserFileAccess.VIEWNAME;
		}
		
		protected User owner;
		protected User user;
		protected File file;
		
		{
			this.setFields(List.of(
					new DBField() {{
						name = "owner_id";
						settings = new DBFieldSettings() {{
							type = Types.OTHER;
							objectCastType = UUID.class;
							required = true;
							insert = true;
						}};
					}},
					new DBField() {{
						name = "user_id";
						settings = new DBFieldSettings() {{
							type = Types.OTHER;
							objectCastType = UUID.class;
							required = true;
							insert = true;
						}};
					}},
					new DBField() {{
						name = "file_id";
						settings = new DBFieldSettings() {{
							type = Types.OTHER;
							objectCastType = UUID.class;
							required = true;
							insert = true;
						}};
					}}).stream().collect(Collectors.toMap(f -> f.name, f -> f)));
		}
		
		/**
		 * @param ufaResult
		 * @throws SQLException
		 */
		public UserFileAccess(ResultSet ufaResult) throws SQLException {
			this.fromResultSet(ufaResult);
			this.owner = new User(this.getOwnerId());
			this.user = new User(this.getUserId());
			this.file = new File(this.getFileId());
		}

		/**
		 *
		 */
		@Override
		public String getId() {
			throw new OperationNotPermitted("UserFileAccess view has no id");
		}

		/**
		 *
		 */
		@Override
		public boolean deleteById() {
			throw new OperationNotPermitted("UserFileAccess is a view. DELETE is invalid");
		}

		/**
		 *
		 */
		@Override
		public boolean deleteByKey(List<String> keyNames) {
			throw new OperationNotPermitted("UserFileAccess is a view. DELETE is invalid");
		}

		/**
		 *
		 */
		@Override
		public boolean save() {
			throw new OperationNotPermitted("UserFileAccess is a view. INSERT/UPDATE is invalid");
		}

		/**
		 * @return
		 */
		public String getOwnerId() {
			return this.fields.get("owner_id").value.toString();
		}

		/**
		 * @return the owner
		 */
		public User getOwner() {
			return owner;
		}

		/**
		 * @return the user
		 */
		public User getUser() {
			return user;
		}

		/**
		 * @return the file
		 */
		public File getFile() {
			return file;
		}
	}
	
	/**
	 * @author Fraser LeFevre
	 *
	 */
	public class FileUserAccess {
		protected File file = null;
		protected List<User> users = null;
		
		/**
		 * @param fileId
		 */
		public FileUserAccess(String fileId) {
			try (Connection conn = getConnection()) {
				PreparedStatement fuaStatement = conn.prepareStatement(
						"SELECT * FROM file_access WHERE file_id = ?");
				fuaStatement.setObject(1, UUID.fromString(fileId), Types.OTHER);
				
				List<FileUserAccess> fuaList = new ArrayList<FileUserAccess>();
				ResultSet fuaResult = fuaStatement.executeQuery();
				
				while(fuaResult.next()) {
					fuaList.add(new FileUserAccess(fuaResult));
				}
				
				FileUserAccess fua = new FileUserAccess(fuaList);
				this.file = fua.file;
				this.users = fua.users;
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		/**
		 * @param fuaResult
		 * @throws SQLException
		 */
		private FileUserAccess(ResultSet fuaResult) throws SQLException {
			if (fuaResult == null) throw new IllegalArgumentException("ResultSet must not be null");
			
			UserFileAccess ufa = new UserFileAccess(fuaResult);
			this.file = ufa.file;
			this.users = List.of(ufa.user);
		}
		
		/**
		 * @param fuas
		 */
		private FileUserAccess(List<FileUserAccess> fuas) {
			if (fuas == null || fuas.isEmpty()) return;
			this.file = fuas.get(0).file;
			this.users = fuas.get(0).users;
			this.merge(fuas.subList(1, fuas.size()));
		}
		
		/**
		 * @param fuas
		 */
		private void merge(List<FileUserAccess> fuas) {
			fuas = (fuas == null ? List.of() : fuas);
			if (fuas.stream().anyMatch(f -> !f.file.id.equals(this.file.id))) throw new IllegalArgumentException("Unable to merge FileUserAccess objects with different file ids");

			this.users = Stream.of(List.of(this), fuas)
					.flatMap(list -> list.stream())
					.map(fua -> fua.users).flatMap(users -> users.stream())
					.distinct().collect(Collectors.toList());
		}

		/**
		 * @return the file
		 */
		public File getFile() {
			return file;
		}

		/**
		 * @return the userIds
		 */
		public String[] getUserIds() {
			return this.users.stream().map(user -> user.id.toString()).toArray(String[]::new);
		}
	}
}
