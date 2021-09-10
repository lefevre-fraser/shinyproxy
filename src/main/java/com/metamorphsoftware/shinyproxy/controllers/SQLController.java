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
package com.metamorphsoftware.shinyproxy.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;
import org.thymeleaf.spring5.SpringTemplateEngine;

import com.metamorphsoftware.shinyproxy.datatypes.Pair;
import com.metamorphsoftware.shinyproxy.datatypes.Triple;
import com.metamorphsoftware.shinyproxy.services.SQLService.File;
import com.metamorphsoftware.shinyproxy.services.SQLService.File.FileBuilder;
import com.metamorphsoftware.shinyproxy.services.SQLService.FilePermission;
import com.metamorphsoftware.shinyproxy.services.SQLService.Record;
import com.metamorphsoftware.shinyproxy.services.SQLService.Record.DBWhereClause.DBWhereClauseBuilder;
import com.metamorphsoftware.shinyproxy.services.SQLService.Record.DBWhereLinker;
import com.metamorphsoftware.shinyproxy.services.SQLService.User;
import com.metamorphsoftware.shinyproxy.services.SQLService.UserFilePermission;
import com.metamorphsoftware.shinyproxy.services.SQLService.UserFilePermission.UserFilePermissionBuilder;

import eu.openanalytics.containerproxy.model.runtime.Proxy;

/**
 * @author Fraser LeFevre
 *
 */
@Controller
public class SQLController extends BaseController {
	
	@Inject
	SpringTemplateEngine engine;
	
	@RequestMapping("/dbstatus")
	protected Object status(ModelMap map, HttpServletRequest request) {
		prepareMap(map, request);
		
		map.put("message", "SQL Controller");
		map.put("stats", sqlService.getStats());

		return "dbstatus";
    }
	
	@RequestMapping(value="/edit", method=RequestMethod.GET)
	protected Object edit(ModelMap map, HttpServletRequest request) {
		prepareMap(map, request);
		
		List<UserFilePermission> userfpList = sqlUserService.getUserFileAccess(false, false);
		List<Triple<List<UUID>, UserFilePermission, File>> apps = userfpList.stream().map(userfp -> {
			File file = File.fromId(File.class, userfp.getFileId());
			List<UserFilePermission> userfpOwnerList = UserFilePermission.fromFileIdAndPermissionId(file.getId(), FilePermission.fromTitle("OWNER").getId());
			List<User> userOwnerList = userfpOwnerList.stream().map(ufp -> User.fromId(User.class, ufp.getUserId())).collect(Collectors.toList());
			List<UUID> uuidOwnerList = userOwnerList.stream().map(user -> user.getId()).collect(Collectors.toList());
			return new Triple<List<UUID>, UserFilePermission, File>(uuidOwnerList, userfp, file);
		}).collect(Collectors.toList());
		
		Long anonymousId = FilePermission.fromTitle("ANONYMOUS").getId();
		Long linkshareId = FilePermission.fromTitle("LINK_SHARE").getId();
		List<Boolean> sharelink = apps.stream().map(app -> app.second).map(userfp -> {
			List<UserFilePermission> ufp = Record.<UserFilePermission>find(new UserFilePermission(), DBWhereClauseBuilder.Builder().withRecord(new UserFilePermission())
					.withClauseList()
						.addClause().withWhereList()
							.addWhere().withColumn("file_id").withValue(userfp.getFileId()).addToWhereList()
							.addListToClause().addClauseToList()
						.linker(DBWhereLinker.AND)
						.addClause().withWhereList()
							.addWhere().withColumn("file_permission_id").withValue(FilePermission.fromTitle("ANONYMOUS").getId()).addToWhereList()
							.linker(DBWhereLinker.OR)
							.addWhere().withColumn("file_permission_id").withValue(FilePermission.fromTitle("LINK_SHARE").getId()).addToWhereList()
						.addListToClause().addClauseToList()
					.addListToClause().build());

			if (!ufp.isEmpty()) {
				return true;
			}
			return false;
		}).collect(Collectors.toList());
		
		map.put("apps", apps);
		map.put("displayShareLink", sharelink);
		map.put("userId", sqlUserService.getUser().getId());
		
		return "index";
	}
	
	@RequestMapping(value="/addfile", method=RequestMethod.GET)
	protected Object addFileGET(ModelMap map, HttpServletRequest request) {
		prepareMap(map, request);
		return "addfile";
	}
	
