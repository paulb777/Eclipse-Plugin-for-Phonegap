/*
 * Copyright (C) 2010-2011 Mobile Developer Solutions
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

package com.mds.apg.wizards;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import java.io.File;

public final class PageInitContents extends WizardSection{

    // widgets
    private Button mCreateFromExampleRadio;
    private Button mBrowseButton;
    private Label mLocationLabel;
    private Text mLocationPathField;

    PageInitContents(AndroidPgProjectCreationPage wizardPage, Composite parent) {
        super(wizardPage);
        createGroup(parent);
    }
    
    /**
     * Creates the group for the Project options:
     * [radio] Use example source from phonegap directory
     * [radio] Create project from existing sources
     * Location [text field] [browse button]
     *
     * @param parent the parent composite
     */
    protected final void createGroup(Composite parent) {
        Group group = new Group(parent, SWT.SHADOW_ETCHED_IN);
        // Layout has 4 columns of non-equal size
        group.setLayout(new GridLayout());
        group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        group.setFont(parent.getFont());
        group.setText("Contents");
        mWizardPage.mContentsSection = group;  // Visibility can be adjusted by other widgets

        boolean initialVal = isCreateFromExample();
        mCreateFromExampleRadio = new Button(group, SWT.RADIO);
        mCreateFromExampleRadio.setText("Use phonegap example source as template for project");
        mCreateFromExampleRadio.setSelection(initialVal);
        mCreateFromExampleRadio.setToolTipText("Populate your project with the example shipped with your phonegap installation"); 
        
        Button existing_project_radio = new Button(group, SWT.RADIO);
        existing_project_radio.setText("Create project from source directory");
        existing_project_radio.setToolTipText("Specify root directory containing your sources that you wish to populate into the Android project"); 
        existing_project_radio.setSelection(!initialVal);

        SelectionListener location_listener = new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                super.widgetSelected(e);
                enableLocationWidgets();
                mWizardPage.validatePageComplete();
            }
        };

        mCreateFromExampleRadio.addSelectionListener(location_listener);
        existing_project_radio.addSelectionListener(location_listener);
        
        Composite location_group = new Composite(parent, SWT.NONE);
        location_group.setLayout(new GridLayout(3, /* num columns */
                false /* columns of not equal size */));
        location_group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        location_group.setFont(parent.getFont());

        mLocationLabel = new Label(location_group, SWT.NONE);
        mLocationLabel.setText("Location:");
        mLocationPathField = new Text(location_group, SWT.BORDER);       
        mBrowseButton = setupDirectoryBrowse(mLocationPathField, parent, location_group);
    }

    // --- Internal getters & setters ------------------

    @Override
    final String getValue() {
        return mLocationPathField == null ? "" : mLocationPathField.getText().trim(); //$NON-NLS-1$
    }
    
    @Override
    final Text getRawValue() {
        return mLocationPathField; 
    }
    
    @Override
    final String getStaticSave() {
        return AndroidPgProjectCreationPage.sCustomLocationOsPath;
    }
    
    @Override
    final void setStaticSave(String s) {
        AndroidPgProjectCreationPage.sCustomLocationOsPath = s;
    }

    /** Returns the value of the "Create from Existing Sample" radio. */
    /* TODO - Simplify like senchaChecked */
    
    protected boolean isCreateFromExample() {
        return mCreateFromExampleRadio == null ? AndroidPgProjectCreationPage.sUseFromExample  
                : mCreateFromExampleRadio.getSelection();
    }

    // --- UI Callbacks ----
    
    /**
     * Enables or disable the location widgets depending on the user selection:
     * the location path is enabled when using the "existing source" mode (i.e. not new project)
     * or in new project mode with the "use default location" turned off.
     */
    protected void enableLocationWidgets() {
        boolean location_enabled = !isCreateFromExample();
        mLocationLabel.setEnabled(location_enabled);
        mLocationPathField.setEnabled(location_enabled);
        mBrowseButton.setVisible(location_enabled);
        update(null);
        AndroidPgProjectCreationPage.sUseFromExample = !location_enabled;
        mWizardPage.doGetPreferenceStore().setValue(AndroidPgProjectCreationPage.USE_EXAMPLE_DIR, 
                location_enabled ? "" : "true");
    }

    
    /**
     * Validates the location path field.
     *
     * @return The wizard message type, one of MSG_ERROR, MSG_WARNING or MSG_NONE.
     */
    protected int validate() {
        File locationDir = new File(getValue());
        if (!locationDir.exists() || !locationDir.isDirectory()) {
            return mWizardPage.setStatus("A directory name must be specified.", AndroidPgProjectCreationPage.MSG_ERROR);
        } else {
            String[] l = locationDir.list();
            if (l.length == 0) {
                return mWizardPage.setStatus("The location directory is empty. It should include the source to populate the project", 
                        AndroidPgProjectCreationPage.MSG_ERROR);
            }
            boolean foundIndexHtml = false;

            for (String s : l) {
                if (s.equals("index.html")) {
                    foundIndexHtml = true;
                    break;
                }
            }
            if (!foundIndexHtml) {
                return mWizardPage.setStatus("The location directory must include an index.html file", 
                        AndroidPgProjectCreationPage.MSG_ERROR);
            }
            // TODO more validation
            
            // We now have a good directory, so set example path and save value
            mWizardPage.doGetPreferenceStore().setValue(AndroidPgProjectCreationPage.SOURCE_DIR, getValue()); 
            return AndroidPgProjectCreationPage.MSG_NONE;
        }
    }
}