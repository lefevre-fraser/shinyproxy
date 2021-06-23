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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.metamorphsoftware.shinyproxy.configurationproperties.FileStorageConfigurationProperties;

/**
 * @author Fraser LeFevre
 *
 */
@Service
public class FileHandlingService {
	
	@Autowired
	protected FileStorageConfigurationProperties config;
	
	protected IOFileFilter notArtifactDir = FileFilterUtils.notFileFilter(FileFilterUtils.nameFileFilter("artifacts", IOCase.INSENSITIVE));
	
	/**
	 * @param fileId
	 * @param userId
	 * @return
	 */
	public Path findJSONLaunch(String fileId, String userId) {
		Path jsonLaunchPath = null;
		
		Path path = Paths.get(config.getUserLocation(), userId, fileId);
		Collection<File> files = FileUtils.listFiles(path.toFile(), FileFilterUtils.nameFileFilter("visualizer_config.json", IOCase.INSENSITIVE), notArtifactDir);
		if (files.size() > 0) {
			jsonLaunchPath = files.toArray(File[]::new)[0].toPath();
		}
		
		return jsonLaunchPath;
	}
	
	/**
	 * @param fileId
	 * @param userId
	 * @return
	 */
	public Path findCSVLaunch(String fileId, String userId) {
		Path csvLaunchPath = null;
		
		Path path = Paths.get(config.getUserLocation(), userId, fileId);
		Collection<File> files = FileUtils.listFiles(path.toFile(), FileFilterUtils.nameFileFilter("output.csv", IOCase.INSENSITIVE), notArtifactDir);
		if (files.size() > 0) {
			csvLaunchPath = files.toArray(File[]::new)[0].toPath();
		}
		
		return csvLaunchPath;
	}

	/**
	 * @param file
	 * @param fileId
	 * @return
	 */
	public boolean store(MultipartFile file, String fileId) {
		boolean success = false;
		String filename = FilenameUtils.getName(file.getOriginalFilename());
		Path path = Paths.get(config.getZipLocation(), fileId, filename);
		
		try {
			Files.createDirectories(path.getParent());
			Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
			success = true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return success;
	}
	
	/**
	 * @param fileId
	 * @param filename
	 * @param userId
	 * @return
	 */
	public boolean copyArchiveData(String fileId, String filename, String userId) {
		boolean success = false;
		Path dataPath = Paths.get(config.getZipLocation(), fileId, filename);
		Path userPath = Paths.get(config.getUserLocation(), userId, fileId);
		
		try {
			unzipArchive(dataPath, userPath);
			success = true;
		} catch (FileHandlingException e) {
			e.printStackTrace();
		}
		
		return success;
	}
	
	/**
	 * @param fromPath
	 * @param toPath
	 * @throws FileHandlingException
	 */
	private void unzipArchive(Path fromPath, Path toPath) throws FileHandlingException {
		try (ZipFile zipFile = new ZipFile(fromPath.toString())) {
			if (toPath.toFile().mkdirs() || toPath.toFile().getParentFile().exists()) {
				for (ZipEntry zipEntry : zipFile.stream().collect(Collectors.toList())) {
					File file = Paths.get(toPath.toString(), zipEntry.getName()).toFile();
					if (!zipEntry.isDirectory()) {
						if (file.getParentFile().mkdirs() || file.getParentFile().exists()) {
							InputStream is = zipFile.getInputStream(zipEntry);
							Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
							is.close();
						} else {
							throw new FileHandlingException("Unable to Make Directories for zip entires");
						}
					}
				}
			} else {
				throw new FileHandlingException("Unable to Make Directory for Zip Output");
			}
			zipFile.close();
		} catch (Exception e) {
			throw new FileHandlingException(String.format("Error unzipping archive: %s", fromPath.getFileName()), e);
		}
	}
	
	/**
	 * @param fileId
	 * @param filename
	 * @param userIds
	 * @return
	 */
	public boolean delete(String fileId, String filename, String[] userIds) {
		boolean success = false;
		
		try {
			for (String userId : userIds) {
				if (!delete(fileId, userId)) {
					throw new FileHandlingException(String.format("Error deleting file: %s for user: %s", fileId, userId));
				}
			}
			Path dataPath = Paths.get(config.getZipLocation(), fileId, filename);
			deleteDirectory(dataPath.getParent());
			success = true;
		} catch (FileHandlingException e) {
			e.printStackTrace();
		}
		
		return success;
	}
	
	/**
	 * @param fileId
	 * @param userId
	 * @return
	 */
	public boolean delete(String fileId, String userId) {
		boolean success = false;
		
		try {
			Path userPath = Paths.get(config.getUserLocation(), userId, fileId);
			deleteDirectory(userPath);
			success = true;
		} catch (FileHandlingException e) {
			e.printStackTrace();
		}
		
		return success;
	}
	
	/**
	 * @param path
	 * @throws FileHandlingException
	 */
	private void deleteDirectory(Path path) throws FileHandlingException {
		if (!Files.exists(path)) return;
		
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(path)) {
			for (Path p : ds) {
				if (Files.isDirectory(p)) {
					deleteDirectory(p);
				}
				Files.deleteIfExists(p);
			}
			
			Files.deleteIfExists(path);
		} catch (Exception e) {
			throw new FileHandlingException(String.format("Error deleting directory: %s", path.toString()), e);
		}
	}
	
	/**
	 * @return the config
	 */
	public FileStorageConfigurationProperties getConfig() {
		return config;
	}

	/**
	 * @author Fraser LeFevre
	 *
	 */
	public static class FileHandlingException extends Exception {
		private static final long serialVersionUID = -4478305888240202337L;

		/**
		 * 
		 */
		public FileHandlingException() {
			super();
		}

		/**
		 * @param message
		 * @param cause
		 */
		public FileHandlingException(String message, Throwable cause) {
			super(message, cause);
		}

		/**
		 * @param message
		 */
		public FileHandlingException(String message) {
			super(message);
		}

		/**
		 * @param cause
		 */
		public FileHandlingException(Throwable cause) {
			super(cause);
		}
		
	}
}
