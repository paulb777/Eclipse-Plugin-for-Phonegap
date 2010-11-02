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
import org.eclipse.osgi.util.TextProcessor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import java.io.File;


/**
 * NewAndroidProjectPgCreationPage is a project creation page that provides the
 * following fields:
 * <ul>
 * <li> phonegap directory
 * <li> location directory (of populating sources) name
 * </ul>
 * Note: this class is public so that it can be accessed from unit tests.
 * It is however an internal class. Its API may change without notice.
 * It should semantically be considered as a private final class.
 * Do not derive from this class.
 */
public class AndroidPgProjectCreationPage extends WizardPage {

    // constants
    private static final String MAIN_PAGE_NAME = "newAndroidPgProjectPage"; 

    // Set up storage for persistent initializers
    public final static String PHONEGAP_DIR = com.mds.apg.Activator.PLUGIN_ID + ".phonegap"; 
    public final static String SOURCE_DIR = com.mds.apg.Activator.PLUGIN_ID + ".source"; 
    public final static String USE_EXAMPLE_DIR = com.mds.apg.Activator.PLUGIN_ID + ".example";

    protected IPreferenceStore doGetPreferenceStore() {
        return com.mds.apg.Activator.getDefault().getPreferenceStore();
    }


    /** Last user-browsed location, static so that it be remembered for the whole session */
    private String sCustomLocationOsPath = "";  
    private String sCustomPhonegapOsPath = "";
    private static boolean sAutoComputeCustomLocation = true;
    private static boolean sAutoComputeCustomPhonegap = true;
    private boolean sUseFromExample = false;

    private final int MSG_NONE = 0;
    private final int MSG_WARNING = 1;
    private final int MSG_ERROR = 2;

