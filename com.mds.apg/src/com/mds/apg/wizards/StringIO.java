package com.mds.apg.wizards;

import java.io.*;

public class StringIO {

    /**
     * Reads and returns the content of a text file 
     * @param filepath the file path to the text file
     * @return null if the file could not be read
     * @throws IOException 
     */
    public static String read(String fileString) throws IOException {

        File file = new File(fileString);
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line = "", output = "";
        while((line = reader.readLine()) != null)
        {
            output += line + "\r\n";
        }
        reader.close();
        return output;
    }
    
    public static void write(String fileString, String output) throws IOException {
        FileWriter writer = new FileWriter(fileString);
        writer.write(output);
        writer.close();
    } 
}