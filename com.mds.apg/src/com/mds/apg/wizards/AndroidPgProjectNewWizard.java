/*
 * Copyright (C) 2010 Mobile Developer Solutions
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

import com.android.ide.eclipse.adt.internal.wizards.newproject.NewProjectCreationPage;
import com.android.ide.eclipse.adt.internal.wizards.newproject.NewProjectWizard;


public class AndroidPgProjectNewWizard extends NewProjectWizard implements INewWizard {
    private AndroidPgProjectCreationPage mPhonegapPage;
    private IProject mNewAndroidProject;


    public void init(IWorkbench workbench, IStructuredSelection selection) {       
        setHelpAvailable(false); // TODO have help
        mPhonegapPage = createPhonegapPage();
        super.init(workbench, selection);
        setWindowTitle("Add Phonegap to Android project "); 
    }


    @Override
    public void addPages() {
        addPage(mPhonegapPage);
        super.addPages();
    }

    /**
     * Creates the main wizard page.
     * This is protected so that it can be overridden by unit tests.
     * However the contract of this class is private and NO ATTEMPT will be made
     * to maintain compatibility between different versions of the plugin.
     */
    protected AndroidPgProjectCreationPage createPhonegapPage() {
        return new AndroidPgProjectCreationPage();
    }

    /**
     * Performs any actions appropriate in response to the user having pressed
     * the Finish button, or refuse if finishing now is not permitted: here, it
     * actually creates the Android workspace project and then adds in the 
     * phonegap components
     *
     * @return True
     */
    @Override
    public boolean performFinish() {
        String[] preAndroidCreateDirectoryList = getWorkspaceDirectoryList();
        if (!super.performFinish() || super.getPackageName() == "") return false;
        String[] postAndroidCreateDirectoryList = getWorkspaceDirectoryList();

        //  should be one new file corresponding to the new Android project
        mNewAndroidProject = findNewAndroidProject(preAndroidCreateDirectoryList, postAndroidCreateDirectoryList);

        if (mNewAndroidProject == null) return false;        
        if (!populatePhonegapComponents()) return false;

        //   TO do - open index.html and JavaScript perspective
        // Open the default Java Perspective
        //                OpenJavaScriptPerspectiveAction action = new OpenJavaScriptPerspectiveAction();
        //                action.run();
        return true;
    }

    /**
     * Start a monitor and thread for updating the project
     * @return True if the project could be created.
     */
    private boolean populatePhonegapComponents() {
        final String sourceDirectory = mPhonegapPage.getLocationPathFieldValue();
        final String phonegapDirectory = mPhonegapPage.getPhonegapPathFieldValue();

        // Create a monitored operation to create the actual project
        WorkspaceModifyOperation op = new WorkspaceModifyOperation() {
            @Override
            protected void execute(IProgressMonitor monitor) throws InvocationTargetException {
                createProjectAsync(monitor, sourceDirectory, phonegapDirectory, 
                        Platform.getLocation().toString() + "/" + mNewAndroidProject.getName() + "/",
                        mNewAndroidProject);
            }
        };

        // Run the operation in a different thread
        runAsyncOperation(op);
        return true;
    }

    /**
     * Runs the operation in a different thread and display generated
     * exceptions.
     *
     * @param op The asynchronous operation to run.
     */
    private void runAsyncOperation(WorkspaceModifyOperation op) {
        try {
            getContainer().run(true /* fork */, true /* cancelable */, op);
        } catch (InvocationTargetException e) {

            //            AdtPlugin.log(e, "New Project Wizard failed");

            // The runnable threw an exception
            Throwable t = e.getTargetException();
            if (t instanceof CoreException) {
                CoreException core = (CoreException) t;
                if (core.getStatus().getCode() == IResourceStatus.CASE_VARIANT_EXISTS) {
                    // The error indicates the file system is not case sensitive
                    // and there's a resource with a similar name.
                    MessageDialog.openError(getShell(), "Error", "Error: Case Variant Exists");
                } else {
                    ErrorDialog.openError(getShell(), "Error", core.getMessage(), core.getStatus());
                }
            } else {
                // Some other kind of exception
                String msg = t.getMessage();
                Throwable t1 = t;
                while (msg == null && t1.getCause() != null) {
                    msg = t1.getMessage();
                    t1 = t1.getCause();
                }
                if (msg == null) {
                    msg = t.toString();
                }
                MessageDialog.openError(getShell(), "Error", msg);
            }
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates the actual project(s). This is run asynchronously in a different thread.
     *
     * @param monitor An existing monitor.
     * @param mainData Data for main project. Can be null.
     * @throws InvocationTargetException to wrap any unmanaged exception and
     *         return it to the calling thread. The method can fail if it fails
     *         to create or modify the project or if it is canceled by the user.
     */
    private void createProjectAsync(IProgressMonitor monitor,
            String sourceDirectory,
            String phonegapDirectory,
            String destinationDirectory,
            IProject newAndroidProject)
    throws InvocationTargetException {
        monitor.beginTask("Create Android Project", 100);
        try {
            updateProjectWithPhonegap(monitor, sourceDirectory, phonegapDirectory, destinationDirectory, newAndroidProject);

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
     * 2. Get the phonegap.jar and update the classpath with it
     * 3. Get the user's sources into the android project
     * 4. Update the AndroidManifest file
     * 5. Fill the res directory with drawables and layout
     * 6. Update the project nature so that JavaScript files are recognize
     * 7. Refresh the project with the updated disc files
     * 8. Do a clean build
     * 
     * Note that there can still be problems where R.java is out of synch, 
     * but that seems to be a generic Android problem
     * 
     * @param monitor An existing monitor.
     * @throws InvocationTargetException to wrap any unmanaged exception and
     *         return it to the calling thread. The method can fail if it fails
     *         to create or modify the project or if it is canceled by the user.
     */

    private void updateProjectWithPhonegap(IProgressMonitor monitor,
            String sourceDirectory,
            String phonegapDirectory,
            String destinationDirectory,
            IProject newAndroidProject) throws CoreException, IOException, URISyntaxException {

        updateJavaMain(destinationDirectory);
        getPhonegapJar(monitor, phonegapDirectory, newAndroidProject);
        getWWWSources(monitor, sourceDirectory, phonegapDirectory, newAndroidProject);
        phonegapizeAndroidManifest(phonegapDirectory, destinationDirectory);
        getResFiles(monitor, phonegapDirectory, destinationDirectory);
        addJsNature(monitor, newAndroidProject);
        newAndroidProject.refreshLocal(2 /* DEPTH_INFINITE */, monitor);
        newAndroidProject.build(IncrementalProjectBuilder.CLEAN_BUILD, monitor);
    }


    /**
     * Find and update the main java file to kick off phonegap
     * @throws IOException 
     */
    private void updateJavaMain(String destDir) throws IOException {
        String javaFile = findJavaFile(destDir + "src");
        String javaFileContents = StringIO.read(javaFile);

        // Import com.phonegap instead of Activity
        javaFileContents = javaFileContents.replace("import android.app.Activity;", "import com.phonegap.*;"); 

        // Change superclass to DroidGap instead of Activity
        javaFileContents = javaFileContents.replace("extends Activity", "extends DroidGap");

        // Change to start with index.html
        javaFileContents = javaFileContents.replace("setContentView(R.layout.main);", "super.loadUrl(\"file:///android_asset/www/index.html\");");  

        // Write out the file
        StringIO.write(javaFile, javaFileContents);
    }

    // Recursively search for java file.  Assuming there is only one in the new Android project

    private String findJavaFile(String dir)
    {
        String retVal;
        File f = new File(dir);
        if (f.isDirectory()) {
            String fList[] = f.list();
            for (String s : fList) {
                if (s.length() > 5 && s.indexOf(".java") == s.length() - 5) {
                    return dir + '/' + s;
                } else {
                    retVal = findJavaFile(dir + '/' + s);
                    if (retVal != null) return retVal;
                }
            }
        }
        return null;
    }


    /**
     * It turns out that phonegap.jar does not yet exist in a raw phonegap installation
     * It needs to be built with the Android installation.  
     * So instead, we'll get the sources, so that it just gets build with our product.
     * We also need to get /framework/libs/commons-codec-1.3.jar upon which the sources depend 
     * 
     * @throws URISyntaxException 
     */
    private void getPhonegapJar(IProgressMonitor monitor,
            String phonegapDirectory,
            IProject newAndroidProject) throws CoreException,IOException, URISyntaxException {
        
        FileCopy.recursiveCopy(phonegapDirectory + "/" + "framework" + "/" + "src",
                Platform.getLocation().toString() + "/" + newAndroidProject.getName() + "/" + "src");

        addDefaultDirectories(newAndroidProject, "/", new String[]{"libs"} , monitor);

        String libsDir = Platform.getLocation().toString() + "/" + newAndroidProject.getName() + "/" + "libs" + "/";
        final String commonCodecLoc = "commons-codec-1.3.jar";

        FileCopy.copy(phonegapDirectory + "/" + "framework" + "/" + "libs" + "/" + commonCodecLoc,
                libsDir + commonCodecLoc);

        // Now update classpath  .classpath should end up like the following.  Note that the path specifies that 
        // phonegap.jar should be included in the build.  sourcepath enables the phonegap source to be found for debugging
        // when doing creation the "output" line doesn't yet exist, so the new line goes last
        //
        // <?xml version="1.0" encoding="UTF-8"?>
        //<classpath>
        //        <classpathentry kind="src" path="src"/>
        //        <classpathentry kind="src" path="gen"/>
        //        <classpathentry kind="con" path="com.android.ide.eclipse.adt.ANDROID_FRAMEWORK"/>
        //        <classpathentry kind="lib" path="libs/phonegap.jar" sourcepath="/Users/paulb/phonegap-android101023/framework/src"/>
        //        <classpathentry kind="output" path="bin"/>
        //</classpath>

        IJavaProject javaProject =  (IJavaProject)newAndroidProject.getNature(JavaCore.NATURE_ID);   // thanks to Larry Isaacs http://dev.eclipse.org/newslists/news.eclipse.webtools/msg10002.html
        IClasspathEntry[] classpathList = javaProject.readRawClasspath();
        IClasspathEntry[] newClasspaths = new IClasspathEntry[classpathList.length + 1];
        System.arraycopy(classpathList, 0, newClasspaths, 0, classpathList.length);

        // Create the new Classpath entry 

        IClasspathEntry newPath = JavaCore.newLibraryEntry(new Path(libsDir + commonCodecLoc),
                new Path(phonegapDirectory + "/" + "framework" + "/" + "src" + "/"), null);

        newClasspaths[classpathList.length] = newPath;

        // write it back out with 
        javaProject.setRawClasspath(newClasspaths, monitor);       
    }


    /**
     * Get the sources from the example directory or alternative specified directory
     * Place them in assets/www
     * Also get phonegap.js from framework assets
     * @throws URISyntaxException 
     */
    private void getWWWSources(IProgressMonitor monitor,
            String sourceDirectory,
            String phonegapDirectory,
            IProject newAndroidProject) throws CoreException,IOException, URISyntaxException {

        addDefaultDirectories(newAndroidProject, "assets/", new String[]{"www"}, monitor);
        String wwwDir = Platform.getLocation().toString() + "/" + newAndroidProject.getName() + "/" + 
        "assets" + "/" + "www" + "/";

        FileCopy.recursiveCopy(sourceDirectory, wwwDir);

        // Even though there is a phonegap.js file in the directory framework/assets/www, it is WRONG!!  
        // phonegap.js must be constructed from the files
        // in framework/assets/js
        
        FileCopy.createPhonegapJs(phonegapDirectory + "/" + "framework" + "/" + "assets" + "/" + "js",
                wwwDir + "phonegap.js");
    }

    /**
     * Get the Android Manifest file and tweak it for phonegap
     * @throws URISyntaxException 
     */
    private void phonegapizeAndroidManifest(
            String phonegapDirectory,
            String destDir) throws CoreException,IOException, URISyntaxException {

        String destFile = destDir + "AndroidManifest.xml";
        String sourceFile = phonegapDirectory + "/" + "framework" + "/" + "AndroidManifest.xml";
        String sourceFileContents = StringIO.read(sourceFile);
        String manifestInsert = getManifestScreensAndPermissions(sourceFileContents); 
        String destFileContents = StringIO.read(destFile);

        // Add phonegap screens, permissions and turn on debuggable
        destFileContents = destFileContents.replace("<application android:", manifestInsert + "<application" + " android:debuggable=\"true\" android:");

        // Add android:configChanges="orientation|keyboardHidden" to the activity
        destFileContents = destFileContents.replace("<activity android:", "<activity android:configChanges=\"orientation|keyboardHidden\" android:");

        if (destFileContents.indexOf("<uses-sdk") < 0) {
            // User did not set min SDK, so use the phonegap template manifest version
            int startIndex = sourceFileContents.indexOf("<uses-sdk");
            int endIndex = sourceFileContents.indexOf("<",startIndex + 1);
            destFileContents = destFileContents.replace("</manifest>", sourceFileContents.substring(startIndex,endIndex) + "</manifest>");
        }
        // Write out the file
        StringIO.write(destFile, destFileContents);
    } 

    /**
     * Helper Function for phonegapizeAndroidManifest
     * It finds the big middle section that needs to be added to the manifest for phonegap
     */

    private String getManifestScreensAndPermissions(String manifest) {
        int startIndex;
        startIndex = manifest.indexOf("<supports-screens");
        if (startIndex == -1) startIndex = manifest.indexOf("<uses-permissions");
        if (startIndex == -1) return null;
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
     * Copy anything in res/layout
     * Copy drawable to drawable*
     * Leave values alone since string maps to app name
     * @throws URISyntaxException 
     */
    private void getResFiles(IProgressMonitor monitor,
            String phonegapDirectory,
            String destDir) throws CoreException,IOException, URISyntaxException {

        String sourceResDir = phonegapDirectory + "/" + "framework" + "/" + "res" + "/"  ;
        String destResDir = destDir + "res" + "/" ;

        FileCopy.recursiveForceCopy(sourceResDir + "layout" + "/", destResDir + "layout" + "/");

        // Copy source drawable to all of the project drawable* directories
        String sourceDrawableDir = sourceResDir + "drawable" + "/" ;
        File destFile = new File(destResDir);
        String fList[] = destFile.list();
        for (String s : fList) {
            if (s.indexOf("drawable") == 0) {
                FileCopy.recursiveForceCopy(sourceDrawableDir, destResDir + s);
            }
        }
    }


    /**
     * Adds default directories to the project.  Unchanged from private version in parent class
     *
     * @param project The Java Project to update.
     * @param parentFolder The path of the parent folder. Must end with a
     *        separator.
     * @param folders Folders to be added.
     * @param monitor An existing monitor.
     * @throws CoreException if the method fails to create the directories in
     *         the project.
     */
    private void addDefaultDirectories(IProject project, String parentFolder,
            String[] folders, IProgressMonitor monitor) throws CoreException {
        for (String name : folders) {
            if (name.length() > 0) {
                IFolder folder = project.getFolder(parentFolder + name);
                if (!folder.exists()) {
                    folder.create(true /* force */, true /* local */,
                            new SubProgressMonitor(monitor, 10));
                }
            }
        }
    }    


    /**
     * Add JavaScript nature to the project.  It gets added last after Android and Java ones.
     * @throws CoreException 
     */ 
    private void addJsNature(IProgressMonitor monitor, IProject project) throws CoreException {

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
    // Thanks to http://code.hammerpig.com/convert-an-array-to-an-arraylist-in-java.html and
    // http://code.hammerpig.com/find-the-difference-between-two-lists-in-java.html

    private static ArrayList<String> CreateStringList(String ... values)
    {
        ArrayList<String> results = new ArrayList<String>();
        Collections.addAll(results, values);
        return results;
    }

    private IProject findNewAndroidProject(String[] pre, String[] post) {
        ArrayList<String> preList = CreateStringList(pre);
        ArrayList<String> postList = CreateStringList(post);
        Collection<String> result = new ArrayList<String>(postList);
        result.removeAll(preList);

        if (result.size() == 1) {   // insure there is only one new directory - our new project     
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            return workspace.getRoot().getProject((String)result.iterator().next());
        } else {
            return null;
        }
    }

    private String[] getWorkspaceDirectoryList() {
        String workspaceLocation = Platform.getLocation().toString();
        File f = new File(workspaceLocation);
        return f.list();
    }
}