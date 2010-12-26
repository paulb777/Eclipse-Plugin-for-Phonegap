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
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

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
        final PageInfo pageInfo = new PageInfo(mPhonegapPage.getLocationPathFieldValue(),
                mPhonegapPage.getPhonegapPathFieldValue(), 
                Platform.getLocation().toString() + "/"+ mNewAndroidProject.getName() + "/",
                mNewAndroidProject,
                mPhonegapPage.getSenchaDirectory(),
                mPhonegapPage.senchaChecked(), 
                mPhonegapPage.useSenchaKitchenSink());

        // Create a monitored operation to create the actual project
        WorkspaceModifyOperation op = new WorkspaceModifyOperation() {
            @Override
            protected void execute(IProgressMonitor monitor) throws InvocationTargetException {
                PhonegapProjectPopulate.createProjectAsync(monitor, pageInfo);
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

            // AdtPlugin.log(e, "New Project Wizard failed");

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
    
    // Thanks to
    // http://code.hammerpig.com/convert-an-array-to-an-arraylist-in-java.html
    // and
    // http://code.hammerpig.com/find-the-difference-between-two-lists-in-java.html

    private static ArrayList<String> CreateStringList(String... values) {
        ArrayList<String> results = new ArrayList<String>();
        Collections.addAll(results, values);
        return results;
    }
    
    private IProject findNewAndroidProject(String[] pre, String[] post) {
        ArrayList<String> preList = CreateStringList(pre);
        ArrayList<String> postList = CreateStringList(post);
        Collection<String> result = new ArrayList<String>(postList);
        result.removeAll(preList);

        if (result.size() == 1) { // insure there is only one new directory -
                                  // our new project
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            return workspace.getRoot().getProject((String) result.iterator().next());
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