	@ResponseBody
	@RequestMapping(value="/addfile", method=RequestMethod.POST)
	protected MessageResponse addFilePOST(@RequestParam("zipfile") MultipartFile zipfile,
			@RequestParam("title") String title, @RequestParam("description") String description, HttpServletRequest request) {
		Assert.hasText(title, "No Title Provided");
		
		String filename = FilenameUtils.getName(zipfile.getOriginalFilename());
		File file = FileBuilder.Builder()
				.withFilename(filename)
				.withTitle(title)
				.withDescription(description)
				.build();//sqlService.new File(sqlUserService.getUserID(), filename, title, description);
			
		try {
			if (file.insert()) {
				UserFilePermission userfp = UserFilePermissionBuilder.Builder().withFileId(file.getId()).withUserId(sqlUserService.getUser().getId())
						.withFilePermissionId(FilePermission.fromTitle("OWNER").getId()).build();
				if (userfp.insert()) {
					if (fileHandlingService.store(zipfile, file.getId().toString())) {
						if (fileHandlingService.copyArchiveData(file.getId().toString(), filename, sqlUserService.getUserID())) {
							return new MessageResponse();
						} else throw new Exception("Error extracting archive data for user");
					} else throw new Exception("Error uploading zip file for user");
				} else {
					file.delete();
					throw new Exception("Error setting file owner permissions");
				}
			} else throw new Exception("Error adding file to database");
		} catch (Exception e) {
			e.printStackTrace();
			file.delete();
		}
		
		return new MessageResponse(String.format("Failed to add file: %s", filename));
	}
	
	@GetMapping(value="/sharefile/{fileId}")
	protected Object shareFileGET(@PathVariable("fileId") String fileId,
			ModelMap map, HttpServletRequest request, HttpServletResponse response) {
		UUID fileUUID = UUID.fromString(fileId);
		prepareMap(map, request);
		
		if (!sqlUserService.isFileOwner(File.fromId(File.class, fileUUID)/*sqlService.new File(fileId)*/)) {
			throw new RuntimeException("Unauthorized to share this file");
		}
		
//		FileUserAccess fua = sqlService.new FileUserAccess(fileId); // sqlService.getFileUserAccess(fileId);
		map.put("file", File.fromId(File.class, fileUUID));//fua.getFile());
		List<UserFilePermission> ownerList = UserFilePermission.find(new UserFilePermission(), DBWhereClauseBuilder.Builder().withRecord(new UserFilePermission()).withWhereList()
				.addWhere().withColumn("file_id").withValue(fileUUID).addToWhereList()
				.linker(DBWhereLinker.AND)
				.addWhere().withColumn("file_permission_id").withValue(FilePermission.fromTitle("OWNER").getId()).addToWhereList()
				.addListToClause().build());
		map.put("ownerList", (ownerList == null ? new ArrayList<UUID>() : ownerList.stream().map(userfp -> userfp.getUserId()).collect(Collectors.toList())));
		UserFilePermission anonymousAccess = UserFilePermission.findOne(new UserFilePermission(), DBWhereClauseBuilder.Builder().withRecord(new UserFilePermission()).withWhereList()
				.addWhere().withColumn("file_id").withValue(fileUUID).addToWhereList()
				.linker(DBWhereLinker.AND)
				.addWhere().withColumn("file_permission_id").withValue(FilePermission.fromTitle("ANONYMOUS").getId()).addToWhereList()
				.addListToClause().build());
		map.put("anonymousAccess", (anonymousAccess == null ? false : true));

		List<UserFilePermission> ufpList = UserFilePermission.fromFileId(fileUUID);
		List<UUID> usersWithAccess = ufpList.stream().map(ufp -> ufp.getUserId()).filter(id -> id != null).map(id -> id).collect(Collectors.toList());
		@SuppressWarnings("unchecked")
		Pair<User, Boolean> userAccess[] = sqlUserService.getUserList().stream()
				.map(new Function<User, Pair<User, Boolean>>() {
					@Override
					public Pair<User, Boolean> apply(User user) {
						return new Pair<User, Boolean>(user, usersWithAccess.contains(user.getId()));
					}
			
				}).toArray(Pair[]::new);
		map.put("userAccess", userAccess);
		
		return "sharefile";
	}
	
