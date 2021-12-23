package FFSync;

import org.javatuples.Pair;
import org.javatuples.Quartet;
import org.javatuples.Triplet;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FTrapid {

    private final Folder folder;
    private final InetAddress ip;
    private final ServerChannel channel;
    private List<String> friend_files;

    private int ticket;                     // Sistema de tickets
    private final Lock locker;              // Lock

                //ficha         file    updated?   ms    bytes
    private Map<Integer, Quartet<String, Boolean, Long, Long>> requests_done;
    private Set<Integer> requests_failed;


    /**
     * Constructor
     */
    public FTrapid(String path, String ip, String password) throws UnknownHostException, SocketException {

        this.folder = new Folder(path);                                 // folder
        this.ip = InetAddress.getByName(ip);                            // ip from friend
        this.friend_files = new ArrayList<>();
        this.channel = new ServerChannel(this, this.ip, password);         // connection channel


        this.ticket = 1;
        this.locker = new ReentrantLock();

        this.requests_done = new HashMap<>();
        this.requests_failed = new HashSet<>();

    }


    public Map<Integer, Quartet<String,Boolean, Long,Long>> getRequests_done(){
        return new HashMap<>(this.requests_done);
    }


    public InetAddress getIP(){ return this.ip;}

    public boolean isSync(String file){

        for(Map.Entry<Integer, Quartet<String, Boolean, Long, Long>> e : this.getRequests_done().entrySet()){

            if(e.getValue().getValue0().equals(file))
                return true;
        }
        return false;
    }

    boolean isSync(int ficha){

        return this.requests_done.containsKey(ficha);
    }

    boolean isSync(String file, int ficha){

        return this.requests_done.get(ficha).getValue0().equals(file);
    }

    public boolean hasFailed(int ficha){
        return this.requests_failed.contains(ficha);
    }

    public void addFailed(int ficha){this.requests_failed.add(ficha);}


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

            String file = new String(b, StandardCharsets.UTF_8);
            if(!this.friend_files.contains(file))               //podia ser substituído por um setlist...
                this.friend_files.add(file);
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



    public static class Sender implements Runnable{

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
            // Esta função admite que a conexão será viável

            // READ REQUEST
            DatagramPacket RRQ = Datagrams.RRQ(this.ip, file, this.ficha);

            // A função do getConexão é enviar um RRQ, receber um WRQ e enviar um ACK 0

            byte[] wrq_buff = new byte[1024];
            DatagramPacket wrq_received = new DatagramPacket(wrq_buff, wrq_buff.length);

            int timeout = 0;
            this.socket.setSoTimeout(3000); // timeout 3 seg

            while(true){

                // enviar o READ REQUEST
                this.socket.send(RRQ);

                try{
                    // Receber os packets
                    if(this.ftr.isSync(this.file)){
                        this.ftr.addFailed(this.ficha);
                        return null;
                    }

                    this.socket.receive(wrq_received);

                    // ler o tipo de packet
                    int received_opcode = Datagrams.getDatagramOpcode(wrq_received);
                    int received_ficha = Datagrams.getDatagramFicha(wrq_received);

                    // Posso receber um WRQ, ou um ERROR

                    // Se eu receber um WRITE REQUEST, posso assumir que a ligação é segura
                    if(received_opcode == 2 && received_ficha == this.ficha){

                        RRQ = Datagrams.ACK(this.ip, this.ficha, 0);
                        this.socket.send(RRQ);
                        // ( [opcode] [ficha] [nBlocks] [fileSize] [filename] [0] ) WRQ
                        return Datagrams.readWRQ(wrq_received);
                    }

                    // Se receber um ERROR, é porque há algum tipo de problema e devo terminar
                    else if(received_opcode == 5 && received_ficha == this.ficha){

                        this.ftr.requests_failed.add(this.ficha);
                        //System.out.println("error: received error from ficha "+ficha+" ... terminating connection");
                        //System.out.println("thread ["+this.ficha+"] ended");
                        return null;
                    }

                    // Só me interessa manter o packet vivo SE a ficha dele ainda não tiver completado o tempo vida e não tiver falhado
                    else if(!this.ftr.hasFailed(received_ficha) && !this.ftr.isSync(received_ficha)){
                        this.ftr.channel.resend(wrq_received);
                    }
                } catch (SocketTimeoutException e){

                    System.out.println("[-timeout-]");
                    timeout++;
                    if(timeout == 2){
                        System.out.println("Too many timeouts...");
                        //System.out.println("thread ["+this.ficha+"] ended");
                        return null;
                    }
                    this.socket.send(RRQ);
                }
            }
        }

        /**
         * RECEIVER RUN
         */
        @Override
        public void run() {

            int timeout = 0;
            // A primeira coisa a fazer é enviar um pedido de conexão, um READ REQUEST

            try {
                //System.out.println("Im [THREAD " + this.ficha + "]");
                Triplet<Integer,Integer,String> file_requested = this.getConexao(this.file);

                // Se a conexão der NULL, é porque houve algum erro e não vale a pena continuar a conexão
                if(file_requested == null){
                    this.ftr.requests_failed.add(this.ficha);
                    //this.ftr.channel.garbageCollector(this.ficha);
                    //System.out.println("thread ["+this.ficha+"] ended");
                    return;
                }

                // Número de blocos que irei receber
                int nblocks = file_requested.getValue1();

                // List onde será guardado o conteúdo recebido por transferência
                List<byte[]> answer = new ArrayList<>();

                // preparação de variáveis para leitura
                byte[] data_buff = new byte[1024];
                DatagramPacket data = new DatagramPacket(data_buff, data_buff.length);
                this.socket.setSoTimeout(3000); // Timeout 3 seg
                int currentblock = 1;

                // Começar por enviar o 0, para ter a certeza que o amigo recebeu o ACK 0
                DatagramPacket ACK = Datagrams.ACK(this.ip, this.ficha, 0);

                long total_bytes = 0;
                long start = System.nanoTime();

                while(true){

                    // Se todos os blocos tiverem sido recebidos, terminar

                    if(currentblock == nblocks+1){

                        long finish = System.nanoTime();
                        this.socket.send(ACK);
                        this.socket.send(ACK);
                        for(byte[] b : answer) total_bytes += b.length;

                        // Se tiver sido um pedido #FILENAMES#
                        if(this.file.equals("#filenames#")){
                            this.ftr.loadFriendFiles(answer);
                        }

                        else{

                            System.out.println("Updating file...");
                            boolean updated = this.ftr.folder.updateFile(this.file, answer, this.ficha, this.ip);
                            long milliseconds = TimeUnit.NANOSECONDS.toMillis(finish-start);
                            if(milliseconds == 0) milliseconds = (long)1;
                            this.ftr.requests_done.put(this.ficha,
                                    new Quartet<>(this.file, updated
                                            , milliseconds
                                            , total_bytes));
                        }
                        System.out.println("Received everything from file \""+this.file+"\"!!");
                        // garbage ??
                        //this.ftr.channel.garbageCollector(this.ficha);
                        //System.out.println("thread ["+this.ficha+"] ended");
                        return;
                    }

                    // envio de ACK
                    this.socket.send(ACK);

                    try{
                        // Receber os DATA
                        this.socket.receive(data);
                        int received_opcode = Datagrams.getDatagramOpcode(data);
                        int received_ficha = Datagrams.getDatagramFicha(data);


                        // No caso de ser um bloco que estou à espera,
                        if(received_opcode == 3 && received_ficha == this.ficha){
                            // ( [opcode] [ficha] [block] [blockSize] [data] ) DATA
                            Triplet<Integer,Integer,byte[]> data_info = Datagrams.readDATA(data);
                            int block = data_info.getValue1();

                            // Se for o bloco que se pretendia receber, update das variáveis
                            if(block == currentblock){

                                ACK = Datagrams.ACK(this.ip, this.ficha, currentblock);
                                currentblock++;
                                if(currentblock == nblocks+1){
                                    this.socket.send(ACK);
                                }
                                answer.add(data_info.getValue2());
                            }
                        }
                        // Se a ficha ainda não tiver sido completada, mantê-la viva
                        else if(!this.ftr.hasFailed(received_ficha) && !this.ftr.isSync(received_ficha)){
                            this.ftr.channel.resend(data);
                        }
                    } catch (SocketTimeoutException e){

                         timeout++;
                        if(timeout == 5){

                            System.out.println("Could not receive data for file " + this.file);
                            //System.out.println("thread ["+this.ficha+"] ended");
                            return;
                        }

                        System.out.println("Timeout trying to receive data");
                        if(currentblock == nblocks+1) {
                            long finish = System.nanoTime();
                            boolean updated = this.ftr.folder.updateFile(this.file, answer, this.ficha, this.ip);
                            long milliseconds = TimeUnit.NANOSECONDS.toMillis(finish-start);
                            if(milliseconds == 0) milliseconds = (long)1;
                            this.ftr.requests_done.put(this.ficha,
                                    new Quartet<>(this.file, updated
                                            , milliseconds
                                            , total_bytes));
                            //this.ftr.channel.garbageCollector(this.ficha);
                           // System.out.println("thread ["+this.ficha+"] ended");
                            return;
                        }
                        socket.send(ACK);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}