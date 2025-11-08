import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Scanner;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
            JFileChooser chooser = new JFileChooser();
            Scanner inFile;
            String line;
            Path target = new File(System.getProperty("user.dir")).toPath();
            target = target.resolve("src");
            // set the chooser to the project src directory
            chooser.setCurrentDirectory(target.toFile());

            try  // Code that might trigger the exception goes here
            {

                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    target = chooser.getSelectedFile().toPath();  // this is a File object not a String filename

                    inFile = new Scanner(target);

                    while (inFile.hasNextLine()) {
                        line = inFile.nextLine();
                        System.out.println(line);
                    }

                    inFile.close();
                } else   // User did not pick a file, closed the chooser
                {
                    System.out.println("Sorry, you must select a file! Termininating!");
                    System.exit(0);
                }
            } catch (FileNotFoundException e) {
                System.out.println("File Not Found Error");
                e.printStackTrace();
            } catch (IOException e) // code to handle this exception
            {
                System.out.println("IOException Error");
                e.printStackTrace();
            }
    }
}