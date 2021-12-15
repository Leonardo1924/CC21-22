package FFSync;

import org.javatuples.Pair;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ServerChannel implements Runnable{

    // ----------------------------------------------------------------------
        private InetAddress ip;                 // ip para onde irei enviar info
        private final static int PORT = 8080;   // porta onde estou à escuta
        private DatagramSocket socket;          // Socket de comunicação
        private Folder folder;
        private FTrapid ftr;
    // ----------------------------------------------------------------------

    // ----------------------------------------------------------------------

        private Map<String, Integer> current_threads;
    // ----------------------------------------------------------------------

    public void resend(DatagramPacket dp) throws IOException {

        this.socket.send(new DatagramPacket(dp.getData(), dp.getData().length, dp.getAddress(), PORT));
    }

    /**
     * Constructor
     */
    public ServerChannel(FTrapid ftr, InetAddress ip) throws SocketException {

        this.ip = ip;
        this.ftr = ftr;
        this.socket = new DatagramSocket(PORT);
        this.folder = ftr.getFolder();
        this.current_threads = new HashMap<>();
    }

    public InetAddress getIP(){ return this.ip;}

    /**
     * Returns server's socket
     * @return
     */
    public DatagramSocket getSocket(){ return this.socket;}


    public void sendMessage(String msg) throws IOException {

        System.out.println("Sending message to " + this.ip + ", port no. " + PORT);

        byte[] Msg = msg.getBytes(StandardCharsets.UTF_8);
        this.socket.send(new DatagramPacket(Msg, Msg.length, this.ip, PORT));
    }


    public void run() {

        byte[] receivingbuff = new byte[1024];
        DatagramPacket receivingPacket = new DatagramPacket(receivingbuff, receivingbuff.length);


        while(true){

            try {
                // Receber os datagramas
                this.socket.receive(receivingPacket);
                // Ler o OPCODE recebido
                int received_opcode = Datagrams.getDatagramOpcode(receivingPacket);

                // Se for um READ REQUEST, tratá-lo
                if(received_opcode == 1){
                    System.out.println("[opcode 1]");

                    Pair<String, Integer> file_requested = Datagrams.readRRQ(receivingPacket);
                    System.out.println("requested ["+file_requested.getValue0()+"], ficha ["+file_requested.getValue1()+"]");

                    if(!this.folder.fileExists(file_requested.getValue0()) && !file_requested.getValue0().equals("#filenames#")){
                        System.out.println("file does not exist");
                        // caso em que eu não tenho a file pedida
                        //Thread save = new Thread(new FTrapid.Sender(this.ftr, file_requested.getValue0()));
                        //save.start();

                        this.socket.send(Datagrams.ERROR(this.ip, file_requested.getValue1(), 1));
                        this.socket.send(Datagrams.ERROR(this.ip, file_requested.getValue1(), 1));
                        return;
                    }

                    else if(!this.current_threads.containsKey(file_requested.getValue0())) {

                        this.current_threads.put(file_requested.getValue0(), file_requested.getValue1()); // adicionar às threads em run
                        this.ftr.getTicket();
                        System.out.println("[Received RRQ for file \"" + file_requested.getValue0() + "\"");

                        Receiver r = new Receiver(this.ftr, file_requested.getValue0(), file_requested.getValue1());
                        Thread receiver = new Thread(r);
                        receiver.start();
                    }
                    //List<DatagramPacket> content = r.acceptConexao(this.folder, file_requested);
                }

                // Se receber algo que não é um pedido, é porque roubei o packet que alguém precisava..
                else{
                    this.resend(receivingPacket);
                }

                // Se for um DATA, tratá-lo

            } catch (IOException ignored) {

                if(socket.isClosed()){
                    return;
                }
            }

        }
    }




    // -------------------------------------------------------- Thread Received -----------------
    static class Receiver implements Runnable{

        private FTrapid ftr;
        private int connection_ticket;
        private DatagramSocket socket;
        private ServerChannel channel;
        private Folder folder;
        private String file;

        // private final int MAX_NUMBER_THREADS = 5;
        // private Semaphore mySemaphore = new Semaphore(MAX_NUMBER_THREADS);


        public Receiver(FTrapid ftr, String file, int ficha){
            this.ftr = ftr;
            this.socket = ftr.getChannel().getSocket();
            this.channel = ftr.getChannel();
            this.connection_ticket = ficha;
            this.folder = ftr.getFolder();
            this.file = file;
        }


        public List<DatagramPacket> acceptConexao(FTrapid ftr, String file) throws IOException {
            // esta função já admite que a file pedida existe!

            List<DatagramPacket> file_content = null;

            if(file.equals("#filenames#")){          // código utilizado para saber que só quer enviar as filenames
                file_content = this.folder.getFilenamesTOSend(this.channel.ip, this.connection_ticket);
            }
            else {
                file_content = folder.getFileContent(this.channel.ip, this.connection_ticket, file);
            }

            // Para responder a um pedido de conexão, envio um WRITE REQUEST
            DatagramPacket wrq = Datagrams.WRQ(this.channel.ip, this.connection_ticket, file_content.size(), file);
            System.out.println("["+this.connection_ticket+" Sending WRQUEST for [" + file_content.size() + "] blocks!");

            byte[] aux = new byte[1024];
            DatagramPacket receiving = new DatagramPacket(aux, aux.length);
            this.socket.setSoTimeout(3000); // 3seg
            int timeouts = 0;

            while(true){
                this.socket.send(wrq);
               // System.out.println("["+this.connection_ticket+" Sent wrq from acceptConexao");

                try{
                    this.socket.receive(receiving);
                    int opcode = Datagrams.getDatagramOpcode(receiving);
                    //System.out.println("["+this.connection_ticket+" Received something on getconexao : " + opcode);

                    // Se receber um ACK e for direcionado a esta ficha,
                    if(opcode == 4){

                        Pair<Integer,Integer> ackInfo = Datagrams.readACK(receiving);
                        System.out.println("["+this.connection_ticket+" opcode 4 : block " + ackInfo.getValue1() + ", ficha " + ackInfo.getValue0());

                        if(ackInfo.getValue0() == this.connection_ticket && ackInfo.getValue1() == 0){
                            System.out.println("["+this.connection_ticket+" Confirmed ACK 0 from file " + file + ", ticket " + this.connection_ticket);
                            return file_content;
                        }
                        else
                            this.channel.resend(receiving);
                    }
                }
                catch (SocketTimeoutException e){

                    this.socket.send(wrq);
                    timeouts++;
                    System.out.println("["+this.connection_ticket+" Timeout in acceptConexao");
                    if(timeouts == 12){

                        System.out.println("["+this.connection_ticket+" Too many timeouts...");
                        return null;
                    }
                }
            }
        }


        @Override
        public void run() {
            // Esta thread está responsável por tratar toda a transferência

            System.out.println("I'm [RECEIVER] no. [" + this.connection_ticket + "]");
            int currentBlock = 1;

            try {
                List<DatagramPacket> content = this.acceptConexao(this.ftr, file);
                System.out.println("Sending d.a.t.a.");

                int current_index = 0;
                if(content != null && content.size() != 0) {
                    //System.out.println("["+this.connection_ticket+" Conection has been made!");

                    byte[] ackbuff = new byte[1024]; // nunca vai ultrapassar os 4*3 tho...
                    DatagramPacket ackpacket = new DatagramPacket(ackbuff, ackbuff.length);
                    socket.setSoTimeout(3000);

                    while(true) {

                        if(current_index == content.size()){
                            System.out.println("["+this.connection_ticket+" Sent everything...");
                            return;
                        }

                        // enviar o packet
                        socket.send(content.get(current_index));

                        try{
                            // receber a resposta
                            socket.receive(ackpacket);
                            int received_opcode = Datagrams.getDatagramOpcode(ackpacket);

                            if(received_opcode == 4){

                                Pair<Integer,Integer> ack_info = Datagrams.readACK(ackpacket);
                                int ficha = ack_info.getValue0();
                                int bloco = ack_info.getValue1();

                                if(ficha == this.connection_ticket && bloco == currentBlock){

                                    System.out.println("["+this.connection_ticket+" Received ack ficha " + ficha + ", bloco " + bloco + ", current was " + currentBlock);

                                    currentBlock++;
                                    current_index++;
                                    if(current_index == content.size()){
                                        System.out.println("["+this.connection_ticket+" Sent everything...");
                                       // this.socket.setSoTimeout(0);
                                        return;
                                    }
                                    socket.send(content.get(current_index));
                                }
                                //else this.socket.send(ackpacket);

                            }
                        }
                        catch (SocketTimeoutException e){

                            System.out.println("["+this.connection_ticket+" Timeout trying to receive acks");
                            if(current_index == content.size()) return;
                            socket.send(content.get(current_index));
                        }
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    // -------------------------------------------------------- Thread Received -----------------

    //TODO



}
