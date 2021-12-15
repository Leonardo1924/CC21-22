package FFSync;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Folder {

    private String path;
    private List<String> filenames;

    /**
     * Constructor
     */
    public Folder(String path){

        this.path = path;
        this.filenames = new ArrayList<>();
        this.storeFilenames();
    }

    /**
     * Loads current folder's files' names
     */
    private void storeFilenames(){

        File folder = new File(path);
        File[] files = folder.listFiles();
        if(files == null) return;
        for(File f : files) {
            this.filenames.add(f.getName());
        }
    }

    /**
     * Devolve o path do folder
     * @return
     */
    public String getPath() {
        return path;
    }

    /**
     * Retorna lista com nome de todas as files presentes no folder
     * @return
     */
    public List<String> getFilenames(){ return new ArrayList<>(this.filenames);}

    /**
     * Verifica se o folder em questão tem uma determinada file
     * @param file
     * @return
     */
    public boolean fileExists(String file){
        return this.filenames.contains(file);
    }

    /**
     * Devolve o path de uma file presente no folder
     * @param file
     * @return
     */
    public String getFilePath(String file){

        String filepath = this.path + File.separator + file;
        return filepath;
    }

    /**
     * Devolve uma lista de packets com os filenames do folder associado
     * @param ip
     * @param ficha
     * @return
     */
    public List<DatagramPacket> getFilenamesTOSend(InetAddress ip, int ficha){

        if(this.filenames == null || this.filenames.size() == 0){
            System.out.println("error: You don't have any files in your folder...");
            return null;
        }
        // Lista com os packets
        List<DatagramPacket> packets = new ArrayList<>();
        // Bloco inicial
        int currentblock = 1;
        // Para cada file, criar um DATA com o seu nome e o bloco correspondente
        for(String s : this.filenames){

            packets.add(Datagrams.DATA(ip, ficha, currentblock, s.getBytes(StandardCharsets.UTF_8)));
            currentblock++;
        }

        return packets;
    }


    /**
     * Prepara o conteúdo de uma file numa lista de Datagramas DATA
     * @param ficha
     * @param filename
     * @return
     * @throws IOException
     */
    public List<DatagramPacket> getFileContent(InetAddress ip, int ficha, String filename) throws IOException {

        // Verifica se a file existe no folder
        if(!this.fileExists(filename)){

            System.out.println("error: That file does not exist!");
            return null;
        }

        // Encontra o diretório completo da file
        String filepath = this.getFilePath(filename);

        // Conteúdo da file
        byte[] content = Files.readAllBytes(Path.of(filepath));
        System.out.println("file has " + content.length + " bytes");

        // ByteBuffer
        ByteBuffer auxBuffer = ByteBuffer.allocate(content.length);
        auxBuffer.put(content);
        auxBuffer.position(0);

        // Número de datagramas que terei de criar
        int size = 1008;
        int npackets = (int) Math.ceil((double)content.length / size);

        // Bloco inicial DATA
        int currentblock = 1;

        int currentLength = content.length;
        int current_index = 0;

        List<DatagramPacket> answer = new ArrayList<>();

        for(int i = 0; i < npackets; i++){
            // Se o bloco contiver mais que o máximo pré-definido ( 1024 - 4 - 4 - 4 - 4 = 1008)
            if(currentLength > size){
                byte[] aux = new byte[size];
                auxBuffer.get(aux, current_index, size-1);
                DatagramPacket packet = Datagrams.DATA(ip, ficha, currentblock, aux);
                answer.add(packet);
                current_index += size;
                currentLength -= size;
                currentblock++;
            }
            else{
                // Se for o último bloco, terá o próprio tamanho
                byte[] aux = new byte[currentLength];
                auxBuffer.get(aux, current_index, currentLength);
                DatagramPacket packet = Datagrams.DATA(ip, ficha, currentblock, aux);
                answer.add(packet);
            }
        }
            return answer;
    }

    /**
     * If a new file's version is received, it updates it
     * @param file
     * @param updatedContent
     * @param ficha
     * @param ip
     * @return
     * @throws IOException
     */
    public boolean updateFile(String file, List<byte[]> updatedContent, int ficha, InetAddress ip) throws IOException {

            List<DatagramPacket> my_version_packets = this.getFileContent(ip, ficha, file);
            if(my_version_packets == null){
                this.rewriteFile(file, updatedContent);
                System.out.println("Your file needed rewriting...");
                return true;
            }

            List<byte[]> my_version_content = new ArrayList<>();
            for(DatagramPacket dp : my_version_packets){
                my_version_content.add(Datagrams.readDATA(dp).getValue2());
            }

            if(my_version_content.size() != updatedContent.size()){
                this.rewriteFile(file, updatedContent);
                System.out.println("Your file needed rewriting...");
                return true;
            }

            int index = 0;
            for(byte[] b : updatedContent){

                if(!Arrays.equals(b, my_version_content.get(index))){
                    this.rewriteFile(file, updatedContent);
                    System.out.println("Your file needed rewriting...");
                    return true;
                }
                index++;
            }

            System.out.println("Your file was already updated!");
        return false;
    }

    /**
     * Actual file rewriter
     * @param file
     * @param content
     * @throws IOException
     */
    public void rewriteFile(String file, List<byte[]> content) throws IOException {

        FileOutputStream fos = new FileOutputStream(this.getFilePath(file));
        for(byte[] b : content) {
            fos.write(b);
            fos.flush();
        }
        fos.close();
    }



}
