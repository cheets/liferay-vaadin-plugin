package com.arcusys.liferay.vaadinplugin;

import com.arcusys.liferay.vaadinplugin.util.ControlPanelPortletUtil;
import com.arcusys.liferay.vaadinplugin.util.ILog;
import com.arcusys.liferay.vaadinplugin.util.WidgetsetUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.service.PortletLocalServiceUtil;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created with IntelliJ IDEA.
 * User: Igor.Borisov
 * Date: 15.02.13
 * Time: 19:00
 * To change this template use File | Settings | File Templates.
 */

/*
integrating steps

* The next step is to remove the integrated Vaadin 6 from the package. What you need to remove is:
liferay-portal-6.1.1-ce-ga2/tomcat-7.0.27/webapps/ROOT/html/VAADIN
liferay-portal-6.1.1-ce-ga2/tomcat-7.0.27/webapps/ROOT/WEB-INF/lib/vaadin.jar

The removed jars need to be replaced by the Vaadin 7 version:
Extract the VAADIN folder from vaadin-server.jar into liferay-portal-6.1.1-ce-ga2/tomcat-7.0.27/webapps/ROOT/html/VAADIN. This extracts vaadinBootstrap.js which is needed by all Vaadin applications.
Extract the VAADIN folder from vaadin-themes.jar into liferay-portal-6.1.1-ce-ga2/tomcat-7.0.27/webapps/ROOT/html/VAADIN. This extracts all Vaadin themes.
Extract the VAADIN folder from vaadin-client-compiled.jar into liferay-portal-6.1.1-ce-ga2/tomcat-7.0.27/webapps/ROOT/html/VAADIN. This extracts the default widget set.
Copy vaadin-server.jar, vaadin-shared.jar, vaadin-shared-deps.jar, jsoup.jar into liferay-portal-6.1.1-ce-ga2/tomcat-7.0.27/webapps/ROOT/WEB-INF/lib. You can optionally remove the version numbers if you want to follow the Liferay standard.

You have now integrated Vaadin into your Liferay Porta and are ready to start creating Vaadin portlets.
*
* */
public class VaadinUpdater implements Runnable {
    private static Log log = LogFactoryUtil.getLog(VaadinUpdater.class);

    private final UpgradeListener upgradeListener;

    private final String downloadLocation;
    private ILog outputLog;

    public interface UpgradeListener {
        void updateComplete();
        void updateFailed(String message);
    }

    public VaadinUpdater(String downloadLocation, UpgradeListener upgradeListener, ILog outputLog) {
        this.downloadLocation = downloadLocation;
        this.upgradeListener = upgradeListener;
        this.outputLog = outputLog;
    }

