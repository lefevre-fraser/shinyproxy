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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.springframework.stereotype.Service;

import com.metamorphsoftware.shinyproxy.services.SQLService.File;
import com.metamorphsoftware.shinyproxy.services.SQLService.User;
import com.metamorphsoftware.shinyproxy.services.SQLService.UserFileAccess;

import eu.openanalytics.containerproxy.service.UserService;

/**
 * @author Fraser LeFevre
 *
 */
@Service
public class SQLUserService {
	@Inject
	protected UserService userService;

	@Inject
	protected SQLService sqlService;
	
	/**
	 * @return
	 */
	public String getUserID() {
		return getUser().getId();
	}
	
	/**
	 * @return
	 */
	public User getUser() {
		return getUser(userService.getCurrentUserId());
	}
	
	/**
	 * @param username
	 * @return
	 */
	private User getUser(String username) {
		User user = sqlService.new User();
		user.setUsername(username);
		user.getFromUsername();
		if (user.getId() == null) {
			user.save();
		}
		return user;
	}
	
	/**
	 * @param file
	 * @param user
	 * @return
	 */
	private boolean isFileOwner(File file, User user) {
		return file.getUserId().equals(user.getId());
	}
	
	/**
	 * @param file
	 * @return
	 */
	public boolean isFileOwner(File file) {
		return isFileOwner(file, getUser());
	}

	/**
	 * @return
	 */
	public User[] getUserList() {
		List<User> users = new ArrayList<User>();
		
		try (Connection conn = sqlService.getConnection()) {
			PreparedStatement userIdStatement = conn.prepareStatement(
					"SELECT * FROM users");
			ResultSet userListResult = userIdStatement.executeQuery();
			
			while (userListResult.next()) { 
				users.add(sqlService.new User(userListResult));
			}
			
			conn.close();
		} catch (SQLException e) {
			throw new RuntimeException("Error retrieving user list", e);
		}
		
		return users.toArray(User[]::new);
	}
	
	/**
	 * @param ignoreAccessControl
	 * @return
	 */
	public UserFileAccess[] getUserFileAccess(boolean ignoreAccessControl) {
		return getUserFileAccess(getUserID(), ignoreAccessControl);
	}

	/**
	 * @param userId
	 * @param ignoreAccessControl
	 * @return
	 */
	private UserFileAccess[] getUserFileAccess(String userId, boolean ignoreAccessControl) {
		List<UserFileAccess> ufaList = new ArrayList<UserFileAccess>();
		
		try (Connection conn = sqlService.getConnection()) {
			PreparedStatement userFilesStatement = null;
			if (ignoreAccessControl) {
				userFilesStatement = conn.prepareStatement("SELECT * FROM file_access");
			} else if (userId != null) {
				userFilesStatement = conn.prepareStatement("SELECT * FROM user_file_access(?)");
				userFilesStatement.setObject(1, UUID.fromString(userId));
			}
			
			if (ignoreAccessControl || userId != null) {
				ResultSet ufaResult = userFilesStatement.executeQuery();
				while (ufaResult.next()) {
					ufaList.add(sqlService.new UserFileAccess(ufaResult));
				}
				ufaResult.close();
			}
			
			conn.close();
		} catch (SQLException e) {
			throw new RuntimeException("Error retrieving user's file list", e);
		}
		
		return ufaList.toArray(UserFileAccess[]::new);
	}
}
