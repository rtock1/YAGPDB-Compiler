package compiler;

import java.io.*;

public class Runner {
    public static final String PATH = "/Users/ryan/git/yagpdb/src/commands";
    public static void main(String[] args) {
        CompileMain compiler = new CompileMain();
        File toCompile = new File(PATH);
        for (File file: toCompile.listFiles()) {
            StringBuilder fileContents = new StringBuilder();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line = "";
                while ((line = reader.readLine()) != null) {
                    fileContents.append(line);
                    fileContents.append("\n");
                }
                reader.close();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            compiler.setCompilationText(fileContents.substring(0, fileContents.length()));
            String output = compiler.compile();
            try {
                File outputFile = new File(file.getAbsolutePath().replaceFirst("commands","output"));
                outputFile.createNewFile();
                BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
                writer.write(output);
                writer.flush();
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
