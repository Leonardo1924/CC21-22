package FFSync;

import org.javatuples.Pair;
import org.javatuples.Triplet;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Datagrams {

    private static final int PORT = 8080;

    /**
     *  Criação de um RRQ
     * @param file
     * @param ip
     * @return
     */
    public static DatagramPacket RRQ(InetAddress ip, String file, int ficha){

        // ( [opcode] [ficha] [filenameSIZE] [filename] [0] )

        byte[] filename = file.getBytes(StandardCharsets.UTF_8);

        // Alocação e preenchimento
        ByteBuffer RRQ = ByteBuffer.allocate(4+4+4+filename.length+4);
        RRQ.putInt(1);
        RRQ.putInt(ficha);
        RRQ.putInt(filename.length);
        RRQ.put(filename);
        RRQ.putInt(0);
        // Retornar à posição 0 do buffer
        RRQ.position(0);

        //System.out.println("Created RRQ for file " + file);

        byte[] data = RRQ.array();
        return new DatagramPacket(data, data.length, ip, PORT);
    }

    /**
     * Leitura de um READ REQUEST
     * @param rrq
     * @return Filename and token
     */
    public static Pair<String, Integer> readRRQ(DatagramPacket rrq){
        //      4           4                    4
        // ( [opcode] [ficha] [filenameSIZE] [filename] [0] )

        // Alocar buffer com o tamanho do packet recebido
        byte[] data = rrq.getData();
        ByteBuffer RRQ = ByteBuffer.allocate(data.length);
        RRQ.put(data, 0, data.length);
        // Atualizar índice do buffer para 0
        RRQ.position(0);
        // Retirar o OPCODE
        RRQ.getInt(); // opcode
        int ficha = RRQ.getInt();
        // Retirar FilenameSize
        int size = RRQ.getInt(); // filenameSize

        byte[] filename = new byte[size];
        RRQ.get(filename, 0, size);

        //System.out.println("RRQ for file " + new String(filename, StandardCharsets.UTF_8));
        return new Pair<String,Integer>(new String(filename, StandardCharsets.UTF_8), ficha);
    }

    /**
     * Criação de WRITE REQUEST
     * @param ip
     * @param ficha
     * @param nBlocks
     * @param file
     * @return
     */
    public static DatagramPacket WRQ(InetAddress ip, int ficha, int nBlocks, String file){
        //      4        4        4         4                  4
        // ( [opcode] [ficha] [nBlocks] [fileSize] [filename] [0] )
        // a partir do filesize poderia ser retirado..

        byte[] filename = file.getBytes(StandardCharsets.UTF_8);

        // Alocação e preenchimento
        ByteBuffer WRQ = ByteBuffer.allocate(4+4+4+4+filename.length+4);
        WRQ.putInt(2); //opcode
        WRQ.putInt(ficha); // ficha
        WRQ.putInt(nBlocks); // nBlocks
        WRQ.putInt(filename.length); // filename length
        WRQ.put(filename);
        WRQ.putInt(0);
        // Retornar à posição 0 do buffer
        WRQ.position(0);

        //System.out.println("Created WRQ for Ficha " + ficha + ", with " + nBlocks + " nBlocks, for file " + file);

        byte[] data = WRQ.array();
        return new DatagramPacket(data, data.length, ip, PORT);
    }

    /**
     * Leitura WRITE REQUEST
     * @param wrq
     * @return Token, Number of blocks, Filename
     */
    public static Triplet<Integer,Integer,String> readWRQ(DatagramPacket wrq){
        //      4        4        4         4                  4
        // ( [opcode] [ficha] [nBlocks] [fileSize] [filename] [0] )

        // Alocar buffer com o tamanho do packet recebido
        byte[] data = wrq.getData();
        ByteBuffer WRQ = ByteBuffer.allocate(data.length);
        WRQ.put(data, 0, data.length);
        // Atualizar índice do buffer para 0
        WRQ.position(0);
        // Retirar o OPCODE
        WRQ.getInt(); // opcode
        // Retirar ficha e nº blocks
        int ficha = WRQ.getInt(); // ficha
        int nBlocks = WRQ.getInt(); // nBlocks
        // Retirar FilenameSize
        int size = WRQ.getInt(); // filenameSize

        byte[] filename = new byte[size];
        //System.out.println("Ficha " + ficha + ", pos: " + WRQ.position() + ", size : " + size);
        WRQ.get(filename, 0, filename.length);

        //System.out.println("WRQ for Ficha " + ficha + ", with nBlocks " + nBlocks + ", for file " + new String(filename, StandardCharsets.UTF_8));
        Triplet<Integer,Integer,String> answer = new Triplet<>(ficha,nBlocks,new String(filename, StandardCharsets.UTF_8));

        return answer;
    }

    /**
     * Criação DATA
     * @param ip
     * @param ficha
     * @param block
     * @param data
     * @return
     */
    public static DatagramPacket DATA(InetAddress ip, int ficha, int block, byte[] data){
        //      4       4       4           4
        // ( [opcode] [ficha] [block] [blockSize] [data] )

        ByteBuffer buffer = ByteBuffer.allocate(4+4+4+4+ data.length);
        buffer.putInt(3);                   // opcode
        buffer.putInt(ficha);               // ficha
        buffer.putInt(block);               // block
        buffer.putInt(data.length);         // data length
        buffer.put(data);                   // data
        buffer.position(0);                 // return to index 0

        byte[] answer = new byte[buffer.limit()];
        answer = buffer.array();

        //System.out.println("Created DATA datagram for ficha " + ficha + ", block " + block + " data: " + new String(data, StandardCharsets.UTF_8));
        return new DatagramPacket(answer, answer.length, ip, PORT);
    }

    /**
     * Leitura DATA
     * @param packet
     * @return Token, Block number, Data
     */
    public static Triplet<Integer, Integer, byte[]> readDATA(DatagramPacket packet){
        //      4       4       4           4
        // ( [opcode] [ficha] [block] [blockSize] [data] )

        // Get datagram's data
        byte[] data = packet.getData();
        ByteBuffer buffer = ByteBuffer.allocate(data.length);
        buffer.put(data);
        buffer.position(0);

        // Parse data
        buffer.getInt();                        // opcode
        int ficha = buffer.getInt();            // ficha
        int block = buffer.getInt();            // block
        int size = buffer.getInt();             // dataSize
        byte[] realData = new byte[size];
        buffer.get(realData, 0, size);    // data

        //System.out.println("DATA with ficha " + ficha + ", block " + block + ", data: " + new String(realData, StandardCharsets.UTF_8));
        return new Triplet<>(ficha, block, realData);
    }


    /**
     * Criação ACK
     * @param ip
     * @param ficha
     * @param block
     * @return
     */
    public static DatagramPacket ACK(InetAddress ip, int ficha, int block){
        //      4       4        4
        // ( [opcode] [ficha] [block] )

        ByteBuffer bbuff = ByteBuffer.allocate(4+4+4);
        bbuff.putInt(4);        // opcode
        bbuff.putInt(ficha);    // ficha
        bbuff.putInt(block);    // block
        bbuff.position(0);

        byte[] answer = bbuff.array();

        //System.out.println("Created ACK datagram for ficha " + ficha + ", block " + block);
        return new DatagramPacket(answer, answer.length, ip, PORT);
    }

    /**
     * Leitura ACK
     * @param packet
     * @return Token and Block number
     */
    public static Pair<Integer, Integer> readACK(DatagramPacket packet){
        //      4       4        4
        // ( [opcode] [ficha] [block] )

        // Get ACK's data
        byte[] d = packet.getData();
        ByteBuffer buffer = ByteBuffer.allocate(d.length);
        buffer.put(d);
        buffer.position(0);

        // Parse info
        buffer.getInt();                        // opcode
        int ficha = buffer.getInt();            // ficha
        int block = buffer.getInt();            // block

        //System.out.println("DATA with ficha " + ficha + ", block " + block);
        return new Pair<>(ficha, block);
    }

    /**
     * Criação ERROR
     * @param ip
     * @param ficha
     * @param error
     * @return
     */
    public static DatagramPacket ERROR(InetAddress ip, int ficha, int error){

        //      4       4        4
        // ( [opcode] [ficha] [errorCode] )

        ByteBuffer bbuff = ByteBuffer.allocate(4+4+4);
        bbuff.putInt(5);        // opcode
        bbuff.putInt(ficha);    // ficha
        bbuff.putInt(error);    // error
        bbuff.position(0);

        byte[] answer = bbuff.array();

        //System.out.println("Created ERROR datagram for ficha " + ficha + ", block " + error);
        return new DatagramPacket(answer, answer.length, ip, PORT);
    }

    /**
     * Leitura ERROR
     * @param packet
     * @return Token and Error code
     */
    public static Pair<Integer,Integer> readERROR(DatagramPacket packet){
        //      4       4        4
        // ( [opcode] [ficha] [error] )

        // Get ERROR's data
        byte[] d = packet.getData();
        ByteBuffer buffer = ByteBuffer.allocate(d.length);
        buffer.put(d);
        buffer.position(0);

        // Parse info
        buffer.getInt();                        // opcode
        int ficha = buffer.getInt();            // ficha
        int error = buffer.getInt();            // error

        //System.out.println("ERROR with ficha " + ficha + ", error-code " + error);
        return new Pair<>(ficha, error);
    }

    /**
     * Reads a Datagram's OPCODE
     * @param packet
     * @return
     */
    public static int getDatagramOpcode(DatagramPacket packet){

        byte[] aux = packet.getData();
        ByteBuffer bf = ByteBuffer.allocate(4);
        bf.put(aux, 0, 4);
        bf.position(0);
        return bf.getInt();
    }

    /**
     * Reads a Datagram's TOKEN
     * @param packet
     * @return
     */
    public static int getDatagramFicha(DatagramPacket packet) {

        byte[] aux = packet.getData();
        ByteBuffer bf = ByteBuffer.allocate(8);
        bf.put(aux, 0, 8);
        bf.position(4);
        return bf.getInt();
    }

    /**
     * Prepares a simple text message
     * @param s
     * @param ip
     * @return
     */
    public static DatagramPacket prepareSimpleMessage(String s, InetAddress ip){

        byte[] msg = s.getBytes(StandardCharsets.UTF_8);
        ByteBuffer bf = ByteBuffer.allocate(4+msg.length+4);
        bf.position(0);
        bf.putInt(msg.length);
        bf.put(msg);

        byte[] realmsg = bf.array();
        return new DatagramPacket(realmsg, realmsg.length, ip, PORT);
    }




}
