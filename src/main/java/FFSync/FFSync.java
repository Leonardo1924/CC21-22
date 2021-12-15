package FFSync;

import org.javatuples.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

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

                for(int i = 0; i < 2; i+=2){
                    Thread t1, t2 = null;
                    t1 = new Thread(new FTrapid.Sender(ftr, filenames.get(i)));
                    System.out.println("starting thread for file " + filenames.get(i));
                    if(i+1 < nfiles) {
                        System.out.println("starting thread for file " + filenames.get(i+1));
                        t2 = new Thread(new FTrapid.Sender(ftr, filenames.get(i + 1)));
                    }
                    t1.start();
                    if(t2 != null)
                        t2.start();
                    t1.join();
                    if(t2 != null)
                        t2.join();
                }



                /*
                 -> na teoria, é só criar os threads e começá-los.
                 -> Ao mesmo tempo, devia esperar que todas terminassem para voltar a mandar algum pedido
                 -> deveria haver um limite de threads, mas ups (tentar semaphore ?)
                 -> devia haver controle de versoes/atualizações, i.e., para cada file atualizada, devia guardar esse facto
                 -> aliás, para cada pedido de SYNC, devia aguardar, antes de voltar a fazer algum SYNC
                 -> join(), depois aviso, depois continuar
                 */
           }

            else if (command == 2) {
                // sync one specific file
                String requested_file = input2.getValue0();
                Thread syncfile = new Thread(new FTrapid.Sender(ftr, requested_file));
                syncfile.start();
                syncfile.join();
                System.out.println("Command has finished");
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

            }
            else if (command == 4) {
                // Print friend's files
                List<String> files = ftr.getFriend_files();
                if(files != null)
                    System.out.println(ftr.getFriend_files());

            }
            else if(command == 5){

                ftr.exit();
                return;
            }
            else if(command == 6){

                System.out.println(ftr.getFolder().getFilenames());
            }
        }
    }
}
