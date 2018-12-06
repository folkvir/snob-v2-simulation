package snob.simulation;

import peersim.Simulator;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class App {
    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("--init")) {
            int peers = 1000;
            int cycles = 10000; // will stop at the end of all queries anyway, but the stop case is around n * log (n)
            int[] replicate = {1, 2, 4, 8, 16, 32, 64, 128};

            int numberofqueries = 5;
            int[] queries = new int[numberofqueries];
            boolean soixantetreize = false;
            for (int i = 0; i < queries.length; i++) {
                queries[i] = (int) Math.floor(Math.random() * 100);
                if(queries[i] == 73) {
                    soixantetreize = true;
                }
            }
            // this query generate a lot of intermediate results. which is cool to present. cCool
            if(!soixantetreize) queries[0] = 73;

            int delta_rps = 1;
            int delta_son = 1;
            int rps_size = 100;
            int pick = 5;
            int son_size = 5; // not effect if the fullmesh is active.
            boolean[] son_activated = {true, false};
            boolean[] trafficMin = {true, false};
            // firstly do it with only the rps
            for(int query: queries) {
                for (int i : replicate) {
                    for (boolean b : son_activated) {
                        for (boolean traffic : trafficMin) {
                            // create a file
                            // first copy the template
                            System.err.println("Copying template to config...");
                            String configName = "p" + peers
                                    + "-q" + query
                                    + "-son" + b
                                    + "-rep" + i
                                    + "-traffic" + traffic
                                    + "-config.conf";
                            String pathTemplate = System.getProperty("user.dir") + "/configs/template.conf";
                            String pathConfig = System.getProperty("user.dir") + "/configs/generated/" + configName;
                            //System.err.println("Template location: " + pathTemplate);
                            System.err.println("Config location: " + pathConfig);
                            File in = new File(pathTemplate);
                            File out = new File(pathConfig);
                            out.createNewFile();
                            copyFileUsingStream(in, out);
                            System.err.println("Replacing config vars to their values...");
                            replace(pathConfig, "\\$son_activated\\$", String.valueOf(b));
                            replace(pathConfig, "\\$traffic\\$", String.valueOf(traffic));
                            replace(pathConfig, "\\$size\\$", String.valueOf(peers));
                            replace(pathConfig, "\\$cycle\\$", String.valueOf(cycles));
                            replace(pathConfig, "\\$rps_size\\$", String.valueOf(rps_size));
                            replace(pathConfig, "\\$pick\\$", String.valueOf(pick));
                            replace(pathConfig, "\\$son_size\\$", String.valueOf(son_size));
                            replace(pathConfig, "\\$rps_delta\\$", String.valueOf(delta_rps));
                            replace(pathConfig, "\\$son_delta\\$", String.valueOf(delta_son));
                            replace(pathConfig, "\\$replicate\\$", String.valueOf(i));
                            replace(pathConfig, "\\$querytoreplicate\\$", String.valueOf(query));
                        }
                    }
                }
            }
        } else if (args.length > 0 && args[0].equals("--config")) {
            executeConfig("./configs/generated/" + args[1]);
        }
    }

    private static void replace(String filename, String old, String newOne) throws IOException {
        Path path = Paths.get(filename);
        Charset charset = StandardCharsets.UTF_8;

        String content = new String(Files.readAllBytes(path), charset);
        content = content.replaceAll(old, newOne);
        Files.write(path, content.getBytes(charset));
    }

    private static void copyFileUsingStream(File source, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            is.close();
            os.close();
        }
    }

    protected static PrintStream outputFile(String name) throws FileNotFoundException {
        return new PrintStream(new FileOutputStream(name));
    }

    private static void executeConfig(String config) {
        try {
            String[] arguments = {config};
            Simulator sim = new Simulator();
            Simulator.main(arguments);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
