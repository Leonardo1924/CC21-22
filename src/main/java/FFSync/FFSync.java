package FFSync;

import HTTP.HttpServer;
import org.javatuples.Pair;
import org.javatuples.Quartet;
import org.javatuples.Triplet;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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

            if (input.equals("sync_filenames")) {
                command = 3;
            }
            else if(input.equals("requests_done"))
            {
                command = 7;
            } else if (input.equals("friend_files")) {
                command = 4;
            } else if (input.equals("full_sync")) {
                command = 1;
            }
            else if (input.equals("exit")){
                command = 5;
            }
            else if(input.equals("my_files")){
                command = 6;
            }
            else {
                String[] s = input.split(" ");
                if (s[0].equals("sync") && s.length == 2) {

                    command = 2;
                    filename = s[1];
                }
            }
        }
        return new Pair<>(filename, command);
    }


    public static void main(String[] args) throws IOException, InterruptedException {

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
    /*
        // [[debug]] - FOLDER
        Folder folder = new Folder(path);
        System.out.println(folder.getFilenames());
        System.out.println();

        // [[debug]] - DATAGRAMS
        Datagrams.readRRQ(Datagrams.RRQ(InetAddress.getLocalHost(), "diogo.txt"));
        System.out.println();
        Datagrams.readWRQ(Datagrams.WRQ(InetAddress.getLocalHost(), 3, 6, "diogo.txt"));
        System.out.println();
        Datagrams.readDATA(Datagrams.DATA(InetAddress.getLocalHost(), 1, 20, "olá, sou o diogo.".getBytes(StandardCharsets.UTF_8)));
        System.out.println();
        Datagrams.readACK(Datagrams.ACK(InetAddress.getLocalHost(), 22, 3));
        System.out.println();
        Datagrams.readERROR(Datagrams.ERROR(InetAddress.getLocalHost(), 2, 1));
        System.out.println();

        // [[debug]] - getFileContent
        FTrapid ftr = new FTrapid(path, ip);
        List<DatagramPacket> fileContent = ftr.getFolder().getFileContent(ftr.getChannel().getIP(), 2, "inferno3.txt");
        // Controlo do conteúdo recebido
        if(fileContent != null && fileContent.size() != 0)
            for(DatagramPacket p : fileContent) {

                Triplet<Integer, Integer, byte[]> data = Datagrams.readDATA(p);
                System.out.println("- ficha " + data.getValue0() + ", block " + data.getValue1()
                        + ", content: \"" + new String(data.getValue2(), StandardCharsets.UTF_8) + "\"");
            }


        // esperar que todos acabem?
        ServerChannel channel = ftr.getChannel();
        for(int i = 0; i < 20; i++) {

            Thread t = new Thread(new ServerChannel.Receiver(channel, channel.getSocket()));
            t.start();
        }


     */
        FTrapid ftr = new FTrapid(path, ip);
        ServerChannel channel = ftr.getChannel();

        System.out.println("Starting server...");
        Thread server = new Thread(channel);
        server.start();
        HttpServer http = new HttpServer(ftr);
        Thread http_thread = new Thread(http);
        http_thread.start();

        FTrapid.Sender sender = null;
        //sender.sendRRQ("diogo.txt");
        //sender.sendWRQ(3, 3, "lalalalalalala");
        //sender.sendRRQ("alguemmeajude.txt");


        while (true) {

            Pair<String, Integer> input2 = confirm_commands();

            int command = input2.getValue1();
            String file = null;

            if(command == 1){

                List<String> filenames = ftr.getFolder().getFilenames();
                int nfiles = filenames.size();

                Thread[] threads = new Thread[nfiles];

                for(int i = 0; i < nfiles; i++){

                    if(!ftr.isSync(filenames.get(i))) {
                        Thread t = new Thread(new FTrapid.Sender(ftr, filenames.get(i)));
                        threads[i] = t;
                        t.start();
                    }
                    else{
                        System.out.println("\""+filenames.get(i)+"\" was already synced");
                    }
                }

                for(int i = 0; i < nfiles;i++){
                    if(threads[i] != null)
                        threads[i].join();
                }

           }

            else if (command == 2) {
                // sync one specific file
                String requested_file = input2.getValue0();

                List<String> friend_files = ftr.getFriend_files();

                // Só posso pedir uma file SE eu a tiver, ou, SE eu souber que o friend a tem!
                if(ftr.getFolder().fileExists(requested_file) || (friend_files != null && friend_files.contains(requested_file))) {

                   if (!ftr.isSync(requested_file) && !ftr.getChannel().getServerRequestedFiles().contains(requested_file)) {
                        System.out.println("Starting request of " + requested_file);
                        Thread syncfile = new Thread(new FTrapid.Sender(ftr, requested_file));
                        syncfile.start();
                        syncfile.join();
                        System.out.println("Command has finished");
                    } else {
                        System.out.println("That file has already been synced");
                    }
                }
                else if(friend_files == null){
                    System.out.println("Tu não sabes se o teu amigo tem essa file... -> sync_filenames");
                }
                else if(!friend_files.contains(requested_file)){
                    System.out.println("Nem tu, nem o teu amigo, tem essa file!");
                }
            }
            else if (command == 3) {
                // Send my files' names
                // #filenames#
                sender = new FTrapid.Sender(ftr, "#filenames#");
                Thread senderthread = new Thread(sender);
                System.out.println("started thread");
                senderthread.start();
                senderthread.join();
                System.out.println("\n\\-----Ended getConexao-----/");

                ftr.getFolder().updateFolder();

            }
            else if (command == 4) {
                // Print friend's files
                List<String> files = ftr.getFriend_files();
                if(files != null)
                    System.out.println(ftr.getFriend_files());

            }
            else if(command == 5){

                ftr.exit();
                http.close();

                return;
            }
            else if(command == 6){

                ftr.getFolder().updateFolder();
                System.out.println(ftr.getFolder().getFilenames());
            }
            else if(command == 7){

                for(Map.Entry<Integer, Quartet<String,Boolean,Long,Long>> e : ftr.getRequests_done().entrySet()){

                    System.out.println("Requested sync for : \""+e.getValue().getValue0()
                            + "\", needed update? : \"" + e.getValue().getValue1()
                            + "\", ms: " + e.getValue().getValue2()
                            + ", bytes: " + e.getValue().getValue3()
                            + ", debit: " + (e.getValue().getValue3() * 8)/(e.getValue().getValue2()*0.001) + " bps");
                }
            }
        }
    }
}
