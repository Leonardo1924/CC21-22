package Servidores.FolderFastSync;

import Servidores.FT_Rapid_Protocol;
import Servidores.GeneratePassWord;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FolderFastSync {
    private Map<String, Long> files;

    private final String pasta;
    private final String ip;

    private DatagramSocket s;

    private Map<String, byte[]> bytesforfile;

    private final String password = "CC_2122_PL25";

    public FolderFastSync(String pasta, String ip) {
        this.pasta = pasta;
        this.ip = ip;
        this.files = new HashMap<>();
        this.bytesforfile = new HashMap<>();

        File file = new File(pasta);
        Arrays.stream(file.listFiles())
                .filter(f -> f.isFile())
                .forEach(f -> this.getInfoFile(f));

        try {
            this.s = new DatagramSocket(8888);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    /*Acho que isto esta a fazer por cada file e não por pasta (tenho de estudar o caso)*/
    private void getInfoFile(File f) {
        String s = f.getAbsolutePath();
        s = s.replace('\\', '/');
        String[] nomes = s.split("/");
        String nomes2 = nomes[nomes.length - 1];
        String[] temp = nomes2.split("\\.");
        String nome = "";
        for (int i = 0; i < temp.length; i++) {
            if ((i + 1) == temp.length) {
                nome = nome + "." + temp[i].toLowerCase();
            } else {
                nome = nome + temp[i];
            }
        }
        this.files.put(nome, f.length());

        try {
            byte[] file1 = new byte[(int) f.length()];
            String nomeFich = pasta + "/" + nome;
            FileInputStream input = new FileInputStream(nomeFich);
            input.read(file1);
            this.bytesforfile.put(nome, file1);
        } catch (IOException ignored) {
            ;
        }
    }

    private void InitConnection(){
        try {
            byte[] passwordCript = GeneratePassWord.generateHMAC(this.password);
            FT_Rapid_Protocol ft = new FT_Rapid_Protocol();//faz cenas
            byte[] aEnviar = ft.getBytes();// faz cenas também
            final DatagramPacket p = new DatagramPacket(aEnviar, aEnviar.length, InetAddress.getByName(ip), 9888);

            Runnable worker2 = () -> {
                while (true) {
                    try {
                        s.send(p);
                    } catch (IOException ignored) {
                        ;
                    }

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {
                        ;
                    }
                }
            };
            Thread t = new Thread(worker2);
            t.start();

            String dadosTotaisS = "";
            for(Map.Entry<String,Long> temp : this.files.entrySet()){
                dadosTotaisS = dadosTotaisS + temp.getKey()+":"+temp.getValue()+" ";
            }
            byte[] dados = dadosTotaisS.getBytes();

            //Recebe pedido de dados
            boolean fim = false;
            while (!fim){
                byte[] aReceber = new byte[1024];//Acho que podemos alterar tamano aqui
                DatagramPacket pedido = new DatagramPacket(aReceber, aReceber.length);
                s.receive(pedido);
                t.stop();

                int porta = pedido.getPort();

                ft = new FT_Rapid_Protocol(aReceber);

                if(ft.isLast()){
                    fim = true;
                }
                else{
                    int offsett = ft.getOffset();
                    byte[] d = new byte[968]; //Acho que pode ser alterado
                    int j = 0;
                    int i = offsett;
                    for(; i<968 && i<dados.length;i++){
                        d[j++] = dados[i];
                    }

                    char ultimo = (i==dados.length) ? 'T' : 'F';

                    ft = new FT_Rapid_Protocol('G',offsett,d.length,d,ultimo);
                    aEnviar = ft.getBytes();

                    DatagramPacket p2 = new DatagramPacket(aEnviar,aEnviar.length, InetAddress.getByName(ip),porta);
                    s.send(p2);
                }
            }
        } catch (IOException ignored){;}
    }
    private void finalizaConeccao(){
        try{
            //Finaliza Conexao
            byte[] passwordCripto = GeneratePassWord.generateHMAC(this.password);
            FT_Rapid_Protocol fr = new FT_Rapid_Protocol('F',0,0,null,passwordCripto.length,passwordCripto,'T');
            byte[] aEnviar = fr.getBytes();
            DatagramSocket ds = new DatagramSocket(8888);
            final DatagramPacket p = new DatagramPacket(aEnviar, aEnviar.length, InetAddress.getByName(ip), );

            Runnable worker2 = () -> {
                while (true) {
                    try {
                        ds.send(p);
                    } catch (IOException ignored) {;}

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {;}
                }
            };
            Thread t = new Thread(worker2);
            t.start();

            //Recebe a confirmação que finalizou a conexao
            boolean fim = false;
            while(!fim){
                byte[] aReceber = new byte[1024];
                DatagramPacket pedido = new DatagramPacket(aReceber, aReceber.length);
                ds.receive(pedido);

                fr = new FT_Rapid_Protocol(aReceber);

                if(fr.isLast()){
                    t.stop();
                    fim = true;
                }
            }
            ds.close();
        }catch (IOException ignored){;}
    }

    public void run(){
        //Iniciar Conexão
        InitConnection();

        Runnable worker3 = () -> {
            while (true){
                try {
                    byte[] aReceber = new byte[1024];
                    DatagramPacket pedido = new DatagramPacket(aReceber, aReceber.length);
                    s.receive(pedido);

                    if(pedido.getAddress().getHostAddress().equals(ip)){

                        Runnable worker = () -> {
                            FT_Rapid_Protocol ft = new FT_Rapid_Protocol(aReceber);

                            System.out.println("Recebeu pedido de ficheiro (offset: " + ft.getOffset() + ", Nome: " + ft.getNome() + ")");

                            int offset = ft.getOffset();
                            String nomeFicheiro = ft.getNome();
                            int tamNomeFich = nomeFicheiro.getBytes().length;
                            int maxPacote = 1008 - tamNomeFich;
                            byte[] dados = new byte[maxPacote];

                            //encher o array de dados
                            try {
                                int j = 0;
                                byte[] file = this.bytesforfile.get(nomeFicheiro);
                                for (int i = offset; j < maxPacote && i < this.files.get(nomeFicheiro); i++) {
                                    dados[j++] = file[i];
                                }

                                FT_Rapid_Protocol frEnviar = new FT_Rapid_Protocol('T', offset, tamNomeFich, nomeFicheiro, dados.length, dados, 'T');
                                byte[] aEnviar = frEnviar.getBytes();

                                DatagramPacket p = new DatagramPacket(aEnviar, aEnviar.length, InetAddress.getByName(ip),8888);
                                s.send(p);

                            } catch (IOException ignored) {
                                System.out.println("Impossivel enviar dados ao HttpGw!");
                            }
                        };
                        new Thread(worker).start();
                    }
                } catch (IOException ignored) {;}
            }
        };
        Thread t = new Thread(worker3);
        t.start();

        Scanner s = new Scanner(System.in);
        boolean entrar = true;
        System.out.println("O servidor está ativo. Presionar a tecla 1 para desconectar-se.");
        while (entrar){
            String input = s.next();
            entrar = input.compareToIgnoreCase("1\n") == 0;

            if(!entrar){
                System.out.println("A finalizar a conecção");
                finalizaConeccao();
                this.s.close();
                t.stop();
                System.out.println("Conecção finalizada");
            }
        }
    }

}
