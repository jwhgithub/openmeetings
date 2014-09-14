/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openmeetings.backup;

import static org.apache.openmeetings.util.OpenmeetingsVariables.webAppRootKey;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.transaction.util.FileHelper;
import org.apache.openmeetings.db.dao.basic.ChatDao;
import org.apache.openmeetings.db.dao.basic.ConfigurationDao;
import org.apache.openmeetings.db.dao.calendar.AppointmentDao;
import org.apache.openmeetings.db.dao.calendar.MeetingMemberDao;
import org.apache.openmeetings.db.dao.file.FileExplorerItemDao;
import org.apache.openmeetings.db.dao.record.FlvRecordingDao;
import org.apache.openmeetings.db.dao.room.PollDao;
import org.apache.openmeetings.db.dao.room.RoomDao;
import org.apache.openmeetings.db.dao.room.RoomOrganisationDao;
import org.apache.openmeetings.db.dao.server.LdapConfigDao;
import org.apache.openmeetings.db.dao.server.OAuth2Dao;
import org.apache.openmeetings.db.dao.server.ServerDao;
import org.apache.openmeetings.db.dao.server.SessiondataDao;
import org.apache.openmeetings.db.dao.user.OrganisationDao;
import org.apache.openmeetings.db.dao.user.PrivateMessageFolderDao;
import org.apache.openmeetings.db.dao.user.PrivateMessagesDao;
import org.apache.openmeetings.db.dao.user.UserContactsDao;
import org.apache.openmeetings.db.dao.user.UserDao;
import org.apache.openmeetings.db.entity.basic.ChatMessage;
import org.apache.openmeetings.db.entity.basic.Configuration;
import org.apache.openmeetings.db.entity.calendar.Appointment;
import org.apache.openmeetings.db.entity.calendar.AppointmentCategory;
import org.apache.openmeetings.db.entity.calendar.AppointmentReminderTyps;
import org.apache.openmeetings.db.entity.file.FileExplorerItem;
import org.apache.openmeetings.db.entity.record.FlvRecording;
import org.apache.openmeetings.db.entity.room.PollType;
import org.apache.openmeetings.db.entity.room.Room;
import org.apache.openmeetings.db.entity.room.RoomPoll;
import org.apache.openmeetings.db.entity.room.RoomType;
import org.apache.openmeetings.db.entity.server.LdapConfig;
import org.apache.openmeetings.db.entity.user.Organisation;
import org.apache.openmeetings.db.entity.user.PrivateMessage;
import org.apache.openmeetings.db.entity.user.State;
import org.apache.openmeetings.db.entity.user.User;
import org.apache.openmeetings.db.entity.user.User.Right;
import org.apache.openmeetings.util.AuthLevelUtil;
import org.apache.openmeetings.util.CalendarPatterns;
import org.apache.openmeetings.util.OmFileHelper;
import org.red5.logging.Red5LoggerFactory;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.convert.Registry;
import org.simpleframework.xml.convert.RegistryStrategy;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.NodeBuilder;
import org.simpleframework.xml.stream.OutputNode;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 
 * @author sebastianwagner
 * 
 */
public class BackupExport {
	private static final Logger log = Red5LoggerFactory.getLogger(BackupExport.class, webAppRootKey);
	private static final String BACKUP_COMMENT = 
			"###############################################\n"
			+ "This File is auto-generated by the Backup Tool \n"
			+ "you should use the BackupPanel to modify or change this file \n"
			+ "see http://openmeetings.apache.org/Upgrade.html for Details \n"
			+ "###############################################\n";

	@Autowired
	private AppointmentDao appointmentDao;
	@Autowired
	private SessiondataDao sessiondataDao;
	@Autowired
	private FileExplorerItemDao fileExplorerItemDao;
	@Autowired
	private FlvRecordingDao flvRecordingDao;
	@Autowired
	private UserDao usersDao;
	@Autowired
	private MeetingMemberDao meetingMemberDao;
	@Autowired
	private LdapConfigDao ldapConfigDao;
	@Autowired
	private PrivateMessagesDao privateMessagesDao;
	@Autowired
	private PrivateMessageFolderDao privateMessageFolderDao;
	@Autowired
	private UserContactsDao userContactsDao;
	@Autowired
	private PollDao pollManager;
	@Autowired
	private ConfigurationDao configurationDao;
	@Autowired
	private ChatDao chatDao;
	@Autowired
	private OAuth2Dao auth2Dao;
	@Autowired
	private ServerDao serverDao;
	@Autowired
	private OrganisationDao organisationDao;
	@Autowired
	private RoomDao roomDao;
	@Autowired
	private RoomOrganisationDao roomOrganisationDao;

