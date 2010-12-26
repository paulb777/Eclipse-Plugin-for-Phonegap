/*
 * Copyright (C) 2010-11 Mobile Developer Solutions
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * References:
 * org.com.android.ide.eclipse.adt.internal.wizards.newproject
 * Platform Plug-in Developer Guide > Programmer's Guide > Dialogs and wizards
 */

package com.mds.apg.wizards;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

class PhonegapProjectPopulate {

    /**
     * Creates the actual project(s). This is run asynchronously in a different
     * thread.
     * 
     * @param monitor An existing monitor.
     * @param mainData Data for main project. Can be null.
     * @throws InvocationTargetException to wrap any unmanaged exception and
     *             return it to the calling thread. The method can fail if it
     *             fails to create or modify the project or if it is canceled by
     *             the user.
     */
    static void createProjectAsync(IProgressMonitor monitor, PageInfo pageInfo)
           throws InvocationTargetException {
        monitor.beginTask("Create Android Project", 100);
        try {
            updateProjectWithPhonegap(monitor, pageInfo);

        } catch (CoreException e) {
            throw new InvocationTargetException(e);
        } catch (IOException e) {
            throw new InvocationTargetException(e);
        } catch (URISyntaxException e) {
            throw new InvocationTargetException(e);
        } finally {
            monitor.done();
        }
    }

    /**
     * Driver for the various tasks to add phonegap. 
     * 1. Update the main java file to load index.html 
     * 2. Get the phonegap.jar and update the classpath
     * 3. Get the user's sources into the android project 
     * 4. Handle project add-ins like Sencha and JQuery Mobile
     * 5. Update the AndroidManifest file 
     * 6. Fill the res directory with drawables and layout
     * 7. Update the project nature so that JavaScript files are recognize 
     * 8. Refresh the project with the updated disc files 
     * 9. Do a clean build - TODO still necessary with ADT 8.0.1?
     * 
     * @param monitor An existing monitor.
     * @throws InvocationTargetException to wrap any unmanaged exception and
     *             return it to the calling thread. The method can fail if it
     *             fails to create or modify the project or if it is canceled by
     *             the user.
     */

    static private void updateProjectWithPhonegap(IProgressMonitor monitor, PageInfo pageInfo)
            throws CoreException, IOException, URISyntaxException {

        updateJavaMain(pageInfo.mDestinationDirectory);
        getPhonegapJar(monitor, pageInfo);
        getWWWSources(monitor, pageInfo);
        if (pageInfo.mSenchaChecked)
            setupSencha(monitor, pageInfo);
        phonegapizeAndroidManifest(pageInfo);
        getResFiles(monitor, pageInfo);
        IProject newAndroidProject = pageInfo.mAndroidProject;
        addJsNature(monitor, newAndroidProject);
        newAndroidProject.refreshLocal(2 /* DEPTH_INFINITE */, monitor);
        newAndroidProject.build(IncrementalProjectBuilder.CLEAN_BUILD, monitor);
    }

    /**
     * Find and update the main java file to kick off phonegap
     * 
     * @throws IOException
     */
    static private void updateJavaMain(String destDir) throws IOException {
        String javaFile = findJavaFile(destDir + "src");
        String javaFileContents = StringIO.read(javaFile);

        // Import com.phonegap instead of Activity
        javaFileContents = javaFileContents.replace("import android.app.Activity;",
                "import com.phonegap.*;");

        // Change superclass to DroidGap instead of Activity
        javaFileContents = javaFileContents.replace("extends Activity", "extends DroidGap");

        // Change to start with index.html
        javaFileContents = javaFileContents.replace("setContentView(R.layout.main);",
                "super.loadUrl(\"file:///android_asset/www/index.html\");");

        // Write out the file
        StringIO.write(javaFile, javaFileContents);
    }

    // Recursively search for java file. Assuming there is only one in the new
    // Android project

    static private String findJavaFile(String dir) {
        String retVal;
        File f = new File(dir);
        if (f.isDirectory()) {
            String fList[] = f.list();
            for (String s : fList) {
                if (s.length() > 5 && s.indexOf(".java") == s.length() - 5) {
                    return dir + '/' + s;
                } else {
                    retVal = findJavaFile(dir + '/' + s);
                    if (retVal != null)
                        return retVal;
                }
            }
        }
        return null;
    }

