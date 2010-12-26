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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import java.io.File;

public final class PagePhonegapPathSet extends WizardSection {

    // widgets
    private Label mPhonegapLabel;
    Text mPhonegapPathField;
    
    PagePhonegapPathSet(AndroidPgProjectCreationPage wizardPage, Composite parent) {
        super(wizardPage);
        createGroup(parent);
    }

    /**
     * Creates the group for the phonegap path:
     * 
     * @param parent the parent composite
     */
    protected final void createGroup(Composite parent) {
        Composite group = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        group.setLayout(layout);
        group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        // Set up layout for phonegap location entry
        {
            Composite phonegapGroup = new Composite(group, SWT.NONE);
            phonegapGroup.setLayout(new GridLayout(2, /* num columns */
                false /* columns of not equal size */));
                phonegapGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
                phonegapGroup.setFont(parent.getFont());

            mPhonegapLabel = new Label(phonegapGroup, SWT.NONE);
            mPhonegapLabel.setText("Enter path to phonegap-android");
            mPhonegapLabel
                    .setToolTipText("Should be the path to the unpacked phonegap-android installation");
            Text forceNewLine = new Text(phonegapGroup, SWT.TRANSPARENCY_NONE); // force new line                                                                                
            forceNewLine.setText("");
            
            mPhonegapPathField = new Text(phonegapGroup, SWT.BORDER);
            mPhonegapPathField.setText(getStaticSave());
            setupDirectoryBrowse(mPhonegapPathField, parent, phonegapGroup);
        }
    }

    // --- Internal getters & setters ------------------

    final String getValue() {
        return mPhonegapPathField == null ? "" : mPhonegapPathField.getText().trim(); //$NON-NLS-1$
    }
    
    final Text getRawValue() {
        return mPhonegapPathField; 
    }
    
    final String getStaticSave() {
        return AndroidPgProjectCreationPage.sPhonegapPathCache;
    }
    
    final void setStaticSave(String s) {
        AndroidPgProjectCreationPage.sPhonegapPathCache = s;
    }


    // --- UI Callbacks ----

    /**
     * Validates the phonegap path field. Make sure there is at least an example
     * and framework sub-directory
     * 
     * @return The wizard message type, one of MSG_ERROR, MSG_WARNING or
     *         MSG_NONE.
     */
    int validate() {
        File phonegapDir = new File(getValue());
        if (!phonegapDir.exists() || !phonegapDir.isDirectory()) {
            return mWizardPage.setStatus("A phonegap directory name must be specified.",  AndroidPgProjectCreationPage.MSG_ERROR);
        } else {
            String[] l = phonegapDir.list();
            if (l.length == 0) {
                return mWizardPage.setStatus("The phonegap directory is empty.", AndroidPgProjectCreationPage.MSG_ERROR);
            }
            boolean foundFramework = false;
            boolean foundExample = false;

            for (String s : l) {
                if (s.equals("example"))
                    foundExample = true;
                if (s.equals("framework"))
                    foundFramework = true;
            }
            if ((!foundFramework) || (!foundExample)) {
                return mWizardPage.setStatus(
                                "The phonegap directory has been corrupted. It is missing the framework and/or example subdirectory",
                                AndroidPgProjectCreationPage.MSG_ERROR);
            }
            // TODO more validation

            // We now have a good directory, so set example path and save value
            mWizardPage.doGetPreferenceStore().setValue(AndroidPgProjectCreationPage.PHONEGAP_DIR,
                    getValue());

            return AndroidPgProjectCreationPage.MSG_NONE;
        }
    }
}