	public void performExport(File filePath, File backup_dir, boolean includeFiles, ProgressHolder progressHolder) throws Exception {
		if (!backup_dir.exists()) {
			backup_dir.mkdirs();
		}
		Serializer simpleSerializer = new Persister();
		
		progressHolder.setProgress(0);
		/*
		 * ##################### Backup Organizations
		 */
		writeList(simpleSerializer, backup_dir, "organizations.xml",
				"organisations", organisationDao.get(0, Integer.MAX_VALUE));
		progressHolder.setProgress(5);
		/*
		 * ##################### Backup Users
		 */
		exportUsers(backup_dir, usersDao.getAllBackupUsers());
		progressHolder.setProgress(10);

		/*
		 * ##################### Backup Room
		 */
		{
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(User.class, UserConverter.class);
			registry.bind(RoomType.class, RoomTypeConverter.class);
			
			writeList(serializer, backup_dir, "rooms.xml", "rooms", roomDao.get());
			progressHolder.setProgress(15);
		}

		/*
		 * ##################### Backup Room Organizations
		 */
		{
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(Organisation.class, OrganisationConverter.class);
			registry.bind(Room.class, RoomConverter.class);
			
			writeList(serializer, backup_dir, "rooms_organisation.xml",
					"room_organisations", roomOrganisationDao.get());
			progressHolder.setProgress(20);
		}

		/*
		 * ##################### Backup Appointments
		 */
		{
			List<Appointment> list = appointmentDao.getAppointments();
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(AppointmentCategory.class, AppointmentCategoryConverter.class);
			registry.bind(User.class, UserConverter.class);
			registry.bind(AppointmentReminderTyps.class, AppointmentReminderTypeConverter.class);
			registry.bind(Room.class, RoomConverter.class);
			if (list != null && list.size() > 0) {
				registry.bind(list.get(0).getStart().getClass(), DateConverter.class);
			}
			
			writeList(serializer, backup_dir, "appointements.xml", "appointments", list);
			progressHolder.setProgress(25);
		}

		/*
		 * ##################### Backup Meeting Members
		 */
		{
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(User.class, UserConverter.class);
			registry.bind(Appointment.class, AppointmentConverter.class);
			
			writeList(serializer, backup_dir, "meetingmembers.xml",
					"meetingmembers", meetingMemberDao.getMeetingMembers());
			progressHolder.setProgress(30);
		}

		/*
		 * ##################### LDAP Configs
		 */
		List<LdapConfig> ldapList = ldapConfigDao.get();
		if (!ldapList.isEmpty()) {
			ldapList.remove(0);
		}
		writeList(simpleSerializer, backup_dir, "ldapconfigs.xml", "ldapconfigs", ldapList);
		progressHolder.setProgress(35);

		/*
		 * ##################### Cluster servers
		 */
		writeList(simpleSerializer, backup_dir, "servers.xml", "servers", serverDao.get(0, Integer.MAX_VALUE));
		progressHolder.setProgress(40);

		/*
		 * ##################### OAuth2 servers
		 */
		writeList(simpleSerializer, backup_dir, "oauth2servers.xml", "oauth2servers", auth2Dao.get(0, Integer.MAX_VALUE));
		progressHolder.setProgress(45);

		/*
		 * ##################### Private Messages
		 */
		{
			List<PrivateMessage> list = privateMessagesDao.get(0, Integer.MAX_VALUE);
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(User.class, UserConverter.class);
			registry.bind(Room.class, RoomConverter.class);
			if (list != null && list.size() > 0) {
				registry.bind(list.get(0).getInserted().getClass(), DateConverter.class);
			}
			
			writeList(serializer, backup_dir, "privateMessages.xml",
					"privatemessages", list);
			progressHolder.setProgress(50);
		}

		/*
		 * ##################### Private Message Folders
		 */
		writeList(simpleSerializer, backup_dir, "privateMessageFolder.xml",
				"privatemessagefolders", privateMessageFolderDao.get(0, Integer.MAX_VALUE));
		progressHolder.setProgress(55);

		/*
		 * ##################### User Contacts
		 */
		{
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(User.class, UserConverter.class);
			
			writeList(serializer, backup_dir, "userContacts.xml",
					"usercontacts", userContactsDao.getUserContacts());
			progressHolder.setProgress(60);
		}

		/*
		 * ##################### File-Explorer
		 */
		{
			List<FileExplorerItem> list = fileExplorerItemDao.getFileExplorerItems();
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			if (list != null && list.size() > 0) {
				registry.bind(list.get(0).getInserted().getClass(), DateConverter.class);
			}
			
			writeList(serializer, backup_dir, "fileExplorerItems.xml",
					"fileExplorerItems", list);
			progressHolder.setProgress(65);
		}

		/*
		 * ##################### Recordings
		 */
		{
			List<FlvRecording> list = flvRecordingDao.getAllFlvRecordings();
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			if (list != null && list.size() > 0) {
				registry.bind(list.get(0).getInserted().getClass(), DateConverter.class);
			}
			
			writeList(serializer, backup_dir, "flvRecordings.xml",
					"flvrecordings", list);
			progressHolder.setProgress(70);
		}

		/*
		 * ##################### Polls
		 */
		{
			List<RoomPoll> list = pollManager.getPollListBackup();
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(User.class, UserConverter.class);
			registry.bind(Room.class, RoomConverter.class);
			registry.bind(PollType.class, PollTypeConverter.class);
			if (list != null && list.size() > 0) {
				registry.bind(list.get(0).getCreated().getClass(), DateConverter.class);
			}
			
			writeList(serializer, backup_dir, "roompolls.xml", "roompolls", list);
			progressHolder.setProgress(75);
		}

		/*
		 * ##################### Config
		 */
		{
			List<Configuration> list = configurationDao.getConfigurations(
					0, Integer.MAX_VALUE, "c.configuration_id", true);
			Registry registry = new Registry();
			registry.bind(State.class, StateConverter.class);
			registry.bind(User.class, UserConverter.class);
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			if (list != null && list.size() > 0) {
				registry.bind(list.get(0).getStarttime().getClass(), DateConverter.class);
			}
			
			writeList(serializer, backup_dir, "configs.xml", "configs", list);
			progressHolder.setProgress(80);
		}
		
		/*
		 * ##################### Chat
		 */
		{
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(User.class, UserConverter.class);
			registry.bind(Room.class, RoomConverter.class);
			List<ChatMessage> list = chatDao.get(0, Integer.MAX_VALUE);
			if (list != null && list.size() > 0) {
				registry.bind(list.get(0).getSent().getClass(), DateConverter.class);
			}
			
			writeList(serializer, backup_dir, "chat_messages.xml", "chat_messages", list);
			progressHolder.setProgress(85);
		}
		if (includeFiles) {
			/*
			 * ##################### Backup Room Files
			 */
			File targetRootDir = new File(backup_dir, "roomFiles");

			if (!targetRootDir.exists()) {
				targetRootDir.mkdir();
			}

			File sourceDir = OmFileHelper.getUploadDir();

			File[] files = sourceDir.listFiles();
			for (File file : files) {
				if (file.isDirectory()) {
					if (!file.getName().equals("backup") && !file.getName().equals("import")) {
						log.debug("### " + file.getName());

						FileHelper.copyRec(file, new File(targetRootDir, file.getName()));
					}
				}
			}

			/*
			 * ##################### Backup Recording Files
			 */
			File targetDirRec = new File(backup_dir, "recordingFiles");

			if (!targetDirRec.exists()) {
				targetDirRec.mkdir();
			}

			File sourceDirRec = OmFileHelper.getStreamsHibernateDir();

			FileHelper.copyRec(sourceDirRec, targetDirRec);
			progressHolder.setProgress(90);
		}

		writeZipDir(backup_dir, filePath);
		progressHolder.setProgress(100);
		log.debug("---Done");
	}
	
