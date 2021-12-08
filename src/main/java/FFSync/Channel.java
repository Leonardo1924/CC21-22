package FFSync;

import org.javatuples.Pair;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Channel implements Runnable{

    private FTrapid ftr;
    private DatagramSocket socket;
    private static final int PORT = 8080;


    public Channel(FTrapid ftr) throws SocketException {

        this.ftr = ftr;
        this.socket = new DatagramSocket(9999);
        // cuidado cm o port
    }


    public void send(DatagramPacket packet) throws IOException {

        this.socket.send(packet);
    }



    @Override
    public void run() {

        byte[] income = new byte[512];
        DatagramPacket receivingPacket = new DatagramPacket(income, income.length);

        try {
            this.socket.receive(receivingPacket);

            System.out.println("Received opcode: " + this.ftr.getDatagramOpcode(receivingPacket));

            int opcode = this.ftr.getDatagramOpcode(receivingPacket);
            int nblocks = 0;

            // Se for um WRQ
            if (opcode == 1) {
                // Enviar um ACK 0
                this.socket.send(this.ftr.ACK(0));

                // Verificar o nº de blocos que me querem enviar + filename
                Pair<Integer, String> rqinfo = this.ftr.readRQ(receivingPacket);

                // Receber e alocar o conteúdo da transferência
                List<byte[]> answer = this.receiveFile(rqinfo.getValue0());

                // Se for um pedido de "#filenames#", quero adicionar À lista de filenames do friend
                if(rqinfo.getValue1().equals("#filenames#")) {
                    for (byte[] b : answer) {

                        System.out.println(new String(b, StandardCharsets.UTF_8));
                    }
                }
                else {
                    // Se for uma outra file qualquer, deveria comparar com a minha file, se forem diferentes, substituir o conteúdo
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

        public boolean sendWRQ(String filename, int nblocks) throws IOException {

        this.socket.setSoTimeout(4000);
        byte[] receivingbuff = new byte[512];
        DatagramPacket receivingPacket = new DatagramPacket(receivingbuff, receivingbuff.length);

        DatagramPacket rq = this.ftr.RQ(1, nblocks, filename);
        int ntimeouts = 0;

        while(true) {
            this.socket.send(rq);

            try {

                this.socket.receive(receivingPacket);
                Pair<Integer, Integer> ack = ftr.readACK(receivingPacket);

                System.out.println("Cheguei aq pelo menos");

                if(ack.getValue0() == 4 && ack.getValue1() == 0){
                    System.out.println("Confirmed ACK0!");
                    return true;
                }
            } catch (SocketTimeoutException e){

                System.out.println("Timedout");
                ntimeouts++;
                if(ntimeouts == 8){
                    System.out.println("Too many timeouts");
                    return false;
                }
                this.socket.send(rq);
            }
        }
    }


    public boolean sendFile(List<DatagramPacket> filepackets) throws IOException {

        this.socket.setSoTimeout(6000);
        byte[] receivingbuff = new byte[512];
        DatagramPacket receivingPacket = new DatagramPacket(receivingbuff, receivingbuff.length);
        int currentBlock = 1;
        int ntimeouts = 0;


        while(true) {
            this.socket.send(filepackets.get(currentBlock-1));

            try{

                this.socket.receive(receivingPacket);

                int received_opcode = this.ftr.getDatagramOpcode(receivingPacket);
                System.out.println("received opcode " + received_opcode + ", current " + currentBlock);
                if(received_opcode == 4){

                    Pair<Integer, Integer> ack = ftr.readACK(receivingPacket);

                    System.out.println("ack block " + ack.getValue1());

                    if(ack.getValue1() == currentBlock){
                        currentBlock++;

                        if(currentBlock == filepackets.size()+1){
                            this.socket.setSoTimeout(0);
                            return true;
                        }

                        this.socket.send(filepackets.get(currentBlock-1));
                    }
                }

            }
            catch (SocketTimeoutException e){

                ntimeouts++;
                if(ntimeouts == 7){
                    System.out.println("Too many timeouts...");
                    return false;
                }
            }
        }
    }

    // no caso da troca de filenames, como eq ele sabe qd para?
    public List<byte[]> receiveFile(int nblocks) throws IOException {

        byte[] buff = new byte[512];
        DatagramPacket receivingPacket = new DatagramPacket(buff, buff.length);

        List<byte[]> actualData = new ArrayList<>();
        int currentblock = 1;
        int ntimeouts = 0;
        this.socket.setSoTimeout(6000);

        while(true){

            try {
                this.socket.receive(receivingPacket);

                int opcode = this.ftr.getDatagramOpcode(receivingPacket);

                if(opcode == 3){

                    Pair<Integer, byte[]> data = this.ftr.readDATA(receivingPacket);
                    System.out.println("Received DATA block " + data.getValue0());

                    if(data.getValue0() == currentblock){
                        actualData.add(data.getValue1());
                        this.socket.send(this.ftr.ACK(currentblock));
                        currentblock++;
                    }
                }

            } catch (SocketTimeoutException e){
                this.socket.send(this.ftr.ACK(currentblock));
                if(currentblock == nblocks+1){
                    System.out.println("Received everything!");
                    this.socket.setSoTimeout(0);
                    return actualData;
                }
                System.out.println("Timeout..");
                ntimeouts++;
                if(ntimeouts == 8){
                    this.socket.setSoTimeout(0);
                    return null;
                }
            }
        }
    }
}
