package FFSync;

import org.javatuples.Pair;
import org.javatuples.Triplet;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FTrapid {

    private String folder_path;
    private Folder folder;
    private InetAddress ip;
    private ServerChannel channel;
    private List<String> friend_files;

        private int ticket;                     // Sistema de tickets
        private Lock locker;                    // Lock

    /**
     * Constructor
     */
    public FTrapid(String path, String ip) throws UnknownHostException, SocketException {

        this.folder_path = path;                                        // folder path
        this.folder = new Folder(path);                                 // folder
        this.ip = InetAddress.getByName(ip);                            // ip from friend
        this.friend_files = new ArrayList<>();
        this.channel = new ServerChannel(this, this.ip);         // connection channel


        this.ticket = 1;
        this.locker = new ReentrantLock();
    }

    /**
     * Gets a unique ticket
     * @return
     */
    public int getTicket(){

        try {
            this.locker.lock();
            int ticket = this.ticket;
            this.ticket += 1;
            return ticket;
        } finally {
            this.locker.unlock();
        }
    }


    public ServerChannel getChannel(){ return this.channel;}

    public Folder getFolder(){return this.folder;}

    public void loadFriendFiles(List<byte[]> files){

        for(byte[] b : files){
            this.friend_files.add(new String(b, StandardCharsets.UTF_8));
        }
    }

    public List<String> getFriend_files(){

        if(this.friend_files.size() == 0){
            System.out.println("error: You don't have any record on your friend's files!");
            return null;
        }
        return new ArrayList<>(this.friend_files);
    }


    public void exit(){

        System.out.println("Closing socket...");
        this.channel.getSocket().close();
        System.out.println("Socket closed, leaving...");
    }

        // AQUI - MTU procurar, o ip vai fragmentar os packets, procurar o tamanho correto para usar



    static class Sender implements Runnable{

        private FTrapid ftr;
        private DatagramSocket socket;
        private InetAddress ip;
        private String file;
        private int ficha;


        public Sender(FTrapid ftr, String file){

            this.ftr = ftr;
            this.socket = ftr.channel.getSocket();
            this.ip = ftr.channel.getIP();
            this.file = file;
            this.ficha = ftr.getTicket();
        }


        public Triplet<Integer,Integer,String> getConexao(String file) throws IOException {

            // O getConexão tem de enviar um pedido de RRQ e, consoante a resposta recebida, tratá-la.

            DatagramPacket RRQ = Datagrams.RRQ(this.ip, file, this.ficha);      // pedido RRQ


            // enquanto não receber uma resposta, devo continuar a enviá-lo
            byte[] answerbuff = new byte[1024]; //no max
            DatagramPacket answerPacket = new DatagramPacket(answerbuff, answerbuff.length);
            this.socket.setSoTimeout(5000);

            while(true){

                // enviar o RRQ
                this.socket.send(RRQ);

                try{

                    // esperar por uma resposta : WRQ, RRQ, ERROR
                    this.socket.receive(answerPacket);

                    int received_opcode = Datagrams.getDatagramOpcode(answerPacket);
                    int received_ficha = Datagrams.getDatagramFicha(answerPacket);
                    System.out.println("[opcode "+received_opcode+"], [ficha "+received_ficha+"]");

                    // recebi um WRQ e é para este mesmo pedido
                    if(received_opcode == 2 && received_ficha == this.ficha){

                        System.out.println("confirmed wrq for ficha " + received_ficha);
                        // Se me querem escrever, vou aceitar
                        RRQ = Datagrams.ACK(this.ip, this.ficha, 0);
                        this.socket.send(RRQ); // this is ack 0
                        this.socket.send(RRQ);
                        return Datagrams.readWRQ(answerPacket);
                    }

                    // Se eu receber um RRQ, esse já vai ter outra ficha associada e é o servidor que o vai tratar...

                    // se eu receber um ERROR,
                    else if(received_opcode == 5 && received_ficha == this.ficha){

                        System.out.println("error: received error from your friend");
                        // o amigo não tinha a file
                        return null;
                    }
                }
                catch (SocketTimeoutException e){

                    this.socket.send(RRQ);
                }
            }
        }





        @Override
        public void run() {

            try {
                // Get conection and receive info about transfer
                Triplet<Integer,Integer,String> wrq_info = this.getConexao(this.file);
                System.out.println("got out of getconexao");

                if(wrq_info == null) return;

                int nblocks = wrq_info.getValue1();
                this.ficha = wrq_info.getValue0();
                this.socket.send(Datagrams.ACK(ip, this.ficha, 0));
                //System.out.println("get conexao left");

                // receiving datas
                byte[] databuff = new byte[1024];
                DatagramPacket datapacket = new DatagramPacket(databuff, databuff.length);
                DatagramPacket ack = Datagrams.ACK(this.ip, this.ficha, 0);
                int current_block = 1;

                List<byte[]> answer = new ArrayList<>();
                this.socket.setSoTimeout(4000);

                while(true){

                    if(current_block == nblocks+1){

                        socket.send(ack);
                        System.out.println("Received everything...");
                        socket.send(ack);

                        for(byte[] b : answer){

                            System.out.println(new String(b, StandardCharsets.UTF_8));
                        }

                        if(wrq_info.getValue2().equals("#filenames#")){

                            ftr.loadFriendFiles(answer);
                        }
                        else{
                            System.out.println("Updating file...");
                            this.ftr.folder.updateFile(wrq_info.getValue2(), answer, wrq_info.getValue0(), this.ip);
                        }
                        return;
                    }

                    socket.send(ack);
                    try {
                        socket.receive(datapacket);
                        int received_opcode = Datagrams.getDatagramOpcode(datapacket);

                        if (received_opcode == 3) {

                            Triplet<Integer, Integer, byte[]> data_info = Datagrams.readDATA(datapacket);

                            int ficha = data_info.getValue0();
                            int bloco = data_info.getValue1();
                            System.out.println("[3] ficha ["+ficha+"] bloco ["+bloco+"]");
                            if (ficha == this.ficha && bloco == current_block) {

                                answer.add(data_info.getValue2());
                                ack = Datagrams.ACK(this.ip, this.ficha, current_block);
                                socket.send(ack);
                                current_block++;
                            } else {
                                // se não é o que quero, pode fazer falta a alguém
                                this.ftr.channel.resend(datapacket);
                            }
                        }
                    } catch (SocketTimeoutException e){
                        System.out.println("Timeout trying to receive data");
                        if(current_block == nblocks+1) {
                            return;
                        }
                        socket.send(ack);
                    }
                }


            } catch (IOException e) {
                e.printStackTrace();
            }



        }
    }



}
