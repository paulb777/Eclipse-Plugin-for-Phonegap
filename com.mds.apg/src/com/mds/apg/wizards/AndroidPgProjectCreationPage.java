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
 */

package com.mds.apg.wizards;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.plugin.AbstractUIPlugin;



/**
 * AndroidProjectPgCreationPage is a project creation page that provides the
 * following fields:
 * <ul>
 * <li> phonegap directory
 * <li> location directory (of populating sources) name
 * </ul>
 */
public class AndroidPgProjectCreationPage extends WizardPage {

    // constants
    private static final String MAIN_PAGE_NAME = "newAndroidPgProjectPage"; 

    // Set up storage for persistent initializers
    protected final static String PHONEGAP_DIR = com.mds.apg.Activator.PLUGIN_ID + ".phonegap"; 
    protected final static String SOURCE_DIR = com.mds.apg.Activator.PLUGIN_ID + ".source"; 
    protected final static String USE_EXAMPLE_DIR = com.mds.apg.Activator.PLUGIN_ID + ".example";
    protected final static String SENCHA_DIR = com.mds.apg.Activator.PLUGIN_ID + ".senchadir";    
    protected final static String SENCHA_CHECK = com.mds.apg.Activator.PLUGIN_ID + ".senchacheck";

    protected IPreferenceStore doGetPreferenceStore() {
        return com.mds.apg.Activator.getDefault().getPreferenceStore();
    }

    /** Last user-browsed location, static so that it be remembered for the whole session */
    protected static String sCustomLocationOsPath = "";  
    protected static String sPhonegapPathCache = "";
    protected static String sSenchaPathCache = "";
    protected static boolean sUseFromExample = false;
    protected static boolean sSenchaCheck = false;

    protected final static int MSG_NONE = 0;
    protected final static int MSG_WARNING = 1;
    protected final static int MSG_ERROR = 2;

    // page sub-sections
 
    private PagePhonegapPathSet mPhonegapDialog;
    private PageSencha mSenchaDialog;
    private PageInitContents mInitContentsDialog;
    
    protected Group mContentsSection; // Manipulate Contents Section visibility

    /**
     * Creates a new project creation wizard page.
     */
    public AndroidPgProjectCreationPage() {
        super(MAIN_PAGE_NAME);
        setPageComplete(false);
        setTitle("Create a Phonegap for Android Project");
        setDescription("Set location of phonegap directory and populating sources");
        ImageDescriptor desc = AbstractUIPlugin.imageDescriptorFromPlugin("com.mds.apg", "icons/phonegap.png");
        setImageDescriptor(desc);
        sPhonegapPathCache = doGetPreferenceStore().getString(PHONEGAP_DIR);  
        sCustomLocationOsPath = doGetPreferenceStore().getString(SOURCE_DIR);  
        sUseFromExample = doGetPreferenceStore().getString(USE_EXAMPLE_DIR) != "" ; // returns false if unset
        sSenchaPathCache = doGetPreferenceStore().getString(SENCHA_DIR);        
        sSenchaCheck = doGetPreferenceStore().getString(SENCHA_CHECK) != "" ; // returns false if unset
    }

    /**
     * Overrides @DialogPage.setVisible(boolean) to put the focus in the project name when
     * the dialog is made visible.
     */
    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            mPhonegapDialog.mPhonegapPathField.setFocus();
            validatePageComplete();
        }
    }

    // --- UI creation ---

    /**
     * Creates the top level control for this dialog page under the given parent
     * composite.
     *
     * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
     */
    public void createControl(Composite parent) {
        final ScrolledComposite scrolledComposite = new ScrolledComposite(parent, SWT.V_SCROLL);
        scrolledComposite.setFont(parent.getFont());
        scrolledComposite.setExpandHorizontal(true);
        scrolledComposite.setExpandVertical(true);
        initializeDialogUnits(parent);

        final Composite composite = new Composite(scrolledComposite, SWT.NULL);
        composite.setFont(parent.getFont());
        scrolledComposite.setContent(composite);

        composite.setLayout(new GridLayout());
        composite.setLayoutData(new GridData(GridData.FILL_BOTH));

        mPhonegapDialog = new PagePhonegapPathSet(this, composite);
        mSenchaDialog = new PageSencha(this, composite);
        mInitContentsDialog = new PageInitContents(this, composite);
        mInitContentsDialog.enableLocationWidgets();          // Update state the first time

        scrolledComposite.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                Rectangle r = scrolledComposite.getClientArea();
                scrolledComposite.setMinSize(composite.computeSize(r.width, SWT.DEFAULT));
            }
        });

        // Show description the first time
        setErrorMessage(null);
        setMessage(null);
        setControl(scrolledComposite);

        // Validate. This will complain about the first empty field.
        validatePageComplete();
    }

    //--- Getters & setters for the wizard controller ------------------

    protected String getPhonegapPathFieldValue() {
        return mPhonegapDialog.getValue();
    }
    
    /** Returns the location path field value with spaces trimmed. */
    protected String getLocationPathFieldValue() {
        return mInitContentsDialog.getValue();
    }
    
    /** Returns the location path field value with spaces trimmed. */
    protected boolean isCreateFromExample() {
        return mInitContentsDialog.isCreateFromExample();
    }
    
    /** Will the project be configured with Sencha Touch? */
    protected boolean senchaChecked() {
        return mSenchaDialog.senchaChecked();
    }

    /** Will the project be configured with Sencha Touch? */
    protected String getSenchaDirectory() {
        return mSenchaDialog.getValue();
    }

    /** Will the project the Sencha Kitchen Sink */
    protected boolean useSenchaKitchenSink() {
        return mSenchaDialog.useSenchaKitchenSink();
    }


    // --- UI Callbacks ----

    /**
     * Returns whether this page's controls currently all contain valid values.
     *
     * @return <code>true</code> if all controls are valid, and
     *         <code>false</code> if at least one is invalid
     */
    private boolean validatePage() {

        int status = mPhonegapDialog.validate();
        
        if (status == MSG_NONE) {
            status = mSenchaDialog.validate();

            if (status == MSG_NONE) {
                if (mSenchaDialog.useSenchaKitchenSink()) {
                    mInitContentsDialog.update(mSenchaDialog.getValue() + "/examples/kitchensink");
                } else if (isCreateFromExample()) {
                    mInitContentsDialog.update(getPhonegapPathFieldValue() + "/example");
                }
                status |= mInitContentsDialog.validate();

                if (status == MSG_NONE) {
                    setStatus(null, MSG_NONE);
                }
            }
        }

        // Return false if there's an error so that the finish button be disabled.
        return (status & MSG_ERROR) == 0;
    }

    /**
     * Validates the page and updates the Next/Finish buttons
     */
    protected void validatePageComplete() {
        setPageComplete(validatePage());
    }


    /**
     * Sets the error message for the wizard with the given message icon.
     *
     * @param message The wizard message type, one of MSG_ERROR or MSG_WARNING.
     * @return As a convenience, always returns messageType so that the caller can return
     *         immediately.
     */
    protected int setStatus(String message, int messageType) {
        if (message == null) {
            setErrorMessage(null);
            setMessage(null);
        } else if (!message.equals(getMessage())) {
            setMessage(message, messageType == MSG_WARNING ? WizardPage.WARNING : WizardPage.ERROR);
        }
        return messageType;
    }
    
    /**
     * Give access to super's private to helper functions
     *
     */

    protected GridData setButtonLayoutData(Button button) {
        return super.setButtonLayoutData(button);
    }

}
