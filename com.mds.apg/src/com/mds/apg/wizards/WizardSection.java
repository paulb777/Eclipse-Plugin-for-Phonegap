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

import java.io.File;

import org.eclipse.osgi.util.TextProcessor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

abstract public class WizardSection {
    private boolean mInternalPathUpdate;
    
    protected AndroidPgProjectCreationPage mWizardPage; // access the wizard page
    // from this helper class
    
    WizardSection(AndroidPgProjectCreationPage wizardPage) {
        mWizardPage = wizardPage;
    }
    
    protected abstract void createGroup(Composite parent);
    
    protected Button setupDirectoryBrowse(Text field, Composite parent, Composite group) {

        GridData data = new GridData(GridData.FILL, /* horizontal alignment */
                GridData.BEGINNING, /* vertical alignment */
            true, /* grabExcessHorizontalSpace */
            false, /* grabExcessVerticalSpace */
            1, /* horizontalSpan */
            1); /* verticalSpan */
        field.setLayoutData(data);
        field.setFont(parent.getFont());
        field.addListener(SWT.Modify, new Listener() {
            public void handleEvent(Event event) {
                onModified();
            }
        });

        Button button = new Button(group, SWT.PUSH);
        button.setText("Browse...");
        mWizardPage.setButtonLayoutData(button);
        button.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onOpenDirectoryBrowser();
            }
        });
        return button;
    }
    

    // --- Internal getters & setters ------------------
    
    abstract String getValue();
    
    abstract Text getRawValue();
    
    abstract String getStaticSave();
    
    abstract void setStaticSave(String s);
    

    // --- UI Callbacks ----

    /**
     * Display a directory browser and update the location path field with the
     * selected path
     */
    protected void onOpenDirectoryBrowser() {

        String existing_dir = getValue();

        // Disable the path if it doesn't exist
        if (existing_dir.length() == 0) {
            existing_dir = null;
        } else {
            File f = new File(existing_dir);
            if (!f.exists()) {
                existing_dir = null;
            }
        }

        DirectoryDialog dd = new DirectoryDialog(getRawValue().getShell());
        dd.setMessage("Browse for folder");
        dd.setFilterPath(existing_dir);
        String abs_dir = dd.open();

        if (abs_dir != null) {
            update(abs_dir);
            mWizardPage.validatePageComplete();
        }
    }
    
    /**
     * Updates a  directory path field.
     * <br/>
     * When custom user selection is enabled, use the abs_dir argument if not null and also
     * save it internally. If abs_dir is null, restore the last saved abs_dir. This allows the
     * user selection to be remembered when the user switches from default to custom.
     * <br/>
     * When custom user selection is disabled, use the workspace default location with the
     * current project name. This does not change the internally cached abs_dir.
     *
     * @param abs_dir A new absolute directory path or null to use the default.
     */ 
    void update(String abs_dir) {
        if (!mInternalPathUpdate) {
            mInternalPathUpdate = true;

            if (abs_dir != null) {
                // We get here if the user selected a directory with the "Browse" button.
                // Disable auto-compute of the custom location unless the user selected
                // the exact same path.
                setStaticSave(TextProcessor.process(abs_dir));
            }
            Text pathValue = getRawValue();
            String staticSave = getStaticSave();
            if (!pathValue.getText().equals(staticSave)) {
                pathValue.setText(staticSave);
            }

            mWizardPage.validatePageComplete();
            mInternalPathUpdate = false;
        }
    }

    /**
     * The location path field is either modified internally (from
     * updateLocationPathField) or manually by the user when the custom_location
     * mode is not set. Ignore the internal modification. When modified by the
     * user, memorize the choice and validate the page.
     */
    void onModified() {

        // When the updates doesn't come from updatePhonegapPathField, it must
        // be the user
        // editing the field manually, in which case we want to save the value
        // internally
        // and we disable auto-compute of the custom location (to avoid
        // overriding the user
        // value)
        if (!mInternalPathUpdate) {
            String newPath = getValue();
            setStaticSave(newPath);
            mWizardPage.validatePageComplete();
        }
    }
  
    abstract int validate();
}
