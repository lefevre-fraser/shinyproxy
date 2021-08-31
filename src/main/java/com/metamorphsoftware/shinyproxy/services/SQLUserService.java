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

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Service;

import com.metamorphsoftware.shinyproxy.services.SQLService.File;
import com.metamorphsoftware.shinyproxy.services.SQLService.FilePermission;
import com.metamorphsoftware.shinyproxy.services.SQLService.Record;
import com.metamorphsoftware.shinyproxy.services.SQLService.Record.DBWhereClause.DBWhereClauseBuilder;
import com.metamorphsoftware.shinyproxy.services.SQLService.Record.DBWhereComparator;
import com.metamorphsoftware.shinyproxy.services.SQLService.Record.DBWhereLinker;
import com.metamorphsoftware.shinyproxy.services.SQLService.Record.RecordNotFoundException;
import com.metamorphsoftware.shinyproxy.services.SQLService.User;
import com.metamorphsoftware.shinyproxy.services.SQLService.UserFilePermission;

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
	 * @throws RecordNotFoundException 
	 */
	public String getUserID() {
		return getUser().getId().toString();
	}
	
	/**
	 * @return
	 * @throws RecordNotFoundException 
	 */
	public User getUser() {
		return getUser(userService.getCurrentUserId());
	}
	
	/**
	 * @param username
	 * @return
	 * @throws RecordNotFoundException 
	 */
	private User getUser(String username) {
//		User user = sqlService.new User();
		User user = new User();
		user.setUsername(username);
		try {
			user.getFromUsername();
		} catch (RecordNotFoundException e) {
			user.insert();
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
	public List<User> getUserList() {
		return Record.find(new User(), null);
	}
	
//	/**
//	 * @param ignoreAccessControl
//	 * @return
//	 */
//	public UserFileAccess[] getUserFileAccess(boolean ignoreAccessControl) {
//		return getUserFileAccess(getUserID(), userService.getCurrentAuth() instanceof AnonymousAuthenticationToken, ignoreAccessControl);
//	}
	
	public List<UserFilePermission> getUserFileAccess(boolean ignoreAccessControl) {
		return getUserFileAccess(getUser(), userService.getCurrentAuth() instanceof AnonymousAuthenticationToken, ignoreAccessControl);
	}

	/**
	 * @param userId
	 * @param anonymousAccess
	 * @param ignoreAccessControl
	 * @return
	 */
//	private UserFileAccess[] getUserFileAccess(String userId, boolean anonymousAccess, boolean ignoreAccessControl) {
//		List<UserFileAccess> ufaList = new ArrayList<UserFileAccess>();
//		
//		try (Connection conn = sqlService.getConnection()) {
//			PreparedStatement userFilesStatement = null;
//			if (ignoreAccessControl) {
//				userFilesStatement = conn.prepareStatement("SELECT * FROM file_access");
//			} else {
//				userFilesStatement = conn.prepareStatement("SELECT * FROM user_file_access(?)");
//				if (anonymousAccess || userId == null) userFilesStatement.setNull(1, Types.OTHER);
//				else userFilesStatement.setObject(1, UUID.fromString(userId));
//			}
//
//			ResultSet ufaResult = userFilesStatement.executeQuery();
//			while (ufaResult.next()) {
//				ufaList.add(sqlService.new UserFileAccess(ufaResult));
//			}
//			ufaResult.close();
//			
//			conn.close();
//		} catch (SQLException e) {
//			throw new RuntimeException("Error retrieving user's file list", e);
//		}
//		
//		return ufaList.toArray(UserFileAccess[]::new);
//	}
	
	private List<UserFilePermission> getUserFileAccess(User user, boolean anonymousAccess, boolean ignoreAccessControl) {
		UserFilePermission ufp = new UserFilePermission();
		if (ignoreAccessControl) {
			return UserFilePermission.<UserFilePermission>find(ufp, null);
		}
		
		if (anonymousAccess) {
			return UserFilePermission.<UserFilePermission>find(ufp, DBWhereClauseBuilder.Builder().withRecord(ufp).withWhereList()
					.addWhere().withColumn("user_id").withComparator(DBWhereComparator.IS).withValue(null).addToWhereList()
					.linker(DBWhereLinker.AND)
					.addWhere().withColumn("file_permission_id").withValue(FilePermission.fromTitle("ANONYMOUS").getId()).addToWhereList()
					.addListToClause().build());
		}
		
		return UserFilePermission.<UserFilePermission>find(ufp, DBWhereClauseBuilder.Builder().withRecord(ufp).withWhereList()
				.addWhere().withColumn("user_id").withValue(user.<UUID>getFieldValue("id")).addToWhereList()
				.addListToClause().build());
//			return ufp.find( 
//					Record.createDBWhereClause(null, 
//							List.of(Record.createDBWhere(ufp, "id", DBWhereComparator.EQ, user.<UUID>getFieldValue("id"))))).toArray(UserFilePermission[]::new);
	}
}
