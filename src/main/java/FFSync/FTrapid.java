package FFSync;

import org.javatuples.Pair;

import javax.xml.crypto.Data;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FTrapid {

    private InetAddress friend_ip;
    private List<String> filenames;
    private static final int PORT = 8080;

    public FTrapid(String ip, String path) throws IOException {

        this.friend_ip = InetAddress.getByName(ip);
        this.filenames = this.uploadFilenames(path);
    }

    /**
     * Carrega os nomes das files no path especificado
     * @param path
     * @return
     */
    private List<String> uploadFilenames(String path) {

        List<String> names = new ArrayList<>();
        File folder = new File(path);
        File[] files = folder.listFiles();
        if(files == null) return null;
        for(File f : files) {
            names.add(f.getName());
        }
        return names;
    }

    /**
     * Get do nome das files dos ficheiros presentes no path
     * @return
     */
    public List<String> getFilenames(){ return new ArrayList<>(this.filenames);}

    /**
     * Envia todos os filenames presentes no path especificado
     * @param channel
     * @throws IOException
     */
    public void sendFileNames(Channel channel) throws IOException {

        int block = 1;
        List<DatagramPacket> d = new ArrayList<>();

        for(String s : this.filenames){

            d.add(this.DATA(block, s.getBytes(StandardCharsets.UTF_8)));
            block++;
        }

        channel.sendWRQ("filenames", d.size());
        channel.sendFile(d);
    }


    /**
     * Retirar a informação útil de um ACK
     * @param ackpacket
     * @return
     */
    public Pair<Integer,Integer> readACK(DatagramPacket ackpacket){

        byte[] ack = ackpacket.getData();

        ByteBuffer a = ByteBuffer.allocate(ack.length);
        a.put(ack,0,ack.length);
        a.position(0);
        int opcode = a.getInt();
        int block = a.getInt();

        return new Pair<>(opcode, block);
    }

    /**
     * Criar um ACK
     * @param block
     * @return
     */
    public DatagramPacket ACK(int block){

        // ( [opcode] [block])
        // integer : 4 bytes
        ByteBuffer ack_pack = ByteBuffer.allocate(8);
        ack_pack.putInt(4);
        ack_pack.putInt(block);
        ack_pack.position(0);

        byte[] data = ack_pack.array();
        return new DatagramPacket(data, data.length, friend_ip, PORT);
    }


    /**
     * Retirar a informação útil de um RQ
     * @param packet
     * @return
     */
    public Pair<Integer, String> readRQ(DatagramPacket packet){

        byte[] data = packet.getData();
        ByteBuffer RQ = ByteBuffer.allocate(data.length);
        RQ.put(data, 0, data.length);
        RQ.position(0);
        RQ.getInt(); // opcode
        int nblocks = RQ.getInt(); // number of blocks
        byte[] filename = new byte[RQ.limit()-12];
        RQ.get(filename, 0, RQ.limit()-12);

        return new Pair<>(nblocks, new String(filename, StandardCharsets.UTF_8));
    }

    /**
     * Criação de um RQ
     * @param rq
     * @param n_blocks
     * @param file
     * @return
     */
    public DatagramPacket RQ(int rq, int n_blocks, String file){

        // ( [opcode] [nblocks] [filename] [0] )

        byte[] filename = file.getBytes(StandardCharsets.UTF_8);

        ByteBuffer RQ = ByteBuffer.allocate(4+4+filename.length+4);
        RQ.putInt(rq);
        RQ.putInt(n_blocks);
        RQ.put(filename);
        RQ.putInt(0);
        RQ.position(0);

        byte[] data = RQ.array();
        return new DatagramPacket(data, data.length, friend_ip, PORT);
    }

    /**
     * Retirar informação útil de um DATA
     * @param packet
     * @return
     */
    public Pair<Integer, byte[]> readDATA(DatagramPacket packet){

        byte[] data = packet.getData();
        ByteBuffer buffer = ByteBuffer.allocate(data.length);
        buffer.put(data, 0, data.length);
        buffer.position(0);

        buffer.getInt();
        int block = buffer.getInt();
        int size = buffer.getInt();

        byte[] realData = new byte[size];
        buffer.get(realData, 0, realData.length);

        return new Pair<>(block, realData);
    }

    /**
     * Criação de um DATA
     * @param block
     * @param data
     * @return
     */
    public DatagramPacket DATA(int block, byte[] data){

        // ( [opcode] [nblock] [datasize] [data] ) 512 (4 + 4 + 4 + 500)

        ByteBuffer buffer = ByteBuffer.allocate(12+data.length);
        if(buffer.limit() > 500){
            return null;
        }

        buffer.putInt(3);
        buffer.putInt(block);
        buffer.putInt(data.length);
        buffer.put(data);
        byte[] datapacket = buffer.array();
        return new DatagramPacket(datapacket, datapacket.length, friend_ip, PORT);
    }

    /**
     * Retirar o OPCODE de um PACKET
     * @param packet
     * @return
     */
    public int getDatagramOpcode(DatagramPacket packet){

        byte[] data = packet.getData();
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.put(data, 0, 4);
        buffer.position(0);

        return buffer.getInt();
    }










}
