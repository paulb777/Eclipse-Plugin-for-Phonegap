// thanks to http://www.java2s.com/Code/Java/File-Input-Output/CopyfilesusingJavaIOAPI.htm

package com.mds.apg.wizards;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileCopy {

    public static void recursiveCopy(String fromFileName, String toFileName) throws IOException {
        copy (fromFileName, toFileName, true, false);
    }

    public static void recursiveForceCopy(String fromFileName, String toFileName) throws IOException {
        copy (fromFileName, toFileName, true, true);
    }

    public static void forceCopy(String fromFileName, String toFileName) throws IOException{
        copy (fromFileName, toFileName, false, true);
    }    

    public static void copy(String fromFileName, String toFileName) throws IOException{
        copy (fromFileName, toFileName, false, false);
    }

    private static void copy(String fromFileName, String toFileName, boolean isRecursive, boolean force)
    throws IOException {
        File fromFile = new File(fromFileName);
        File toFile = new File(toFileName);

        if (!fromFile.exists())
            throw new IOException("FileCopy: " + "no such source file: "
                    + fromFileName);

        if (isRecursive && fromFile.isDirectory()) {
            if (toFile.exists()) {
                if (!toFile.isDirectory()) {
                    throw new IOException("FileCopy: " + "Cannot copy directory to non-directory: "
                            + toFileName);
                }
            } else { // create the directory
                toFile = new File(toFileName);
                if (!toFile.mkdir()) {
                    throw new IOException("FileCopy: " + "directory Creation Failed: "
                            + toFileName);
                }
            }                
            String fList[] = fromFile.list();
            for (String s : fList) {
                copy(fromFileName + "/" + s, toFileName + "/" + s, true, force);
            }
            return;
        }

        if (!fromFile.isFile())
            throw new IOException("FileCopy: " + "can't copy directory: "
                    + fromFileName);
        if (!fromFile.canRead())
            throw new IOException("FileCopy: " + "source file is unreadable: "
                    + fromFileName);

        if (toFile.isDirectory()) 
            toFile = new File(toFile, fromFile.getName());

        if (toFile.exists()) {
            if (!toFile.canWrite())
                throw new IOException("FileCopy: "
                        + "destination file is unwriteable: " + toFileName);
            if (!force) {
                throw new IOException("FileCopy: "
                        + "trying to overwrite an existing file" + toFileName);
            }
        } else {
            String parent = toFile.getParent();
            if (parent == null)
                parent = System.getProperty("user.dir");
            File dir = new File(parent);
            if (!dir.exists())
                throw new IOException("FileCopy: "
                        + "destination directory doesn't exist: " + parent);
            if (dir.isFile())
                throw new IOException("FileCopy: "
                        + "destination is not a directory: " + parent);
            if (!dir.canWrite())
                throw new IOException("FileCopy: "
                        + "destination directory is unwriteable: " + parent);
        }

        FileInputStream from = null;
        FileOutputStream to = null;
        try {
            from = new FileInputStream(fromFile);
            to = new FileOutputStream(toFile);
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = from.read(buffer)) != -1)
                to.write(buffer, 0, bytesRead); // write
        } finally {
            if (from != null)
                try {
                    from.close();
                } catch (IOException e) {
                    ;
                }
                if (to != null)
                    try {
                        to.close();
                    } catch (IOException e) {
                        ;
                    }
        }
    }
    
    
    public static void createPhonegapJs(String fromDirName, String toFileName)
    throws IOException {
        File fromFile = new File(fromDirName);
        File toFile = new File(toFileName);

        if (!fromFile.exists()) {
            throw new IOException("createPhongapJs: " + "no such source file: "
                    + fromDirName);
        }
        FileOutputStream to = new FileOutputStream(toFile);
        try {
            to = new FileOutputStream(toFile);
            to = appendStream(new File(fromDirName + "/" + "phonegap.js.base"), to);

            String fList[] = fromFile.list();
            for (String s : fList) {
                if (!s.equals("phonegap.js.base")) {
                    to = appendStream(new File(fromDirName + "/" + s), to);
                }
            }
        } finally {
            if (to != null) {
                try {
                    to.close();
                } catch (IOException e) {
                    ;
                }
            }
        }
    }
    
    private static FileOutputStream appendStream(File fromFile, FileOutputStream to) throws IOException {
        
        FileInputStream from = null;
        try {
            from = new FileInputStream(fromFile);
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = from.read(buffer)) != -1)
                to.write(buffer, 0, bytesRead); // write
        } finally {
            if (from != null) {
                try {
                    from.close();
                } catch (IOException e) {
                    ;
                }
            }
        }
        return to;
    }
}