	@ResponseBody
	@PostMapping(value="/sharefile")
	protected MessageResponse shareFilePOST(@RequestParam("fileId") String fileId, 
			@RequestParam(name="users[]", required=false) String users[],
			@RequestParam(name="allowAnonymous", required=false) Boolean allowAnonymous, HttpServletRequest request) {
		UUID fileUUID = UUID.fromString(fileId);
		File file = File.fromId(File.class, fileUUID);// sqlService.new File(fileId);
//		file.setAnonymousAccess(allowAnonymous);
//		file.update();
		
		sqlUserService.anonymousFileShare(file, allowAnonymous);
		
//		UserFilePermission anonymousAccess = UserFilePermission.findOne(new UserFilePermission(), DBWhereClauseBuilder.Builder().withRecord(new UserFilePermission()).withWhereList()
//				.addWhere().withColumn("file_id").withValue(fileUUID).addToWhereList()
//				.linker(DBWhereLinker.AND)
//				.addWhere().withColumn("file_permission_id").withValue(FilePermission.fromTitle("ANONYMOUS").getId()).addToWhereList()
//				.addListToClause().build());
//		if (allowAnonymous) {
//			if (anonymousAccess == null) {
//				UserFilePermissionBuilder.Builder().withFileId(fileUUID)
//					.withUserId(null).withFilePermissionId(FilePermission.fromTitle("ANONYMOUS").getId())
//					.build().insert();
//			}
//		} else {
//			anonymousAccess.delete();
//		}
		
		List<String> errorMessages = new ArrayList<String>();
		
		if (users == null) users = new String[0];
		
		List<UserFilePermission> ownerList = new UserFilePermission().find(DBWhereClauseBuilder.Builder().withRecord(new UserFilePermission()).withWhereList()
				.addWhere().withColumn("file_id").withValue(fileUUID).addToWhereList()
				.linker(DBWhereLinker.AND)
				.addWhere().withColumn("file_permission_id").withValue(FilePermission.fromTitle("OWNER").getId()).addToWhereList()
				.addListToClause().build());
		if (ownerList == null) ownerList = new ArrayList<UserFilePermission>();
		List<UUID> ownerUUIDList = ownerList.stream().map(owner -> owner.getUserId()).collect(Collectors.toList());
		
		List<String> newShareList = List.of(users);
		List<String> currentShareList = UserFilePermission.fromFileId(fileUUID).stream().filter(ufp -> !ownerUUIDList.contains(ufp.getUserId())).map(ufp -> ufp.getUserId()).filter(id -> id != null).map(id -> id.toString()).collect(Collectors.toList());
		//List.of(sqlService.new FileUserAccess(fileId).getUserIds())
//				.stream().filter(id -> !id.equals(file.getUserId())).collect(Collectors.toList());
		List<Proxy> activeFileProxies = proxyService.getProxies(proxy -> proxy.getId() == fileId, true);
		
		// Remove access from users not in the newShareList
		for (String id: currentShareList) {
			if (!newShareList.contains(id)) {
				User user = User.fromId(User.class, UUID.fromString(id));//sqlService.new User(id);
				if (activeFileProxies.stream().anyMatch(proxy -> proxy.getUserId() == user.getUsername())) {
					errorMessages.add(String.format("Error removing access from user: %s. User has active proxy using this file", user.getUsername()));
				} else {
//					SharedFile sharedFile = sqlService.new SharedFile(id, fileId);
					UserFilePermission userfp = UserFilePermission.fromKey(fileUUID, UUID.fromString(id));
					if (userfp != null) {
						if (!fileHandlingService.delete(fileId, id)) {
							errorMessages.add(String.format("Error deleting data from user: %s", user.getUsername()));
						} else if (!userfp.delete()) {
							errorMessages.add(String.format("Error removing access from user: %s", user.getUsername()));
						}
					}
				}
			}
		}
		
		// Share with any users who don't have access
		for (String id: newShareList) {
			if (!currentShareList.contains(id)) {
				User user = User.fromId(User.class, UUID.fromString(id));//sqlService.new User(id);
//				SharedFile sharedFile = sqlService.new SharedFile(id, fileId);
				UserFilePermission userfp = UserFilePermissionBuilder.Builder().withFileId(fileUUID).withUserId(UUID.fromString(id))
						.withFilePermissionId(FilePermission.fromTitle("VIEW").getId()).build();
				if (!userfp.insert()) {
					errorMessages.add(String.format("Error shareing access with user: %s", user.getUsername()));
				} else if (!fileHandlingService.copyArchiveData(fileId, file.getFilename(), id)) {
					errorMessages.add(String.format("Error shareing access with user: %s", user.getUsername()));
					userfp.delete();
				}
			}
		}
		
		// share with anonymous users
		if (sqlUserService.hasAnonymousAccess(file)/*file.hasAnonymousAccess()*/) {
			if (!fileHandlingService.copyArchiveData(fileId, file.getFilename(), "anonymous")) {
				errorMessages.add("Error sharing file anonymously");
				sqlUserService.anonymousFileShare(file, false);
//				file.setAnonymousAccess(false);
//				file.update();
			}
		} else {
			if (!fileHandlingService.delete(fileId, "anonymous")) {
				errorMessages.add("Error removing anonymous access");
				sqlUserService.anonymousFileShare(file, true);
//				file.setAnonymousAccess(true);
//				file.update();
			}
		}

		// if there were errors return an error response
		if (!errorMessages.isEmpty()) {
			return MessageResponse.error(String.join("\n", errorMessages));
		}
		
		return new MessageResponse();
	}
	