    public void run() {
        File tmpDir = null;
        File vaadinZipFile = null;
        String tmpPath = null;

        try {
            tmpDir = WidgetsetUtil.createTempDir();
            tmpPath = tmpDir.getPath();
            try {
                outputLog.log("Downloading " + downloadLocation + " to "
                        + tmpPath);
                ControlPanelPortletUtil.download(downloadLocation, tmpPath, ControlPanelPortletUtil.VAADIN_ALL_ZIP);
                vaadinZipFile = new File(tmpDir, ControlPanelPortletUtil.VAADIN_ALL_ZIP);

                outputLog.log("Download complete.");
            } catch (Exception e) {
                log.warn("Download failed.", e);
                upgradeListener.updateFailed("Download failed: " + e.getMessage());
                return;
            }

            outputLog.log("Extracting files... ");

            byte[] buf = new byte[1024];
            String zipDestinationPath = tmpPath + "/unzip/";
            File unzipDirectory = new File(zipDestinationPath);
            if(!unzipDirectory.mkdir()){
                log.warn("Zip extract failed.");
                upgradeListener.updateFailed("Zip extract failed: Can not create directory " + zipDestinationPath);
                return;
            }

            try {
                ZipInputStream zinstream = new ZipInputStream(new FileInputStream(vaadinZipFile.getAbsolutePath()));
                ZipEntry zentry = zinstream.getNextEntry();

                while (zentry != null) {
                    String entryName = zentry.getName();
                    if(zentry.isDirectory())
                    {
                        File newFile = new File(zipDestinationPath + entryName);
                        if(!newFile.mkdir()){
                            break;
                        }
                        zentry = zinstream.getNextEntry();
                        continue;
                    }
                    outputLog.log("Extracting " + entryName);
                    FileOutputStream outstream = new FileOutputStream(zipDestinationPath +  getFileNameWithoutVersion(entryName));
                    int n;

                    while ((n = zinstream.read(buf, 0, 1024)) > -1) {
                        outstream.write(buf, 0, n);
                    }

                    outputLog.log("Successfully Extracted File Name : " + entryName);
                    outstream.close();

                    zinstream.closeEntry();
                    zentry = zinstream.getNextEntry();
                }
                zinstream.close();
            } catch (Exception e) {
                log.warn("Unzip failed.", e);
                upgradeListener.updateFailed("Extraction failed: " + e.getMessage());
                return;
            }

             String vaadinResourcePath = ControlPanelPortletUtil.getVaadinResourceDir();
             File vaadinResource = new File(vaadinResourcePath);
             if(vaadinResource.exists()){
                 outputLog.log("Removing old vaadin resources : " + vaadinResourcePath);
                  FileUtils.deleteDirectory(vaadinResource);
             }

            File vaadin6Version = ControlPanelPortletUtil.get6VersionVaadinJarLocation();
            if(vaadin6Version.exists()){
                outputLog.log("Removing old vaadin.jar : " + vaadin6Version.getAbsolutePath());
                vaadin6Version.delete();
            }

            /*
            Extract the VAADIN folder from vaadin-server.jar into liferay-portal-6.1.1-ce-ga2/tomcat-7.0.27/webapps/ROOT/html/VAADIN.
            This extracts vaadinBootstrap.js which is needed by all Vaadin applications.
*/
            if (extractVAADINFolder(zipDestinationPath, ControlPanelPortletUtil.VAADIN_SERVER_JAR, "/vaadin-server/", vaadinResourcePath)){
                return;
            }

            /*
            Extract the VAADIN folder from vaadin-themes.jar into liferay-portal-6.1.1-ce-ga2/tomcat-7.0.27/webapps/ROOT/html/VAADIN.
            This extracts all Vaadin themes.
             */
            if (extractVAADINFolder(zipDestinationPath, ControlPanelPortletUtil.VAADIN_THEMES_JAR,"/vaadin-themes/", vaadinResourcePath)){
                return;
            }

            /*
            Extract the VAADIN folder from vaadin-client-compiled.jar into liferay-portal-6.1.1-ce-ga2/tomcat-7.0.27/webapps/ROOT/html/VAADIN.
            This extracts the default widget set.
            */
            if (extractVAADINFolder(zipDestinationPath, ControlPanelPortletUtil.VAADIN_CLIENT_COMPILED_JAR, "/vaadin-client-compiled/", vaadinResourcePath)){
                return;
            }

            /*
            * Copy vaadin-server.jar, vaadin-shared.jar, vaadin-shared-deps.jar, jsoup.jar into
            * liferay-portal-6.1.1-ce-ga2/tomcat-7.0.27/webapps/ROOT/WEB-INF/lib.
            * You can optionally remove the version numbers if you want to follow the Liferay standard.
            *
            * */

            String portalLibDirPath = ControlPanelPortletUtil.getPortalLibDir();
            String vaadinClientJarsDirPath =  ControlPanelPortletUtil.getVaadinClientJarsDir();

            File vaadinClientJarsDir = new File(vaadinClientJarsDirPath);
            if(!vaadinClientJarsDir.exists()){
                vaadinClientJarsDir.mkdir();
            }

            replaceFile(zipDestinationPath, portalLibDirPath, ControlPanelPortletUtil.VAADIN_SERVER_JAR);
            replaceFile(zipDestinationPath , portalLibDirPath, ControlPanelPortletUtil.VAADIN_SHARED_JAR);
            replaceFile(zipDestinationPath + "/lib/", portalLibDirPath, ControlPanelPortletUtil.VAADIN_SHARED_DEPS_JAR);
            replaceFile(zipDestinationPath + "/lib/", portalLibDirPath, ControlPanelPortletUtil.JSOUP_JAR);

            replaceFile(zipDestinationPath , vaadinClientJarsDirPath, ControlPanelPortletUtil.VAADIN_CLIENT_COMPILER_JAR);
            replaceFile(zipDestinationPath , vaadinClientJarsDirPath, ControlPanelPortletUtil.VAADIN_CLIENT_JAR);

            replaceFile(zipDestinationPath , portalLibDirPath, ControlPanelPortletUtil.VAADIN_THEME_COMPILER_JAR);
            replaceFile(zipDestinationPath , portalLibDirPath, ControlPanelPortletUtil.VAADIN_THEMES_JAR);

            upgradeListener.updateComplete();

        } catch (Exception e) {
            log.warn("Exception while updating Vaadin version.", e);
            upgradeListener.updateFailed("Upgrade failed: " + e.getMessage());
        } finally {
            try {
                FileUtils.deleteDirectory(tmpDir);
            } catch (IOException e) {
                log.warn(e);
            }
        }
    }