    // widgets
    private Button mCreateFromExampleRadio;
    private Label mPhonegapLabel;
    private Label mLocationLabel;
    private Text mPhonegapPathField;
    private Text mLocationPathField;
    private Button mBrowseButton;
    private boolean mInternalPathUpdate;  

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
        sCustomPhonegapOsPath = doGetPreferenceStore().getString(PHONEGAP_DIR);  
        sCustomLocationOsPath = doGetPreferenceStore().getString(SOURCE_DIR);  
        sUseFromExample = doGetPreferenceStore().getString(USE_EXAMPLE_DIR) != "" ; // returns false if unset
    }

    /** Returns the value of the "Create from Existing Sample" radio. */
    private boolean isCreateFromExample() {
        return mCreateFromExampleRadio == null ? sUseFromExample  
                : mCreateFromExampleRadio.getSelection();
    }

    /**
     * Overrides @DialogPage.setVisible(boolean) to put the focus in the project name when
     * the dialog is made visible.
     */
    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            mPhonegapPathField.setFocus();
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

        createPhonegapPathGroup(composite);
        createLocationGroup(composite);

        // Update state the first time
        enableLocationWidgets();

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


    /**
     * Creates the group for the phonegap path:

     *
     * @param parent the parent composite
     */
    private final void createPhonegapPathGroup(Composite parent) {
        Composite group = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        group.setLayout(layout);
        group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        // Set up layout for phonegap location entry
        {
            Composite phonegap_group = new Composite(group, SWT.NONE);
            phonegap_group.setLayout(new GridLayout(2, /* num columns */
                    false /* columns of not equal size */));
            phonegap_group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            phonegap_group.setFont(parent.getFont());

            mPhonegapLabel = new Label(phonegap_group, SWT.NONE);
            mPhonegapLabel.setText("Enter path to phonegap-android");
            mPhonegapLabel.setToolTipText("Should be the path to the unpacked phonegap-android installation"); 
            Text forceNewLine = new Text(phonegap_group, SWT.TRANSPARENCY_NONE);  // force new line
            forceNewLine.setText("");
            mPhonegapPathField = new Text(phonegap_group, SWT.BORDER);
            mPhonegapPathField.setText(sCustomPhonegapOsPath);
            GridData data = new GridData(GridData.FILL, /* horizontal alignment */
                    GridData.BEGINNING, /* vertical alignment */
                    true,  /* grabExcessHorizontalSpace */
                    false, /* grabExcessVerticalSpace */
                    1,     /* horizontalSpan */
                    1);    /* verticalSpan */
            mPhonegapPathField.setLayoutData(data);
            mPhonegapPathField.setFont(parent.getFont());
            mPhonegapPathField.addListener(SWT.Modify, new Listener() {
                public void handleEvent(Event event) {
                    onPhonegapPathFieldModified();
                }
            });

            Button pgBrowseButton = new Button(phonegap_group, SWT.PUSH);
            pgBrowseButton.setText("Browse...");
            setButtonLayoutData(pgBrowseButton);
            pgBrowseButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    onOpenPhonegapDirectoryBrowser();
                }
            }); 
        }   
    }


    /**
     * Creates the group for the Project options:
     * [radio] Create new project
     * [radio] Create project from existing sources
     * [check] Use default location
     * Location [text field] [browse button]
     *
     * @param parent the parent composite
     */
    private final void createLocationGroup(Composite parent) {
        Group group = new Group(parent, SWT.SHADOW_ETCHED_IN);
        // Layout has 4 columns of non-equal size
        group.setLayout(new GridLayout());
        group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        group.setFont(parent.getFont());
        group.setText("Contents");

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
                validatePageComplete();
            }
        };

        mCreateFromExampleRadio.addSelectionListener(location_listener);
        existing_project_radio.addSelectionListener(location_listener);

        Composite location_group = new Composite(group, SWT.NONE);
        location_group.setLayout(new GridLayout(3, /* num columns */
                false /* columns of not equal size */));
        location_group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        location_group.setFont(parent.getFont());

        mLocationLabel = new Label(location_group, SWT.NONE);
        mLocationLabel.setText("Location:");

        mLocationPathField = new Text(location_group, SWT.BORDER);
        GridData data = new GridData(GridData.FILL, /* horizontal alignment */
                GridData.BEGINNING, /* vertical alignment */
                true,  /* grabExcessHorizontalSpace */
                false, /* grabExcessVerticalSpace */
                1,     /* horizontalSpan */
                1);    /* verticalSpan */
        mLocationPathField.setLayoutData(data);
        mLocationPathField.setFont(parent.getFont());
        mLocationPathField.addListener(SWT.Modify, new Listener() {
            public void handleEvent(Event event) {
                onLocationPathFieldModified();
            }
        });

        mBrowseButton = new Button(location_group, SWT.PUSH);
        mBrowseButton.setText("Browse...");
        setButtonLayoutData(mBrowseButton);
        mBrowseButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onOpenDirectoryBrowser();
            }
        });
    }

    //--- Internal getters & setters ------------------

    /** Returns the location path field value with spaces trimmed. */
    protected String getLocationPathFieldValue() {
        return mLocationPathField == null ? "" : mLocationPathField.getText().trim();  //$NON-NLS-1$
    }

    protected String getPhonegapPathFieldValue() {
        return mLocationPathField == null ? "" : mPhonegapPathField.getText().trim();  //$NON-NLS-1$
    }

    // --- UI Callbacks ----

    /**
     * Display a directory browser and update the location path field with the selected path
     */
    private void onOpenPhonegapDirectoryBrowser() {

        String existing_dir = getPhonegapPathFieldValue();

        // Disable the path if it doesn't exist
        if (existing_dir.length() == 0) {
            existing_dir = null;
        } else {
            File f = new File(existing_dir);
            if (!f.exists()) {
                existing_dir = null;
            }
        }

        DirectoryDialog dd = new DirectoryDialog(mPhonegapPathField.getShell());
        dd.setMessage("Browse for folder");
        dd.setFilterPath(existing_dir);
        String abs_dir = dd.open();

        if (abs_dir != null) {
            updatePhonegapPathField(abs_dir);
            validatePageComplete();
        }
    }

    /**
     * Display a directory browser and update the location path field with the selected path
     */
    private void onOpenDirectoryBrowser() {

        String existing_dir = getLocationPathFieldValue();

        // Disable the path if it doesn't exist
        if (existing_dir.length() == 0) {
            existing_dir = null;
        } else {
            File f = new File(existing_dir);
            if (!f.exists()) {
                existing_dir = null;
            }
        }

        DirectoryDialog dd = new DirectoryDialog(mLocationPathField.getShell());
        dd.setMessage("Browse for folder");
        dd.setFilterPath(existing_dir);
        String abs_dir = dd.open();

        if (abs_dir != null) {
            updateLocationPathField(abs_dir);
            validatePageComplete();
        }
    }

    /**
     * Enables or disable the location widgets depending on the user selection:
     * the location path is enabled when using the "existing source" mode (i.e. not new project)
     * or in new project mode with the "use default location" turned off.
     */
    private void enableLocationWidgets() {
        boolean location_enabled = !isCreateFromExample();
        mLocationLabel.setEnabled(location_enabled);
        mLocationPathField.setEnabled(location_enabled);
        mBrowseButton.setEnabled(location_enabled);
        updateLocationPathField(null);
        sUseFromExample = !location_enabled;
        doGetPreferenceStore().setValue(USE_EXAMPLE_DIR, location_enabled ? "" : "true");
    }

    /**
     * Updates the phonegap directory path field.
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
    private void updatePhonegapPathField(String abs_dir) {

        if (!mInternalPathUpdate) {
            mInternalPathUpdate = true;

            // We get here if the user selected a directory with the "Browse" button.
            // Disable auto-compute of the custom Phonegap unless the user selected
            // the exact same path.
            sAutoComputeCustomPhonegap = sAutoComputeCustomPhonegap &&
            abs_dir.equals(sCustomPhonegapOsPath);
            sCustomPhonegapOsPath = TextProcessor.process(abs_dir);

            if (!mPhonegapPathField.getText().equals(sCustomPhonegapOsPath)) {
                mPhonegapPathField.setText(sCustomPhonegapOsPath);
            }

            validatePageComplete();
            mInternalPathUpdate = false;
        }
    }
    /**
     * Updates the location directory path field.
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
    private void updateLocationPathField(String abs_dir) {

        if (!mInternalPathUpdate) {
            mInternalPathUpdate = true;

            if (abs_dir != null) {
                // We get here if the user selected a directory with the "Browse" button.
                // Disable auto-compute of the custom location unless the user selected
                // the exact same path.
                sAutoComputeCustomLocation = sAutoComputeCustomLocation &&
                abs_dir.equals(sCustomLocationOsPath);
                sCustomLocationOsPath = TextProcessor.process(abs_dir);
            }
            if (!mLocationPathField.getText().equals(sCustomLocationOsPath)) {
                mLocationPathField.setText(sCustomLocationOsPath);
            }

            validatePageComplete();
            mInternalPathUpdate = false;
        }
    }
    /**
     * The location path field is either modified internally (from updateLocationPathField)
     * or manually by the user when the custom_location mode is not set.
     *
     * Ignore the internal modification. When modified by the user, memorize the choice and
     * validate the page.
     */
    private void onPhonegapPathFieldModified() {

        // When the updates doesn't come from updatePhonegapPathField, it must be the user
        // editing the field manually, in which case we want to save the value internally
        // and we disable auto-compute of the custom location (to avoid overriding the user
        // value)
        if (!mInternalPathUpdate) {
            String newPath = getPhonegapPathFieldValue();
            sAutoComputeCustomPhonegap = sAutoComputeCustomPhonegap &&
            newPath.equals(sCustomPhonegapOsPath);
            sCustomPhonegapOsPath = newPath;
            validatePageComplete();
        }
    }

    /**
     * The location path field is either modified internally (from updateLocationPathField)
     * or manually by the user when the custom_location mode is not set.
     *
     * Ignore the internal modification. When modified by the user, memorize the choice and
     * validate the page.
     */
    private void onLocationPathFieldModified() {

        // When the updates doesn't come from updateLocationPathField, it must be the user
        // editing the field manually, in which case we want to save the value internally
        // and we disable auto-compute of the custom location (to avoid overriding the user
        // value)
        if (!mInternalPathUpdate) {
            String newPath = getLocationPathFieldValue();
            sAutoComputeCustomLocation = sAutoComputeCustomLocation &&
            newPath.equals(sCustomLocationOsPath);
            sCustomLocationOsPath = newPath;
            validatePageComplete();
        }
    }

    /**
     * Returns whether this page's controls currently all contain valid values.
     *
     * @return <code>true</code> if all controls are valid, and
     *         <code>false</code> if at least one is invalid
     */
    private boolean validatePage() {

        int status = validatePhonegapPath();

        if (status == MSG_NONE)  {
            status |= validateLocationPath();
        }

        if (status == MSG_NONE)  {
            setStatus(null, MSG_NONE);
        }

        // Return false if there's an error so that the finish button be disabled.
        return (status & MSG_ERROR) == 0;
    }

    /**
     * Validates the page and updates the Next/Finish buttons
     */
    private void validatePageComplete() {
        setPageComplete(validatePage());
    }

    /**
     * Validates the phonegap path field.  Make sure there is at least an example and framework sub-directory
     *
     * @return The wizard message type, one of MSG_ERROR, MSG_WARNING or MSG_NONE.
     */
    private int validatePhonegapPath() {
        File phonegapDir = new File(getPhonegapPathFieldValue());
        if (!phonegapDir.exists() || !phonegapDir.isDirectory()) {
            return setStatus("A phonegap directory name must be specified.", MSG_ERROR);
        } else {
            // However if the directory exists, we should put a warning if it is not
            // empty. We don't put an error (we'll ask the user again for confirmation
            // before using the directory.)
            String[] l = phonegapDir.list();
            if (l.length == 0) {
                return setStatus("The phonegap directory is empty.", MSG_ERROR);
            }
            boolean foundFramework = false;
            boolean foundExample = false;

            for (String s : l) {
                if (s.equals("example")) foundExample = true;
                if (s.equals("framework")) foundFramework = true;
            }
            if ((!foundFramework) || (!foundExample)) {
                return setStatus("The phonegap directory has been corrupted. It is missing the framework and/or example subdirectory", 
                        MSG_ERROR);
            }
            // TODO more validation

            // We now have a good directory, so set example path and save value
            doGetPreferenceStore().setValue(PHONEGAP_DIR,getPhonegapPathFieldValue()); 

            if (isCreateFromExample()) {
                updateLocationPathField(getPhonegapPathFieldValue() + "/example");
            }
            return MSG_NONE;
        }
    }


    /**
     * Validates the location path field.
     *
     * @return The wizard message type, one of MSG_ERROR, MSG_WARNING or MSG_NONE.
     */
    private int validateLocationPath() {
        File locationDir = new File(getLocationPathFieldValue());
        if (!locationDir.exists() || !locationDir.isDirectory()) {
            return setStatus("A directory name must be specified.", MSG_ERROR);
        } else {
            // However if the directory exists, we should put a warning if it is not
            // empty. We don't put an error (we'll ask the user again for confirmation
            // before using the directory.)
            String[] l = locationDir.list();
            if (l.length == 0) {
                return setStatus("The location directory is empty. It should include the source to poplulate the project", 
                        MSG_ERROR);
            }
            
            // We now have a good directory, so set example path and save value
            doGetPreferenceStore().setValue(SOURCE_DIR, getLocationPathFieldValue()); 
            return MSG_NONE;
        }
    }


    /**
     * Sets the error message for the wizard with the given message icon.
     *
     * @param message The wizard message type, one of MSG_ERROR or MSG_WARNING.
     * @return As a convenience, always returns messageType so that the caller can return
     *         immediately.
     */
    private int setStatus(String message, int messageType) {
        if (message == null) {
            setErrorMessage(null);
            setMessage(null);
        } else if (!message.equals(getMessage())) {
            setMessage(message, messageType == MSG_WARNING ? WizardPage.WARNING : WizardPage.ERROR);
        }
        return messageType;
    }
}
