import Http_server.Http;
import org.javatuples.Pair;
import FFSync.FTrapid;
import FFSync.ServerChannel;
import org.javatuples.Quartet;
import FFSync.Datagrams;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FFSync {


    private static Pair<String, Integer> confirm_commands() throws IOException {

        String input;
        int command = 0;
        String filename = null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("#> command: ");
        while (command == 0) {
            input = reader.readLine();

            switch(input){
                case "sync_filenames":
                    command = 3;
                    break;

                case "requests_done":
                    command = 7;
                    break;
                case "friend_files":
                    command = 4;
                    break;

                case "full_sync":
                    command = 1;
                    break;
                case "exit":
                    command = 5;
                    break;

                case "my_files" :
                command = 6;
                break;

                default:
                    String[] s = input.split(" ");
                    if (s[0].equals("sync") && s.length == 2) {

                        command = 2;
                        filename = s[1];
                    }
                    else if(s[0].equals("login") && s.length == 2){

                        command = 8;
                        filename = s[1];
                    }
                    break;
            }
        }
        return new Pair<>(filename, command);
    }


    public static void main(String[] args) throws IOException, InterruptedException {

        String path = null;
        String ip = null;
        String password = null;

        if (args.length == 3) {
            path = args[0];
            ip = args[1];
            if (!(new File(path).isDirectory())) {
                System.out.println("Directory does not exist!\n");  //confirm
                return;
            }
            password = args[2];
        } else {
            System.out.println("Faltam argumentos referentes ao caminho/ip do servidor");
        }

        // Preparação do servidor --------------------------------
        FTrapid ftr = new FTrapid(path, ip, password);
        ServerChannel channel = ftr.getChannel();
        boolean isLoggedIn = channel.isLoggedIn();

        System.out.println("Starting server...");
        Thread server = new Thread(channel);
        server.start();
        Http http = new Http(ftr);
        Thread http_thread = new Thread(http);
        http_thread.start();


        // Leitura consecutiva do input--------------------------

        FTrapid.Sender sender = null;
        while (true) {

            // Parse do input
            Pair<String, Integer> input2 = confirm_commands();

            // comando escolhido
            int command = input2.getValue1();
            String file = null;

            switch(command) {
                // FULL SYNC
                case 1:
                    if(channel.isLoggedIn()) {
                        List<String> filenames = ftr.getFolder().getFilenames();
                        int nfiles = filenames.size();

                        Thread[] threads = new Thread[nfiles];

                        for (int i = 0; i < nfiles; i++) {

                            if (!ftr.isSync(filenames.get(i))) {
                                Thread t = new Thread(new FTrapid.Sender(ftr, filenames.get(i)));
                                threads[i] = t;
                                t.start();
                            } else {
                                System.out.println("\"" + filenames.get(i) + "\" was already synced");
                            }
                        }

                        for (int i = 0; i < nfiles; i++) {
                            if (threads[i] != null)
                                threads[i].join();
                        }
                    }
                    else{
                        System.out.println("error: login needed -> login {password}");
                    }
                    break;

                // SYNC SPECIFIC FILE
                case 2:
                    if(channel.isLoggedIn()) {
                        //requested file
                        String requested_file = input2.getValue0();

                        List<String> friend_files = ftr.getFriend_files();

                        // Só posso pedir uma file SE eu a tiver, ou, SE eu souber que o friend a tem!
                        if (ftr.getFolder().fileExists(requested_file) || (friend_files != null && friend_files.contains(requested_file))) {

                            if (!ftr.isSync(requested_file) && !ftr.getChannel().getServerRequestedFiles().contains(requested_file)) {
                                System.out.println("Starting request of " + requested_file);
                                Thread syncfile = new Thread(new FTrapid.Sender(ftr, requested_file));
                                syncfile.start();
                                syncfile.join();
                                System.out.println("Command has finished");
                            } else {
                                System.out.println("That file has already been synced");
                            }
                        } else if (friend_files == null) {
                            System.out.println("Tu não sabes se o teu amigo tem essa file... -> sync_filenames");
                        } else if (!friend_files.contains(requested_file)) {
                            System.out.println("Nem tu, nem o teu amigo, tem essa file!");
                        }
                    }
                    else{
                        System.out.println("error: login needed -> login {password}");
                    }
                    break;

                // SEND FILE'S NAMES
                case 3:
                    if(channel.isLoggedIn()) {
                        // #filenames#
                        sender = new FTrapid.Sender(ftr, "#filenames#");
                        Thread senderthread = new Thread(sender);
                        System.out.println("started thread");

                        senderthread.start();
                        senderthread.join();

                        System.out.println("\n\\-----Ended-----/");
                    }
                    else{
                        System.out.println("error: login needed -> login {password}");
                    }
                    break;

                    // Print friend's files
                case 4:
                    if(channel.isLoggedIn()){
                            List<String> files = ftr.getFriend_files();
                            if (files != null)
                                System.out.println(ftr.getFriend_files());
                        }
                    else{
                        System.out.println("error: login needed -> login {password}");
                    }
                        break;

                // EXIT
                case 5:
                    ftr.exit();
                    http.close();
                    return;

                // Print MY files
                case 6:

                    ftr.getFolder().updateFolder();
                    System.out.println(ftr.getFolder().getFilenames());
                    break;

                case 7:
                    if(channel.isLoggedIn()) {
                        for (Map.Entry<Integer, Quartet<String, Boolean, Long, Long>> e : ftr.getRequests_done().entrySet()) {

                            System.out.println("Requested sync for : \"" + e.getValue().getValue0()
                                    + "\", needed update? : \"" + e.getValue().getValue1()
                                    + "\", ms: " + e.getValue().getValue2()
                                    + ", bytes: " + e.getValue().getValue3()
                                    + ", debit: " + (e.getValue().getValue3() * 8) / (e.getValue().getValue2() * 0.001) + " bps");
                        }
                    }
                    else{
                        System.out.println("error: login needed -> login {password}");
                    }
                    break;

                case 8:
                    if(!isLoggedIn) {
                        String pass = input2.getValue0();
                        channel.getSocket().send(Datagrams.prepareSimpleMessage(pass, ftr.getIP()));
                        TimeUnit.SECONDS.sleep(2);
                        isLoggedIn = channel.isLoggedIn();
                        if(isLoggedIn){
                            System.out.println("Login has been made!");
                        }
                        // pass que eu acho ser a do meu colega

                    }
                    else{
                        System.out.println("error: o login ja foi feito");
                    }
                    break;
                default:
                    System.out.println("error: comando desconhecido");
                    break;
            }
        }
    }
}