    /**
     * It turns out that phonegap.jar does not yet exist in a raw phonegap
     * installation It needs to be built with the Android installation. So
     * instead, we'll get the sources, so that it just gets build with our
     * product. We also need to get /framework/libs/commons-codec-1.3.jar upon
     * which the sources depend
     * 
     * @throws URISyntaxException
     */
    static private void getPhonegapJar(IProgressMonitor monitor, PageInfo pageInfo) throws CoreException,
            IOException, URISyntaxException {

        FileCopy.recursiveCopy(pageInfo.mPhonegapDirectory + "/" + "framework" + "/" + "src",
                Platform.getLocation().toString() + "/" + pageInfo.mAndroidProject.getName() + "/"
                        + "src");

        final String commonCodecLoc = "commons-codec-1.3.jar";

        // Point at original file in phonegap directory instead of copy
        // FileCopy.copy(pageInfo.mPhonegapDirectory + "/" + "framework" + "/" +
        // "libs" + "/" + commonCodecLoc,
        // libsDir + commonCodecLoc);

        // Now update classpath .classpath should end up like the following.
        // Note that the path specifies that
        // phonegap.jar should be included in the build. sourcepath enables the
        // phonegap source to be found for debugging
        // when doing creation the "output" line doesn't yet exist, so the new
        // line goes last
        //
        // <?xml version="1.0" encoding="UTF-8"?>
        // <classpath>
        // <classpathentry kind="src" path="src"/>
        // <classpathentry kind="src" path="gen"/>
        // <classpathentry kind="con"
        // path="com.android.ide.eclipse.adt.ANDROID_FRAMEWORK"/>
        // <classpathentry kind="lib" path="libs/phonegap.jar"
        // sourcepath="/Users/paulb/phonegap-android101023/framework/src"/>
        // <classpathentry kind="output" path="bin"/>
        // </classpath>

        IJavaProject javaProject = (IJavaProject) pageInfo.mAndroidProject
                .getNature(JavaCore.NATURE_ID); // thanks to Larry Isaacs
                                                // http://dev.eclipse.org/newslists/news.eclipse.webtools/msg10002.html
        IClasspathEntry[] classpathList = javaProject.readRawClasspath();
        IClasspathEntry[] newClasspaths = new IClasspathEntry[classpathList.length + 1];
        System.arraycopy(classpathList, 0, newClasspaths, 0, classpathList.length);

        // Create the new Classpath entry

        IClasspathEntry newPath = JavaCore.newLibraryEntry(new Path(pageInfo.mPhonegapDirectory
                + "/" + "framework" + "/" + "libs" + "/" + commonCodecLoc), new Path(
                pageInfo.mPhonegapDirectory + "/" + "framework" + "/" + "src" + "/"), null);

        newClasspaths[classpathList.length] = newPath;

        // write it back out with
        javaProject.setRawClasspath(newClasspaths, monitor);
    }

    /**
     * Get the sources from the example directory or alternative specified
     * directory Place them in assets/www Also get phonegap.js from framework
     * assets
     * 
     * @throws URISyntaxException
     */
    static private void getWWWSources(IProgressMonitor monitor, PageInfo pageInfo) throws CoreException,
            IOException, URISyntaxException {

        addDefaultDirectories(pageInfo.mAndroidProject, "assets/", new String[] {
            "www"
        }, monitor);
        String wwwDir = Platform.getLocation().toString() + "/"
                + pageInfo.mAndroidProject.getName() + "/" + "assets" + "/" + "www" + "/";

        FileCopy.recursiveCopy(pageInfo.mSourceDirectory, wwwDir);

        // Even though there is a phonegap.js file in the directory
        // framework/assets/www, it is WRONG!!
        // phonegap.js must be constructed from the files
        // in framework/assets/js

        FileCopy.createPhonegapJs(pageInfo.mPhonegapDirectory + "/" + "framework" + "/" + "assets"
                + "/" + "js", wwwDir + "phonegap.js");
    }

