/**
 * ShinyProxy-Visualizer
 * 
 * Copyright (C) 2016-2021 Open Analytics
 * 
 * ===========================================================================
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 * 
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package com.metamorphsoftware.shinyproxy.services;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import org.apache.commons.dbcp2.BasicDataSource;

@Service
public class SQLService {
	
	public static class Record {
		protected String id;
		
		public Record(String id) {
			this.id = id;
		}

		/**
		 * @return the id
		 */
		public String getId() {
			return id;
		}
	}
	
	public static class User extends Record {
		protected String username;
		protected String fName;
		protected String lName;
		
		public User(String id, String username, String fName, String lName) {
			super(id);
			this.username = username;
			this.fName = fName;
			this.lName = lName;
		}
		
		public User(String id) {
			this(id, null, null, null);
		}
		
		public User() {
			this(null, null, null, null);
		}
		
		public User(ResultSet userResult) throws SQLException {
			this(userResult.getString("id"), userResult.getString("username"),
					userResult.getString("f_name"), userResult.getString("l_name"));
		}

		public String getId() {
			return id;
		}

		public String getUsername() {
			return username;
		}

		public String getfName() {
			return fName;
		}

		public String getlName() {
			return lName;
		}
	}
	
	public static class File extends Record {
		protected String userId;
		protected String filename;
		protected String description;
		protected Date dateAdded;
		
		public File(String id, String userId, String filename, String description, Date dateAdded) {
			super(id);
			this.userId = userId;
			this.filename = filename;
			this.description = description;
			this.dateAdded = dateAdded;
		}
		
		public File(String id) {
			this(id, null, null, null, null);
		}
		
		public File(ResultSet fileResult) throws SQLException {
			this(fileResult.getString("id"), fileResult.getString("user_id"), fileResult.getString("filename"),
					fileResult.getString("description"), fileResult.getDate("date_added"));
		}
	
		public static File fromSharedFileResult(ResultSet fileResult) throws SQLException {
			return new File(fileResult.getString("file_id"), fileResult.getString("owner_id"), fileResult.getString("filename"),
					fileResult.getString("description"), fileResult.getDate("date_added"));
		}

		public String getId() {
			return id;
		}

		public String getFilename() {
			return filename;
		}

		public String getDescription() {
			return description;
		}

		public Date getDateAdded() {
			return dateAdded;
		}
	}
	
	public static class UserFileAccess {
		protected String sharedUserId;
		protected String ownerId;
		protected String fileId;
		protected String filename;
		protected String description;
		protected Date dateAdded;
		
		public UserFileAccess(String sharedUserId, String ownerId, String fileId, 
				String filename, String description, Date dateAdded) {
			this.sharedUserId = sharedUserId;
			this.ownerId = ownerId;
			this.fileId = fileId;
			this.filename = filename;
			this.description = description;
			this.dateAdded = dateAdded;
		}
		
		public UserFileAccess(ResultSet ufaResult) throws SQLException {
			this.sharedUserId = ufaResult.getString("shared_user_id");
			this.ownerId = ufaResult.getString("owner_id"); 
			this.fileId = ufaResult.getString("file_id");
			this.filename = ufaResult.getString("filename"); 
			this.description = ufaResult.getString("description"); 
			this.dateAdded = ufaResult.getDate("date_added");
		}

		public String getSharedUserId() {
			return sharedUserId;
		}

		public String getOwnerId() {
			return ownerId;
		}

		public String getFileId() {
			return fileId;
		}

		public String getFilename() {
			return filename;
		}

		public String getDescription() {
			return description;
		}

		public Date getDateAdded() {
			return dateAdded;
		}
	}
	
	private BasicDataSource pool;
	
	public SQLService() {
		pool = new BasicDataSource();
		pool.setUrl("jdbc:postgresql://localhost:8080/postgres");
		pool.setUsername("postgres");
		pool.setPassword("supersecure");
		pool.setMaxTotal(10);
	}
	
	public Connection getConnection() throws SQLException {
		return pool.getConnection();
	}
	
	public User getUser(String username) {
		User user = new User();
		if (username == null) return user;
		
		try (Connection conn = getConnection()) {
			PreparedStatement userIdStatement = conn.prepareStatement(
					"SELECT * FROM users WHERE username = ?");
			userIdStatement.setString(1, username);
			ResultSet userResult = userIdStatement.executeQuery();
			
			if (userResult.next()) { 
				user = new User(userResult);
			} else {
				return addUser(new User(null, username, null, null));
			}
			
			conn.close();
		} catch (SQLException e) {
			System.out.println("Connection Failure");
			e.printStackTrace();
		}
		
		return user;
	}
	
	public User addUser(User user) {
		User newUser = null;
		
		try (Connection conn = getConnection()) {
			PreparedStatement addUserStatement = conn.prepareStatement(
					"INSERT INTO users (username) VALUES (?) RETURNING *");
			addUserStatement.setString(1, user.username);
			ResultSet addUserResult = addUserStatement.executeQuery();
			
			if (addUserResult.next()) {
				newUser = new User(addUserResult);
			}
			
			conn.close();
		} catch (SQLException e) {
			System.out.println("Connection Failure");
			e.printStackTrace();
		}
		
		return newUser;
	}
	
	public File addFile(File file) {
		File newFile = null;
		
		try (Connection conn = getConnection()) {
			PreparedStatement addFileStatement = conn.prepareStatement(
					"INSERT INTO files (user_id, filename, description) VALUES (?::uuid, ?, ?) RETURNING *");
			addFileStatement.setString(1, file.userId);
			addFileStatement.setString(2, file.filename);
			addFileStatement.setString(3, file.description);
			ResultSet addFileResult = addFileStatement.executeQuery();
			
			if (addFileResult.next()) {
				newFile =File.fromSharedFileResult(addFileResult);
			}
			
			conn.close();
		} catch (SQLException e) {
			System.out.println("Connection Failure");
			e.printStackTrace();
		}
		
		return newFile;
	}
	
	public List<UserFileAccess> getUserFileAccess(String userId, boolean ignoreAccessControl) {
		List<UserFileAccess> ufaList = new ArrayList<UserFileAccess>();
		
		try (Connection conn = getConnection()) {
			PreparedStatement userFilesStatement = null;
			if (ignoreAccessControl) {
				userFilesStatement = conn.prepareStatement("SELECT * FROM file_access");
			} else if (userId != null) {
				userFilesStatement = conn.prepareStatement("SELECT * FROM user_file_access(?::uuid)");
				userFilesStatement.setString(1, userId);
			}
			
			if (ignoreAccessControl || userId != null) {
				ResultSet ufaResult = userFilesStatement.executeQuery();
				while (ufaResult.next()) {
					ufaList.add(new UserFileAccess(ufaResult));
				}
			}
			
			conn.close();
		} catch (SQLException e) {
			System.out.println("Connection Failure");
			e.printStackTrace();
		}
		
		return ufaList;
	}

	public File deleteFile(File file) {
		return (File) deleteFromTableById("files", file, File::new);
	}

	public User deleteUser(User user) {
		return (User) deleteFromTableById("users", user, User::new);
	}
	
	public interface SQLExceptionFunction {
		public Record apply(ResultSet set) throws SQLException;
	}
	
	protected Record deleteFromTableById(String tablename, Record record, SQLExceptionFunction constructorFromResult) {
		Record deletedRecord = null;
		if (record == null) return null;
		
		try (Connection conn = getConnection()) {
			PreparedStatement deleteObjectStatement = conn.prepareStatement(
					String.format("DELETE FROM %s WHERE id = ?::uuid RETURNING *", tablename));
			deleteObjectStatement.setString(1, record.getId());
			ResultSet deleteObjectResult = deleteObjectStatement.executeQuery();
			
			if (deleteObjectResult.next()) {
				deletedRecord = constructorFromResult.apply(deleteObjectResult);
			}
			
			conn.close();
		} catch (SQLException e) {
			System.out.println("Connection Failure");
			e.printStackTrace();
		}
		
		return deletedRecord;
	}
}
