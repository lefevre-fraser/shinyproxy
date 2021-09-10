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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Service;

import com.metamorphsoftware.shinyproxy.services.SQLService.File;
import com.metamorphsoftware.shinyproxy.services.SQLService.FilePermission;
import com.metamorphsoftware.shinyproxy.services.SQLService.Record;
import com.metamorphsoftware.shinyproxy.services.SQLService.Record.DBWhereClause.DBWhereClauseBuilder;
import com.metamorphsoftware.shinyproxy.services.SQLService.UserFilePermission.UserFilePermissionBuilder;
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
			if (!(userService.getCurrentAuth() instanceof AnonymousAuthenticationToken)) {
				user.insert();
			}
		}
		return user;
	}
	
	/**
	 * @param file
	 * @param user
	 * @return
	 */
	private boolean isFileOwner(File file, User user) {
		List<UserFilePermission> userfpList = UserFilePermission.fromFileId(file.getId());
		if (userfpList == null) return false;
		
		Long ownerPermissionId = FilePermission.fromTitle("OWNER").getId();
		List<UUID> ownerIdList = userfpList.stream().filter(userfp -> userfp.getFilePermissionId().equals(ownerPermissionId))
				.map(userfp -> userfp.getUserId()).collect(Collectors.toList());
		
		if (ownerIdList.contains(user.getId())) return true;
		return false;
	}
	
	/**
	 * @param file
	 * @return
	 */
	public boolean isFileOwner(File file) {
		return isFileOwner(file, getUser());
	}
	
	public void anonymousFileShare(File file, Boolean allowAnonymous) {
		UserFilePermission anonymousAccess = UserFilePermission.findOne(new UserFilePermission(), DBWhereClauseBuilder.Builder().withRecord(new UserFilePermission()).withWhereList()
				.addWhere().withColumn("file_id").withValue(file.getId()).addToWhereList()
				.linker(DBWhereLinker.AND)
				.addWhere().withColumn("file_permission_id").withValue(FilePermission.fromTitle("ANONYMOUS").getId()).addToWhereList()
				.addListToClause().build());
		
		if (allowAnonymous) {
			if (anonymousAccess == null) {
				UserFilePermissionBuilder.Builder().withFileId(file.getId())
					.withUserId(null).withFilePermissionId(FilePermission.fromTitle("ANONYMOUS").getId())
					.build().insert();
			}
		} else if (anonymousAccess != null) {
			anonymousAccess.delete();
		}
	}
	
	public boolean hasAnonymousAccess(File file) {
		UserFilePermission anonymousAccess = UserFilePermission.findOne(new UserFilePermission(), DBWhereClauseBuilder.Builder().withRecord(new UserFilePermission()).withWhereList()
				.addWhere().withColumn("file_id").withValue(file.getId()).addToWhereList()
				.linker(DBWhereLinker.AND)
				.addWhere().withColumn("file_permission_id").withValue(FilePermission.fromTitle("ANONYMOUS").getId()).addToWhereList()
				.addListToClause().build());
		
		return (anonymousAccess == null ? false : true);
	}

	/**
	 * @return
	 */
	public List<User> getUserList() {
		return Record.find(new User(), null);
	}
	
	public List<UserFilePermission> getUserFileAccess(boolean ignoreAccessControl, boolean includeAnonymousFiles) {
		return getUserFileAccess(getUser(), userService.getCurrentAuth() instanceof AnonymousAuthenticationToken, includeAnonymousFiles, ignoreAccessControl);
	}
	
	private List<UserFilePermission> getUserFileAccess(User user, boolean anonymousAccess, boolean includeAnonymousFiles, boolean ignoreAccessControl) {
		UserFilePermission ufp = new UserFilePermission();
		List<UserFilePermission> uniqueFileList = new ArrayList<UserFilePermission>();
		
		if (ignoreAccessControl) {
			List<UserFilePermission> ufpList = ufp.find(null); // UserFilePermission.<UserFilePermission>find(ufp, null);
			return (ufpList == null ? new ArrayList<UserFilePermission>() : ufpList);
//			for (UserFilePermission userfp: (ufpList == null ? new ArrayList<UserFilePermission>() : ufpList)) {
//				if (!uniqueFileList.contains(userfp)) {
//					
//				}
//			}
//			uniqueFileList.addAll((ufpList == null ? new ArrayList<UserFilePermission>() : ufpList));
		}
		
		else {
			List<UserFilePermission> userfpList = new ArrayList<UserFilePermission>();
			
			if (!anonymousAccess) {
				List<UserFilePermission> ufpList = ufp.find(DBWhereClauseBuilder.Builder().withRecord(ufp).withWhereList()
						.addWhere().withColumn("user_id").withValue(user.<UUID>getFieldValue("id")).addToWhereList()
						.addListToClause().build());
				if (ufpList != null) userfpList.addAll(ufpList);
			}
			
			if (anonymousAccess || includeAnonymousFiles) {
				List<UserFilePermission> ufpList = ufp.find(DBWhereClauseBuilder.Builder().withRecord(ufp).withWhereList()
	//					.addWhere().withColumn("user_id").withComparator(DBWhereComparator.IS).withValue(null).addToWhereList()
	//					.linker(DBWhereLinker.AND)
						.addWhere().withColumn("file_permission_id").withValue(FilePermission.fromTitle("ANONYMOUS").getId()).addToWhereList()
						.addListToClause().build());
				if (ufpList != null) {
					List<UUID> fileIds = userfpList.stream().map(usfp -> usfp.getFileId()).collect(Collectors.toList());
					for (UserFilePermission userfp: ufpList) {
						if (!fileIds.contains(userfp.getFileId())) {
							userfpList.add(userfp);
						}
					}
				}
//				if (ufpList != null) userfpList.addAll(ufpList);
			}

			return userfpList;
		}
	}
	
	private List<UserFilePermission> uniqueFileAccessList(@NotNull List<UserFilePermission> userfpList) {
		List<UserFilePermission> uniqeFileAccessList = new ArrayList<UserFilePermission>();
		
		Map<UUID, List<UserFilePermission>> userfpListGrouped = userfpList.stream().collect(Collectors.groupingBy(UserFilePermission::getFileId));
		Map<Long, FilePermission> filePermissions = FilePermission.<FilePermission>find(new FilePermission(), null).stream().collect(Collectors.toMap(FilePermission::getId, filep -> filep));
		for (Map.Entry<UUID, List<UserFilePermission>> userfp: userfpListGrouped.entrySet()) {
			if (userfp.getValue().size() == 1) uniqeFileAccessList.add(userfp.getValue().get(0));
			
			else {
				for (Long id: filePermissions.keySet()) {
					List<UserFilePermission> ufp = userfp.getValue().stream().filter(usfp -> usfp.getFilePermissionId().equals(id)).collect(Collectors.toList());
					
				}
			}
		}
		
		return uniqeFileAccessList;
	}
}