	@GetMapping(value="/linkshare/{fileId}")
	protected Object linkShare(@PathVariable("fileId") String fileId, ModelMap map, HttpServletRequest request, RedirectAttributes redirectAttributes) {
		File file = File.fromId(File.class, UUID.fromString(fileId));//sqlService.new File(fileId);
		List<String> errorMessages = new ArrayList<String>();
		
		if (userService.getCurrentAuth() instanceof AnonymousAuthenticationToken) {
			// Nothing to do, shared files will be used by all anonymous users.
		} else {
			User user = sqlUserService.getUser();
//			SharedFile sharedFile = sqlService.new SharedFile(user.getId(), fileId);
			UserFilePermission userfp = UserFilePermissionBuilder.Builder().withFileId(UUID.fromString(fileId)).withUserId(user.getId())
					.withFilePermissionId(FilePermission.fromTitle("VIEW").getId()).build();
			if (!userfp.insert()) {
				errorMessages.add(String.format("Error shareing access with user: %s", user.getUsername()));
			} else if (!fileHandlingService.copyArchiveData(fileId, file.getFilename(), user.getId().toString())) {
				errorMessages.add(String.format("Error shareing access with user: %s", user.getUsername()));
				userfp.delete();
			}
		}
		
		if (!errorMessages.isEmpty()) {
			redirectAttributes.addAttribute("error_messages", errorMessages);
			return new RedirectView("/errormessagelist");
		}
			
		return new RedirectView("/app/" + fileId);
	}
	
	@ResponseBody
	@GetMapping(value="/errormessagelist")
	protected MessageResponse errorMessageList(RedirectAttributes redirectAttributes) {
		@SuppressWarnings("unchecked")
		List<String> errorMessages = (List<String>) redirectAttributes.getAttribute("error_messages");
				
		if (!errorMessages.isEmpty()) {
			return MessageResponse.error(String.join("\n", errorMessages));
		}
		
		return MessageResponse.error("Unknown cause");
	}
	
	@ResponseBody
	@RequestMapping(value="/deletefile/{fileId}", method=RequestMethod.DELETE)
	protected MessageResponse deleteFile(@PathVariable("fileId") String fileId, ModelMap map, HttpServletRequest request) {
		File deletedFile = null;
		
		List<Proxy> activeFileProxies = proxyService.getProxies(proxy -> proxy.getId() == fileId, true);
		if (!activeFileProxies.isEmpty()) {
			File file = File.fromId(File.class, UUID.fromString(fileId));//sqlService.new File(fileId);
			return new MessageResponse(
					String.format("Error deleting file: %s-%s\nActive Sessions are using this data", file.getTitle(), file.getFilename()));
		}

//		FileUserAccess fua = sqlService.new FileUserAccess(fileId);// sqlService.getFileUserAccess(fileId);
		List<UserFilePermission> userfp = UserFilePermission.fromFileId(UUID.fromString(fileId));
		fileHandlingService.delete(fileId, File.fromId(File.class, UUID.fromString(fileId)).getFilename(), 
				userfp.stream().map(ufp -> ufp.getUserId()).filter(id -> id != null).map(id -> id.toString()).toArray(String[]::new));
		
		File file = File.fromId(File.class, UUID.fromString(fileId));// sqlService.new File(fileId);
		if (sqlUserService.isFileOwner(file) && file.delete()) {
			deletedFile = file;
		}
		
		return new MessageResponse(deletedFile == null,
				String.format("Filed to delete file: %s", fileId));
	}
	
	@ResponseBody
	@RequestMapping(value="/removefile/{fileId}", method=RequestMethod.DELETE)
	protected MessageResponse removeFile(@PathVariable("fileId") String fileId, ModelMap map, HttpServletRequest request) {
		File file = File.fromId(File.class, UUID.fromString(fileId));//sqlService.new File(fileId);
		List<Proxy> activeFileProxies = proxyService.getProxies(proxy -> proxy.getId() == fileId, false);
		if (!activeFileProxies.isEmpty()) return new MessageResponse("An active proxy is using the file. Cannot remove");
		
		if (fileHandlingService.delete(fileId, sqlUserService.getUserID())) {
//			SharedFile sharedFile = sqlService.new SharedFile(sqlUserService.getUserID(), fileId);
			UserFilePermission userfp = UserFilePermission.fromKey(sqlUserService.getUser().getId(), UUID.fromString(fileId));
			if (userfp != null) {
				if (!userfp.delete()) {
					return new MessageResponse(
							String.format("Error removing shared file: %s-%s from database", file.getTitle(), file.getFilename()));
				}
			}
		} else {
			return new MessageResponse(
					String.format("Error removing shared file: %s-%s from file system", file.getTitle(), file.getFilename()));
		}
		
		return new MessageResponse();
	}
}