	private <T> void writeList(Serializer ser, File backup_dir, String fileName, String listElement, List<T> list) throws Exception {
		FileOutputStream fos = new FileOutputStream(new File(backup_dir, fileName));
		writeList(ser, fos, listElement, list);
	}
	
	private <T> void writeList(Serializer ser, OutputStream os, String listElement, List<T> list) throws Exception {
		Format format = new Format("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		OutputNode doc = NodeBuilder.write(new OutputStreamWriter(os, "UTF-8"), format);
		OutputNode root = doc.getChild("root");
		root.setComment(BACKUP_COMMENT);
		OutputNode listNode = root.getChild(listElement);

		if (list != null) {
			for (T t : list) {
				try {
					ser.write(t, listNode);
				} catch (Exception e) {
					log.debug("Exception While writing node of type: " + t.getClass(), e);
				}
			}
		}
		root.commit();
	}

	public void exportUsers(File backup_dir, List<User> list) throws Exception {
		FileOutputStream fos = new FileOutputStream(new File(backup_dir, "users.xml"));
		exportUsers(fos, list);
	}
	
	public void exportUsers(OutputStream os, List<User> list) throws Exception {
		Registry registry = new Registry();
		Strategy strategy = new RegistryStrategy(registry);
		Serializer serializer = new Persister(strategy);

		registry.bind(Organisation.class, OrganisationConverter.class);
		registry.bind(State.class, StateConverter.class);
		if (list != null && list.size() > 0) {
			registry.bind(list.get(0).getRegdate().getClass(), DateConverter.class);
		}
		
		writeList(serializer, os, "users", list);
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest
	 * , javax.servlet.http.HttpServletResponse)
	 */
	public void service(HttpServletRequest request, HttpServletResponse response, ServletContext ctx) throws ServletException, IOException {
		String sid = request.getParameter("sid");
		if (sid == null) {
			sid = "default";
		}
		log.debug("sid: " + sid);

		Long users_id = sessiondataDao.checkSession(sid);
		Set<Right> rights = usersDao.get(users_id).getRights();

		log.debug("users_id: " + users_id);
		log.debug("user_level: " + rights);

		if (AuthLevelUtil.hasAdminLevel(rights)) {
			// if (true) {

			String includeFileOption = request.getParameter("includeFileOption");
			boolean includeFiles = includeFileOption == null || "yes".equals(includeFileOption);

			String moduleName = request.getParameter("moduleName");
			if (moduleName == null) {
				moduleName = "moduleName";
			}
			log.debug("moduleName: " + moduleName);

			if (moduleName.equals("backup")) {

				/*
				 * ##################### Create Base Folder structure
				 */

				File working_dir = OmFileHelper.getUploadBackupDir();

				String dateString = "backup_"
						+ CalendarPatterns.getTimeForStreamId(new Date());

				File backup_dir = new File(working_dir, dateString);
				String requestedFile = dateString + ".zip";
				File backupFile = new File(backup_dir, requestedFile);

				try {
					performExport(backupFile, backup_dir, includeFiles, new ProgressHolder());

					response.reset();
					response.resetBuffer();
					response.setContentType("APPLICATION/OCTET-STREAM");
					response.setHeader("Content-Disposition", "attachment; filename=\"" + requestedFile + "\"");
					response.setHeader("Content-Length", "" + backupFile.length());

					OutputStream out = response.getOutputStream();
					OmFileHelper.copyFile(backupFile, out);

					out.flush();
					out.close();
				} catch (Exception er) {
					log.error("Error exporting: ", er);
				}

				if (backupFile.exists()) {
					// log.debug("DELETE :1: "+backupFile.getCanonicalPath());
					backupFile.delete();
				}

				FileHelper.removeRec(backup_dir);
			}
		} else {
			log.debug("ERROR LangExport: not authorized FileDownload "
					+ (new Date()));
		}
	}

	private void writeZipDir(File directoryToZip, File zipFile) throws IOException {
		FileOutputStream fos = null;
		ZipOutputStream zos = null;
		try {
			fos = new FileOutputStream(zipFile);
			zos = new ZipOutputStream(fos);
			
			writeZipDir(directoryToZip.toURI(), directoryToZip, zos, zipFile);
		} finally {
			if (zos != null) {
				try {
					zos.close();
				} catch (IOException e) {
					log.debug("Enexpected error while closing ZipOutputStream", e);
				}
			}
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					log.debug("Enexpected error while closing FileOutputStream", e);
				}
			}
		}
	}
	
	private void writeZipDir(URI base, File dir, ZipOutputStream zos, File zipFile) throws IOException {
		for (File file : dir.listFiles()) {
			if (zipFile.equals(file)) {
				continue;
			}
			if (file.isDirectory()) {
				writeZipDir(base, file, zos, zipFile);
			} else {
				String path = base.relativize(file.toURI()).toString();
				log.debug("Writing '" + path + "' to zip file");
				ZipEntry zipEntry = new ZipEntry(path);
				zos.putNextEntry(zipEntry);

				OmFileHelper.copyFile(file, zos);
				zos.closeEntry();
			}
		}
	}
}