    /**
     * Get sencha-touch.js and resources directory. Add references to them in
     * index.html If kitchen sink is selected, so other copies TBD
     * 
     * @throws URISyntaxException
     */
    static private void setupSencha(IProgressMonitor monitor, PageInfo pageInfo) throws CoreException,
            IOException, URISyntaxException {

        addDefaultDirectories(pageInfo.mAndroidProject, "assets/www/", new String[] {
            "sencha"
        }, monitor);
        addDefaultDirectories(pageInfo.mAndroidProject, "assets/www/sencha/", new String[] {
            "resources"
        }, monitor);
        String senchaDir = pageInfo.mDestinationDirectory + "/" + "assets/www/sencha/";

        FileCopy.recursiveCopy(pageInfo.mSenchaDirectory + "/resources", senchaDir + "/resources");

        // Now copy the sencha-touch.js
        FileCopy.copy(pageInfo.mSenchaDirectory + "/sencha-touch.js", senchaDir);

        // Update the index.html with path to sencha-touch.css and
        // sencha-touch.js
        String file = pageInfo.mDestinationDirectory + "/" + "assets/www/index.html";
        String fileContents = StringIO.read(file);

        int senchaTouchCssIndex = fileContents.indexOf("/sencha-touch.css\"");
        if (senchaTouchCssIndex > 0) {
            int startIncludeIndex = fileContents.lastIndexOf("\"", senchaTouchCssIndex);
            fileContents = fileContents.substring(0, startIncludeIndex) + "\"sencha/resources/css"
                    + fileContents.substring(senchaTouchCssIndex);
        } else { // must add a new line
            int firstCssIndex = fileContents.indexOf(".css\"");
            int insertSpot;
            if (firstCssIndex > 0) {
                insertSpot = fileContents.lastIndexOf('<', firstCssIndex);
            } else {
                insertSpot = fileContents.indexOf("</head>");
            }
            if (insertSpot <= 0) {
                throw new IOException("setupSencha: " + "index.html does not have </head> tag");
            }
            // adjust insertSpot back to end of last line
            while (Character.isWhitespace(fileContents.charAt(--insertSpot)))
                ;
            insertSpot++;

            fileContents = fileContents.substring(0, insertSpot)
                    + "\n      <link rel=\"stylesheet\" href=\"sencha/resources/css/sencha-touch.css\" type=\"text/css\">"
                    + fileContents.substring(insertSpot);
        }

        int senchaTouchJsIndex = fileContents.indexOf("/sencha-touch.js\"");
        if (senchaTouchJsIndex > 0) {
            int startIncludeIndex = fileContents.lastIndexOf("\"", senchaTouchJsIndex);
            fileContents = fileContents.substring(0, startIncludeIndex) + "\"sencha"
                    + fileContents.substring(senchaTouchJsIndex);
        } else { // must add a new line
            int firstJsIndex = fileContents.indexOf(".js\"");
            int insertSpot;
            if (firstJsIndex > 0) {
                insertSpot = fileContents.lastIndexOf('<', firstJsIndex);
            } else {
                insertSpot = fileContents.indexOf("</head>");
            }
            // Failure case already caught in css section

            // adjust insertSpot back to end of last line
            while (Character.isWhitespace(fileContents.charAt(--insertSpot)))
                ;
            insertSpot++;

            fileContents = fileContents.substring(0, insertSpot)
                    + "\n      <script type=\"text/javascript\" src=\"sencha/sencha-touch.js\"></script>"
                    + fileContents.substring(insertSpot);
        }

        // Write out the file
        StringIO.write(file, fileContents);
    }