    private void replaceFile(String sourceDir, String destinationDir, String fileName) throws IOException {
        File newJar = new File(sourceDir + fileName);
        File oldJar = new File(destinationDir + fileName);
        FileUtils.copyFile(newJar, oldJar, true);
    }

    private boolean extractVAADINFolder(String sourceDirPath, String jarName, String tmpFolderName, String destination) throws IOException {
        String vaadinJarFilePath = sourceDirPath + "\\" + jarName;
        JarFile vaadinJar = new JarFile(vaadinJarFilePath);
        String vaadinExtractedPath = sourceDirPath + tmpFolderName;
        outputLog.log("Extracting " + jarName);
        try {
            Enumeration<JarEntry> entries = vaadinJar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                boolean extractSuccessful = ControlPanelPortletUtil
                        .extractJarEntry(vaadinJar, entry, vaadinExtractedPath);
                if (!extractSuccessful) {
                    outputLog.log("Extraction failed: " + entry.getName());
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Extracting VAADIN folder failed.", e);
            upgradeListener.updateFailed("Extraction failed: " + e.getMessage());
            return true;
        } finally {
            vaadinJar.close();
        }

        String vaadinExtractedVaadinPath = vaadinExtractedPath+ "/VAADIN/";
        File vaadinExtractedVaadin = new File(vaadinExtractedVaadinPath);
        if(!vaadinExtractedVaadin.exists()){
            upgradeListener.updateFailed("Could not find " + vaadinExtractedVaadinPath);
            return true;
        }

        FileUtils.copyDirectory(vaadinExtractedVaadin, new File(destination));
        return false;
    }

    private String getFileNameWithoutVersion(String fileName)
    {
       return fileName.replaceAll("-[0-9.]+.*" , ".jar");
    }



    private void replace(File[] themes, File vaadinFile) throws Exception {
        if (themes != null && vaadinFile != null) {
            File themeDir = new File(
                    ControlPanelPortletUtil.getVaadinResourceDir() + "/themes/");
            for (File theme : themes) {
                if (theme.exists()) {
                    File oldTheme = new File(themeDir, theme.getName());
                    if (oldTheme.exists()) {
                        try {
                            FileUtils.deleteDirectory(oldTheme);
                        } catch (IOException e) {
                            log.warn(e);
                        }
                    }
                    FileUtils.copyDirectory(theme, oldTheme);
                }
            }
            File oldVaadinFile = ControlPanelPortletUtil.getVaadinServerJarLocation();
            FileUtils.copyFile(vaadinFile, oldVaadinFile);
        } else {
            throw new Exception("Download incomplete.");
        }
    }
}