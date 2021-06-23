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
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
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
import org.thymeleaf.spring5.SpringTemplateEngine;

import com.metamorphsoftware.shinyproxy.datatypes.Pair;
import com.metamorphsoftware.shinyproxy.services.SQLService.File;
import com.metamorphsoftware.shinyproxy.services.SQLService.FileUserAccess;
import com.metamorphsoftware.shinyproxy.services.SQLService.SharedFile;
import com.metamorphsoftware.shinyproxy.services.SQLService.User;
import com.metamorphsoftware.shinyproxy.services.SQLService.UserFileAccess;

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
		
		UserFileAccess ufa[] = sqlUserService.getUserFileAccess(false);
		map.put("apps", ufa);
		
		map.put("userId", sqlUserService.getUserID());
		
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
		File file = sqlService.new File(sqlUserService.getUserID(), filename, title, description);
			
		try {
			if (file.save()) {
				if (fileHandlingService.store(zipfile, file.getId())) {
					if (fileHandlingService.copyArchiveData(file.getId(), filename, sqlUserService.getUserID())) {
						return new MessageResponse();
					} else throw new Exception("Error extracting archive data for user");
				} else throw new Exception("Error uploading zip file for user");
			} else throw new Exception("Error adding file to database");
		} catch (Exception e) {
			e.printStackTrace();
			file.deleteById();
		}
		
		return new MessageResponse(String.format("Failed to add file: %s", filename));
	}
	
	@GetMapping(value="/sharefile/{fileId}")
	protected Object shareFileGET(@PathVariable("fileId") String fileId,
			ModelMap map, HttpServletRequest request, HttpServletResponse response) {
		prepareMap(map, request);
		
		if (!sqlUserService.isFileOwner(sqlService.new File(fileId))) {
			throw new RuntimeException("Unauthorized to share this file");
		}
		
		FileUserAccess fua = sqlService.new FileUserAccess(fileId); // sqlService.getFileUserAccess(fileId);
		map.put("file", fua.getFile());
		
		List<String> usersWithAccess = List.of(fua.getUserIds());
		@SuppressWarnings("unchecked")
		Pair<User, Boolean> userAccess[] = List.of(sqlUserService.getUserList()).stream()
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
			@RequestParam(name="users[]", required=false) String users[], HttpServletRequest request) {
		File file = sqlService.new File(fileId);
		List<String> errorMessages = new ArrayList<String>();
		
		if (users == null) users = new String[0];
		
		List<String> newShareList = List.of(users);
		List<String> currentShareList = List.of(sqlService.new FileUserAccess(fileId).getUserIds())
				.stream().filter(id -> !id.equals(file.getUserId())).collect(Collectors.toList());
		List<Proxy> activeFileProxies = proxyService.getProxies(proxy -> proxy.getId() == fileId, true);
		
		// Remove access from users not in the newShareList
		for (String id: currentShareList) {
			if (!newShareList.contains(id)) {
				User user = sqlService.new User(id);
				if (activeFileProxies.stream().anyMatch(proxy -> proxy.getUserId() == user.getUsername())) {
					errorMessages.add(String.format("Error removing access from user: %s. User has active proxy using this file", user.getUsername()));
				} else {
					SharedFile sharedFile = sqlService.new SharedFile(id, fileId);
					if (sharedFile.getByKey()) {
						if (!sharedFile.deleteById()) {
							errorMessages.add(String.format("Error removing access from user: %s", user.getUsername()));
						} else if (!fileHandlingService.delete(fileId, id)) {
							errorMessages.add(String.format("Error deleting data from user: %s", user.getUsername()));
							sharedFile.save();
						}
					}
				}
			}
		}
		
		// Share with any users who don't have access
		for (String id: newShareList) {
			if (!currentShareList.contains(id)) {
				User user = sqlService.new User(id);
				SharedFile sharedFile = sqlService.new SharedFile(id, fileId);
				if (!sharedFile.save()) {
					errorMessages.add(String.format("Error shareing access with user: %s", user.getUsername()));
				} else if (!fileHandlingService.copyArchiveData(fileId, file.getFilename(), id)) {
					errorMessages.add(String.format("Error shareing access with user: %s", user.getUsername()));
					sharedFile.deleteById();
				}
			}
		}

		// if there were errors return an error response
		if (!errorMessages.isEmpty()) {
			return MessageResponse.error(String.join("\n", errorMessages));
		}
		
		return new MessageResponse();
	}
	
	@ResponseBody
	@RequestMapping(value="/deletefile/{fileId}", method=RequestMethod.DELETE)
	protected MessageResponse deleteFile(@PathVariable("fileId") String fileId, ModelMap map, HttpServletRequest request) {
		File deletedFile = null;
		
		List<Proxy> activeFileProxies = proxyService.getProxies(proxy -> proxy.getId() == fileId, true);
		if (!activeFileProxies.isEmpty()) {
			File file = sqlService.new File(fileId);
			return new MessageResponse(
					String.format("Error deleting file: %s-%s\nActive Sessions are using this data", file.getTitle(), file.getFilename()));
		}

		FileUserAccess fua = sqlService.new FileUserAccess(fileId);// sqlService.getFileUserAccess(fileId);
		fileHandlingService.delete(fileId, fua.getFile().getFilename(), fua.getUserIds());
		
		File file = sqlService.new File(fileId);
		if (sqlUserService.isFileOwner(file) && file.deleteById()) {
			deletedFile = file;
		}
		
		return new MessageResponse(deletedFile == null,
				String.format("Filed to delete file: %s", fileId));
	}
	
	@ResponseBody
	@RequestMapping(value="/removefile/{fileId}", method=RequestMethod.DELETE)
	protected MessageResponse removeFile(@PathVariable("fileId") String fileId, ModelMap map, HttpServletRequest request) {
		File file = sqlService.new File(fileId);
		List<Proxy> activeFileProxies = proxyService.getProxies(proxy -> proxy.getId() == fileId, false);
		if (!activeFileProxies.isEmpty()) return new MessageResponse("An active proxy is using the file. Cannot remove");
		
		if (fileHandlingService.delete(fileId, sqlUserService.getUserID())) {
			SharedFile sharedFile = sqlService.new SharedFile(sqlUserService.getUserID(), fileId);
			if (sharedFile.getByKey()) {
				if (!sharedFile.deleteById()) {
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
