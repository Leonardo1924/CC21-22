package FFSync;

import org.javatuples.Pair;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ServerChannel implements Runnable {

    // ----------------------------------------------------------------------
        private InetAddress ip;                 // IP para o qual enviarei coisas
        private final static int PORT = 8080;   // Porta na qual estou à escuta
        private DatagramSocket socket;          // Socket de comunicação
        private FTrapid ftr;                    // FTrapid
    // ----------------------------------------------------------------------

    // ----------------------------------------------------------------------
        private Map<String, Integer> current_threads;      // Threads já iniciadas, Map<Filename, Token>
        private String password;
        private boolean isLoggedIn;
    // ----------------------------------------------------------------------


    /**
     * Constructor
     */
    public ServerChannel(FTrapid ftr, InetAddress ip, String password) throws SocketException {

        this.ip = ip;                                   // IP
        this.ftr = ftr;                                 // FTR
        this.socket = new DatagramSocket(PORT);         // SOCKET
        this.current_threads = new HashMap<>();         // CURRENT THREADS
        this.password = password;
        this.isLoggedIn = false;
    }

    public boolean isLoggedIn(){
        return this.isLoggedIn;
    }


    /**
     * Returns destination's IP address
     * @return
     */
    public InetAddress getIP() {
        return this.ip;
    }

    /**
     * Returns server's socket
     * @return
     */
    public DatagramSocket getSocket() {
        return this.socket;
    }

    /**
     * Returns a list of already requested files
     * @return
     */
    public List<String> getServerRequestedFiles() {

        List<String> a = new ArrayList<>();
        for (Map.Entry<String, Integer> e : current_threads.entrySet()) {

            if (!e.getKey().equals("#filenames#"))
                a.add(e.getKey());
        }
        return a;
    }

    /**
     * Reenvia para si mesmo um datagrama que havia recebido previamente
     * @param dp
     * @throws IOException
     */
    public void resend(DatagramPacket dp) throws IOException {

        this.socket.send(new DatagramPacket(dp.getData(), dp.getData().length, dp.getAddress(), PORT));
    }


    /**
     * SERVER RUN
     */
    public void run() {

        // O servidor vai apanhar packets de, no máximo, 1024 bytes
        byte[] receivingbuff = new byte[1024];
        DatagramPacket receivingPacket = new DatagramPacket(receivingbuff, receivingbuff.length);

        // Enquanto estiver vivo,
        while (true) {

            try {

                // Receber packets
                socket.receive(receivingPacket);

                if(!isLoggedIn){

                    int received_opcode = Datagrams.getDatagramOpcode(receivingPacket);
                    if(received_opcode == 4){
                        int received_ficha = Datagrams.getDatagramFicha(receivingPacket);
                        if(received_ficha == -1)
                            this.isLoggedIn = true;
                    }

                    if(!this.isLoggedIn) {
                        ByteBuffer bf = ByteBuffer.allocate(receivingPacket.getData().length);
                        bf.put(receivingPacket.getData());
                        bf.position(0);
                        int size = bf.getInt();
                        byte[] msg = new byte[size];
                        bf.get(msg, 0, size);
                        String pass_try = new String(msg, StandardCharsets.UTF_8);

                        if (pass_try.equals(this.password)) {
                            isLoggedIn = true;
                            System.out.println("Login was made");
                            this.socket.send(Datagrams.ACK(this.ip, -1, -1));
                        }
                    }
                }

                else {
                    int received_opcode = Datagrams.getDatagramOpcode(receivingPacket);
                    int received_ficha = Datagrams.getDatagramFicha(receivingPacket);

                    // Se for recebido um READ REQUEST [1]
                    if (received_opcode == 1) {
                        // ( [opcode] [ficha] [filenameSIZE] [filename] [0] )

                        // Info do RRQ
                        Pair<String, Integer> rrqInfo = Datagrams.readRRQ(receivingPacket);
                        String file = rrqInfo.getValue0();
                        int ficha = rrqInfo.getValue1();

                        // Se for um NOVO pedido,
                        if (!this.current_threads.containsValue(received_ficha)) {

                            // Pedido #FILENAMES#
                            if (file.equals("#filenames#")) {
                                this.current_threads.put(file, ficha);

                                Thread filenames_request = new Thread(new Receiver(this.ftr, file, ficha));
                                filenames_request.start();
                                this.ftr.getTicket();
                            }

                            // Pedido NORMAL
                            else {
                                // Se ainda não tiver havido algum pedido sobre a FILE, executá-lo
                                if (!this.current_threads.containsKey(file)) {

                                    // Se a file não existir, cortar logo a tentativa de transferência
                                    // Se a file existir, proceder
                                    // Atualizar o mapa que faz o registo dos pedidos já efetuados
                                    this.current_threads.put(file, ficha);

                                    Thread file_request = new Thread(new Receiver(this.ftr, file, ficha));
                                    file_request.start();
                                    // Para atualizar o número do TICKET em AMBOS OS LADOS
                                    this.ftr.getTicket();
                                }

                                // Se já tiver sido efetuado um pedido sobre a file, ignorar
                                else {
                                    this.socket.send(Datagrams.ERROR(this.ip, ficha, 1));
                                }
                            }

                        } else {

                            // Se não for um pedido novo,
                            // só o vou querer "receber" se esse pedido ainda não tiver terminado...
                            if (!this.ftr.hasFailed(ficha) && !this.ftr.isSync(file)) {
                                this.resend(receivingPacket);
                            }
                        }
                    } else {

                        // Se não for um pedido RRQ,
                        // só o vou querer receber SE: Ele não registou erro
                        //                         SE: essa thread ainda não terminou

                        if (!this.ftr.hasFailed(received_ficha) && !this.ftr.isSync(received_ficha)) {
                            this.resend(receivingPacket);
                        }
                    }
                }
            } catch (IOException e) {

                if(this.socket.isClosed()){
                    return;
                }
            }
        }
    }


    // -------------------------------------------------------- Thread Receiver -----------------
    static class Receiver implements Runnable {

        private FTrapid ftr;                                            // FTR
        private int connection_ticket;                                  // TOKEN
        private DatagramSocket socket;                                  // SOCKET
        private ServerChannel channel;                                  // CHANNEL
        private Folder folder;                                          // FOLDER
        private String file;                                            // FILE


        public Receiver(FTrapid ftr, String file, int ficha) {
            this.ftr = ftr;                                             // FTR
            this.socket = ftr.getChannel().getSocket();                 // SOCKET
            this.channel = ftr.getChannel();                            // CHANNEL
            this.connection_ticket = ficha;                             // TOKEN
            this.folder = ftr.getFolder();                              // FOLDER
            this.file = file;                                           // FILENAME
        }

        /**
         * Função responsável por aceitar um PEDIDO READ REQUEST
         * @param file
         * @return
         * @throws IOException
         */
        public List<DatagramPacket> acceptConexao(String file) throws IOException {

            // List com o conteúdo da file requerida
            List<DatagramPacket> answer_content;

            //-- Há 2 casos de pedidos possíveis:

            // Pedido #FILENAMES#
            if (file.equals("#filenames#")) {
                answer_content = this.folder.getFilenamesTOSend(this.ftr.getIP(), this.connection_ticket);
            }
            // Pedido Normal File
            else {
                answer_content = this.folder.getFileContent(this.ftr.getIP(), this.connection_ticket, this.file);
            }

            // Se não houver nada para enviar, terminar
            if (answer_content.size() == 0){
                this.ftr.addFailed(this.connection_ticket);

                this.socket.send(Datagrams.ERROR(this.ftr.getIP(), this.connection_ticket, 1));
                return null;
            }

            // Para avançar com a transferência, é necessário enviar um WRQ, até receber um ACK 0
            DatagramPacket wrq = Datagrams.WRQ(this.ftr.getIP(), this.connection_ticket, answer_content.size(), file);

            // Para receber o ACK 0, é preciso preparar um packet para esse efeito
            byte[] ackBuff = new byte[1024]; // como pode receber "lixo", o tamanho default é preferível
            DatagramPacket ack_receiver = new DatagramPacket(ackBuff, ackBuff.length);

            this.socket.setSoTimeout(3000); // timeout de 3 seg
            int timeouts = 0;

            while (true) {

                if(this.ftr.isSync(this.file)) {
                                this.ftr.addFailed(this.connection_ticket);
                                return null;
                            }

                // Enviar o WRITE REQUEST
                this.socket.send(wrq);

                try {
                    // Receber packets
                    this.socket.receive(ack_receiver);
                    int received_opcode = Datagrams.getDatagramOpcode(ack_receiver);
                    int received_ficha = Datagrams.getDatagramFicha(ack_receiver);

                    // Se for um ACK [opcode 4] e se o ACK for referente a ESTA THREAD,
                    if (received_opcode == 4 && received_ficha == this.connection_ticket) {
                        // ( [opcode] [ficha] [block] )
                        Pair<Integer, Integer> ackInfo = Datagrams.readACK(ack_receiver);

                        // Só me interessa o ACK 0 (neste ponto também deverá ser impossível receber outro...)
                        if (ackInfo.getValue1() == 0)
                            return answer_content;  // recebi o ACK, posso concluir que a conexão foi realizada
                    }
                    // Se receber coisas que são lixo,
                    // não tenho garantia que serão úteis ou não, então deixo o trabalho para alguém que o saiba
                    else {
                        if(!this.ftr.hasFailed(received_ficha) && !this.ftr.isSync(received_ficha))
                        this.channel.resend(ack_receiver);
                    }
                } catch (SocketTimeoutException e) {

                    timeouts++;
                    if (timeouts == 5) {
                        System.out.println("too many timeouts...");
                        this.socket.send(Datagrams.ERROR(this.ftr.getIP(), this.connection_ticket, 1));
                        this.ftr.addFailed(this.connection_ticket);
                        //System.out.println("thread ["+this.connection_ticket+"] ended");
                        return null;
                        // Os casos em que é retornado NULL são casos para serem ignorados
                    }
                    this.socket.send(wrq);
                }
            }
        }

        @Override
        public void run() {

            // THREAD responsável pela transferência
            //System.out.println("I'm [R_THREAD] no. [" + this.connection_ticket + "]");
            int timeout = 0;

            // A file pedida é válida, i.e., eu tenho-a?

            // Tenho a file
            if (this.folder.fileExists(this.file) || this.file.equals("#filenames#")) {

                try {
                    // Tento, primeiro, estabelecer ligação e receber a informação que vou enviar
                    List<DatagramPacket> file_content = this.acceptConexao(file);

                    // Se a List voltar vazia, é porque não há nada a ser enviado...
                    // Só deve acontecer no caso de não ter ficheiros e tiver sido pedido um #FILENAMES#
                    if (file_content == null || file_content.size() == 0) {

                        // quem fica à escuta deve receber um erro, para terminar o processo
                        this.socket.send(Datagrams.ERROR(this.ftr.getIP(), this.connection_ticket, 1));
                        System.out.println("error: não há informação para enviar...");
                        this.ftr.addFailed(this.connection_ticket);
                        //System.out.println("thread ["+this.connection_ticket+"] ended");
                        return;
                    }
                    // Se houver informação para ser enviada, proceder
                    else {

                        int currentblock = 1;
                        int currentindex = 0;
                        this.socket.setSoTimeout(3000); // timeout 3 seg
                        byte[] ack_buff = new byte[1024];
                        DatagramPacket ack_receiver = new DatagramPacket(ack_buff, ack_buff.length);

                        // Enviar os blocos de informação
                        while (true) {

                            if(this.ftr.isSync(this.file)) {
                                this.ftr.addFailed(this.connection_ticket);
                                return;
                            }

                            this.socket.send(file_content.get(currentindex));

                            try {

                                this.socket.receive(ack_receiver);
                                int received_opcode = Datagrams.getDatagramOpcode(ack_receiver);
                                int received_ficha = Datagrams.getDatagramFicha(ack_receiver);

                                // Se receber um ACK destinado a este THREAD
                                if (received_opcode == 4 && received_ficha == this.connection_ticket) {

                                    Pair<Integer, Integer> ack_info = Datagrams.readACK(ack_receiver);
                                    //Pair<Ficha,Block>
                                    int received_block = ack_info.getValue1();

                                    // Se o bloco recebido for o pretendido, posso continuar a mandar os seguintes
                                    if (received_block == currentblock) {

                                        // Se recebi o último ACK, posso terminar a transferência
                                        if (currentblock == file_content.size()) {

                                            System.out.println("Sent everything from file \"" + this.file + "\"!");
                                            //this.channel.garbageCollector(this.connection_ticket);
                                            // garbage collector?
                                            //System.out.println("thread ["+this.connection_ticket+"] ended");
                                            return;
                                        }
                                        currentblock++;
                                        currentindex++;
                                    }
                                }
                                // Se receber algum packet referente a algum thread AINDA em execução, mantê-lo vivo no universo
                                else if (!this.ftr.hasFailed(received_ficha) && !this.ftr.isSync(received_ficha)) {
                                    this.channel.resend(ack_receiver);
                                }
                            } catch (SocketTimeoutException e) {

                                timeout++;
                                if(timeout == 5){
                                    this.ftr.addFailed(this.connection_ticket);
                                    System.out.println("too many tmeouts");
                                    //System.out.println("thread ["+this.connection_ticket+"] ended");
                                    return;
                                }
                                // controlo de timeouts?
                                this.socket.send(file_content.get(currentindex));
                            }
                        }
                    }
                } catch (IOException e) {
                    this.socket.isClosed();
                }
            }

            // Não tenho a file
            else {

                // Neste caso, e assumindo a condição de
                // Só pode ser feito um pedido SE:
                // ou [EU] tenho a file e posso, portanto, enviá-la
                // ou [ELE] tem a file e posso, portanto, pedi-la
                try {
                    this.socket.send(Datagrams.ERROR(this.ftr.getIP(), this.connection_ticket, 1));
                    this.ftr.addFailed(this.connection_ticket);

                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Thread getFile = new Thread(new FTrapid.Sender(this.ftr, this.file));
                getFile.start();

                // terminar esta thread
                //System.out.println("thread ["+this.connection_ticket+"] ended");
                return;
                //todo
            }

        }
    }
}