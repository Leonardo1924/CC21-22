package Servidores;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

public class FT_Rapid_Protocol {
    private char tipo;
    private int offset;
    private int tamNome;
    private String nome;
    private int length;
    private byte[] dados;
    private char ultimo;

    public FT_Rapid_Protocol(char tipo, int offset, int tamNome, String nome, int length, byte[] dados, char ultimo){
        this.tipo = tipo;
        this.offset = offset;
        this.tamNome = tamNome;
        this.nome = nome;
        this.length = length;
        this.dados = dados;
        this.ultimo = ultimo;
    }

    public FT_Rapid_Protocol(byte[] recebido){
        ByteBuffer bf = ByteBuffer.allocate(recebido.length);
        bf.put(recebido);
        bf.position(0);

        this.tipo = bf.getChar();
        this.offset = bf.getInt();
        this.tamNome = bf.getInt();

        byte[] dst = new byte[tamNome];
        bf.get(dst,0,tamNome);
        this.nome = new String(dst);

        this.length = bf.getInt();

        this.dados = new byte[length];
        bf.get(dados,0,length);

        this.ultimo = bf.getChar();
    }

    private ByteBuffer constroiBB_SemChecksum(){
        int tam = 2 + 4 + 4 + 4 + 2 + tamNome + length;

        ByteBuffer bf = ByteBuffer.allocate(tam);

        bf.putChar(this.tipo);
        bf.putInt(offset);
        bf.putInt(tamNome);
        if(tamNome != 0)
            bf.put(nome.getBytes());
        bf.putInt(length);
        if(length != 0)
            bf.put(dados);
        bf.putChar(ultimo);

        return bf;
    }

    private String calculaChecksum(ByteBuffer buffer){
        String sha1 = "";

        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.reset();
            digest.update(buffer.array());
            sha1 = String.format("%040x", new BigInteger(1,digest.digest()));
        }catch (Exception e){
            e.printStackTrace();
        }
        return sha1;
    }

    public byte[] getBytes(){
            int tam = 2 + 4 + 4 + 4 + 2 + tamNome + length;

            ByteBuffer bf = ByteBuffer.allocate(tam);

            bf.putChar(this.tipo);
            bf.putInt(offset);
            bf.putInt(tamNome);
            if(tamNome!=0)
                bf.put(nome.getBytes());
            bf.putInt(length);
            if(length != 0)
                bf.put(dados);
            bf.putChar(ultimo);

            return bf.array();
    }

    public char getTipo() {
        return tipo;
    }

    public void setTipo(char tipo) {
        this.tipo = tipo;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getTamNome() {
        return tamNome;
    }

    public void setTamNome(int tamNome) {
        this.tamNome = tamNome;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public byte[] getDados() {
        return dados;
    }

    public void setDados(byte[] dados) {
        this.dados = dados;
    }

    public char getUltimo() {
        return ultimo;
    }

    public void setUltimo(char ultimo) {
        this.ultimo = ultimo;
    }

    public boolean isLast(){
        return this.ultimo == 'T';
    }

    public int getTamanhoDados(){
        return this.dados.length;
    }
}