    /**
     * Get the Android Manifest file and tweak it for phonegap
     * 
     * @throws URISyntaxException
     */
    static private void phonegapizeAndroidManifest(PageInfo pageInfo) throws CoreException, IOException,
            URISyntaxException {

        String destFile = pageInfo.mDestinationDirectory + "AndroidManifest.xml";
        String sourceFile = pageInfo.mPhonegapDirectory + "/" + "framework" + "/"
                + "AndroidManifest.xml";
        String sourceFileContents = StringIO.read(sourceFile);
        String manifestInsert = getManifestScreensAndPermissions(sourceFileContents);
        String destFileContents = StringIO.read(destFile);

        // Add phonegap screens, permissions and turn on debuggable
        destFileContents = destFileContents.replace("<application android:", manifestInsert
                + "<application" + " android:debuggable=\"true\" android:");

        // Add android:configChanges="orientation|keyboardHidden" to the
        // activity
        destFileContents = destFileContents.replace("<activity android:",
                "<activity android:configChanges=\"orientation|keyboardHidden\" android:");

        if (destFileContents.indexOf("<uses-sdk") < 0) {
            // User did not set min SDK, so use the phonegap template manifest
            // version
            int startIndex = sourceFileContents.indexOf("<uses-sdk");
            int endIndex = sourceFileContents.indexOf("<", startIndex + 1);
            destFileContents = destFileContents.replace("</manifest>",
                    sourceFileContents.substring(startIndex, endIndex) + "</manifest>");
        }
        // Write out the file
        StringIO.write(destFile, destFileContents);
    }

    /**
     * Helper Function for phonegapizeAndroidManifest It finds the big middle
     * section that needs to be added to the manifest for phonegap
     */

    static private String getManifestScreensAndPermissions(String manifest) {
        int startIndex;
        startIndex = manifest.indexOf("<supports-screens");
        if (startIndex == -1)
            startIndex = manifest.indexOf("<uses-permissions");
        if (startIndex == -1)
            return null;
        int index = startIndex;
        int lastIndex;
        do {
            lastIndex = index;
            index = manifest.indexOf("<uses-permission", index + 1);
        } while (index > 0);
        lastIndex = manifest.indexOf('<', lastIndex + 1);
        return manifest.substring(startIndex, lastIndex);
    }

    /**
     * Copy anything in res/layout Copy drawable to drawable* Leave values alone
     * since string maps to app name
     * 
     * @throws URISyntaxException
     */
    static private void getResFiles(IProgressMonitor monitor, PageInfo pageInfo) throws CoreException,
            IOException, URISyntaxException {

        String sourceResDir = pageInfo.mPhonegapDirectory + "/" + "framework" + "/" + "res" + "/";
        String destResDir = pageInfo.mDestinationDirectory + "res" + "/";

        FileCopy.recursiveForceCopy(sourceResDir + "layout" + "/", destResDir + "layout" + "/");

        // Copy source drawable to all of the project drawable* directories
        String sourceDrawableDir = sourceResDir + "drawable" + "/";
        File destFile = new File(destResDir);
        String fList[] = destFile.list();
        for (String s : fList) {
            if (s.indexOf("drawable") == 0) {
                FileCopy.recursiveForceCopy(sourceDrawableDir, destResDir + s);
            }
        }
    }

    /**
     * Adds default directories to the project. Unchanged from private version
     * in parent class
     * 
     * @param project The Java Project to update.
     * @param parentFolder The path of the parent folder. Must end with a
     *            separator.
     * @param folders Folders to be added.
     * @param monitor An existing monitor.
     * @throws CoreException if the method fails to create the directories in
     *             the project.
     */
    static private void addDefaultDirectories(IProject project, String parentFolder, String[] folders,
            IProgressMonitor monitor) throws CoreException {
        for (String name : folders) {
            if (name.length() > 0) {
                IFolder folder = project.getFolder(parentFolder + name);
                if (!folder.exists()) {
                    folder.create(true /* force */, true /* local */, new SubProgressMonitor(
                            monitor, 10));
                }
            }
        }
    }

    /**
     * Add JavaScript nature to the project. It gets added last after Android
     * and Java ones.
     * 
     * @throws CoreException
     */
    static private void addJsNature(IProgressMonitor monitor, IProject project) throws CoreException {

        final String JS_NATURE = "org.eclipse.wst.jsdt.core.jsNature";
        if (!project.hasNature(JS_NATURE)) {

            IProjectDescription description = project.getDescription();
            String[] natures = description.getNatureIds();
            String[] newNatures = new String[natures.length + 1];

            System.arraycopy(natures, 0, newNatures, 0, natures.length);
            newNatures[natures.length] = JS_NATURE;

            description.setNatureIds(newNatures);
            project.setDescription(description, new SubProgressMonitor(monitor, 10));
        }
    }
}
