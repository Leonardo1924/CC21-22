package FFSync;

import org.javatuples.Pair;
import sun.misc.Signal;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class FFSync {


    private static Pair<String, Integer> confirm_commands() throws IOException {

        String input;
        int command = 0;
        String filename = null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("#> command: ");
        while (command == 0) {
            input = reader.readLine();

            if (input.equals("filenames")) {
                command = 3;
            } else if (input.equals("friend_files")) {
                command = 4;
            } else if (input.equals("full_sync")) {
                command = 1;
            } else {
                String[] s = input.split(" ");
                if (s[0].equals("sync") && s.length == 2) {

                    command = 2;
                    filename = s[1];
                }
            }
        }
        return new Pair<>(filename, command);
    }



    public static void main(String[] args) throws IOException {

        String path = null;
        String ip = null;


        if (args.length == 2) {
            path = args[0];
            ip = args[1];

            if (!(new File(path).isDirectory())) {
                System.out.println("Directory does not exist!\n");  //confirm
                return;
            }

        } else {
            System.out.println("Faltam argumentos referentes ao caminho/ip do servidor");
        }

        // --------------------------------------------
        FTrapid ftr = new FTrapid(ip, path);
        Channel channel = new Channel(ftr);
        // --------------------------------------------


        while (true) {

            Pair<String, Integer> input2 = confirm_commands();

            int command = input2.getValue1();
            String file = null;
            if (command == 2) {
                file = input2.getValue0();
            }

            if(command == 3){

                ftr.sendFileNames(channel);
            }

        }

    }

    }
