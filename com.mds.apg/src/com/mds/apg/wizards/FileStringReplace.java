// thanks to http://www.java.happycodings.com/Core_Java/code69.html

package com.mds.apg.wizards;

import java.io.*;

public class FileStringReplace {

    public static void replace(String fileString, String fromString, String toString) throws IOException {

        String oldtext = StringIO.read(fileString);
        // replace a word in a file
        String newtext = oldtext.replaceAll(fromString, toString);
        StringIO.write(fileString, newtext);
    }
}